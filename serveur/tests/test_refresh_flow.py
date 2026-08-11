"""Flux des refresh tokens : rotation, reuse detection, logout.

Regression 2026-06-25 : `find_token` faisait un scan bcrypt O(N) sur toute la
table `refresh_tokens` (event loop sature, ~2 min de hang quand la table
grossit). Passe a un lookup sha256 indexe O(1). Ces tests verrouillent le
comportement fonctionnel du flux (le gain de perf, lui, vient de l'index).
"""


async def _login(client, username: str, password: str) -> dict:
    resp = await client.post(
        "/api/v1/token",
        data={"username": username, "password": password},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    assert resp.status_code == 200, resp.text
    return resp.json()


async def test_refresh_rotation_and_reuse_detection(client):
    """Rotation au /refresh + reuse detection sur un token deja revoke."""
    tokens = await _login(client, "testuser", "testpass")
    r1 = tokens["refresh_token"]

    # Rotation : r1 -> nouveau pair (r2), r1 est revoke.
    resp = await client.post("/api/v1/refresh", json={"refresh_token": r1})
    assert resp.status_code == 200, resp.text
    r2 = resp.json()["refresh_token"]
    assert r2 and r2 != r1

    # Reuse de r1 (deja revoke) -> 401 + reuse detection (revoke tout le user).
    resp = await client.post("/api/v1/refresh", json={"refresh_token": r1})
    assert resp.status_code == 401

    # r2 a ete revoke par la reuse detection -> 401.
    resp = await client.post("/api/v1/refresh", json={"refresh_token": r2})
    assert resp.status_code == 401


async def test_logout_then_refresh_rejected(client):
    """Logout revoke le refresh : il n'est plus echangeable."""
    tokens = await _login(client, "otheruser", "otherpass")
    r = tokens["refresh_token"]

    resp = await client.post("/api/v1/logout", json={"refresh_token": r})
    assert resp.status_code == 204

    resp = await client.post("/api/v1/refresh", json={"refresh_token": r})
    assert resp.status_code == 401


async def test_unknown_token_refresh_401_logout_204(client):
    """Token inconnu : /refresh -> 401, /logout -> 204 (idempotent)."""
    resp = await client.post("/api/v1/refresh", json={"refresh_token": "bogus-not-a-real-token"})
    assert resp.status_code == 401

    resp = await client.post("/api/v1/logout", json={"refresh_token": "bogus-not-a-real-token"})
    assert resp.status_code == 204
