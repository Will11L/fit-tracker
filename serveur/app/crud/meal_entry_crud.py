# app/crud/meal_entry_crud.py
# Nutrition V1 — entité enfant centrale (ownership indirect MealEntry -> Meal -> User,
# cascade ownership politique 8). Snapshot D5 : macros figées dans le payload,
# food_uuid/recipe_uuid = références informatives nullables (SET NULL en DB) —
# si fournies, elles doivent appartenir au user.
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app import models
from app.models.meal_entry import MealEntry
from app.schemas import MealEntryCreate
from app.crud._concurrency import is_payload_stale


# Vérifie si un meal appartient à un utilisateur (cascade ownership)
async def is_meal_owned_by_user_uuid(db: AsyncSession, meal_uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(models.Meal).where(
            models.Meal.uuid == meal_uuid,
            models.Meal.user_id == user_id,
        )
    )
    return res.scalar_one_or_none() is not None


async def get_all_meal_entries(db: AsyncSession, user_id: int) -> Sequence[MealEntry]:
    res = await db.execute(
        select(MealEntry)
        .join(models.Meal, MealEntry.meal_uuid == models.Meal.uuid)
        .where(models.Meal.user_id == user_id)
    )
    return res.scalars().all()


async def get_meal_entry_by_uuid(db: AsyncSession, uuid: str) -> MealEntry | None:
    res = await db.execute(select(MealEntry).where(MealEntry.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_meal_entry(db: AsyncSession, uuid: str, dto: MealEntryCreate, user_id: int) -> MealEntry:
    # Cascade ownership : le meal cible doit appartenir au user
    if not await is_meal_owned_by_user_uuid(db, dto.meal_uuid, user_id):
        raise HTTPException(status_code=403, detail="Repas cible non autorisé")

    # Références optionnelles : si fournies, elles doivent appartenir au user
    if dto.food_uuid is not None:
        food_res = await db.execute(
            select(models.Food).where(
                models.Food.uuid == dto.food_uuid,
                models.Food.user_id == user_id,
            )
        )
        if not food_res.scalar_one_or_none():
            raise HTTPException(status_code=403, detail="Aliment cible non autorisé")

    if dto.recipe_uuid is not None:
        recipe_res = await db.execute(
            select(models.Recipe).where(
                models.Recipe.uuid == dto.recipe_uuid,
                models.Recipe.user_id == user_id,
            )
        )
        if not recipe_res.scalar_one_or_none():
            raise HTTPException(status_code=403, detail="Recette cible non autorisée")

    res = await db.execute(select(MealEntry).where(MealEntry.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        # Si update, l'ancien parent doit aussi appartenir au user
        if not await is_meal_owned_by_user_uuid(db, existing.meal_uuid, user_id):
            raise HTTPException(status_code=403, detail="Accès interdit à cette entrée de repas")
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
    entry = MealEntry(**data)
    db.add(entry)
    await db.commit()
    await db.refresh(entry)
    return entry


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_meal_entries(
    db: AsyncSession,
    items: list[MealEntryCreate],
    user_id: int,
) -> list[MealEntry]:
    out: list[MealEntry] = []
    for dto in items:
        out.append(await upsert_meal_entry(db, dto.uuid, dto, user_id))
    return out


async def delete_meal_entry(db: AsyncSession, uuid: str, user_id: int) -> bool:
    entry = await get_meal_entry_by_uuid(db, uuid)
    if not entry:
        return False
    if not await is_meal_owned_by_user_uuid(db, entry.meal_uuid, user_id):
        return False
    await db.delete(entry)
    await db.commit()
    return True
