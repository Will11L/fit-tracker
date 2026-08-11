# app/crud/nutrition_goal_crud.py
# Nutrition V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.nutrition_goal import NutritionGoal
from app.schemas import NutritionGoalCreate
from app.crud._concurrency import is_payload_stale


async def get_all_nutrition_goals(db: AsyncSession, user_id: int) -> Sequence[NutritionGoal]:
    res = await db.execute(select(NutritionGoal).where(NutritionGoal.user_id == user_id))
    return res.scalars().all()


async def get_nutrition_goal_by_uuid(db: AsyncSession, uuid: str) -> NutritionGoal | None:
    res = await db.execute(select(NutritionGoal).where(NutritionGoal.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_nutrition_goal(
    db: AsyncSession, uuid: str, dto: NutritionGoalCreate, user_id: int
) -> NutritionGoal:
    res = await db.execute(select(NutritionGoal).where(NutritionGoal.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cet objectif nutrition")
        if is_payload_stale(dto.updated_at, existing.updated_at):
            return existing
        for key, value in dto.model_dump().items():
            if key not in ("uuid", "user_id"):
                setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    data = dto.model_dump()
    data["user_id"] = user_id
    data["uuid"] = uuid
    goal = NutritionGoal(**data)
    db.add(goal)
    await db.commit()
    await db.refresh(goal)
    return goal


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_nutrition_goals(
    db: AsyncSession,
    items: list[NutritionGoalCreate],
    user_id: int,
) -> list[NutritionGoal]:
    out: list[NutritionGoal] = []
    for dto in items:
        out.append(await upsert_nutrition_goal(db, dto.uuid, dto, user_id))
    return out


async def delete_nutrition_goal(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(NutritionGoal).where(NutritionGoal.uuid == uuid, NutritionGoal.user_id == user_id)
    )
    goal = res.scalar_one_or_none()
    if not goal:
        return False
    await db.delete(goal)
    await db.commit()
    return True
