# app/crud/task_crud.py
#
# Phase 0 (2026-05-12) : remplace routine_task_crud. Modele unifie Task
# (toutes recurrences NONE/DAILY/WEEKLY/MONTHLY/YEARLY).

from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.task import Task
from app.schemas import TaskCreate
from app.crud._concurrency import is_payload_stale


async def get_user_accessible_tasks(db: AsyncSession, user_id: int) -> Sequence[Task]:
    stmt = (
        select(Task)
        .where(Task.user_id == user_id)
        .order_by(Task.recurrence_kind.asc(), Task.order.asc(), Task.title.asc())
    )
    res = await db.execute(stmt)
    return res.scalars().all()


async def get_tasks_by_period_uuid(db: AsyncSession, user_id: int, period_uuid: str) -> Sequence[Task]:
    """Helper pour RoutineTasksScreen (Phase 0b) : taches DAILY groupees par periode."""
    stmt = (
        select(Task)
        .where(
            Task.user_id == user_id,
            Task.recurrence_kind == "DAILY",
            Task.period_uuid == period_uuid,
        )
        .order_by(Task.order.asc(), Task.title.asc())
    )
    res = await db.execute(stmt)
    return res.scalars().all()


async def get_task_by_uuid(db: AsyncSession, uuid: str) -> Task | None:
    res = await db.execute(select(Task).where(Task.uuid == uuid))
    return res.scalar_one_or_none()


async def upsert_task(db: AsyncSession, uuid: str, dto: TaskCreate, user_id: int) -> Task:
    res = await db.execute(select(Task).where(Task.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Acces interdit a cette tache")
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
    t = Task(**data)
    db.add(t)
    await db.commit()
    await db.refresh(t)
    return t


async def bulk_upsert_tasks(
    db: AsyncSession,
    items: list[TaskCreate],
    user_id: int,
) -> list[Task]:
    out: list[Task] = []
    for dto in items:
        out.append(await upsert_task(db, dto.uuid, dto, user_id))
    return out


async def delete_task(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(Task).where(Task.uuid == uuid, Task.user_id == user_id)
    )
    task = res.scalar_one_or_none()
    if not task:
        return False
    await db.delete(task)
    await db.commit()
    return True
