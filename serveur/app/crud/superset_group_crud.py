from fastapi import HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app import models, schemas
from app.crud._concurrency import is_payload_stale


async def get_superset_group_by_uuid(db: AsyncSession, uuid: str):
    result = await db.execute(
        select(models.SupersetGroup).where(models.SupersetGroup.uuid == uuid)
    )
    return result.scalars().first()


async def get_all_superset_groups(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.SupersetGroup).where(models.SupersetGroup.user_id == user_id)
    )
    return result.scalars().all()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_superset_group(
    db: AsyncSession, uuid: str, dto: schemas.SupersetGroupCreate, user_id: int
):
    result = await db.execute(
        select(models.SupersetGroup).where(models.SupersetGroup.uuid == uuid)
    )
    existing = result.scalars().first()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce superset group")
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
    new_group = models.SupersetGroup(**data)
    db.add(new_group)
    await db.commit()
    await db.refresh(new_group)
    return new_group


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_superset_groups(
    db: AsyncSession, items: list[schemas.SupersetGroupCreate], user_id: int
):
    results = []
    for group in items:
        obj = await upsert_superset_group(db, group.uuid, group, user_id)
        results.append(obj)
    return results


# Supprimer un SupersetGroup (canonique : retourne bool)
async def delete_superset_group(db: AsyncSession, uuid: str, user_id: int) -> bool:
    result = await db.execute(
        select(models.SupersetGroup).where(
            models.SupersetGroup.uuid == uuid,
            models.SupersetGroup.user_id == user_id,
        )
    )
    group = result.scalars().first()
    if not group:
        return False
    await db.delete(group)
    await db.commit()
    return True
