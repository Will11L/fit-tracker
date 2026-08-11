"""Tests pour l'hydratation (water_intakes, 2026-07-05).

Type A user-scoped : round-trip PUT -> GET, re-upsert, bulk, delete, isolation
cross-user (403 upsert / 404 get), uuid mismatch, validation amount_ml > 0,
last-write-wins, préservation de created_at.

L'objectif journalier WATER_ML est versionné via l'API health_goals existante
(colonne `type` libre, aucun changement de schéma) — cf. test dédié en bas.
"""
import uuid

from .conftest import login_headers


async def _put_intake(client, headers, u, **extra):
    body = {"uuid": u, "date": "2026-07-05", "amountMl": 250}
    body.update(extra)
    return await client.put(f"/api/v1/water-intakes/{u}", json=body, headers=headers)


async def test_water_intake_round_trip(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_intake(client, headers, u, createdAt="2026-07-05T08:15:00Z")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["date"] == "2026-07-05"
    assert data["amountMl"] == 250
    assert data["userId"] >= 1
    assert data["createdAt"] == "2026-07-05T08:15:00.000000Z"

    g = await client.get(f"/api/v1/water-intakes/{u}", headers=headers)
    assert g.status_code == 200, g.text
    assert g.json()["amountMl"] == 250


async def test_water_intake_reupsert_changes_amount(client):
    """Corriger une prise (ex. gorgée finie) : ré-upsert du même uuid met à jour amountMl."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await _put_intake(client, headers, u, amountMl=200)
    r = await _put_intake(client, headers, u, amountMl=500)
    assert r.status_code == 200, r.text
    assert r.json()["amountMl"] == 500


async def test_water_intake_list_and_delete(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await _put_intake(client, headers, u)

    lst = await client.get("/api/v1/water-intakes", headers=headers)
    assert lst.status_code == 200, lst.text
    assert any(x["uuid"] == u for x in lst.json())

    d = await client.delete(f"/api/v1/water-intakes/{u}", headers=headers)
    assert d.status_code == 200, d.text
    g = await client.get(f"/api/v1/water-intakes/{u}", headers=headers)
    assert g.status_code == 404


async def test_water_intake_bulk_upsert(client):
    """Plusieurs prises du jour en un push (SUM côté client = total du jour)."""
    headers = await login_headers(client, "testuser", "testpass")
    u1, u2 = str(uuid.uuid4()), str(uuid.uuid4())
    items = [
        {"uuid": u1, "date": "2026-07-05", "amountMl": 330},
        {"uuid": u2, "date": "2026-07-05", "amountMl": 500},
    ]
    r = await client.put("/api/v1/water-intakes/bulk", json=items, headers=headers)
    assert r.status_code == 200, r.text
    assert {x["uuid"] for x in r.json()} == {u1, u2}


async def test_water_intake_uuid_mismatch_400(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    body = {"uuid": str(uuid.uuid4()), "date": "2026-07-05", "amountMl": 100}
    r = await client.put(f"/api/v1/water-intakes/{u}", json=body, headers=headers)
    assert r.status_code == 400, r.text


async def test_water_intake_rejects_non_positive_amount(client):
    """amount_ml doit être strictement positif (Field gt=0) -> 422."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_intake(client, headers, u, amountMl=0)
    assert r.status_code == 422, r.text


async def test_water_intake_last_write_wins(client):
    """Un PUT avec updatedAt plus ancien ne doit pas écraser la prise en base."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r1 = await _put_intake(client, headers, u, amountMl=250, updatedAt="2026-07-05T10:00:00Z")
    assert r1.status_code == 200, r1.text
    r2 = await _put_intake(client, headers, u, amountMl=999, updatedAt="2026-07-05T09:00:00Z")
    assert r2.status_code == 200, r2.text
    assert r2.json()["amountMl"] == 250, "older payload must not overwrite"


async def test_water_intake_cross_user_isolation(client):
    """Un autre user ne peut ni lire (404) ni écraser (403) la prise d'un tiers."""
    owner = await login_headers(client, "testuser", "testpass")
    other = await login_headers(client, "otheruser", "otherpass")
    u = str(uuid.uuid4())
    await _put_intake(client, owner, u)

    g = await client.get(f"/api/v1/water-intakes/{u}", headers=other)
    assert g.status_code == 404, g.text

    body = {"uuid": u, "date": "2026-07-05", "amountMl": 9999}
    up = await client.put(f"/api/v1/water-intakes/{u}", json=body, headers=other)
    assert up.status_code == 403, up.text


async def test_water_intake_requires_auth(client):
    u = str(uuid.uuid4())
    r = await client.get(f"/api/v1/water-intakes/{u}")
    assert r.status_code == 401


# -------------------- Objectif journalier WATER_ML --------------------
# L'objectif d'hydratation est un goal versionné `type` + `target` + `effective_from` :
# il vit dans health_goals (la seule table de goals à cette forme, `type` extensible),
# sans aucun changement de schéma. `target` = ml/jour.

async def _put_water_goal(client, headers, u, **extra):
    body = {"uuid": u, "type": "WATER_ML", "target": 2000.0, "effectiveFrom": "2026-07-05"}
    body.update(extra)
    return await client.put(f"/api/v1/health-goals/{u}", json=body, headers=headers)


async def test_water_ml_goal_round_trip(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_water_goal(client, headers, u)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["type"] == "WATER_ML"
    assert data["target"] == 2000.0
    assert data["effectiveFrom"] == "2026-07-05"

    g = await client.get(f"/api/v1/health-goals/{u}", headers=headers)
    assert g.status_code == 200, g.text
    assert g.json()["type"] == "WATER_ML"


async def test_water_ml_goal_versioning(client):
    """Objectif actif un jour J = max(effective_from <= J) : une nouvelle cible
    plus récente coexiste avec l'ancienne (versionnement)."""
    headers = await login_headers(client, "testuser", "testpass")
    u1, u2 = str(uuid.uuid4()), str(uuid.uuid4())
    await _put_water_goal(client, headers, u1, target=2000.0, effectiveFrom="2026-07-01")
    await _put_water_goal(client, headers, u2, target=2500.0, effectiveFrom="2026-07-05")

    lst = await client.get("/api/v1/health-goals", headers=headers)
    assert lst.status_code == 200, lst.text
    water_goals = {x["uuid"]: x for x in lst.json() if x["type"] == "WATER_ML"}
    assert u1 in water_goals and u2 in water_goals
    assert water_goals[u2]["target"] == 2500.0
