# app/crud/routine_period_crud.py
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.routine_period import RoutinePeriod
from app.schemas import RoutinePeriodCreate
from app.crud._concurrency import is_payload_stale


async def get_user_accessible_routine_periods(db: AsyncSession, user_id: int) -> Sequence[RoutinePeriod]:
    stmt = (
        select(RoutinePeriod)
        .where(RoutinePeriod.user_id == user_id)
        .order_by(RoutinePeriod.order.asc(), RoutinePeriod.start_time.asc(), RoutinePeriod.name.asc())
    )
    res = await db.execute(stmt)
    return res.scalars().all()


async def get_routine_period_by_uuid(db: AsyncSession, uuid: str) -> RoutinePeriod | None:
    res = await db.execute(select(RoutinePeriod).where(RoutinePeriod.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_routine_period(db: AsyncSession, uuid: str, dto: RoutinePeriodCreate, user_id: int) -> RoutinePeriod:
    res = await db.execute(select(RoutinePeriod).where(RoutinePeriod.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cette période")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(dto.updated_at, existing.updated_at):
            return existing
        # Écrasement total via model_dump (canonique)
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
    p = RoutinePeriod(**data)
    db.add(p)
    await db.commit()
    await db.refresh(p)
    return p


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_routine_periods(
    db: AsyncSession,
    items: list[RoutinePeriodCreate],
    user_id: int,
) -> list[RoutinePeriod]:
    out: list[RoutinePeriod] = []
    for dto in items:
        out.append(await upsert_routine_period(db, dto.uuid, dto, user_id))
    return out


async def delete_routine_period(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(RoutinePeriod).where(RoutinePeriod.uuid == uuid, RoutinePeriod.user_id == user_id)
    )
    period = res.scalar_one_or_none()
    if not period:
        return False
    await db.delete(period)
    await db.commit()
    return True
