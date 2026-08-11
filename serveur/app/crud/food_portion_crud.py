# app/crud/food_portion_crud.py
# Nutrition V1 — entité enfant (ownership indirect FoodPortion -> Food -> User,
# cascade ownership politique 8 ; squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app import models
from app.models.food_portion import FoodPortion
from app.schemas import FoodPortionCreate
from app.crud._concurrency import is_payload_stale


# Vérifie si un food appartient à un utilisateur (cascade ownership)
async def is_food_owned_by_user_uuid(db: AsyncSession, food_uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(models.Food).where(
            models.Food.uuid == food_uuid,
            models.Food.user_id == user_id,
        )
    )
    return res.scalar_one_or_none() is not None


async def get_all_food_portions(db: AsyncSession, user_id: int) -> Sequence[FoodPortion]:
    res = await db.execute(
        select(FoodPortion)
        .join(models.Food, FoodPortion.food_uuid == models.Food.uuid)
        .where(models.Food.user_id == user_id)
    )
    return res.scalars().all()


async def get_food_portion_by_uuid(db: AsyncSession, uuid: str) -> FoodPortion | None:
    res = await db.execute(select(FoodPortion).where(FoodPortion.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_food_portion(db: AsyncSession, uuid: str, dto: FoodPortionCreate, user_id: int) -> FoodPortion:
    # Cascade ownership : le food cible doit appartenir au user
    if not await is_food_owned_by_user_uuid(db, dto.food_uuid, user_id):
        raise HTTPException(status_code=403, detail="Aliment cible non autorisé")

    res = await db.execute(select(FoodPortion).where(FoodPortion.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        # Si update, l'ancien parent doit aussi appartenir au user
        if not await is_food_owned_by_user_uuid(db, existing.food_uuid, user_id):
            raise HTTPException(status_code=403, detail="Accès interdit à cette portion")
        if is_payload_stale(dto.updated_at, existing.updated_at):
            return existing
        for key, value in dto.model_dump().items():
            if key != "uuid":
                setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    data = dto.model_dump()
    data["uuid"] = uuid
    portion = FoodPortion(**data)
    db.add(portion)
    await db.commit()
    await db.refresh(portion)
    return portion


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_food_portions(
    db: AsyncSession,
    items: list[FoodPortionCreate],
    user_id: int,
) -> list[FoodPortion]:
    out: list[FoodPortion] = []
    for dto in items:
        out.append(await upsert_food_portion(db, dto.uuid, dto, user_id))
    return out


async def delete_food_portion(db: AsyncSession, uuid: str, user_id: int) -> bool:
    portion = await get_food_portion_by_uuid(db, uuid)
    if not portion:
        return False
    if not await is_food_owned_by_user_uuid(db, portion.food_uuid, user_id):
        return False
    await db.delete(portion)
    await db.commit()
    return True
