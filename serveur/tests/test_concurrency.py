"""Tests last-write-wins (optimistic concurrency control) — 2026-05-07.

Quand un client push une row avec `updated_at` plus ancien que la version
serveur, le serveur skip silencieusement et retourne la version serveur
(la plus recente). Garantit la semantique last-write-wins symetrique avec
le merge cote client (`SyncMergeOps.mergeFromRemote`).

Sans ce check, le serveur faisait du last-PUSH-wins : si 2 devices modifient
la meme row hors-ligne, le dernier a push ecrasait l'autre meme s'il avait
une version plus ancienne (lost update).

Cible Exercise (Type A user-scoped, squelette canonique). Le check est applique
uniformement aux 20 CRUDs single-upsert via le helper `is_payload_stale`.
"""
import uuid

from .conftest import login_headers


def _exercise_payload(u: str, name: str, updated_at: str | None) -> dict:
    return {
        "uuid": u,
        "name": name,
        "description": None,
        "instructions": None,
        "recommendedSets": None,
        "recommendedReps": None,
        "durationInSeconds": None,
        "restTimeSeconds": None,
        "gifUrl": None,
        "isFavorite": False,
        "lastDone": None,
        "updatedAt": updated_at,
    }


async def test_upsert_with_older_updated_at_skips_overwrite(client):
    """PUT avec updated_at < existing.updated_at → skip + return existing.

    Scenario lost-update :
    1. Client crée Exercise V1 avec updatedAt=T2 (recent).
    2. Client tente de PUT V1' avec updatedAt=T1 (T1 < T2, ancien) et name=Stale.
    3. Serveur skip → retourne V1 (name original preserved).
    """
    headers = await login_headers(client, "testuser", "testpass")
    ex_uuid = str(uuid.uuid4())

    # 1. Crée la row avec updatedAt recent
    r1 = await client.put(
        f"/api/v1/exercises/{ex_uuid}",
        json=_exercise_payload(ex_uuid, name=f"Original_{ex_uuid[:6]}", updated_at="2026-05-07T12:00:00.000000Z"),
        headers=headers,
    )
    assert r1.status_code == 200, r1.text

    # 2. Tente d'ecraser avec updatedAt PLUS ANCIEN
    r2 = await client.put(
        f"/api/v1/exercises/{ex_uuid}",
        json=_exercise_payload(ex_uuid, name=f"Stale_{ex_uuid[:6]}", updated_at="2026-05-07T11:00:00.000000Z"),
        headers=headers,
    )
    assert r2.status_code == 200, r2.text
    # 3. Le nom doit RESTER "Original" (le payload "Stale" a ete skippe)
    assert r2.json()["name"].startswith("Original_"), \
        f"last-write-wins viole : payload plus ancien ne doit pas ecraser. Got name={r2.json()['name']}"

    # 4. Confirme via GET
    r3 = await client.get(f"/api/v1/exercises/{ex_uuid}", headers=headers)
    assert r3.status_code == 200
    assert r3.json()["name"].startswith("Original_")


async def test_upsert_with_newer_updated_at_overwrites(client):
    """PUT avec updated_at > existing.updated_at → ecrase normalement.

    Garde-fou : le check ne doit pas casser le cas standard last-write.
    """
    headers = await login_headers(client, "testuser", "testpass")
    ex_uuid = str(uuid.uuid4())

    r1 = await client.put(
        f"/api/v1/exercises/{ex_uuid}",
        json=_exercise_payload(ex_uuid, name=f"Original_{ex_uuid[:6]}", updated_at="2026-05-07T11:00:00.000000Z"),
        headers=headers,
    )
    assert r1.status_code == 200

    r2 = await client.put(
        f"/api/v1/exercises/{ex_uuid}",
        json=_exercise_payload(ex_uuid, name=f"Updated_{ex_uuid[:6]}", updated_at="2026-05-07T12:00:00.000000Z"),
        headers=headers,
    )
    assert r2.status_code == 200
    assert r2.json()["name"].startswith("Updated_")


async def test_upsert_with_no_updated_at_falls_back_to_overwrite(client):
    """PUT sans updated_at (None) → ecrase (compat retro).

    Le check ne s'active que si les 2 cotes ont un `updated_at` comparable.
    Si payload.updated_at = None (legacy / migration), on retombe sur le
    comportement pre-fix (ecrase).
    """
    headers = await login_headers(client, "testuser", "testpass")
    ex_uuid = str(uuid.uuid4())

    r1 = await client.put(
        f"/api/v1/exercises/{ex_uuid}",
        json=_exercise_payload(ex_uuid, name=f"WithTs_{ex_uuid[:6]}", updated_at="2026-05-07T11:00:00.000000Z"),
        headers=headers,
    )
    assert r1.status_code == 200

    r2 = await client.put(
        f"/api/v1/exercises/{ex_uuid}",
        json=_exercise_payload(ex_uuid, name=f"NoTs_{ex_uuid[:6]}", updated_at=None),
        headers=headers,
    )
    assert r2.status_code == 200
    # Fallback : ecrase
    assert r2.json()["name"].startswith("NoTs_")
