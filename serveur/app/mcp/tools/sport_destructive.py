"""Tools MCP destructifs pour Cas A (data sport) — design doc §3.1.

4 tools de suppression, scope `sport:destructive`, annotés `destructiveHint=true`
côté protocole (le client MCP demande confirmation avant exécution). Isolés dans
leur propre module pour faciliter l'audit de la surface destructive.

Convention identique à sport_data/sport_write : cascade ownership via user_id du
JWT, getters CRUD ownership-checked, {"ok": bool, ...} en retour.
"""

from __future__ import annotations

import logging
from datetime import date
from typing import Any

from fastapi import HTTPException
from sqlalchemy import delete, select

from app.crud import actual_workout_crud, exercise_crud
from app.database import AsyncSessionLocal
from app.models.muscle_goal import MuscleGoal
from app.models.notification import Notification

logger = logging.getLogger(__name__)


async def delete_actual_workout(user_id: int, workout_uuid: str) -> dict[str, Any]:
    """Supprime une séance réalisée (et ses exercices/sets en cascade)."""
    async with AsyncSessionLocal() as db:
        ok = await actual_workout_crud.delete_actual_workout(db, workout_uuid, user_id)
        if not ok:
            return {"ok": False, "message": "Séance introuvable."}
        return {"ok": True, "deleted": True}


async def delete_exercise(user_id: int, exercise_uuid: str) -> dict[str, Any]:
    """Supprime un exercice du catalogue user.

    ⚠️ Cascade : supprime aussi toutes les occurrences historiques de cet
    exercice dans les séances réalisées (FK ondelete CASCADE) et ses relations
    muscles. Action irréversible.
    """
    async with AsyncSessionLocal() as db:
        try:
            await exercise_crud.delete_exercise(db, exercise_uuid, user_id)
        except HTTPException:
            return {"ok": False, "message": "Exercice introuvable."}
        return {"ok": True, "deleted": True}


async def delete_muscle_goal(user_id: int, muscle_uuid: str) -> dict[str, Any]:
    """Supprime l'objectif hebdo de la semaine courante pour un muscle."""
    week = date.today().strftime("%G-W%V")
    async with AsyncSessionLocal() as db:
        goal = (
            await db.execute(
                select(MuscleGoal).where(
                    MuscleGoal.user_id == user_id,
                    MuscleGoal.muscle_uuid == muscle_uuid,
                    MuscleGoal.week_iso == week,
                )
            )
        ).scalar_one_or_none()
        if goal is None:
            return {"ok": False, "message": "Aucun objectif cette semaine pour ce muscle."}
        await db.delete(goal)
        await db.commit()
        return {"ok": True, "deleted": True, "week_iso": week}


async def bulk_delete_notifications(user_id: int, scope: str = "read") -> dict[str, Any]:
    """Supprime en masse les notifications de l'utilisateur.

    Args:
        scope: "read" (lues uniquement, défaut), "archived" (archivées), ou "all".
    """
    if scope not in ("read", "archived", "all"):
        raise ValueError("scope doit être 'read', 'archived' ou 'all'")

    async with AsyncSessionLocal() as db:
        stmt = delete(Notification).where(Notification.user_id == user_id)
        if scope == "read":
            stmt = stmt.where(Notification.read_at.isnot(None))
        elif scope == "archived":
            stmt = stmt.where(Notification.archived_at.isnot(None))
        result = await db.execute(stmt)
        await db.commit()
        return {"ok": True, "scope": scope, "deleted_count": result.rowcount}
