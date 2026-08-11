from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException
from app import models, schemas
from app.models import SupersetExercise, SupersetGroup, Exercise
from app.crud._concurrency import is_payload_stale

# 🔁 Helpers

async def get_group_by_uuid(db: AsyncSession, uuid: str):
    result = await db.execute(
        select(models.SupersetGroup).where(models.SupersetGroup.uuid == uuid)
    )
    group = result.scalars().first()
    if not group:
        raise HTTPException(status_code=404, detail="Groupe introuvable")
    return group


async def get_exercise_by_uuid(db: AsyncSession, uuid: str):
    result = await db.execute(
        select(models.Exercise).where(models.Exercise.uuid == uuid)
    )
    exercise = result.scalars().first()
    if not exercise:
        raise HTTPException(status_code=404, detail="Exercice introuvable")
    return exercise


async def is_superset_exercise_owned_by_user(
    db: AsyncSession, superset_exercise: models.SupersetExercise, user_id: int
) -> bool:
    result = await db.execute(
        select(models.SupersetGroup.user_id)
        .where(models.SupersetGroup.uuid == superset_exercise.superset_group_uuid)
    )
    owner_id = result.scalar_one_or_none()
    return owner_id == user_id

# 📥 CRUD

async def get_superset_exercise_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(SupersetExercise)
        .join(SupersetGroup, SupersetExercise.superset_group_uuid == SupersetGroup.uuid)
        .where(SupersetExercise.uuid == uuid)
        .where(SupersetGroup.user_id == user_id)
    )
    return result.scalars().first()


async def get_all_superset_exercises(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.SupersetExercise)
        .join(
            models.SupersetGroup,
            models.SupersetExercise.superset_group_uuid == models.SupersetGroup.uuid
        )
        .join(
            models.Exercise,
            models.SupersetExercise.exercise_uuid == models.Exercise.uuid
        )
        .where(models.SupersetGroup.user_id == user_id)
        .where(models.Exercise.user_id == user_id)
    )
    return result.scalars().all()


# crud/superset_exercise_crud.py
async def upsert_superset_exercise(
    db: AsyncSession, uuid: str, data: schemas.SupersetExerciseCreate, user_id: int
):
    # Vérifie que le SupersetGroup existe et appartient bien à l’utilisateur
    group_result = await db.execute(
        select(models.SupersetGroup).where(
            models.SupersetGroup.uuid == data.superset_group_uuid,
            models.SupersetGroup.user_id == user_id
        )
    )
    group = group_result.scalars().first()
    if not group:
        raise HTTPException(status_code=403, detail="Access denied to superset group")

    # Vérifie que l’exercice existe
    exercise_result = await db.execute(
        select(models.Exercise).where(models.Exercise.uuid == data.exercise_uuid)
    )
    exercise = exercise_result.scalars().first()
    if not exercise:
        raise HTTPException(status_code=404, detail="Exercise not found")

    # Cherche si déjà présent
    existing_result = await db.execute(
        select(models.SupersetExercise).where(models.SupersetExercise.uuid == uuid)
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

    # Création
    payload = data.model_dump(exclude={"uuid"})
    payload["uuid"] = uuid
    new_ex = models.SupersetExercise(**payload)
    db.add(new_ex)
    await db.commit()
    await db.refresh(new_ex)
    return new_ex


async def bulk_upsert_superset_exercises(
    db: AsyncSession, exercises: list[schemas.SupersetExerciseCreate], user_id: int
):
    results = []

    for e in exercises:
        # Vérifie accès au superset_group
        group_result = await db.execute(
            select(models.SupersetGroup).where(
                models.SupersetGroup.uuid == e.superset_group_uuid,
                models.SupersetGroup.user_id == user_id
            )
        )
        group = group_result.scalars().first()
        if not group:
            raise HTTPException(status_code=403, detail="Access denied to superset group")

        # Vérifie existence de l’exercice
        exercise_result = await db.execute(
            select(models.Exercise).where(models.Exercise.uuid == e.exercise_uuid)
        )
        exercise = exercise_result.scalars().first()
        if not exercise:
            raise HTTPException(status_code=404, detail=f"Exercise {e.exercise_uuid} not found")

        # Cherche un enregistrement existant
        existing_result = await db.execute(
            select(models.SupersetExercise).where(
                models.SupersetExercise.superset_group_uuid == e.superset_group_uuid,
                models.SupersetExercise.exercise_uuid == e.exercise_uuid,
            )
        )
        existing = existing_result.scalars().first()

        if existing:
            for key, value in e.model_dump().items():
                setattr(existing, key, value)
            results.append(existing)
        else:
            new_ex = models.SupersetExercise(**e.model_dump())
            db.add(new_ex)
            results.append(new_ex)

    await db.commit()
    for r in results:
        await db.refresh(r)

    return results


async def delete_superset_exercise(db: AsyncSession, uuid: str, user_id: int):
    superset_exercise = await get_superset_exercise_by_uuid(db, uuid, user_id)
    if not superset_exercise:
        raise HTTPException(status_code=404, detail="Superset exercise non trouvé ou accès interdit")

    await db.delete(superset_exercise)
    await db.commit()
    return True
