"""Tools MCP read pour Cas A (data sport).

Phase 1 : 9 tools en lecture (design doc §3.1). Voir `server.py` pour les
wrappers `@mcp.tool()` exposés au protocole.

Convention :
- Tools sont des fonctions async qui prennent (user_id, **kwargs) et renvoient
  un dict JSON-serializable.
- Ils ouvrent leur propre session SQLAlchemy via AsyncSessionLocal.
- Cascade ownership : user_id vient du JWT MCP (jamais du payload tool).
- Erreurs : raise ValueError → le wrapper MCP convertit en tool_result error.

Convention semaine : `%G-W%V` (ISO year-week, ex. "2026-W22") — même format que
`muscle_goals.week_iso`. Côté Postgres : `to_char(d, 'IYYY"-W"IW')`. Le volume
muscle est pondéré par `exercise_muscles.coefficient` et filtré `status='DONE'`,
identique à l'agrégation de l'écran Stats Android (ActualWorkoutSetDao).
"""

from __future__ import annotations

import logging
from datetime import date, timedelta
from typing import Any, Optional

from sqlalchemy import select, text

from app.database import AsyncSessionLocal
from app.models.actual_workout import ActualWorkout
from app.models.actual_workout_exercise import ActualWorkoutExercise
from app.models.actual_workout_set import ActualWorkoutSet
from app.models.available_equipment import AvailableEquipment
from app.models.exercise import Exercise
from app.models.muscle import Muscle
from app.models.muscle_goal import MuscleGoal
from app.models.planned_workout import PlannedWorkout

logger = logging.getLogger(__name__)


async def get_next_planned_workout(user_id: int) -> dict[str, Any]:
    """Prochaine séance planifiée de l'utilisateur (rolling week).

    Retourne le PlannedWorkout dont `day_of_week` est le plus proche
    aujourd'hui ou dans le futur (rolling sur la semaine).
    Si aucune séance planifiée → message indicatif.
    """
    days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    today_idx = date.today().weekday()  # 0 = Monday

    async with AsyncSessionLocal() as db:
        res = await db.execute(
            select(PlannedWorkout).where(PlannedWorkout.user_id == user_id)
        )
        workouts = list(res.scalars().all())

    if not workouts:
        return {"found": False, "message": "Aucune séance planifiée."}

    # Trouver le PlannedWorkout au jour le plus proche (incluant aujourd'hui)
    def day_distance(w: PlannedWorkout) -> int:
        try:
            w_idx = days.index(w.day_of_week)
        except ValueError:
            return 999
        return (w_idx - today_idx) % 7

    workouts.sort(key=day_distance)
    next_w = workouts[0]
    distance = day_distance(next_w)
    target_date = date.today() + timedelta(days=distance)

    return {
        "found": True,
        "uuid": next_w.uuid,
        "name": next_w.name,
        "day_of_week": next_w.day_of_week,
        "date": target_date.isoformat(),
        "is_today": distance == 0,
    }


async def list_recent_workouts(
    user_id: int,
    days_back: int = 30,
    limit: int = 20,
) -> dict[str, Any]:
    """Workouts effectivement faits (`actual_workouts`) sur les N derniers jours."""
    if days_back < 1 or days_back > 365:
        raise ValueError("days_back doit être entre 1 et 365")
    if limit < 1 or limit > 100:
        raise ValueError("limit doit être entre 1 et 100")

    start = (date.today() - timedelta(days=days_back)).isoformat()

    async with AsyncSessionLocal() as db:
        res = await db.execute(
            select(ActualWorkout)
            .where(ActualWorkout.user_id == user_id)
            .where(ActualWorkout.date >= start)
            .order_by(ActualWorkout.date.desc())
            .limit(limit)
        )
        workouts = res.scalars().all()

    return {
        "count": len(workouts),
        "workouts": [
            {
                "uuid": w.uuid,
                "name": w.name,
                "date": w.date,
                "is_done": w.is_done,
                "location": w.location,
            }
            for w in workouts
        ],
    }


async def search_exercises(
    user_id: int,
    query: str,
    limit: int = 20,
) -> dict[str, Any]:
    """Recherche dans le catalogue d'exercices de l'utilisateur (substring case-insensitive)."""
    if not query or len(query) < 2:
        raise ValueError("query doit faire au moins 2 caractères")
    if limit < 1 or limit > 50:
        raise ValueError("limit doit être entre 1 et 50")

    pattern = f"%{query.lower()}%"

    async with AsyncSessionLocal() as db:
        res = await db.execute(
            select(Exercise)
            .where(Exercise.user_id == user_id)
            .where(Exercise.name.ilike(pattern))
            .order_by(Exercise.is_favorite.desc(), Exercise.name)
            .limit(limit)
        )
        exercises = res.scalars().all()

    return {
        "count": len(exercises),
        "exercises": [
            {
                "uuid": e.uuid,
                "name": e.name,
                "description": e.description,
                "is_favorite": e.is_favorite,
                "recommended_sets": e.recommended_sets,
                "recommended_reps": e.recommended_reps,
            }
            for e in exercises
        ],
    }


async def get_workout(user_id: int, workout_uuid: str) -> dict[str, Any]:
    """Détail d'une séance réalisée : exercices ordonnés + sets de chacun.

    Cascade ownership : `found=False` si le workout n'existe pas ou appartient
    à un autre user (filtre `user_id`, jamais d'exposition cross-user).
    """
    async with AsyncSessionLocal() as db:
        aw = (
            await db.execute(
                select(ActualWorkout)
                .where(ActualWorkout.uuid == workout_uuid)
                .where(ActualWorkout.user_id == user_id)
            )
        ).scalar_one_or_none()

        if aw is None:
            return {"found": False, "message": "Séance introuvable."}

        awes = list(
            (
                await db.execute(
                    select(ActualWorkoutExercise)
                    .where(ActualWorkoutExercise.actual_workout_uuid == aw.uuid)
                    .order_by(ActualWorkoutExercise.order)
                )
            ).scalars().all()
        )

        awe_uuids = [a.uuid for a in awes]
        ex_uuids = [a.exercise_uuid for a in awes]

        # Résout les noms d'exercices + tous les sets en 2 requêtes (pas de N+1).
        ex_names: dict[str, str] = {}
        if ex_uuids:
            rows = await db.execute(
                select(Exercise.uuid, Exercise.name).where(Exercise.uuid.in_(ex_uuids))
            )
            ex_names = {u: n for u, n in rows.all()}

        sets_by_awe: dict[str, list[ActualWorkoutSet]] = {}
        if awe_uuids:
            sets = (
                await db.execute(
                    select(ActualWorkoutSet)
                    .where(ActualWorkoutSet.actual_workout_exercise_uuid.in_(awe_uuids))
                    .order_by(ActualWorkoutSet.set_order)
                )
            ).scalars().all()
            for s in sets:
                sets_by_awe.setdefault(s.actual_workout_exercise_uuid, []).append(s)

    return {
        "found": True,
        "uuid": aw.uuid,
        "name": aw.name,
        "date": aw.date,
        "is_done": aw.is_done,
        "location": aw.location,
        "notes": aw.notes,
        "exercises": [
            {
                "uuid": a.uuid,
                "exercise_uuid": a.exercise_uuid,
                "exercise_name": ex_names.get(a.exercise_uuid),
                "phase": a.phase,
                "status": a.status,
                "order": a.order,
                "sets": [
                    {
                        "set_order": s.set_order,
                        "reps": s.reps,
                        "weight": s.weight,
                        "status": s.status,
                        "is_dropset": s.is_dropset,
                    }
                    for s in sets_by_awe.get(a.uuid, [])
                ],
            }
            for a in awes
        ],
    }


async def get_weekly_volume(
    user_id: int,
    muscle_name: str,
    weeks: int = 4,
) -> dict[str, Any]:
    """Volume hebdo pour un muscle sur les N dernières semaines.

    Volume = `SUM(weight * reps * coefficient)` sur les sets `status='DONE'`,
    pondéré par `exercise_muscles.coefficient` (sémantique de l'écran Stats).
    `sets` = somme des coefficients (sets "effectifs"). Buckets ISO week.
    """
    if weeks < 1 or weeks > 52:
        raise ValueError("weeks doit être entre 1 et 52")

    cutoff = (date.today() - timedelta(weeks=weeks)).isoformat()

    async with AsyncSessionLocal() as db:
        muscle = (
            await db.execute(
                select(Muscle.uuid, Muscle.name)
                .where(Muscle.user_id == user_id)
                .where(Muscle.name.ilike(muscle_name))
            )
        ).first()

        if muscle is None:
            return {"found": False, "message": f"Muscle introuvable : {muscle_name}"}

        rows = await db.execute(
            text(
                """
                SELECT to_char(aw.date::date, 'IYYY"-W"IW') AS week_iso,
                       COALESCE(SUM(s.weight * s.reps * em.coefficient), 0) AS volume,
                       COALESCE(SUM(em.coefficient), 0) AS sets,
                       COALESCE(SUM(s.reps), 0) AS reps
                FROM actual_workout_sets s
                JOIN actual_workout_exercises awe
                  ON s.actual_workout_exercise_uuid = awe.uuid
                JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
                JOIN exercise_muscles em ON em.exercise_uuid = awe.exercise_uuid
                JOIN muscles m ON em.muscle_uuid = m.uuid
                WHERE aw.user_id = :uid
                  AND m.uuid = :muscle_uuid
                  AND s.status = 'DONE'
                  AND aw.date >= :cutoff
                GROUP BY week_iso
                ORDER BY week_iso ASC
                """
            ),
            {"uid": user_id, "muscle_uuid": muscle.uuid, "cutoff": cutoff},
        )

        weeks_data = [
            {
                "week_iso": r.week_iso,
                "volume": round(float(r.volume), 2),
                "sets": round(float(r.sets), 2),
                "reps": int(r.reps),
            }
            for r in rows.all()
        ]

    return {
        "found": True,
        "muscle_name": muscle.name,
        "weeks": weeks_data,
    }


async def get_exercise_history(
    user_id: int,
    exercise_name: str,
    limit: int = 20,
) -> dict[str, Any]:
    """Dernières séances où l'exercice a été réalisé, sets inclus (date desc)."""
    if not exercise_name or len(exercise_name) < 2:
        raise ValueError("exercise_name doit faire au moins 2 caractères")
    if limit < 1 or limit > 100:
        raise ValueError("limit doit être entre 1 et 100")

    async with AsyncSessionLocal() as db:
        exercise = (
            await db.execute(
                select(Exercise.uuid, Exercise.name)
                .where(Exercise.user_id == user_id)
                .where(Exercise.name.ilike(exercise_name))
            )
        ).first()

        if exercise is None:
            return {"found": False, "message": f"Exercice introuvable : {exercise_name}"}

        rows = await db.execute(
            text(
                """
                SELECT aw.date AS date, aw.name AS workout_name,
                       s.set_order AS set_order, s.reps AS reps,
                       s.weight AS weight, s.status AS status
                FROM actual_workout_sets s
                JOIN actual_workout_exercises awe
                  ON s.actual_workout_exercise_uuid = awe.uuid
                JOIN actual_workouts aw ON awe.actual_workout_uuid = aw.uuid
                WHERE aw.user_id = :uid AND awe.exercise_uuid = :ex_uuid
                ORDER BY aw.date DESC, s.set_order ASC
                """
            ),
            {"uid": user_id, "ex_uuid": exercise.uuid},
        )

        # Regroupe les sets par séance, en préservant l'ordre date desc, puis
        # tronque aux `limit` dernières séances.
        sessions: list[dict[str, Any]] = []
        index: dict[tuple, dict[str, Any]] = {}
        for r in rows.all():
            key = (r.date, r.workout_name)
            session = index.get(key)
            if session is None:
                session = {"date": r.date, "workout_name": r.workout_name, "sets": []}
                index[key] = session
                sessions.append(session)
            session["sets"].append(
                {
                    "set_order": r.set_order,
                    "reps": r.reps,
                    "weight": r.weight,
                    "status": r.status,
                }
            )

    sessions = sessions[:limit]
    return {
        "found": True,
        "exercise_name": exercise.name,
        "count": len(sessions),
        "sessions": sessions,
    }


async def get_muscle_goals_progress(
    user_id: int,
    week_offset: int = 0,
) -> dict[str, Any]:
    """% d'atteinte des objectifs hebdo par muscle pour une semaine donnée.

    `week_offset=0` = semaine courante, `-1` = semaine passée, etc.
    `percent` est `null` si la cible n'est pas un entier parsable.
    """
    target_week = (date.today() + timedelta(weeks=week_offset)).strftime("%G-W%V")

    async with AsyncSessionLocal() as db:
        rows = (
            await db.execute(
                select(MuscleGoal, Muscle.name)
                .join(Muscle, MuscleGoal.muscle_uuid == Muscle.uuid)
                .where(MuscleGoal.user_id == user_id)
                .where(MuscleGoal.week_iso == target_week)
            )
        ).all()

    goals = []
    for goal, muscle_name in rows:
        try:
            target_int = int(goal.target)
            percent = round(goal.done / target_int * 100, 1) if target_int else None
        except (ValueError, TypeError):
            target_int = None
            percent = None
        goals.append(
            {
                "muscle_uuid": goal.muscle_uuid,
                "muscle_name": muscle_name,
                "done": goal.done,
                "target": goal.target,
                "percent": percent,
                "priority": goal.priority,
                "status": goal.status,
            }
        )

    goals.sort(key=lambda g: (g["percent"] is None, -(g["percent"] or 0)))
    return {
        "week_iso": target_week,
        "count": len(goals),
        "goals": goals,
    }


async def get_available_equipment(user_id: int) -> dict[str, Any]:
    """Équipement disponible déclaré par l'utilisateur."""
    async with AsyncSessionLocal() as db:
        rows = (
            await db.execute(
                select(AvailableEquipment)
                .where(AvailableEquipment.user_id == user_id)
                .order_by(AvailableEquipment.name)
            )
        ).scalars().all()

    return {
        "count": len(rows),
        "equipment": [{"uuid": e.uuid, "name": e.name} for e in rows],
    }


async def list_muscles(user_id: int) -> dict[str, Any]:
    """Catalogue des muscles de l'utilisateur (niveau précis + groupe + zone)."""
    async with AsyncSessionLocal() as db:
        rows = (
            await db.execute(
                select(Muscle)
                .where(Muscle.user_id == user_id)
                .order_by(Muscle.zone, Muscle.muscle_group, Muscle.name)
            )
        ).scalars().all()

    return {
        "count": len(rows),
        "muscles": [
            {
                "uuid": m.uuid,
                "name": m.name,
                "muscle_group": m.muscle_group,
                "zone": m.zone,
                "is_favorite": m.is_favorite,
            }
            for m in rows
        ],
    }
