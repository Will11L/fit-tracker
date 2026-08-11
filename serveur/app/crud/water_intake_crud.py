# app/crud/water_intake_crud.py
# Hydratation — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.water_intake import WaterIntake
from app.schemas import WaterIntakeCreate
from app.crud._concurrency import is_payload_stale


async def get_all_water_intakes(db: AsyncSession, user_id: int) -> Sequence[WaterIntake]:
    res = await db.execute(select(WaterIntake).where(WaterIntake.user_id == user_id))
    return res.scalars().all()


async def get_water_intake_by_uuid(db: AsyncSession, uuid: str) -> WaterIntake | None:
    res = await db.execute(select(WaterIntake).where(WaterIntake.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_water_intake(
    db: AsyncSession, uuid: str, dto: WaterIntakeCreate, user_id: int
) -> WaterIntake:
    res = await db.execute(select(WaterIntake).where(WaterIntake.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cette prise d'eau")
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
    row = WaterIntake(**data)
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return row


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_water_intakes(
    db: AsyncSession,
    items: list[WaterIntakeCreate],
    user_id: int,
) -> list[WaterIntake]:
    out: list[WaterIntake] = []
    for dto in items:
        out.append(await upsert_water_intake(db, dto.uuid, dto, user_id))
    return out


async def delete_water_intake(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(WaterIntake).where(WaterIntake.uuid == uuid, WaterIntake.user_id == user_id)
    )
    row = res.scalar_one_or_none()
    if not row:
        return False
    await db.delete(row)
    await db.commit()
    return True
