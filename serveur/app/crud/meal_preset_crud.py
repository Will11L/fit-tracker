# app/crud/meal_preset_crud.py
# Nutrition V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.meal_preset import MealPreset
from app.schemas import MealPresetCreate
from app.crud._concurrency import is_payload_stale


async def get_all_meal_presets(db: AsyncSession, user_id: int) -> Sequence[MealPreset]:
    res = await db.execute(select(MealPreset).where(MealPreset.user_id == user_id))
    return res.scalars().all()


async def get_meal_preset_by_uuid(db: AsyncSession, uuid: str) -> MealPreset | None:
    res = await db.execute(select(MealPreset).where(MealPreset.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_meal_preset(db: AsyncSession, uuid: str, dto: MealPresetCreate, user_id: int) -> MealPreset:
    res = await db.execute(select(MealPreset).where(MealPreset.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce preset de repas")
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
    preset = MealPreset(**data)
    db.add(preset)
    await db.commit()
    await db.refresh(preset)
    return preset


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_meal_presets(
    db: AsyncSession,
    items: list[MealPresetCreate],
    user_id: int,
) -> list[MealPreset]:
    out: list[MealPreset] = []
    for dto in items:
        out.append(await upsert_meal_preset(db, dto.uuid, dto, user_id))
    return out


async def delete_meal_preset(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(MealPreset).where(MealPreset.uuid == uuid, MealPreset.user_id == user_id)
    )
    preset = res.scalar_one_or_none()
    if not preset:
        return False
    await db.delete(preset)
    await db.commit()
    return True
