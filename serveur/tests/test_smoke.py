"""Smoke tests basiques — valide que la chaîne pytest + httpx + ASGI fonctionne.

Aucune dépendance DB ici (T1.1.a). Les tests qui touchent la DB arrivent en T1.1.b.
"""
async def test_openapi_returns_json(client):
    """GET /openapi.json doit renvoyer un JSON valide avec au moins le titre de l'app."""
    response = await client.get("/openapi.json")
    assert response.status_code == 200
    data = response.json()
    assert "openapi" in data
    assert "paths" in data
    # Sanity check : on a bien des routes enregistrees
    assert len(data["paths"]) > 10


async def test_token_helper_serves_html(client):
    """GET /token-helper doit renvoyer la page HTML statique (helper login Swagger)."""
    response = await client.get("/token-helper")
    assert response.status_code == 200
    assert "text/html" in response.headers.get("content-type", "")


async def test_healthz_returns_status_structure(client):
    """GET /healthz doit renvoyer 200 + structure {status, db, ts} sans auth.

    NB : on ne valide pas la valeur de `db` ("ok"/"ko") car en test env
    l'endpoint utilise `AsyncSessionLocal` direct (prod DB URL, peut varier).
    Seule la structure est garantie.
    """
    response = await client.get("/healthz")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["db"] in ("ok", "ko")
    assert "ts" in data
