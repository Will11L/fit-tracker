"""Tests pour les rappels de RoutinePeriod (reminder_before_start/end_minutes).

Feature 2026-06-08 : rappel des routines avant le début et/ou avant la fin.
Couvre le contrat wire des 2 champs nullable :
- round-trip PUT -> GET (valeur + 0 "pile à l'heure" + null désactivé) ;
- omission des champs au wire (Gson serializeNulls off) -> pas de 422, lus null ;
- last-write-wins : un PUT plus ancien ne doit pas écraser les rappels en base.
"""
import uuid

from .conftest import login_headers


async def _put_period(client, headers, u, **extra):
    body = {
        "uuid": u,
        "name": "Morning",
        "startTime": "06:00",
        "endTime": "09:00",
    }
    body.update(extra)
    return await client.put(f"/api/v1/routine-periods/{u}", json=body, headers=headers)


async def test_put_period_round_trips_both_reminders(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_period(
        client, headers, u,
        reminderBeforeStartMinutes=15,
        reminderBeforeEndMinutes=30,
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["reminderBeforeStartMinutes"] == 15
    assert data["reminderBeforeEndMinutes"] == 30

    g = await client.get(f"/api/v1/routine-periods/{u}", headers=headers)
    assert g.status_code == 200, g.text
    assert g.json()["reminderBeforeStartMinutes"] == 15
    assert g.json()["reminderBeforeEndMinutes"] == 30


async def test_put_period_reminder_zero_is_preserved(client):
    """0 = "pile à l'heure" doit rester 0 (et non être confondu avec null)."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_period(client, headers, u, reminderBeforeStartMinutes=0)
    assert r.status_code == 200, r.text
    assert r.json()["reminderBeforeStartMinutes"] == 0
    # end non fourni -> null (rappel de fin opt-in)
    assert r.json()["reminderBeforeEndMinutes"] is None


async def test_put_period_omitting_reminders_is_not_422(client):
    """Wire Gson omet les null -> les champs absents doivent valider et être null."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await _put_period(client, headers, u)  # aucun champ reminder
    assert r.status_code == 200, r.text
    assert r.json()["reminderBeforeStartMinutes"] is None
    assert r.json()["reminderBeforeEndMinutes"] is None


async def test_put_period_update_changes_reminders(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    await _put_period(client, headers, u, reminderBeforeStartMinutes=15)
    # ré-upsert avec une autre valeur -> écrasement (pas de updatedAt = plus récent)
    r = await _put_period(client, headers, u, reminderBeforeStartMinutes=60)
    assert r.status_code == 200, r.text
    assert r.json()["reminderBeforeStartMinutes"] == 60


async def test_put_period_reminders_last_write_wins(client):
    """Un PUT avec updatedAt plus ancien ne doit pas écraser les rappels en base."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    # version "récente" en base (updatedAt T2)
    r1 = await _put_period(
        client, headers, u,
        reminderBeforeStartMinutes=15,
        updatedAt="2026-06-08T10:00:00Z",
    )
    assert r1.status_code == 200, r1.text
    # PUT plus ancien (T1) avec une autre valeur -> doit être ignoré (stale)
    r2 = await _put_period(
        client, headers, u,
        reminderBeforeStartMinutes=99,
        updatedAt="2026-06-08T09:00:00Z",
    )
    assert r2.status_code == 200, r2.text
    assert r2.json()["reminderBeforeStartMinutes"] == 15, "older payload must not overwrite"
