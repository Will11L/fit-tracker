"""conftest.py — fixtures pytest pour les tests serveur sport-app.

T1.1.a (2026-05-06) : fondation pytest + fixture httpx async client.
T1.1.b (2026-05-06) : fixture DB Postgres `fittracker_test` (session-level)
+ seed user `testuser/testpass` + override de `get_session` dependency.

DB de test : fittracker_test (Postgres separee, OWNER fittracker).
Strategie isolation : la DB est drop+recreate au debut de la session pytest
(fixture session-level autouse). Les tests successifs partagent l'etat ; les
tests qui mutent doivent gerer leur propre cleanup ou utiliser un user
distinct.

Cf. CLAUDE.md politique fix-first + TODO_FEATURES.md §0 Tier 1.
"""
import bcrypt
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

import app.models  # noqa: F401 -- enregistre tous les modeles dans Base.metadata
from app.database import Base, get_session
from app.main import app
from app.models import User
from app.rate_limit import limiter
from app.settings import settings
from app.triggers_loader import (
    attach_triggers_sql,
    compose_function_sql,
    iso_utc_helper_sql,
    user_id_helper_sql,
)

# T1.1.c (2026-05-06) : desactive slowapi pendant la session pytest. Sans ça,
# les tests qui font multiples /token (1 login par test x 2 users) atteignent
# rapidement la limite 5/min sur 127.0.0.1 et echouent en 429. En prod, le
# limiter reste active (V8.2-4 anti brute-force).
limiter.enabled = False


@pytest_asyncio.fixture(scope="session", loop_scope="session")
async def test_engine():
    """Engine async pointant vers fittracker_test. Disposé en fin de session."""
    engine = create_async_engine(settings.TEST_DATABASE_URL, future=True)
    yield engine
    await engine.dispose()


@pytest_asyncio.fixture(scope="session", loop_scope="session", autouse=True)
async def setup_test_db(test_engine):
    """Drop + create schema sur fittracker_test, installe helpers + triggers, seed un user.

    autouse=True : applique automatiquement à TOUTE la session pytest.
    Pas de teardown : la DB de test reste en l'etat apres pytest (utile pour
    debug post-mortem, sera reset au prochain run).
    """
    async with test_engine.begin() as conn:
        # Drop orphan tables from previous schema versions (e.g. Phase 0
        # 2026-05-12 unification routine_tasks/routine_task_checks -> tasks/task_checks).
        # Base.metadata.drop_all ne voit que les tables actuellement mappees ;
        # les tables retirees par une migration restent dans la DB de test
        # comme orphelines apres un git pull. On les nettoie explicitement
        # ici pour que la fixture reste idempotente apres tout refactor.
        await conn.exec_driver_sql("DROP TABLE IF EXISTS routine_task_checks CASCADE")
        await conn.exec_driver_sql("DROP TABLE IF EXISTS routine_tasks CASCADE")

        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
        await conn.exec_driver_sql(iso_utc_helper_sql())
        await conn.exec_driver_sql(user_id_helper_sql())
        await conn.exec_driver_sql(compose_function_sql())
        await conn.exec_driver_sql(attach_triggers_sql())

    # Seed 2 users pour les tests d'auth + ownership cross-user.
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with session_maker() as session:
        for username, password in [("testuser", b"testpass"), ("otheruser", b"otherpass")]:
            password_hash = bcrypt.hashpw(password, bcrypt.gensalt()).decode("utf-8")
            session.add(User(
                username=username,
                hashed_password=password_hash,
                first_name=username.capitalize(),
                last_name="Test",
                is_admin=False,
            ))
        await session.commit()

    yield


async def login_headers(client, username: str, password: str) -> dict:
    """Helper : POST /token + retourne le header Authorization Bearer.

    Usage dans un test :
        headers_a = await login_headers(client, "testuser", "testpass")
        await client.put("/api/v1/exercises/uuid", json={...}, headers=headers_a)
    """
    response = await client.post(
        "/api/v1/token",
        data={"username": username, "password": password},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    assert response.status_code == 200, f"Login failed for {username}: {response.text}"
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@pytest_asyncio.fixture
async def client(test_engine):
    """HTTP async client (ASGITransport) avec get_session override vers la DB de test."""
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)

    async def override_get_session():
        async with session_maker() as session:
            yield session

    app.dependency_overrides[get_session] = override_get_session

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac

    app.dependency_overrides.clear()
