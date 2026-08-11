from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException
from app import models, schemas
from app.crud._concurrency import is_payload_stale


# Obtenir toutes les ActualWorkoutExercise d'un utilisateur
async def get_all_actual_workout_exercises(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.ActualWorkoutExercise)
        .join(models.ActualWorkout)
        .where(models.ActualWorkout.user_id == user_id)
    )
    return result.scalars().all()


# Obtenir une ActualWorkoutExercise par UUID
async def get_actual_workout_exercise_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(models.ActualWorkoutExercise)
        .join(models.ActualWorkout)
        .where(
            models.ActualWorkoutExercise.uuid == uuid,
            models.ActualWorkout.user_id == user_id
        )
    )
    return result.scalars().first()


# Supprimer une ActualWorkoutExercise (canonique : retourne bool)
async def delete_actual_workout_exercise(db: AsyncSession, uuid: str, user_id: int) -> bool:
    obj = await get_actual_workout_exercise_by_uuid(db, uuid, user_id)
    if not obj:
        return False
    await db.delete(obj)
    await db.commit()
    return True


# Upsert une ActualWorkoutExercise
async def upsert_actual_workout_exercise(db: AsyncSession, uuid: str, data: schemas.ActualWorkoutExerciseCreate, user_id: int):
    # Vérifie accès au workout
    workout_result = await db.execute(
        select(models.ActualWorkout).where(
            models.ActualWorkout.uuid == data.actual_workout_uuid,
            models.ActualWorkout.user_id == user_id
        )
    )
    workout = workout_result.scalars().first()
    if not workout:
        raise HTTPException(status_code=403, detail="Access denied to actual workout")

    # Vérifie que l'exercice existe
    exercise_result = await db.execute(
        select(models.Exercise).where(models.Exercise.uuid == data.exercise_uuid)
    )
    exercise = exercise_result.scalars().first()
    if not exercise:
        raise HTTPException(status_code=404, detail="Exercise not found")

    # Recherche par UUID
    existing_result = await db.execute(
        select(models.ActualWorkoutExercise).where(models.ActualWorkoutExercise.uuid == uuid)
    )
    existing = existing_result.scalars().first()

    if existing:
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(data.updated_at, existing.updated_at):
            return existing
        for key, value in data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing
    else:
        payload = data.model_dump(exclude={"uuid"})
        payload["uuid"] = uuid
        new_exercise = models.ActualWorkoutExercise(**payload)
        db.add(new_exercise)
        await db.commit()
        await db.refresh(new_exercise)
        return new_exercise


# Upsert en masse
async def upsert_many_actual_workout_exercises(db: AsyncSession, exercises: list[schemas.ActualWorkoutExerciseCreate], user_id: int):
    results = []

    for data in exercises:
        # Cherche un enregistrement existant
        existing_result = await db.execute(
            select(models.ActualWorkoutExercise)
            .join(models.ActualWorkout)
            .where(
                models.ActualWorkoutExercise.actual_workout_uuid == data.actual_workout_uuid,
                models.ActualWorkoutExercise.exercise_uuid == data.exercise_uuid,
                models.ActualWorkout.user_id == user_id
            )
        )
        existing = existing_result.scalars().first()

        if existing:
            for key, value in data.model_dump().items():
                setattr(existing, key, value)
            db.add(existing)
            results.append(existing)
        else:
            # Sécurité : vérifier accès workout
            workout_result = await db.execute(
                select(models.ActualWorkout).where(
                    models.ActualWorkout.uuid == data.actual_workout_uuid,
                    models.ActualWorkout.user_id == user_id
                )
            )
            workout = workout_result.scalars().first()
            if not workout:
                raise HTTPException(status_code=403, detail="Access denied to actual workout")

            # Vérifie que l'exercice existe
            exercise_result = await db.execute(
                select(models.Exercise).where(models.Exercise.uuid == data.exercise_uuid)
            )
            exercise = exercise_result.scalars().first()
            if not exercise:
                print(f"Exercise not found for UUID: {data.exercise_uuid}")
                raise HTTPException(status_code=404, detail="Exercise not found")

            new_exercise = models.ActualWorkoutExercise(**data.model_dump())
            db.add(new_exercise)
            results.append(new_exercise)

    await db.commit()
    return results
