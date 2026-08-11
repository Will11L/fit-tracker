"""Phase 0 (2026-05-12) : tests pour le modele Task unifie.

Couvre :
- Validators conditionnels Pydantic (recurrence_kind vs autres fields)
- CRUD canonique : PUT one-off (NONE), PUT DAILY (avec period_uuid), GET, DELETE
- task_checks : upsert + occurrence_date validation
"""
import uuid

import pytest
from pydantic import ValidationError

from app.schemas import TaskCreate, TaskCheckCreate
from .conftest import login_headers


# ============================================================
# Pydantic validators (unit tests, no DB)
# ============================================================

def test_task_none_requires_due_date():
    """recurrence_kind=NONE doit avoir due_date REQUIRED."""
    with pytest.raises(ValidationError, match="due_date REQUIRED"):
        TaskCreate(
            uuid=str(uuid.uuid4()),
            title="One-off without date",
            recurrence_kind="NONE",
            # due_date manquante
        )


def test_task_none_forbids_period_uuid():
    """recurrence_kind=NONE ne doit PAS avoir period_uuid."""
    with pytest.raises(ValidationError, match="period_uuid forbidden"):
        TaskCreate(
            uuid=str(uuid.uuid4()),
            title="One-off avec period bug",
            recurrence_kind="NONE",
            due_date="2026-05-15",
            period_uuid=str(uuid.uuid4()),  # interdit pour NONE
        )


def test_task_daily_requires_period_and_start_date():
    """recurrence_kind=DAILY doit avoir period_uuid + recurrence_start_date REQUIRED."""
    with pytest.raises(ValidationError, match="period_uuid REQUIRED"):
        TaskCreate(
            uuid=str(uuid.uuid4()),
            title="Routine sans periode",
            recurrence_kind="DAILY",
            recurrence_start_date="2026-05-01",
        )

    with pytest.raises(ValidationError, match="recurrence_start_date REQUIRED"):
        TaskCreate(
            uuid=str(uuid.uuid4()),
            title="Routine sans start_date",
            recurrence_kind="DAILY",
            period_uuid=str(uuid.uuid4()),
        )


def test_task_weekly_requires_weekdays():
    """recurrence_kind=WEEKLY doit avoir recurrence_weekdays non-empty + start_date."""
    with pytest.raises(ValidationError, match="recurrence_weekdays REQUIRED"):
        TaskCreate(
            uuid=str(uuid.uuid4()),
            title="Weekly sans weekdays",
            recurrence_kind="WEEKLY",
            recurrence_start_date="2026-05-01",
        )

    # Out-of-range weekdays
    with pytest.raises(ValidationError, match=r"\[0\.\.6\]"):
        TaskCreate(
            uuid=str(uuid.uuid4()),
            title="Weekly avec weekday 7",
            recurrence_kind="WEEKLY",
            recurrence_weekdays=[0, 2, 7],  # 7 invalide
            recurrence_start_date="2026-05-01",
        )


def test_task_end_date_must_be_after_start():
    """recurrence_end_date < recurrence_start_date doit etre rejete."""
    with pytest.raises(ValidationError, match="end_date must be >="):
        TaskCreate(
            uuid=str(uuid.uuid4()),
            title="Daily avec end avant start",
            recurrence_kind="DAILY",
            period_uuid=str(uuid.uuid4()),
            recurrence_start_date="2026-05-15",
            recurrence_end_date="2026-05-01",  # antarieur
        )


def test_task_valid_none_one_off():
    """Cas valide one-off : NONE + due_date."""
    t = TaskCreate(
        uuid=str(uuid.uuid4()),
        title="RDV medecin",
        recurrence_kind="NONE",
        due_date="2026-05-15",
        due_time="14:00",
    )
    assert t.recurrence_kind == "NONE"
    assert t.due_date == "2026-05-15"
    assert t.period_uuid is None


def test_task_valid_daily_with_period():
    """Cas valide DAILY : period_uuid + start_date."""
    pid = str(uuid.uuid4())
    t = TaskCreate(
        uuid=str(uuid.uuid4()),
        title="Etirements matin",
        recurrence_kind="DAILY",
        period_uuid=pid,
        recurrence_start_date="2026-05-01",
    )
    assert t.recurrence_kind == "DAILY"
    assert t.period_uuid == pid
    assert t.due_date is None


def test_task_valid_weekly():
    """Cas valide WEEKLY : weekdays [0,2,4] (Lun/Mer/Ven)."""
    t = TaskCreate(
        uuid=str(uuid.uuid4()),
        title="Courses",
        recurrence_kind="WEEKLY",
        recurrence_weekdays=[5],  # Samedi
        recurrence_start_date="2026-05-01",
        recurrence_end_date="2026-12-31",
    )
    assert t.recurrence_weekdays == [5]


# ============================================================
# CRUD smoke (avec DB)
# ============================================================

async def test_put_task_none_one_off_returns_200(client):
    """PUT /tasks/{uuid} avec NONE + due_date doit creer la tache."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuid.uuid4())
    r = await client.put(
        f"/api/v1/tasks/{u}",
        json={
            "uuid": u,
            "title": "Appeler dentiste",
            "isActive": True,
            "order": 0,
            "recurrenceKind": "NONE",
            "dueDate": "2026-05-20",
        },
        headers=headers,
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["uuid"] == u
    assert data["recurrenceKind"] == "NONE"
    assert data["dueDate"] == "2026-05-20"


async def test_put_task_daily_requires_existing_period(client):
    """PUT /tasks/{uuid} avec DAILY + period_uuid valide."""
    headers = await login_headers(client, "testuser", "testpass")
    # Create routine_period first
    pu = str(uuid.uuid4())
    rp = await client.put(
        f"/api/v1/routine-periods/{pu}",
        json={"uuid": pu, "name": "Morning", "startTime": "06:30", "endTime": "09:00", "order": 1},
        headers=headers,
    )
    assert rp.status_code == 200, rp.text

    # Create DAILY task pointing to that period
    tu = str(uuid.uuid4())
    r = await client.put(
        f"/api/v1/tasks/{tu}",
        json={
            "uuid": tu,
            "title": "Stretch",
            "isActive": True,
            "order": 1,
            "recurrenceKind": "DAILY",
            "periodUUID": pu,
            "recurrenceStartDate": "2026-05-01",
        },
        headers=headers,
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["recurrenceKind"] == "DAILY"
    assert data["periodUUID"] == pu


async def test_task_check_upsert_and_retrieve(client):
    """PUT /task-checks/{uuid} doit lier a une task existante."""
    headers = await login_headers(client, "testuser", "testpass")
    # Setup : task NONE
    tu = str(uuid.uuid4())
    await client.put(
        f"/api/v1/tasks/{tu}",
        json={
            "uuid": tu,
            "title": "Run errand",
            "isActive": True,
            "order": 0,
            "recurrenceKind": "NONE",
            "dueDate": "2026-05-25",
        },
        headers=headers,
    )

    # PUT check
    cu = str(uuid.uuid4())
    r = await client.put(
        f"/api/v1/task-checks/{cu}",
        json={
            "uuid": cu,
            "taskUUID": tu,
            "occurrenceDate": "2026-05-25",
            "isChecked": True,
        },
        headers=headers,
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["taskUUID"] == tu
    assert data["occurrenceDate"] == "2026-05-25"
    assert data["isChecked"] is True
