from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import Equipment
from app import schemas, models
from fastapi import HTTPException
from app.crud._concurrency import is_payload_stale


# Récupère tous les équipements
async def get_all_equipments(db: AsyncSession):
    result = await db.execute(select(Equipment))
    return result.scalars().all()


# Récupère un équipement par UUID
async def get_equipment_by_uuid(db: AsyncSession, uuid: str):
    result = await db.execute(select(Equipment).where(Equipment.uuid == uuid))
    return result.scalars().first()


# Upsert (signature canonique : db, uuid, dto). Type C global -> pas de user_id.
async def upsert_equipment(db: AsyncSession, uuid: str, dto: schemas.EquipmentCreate):
    result = await db.execute(select(Equipment).where(Equipment.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(dto.updated_at, existing.updated_at):
            return existing
        # Écrasement total via model_dump (canonique)
        for key, value in dto.model_dump().items():
            if key not in ("uuid",):
                setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    data = dto.model_dump()
    data["uuid"] = uuid
    new_eq = Equipment(**data)
    db.add(new_eq)
    await db.commit()
    await db.refresh(new_eq)
    return new_eq


# Upsert en masse
async def bulk_upsert_equipments(db: AsyncSession, equipment_list: list[schemas.EquipmentCreate]):
    result_list = []

    for equipment in equipment_list:
        data = equipment.model_dump()

        existing = None
        if data.get("uuid"):
            existing_result = await db.execute(
                select(models.Equipment).where(models.Equipment.uuid == data["uuid"])
            )
            existing = existing_result.scalars().first()

        if not existing:
            existing_result = await db.execute(
                select(models.Equipment).where(models.Equipment.name == data["name"])
            )
            existing = existing_result.scalars().first()

        if existing:
            for key, value in data.items():
                if key not in ("id", "uuid"):
                    setattr(existing, key, value)
            result_list.append(existing)
        else:
            new_eq = models.Equipment(**data)
            db.add(new_eq)
            result_list.append(new_eq)

    try:
        await db.commit()
    except Exception as e:
        await db.rollback()
        raise e

    return result_list


# Supprimer un équipement (canonique : retourne bool)
async def delete_equipment(db: AsyncSession, uuid: str) -> bool:
    result = await db.execute(select(Equipment).where(Equipment.uuid == uuid))
    eq = result.scalars().first()
    if not eq:
        return False
    await db.delete(eq)
    await db.commit()
    return True
