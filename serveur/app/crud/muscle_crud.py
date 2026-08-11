# app/crud/muscle_crud.py
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select, or_
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.muscle import Muscle
from app.schemas import MuscleCreate
from app.crud._concurrency import is_payload_stale


async def get_user_accessible_muscles(db: AsyncSession, user_id: int) -> Sequence[Muscle]:
    stmt = select(Muscle).where(
        or_(Muscle.user_id.is_(None), Muscle.user_id == user_id)
    ).order_by(Muscle.name.asc())
    res = await db.execute(stmt)
    return res.scalars().all()


async def get_muscle_by_uuid(db: AsyncSession, uuid: str) -> Muscle | None:
    res = await db.execute(select(Muscle).where(Muscle.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert un muscle (signature canonique : db, uuid, dto, user_id)
async def upsert_muscle(db: AsyncSession, uuid: str, dto: MuscleCreate, user_id: int) -> Muscle:
    res = await db.execute(select(Muscle).where(Muscle.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce muscle")
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
    data["uuid"] = uuid  # le path uuid prevaut
    m = Muscle(**data)
    db.add(m)
    await db.commit()
    await db.refresh(m)
    return m


# Upsert en masse (signature canonique : db, items, user_id)
async def bulk_upsert_muscles(db: AsyncSession, items: list[MuscleCreate], user_id: int) -> list[Muscle]:
    out: list[Muscle] = []
    for dto in items:
        out.append(await upsert_muscle(db, dto.uuid, dto, user_id))
    return out


# Supprimer un muscle (ownership check via WHERE combine)
async def delete_muscle(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(Muscle).where(Muscle.uuid == uuid, Muscle.user_id == user_id)
    )
    muscle = res.scalar_one_or_none()
    if not muscle:
        return False
    await db.delete(muscle)
    await db.commit()
    return True
