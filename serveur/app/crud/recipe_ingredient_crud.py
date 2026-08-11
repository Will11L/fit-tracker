# app/crud/recipe_ingredient_crud.py
# Nutrition V1 — entité enfant (ownership indirect RecipeIngredient -> Recipe -> User,
# cascade ownership politique 8 ; le food référencé doit aussi appartenir au user).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app import models
from app.models.recipe_ingredient import RecipeIngredient
from app.schemas import RecipeIngredientCreate
from app.crud._concurrency import is_payload_stale


# Vérifie si une recette appartient à un utilisateur (cascade ownership)
async def is_recipe_owned_by_user_uuid(db: AsyncSession, recipe_uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(models.Recipe).where(
            models.Recipe.uuid == recipe_uuid,
            models.Recipe.user_id == user_id,
        )
    )
    return res.scalar_one_or_none() is not None


async def get_all_recipe_ingredients(db: AsyncSession, user_id: int) -> Sequence[RecipeIngredient]:
    res = await db.execute(
        select(RecipeIngredient)
        .join(models.Recipe, RecipeIngredient.recipe_uuid == models.Recipe.uuid)
        .where(models.Recipe.user_id == user_id)
    )
    return res.scalars().all()


async def get_recipe_ingredient_by_uuid(db: AsyncSession, uuid: str) -> RecipeIngredient | None:
    res = await db.execute(select(RecipeIngredient).where(RecipeIngredient.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_recipe_ingredient(
    db: AsyncSession, uuid: str, dto: RecipeIngredientCreate, user_id: int
) -> RecipeIngredient:
    # Cascade ownership : la recette cible doit appartenir au user
    if not await is_recipe_owned_by_user_uuid(db, dto.recipe_uuid, user_id):
        raise HTTPException(status_code=403, detail="Recette cible non autorisée")

    # Cascade ownership : le food référencé doit appartenir au user
    food_res = await db.execute(
        select(models.Food).where(
            models.Food.uuid == dto.food_uuid,
            models.Food.user_id == user_id,
        )
    )
    if not food_res.scalar_one_or_none():
        raise HTTPException(status_code=403, detail="Aliment cible non autorisé")

    res = await db.execute(select(RecipeIngredient).where(RecipeIngredient.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        # Si update, l'ancien parent doit aussi appartenir au user
        if not await is_recipe_owned_by_user_uuid(db, existing.recipe_uuid, user_id):
            raise HTTPException(status_code=403, detail="Accès interdit à cet ingrédient")
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
    ingredient = RecipeIngredient(**data)
    db.add(ingredient)
    await db.commit()
    await db.refresh(ingredient)
    return ingredient


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_recipe_ingredients(
    db: AsyncSession,
    items: list[RecipeIngredientCreate],
    user_id: int,
) -> list[RecipeIngredient]:
    out: list[RecipeIngredient] = []
    for dto in items:
        out.append(await upsert_recipe_ingredient(db, dto.uuid, dto, user_id))
    return out


async def delete_recipe_ingredient(db: AsyncSession, uuid: str, user_id: int) -> bool:
    ingredient = await get_recipe_ingredient_by_uuid(db, uuid)
    if not ingredient:
        return False
    if not await is_recipe_owned_by_user_uuid(db, ingredient.recipe_uuid, user_id):
        return False
    await db.delete(ingredient)
    await db.commit()
    return True
