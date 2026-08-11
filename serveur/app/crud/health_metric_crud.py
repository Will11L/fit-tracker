# app/crud/health_metric_crud.py
# Santé V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.health_metric import HealthMetric
from app.schemas import HealthMetricCreate
from app.crud._concurrency import is_payload_stale


async def get_all_health_metrics(db: AsyncSession, user_id: int) -> Sequence[HealthMetric]:
    res = await db.execute(select(HealthMetric).where(HealthMetric.user_id == user_id))
    return res.scalars().all()


async def get_health_metric_by_uuid(db: AsyncSession, uuid: str) -> HealthMetric | None:
    res = await db.execute(select(HealthMetric).where(HealthMetric.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_health_metric(
    db: AsyncSession, uuid: str, dto: HealthMetricCreate, user_id: int
) -> HealthMetric:
    res = await db.execute(select(HealthMetric).where(HealthMetric.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cette métrique santé")
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
    row = HealthMetric(**data)
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return row


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_health_metrics(
    db: AsyncSession,
    items: list[HealthMetricCreate],
    user_id: int,
) -> list[HealthMetric]:
    out: list[HealthMetric] = []
    for dto in items:
        out.append(await upsert_health_metric(db, dto.uuid, dto, user_id))
    return out


async def delete_health_metric(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(HealthMetric).where(HealthMetric.uuid == uuid, HealthMetric.user_id == user_id)
    )
    row = res.scalar_one_or_none()
    if not row:
        return False
    await db.delete(row)
    await db.commit()
    return True
