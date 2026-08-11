# app/crud/recipe_crud.py
# Nutrition V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.recipe import Recipe
from app.schemas import RecipeCreate
from app.crud._concurrency import is_payload_stale


async def get_all_recipes(db: AsyncSession, user_id: int) -> Sequence[Recipe]:
    res = await db.execute(select(Recipe).where(Recipe.user_id == user_id))
    return res.scalars().all()


async def get_recipe_by_uuid(db: AsyncSession, uuid: str) -> Recipe | None:
    res = await db.execute(select(Recipe).where(Recipe.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_recipe(db: AsyncSession, uuid: str, dto: RecipeCreate, user_id: int) -> Recipe:
    res = await db.execute(select(Recipe).where(Recipe.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cette recette")
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
    recipe = Recipe(**data)
    db.add(recipe)
    await db.commit()
    await db.refresh(recipe)
    return recipe


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_recipes(
    db: AsyncSession,
    items: list[RecipeCreate],
    user_id: int,
) -> list[Recipe]:
    out: list[Recipe] = []
    for dto in items:
        out.append(await upsert_recipe(db, dto.uuid, dto, user_id))
    return out


async def delete_recipe(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(Recipe).where(Recipe.uuid == uuid, Recipe.user_id == user_id)
    )
    recipe = res.scalar_one_or_none()
    if not recipe:
        return False
    await db.delete(recipe)
    await db.commit()
    return True
