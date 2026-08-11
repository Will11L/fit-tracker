"""Tests bypass ownership cross-user (V2.4 cascade — valide les fixes V2.1).

T1.1.c (2026-05-06) :
- T1.1.c-pattern : Exercise (Type A direct user_id) — 3 tests.
- T1.1.c-bis : 5 entités feuilles cascade — 1 test PUT cross-user par entité.
  - actual_workout_exercise (cascade via actual_workout.user_id)
  - actual_workout_set (cascade via actual_workout_exercise → actual_workout)
  - planned_workout_exercise (cascade via planned_workout.user_id)
  - routine_task_check (cascade via routine_task → routine_period → user)
  - superset_exercise (cascade via superset_group.user_id)

Politique sécurité (CLAUDE.md §8) : un user ne peut pas modifier les entités
d'un autre user. Pour les feuilles cascade, l'ownership est validé via JOIN
parent (le helper `assert_user_owns_X` ou le filtre dans le get_by_uuid).

Comportement attendu (cohérent avec l'audit V2.1) :
- PUT cross-user → 403 (explicit) OU 404 (sécurité par obscurité), selon CRUD.
- Les 2 sont acceptables tant qu'on ne renvoie PAS 200.
"""
import uuid

from .conftest import login_headers


# ============================================================
# Helpers de création (parents + feuilles)
# Chaque helper retourne l'uuid de l'entité créée.
# ============================================================

def _exercise_payload(u: str, name: str = None) -> dict:
    # Name unique par défaut (UNIQUE(user_id, name) posé F8-Q4) — sinon collision entre tests.
    if name is None:
        name = f"Ex_{u[:8]}"
    return {
        "uuid": u, "name": name, "description": None, "instructions": None,
        "recommendedSets": None, "recommendedReps": None, "durationInSeconds": None,
        "restTimeSeconds": None, "gifUrl": None, "isFavorite": False, "lastDone": None,
    }


def _actual_workout_payload(u: str) -> dict:
    return {
        "uuid": u, "name": f"AW_{u[:6]}", "date": "2026-01-15",
        "notes": None, "location": None, "isDone": False,
    }


def _actual_workout_exercise_payload(u: str, aw_uuid: str, ex_uuid: str) -> dict:
    return {
        "uuid": u, "actualWorkoutUUID": aw_uuid, "exerciseUUID": ex_uuid,
        "sets": 3, "reps": "8-12", "phase": "TRAINING", "status": "NOT_STARTED",
        "order": 1, "addedManually": False,
    }


def _actual_workout_set_payload(u: str, awe_uuid: str) -> dict:
    return {
        "uuid": u, "actualWorkoutExerciseUUID": awe_uuid,
        "setOrder": 1, "reps": 10, "weight": 50.0, "isDropset": False,
        "notes": None, "recommendation": None, "status": "NOT_STARTED",
    }


def _planned_workout_payload(u: str) -> dict:
    return {"uuid": u, "name": f"PW_{u[:6]}", "dayOfWeek": "MONDAY"}


def _planned_workout_exercise_payload(u: str, pw_uuid: str, ex_uuid: str) -> dict:
    return {
        "uuid": u, "plannedWorkoutUUID": pw_uuid, "exerciseUUID": ex_uuid,
        "sets": 3, "reps": "8-12", "phase": "TRAINING", "status": "PLANNED",
        "order": 1, "ignored": False,
    }


def _routine_period_payload(u: str) -> dict:
    return {"uuid": u, "name": f"RP_{u[:6]}", "startTime": "06:30", "endTime": "09:00", "order": 1}


def _task_payload(u: str, period_uuid: str) -> dict:
    """Phase 0 (2026-05-12) : task DAILY remplace routine_task."""
    return {
        "uuid": u, "title": f"T_{u[:6]}", "notes": None, "isActive": True, "order": 1,
        "recurrenceKind": "DAILY", "periodUUID": period_uuid,
        "recurrenceStartDate": "2026-01-15",
    }


def _task_check_payload(u: str, task_uuid: str) -> dict:
    """Phase 0 (2026-05-12) : task_check remplace routine_task_check, rename date -> occurrenceDate."""
    return {
        "uuid": u, "taskUUID": task_uuid, "occurrenceDate": "2026-01-15",
        "isChecked": False, "checkedAt": None,
    }


def _superset_group_payload(u: str) -> dict:
    return {"uuid": u, "name": f"SG_{u[:6]}"}


def _superset_exercise_payload(u: str, sg_uuid: str, ex_uuid: str) -> dict:
    return {
        "uuid": u, "supersetGroupUUID": sg_uuid, "exerciseUUID": ex_uuid,
        "orderInGroup": 1,
    }


async def _create(client, headers, path: str, payload: dict, *, uuid_key: str = "uuid") -> str:
    """PUT /api/v1/{path}/{uuid} avec payload, retourne uuid après assertion 200."""
    u = payload[uuid_key]
    r = await client.put(f"/api/v1/{path}/{u}", json=payload, headers=headers)
    assert r.status_code == 200, f"Setup failed PUT /api/v1/{path}/{u}: {r.status_code} {r.text}"
    return u


# ============================================================
# Tests V2.1 ownership direct (Type A user_id)
# ============================================================

async def test_exercise_put_cross_user_returns_403(client):
    """User A crée son exercise, user B PUT le même uuid → 403."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")

    ex_uuid = await _create(client, headers_a, "exercises", _exercise_payload(str(uuid.uuid4())))

    r = await client.put(f"/api/v1/exercises/{ex_uuid}", json=_exercise_payload(ex_uuid, name=f"HACKED_{ex_uuid[:6]}"), headers=headers_b)
    assert r.status_code == 403, f"Expected 403, got {r.status_code}: {r.text}"


async def test_exercise_delete_cross_user_returns_404(client):
    """User A crée son exercise, user B DELETE → 404 (sécurité par obscurité)."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")

    ex_uuid = await _create(client, headers_a, "exercises", _exercise_payload(str(uuid.uuid4())))

    r = await client.delete(f"/api/v1/exercises/{ex_uuid}", headers=headers_b)
    assert r.status_code == 404, f"Expected 404, got {r.status_code}: {r.text}"

    # Verify owner can still delete
    r_owner = await client.delete(f"/api/v1/exercises/{ex_uuid}", headers=headers_a)
    assert r_owner.status_code == 200


async def test_exercise_no_auth_returns_401(client):
    """Sans Authorization header → 401 (politique V1.1)."""
    u = str(uuid.uuid4())
    r = await client.put(f"/api/v1/exercises/{u}", json=_exercise_payload(u))
    assert r.status_code == 401


# ============================================================
# Tests V2.4 cascade ownership (5 entités feuilles)
# Pour chaque entité : user A crée la chaîne, user B essaie PUT → 403/404.
# ============================================================

async def test_actual_workout_exercise_cascade_cross_user(client):
    """V2.4 — actual_workout_exercise cascade via actual_workout.user_id."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")

    aw_uuid = await _create(client, headers_a, "actual-workouts", _actual_workout_payload(str(uuid.uuid4())))
    ex_uuid = await _create(client, headers_a, "exercises", _exercise_payload(str(uuid.uuid4())))
    awe_uuid = await _create(client, headers_a, "actual-workout-exercises",
                              _actual_workout_exercise_payload(str(uuid.uuid4()), aw_uuid, ex_uuid))

    r = await client.put(f"/api/v1/actual-workout-exercises/{awe_uuid}",
                          json=_actual_workout_exercise_payload(awe_uuid, aw_uuid, ex_uuid),
                          headers=headers_b)
    assert r.status_code in [403, 404], f"Expected 403/404, got {r.status_code}: {r.text}"


async def test_actual_workout_set_cascade_cross_user(client):
    """V2.4 — actual_workout_set cascade via actual_workout_exercise → actual_workout."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")

    aw_uuid = await _create(client, headers_a, "actual-workouts", _actual_workout_payload(str(uuid.uuid4())))
    ex_uuid = await _create(client, headers_a, "exercises", _exercise_payload(str(uuid.uuid4())))
    awe_uuid = await _create(client, headers_a, "actual-workout-exercises",
                              _actual_workout_exercise_payload(str(uuid.uuid4()), aw_uuid, ex_uuid))
    aws_uuid = await _create(client, headers_a, "actual-workout-sets",
                              _actual_workout_set_payload(str(uuid.uuid4()), awe_uuid))

    r = await client.put(f"/api/v1/actual-workout-sets/{aws_uuid}",
                          json=_actual_workout_set_payload(aws_uuid, awe_uuid),
                          headers=headers_b)
    assert r.status_code in [403, 404], f"Expected 403/404, got {r.status_code}: {r.text}"


async def test_planned_workout_exercise_cascade_cross_user(client):
    """V2.4 — planned_workout_exercise cascade via planned_workout.user_id."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")

    pw_uuid = await _create(client, headers_a, "planned-workouts", _planned_workout_payload(str(uuid.uuid4())))
    ex_uuid = await _create(client, headers_a, "exercises", _exercise_payload(str(uuid.uuid4())))
    pwe_uuid = await _create(client, headers_a, "planned-workout-exercises",
                              _planned_workout_exercise_payload(str(uuid.uuid4()), pw_uuid, ex_uuid))

    r = await client.put(f"/api/v1/planned-workout-exercises/{pwe_uuid}",
                          json=_planned_workout_exercise_payload(pwe_uuid, pw_uuid, ex_uuid),
                          headers=headers_b)
    assert r.status_code in [403, 404], f"Expected 403/404, got {r.status_code}: {r.text}"


async def test_task_check_cascade_cross_user(client):
    """Phase 0 (2026-05-12) : task_check cascade via task.user_id (unification).

    Remplace l'ancien test routine_task_check (cascade routine_task -> routine_period -> user)
    par le nouveau modele Task unifie : task_check.user_id check direct (politique V2.1).
    """
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")

    rp_uuid = await _create(client, headers_a, "routine-periods", _routine_period_payload(str(uuid.uuid4())))
    t_uuid = await _create(client, headers_a, "tasks", _task_payload(str(uuid.uuid4()), rp_uuid))
    tc_uuid = await _create(client, headers_a, "task-checks",
                             _task_check_payload(str(uuid.uuid4()), t_uuid))

    r = await client.put(f"/api/v1/task-checks/{tc_uuid}",
                          json=_task_check_payload(tc_uuid, t_uuid),
                          headers=headers_b)
    assert r.status_code in [403, 404], f"Expected 403/404, got {r.status_code}: {r.text}"


async def test_superset_exercise_cascade_cross_user(client):
    """V2.4 — superset_exercise cascade via superset_group.user_id."""
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")

    sg_uuid = await _create(client, headers_a, "superset-groups", _superset_group_payload(str(uuid.uuid4())))
    ex_uuid = await _create(client, headers_a, "exercises", _exercise_payload(str(uuid.uuid4())))
    se_uuid = await _create(client, headers_a, "superset-exercises",
                             _superset_exercise_payload(str(uuid.uuid4()), sg_uuid, ex_uuid))

    r = await client.put(f"/api/v1/superset-exercises/{se_uuid}",
                          json=_superset_exercise_payload(se_uuid, sg_uuid, ex_uuid),
                          headers=headers_b)
    assert r.status_code in [403, 404], f"Expected 403/404, got {r.status_code}: {r.text}"
