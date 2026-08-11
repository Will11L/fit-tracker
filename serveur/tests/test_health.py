"""Tests pour le domaine Santé / Health Connect V1 (2026-06-17).

Couvre les 3 entités user-scoped (health_step_counts, health_metrics,
health_goals) : round-trip PUT -> GET, bulk upsert, delete, isolation
cross-user (403 upsert / 404 get), uuid mismatch, last-write-wins.
"""
import uuid

from .conftest import login_headers


# -------------------- health_step_counts --------------------

async def _put_step(client, headers, u, **extra):
    body = {"uuid": u, "date": "2026-06-17", "bucketStart": "14:00", "steps": 1200}
    body.update(extra)
    return await client.put(f"/api/v1/health-step-counts/{u}", json=body, headers=headers)


async def test_step_count_round_trip(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_step(client, headers, u)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["date"] == "2026-06-17"
    assert data["bucketStart"] == "14:00"
    assert data["steps"] == 1200
    assert data["userId"] >= 1

    g = await client.get(f"/api/v1/health-step-counts/{u}", headers=headers)
    assert g.status_code == 200, g.text
    assert g.json()["steps"] == 1200


async def test_step_count_upsert_refreshes_bucket(client):
    """Near-real-time : ré-upsert du même bucket met à jour steps (compteur courant)."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await _put_step(client, headers, u, steps=500)
    r = await _put_step(client, headers, u, steps=1800)
    assert r.status_code == 200, r.text
    assert r.json()["steps"] == 1800


async def test_step_count_list_and_delete(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await _put_step(client, headers, u)

    lst = await client.get("/api/v1/health-step-counts", headers=headers)
    assert lst.status_code == 200, lst.text
    assert any(x["uuid"] == u for x in lst.json())

    d = await client.delete(f"/api/v1/health-step-counts/{u}", headers=headers)
    assert d.status_code == 200, d.text
    g = await client.get(f"/api/v1/health-step-counts/{u}", headers=headers)
    assert g.status_code == 404


async def test_step_count_bulk_upsert(client):
    headers = await login_headers(client, "testuser", "testpass")
    u1, u2 = str(uuid.uuid4()), str(uuid.uuid4())
    items = [
        {"uuid": u1, "date": "2026-06-17", "bucketStart": "08:00", "steps": 300},
        {"uuid": u2, "date": "2026-06-17", "bucketStart": "09:00", "steps": 900},
    ]
    r = await client.put("/api/v1/health-step-counts/bulk", json=items, headers=headers)
    assert r.status_code == 200, r.text
    assert {x["uuid"] for x in r.json()} == {u1, u2}


async def test_step_count_uuid_mismatch_400(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    body = {"uuid": str(uuid.uuid4()), "date": "2026-06-17", "bucketStart": "10:00", "steps": 1}
    r = await client.put(f"/api/v1/health-step-counts/{u}", json=body, headers=headers)
    assert r.status_code == 400, r.text


async def test_step_count_cross_user_isolation(client):
    """Un autre user ne peut ni lire (404) ni écraser (403) le bucket d'un tiers."""
    owner = await login_headers(client, "testuser", "testpass")
    other = await login_headers(client, "otheruser", "otherpass")
    u = str(uuid.uuid4())
    await _put_step(client, owner, u)

    g = await client.get(f"/api/v1/health-step-counts/{u}", headers=other)
    assert g.status_code == 404, g.text

    body = {"uuid": u, "date": "2026-06-17", "bucketStart": "14:00", "steps": 9999}
    up = await client.put(f"/api/v1/health-step-counts/{u}", json=body, headers=other)
    assert up.status_code == 403, up.text


async def test_step_count_requires_auth(client):
    u = str(uuid.uuid4())
    r = await client.get(f"/api/v1/health-step-counts/{u}")
    assert r.status_code == 401


# -------------------- health_metrics --------------------

async def _put_metric(client, headers, u, **extra):
    body = {"uuid": u, "type": "HEART_RATE", "value": 72.0, "unit": "bpm", "date": "2026-06-17"}
    body.update(extra)
    return await client.put(f"/api/v1/health-metrics/{u}", json=body, headers=headers)


async def test_metric_round_trip(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_metric(client, headers, u, startTime="07:30")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["type"] == "HEART_RATE"
    assert data["value"] == 72.0
    assert data["unit"] == "bpm"
    assert data["startTime"] == "07:30"

    g = await client.get(f"/api/v1/health-metrics/{u}", headers=headers)
    assert g.status_code == 200, g.text
    assert g.json()["type"] == "HEART_RATE"


async def test_metric_omitting_start_time_is_null(client):
    """startTime est optionnel (mesures journalières comme SLEEP/DISTANCE)."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_metric(client, headers, u, type="SLEEP", value=431.0, unit="min")
    assert r.status_code == 200, r.text
    assert r.json()["startTime"] is None


async def test_metric_bulk_and_delete(client):
    headers = await login_headers(client, "testuser", "testpass")
    u1, u2 = str(uuid.uuid4()), str(uuid.uuid4())
    items = [
        {"uuid": u1, "type": "DISTANCE", "value": 5.3, "unit": "km", "date": "2026-06-17"},
        {"uuid": u2, "type": "ACTIVE_CALORIES", "value": 320.0, "unit": "kcal", "date": "2026-06-17"},
    ]
    r = await client.put("/api/v1/health-metrics/bulk", json=items, headers=headers)
    assert r.status_code == 200, r.text
    assert {x["uuid"] for x in r.json()} == {u1, u2}

    d = await client.delete(f"/api/v1/health-metrics/{u1}", headers=headers)
    assert d.status_code == 200, d.text


async def test_metric_cross_user_isolation(client):
    owner = await login_headers(client, "testuser", "testpass")
    other = await login_headers(client, "otheruser", "otherpass")
    u = str(uuid.uuid4())
    await _put_metric(client, owner, u)

    g = await client.get(f"/api/v1/health-metrics/{u}", headers=other)
    assert g.status_code == 404, g.text

    body = {"uuid": u, "type": "HEART_RATE", "value": 200.0, "unit": "bpm", "date": "2026-06-17"}
    up = await client.put(f"/api/v1/health-metrics/{u}", json=body, headers=other)
    assert up.status_code == 403, up.text


# -------------------- health_goals --------------------

async def _put_goal(client, headers, u, **extra):
    body = {"uuid": u, "type": "STEPS", "target": 10000.0, "effectiveFrom": "2026-06-17"}
    body.update(extra)
    return await client.put(f"/api/v1/health-goals/{u}", json=body, headers=headers)


async def test_goal_round_trip(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_goal(client, headers, u)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["type"] == "STEPS"
    assert data["target"] == 10000.0
    assert data["effectiveFrom"] == "2026-06-17"

    g = await client.get(f"/api/v1/health-goals/{u}", headers=headers)
    assert g.status_code == 200, g.text
    assert g.json()["target"] == 10000.0


async def test_goal_update_changes_target(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await _put_goal(client, headers, u, target=8000.0)
    r = await _put_goal(client, headers, u, target=12000.0)
    assert r.status_code == 200, r.text
    assert r.json()["target"] == 12000.0


async def test_goal_last_write_wins(client):
    """Un PUT avec updatedAt plus ancien ne doit pas écraser l'objectif en base."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r1 = await _put_goal(client, headers, u, target=10000.0, updatedAt="2026-06-17T10:00:00Z")
    assert r1.status_code == 200, r1.text
    r2 = await _put_goal(client, headers, u, target=5000.0, updatedAt="2026-06-17T09:00:00Z")
    assert r2.status_code == 200, r2.text
    assert r2.json()["target"] == 10000.0, "older payload must not overwrite"


async def test_goal_bulk_and_delete(client):
    headers = await login_headers(client, "testuser", "testpass")
    u1, u2 = str(uuid.uuid4()), str(uuid.uuid4())
    items = [
        {"uuid": u1, "type": "STEPS", "target": 10000.0, "effectiveFrom": "2026-06-01"},
        {"uuid": u2, "type": "STEPS", "target": 11000.0, "effectiveFrom": "2026-06-17"},
    ]
    r = await client.put("/api/v1/health-goals/bulk", json=items, headers=headers)
    assert r.status_code == 200, r.text
    assert {x["uuid"] for x in r.json()} == {u1, u2}

    d = await client.delete(f"/api/v1/health-goals/{u1}", headers=headers)
    assert d.status_code == 200, d.text


async def test_goal_cross_user_isolation(client):
    owner = await login_headers(client, "testuser", "testpass")
    other = await login_headers(client, "otheruser", "otherpass")
    u = str(uuid.uuid4())
    await _put_goal(client, owner, u)

    g = await client.get(f"/api/v1/health-goals/{u}", headers=other)
    assert g.status_code == 404, g.text

    body = {"uuid": u, "type": "STEPS", "target": 1.0, "effectiveFrom": "2026-06-17"}
    up = await client.put(f"/api/v1/health-goals/{u}", json=body, headers=other)
    assert up.status_code == 403, up.text
