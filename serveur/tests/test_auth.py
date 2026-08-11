"""Tests du flow d'authentification (login + JWT).

T1.1.b (2026-05-06) : 1er test qui touche la DB de test (fittracker_test).
Pre-requis : conftest fixtures `setup_test_db` (autouse) + `client`.
"""
from jose import jwt

from app.settings import settings


async def test_login_returns_jwt_token(client):
    """POST /token avec testuser/testpass -> 200 + JWT decodable contenant user_id + username."""
    response = await client.post(
        "/api/v1/token",
        data={"username": "testuser", "password": "testpass"},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )

    assert response.status_code == 200, f"Expected 200, got {response.status_code}: {response.text}"

    data = response.json()
    assert "access_token" in data
    assert "refresh_token" in data
    assert data.get("token_type") == "bearer"

    # JWT access decodable + claims attendus
    payload = jwt.decode(
        data["access_token"],
        settings.JWT_SECRET_KEY,
        algorithms=[settings.JWT_ALGORITHM],
        audience=settings.JWT_AUD,
        issuer=settings.JWT_ISS,
    )
    assert payload["sub"] == "testuser"
    assert payload["user_id"] is not None
    assert isinstance(payload["user_id"], int)


async def test_login_wrong_password_returns_401(client):
    """Mauvais password -> 401 Unauthorized."""
    response = await client.post(
        "/api/v1/token",
        data={"username": "testuser", "password": "wrongpass"},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    assert response.status_code == 401


async def test_login_unknown_user_returns_401(client):
    """User inexistant -> 401 Unauthorized."""
    response = await client.post(
        "/api/v1/token",
        data={"username": "nonexistent", "password": "whatever"},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    assert response.status_code == 401
