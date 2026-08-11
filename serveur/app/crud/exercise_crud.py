from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import Exercise
from app import schemas
from fastapi import HTTPException
from app.crud._concurrency import is_payload_stale


# Récupérer tous les exercices d'un utilisateur
async def get_all_exercises(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(Exercise).where(Exercise.user_id == user_id)
    )
    return result.scalars().all()


# Récupérer un exercice par UUID
async def get_exercise_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(Exercise).where(Exercise.uuid == uuid, Exercise.user_id == user_id)
    )
    return result.scalars().first()


# Upsert un exercice
async def upsert_exercise(db: AsyncSession, uuid: str, exercise_data: schemas.ExerciseCreate, user_id: int):
    result = await db.execute(select(Exercise).where(Exercise.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cet exercice")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(exercise_data.updated_at, existing.updated_at):
            return existing
        for key, value in exercise_data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création
    new_exercise = Exercise(**exercise_data.model_dump(), user_id=user_id)
    db.add(new_exercise)
    await db.commit()
    await db.refresh(new_exercise)
    return new_exercise


# Upsert en masse
async def bulk_upsert_exercises(db: AsyncSession, exercises: list[schemas.ExerciseCreate], user_id: int):
    result_list = []

    for ex in exercises:
        existing_result = await db.execute(
            select(Exercise).where(Exercise.uuid == ex.uuid, Exercise.user_id == user_id)
        )
        existing = existing_result.scalars().first()

        if existing:
            for key, value in ex.model_dump().items():
                if key not in ("uuid", "user_id"):
                    setattr(existing, key, value)
            result_list.append(existing)
        else:
            data = ex.model_dump()
            data["user_id"] = user_id
            new_exercise = Exercise(**data)
            db.add(new_exercise)
            result_list.append(new_exercise)

    await db.commit()
    return result_list


# Supprimer un exercice
async def delete_exercise(db: AsyncSession, uuid: str, user_id: int):
    exercise = await get_exercise_by_uuid(db, uuid, user_id)
    if not exercise:
        raise HTTPException(status_code=404, detail="Exercice non trouvé ou accès interdit")
    await db.delete(exercise)
    await db.commit()
    return True
