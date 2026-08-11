"""Tests pour l'entite Quote (citations motivantes user-scoped).

Couvre :
- CRUD canonique : PUT (create + update), GET all, GET by uuid, DELETE
- bulk upsert
- ownership cross-user : 403 sur upsert d'un uuid d'autrui, 404 sur GET/DELETE
"""
import uuid

from .conftest import login_headers


async def test_put_quote_create_returns_200(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "Just do it.", "author": "Nike"},
        headers=headers,
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["uuid"] == u
    assert data["text"] == "Just do it."
    assert data["author"] == "Nike"
    assert "userId" in data


async def test_put_quote_uuid_mismatch_returns_400(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": str(uuid.uuid4()), "text": "Mismatch"},
        headers=headers,
    )
    assert r.status_code == 400, r.text


async def test_get_all_quotes_lists_own(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "No pain no gain"},
        headers=headers,
    )
    r = await client.get("/api/v1/quotes", headers=headers)
    assert r.status_code == 200, r.text
    uuids = [q["uuid"] for q in r.json()]
    assert u in uuids


async def test_get_quote_by_uuid(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "Stay hungry", "author": None},
        headers=headers,
    )
    r = await client.get(f"/api/v1/quotes/{u}", headers=headers)
    assert r.status_code == 200, r.text
    assert r.json()["text"] == "Stay hungry"


async def test_bulk_upsert_quotes(client):
    headers = await login_headers(client, "testuser", "testpass")
    u1, u2 = str(uuid.uuid4()), str(uuid.uuid4())
    r = await client.put(
        "/api/v1/quotes/bulk",
        json=[
            {"uuid": u1, "text": "Quote one"},
            {"uuid": u2, "text": "Quote two", "author": "Someone"},
        ],
        headers=headers,
    )
    assert r.status_code == 200, r.text
    returned = {q["uuid"] for q in r.json()}
    assert {u1, u2}.issubset(returned)


async def test_delete_quote(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "To delete"},
        headers=headers,
    )
    r = await client.delete(f"/api/v1/quotes/{u}", headers=headers)
    assert r.status_code == 200, r.text

    # Plus accessible apres suppression
    r2 = await client.get(f"/api/v1/quotes/{u}", headers=headers)
    assert r2.status_code == 404


async def test_quote_cross_user_upsert_forbidden(client):
    """Un autre user ne peut pas ecraser une citation possedee par testuser."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    u = str(uuid.uuid4())
    r = await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "Owned by A"},
        headers=headers_a,
    )
    assert r.status_code == 200, r.text

    # B tente d'ecraser le meme uuid -> 403
    r2 = await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "Hijack by B"},
        headers=headers_b,
    )
    assert r2.status_code == 403, r2.text


async def test_quote_cross_user_get_not_found(client):
    """GET sur une citation d'autrui renvoie 404 (pas de fuite cross-user)."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    u = str(uuid.uuid4())
    await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "A private quote"},
        headers=headers_a,
    )
    r = await client.get(f"/api/v1/quotes/{u}", headers=headers_b)
    assert r.status_code == 404, r.text


async def test_quote_cross_user_delete_not_found(client):
    """DELETE sur une citation d'autrui renvoie 404 et ne la supprime pas (fix 49084c1)."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    u = str(uuid.uuid4())
    await client.put(
        f"/api/v1/quotes/{u}",
        json={"uuid": u, "text": "A's quote, hands off"},
        headers=headers_a,
    )

    # B tente de supprimer la citation de A -> 404 (securite par obscurite)
    r = await client.delete(f"/api/v1/quotes/{u}", headers=headers_b)
    assert r.status_code == 404, r.text

    # La citation existe toujours pour son proprietaire (rien n'a ete supprime)
    r_owner = await client.delete(f"/api/v1/quotes/{u}", headers=headers_a)
    assert r_owner.status_code == 200, r_owner.text
