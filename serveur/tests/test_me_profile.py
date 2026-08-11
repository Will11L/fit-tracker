"""Tests endpoint PATCH /api/v1/me/profile (self-only update firstName/lastName).

2026-05-11 : feature B1 onboarding (Welcome step "How should we call you?").
"""
from .conftest import login_headers


async def test_patch_me_profile_updates_first_name(client):
    """User authentifié patche son firstName -> 200 + valeur reflétée dans /me."""
    headers = await login_headers(client, "testuser", "testpass")

    response = await client.patch(
        "/api/v1/me/profile",
        json={"firstName": "Will"},
        headers=headers,
    )
    assert response.status_code == 200, f"Expected 200, got {response.status_code}: {response.text}"
    body = response.json()
    assert body["firstName"] == "Will"
    assert body["username"] == "testuser"

    # Cohérence : /me retourne la nouvelle valeur
    me_response = await client.get("/api/v1/me", headers=headers)
    assert me_response.status_code == 200
    assert me_response.json()["firstName"] == "Will"


async def test_patch_me_profile_partial_update(client):
    """PATCH partiel : envoyer juste firstName ne touche pas lastName."""
    headers = await login_headers(client, "otheruser", "otherpass")

    # Set initial state
    await client.patch(
        "/api/v1/me/profile",
        json={"firstName": "Bob", "lastName": "Smith"},
        headers=headers,
    )

    # Update only firstName
    response = await client.patch(
        "/api/v1/me/profile",
        json={"firstName": "Robert"},
        headers=headers,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["firstName"] == "Robert"
    assert body["lastName"] == "Smith"  # préservé


async def test_patch_me_profile_requires_auth(client):
    """Sans token -> 401."""
    response = await client.patch(
        "/api/v1/me/profile",
        json={"firstName": "Anonymous"},
    )
    assert response.status_code == 401


async def test_patch_me_profile_email_persists(client):
    """Editer l'email -> persiste apres reload (GET /me le renvoie). Le vrai
    email remplace l'ancien synthetique {username}@sportapp.com (2026-06-06)."""
    headers = await login_headers(client, "testuser", "testpass")

    response = await client.patch(
        "/api/v1/me/profile",
        json={"email": "will@example.com"},
        headers=headers,
    )
    assert response.status_code == 200, f"Expected 200, got {response.status_code}: {response.text}"
    assert response.json()["email"] == "will@example.com"

    # Reload : /me renvoie le vrai email, plus jamais le synthetique.
    me = await client.get("/api/v1/me", headers=headers)
    assert me.status_code == 200
    body = me.json()
    assert body["email"] == "will@example.com"
    assert body["email"] != "testuser@sportapp.com"


async def test_me_returns_bio_fields(client):
    """GET /me renvoie la bio (birthDate/sex/heightCm/weightKg) -- fix du bug
    'bio a — au reload' (ces champs etaient absents du dict /me)."""
    headers = await login_headers(client, "testuser", "testpass")

    patch = await client.patch(
        "/api/v1/me/profile",
        json={"birthDate": "1995-03-12", "sex": "MALE", "heightCm": 180.0, "weightKg": 75.0},
        headers=headers,
    )
    assert patch.status_code == 200, f"Expected 200, got {patch.status_code}: {patch.text}"

    me = await client.get("/api/v1/me", headers=headers)
    assert me.status_code == 200
    body = me.json()
    assert body["birthDate"] == "1995-03-12"
    assert body["sex"] == "MALE"
    assert body["heightCm"] == 180.0
    assert body["weightKg"] == 75.0
