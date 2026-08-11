# app/crud/task_check_crud.py
#
# Phase 0 (2026-05-12) : remplace routine_task_check_crud. Rename `date` -> `occurrence_date`.

from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.task_check import TaskCheck
from app.schemas import TaskCheckCreate
from app.crud._concurrency import is_payload_stale


async def get_user_accessible_task_checks(db: AsyncSession, user_id: int) -> Sequence[TaskCheck]:
    stmt = (
        select(TaskCheck)
        .where(TaskCheck.user_id == user_id)
        .order_by(TaskCheck.occurrence_date.desc())
    )
    res = await db.execute(stmt)
    return res.scalars().all()


async def get_task_check_by_uuid(db: AsyncSession, uuid: str) -> TaskCheck | None:
    res = await db.execute(select(TaskCheck).where(TaskCheck.uuid == uuid))
    return res.scalar_one_or_none()


async def upsert_task_check(db: AsyncSession, uuid: str, dto: TaskCheckCreate, user_id: int) -> TaskCheck:
    res = await db.execute(select(TaskCheck).where(TaskCheck.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Acces interdit a ce check")
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
    c = TaskCheck(**data)
    db.add(c)
    try:
        await db.commit()
    except IntegrityError as e:
        # FK violation typique : task_uuid pas encore sync cote serveur
        # (Android push TaskCheck avant que Task parent soit sur Pi). Retourner
        # 404 explicite plutot qu'un 500 -- l'Android sync layer comprend 404
        # et re-pushera dans l'ordre correct au prochain pushAll.
        await db.rollback()
        raise HTTPException(
            status_code=404,
            detail=f"Task parent introuvable (task_uuid={dto.task_uuid}). Push Task avant TaskCheck.",
        ) from e
    await db.refresh(c)
    return c


async def bulk_upsert_task_checks(
    db: AsyncSession,
    items: list[TaskCheckCreate],
    user_id: int,
) -> list[TaskCheck]:
    out: list[TaskCheck] = []
    for dto in items:
        out.append(await upsert_task_check(db, dto.uuid, dto, user_id))
    return out


async def delete_task_check(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(TaskCheck).where(TaskCheck.uuid == uuid, TaskCheck.user_id == user_id)
    )
    check = res.scalar_one_or_none()
    if not check:
        return False
    await db.delete(check)
    await db.commit()
    return True


async def get_check_for_task_on_date(
    db: AsyncSession,
    user_id: int,
    task_uuid: str,
    occurrence_date: str,
) -> TaskCheck | None:
    res = await db.execute(
        select(TaskCheck).where(
            TaskCheck.user_id == user_id,
            TaskCheck.task_uuid == task_uuid,
            TaskCheck.occurrence_date == occurrence_date,
        )
    )
    return res.scalar_one_or_none()


async def set_check_for_task_on_date(
    db: AsyncSession,
    user_id: int,
    task_uuid: str,
    occurrence_date: str,
    is_checked: bool,
    checked_at=None,
) -> TaskCheck:
    existing = await get_check_for_task_on_date(db, user_id, task_uuid, occurrence_date)

    if existing:
        existing.is_checked = is_checked
        existing.checked_at = checked_at
        await db.flush()
        await db.commit()
        await db.refresh(existing)
        return existing

    c = TaskCheck(
        user_id=user_id,
        task_uuid=task_uuid,
        occurrence_date=occurrence_date,
        is_checked=is_checked,
        checked_at=checked_at,
    )
    db.add(c)
    await db.flush()
    await db.commit()
    await db.refresh(c)
    return c
