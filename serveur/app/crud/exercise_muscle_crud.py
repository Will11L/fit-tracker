from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException
from app.models import ExerciseMuscle, Exercise
from app.schemas import ExerciseMuscleCreate
from app.crud._concurrency import is_payload_stale


# Vérifie que l'exercice appartient à l'utilisateur
async def assert_user_owns_exercise(db: AsyncSession, exercise_uuid: str, user_id: int):
    result = await db.execute(
        select(Exercise).where(Exercise.uuid == exercise_uuid)
    )
    exo = result.scalars().first()

    if not exo:
        raise HTTPException(status_code=404, detail="Exercice non trouvé")
    if exo.user_id != user_id:
        raise HTTPException(status_code=403, detail="Accès interdit à cet exercice")


# Récupère tous les liens exercice-muscle d'un utilisateur
async def get_all_exercise_muscles(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(ExerciseMuscle)
        .join(Exercise, ExerciseMuscle.exercise_uuid == Exercise.uuid)
        .where(Exercise.user_id == user_id)
    )
    return result.scalars().all()


# Upsert un exercise_muscle
async def upsert_exercise_muscle(
    db: AsyncSession,
    uuid: str,
    exercise_muscle_data: ExerciseMuscleCreate,
    user_id: int
):
    # Cascade ownership: l'exercice cible (payload) doit appartenir au user
    await assert_user_owns_exercise(db, exercise_muscle_data.exercise_uuid, user_id)

    result = await db.execute(select(ExerciseMuscle).where(ExerciseMuscle.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        # Si on update, l'ancien exercice cible doit aussi appartenir au user
        # (sinon un user pourrait detourner un lien d'un autre user)
        if existing.exercise_uuid != exercise_muscle_data.exercise_uuid:
            await assert_user_owns_exercise(db, existing.exercise_uuid, user_id)
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(exercise_muscle_data.updated_at, existing.updated_at):
            return existing
        for key, value in exercise_muscle_data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création
    new_exercise_muscle = ExerciseMuscle(
        **exercise_muscle_data.model_dump()
    )
    db.add(new_exercise_muscle)
    await db.commit()
    await db.refresh(new_exercise_muscle)
    return new_exercise_muscle


# Upsert en masse
async def bulk_upsert_exercise_muscles(
    db: AsyncSession,
    links: list[ExerciseMuscleCreate],
    user_id: int
):
    result_list = []
    for link in links:
        updated_or_created = await upsert_exercise_muscle(
            db,
            link.uuid,
            link,
            user_id
        )
        result_list.append(updated_or_created)
    return result_list



async def delete_exercise_muscle_by_uuid(
    db: AsyncSession,
    uuid: str,
    user_id: int,
) -> bool:
    result = await db.execute(select(ExerciseMuscle).where(ExerciseMuscle.uuid == uuid))
    existing = result.scalars().first()
    if not existing:
        return False
    # Cascade ownership : l'exercice parent doit appartenir au user
    await assert_user_owns_exercise(db, existing.exercise_uuid, user_id)
    await db.delete(existing)
    await db.commit()
    return True


