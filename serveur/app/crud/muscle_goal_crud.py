from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import MuscleGoal, Muscle
from app.schemas import MuscleGoalCreate
from fastapi import HTTPException
from app.crud._concurrency import is_payload_stale


# Récupérer tous les objectifs musculaires d'un utilisateur
async def get_all_muscle_goals(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(MuscleGoal).where(MuscleGoal.user_id == user_id)
    )
    return result.scalars().all()


# Récupérer un objectif musculaire par UUID
async def get_muscle_goal_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(MuscleGoal).where(
            MuscleGoal.uuid == uuid,
            MuscleGoal.user_id == user_id
        )
    )
    return result.scalars().first()


# Récupérer un muscle par UUID
async def get_muscle_by_uuid(db: AsyncSession, uuid: str) -> Muscle:
    result = await db.execute(
        select(Muscle).where(Muscle.uuid == uuid)
    )
    muscle = result.scalars().first()
    if not muscle:
        raise HTTPException(status_code=404, detail="Muscle introuvable")
    return muscle


# Upsert un objectif musculaire
async def upsert_muscle_goal(db: AsyncSession, uuid: str, goal_data: MuscleGoalCreate, user_id: int):
    result = await db.execute(select(MuscleGoal).where(MuscleGoal.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cet objectif musculaire")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(goal_data.updated_at, existing.updated_at):
            return existing
        for key, value in goal_data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création si pas trouvé
    new_goal = MuscleGoal(**goal_data.model_dump(), user_id=user_id)
    db.add(new_goal)
    await db.commit()
    await db.refresh(new_goal)
    return new_goal


# Bulk upsert
async def bulk_upsert_muscle_goals(
    db: AsyncSession,
    goals: list[MuscleGoalCreate],
    user_id: int
):
    results = []
    for g in goals:
        goal_obj = await upsert_muscle_goal(db, g.uuid, g, user_id)
        results.append(goal_obj)
    return results


# Supprimer un objectif musculaire
async def delete_muscle_goal(db: AsyncSession, uuid: str, user_id: int):
    goal = await get_muscle_goal_by_uuid(db, uuid, user_id)
    if not goal:
        return False
    await db.delete(goal)
    await db.commit()
    return True
