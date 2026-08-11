"""Repro du HTTP 500 sur PUT /task-checks/{uuid} observe S21+ 2026-05-12.

Payload Android typique :
{
  "uuid": "...",
  "userId": 1,
  "taskUUID": "...",
  "occurrenceDate": "2026-05-12",
  "isChecked": true,
  "checkedAt": "2026-05-12T14:57:36.503540Z",
  "synced": false,
  "pendingDeletion": false,
  "updatedAt": "2026-05-12T14:57:36.505046Z"
}

Hypothese 1 : FK task_uuid orpheline -> IntegrityError -> 500
Hypothese 2 : trigger task_checks crash
"""
import uuid

from .conftest import login_headers


async def test_upsert_task_check_fk_orphan_returns_4xx_not_500(client):
    """Si task_uuid n'existe pas, doit retourner 4xx (404 ou 400) pas 500."""
    headers = await login_headers(client, "testuser", "testpass")

    orphan_task_uuid = str(uuid.uuid4())  # uuid bidon, pas de Task associee
    check_uuid = str(uuid.uuid4())

    r = await client.put(
        f"/api/v1/task-checks/{check_uuid}",
        json={
            "uuid": check_uuid,
            "taskUUID": orphan_task_uuid,
            "occurrenceDate": "2026-05-12",
            "isChecked": True,
            "checkedAt": "2026-05-12T14:57:36.503540Z",
            "updatedAt": "2026-05-12T14:57:36.505046Z",
        },
        headers=headers,
    )

    # Reveler le 500 : c'est ce qu'on suspecte
    assert r.status_code != 500, f"500 reproduit : {r.text}"


async def test_upsert_task_check_happy_path(client):
    """Happy path : task existe + check insere -> 200."""
    headers = await login_headers(client, "testuser", "testpass")

    # Cree une periode puis une tache DAILY (pour reusir la FK)
    period_uuid = str(uuid.uuid4())
    rp_r = await client.put(
        f"/api/v1/routine-periods/{period_uuid}",
        json={"uuid": period_uuid, "name": "Morning", "startTime": "06:30", "endTime": "09:00", "order": 1},
        headers=headers,
    )
    assert rp_r.status_code == 200, rp_r.text

    task_uuid = str(uuid.uuid4())
    t_r = await client.put(
        f"/api/v1/tasks/{task_uuid}",
        json={
            "uuid": task_uuid,
            "title": "Stretch",
            "isActive": True,
            "order": 1,
            "recurrenceKind": "DAILY",
            "periodUUID": period_uuid,
            "recurrenceStartDate": "2026-05-01",
        },
        headers=headers,
    )
    assert t_r.status_code == 200, t_r.text

    check_uuid = str(uuid.uuid4())
    c_r = await client.put(
        f"/api/v1/task-checks/{check_uuid}",
        json={
            "uuid": check_uuid,
            "taskUUID": task_uuid,
            "occurrenceDate": "2026-05-12",
            "isChecked": True,
            "checkedAt": "2026-05-12T14:57:36.503540Z",
            "updatedAt": "2026-05-12T14:57:36.505046Z",
        },
        headers=headers,
    )
    assert c_r.status_code == 200, f"happy path failed : {c_r.text}"
