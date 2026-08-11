"""Tests endpoint DELETE /api/v1/me (suppression de compte self-only).

2026-05-21 : feature TODO_FEATURES §9 "Endpoint DELETE /me (suppression de compte)".

Couvre :
- 200 suppression OK + UserOut renvoyé + le compte ne peut plus se reconnecter.
- Cascade : les refresh_tokens du user sont supprimés (FK ON DELETE CASCADE).
- 403 si le mot de passe de confirmation est incorrect (le compte survit).
- 401 sans token (auth requise).
- 400 si le user est le dernier admin restant (le compte survit).

Les tests créent des users JETABLES (fixture make_user) -- la DB de test est
session-scoped, on ne touche pas testuser/otheruser.
"""
import bcrypt
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.models import RefreshToken, User

from .conftest import login_headers


@pytest_asyncio.fixture
async def make_user(test_engine):
    """Crée des users jetables pour les tests destructifs de DELETE /me.
    Cleanup : supprime en fin de test ceux qui existent encore (un test qui
    a effectivement supprimé son user via l'endpoint n'a rien à nettoyer)."""
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    created_ids: list[int] = []

    async def _make(username: str, password: str, is_admin: bool = False) -> int:
        async with session_maker() as session:
            pw_hash = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
            user = User(
                username=username,
                hashed_password=pw_hash,
                first_name=username.capitalize(),
                last_name="Throwaway",
                is_admin=is_admin,
            )
            session.add(user)
            await session.commit()
            created_ids.append(user.id)
            return user.id

    yield _make

    async with session_maker() as session:
        for uid in created_ids:
            obj = await session.get(User, uid)
            if obj is not None:
                await session.delete(obj)
        await session.commit()


async def test_delete_me_success(client, make_user):
    """User authentifié supprime son compte -> 200 + UserOut + login impossible après."""
    await make_user("delete_me_ok", "secretpass")
    headers = await login_headers(client, "delete_me_ok", "secretpass")

    response = await client.request(
        "DELETE", "/api/v1/me",
        json={"password": "secretpass"},
        headers=headers,
    )
    assert response.status_code == 200, f"Expected 200, got {response.status_code}: {response.text}"
    body = response.json()
    assert body["username"] == "delete_me_ok"

    # Le compte n'existe plus -> /token retourne 401.
    relogin = await client.post(
        "/api/v1/token",
        data={"username": "delete_me_ok", "password": "secretpass"},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    assert relogin.status_code == 401


async def test_delete_me_cascades_refresh_tokens(client, make_user, test_engine):
    """Supprimer le compte cascade sur refresh_tokens (FK ON DELETE CASCADE)."""
    user_id = await make_user("delete_me_cascade", "secretpass")
    headers = await login_headers(client, "delete_me_cascade", "secretpass")

    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)

    # Le login a créé au moins un refresh_token pour ce user.
    async with session_maker() as session:
        before = (await session.execute(
            select(RefreshToken).where(RefreshToken.user_id == user_id)
        )).scalars().all()
    assert len(before) >= 1, "le login devrait avoir créé un refresh_token"

    response = await client.request(
        "DELETE", "/api/v1/me",
        json={"password": "secretpass"},
        headers=headers,
    )
    assert response.status_code == 200

    # Cascade : plus aucun refresh_token pour ce user_id.
    async with session_maker() as session:
        after = (await session.execute(
            select(RefreshToken).where(RefreshToken.user_id == user_id)
        )).scalars().all()
    assert after == [], "les refresh_tokens auraient dû être cascade-deleted"


async def test_delete_me_wrong_password(client, make_user):
    """Mauvais mot de passe de confirmation -> 403, le compte survit."""
    await make_user("delete_me_wrongpw", "secretpass")
    headers = await login_headers(client, "delete_me_wrongpw", "secretpass")

    response = await client.request(
        "DELETE", "/api/v1/me",
        json={"password": "WRONGPASS"},
        headers=headers,
    )
    assert response.status_code == 403

    # Le compte survit -> login toujours possible.
    relogin = await login_headers(client, "delete_me_wrongpw", "secretpass")
    assert relogin["Authorization"].startswith("Bearer ")


async def test_delete_me_requires_auth(client):
    """Sans token -> 401 (body fourni pour isoler l'échec sur l'auth)."""
    response = await client.request(
        "DELETE", "/api/v1/me",
        json={"password": "whatever"},
    )
    assert response.status_code == 401


async def test_delete_me_last_admin_blocked(client, make_user):
    """Le seul admin restant ne peut pas supprimer son compte -> 400, survit."""
    await make_user("delete_me_admin", "secretpass", is_admin=True)
    headers = await login_headers(client, "delete_me_admin", "secretpass")

    response = await client.request(
        "DELETE", "/api/v1/me",
        json={"password": "secretpass"},
        headers=headers,
    )
    assert response.status_code == 400, f"Expected 400, got {response.status_code}: {response.text}"
    assert "last remaining admin" in response.json()["detail"]

    # Le compte survit -> login toujours possible.
    relogin = await login_headers(client, "delete_me_admin", "secretpass")
    assert relogin["Authorization"].startswith("Bearer ")
