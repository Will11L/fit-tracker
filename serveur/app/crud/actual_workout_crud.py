from fastapi import HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app import models, schemas
from app.crud._concurrency import is_payload_stale

# Lire tous les workouts d'un utilisateur
async def get_user_actual_workouts(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.ActualWorkout).where(models.ActualWorkout.user_id == user_id)
    )
    return result.scalars().all()

# Lire un workout par UUID
async def get_actual_workout_by_uuid(db: AsyncSession, workout_uuid: str, user_id: int = None):
    stmt = select(models.ActualWorkout).where(models.ActualWorkout.uuid == workout_uuid)
    if user_id is not None:
        stmt = stmt.where(models.ActualWorkout.user_id == user_id)

    result = await db.execute(stmt)
    return result.scalars().first()

# Créer un workout
async def create_actual_workout(db: AsyncSession, user_id: int, workout_data: dict):
    new_workout = models.ActualWorkout(**workout_data, user_id=user_id)
    db.add(new_workout)
    await db.commit()
    await db.refresh(new_workout)
    return new_workout

# Insérer plusieurs workouts
async def insert_many_actual_workouts(db: AsyncSession, user_id: int, workouts: list[dict]):
    new_workouts = [models.ActualWorkout(**w, user_id=user_id) for w in workouts]
    db.add_all(new_workouts)
    await db.commit()
    return new_workouts

# Upsert workout
async def upsert_actual_workout(
    db: AsyncSession,
    workout_uuid: str,
    data: schemas.ActualWorkoutCreate,
    user_id: int,
):
    result = await db.execute(
        select(models.ActualWorkout).where(models.ActualWorkout.uuid == workout_uuid)
    )
    workout = result.scalars().first()

    if workout:
        if workout.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cette séance")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(data.updated_at, workout.updated_at):
            return workout
        for key, value in data.model_dump().items():
            setattr(workout, key, value)
        await db.commit()
        await db.refresh(workout)
        return workout

    # Création
    new_workout = models.ActualWorkout(**data.model_dump(), user_id=user_id)
    db.add(new_workout)
    await db.commit()
    await db.refresh(new_workout)
    return new_workout

# Upsert en bulk
async def upsert_many_actual_workouts(
    db: AsyncSession,
    workouts: list[schemas.ActualWorkoutCreate],
    user_id: int,
):
    results = []
    for workout in workouts:
        obj = await upsert_actual_workout(db, workout.uuid, workout, user_id)
        results.append(obj)
    return results


# Supprimer un workout
async def delete_actual_workout(db: AsyncSession, workout_uuid: str, user_id: int):
    result = await db.execute(
        select(models.ActualWorkout).where(
            models.ActualWorkout.uuid == workout_uuid,
            models.ActualWorkout.user_id == user_id,
        )
    )
    workout = result.scalars().first()
    if not workout:
        return False
    await db.delete(workout)
    await db.commit()
    return True
