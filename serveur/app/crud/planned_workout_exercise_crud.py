from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app import models, schemas
from fastapi import HTTPException
from app.models import PlannedWorkoutExercise
from app.crud._concurrency import is_payload_stale


# Vérifie si un planned workout appartient à un utilisateur
async def is_planned_workout_owned_by_user_uuid(db: AsyncSession, planned_workout_uuid: str, user_id: int) -> bool:
    result = await db.execute(
        select(models.PlannedWorkout).where(
            models.PlannedWorkout.uuid == planned_workout_uuid,
            models.PlannedWorkout.user_id == user_id
        )
    )
    workout = result.scalars().first()
    return workout is not None


# Récupère un PlannedWorkoutExercise par UUID
async def get_planned_workout_exercise_by_uuid(db: AsyncSession, exercise_uuid: str):
    result = await db.execute(
        select(models.PlannedWorkoutExercise).where(models.PlannedWorkoutExercise.uuid == exercise_uuid)
    )
    return result.scalars().first()


# Récupère tous les PlannedWorkoutExercise pour un utilisateur
async def get_all_planned_workout_exercises_for_user(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.PlannedWorkoutExercise)
        .join(models.PlannedWorkout, models.PlannedWorkoutExercise.planned_workout_uuid == models.PlannedWorkout.uuid)
        .join(models.Exercise, models.PlannedWorkoutExercise.exercise_uuid == models.Exercise.uuid)
        .where(
            models.PlannedWorkout.user_id == user_id,
            models.Exercise.user_id == user_id
        )
    )
    return result.scalars().all()


# Supprime un PlannedWorkoutExercise
async def delete_planned_workout_exercise_by_uuid(db: AsyncSession, exercise_uuid: str, user_id: int):
    ex = await get_planned_workout_exercise_by_uuid(db, exercise_uuid)
    if ex:
        owned = await is_planned_workout_owned_by_user_uuid(db, ex.planned_workout_uuid, user_id)
        if not owned:
            raise HTTPException(status_code=403, detail="Delete interdit à cet exercice")
        await db.delete(ex)
        await db.commit()
        return True
    return False


# Upsert un PlannedWorkoutExercise
async def upsert_planned_workout_exercise(
    db: AsyncSession,
    uuid: str,
    data: schemas.PlannedWorkoutExerciseCreate,
    user_id: int
):
    # Cascade ownership : le planned_workout cible doit appartenir au user
    if not await is_planned_workout_owned_by_user_uuid(db, data.planned_workout_uuid, user_id):
        raise HTTPException(status_code=403, detail="Planned workout cible non autorisé")

    # Cascade ownership : l'exercice cible doit appartenir au user
    exercise_result = await db.execute(
        select(models.Exercise).where(
            models.Exercise.uuid == data.exercise_uuid,
            models.Exercise.user_id == user_id,
        )
    )
    if not exercise_result.scalars().first():
        raise HTTPException(status_code=403, detail="Exercice cible non autorisé")

    result = await db.execute(select(PlannedWorkoutExercise).where(PlannedWorkoutExercise.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        # Si update, l'ancien parent doit aussi appartenir au user
        if not await is_planned_workout_owned_by_user_uuid(db, existing.planned_workout_uuid, user_id):
            raise HTTPException(status_code=403, detail="Accès interdit à cet exercice planifié")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(data.updated_at, existing.updated_at):
            return existing
        for key, value in data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création
    new_pe = PlannedWorkoutExercise(**data.model_dump())
    db.add(new_pe)
    await db.commit()
    await db.refresh(new_pe)
    return new_pe


# Upsert en masse des PlannedWorkoutExercise
async def upsert_many_planned_workout_exercises(db: AsyncSession, exercises: list[schemas.PlannedWorkoutExerciseCreate], user_id: int):
    result_list = []

    for e in exercises:
        data = e.model_dump()

        # Vérifier que le PlannedWorkout existe
        workout_result = await db.execute(
            select(models.PlannedWorkout).where(
                models.PlannedWorkout.uuid == e.planned_workout_uuid,
                models.PlannedWorkout.user_id == user_id
            )
        )
        workout = workout_result.scalars().first()
        if not workout:
            raise HTTPException(status_code=404, detail=f"Planned workout {e.planned_workout_uuid} introuvable")

        # Vérifier que l'exercice existe
        exercise_result = await db.execute(
            select(models.Exercise).where(
                models.Exercise.uuid == e.exercise_uuid,
                models.Exercise.user_id == user_id
            )
        )
        exercise = exercise_result.scalars().first()
        if not exercise:
            raise HTTPException(status_code=404, detail=f"Exercise {e.exercise_uuid} introuvable")

        data["planned_workout_uuid"] = workout.uuid
        data["exercise_uuid"] = exercise.uuid

        # Vérifier si l'entrée existe déjà
        existing_result = await db.execute(
            select(models.PlannedWorkoutExercise).where(models.PlannedWorkoutExercise.uuid == e.uuid)
        )
        existing = existing_result.scalars().first()

        if existing:
            for key, value in data.items():
                if key != "id":
                    setattr(existing, key, value)
            result_list.append(existing)
        else:
            new_ex = models.PlannedWorkoutExercise(**data)
            db.add(new_ex)
            result_list.append(new_ex)

    await db.commit()

    for ex in result_list:
        await db.refresh(ex)

    return result_list
