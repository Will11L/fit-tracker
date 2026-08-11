from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app import models, schemas
from fastapi import HTTPException
from app.crud._concurrency import is_payload_stale


# Récupère tous les sets pour un exercice donné
async def get_sets_for_actual_workout_exercise(db: AsyncSession, actual_workout_exercise_uuid: str):
    result = await db.execute(
        select(models.ActualWorkoutSet).where(
            models.ActualWorkoutSet.actual_workout_exercise_uuid == actual_workout_exercise_uuid
        )
    )
    return result.scalars().all()


# Récupère un set par UUID (avec vérification user_id)
async def get_actual_workout_set_by_uuid(db: AsyncSession, set_uuid: str, user_id: int):
    result = await db.execute(
        select(models.ActualWorkoutSet)
        .join(models.ActualWorkoutExercise, models.ActualWorkoutSet.actual_workout_exercise_uuid == models.ActualWorkoutExercise.uuid)
        .join(models.ActualWorkout, models.ActualWorkoutExercise.actual_workout_uuid == models.ActualWorkout.uuid)
        .where(
            models.ActualWorkoutSet.uuid == set_uuid,
            models.ActualWorkout.user_id == user_id
        )
    )
    return result.scalars().first()


# Récupère tous les sets d'un utilisateur
async def get_all_actual_workout_sets(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.ActualWorkoutSet)
        .join(models.ActualWorkoutExercise, models.ActualWorkoutSet.actual_workout_exercise_uuid == models.ActualWorkoutExercise.uuid)
        .join(models.ActualWorkout, models.ActualWorkoutExercise.actual_workout_uuid == models.ActualWorkout.uuid)
        .where(models.ActualWorkout.user_id == user_id)
    )
    return result.scalars().all()


# Upsert un set par UUID (signature canonique : db, uuid, dto, user_id)
async def upsert_actual_workout_set_by_uuid(
    db: AsyncSession,
    uuid: str,
    dto: schemas.ActualWorkoutSetCreate,
    user_id: int,
) -> models.ActualWorkoutSet:
    # 1. Check si l'uuid existe globalement (sans filter user_id) — évite UniqueViolation
    #    si user B tente d'upsert un uuid déjà créé par user A (bug T1.1.c-bis).
    res_global = await db.execute(
        select(models.ActualWorkoutSet).where(models.ActualWorkoutSet.uuid == uuid)
    )
    existing_global = res_global.scalar_one_or_none()

    if existing_global:
        # Vérif ownership cascade (via JOIN exercise -> workout -> user_id)
        res_owned = await db.execute(
            select(models.ActualWorkoutSet)
            .join(
                models.ActualWorkoutExercise,
                models.ActualWorkoutSet.actual_workout_exercise_uuid == models.ActualWorkoutExercise.uuid,
            )
            .join(
                models.ActualWorkout,
                models.ActualWorkoutExercise.actual_workout_uuid == models.ActualWorkout.uuid,
            )
            .where(
                models.ActualWorkoutSet.uuid == uuid,
                models.ActualWorkout.user_id == user_id,
            )
        )
        if res_owned.scalar_one_or_none() is None:
            raise HTTPException(status_code=403, detail="Accès interdit à ce set")

        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(dto.updated_at, existing_global.updated_at):
            return existing_global
        # Écrasement total via model_dump (canonique)
        for key, value in dto.model_dump().items():
            if key not in ("uuid",):
                setattr(existing_global, key, value)
        await db.commit()
        await db.refresh(existing_global)
        return existing_global

    # 2. Création
    data = dto.model_dump()
    data["uuid"] = uuid
    new_set = models.ActualWorkoutSet(**data)
    db.add(new_set)
    await db.commit()
    await db.refresh(new_set)
    return new_set


# Upsert en masse
async def upsert_many_actual_workout_sets(
    db: AsyncSession,
    sets: list[schemas.ActualWorkoutSetCreate],
    user_id: int,
):
    results = []
    for s in sets:
        obj = await upsert_actual_workout_set_by_uuid(db, s.uuid, s, user_id)
        results.append(obj)
    return results


# Supprimer un set (canonique : retourne bool)
async def delete_set_from_actual_workout_by_uuid(db: AsyncSession, set_uuid: str, user_id: int) -> bool:
    result = await db.execute(
        select(models.ActualWorkoutSet)
        .join(models.ActualWorkoutExercise, models.ActualWorkoutSet.actual_workout_exercise_uuid == models.ActualWorkoutExercise.uuid)
        .join(models.ActualWorkout, models.ActualWorkoutExercise.actual_workout_uuid == models.ActualWorkout.uuid)
        .where(
            models.ActualWorkoutSet.uuid == set_uuid,
            models.ActualWorkout.user_id == user_id,
        )
    )
    set_obj = result.scalars().first()
    if not set_obj:
        return False
    await db.delete(set_obj)
    await db.commit()
    return True
