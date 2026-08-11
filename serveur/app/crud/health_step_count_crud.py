# app/crud/health_step_count_crud.py
# Santé V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.health_step_count import HealthStepCount
from app.schemas import HealthStepCountCreate
from app.crud._concurrency import is_payload_stale


async def get_all_health_step_counts(db: AsyncSession, user_id: int) -> Sequence[HealthStepCount]:
    res = await db.execute(select(HealthStepCount).where(HealthStepCount.user_id == user_id))
    return res.scalars().all()


async def get_health_step_count_by_uuid(db: AsyncSession, uuid: str) -> HealthStepCount | None:
    res = await db.execute(select(HealthStepCount).where(HealthStepCount.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_health_step_count(
    db: AsyncSession, uuid: str, dto: HealthStepCountCreate, user_id: int
) -> HealthStepCount:
    res = await db.execute(select(HealthStepCount).where(HealthStepCount.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce compteur de pas")
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
    row = HealthStepCount(**data)
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return row


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_health_step_counts(
    db: AsyncSession,
    items: list[HealthStepCountCreate],
    user_id: int,
) -> list[HealthStepCount]:
    out: list[HealthStepCount] = []
    for dto in items:
        out.append(await upsert_health_step_count(db, dto.uuid, dto, user_id))
    return out


async def delete_health_step_count(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(HealthStepCount).where(HealthStepCount.uuid == uuid, HealthStepCount.user_id == user_id)
    )
    row = res.scalar_one_or_none()
    if not row:
        return False
    await db.delete(row)
    await db.commit()
    return True
