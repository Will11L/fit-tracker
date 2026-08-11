"""Tests des tools MCP read Cas A (design doc §3.1 — 9 read tools).

Les tools (`app/mcp/tools/sport_data.py`) ouvrent leur propre session via
`AsyncSessionLocal` (pointant la DB dev). Pour les tester contre `fittracker_test`,
on monkeypatche `sport_data.AsyncSessionLocal` vers un sessionmaker bound au
test_engine de conftest. Les tools sont appelés directement (pas via le protocole
MCP HTTP) avec un user_id seedé.

Couvre les 3 tools POC + les 6 ajoutés (get_workout, get_weekly_volume,
get_exercise_history, get_muscle_goals_progress, get_available_equipment,
list_muscles) + l'isolation cross-user (ownership).
"""
from datetime import date, datetime, timedelta, timezone

import bcrypt
import pytest
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.mcp import audit as mcp_audit
from app.mcp.context import set_mcp_context
from app.mcp.models import MCPAuditLog
from app.mcp.tools import sport_data, sport_destructive, sport_dev, sport_write
from app.models import User
from app.models.actual_workout import ActualWorkout
from app.models.actual_workout_exercise import ActualWorkoutExercise
from app.models.actual_workout_set import ActualWorkoutSet
from app.models.available_equipment import AvailableEquipment
from app.models.exercise import Exercise
from app.models.exercise_muscle import ExerciseMuscle
from app.models.muscle import Muscle
from app.models.muscle_goal import MuscleGoal
from app.models.notification import Notification
from app.models.planned_workout import PlannedWorkout
from app.models.task import Task

_DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]


@pytest_asyncio.fixture(scope="module")
async def seeded(test_engine):
    """Seed un user MCP + données complètes. Retourne les handles (uuids)."""
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    today = date.today()
    week = today.strftime("%G-W%V")

    async with maker() as db:
        user = (
            await db.execute(select(User).where(User.username == "mcp_testuser"))
        ).scalar_one_or_none()

        if user is None:
            user = User(
                username="mcp_testuser",
                hashed_password=bcrypt.hashpw(b"pw", bcrypt.gensalt()).decode("utf-8"),
                first_name="Mcp", last_name="Test", is_admin=False,
            )
            db.add(user)
            await db.flush()
            uid = user.id

            db.add(User(
                username="mcp_admin",
                hashed_password=bcrypt.hashpw(b"pw", bcrypt.gensalt()).decode("utf-8"),
                first_name="Admin", last_name="Test", is_admin=True,
            ))

            chest = Muscle(user_id=uid, name="Mid Chest", muscle_group="Pecs", zone="Chest")
            lats = Muscle(user_id=uid, name="Lats", muscle_group="Lats", zone="Back")
            db.add_all([chest, lats])
            await db.flush()

            bench = Exercise(user_id=uid, name="Bench Press", recommended_sets=4, is_favorite=True)
            db.add(bench)
            await db.flush()
            db.add(ExerciseMuscle(exercise_uuid=bench.uuid, muscle_uuid=chest.uuid, coefficient=1.0))

            db.add_all([
                AvailableEquipment(user_id=uid, name="Barbell"),
                AvailableEquipment(user_id=uid, name="Dumbbells"),
            ])
            db.add(PlannedWorkout(user_id=uid, name="Push Day", day_of_week=_DAYS[today.weekday()]))

            aw = ActualWorkout(user_id=uid, name="Push Session", date=today.isoformat(), is_done=True)
            db.add(aw)
            await db.flush()
            awe = ActualWorkoutExercise(
                actual_workout_uuid=aw.uuid, exercise_uuid=bench.uuid,
                sets=2, reps="8-10", status="DONE", order=0,
            )
            db.add(awe)
            await db.flush()
            db.add_all([
                ActualWorkoutSet(actual_workout_exercise_uuid=awe.uuid, set_order=0,
                                 reps=10, weight=80.0, status="DONE"),
                ActualWorkoutSet(actual_workout_exercise_uuid=awe.uuid, set_order=1,
                                 reps=8, weight=85.0, status="DONE"),
            ])
            db.add(MuscleGoal(user_id=uid, muscle_uuid=chest.uuid, priority="HIGH",
                              done=6, target="12", week_iso=week, status="IN_PROGRESS"))
            db.add(Task(user_id=uid, title="Stretch", recurrence_kind="NONE",
                        due_date=today.isoformat()))
            await db.commit()

        uid = user.id
        # Handles, récupérés uniformément (que le user vienne d'être créé ou non).
        aw = (await db.execute(
            select(ActualWorkout).where(ActualWorkout.user_id == uid)
        )).scalars().first()
        bench = (await db.execute(
            select(Exercise).where(Exercise.user_id == uid, Exercise.name == "Bench Press")
        )).scalar_one()
        chest = (await db.execute(
            select(Muscle).where(Muscle.user_id == uid, Muscle.name == "Mid Chest")
        )).scalar_one()
        lats = (await db.execute(
            select(Muscle).where(Muscle.user_id == uid, Muscle.name == "Lats")
        )).scalar_one()
        set0 = (await db.execute(
            select(ActualWorkoutSet)
            .join(ActualWorkoutExercise,
                  ActualWorkoutSet.actual_workout_exercise_uuid == ActualWorkoutExercise.uuid)
            .where(ActualWorkoutExercise.actual_workout_uuid == aw.uuid,
                   ActualWorkoutSet.set_order == 0)
        )).scalars().first()
        task = (await db.execute(
            select(Task).where(Task.user_id == uid)
        )).scalars().first()
        admin = (await db.execute(
            select(User).where(User.username == "mcp_admin")
        )).scalar_one()

    return {
        "user_id": uid, "admin_user_id": admin.id, "aw_uuid": aw.uuid,
        "set_uuid": set0.uuid, "exercise_uuid": bench.uuid, "muscle_uuid": chest.uuid,
        "lats_uuid": lats.uuid, "task_uuid": task.uuid, "week": week,
    }


@pytest.fixture(autouse=True)
def patch_session(monkeypatch, test_engine):
    """Redirige AsyncSessionLocal de TOUS les modules tools vers la DB de test.

    🔴 Critique : chaque module qui ouvre une session (sport_data, sport_write,
    sport_destructive) doit être patché — sinon ses tools tapent la vraie DB
    (app.database.AsyncSessionLocal) au lieu de fittracker_test. Incident
    2026-05-28 : sport_write non patché -> écriture en prod.
    """
    test_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    monkeypatch.setattr(sport_data, "AsyncSessionLocal", test_maker)
    monkeypatch.setattr(sport_write, "AsyncSessionLocal", test_maker)
    monkeypatch.setattr(sport_destructive, "AsyncSessionLocal", test_maker)
    monkeypatch.setattr(sport_dev, "AsyncSessionLocal", test_maker)
    monkeypatch.setattr(mcp_audit, "AsyncSessionLocal", test_maker)


async def test_get_next_planned_workout(seeded):
    res = await sport_data.get_next_planned_workout(seeded["user_id"])
    assert res["found"] is True
    assert res["name"] == "Push Day"
    assert res["is_today"] is True


async def test_list_recent_workouts(seeded):
    res = await sport_data.list_recent_workouts(seeded["user_id"])
    assert res["count"] >= 1
    assert any(w["name"] == "Push Session" for w in res["workouts"])


async def test_search_exercises(seeded):
    res = await sport_data.search_exercises(seeded["user_id"], query="bench")
    assert res["count"] == 1
    assert res["exercises"][0]["name"] == "Bench Press"


async def test_search_exercises_too_short(seeded):
    with pytest.raises(ValueError):
        await sport_data.search_exercises(seeded["user_id"], query="b")


async def test_get_workout(seeded):
    res = await sport_data.get_workout(seeded["user_id"], seeded["aw_uuid"])
    assert res["found"] is True
    assert res["name"] == "Push Session"
    assert len(res["exercises"]) == 1
    ex = res["exercises"][0]
    assert ex["exercise_name"] == "Bench Press"
    assert len(ex["sets"]) == 2
    assert {s["set_order"] for s in ex["sets"]} == {0, 1}


async def test_get_workout_cross_user_isolation(seeded):
    # user_id bidon → la séance ne lui appartient pas → found False.
    res = await sport_data.get_workout(999999, seeded["aw_uuid"])
    assert res["found"] is False


async def test_get_weekly_volume(seeded):
    res = await sport_data.get_weekly_volume(seeded["user_id"], muscle_name="mid chest")
    assert res["found"] is True
    assert res["muscle_name"] == "Mid Chest"
    bucket = next(w for w in res["weeks"] if w["week_iso"] == seeded["week"])
    # 10*80*1.0 + 8*85*1.0 = 1480 ; sets effectifs = 1.0 + 1.0 = 2.0
    assert bucket["volume"] == 1480.0
    assert bucket["sets"] == 2.0
    assert bucket["reps"] == 18


async def test_get_weekly_volume_unknown_muscle(seeded):
    res = await sport_data.get_weekly_volume(seeded["user_id"], muscle_name="Nonexistent")
    assert res["found"] is False


async def test_get_exercise_history(seeded):
    res = await sport_data.get_exercise_history(seeded["user_id"], exercise_name="Bench Press")
    assert res["found"] is True
    assert res["count"] == 1
    assert len(res["sessions"][0]["sets"]) == 2


async def test_get_muscle_goals_progress(seeded):
    res = await sport_data.get_muscle_goals_progress(seeded["user_id"], week_offset=0)
    assert res["week_iso"] == seeded["week"]
    assert res["count"] == 1
    goal = res["goals"][0]
    assert goal["muscle_name"] == "Mid Chest"
    assert goal["percent"] == 50.0


async def test_get_available_equipment(seeded):
    res = await sport_data.get_available_equipment(seeded["user_id"])
    assert res["count"] == 2
    assert {e["name"] for e in res["equipment"]} == {"Barbell", "Dumbbells"}


async def test_list_muscles(seeded):
    res = await sport_data.list_muscles(seeded["user_id"])
    assert res["count"] == 2
    assert {m["name"] for m in res["muscles"]} == {"Mid Chest", "Lats"}


# ============================================================
# Write tools (sport:write) — happy path + isolation cross-user + validation.
# Définis APRÈS les read tests : ils mutent les données seedées (les asserts
# read sur volume/goals s'appuient sur l'état initial non muté).
# ============================================================


async def test_mark_set_done(seeded):
    res = await sport_write.mark_set_done(seeded["user_id"], seeded["set_uuid"], reps=12, weight=90.0)
    assert res["ok"] is True
    assert res["status"] == "DONE"
    assert res["reps"] == 12 and res["weight"] == 90.0


async def test_mark_set_done_cross_user(seeded):
    res = await sport_write.mark_set_done(999999, seeded["set_uuid"], reps=5, weight=20.0)
    assert res["ok"] is False


async def test_mark_set_done_negative_reps(seeded):
    with pytest.raises(ValueError):
        await sport_write.mark_set_done(seeded["user_id"], seeded["set_uuid"], reps=-1, weight=10.0)


async def test_create_actual_workout(seeded):
    res = await sport_write.create_actual_workout(
        seeded["user_id"], name="Leg Day", workout_date="2026-05-20"
    )
    assert res["ok"] is True
    assert res["name"] == "Leg Day"
    assert res["date"] == "2026-05-20"
    # Persistance vérifiée via le read tool.
    got = await sport_data.get_workout(seeded["user_id"], res["uuid"])
    assert got["found"] is True and got["name"] == "Leg Day"


async def test_add_exercise_to_workout(seeded):
    res = await sport_write.add_exercise_to_workout(
        seeded["user_id"], seeded["aw_uuid"], seeded["exercise_uuid"], sets=4, reps="5"
    )
    assert res["ok"] is True
    assert res["exercise_name"] == "Bench Press"
    assert res["sets"] == 4 and res["reps"] == "5"


async def test_add_exercise_to_workout_cross_user(seeded):
    res = await sport_write.add_exercise_to_workout(
        999999, seeded["aw_uuid"], seeded["exercise_uuid"]
    )
    assert res["ok"] is False


async def test_update_muscle_goal_existing(seeded):
    # Mid Chest a déjà un goal cette semaine (seed target=12) -> update, pas création.
    res = await sport_write.update_muscle_goal(
        seeded["user_id"], seeded["muscle_uuid"], target=20, priority="HIGH"
    )
    assert res["ok"] is True
    assert res["created"] is False
    assert res["target"] == "20"
    prog = await sport_data.get_muscle_goals_progress(seeded["user_id"], 0)
    goal = next(g for g in prog["goals"] if g["muscle_name"] == "Mid Chest")
    assert goal["target"] == "20"


async def test_update_muscle_goal_cross_user(seeded):
    res = await sport_write.update_muscle_goal(999999, seeded["muscle_uuid"], target=10)
    assert res["ok"] is False


async def test_tick_routine_task(seeded):
    res = await sport_write.tick_routine_task(
        seeded["user_id"], seeded["task_uuid"], occurrence_date="2026-05-28"
    )
    assert res["ok"] is True and res["is_checked"] is True
    # Upsert idempotent par (task, date) : décocher renvoie le MÊME check.
    res2 = await sport_write.tick_routine_task(
        seeded["user_id"], seeded["task_uuid"], occurrence_date="2026-05-28", is_checked=False
    )
    assert res2["ok"] is True and res2["is_checked"] is False
    assert res2["uuid"] == res["uuid"]


async def test_tick_routine_task_cross_user(seeded):
    res = await sport_write.tick_routine_task(999999, seeded["task_uuid"])
    assert res["ok"] is False


# ============================================================
# Destructive tools (scope sport:destructive, destructiveHint=true).
# Chaque test crée sa propre donnée jetable (pas de couplage au seed partagé).
# ============================================================


async def test_destructive_tools_have_hint():
    from app.mcp.server import mcp
    tools = {t.name: t for t in await mcp.list_tools()}
    for n in ["delete_actual_workout", "delete_exercise",
              "delete_muscle_goal", "bulk_delete_notifications"]:
        assert tools[n].annotations is not None
        assert tools[n].annotations.destructiveHint is True


async def test_delete_actual_workout(seeded):
    created = await sport_write.create_actual_workout(seeded["user_id"], name="Throwaway WO")
    wo_uuid = created["uuid"]
    # cross-user : ne supprime pas, la séance reste.
    assert (await sport_destructive.delete_actual_workout(999999, wo_uuid))["ok"] is False
    assert (await sport_data.get_workout(seeded["user_id"], wo_uuid))["found"] is True
    # owner : supprime.
    assert (await sport_destructive.delete_actual_workout(seeded["user_id"], wo_uuid))["ok"] is True
    assert (await sport_data.get_workout(seeded["user_id"], wo_uuid))["found"] is False


async def test_delete_exercise(seeded, test_engine):
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with maker() as db:
        ex = Exercise(user_id=seeded["user_id"], name="Throwaway Ex")
        db.add(ex)
        await db.commit()
        await db.refresh(ex)
        ex_uuid = ex.uuid

    assert (await sport_destructive.delete_exercise(999999, ex_uuid))["ok"] is False
    assert (await sport_destructive.delete_exercise(seeded["user_id"], ex_uuid))["ok"] is True
    assert (await sport_data.search_exercises(seeded["user_id"], query="Throwaway"))["count"] == 0


async def test_delete_muscle_goal(seeded):
    # Crée un goal Lats (semaine courante) -> couvre aussi update_muscle_goal created=True.
    created = await sport_write.update_muscle_goal(seeded["user_id"], seeded["lats_uuid"], target=5)
    assert created["created"] is True
    assert (await sport_destructive.delete_muscle_goal(999999, seeded["lats_uuid"]))["ok"] is False
    assert (await sport_destructive.delete_muscle_goal(seeded["user_id"], seeded["lats_uuid"]))["ok"] is True
    prog = await sport_data.get_muscle_goals_progress(seeded["user_id"], 0)
    assert not any(g["muscle_name"] == "Lats" for g in prog["goals"])


async def test_bulk_delete_notifications(seeded, test_engine):
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with maker() as db:
        db.add_all([
            Notification(user_id=seeded["user_id"], type="TEST", title="lue",
                         read_at=datetime.now(timezone.utc)),
            Notification(user_id=seeded["user_id"], type="TEST", title="non lue"),
        ])
        await db.commit()

    # scope=read : supprime seulement la notif lue.
    res = await sport_destructive.bulk_delete_notifications(seeded["user_id"], scope="read")
    assert res["ok"] is True and res["deleted_count"] == 1
    # cross-user : user bidon -> rien à supprimer.
    res2 = await sport_destructive.bulk_delete_notifications(999999, scope="all")
    assert res2["deleted_count"] == 0


async def test_bulk_delete_notifications_bad_scope(seeded):
    with pytest.raises(ValueError):
        await sport_destructive.bulk_delete_notifications(seeded["user_id"], scope="bogus")


# ============================================================
# Cas B1 — dev runtime (scope ops:read + require_admin).
# require_admin testé via set_mcp_context ; subprocess via _run monkeypatché ;
# healthcheck/alembic en réel sur la test DB.
# ============================================================


async def test_require_admin_allows_admin(seeded):
    set_mcp_context(user_id=seeded["admin_user_id"], client_id="c", scopes=["ops:read"])
    await sport_dev.require_admin()  # ne raise pas


async def test_require_admin_rejects_non_admin(seeded):
    set_mcp_context(user_id=seeded["user_id"], client_id="c", scopes=["ops:read"])
    with pytest.raises(PermissionError):
        await sport_dev.require_admin()


async def test_get_service_status(monkeypatch):
    async def fake_run(cmd):
        return 0, ("ActiveState=active\nSubState=running\nNRestarts=2\n"
                   "ExecMainStartTimestamp=Thu 2026-05-28 22:18:21 CEST\nMainPID=578320\n")
    monkeypatch.setattr(sport_dev, "_run", fake_run)
    res = await sport_dev.get_service_status("sportapi")
    assert res["ok"] is True
    assert res["active_state"] == "active" and res["sub_state"] == "running"
    assert res["restarts"] == 2 and res["main_pid"] == "578320"


async def test_get_service_status_rejects_unknown_unit():
    res = await sport_dev.get_service_status("nginx")
    assert res["ok"] is False


async def test_get_recent_logs_redacts_secrets(monkeypatch):
    async def fake_run(cmd):
        return 0, ("2026-05-28 ligne normale\nJWT_SECRET_KEY=supersecretvalue\n"
                   "password=hunter2\n")
    monkeypatch.setattr(sport_dev, "_run", fake_run)
    res = await sport_dev.get_recent_logs("sportapi", since="5min", limit=10)
    assert res["ok"] is True
    joined = "\n".join(res["lines"])
    assert "supersecretvalue" not in joined and "hunter2" not in joined
    assert "[REDACTED]" in joined


async def test_get_recent_logs_bad_since(monkeypatch):
    async def fake_run(cmd):
        return 0, ""
    monkeypatch.setattr(sport_dev, "_run", fake_run)
    with pytest.raises(ValueError):
        await sport_dev.get_recent_logs("sportapi", since="yesterday")


async def test_get_recent_logs_rejects_unknown_unit():
    res = await sport_dev.get_recent_logs("nginx")
    assert res["ok"] is False


async def test_healthcheck(seeded):
    res = await sport_dev.healthcheck()
    assert res["ok"] is True and res["db"] == "ok"


async def test_get_alembic_status(seeded):
    res = await sport_dev.get_alembic_status()
    assert res["ok"] is True and "current" in res


# ============================================================
# Audit logging (T7) — design §7, table mcp_audit_log.
# ============================================================


async def test_write_audit(seeded, test_engine):
    set_mcp_context(user_id=seeded["user_id"], client_id="cli-test", scopes=["sport:read"])
    await mcp_audit.write_audit("__audit_unit__", {"q": "bench"}, "ok", None, {"count": 1})
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with maker() as db:
        row = (await db.execute(
            select(MCPAuditLog).where(MCPAuditLog.tool_name == "__audit_unit__")
        )).scalars().first()
    assert row is not None
    assert row.user_id == seeded["user_id"] and row.client_id == "cli-test"
    assert row.status == "ok" and row.args == {"q": "bench"}
    assert "count" in (row.result_summary or "")


async def test_audit_hook_logs_tool_call(seeded, test_engine):
    from app.mcp.server import mcp
    mcp_audit.install_audit_hook(mcp)  # idempotent
    set_mcp_context(user_id=seeded["user_id"], client_id="cli-hook", scopes=["sport:read"])
    await mcp._tool_manager.call_tool("list_muscles", {})
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with maker() as db:
        row = (await db.execute(
            select(MCPAuditLog)
            .where(MCPAuditLog.tool_name == "list_muscles", MCPAuditLog.client_id == "cli-hook")
            .order_by(MCPAuditLog.id.desc())
        )).scalars().first()
    assert row is not None and row.status == "ok"


async def test_get_table_row_count(seeded):
    res = await sport_dev.get_table_row_count("muscles")
    assert res["ok"] is True and res["count"] >= 2  # seed : Mid Chest + Lats


async def test_get_table_row_count_rejects_unknown(seeded):
    res = await sport_dev.get_table_row_count("pg_user")
    assert res["ok"] is False


async def test_db_schema_info_list(seeded):
    res = await sport_dev.db_schema_info()
    assert res["ok"] is True and "muscles" in res["tables"]


async def test_db_schema_info_columns(seeded):
    res = await sport_dev.db_schema_info("muscles")
    assert res["ok"] is True
    cols = {c["name"] for c in res["columns"]}
    assert {"name", "zone", "muscle_group"} <= cols


async def test_db_schema_info_rejects_unknown(seeded):
    res = await sport_dev.db_schema_info("information_schema")
    assert res["ok"] is False


async def test_get_db_size(seeded):
    res = await sport_dev.get_db_size()
    assert res["ok"] is True and res["total_bytes"] > 0
    assert isinstance(res["tables"], list)


async def test_get_user_activity_summary(seeded):
    res = await sport_dev.get_user_activity_summary(seeded["user_id"])
    assert res["ok"] is True and res["scope"] == "user"
    assert res["workouts"] >= 1  # seed Push Session (date du jour)


async def test_purge_old_audit_logs(test_engine):
    maker = async_sessionmaker(test_engine, expire_on_commit=False)
    async with maker() as db:
        db.add_all([
            MCPAuditLog(user_id=1, tool_name="__purge_old__", status="ok",
                        created_at=datetime.now(timezone.utc) - timedelta(days=40)),
            MCPAuditLog(user_id=1, tool_name="__purge_recent__", status="ok",
                        created_at=datetime.now(timezone.utc)),
        ])
        await db.commit()
    deleted = await mcp_audit.purge_old_audit_logs(days=30)
    assert deleted >= 1
    async with maker() as db:
        old_left = (await db.execute(
            select(MCPAuditLog).where(MCPAuditLog.tool_name == "__purge_old__")
        )).scalars().all()
        recent_left = (await db.execute(
            select(MCPAuditLog).where(MCPAuditLog.tool_name == "__purge_recent__")
        )).scalars().all()
    assert len(old_left) == 0 and len(recent_left) == 1
