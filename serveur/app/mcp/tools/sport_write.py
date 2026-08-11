"""Tools MCP write (non destructifs) pour Cas A (data sport) — design doc §3.1.

5 tools de mutation sélective. `add_notification` du design est déféré (le
modèle `notifications` n'a pas de champ `scheduled_at` et `type` est un
vocabulaire contrôlé — à re-scoper avant exposition).

Convention (identique à sport_data.py) :
- Fonctions async (user_id, **kwargs) → dict JSON-serializable.
- Cascade ownership : user_id vient du JWT MCP, jamais du payload. Chaque tool
  valide que l'entité (et ses parents) appartient au user avant de muter —
  réutilise les getters ownership-checked des CRUD existants.
- updated_at posé à `now` sur toute écriture : garantit la remontée temps-réel
  (trigger WS NOTIFY) ET la convergence sync pull/merge (last-write-wins).
- Erreurs métier → {"ok": False, "message": ...} ; erreurs d'argument → ValueError.
"""

from __future__ import annotations

import logging
from datetime import date, datetime, timezone
from typing import Any, Optional

from sqlalchemy import func, select

from app.crud import actual_workout_crud, actual_workout_set_crud, task_crud, task_check_crud
from app.database import AsyncSessionLocal
from app.models.actual_workout_exercise import ActualWorkoutExercise
from app.models.exercise import Exercise
from app.models.muscle import Muscle
from app.models.muscle_goal import MuscleGoal
from app.models.task_check import TaskCheck

logger = logging.getLogger(__name__)


def _now() -> datetime:
    return datetime.now(timezone.utc)


async def mark_set_done(user_id: int, set_uuid: str, reps: int, weight: float) -> dict[str, Any]:
    """Termine un set : pose reps + weight réalisés et status=DONE."""
    if reps < 0:
        raise ValueError("reps doit être >= 0")
    if weight < 0:
        raise ValueError("weight doit être >= 0")

    async with AsyncSessionLocal() as db:
        s = await actual_workout_set_crud.get_actual_workout_set_by_uuid(db, set_uuid, user_id)
        if s is None:
            return {"ok": False, "message": "Set introuvable."}
        s.reps = reps
        s.weight = weight
        s.status = "DONE"
        s.updated_at = _now()
        await db.commit()
        await db.refresh(s)
        return {"ok": True, "uuid": s.uuid, "reps": s.reps, "weight": s.weight, "status": s.status}


async def create_actual_workout(
    user_id: int,
    name: str,
    workout_date: Optional[str] = None,
    location: Optional[str] = None,
    notes: Optional[str] = None,
) -> dict[str, Any]:
    """Crée une séance ad-hoc (shell sans exercices — voir add_exercise_to_workout)."""
    if not name or not name.strip():
        raise ValueError("name est requis")

    data: dict[str, Any] = {
        "name": name,
        "date": workout_date or date.today().isoformat(),
        "is_done": False,
        "updated_at": _now(),
    }
    if location is not None:
        data["location"] = location
    if notes is not None:
        data["notes"] = notes

    async with AsyncSessionLocal() as db:
        w = await actual_workout_crud.create_actual_workout(db, user_id, data)
        return {"ok": True, "uuid": w.uuid, "name": w.name, "date": w.date}


async def add_exercise_to_workout(
    user_id: int,
    workout_uuid: str,
    exercise_uuid: str,
    sets: int = 3,
    reps: str = "8-12",
) -> dict[str, Any]:
    """Ajoute un exercice à une séance (append en fin, added_manually=True)."""
    if sets < 1:
        raise ValueError("sets doit être >= 1")

    async with AsyncSessionLocal() as db:
        workout = await actual_workout_crud.get_actual_workout_by_uuid(db, workout_uuid, user_id)
        if workout is None:
            return {"ok": False, "message": "Séance introuvable."}

        exercise = (
            await db.execute(
                select(Exercise).where(Exercise.uuid == exercise_uuid, Exercise.user_id == user_id)
            )
        ).scalar_one_or_none()
        if exercise is None:
            return {"ok": False, "message": "Exercice introuvable."}

        max_order = (
            await db.execute(
                select(func.max(ActualWorkoutExercise.order)).where(
                    ActualWorkoutExercise.actual_workout_uuid == workout_uuid
                )
            )
        ).scalar()

        awe = ActualWorkoutExercise(
            actual_workout_uuid=workout_uuid,
            exercise_uuid=exercise_uuid,
            sets=sets,
            reps=reps,
            phase="TRAINING",
            status="NOT_STARTED",
            order=(max_order if max_order is not None else -1) + 1,
            added_manually=True,
            updated_at=_now(),
        )
        db.add(awe)
        await db.commit()
        await db.refresh(awe)
        return {
            "ok": True,
            "uuid": awe.uuid,
            "exercise_name": exercise.name,
            "order": awe.order,
            "sets": awe.sets,
            "reps": awe.reps,
        }


async def update_muscle_goal(
    user_id: int,
    muscle_uuid: str,
    target: int,
    priority: Optional[str] = None,
) -> dict[str, Any]:
    """Crée ou met à jour l'objectif hebdo (semaine courante) d'un muscle."""
    if target < 0:
        raise ValueError("target doit être >= 0")

    week = date.today().strftime("%G-W%V")

    async with AsyncSessionLocal() as db:
        muscle = (
            await db.execute(
                select(Muscle).where(Muscle.uuid == muscle_uuid, Muscle.user_id == user_id)
            )
        ).scalar_one_or_none()
        if muscle is None:
            return {"ok": False, "message": "Muscle introuvable."}

        goal = (
            await db.execute(
                select(MuscleGoal).where(
                    MuscleGoal.user_id == user_id,
                    MuscleGoal.muscle_uuid == muscle_uuid,
                    MuscleGoal.week_iso == week,
                )
            )
        ).scalar_one_or_none()

        if goal is not None:
            goal.target = str(target)
            if priority is not None:
                goal.priority = priority
            goal.updated_at = _now()
            created = False
        else:
            goal = MuscleGoal(
                user_id=user_id,
                muscle_uuid=muscle_uuid,
                priority=priority or "MEDIUM",
                done=0,
                target=str(target),
                week_iso=week,
                status="IN_PROGRESS",
                added_manually=True,
                updated_at=_now(),
            )
            db.add(goal)
            created = True

        await db.commit()
        await db.refresh(goal)
        return {
            "ok": True,
            "created": created,
            "muscle_name": muscle.name,
            "week_iso": week,
            "target": goal.target,
            "priority": goal.priority,
        }


async def tick_routine_task(
    user_id: int,
    task_uuid: str,
    occurrence_date: Optional[str] = None,
    is_checked: bool = True,
) -> dict[str, Any]:
    """Coche (ou décoche) une tâche routine pour une date (défaut aujourd'hui)."""
    occ = occurrence_date or date.today().isoformat()

    async with AsyncSessionLocal() as db:
        task = await task_crud.get_task_by_uuid(db, task_uuid)
        if task is None or task.user_id != user_id:
            return {"ok": False, "message": "Tâche introuvable."}

        now = _now()
        existing = await task_check_crud.get_check_for_task_on_date(db, user_id, task_uuid, occ)
        if existing is not None:
            existing.is_checked = is_checked
            existing.checked_at = now if is_checked else None
            existing.updated_at = now
            check = existing
        else:
            check = TaskCheck(
                user_id=user_id,
                task_uuid=task_uuid,
                occurrence_date=occ,
                is_checked=is_checked,
                checked_at=now if is_checked else None,
                updated_at=now,
            )
            db.add(check)

        await db.commit()
        await db.refresh(check)
        return {
            "ok": True,
            "uuid": check.uuid,
            "task_uuid": task_uuid,
            "occurrence_date": occ,
            "is_checked": check.is_checked,
        }
