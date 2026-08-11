# app/crud/food_crud.py
# Nutrition V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.food import Food
from app.schemas import FoodCreate
from app.crud._concurrency import is_payload_stale


async def get_all_foods(db: AsyncSession, user_id: int) -> Sequence[Food]:
    res = await db.execute(select(Food).where(Food.user_id == user_id))
    return res.scalars().all()


async def get_food_by_uuid(db: AsyncSession, uuid: str) -> Food | None:
    res = await db.execute(select(Food).where(Food.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_food(db: AsyncSession, uuid: str, dto: FoodCreate, user_id: int) -> Food:
    res = await db.execute(select(Food).where(Food.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cet aliment")
        # Last-write-wins : skip si payload plus ancien que serveur
        if is_payload_stale(dto.updated_at, existing.updated_at):
            return existing
        for key, value in dto.model_dump().items():
            if key not in ("uuid", "user_id"):
                setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création : injecte user_id depuis l'auth (politique sécurité V2.2)
    data = dto.model_dump()
    data["user_id"] = user_id
    data["uuid"] = uuid
    food = Food(**data)
    db.add(food)
    await db.commit()
    await db.refresh(food)
    return food


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_foods(
    db: AsyncSession,
    items: list[FoodCreate],
    user_id: int,
) -> list[Food]:
    out: list[Food] = []
    for dto in items:
        out.append(await upsert_food(db, dto.uuid, dto, user_id))
    return out


async def delete_food(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(Food).where(Food.uuid == uuid, Food.user_id == user_id)
    )
    food = res.scalar_one_or_none()
    if not food:
        return False
    await db.delete(food)
    await db.commit()
    return True
