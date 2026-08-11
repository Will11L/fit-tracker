# app/crud/health_goal_crud.py
# Santé V1 — Type A user-scoped (squelette canonique docs/SERVEUR.md §2B-1).
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.health_goal import HealthGoal
from app.schemas import HealthGoalCreate
from app.crud._concurrency import is_payload_stale


async def get_all_health_goals(db: AsyncSession, user_id: int) -> Sequence[HealthGoal]:
    res = await db.execute(select(HealthGoal).where(HealthGoal.user_id == user_id))
    return res.scalars().all()


async def get_health_goal_by_uuid(db: AsyncSession, uuid: str) -> HealthGoal | None:
    res = await db.execute(select(HealthGoal).where(HealthGoal.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_health_goal(
    db: AsyncSession, uuid: str, dto: HealthGoalCreate, user_id: int
) -> HealthGoal:
    res = await db.execute(select(HealthGoal).where(HealthGoal.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cet objectif santé")
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
    goal = HealthGoal(**data)
    db.add(goal)
    await db.commit()
    await db.refresh(goal)
    return goal


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_health_goals(
    db: AsyncSession,
    items: list[HealthGoalCreate],
    user_id: int,
) -> list[HealthGoal]:
    out: list[HealthGoal] = []
    for dto in items:
        out.append(await upsert_health_goal(db, dto.uuid, dto, user_id))
    return out


async def delete_health_goal(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(HealthGoal).where(HealthGoal.uuid == uuid, HealthGoal.user_id == user_id)
    )
    goal = res.scalar_one_or_none()
    if not goal:
        return False
    await db.delete(goal)
    await db.commit()
    return True
