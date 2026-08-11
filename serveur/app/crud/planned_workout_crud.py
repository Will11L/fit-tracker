from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app import models, schemas
from fastapi import HTTPException
from app.models import PlannedWorkout
from app.crud._concurrency import is_payload_stale


# Récupérer tous les PlannedWorkout d'un utilisateur
async def get_user_planned_workouts(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.PlannedWorkout).where(models.PlannedWorkout.user_id == user_id)
    )
    return result.scalars().all()


# Récupérer un PlannedWorkout par UUID
async def get_planned_workout_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(models.PlannedWorkout).where(
            models.PlannedWorkout.uuid == uuid,
            models.PlannedWorkout.user_id == user_id
        )
    )
    return result.scalars().first()


# Upsert un planned_workout
async def upsert_planned_workout(
    db: AsyncSession,
    uuid: str,
    planned_workout_data: schemas.PlannedWorkoutCreate,
    user_id: int
):
    result = await db.execute(select(PlannedWorkout).where(PlannedWorkout.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce planned workout")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(planned_workout_data.updated_at, existing.updated_at):
            return existing
        for key, value in planned_workout_data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création
    new_planned_workout = PlannedWorkout(
        **planned_workout_data.model_dump(),
        user_id=user_id,
    )
    db.add(new_planned_workout)
    await db.commit()
    await db.refresh(new_planned_workout)
    return new_planned_workout


# Upsert en masse
async def bulk_upsert_planned_workouts(
    db: AsyncSession,
    workouts: list[schemas.PlannedWorkoutCreate],
    user_id: int
):
    results = []
    for workout in workouts:
        obj = await upsert_planned_workout(db, workout.uuid, workout, user_id)
        results.append(obj)
    return results


# Supprimer un PlannedWorkout par UUID
async def delete_planned_workout_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(models.PlannedWorkout).where(
            models.PlannedWorkout.uuid == uuid,
            models.PlannedWorkout.user_id == user_id
        )
    )
    workout = result.scalars().first()

    if workout:
        await db.delete(workout)
        await db.commit()
        return True
    return False
