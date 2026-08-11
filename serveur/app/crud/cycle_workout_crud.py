from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import CycleWorkout, PlannedWorkout
from app.schemas import CycleWorkoutOut
from fastapi import HTTPException
from app import schemas
from app import models
from app.crud._concurrency import is_payload_stale


# Vérifie que le PlannedWorkout appartient bien à l'utilisateur
async def assert_user_owns_planned_workout(db: AsyncSession, planned_workout_uuid: str, user_id: int):
    result = await db.execute(
        select(PlannedWorkout).where(PlannedWorkout.uuid == planned_workout_uuid)
    )
    pw = result.scalars().first()

    if not pw:
        raise HTTPException(status_code=404, detail="PlannedWorkout non trouvé")
    if pw.user_id != user_id:
        raise HTTPException(status_code=403, detail="Accès interdit à cette séance")
    return pw


# Récupère tous les CycleWorkout de l'utilisateur
async def get_all_cycle_workouts(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(CycleWorkout)
        .join(PlannedWorkout, CycleWorkout.planned_workout_uuid == PlannedWorkout.uuid)
        .where(PlannedWorkout.user_id == user_id)
    )
    return result.scalars().all()


async def get_cycle_workout_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(models.CycleWorkout)
        .join(PlannedWorkout, models.CycleWorkout.planned_workout_uuid == PlannedWorkout.uuid)
        .where(
            models.CycleWorkout.uuid == uuid,
            PlannedWorkout.user_id == user_id,
        )
    )
    return result.scalars().first()


async def upsert_cycle_workout(
    db: AsyncSession,
    uuid: str,
    data: schemas.CycleWorkoutCreate,
    user_id: int
):
    # Cascade ownership : le planned_workout cible doit appartenir au user
    await assert_user_owns_planned_workout(db, data.planned_workout_uuid, user_id)

    # Recherche par UUID
    existing_result = await db.execute(
        select(models.CycleWorkout).where(models.CycleWorkout.uuid == uuid)
    )
    existing = existing_result.scalars().first()

    if existing:
        # Si update, l'ancien planned_workout parent doit aussi appartenir au user
        await assert_user_owns_planned_workout(db, existing.planned_workout_uuid, user_id)
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(data.updated_at, existing.updated_at):
            return existing
        for key, value in data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing
    else:
        # Création
        payload = data.model_dump(exclude={"uuid"})
        payload["uuid"] = uuid
        new_cw = models.CycleWorkout(**payload)
        db.add(new_cw)
        await db.commit()
        await db.refresh(new_cw)
        return new_cw


# Upsert en masse
async def bulk_upsert_cycle_workouts(db: AsyncSession, cws: list[schemas.CycleWorkoutCreate], user_id: int):
    result = []
    for cw in cws:
        new_cw = await upsert_cycle_workout(
            db,
            cw.uuid,   # 👈 identifiant
            cw,        # 👈 payload complet
            user_id
        )
        result.append(new_cw)
    return result



# Supprime un CycleWorkout par uuid (canonique : retourne bool)
async def delete_cycle_workout(db: AsyncSession, uuid: str, user_id: int) -> bool:
    cw = await get_cycle_workout_by_uuid(db, uuid, user_id)
    if not cw:
        return False
    await db.delete(cw)
    await db.commit()
    return True
