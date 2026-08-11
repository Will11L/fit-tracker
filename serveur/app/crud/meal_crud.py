# app/crud/meal_crud.py
# Nutrition V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.meal import Meal
from app.schemas import MealCreate
from app.crud._concurrency import is_payload_stale


async def get_all_meals(db: AsyncSession, user_id: int) -> Sequence[Meal]:
    res = await db.execute(select(Meal).where(Meal.user_id == user_id))
    return res.scalars().all()


async def get_meal_by_uuid(db: AsyncSession, uuid: str) -> Meal | None:
    res = await db.execute(select(Meal).where(Meal.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_meal(db: AsyncSession, uuid: str, dto: MealCreate, user_id: int) -> Meal:
    res = await db.execute(select(Meal).where(Meal.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce repas")
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
    meal = Meal(**data)
    db.add(meal)
    await db.commit()
    await db.refresh(meal)
    return meal


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_meals(
    db: AsyncSession,
    items: list[MealCreate],
    user_id: int,
) -> list[Meal]:
    out: list[Meal] = []
    for dto in items:
        out.append(await upsert_meal(db, dto.uuid, dto, user_id))
    return out


async def delete_meal(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(Meal).where(Meal.uuid == uuid, Meal.user_id == user_id)
    )
    meal = res.scalar_one_or_none()
    if not meal:
        return False
    await db.delete(meal)
    await db.commit()
    return True
