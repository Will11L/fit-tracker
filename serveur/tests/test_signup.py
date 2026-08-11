"""Tests endpoint POST /api/v1/signup -- email reel optionnel (2026-06-06).

Couvre :
- Signup SANS email -> 201, email null (PAS l'ancien synthetique).
- Signup AVEC email -> 201, email stocke + relu via GET /me.
- Login reste username (on se logge avec le username, jamais l'email).

Pre-requis : le user fixture `starter_template` doit exister (copy_starter_pack
au signup, sinon 503). On le seed ici ; en DB de test son catalogue est vide
donc la copie est un no-op. Users JETABLES nettoyes en teardown (DB de test
session-scoped).
"""
import bcrypt
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.models import User
from app.settings import settings

from .conftest import login_headers


@pytest_asyncio.fixture
async def signup_env(test_engine):
    """Garantit le user starter_template + nettoie les comptes crees par les
    tests de signup (track via le callable yielde)."""
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    created_usernames: list[str] = []

    async with session_maker() as session:
        existing = (await session.execute(
            select(User).where(User.username == settings.STARTER_TEMPLATE_USERNAME)
        )).scalar_one_or_none()
        if existing is None:
            pw_hash = bcrypt.hashpw(b"tpl", bcrypt.gensalt()).decode("utf-8")
            session.add(User(
                username=settings.STARTER_TEMPLATE_USERNAME, hashed_password=pw_hash,
            ))
            await session.commit()

    def _track(username: str) -> None:
        created_usernames.append(username)

    yield _track

    async with session_maker() as session:
        for username in created_usernames:
            obj = (await session.execute(
                select(User).where(User.username == username)
            )).scalar_one_or_none()
            if obj is not None:
                await session.delete(obj)
        await session.commit()


async def test_signup_without_email(client, signup_env):
    """Signup sans email -> 201, email null cote sortie + via GET /me (PAS le
    synthetique {username}@sportapp.com)."""
    signup_env("nomail_user")
    resp = await client.post("/api/v1/signup", json={
        "username": "nomail_user", "password": "supersecret",
    })
    assert resp.status_code == 201, f"Expected 201, got {resp.status_code}: {resp.text}"
    body = resp.json()
    assert body["username"] == "nomail_user"
    assert body["email"] is None

    # Login = username -> GET /me : email null (plus de synthetique).
    headers = await login_headers(client, "nomail_user", "supersecret")
    me = await client.get("/api/v1/me", headers=headers)
    assert me.status_code == 200
    me_body = me.json()
    assert me_body["email"] is None
    assert me_body["email"] != "nomail_user@sportapp.com"


async def test_signup_with_email(client, signup_env):
    """Signup avec email -> 201, email stocke + relu via GET /me."""
    signup_env("withmail_user")
    resp = await client.post("/api/v1/signup", json={
        "username": "withmail_user", "password": "supersecret",
        "email": "with@example.com",
    })
    assert resp.status_code == 201, f"Expected 201, got {resp.status_code}: {resp.text}"
    assert resp.json()["email"] == "with@example.com"

    # Persistance : relu via GET /me apres login par username.
    headers = await login_headers(client, "withmail_user", "supersecret")
    me = await client.get("/api/v1/me", headers=headers)
    assert me.status_code == 200
    assert me.json()["email"] == "with@example.com"


async def test_login_is_username_not_email(client, signup_env):
    """Login = username (invariant de la tache). L'email n'est PAS un
    identifiant de connexion : se logger avec l'email en `username` -> 401,
    se logger avec le vrai username -> 200. Garde-fou pour la decision
    'email optionnel, login inchange'."""
    signup_env("loginuser")
    resp = await client.post("/api/v1/signup", json={
        "username": "loginuser", "password": "supersecret",
        "email": "login@example.com",
    })
    assert resp.status_code == 201, f"Expected 201, got {resp.status_code}: {resp.text}"

    form = {"Content-Type": "application/x-www-form-urlencoded"}

    # Login avec l'email comme username -> rejete (l'email n'authentifie pas).
    by_email = await client.post(
        "/api/v1/token",
        data={"username": "login@example.com", "password": "supersecret"},
        headers=form,
    )
    assert by_email.status_code == 401, (
        f"L'email ne doit pas etre un identifiant de login, got {by_email.status_code}"
    )

    # Login avec le username -> OK.
    by_username = await client.post(
        "/api/v1/token",
        data={"username": "loginuser", "password": "supersecret"},
        headers=form,
    )
    assert by_username.status_code == 200, f"Login username doit marcher: {by_username.text}"
    assert "access_token" in by_username.json()
