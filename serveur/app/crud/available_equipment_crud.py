from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete
from app import models, schemas
from fastapi import HTTPException
from app.crud._concurrency import is_payload_stale


# Récupérer tous les équipements disponibles d'un utilisateur
async def get_all_available_equipments(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.AvailableEquipment).where(models.AvailableEquipment.user_id == user_id)
    )
    return result.scalars().all()


# Récupérer un équipement par UUID
async def get_available_equipment_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(models.AvailableEquipment).where(
            models.AvailableEquipment.uuid == uuid,
            models.AvailableEquipment.user_id == user_id,
        )
    )
    return result.scalars().first()


# Upsert un équipement
async def upsert_available_equipment(
    db: AsyncSession,
    uuid: str,
    equipment_data: schemas.AvailableEquipmentCreate,
    user_id: int,
):
    result = await db.execute(
        select(models.AvailableEquipment).where(models.AvailableEquipment.uuid == uuid)
    )
    existing = result.scalars().first()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cet équipement")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(equipment_data.updated_at, existing.updated_at):
            return existing
        for key, value in equipment_data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création
    new_equipment = models.AvailableEquipment(**equipment_data.model_dump(), user_id=user_id)
    db.add(new_equipment)
    await db.commit()
    await db.refresh(new_equipment)
    return new_equipment


# Bulk upsert équipements (user-scoped)
async def bulk_upsert_available_equipments(
    db: AsyncSession,
    equipment_list: list[schemas.AvailableEquipmentCreate],
    user_id: int,
):
    result_list = []

    for equipment in equipment_list:
        data = equipment.model_dump()

        existing = None
        if data.get("uuid"):
            existing_result = await db.execute(
                select(models.AvailableEquipment).where(models.AvailableEquipment.uuid == data["uuid"])
            )
            existing = existing_result.scalars().first()
            if existing and existing.user_id != user_id:
                raise HTTPException(status_code=403, detail="Accès interdit à cet équipement")

        if existing:
            for key, value in data.items():
                if key not in ("id", "uuid", "user_id"):
                    setattr(existing, key, value)
            result_list.append(existing)
        else:
            data["user_id"] = user_id
            new_eq = models.AvailableEquipment(**data)
            db.add(new_eq)
            result_list.append(new_eq)

    try:
        await db.commit()
    except Exception as e:
        await db.rollback()
        raise e

    return result_list


async def delete_available_equipment(db: AsyncSession, uuid: str, user_id: int):
    existing = await get_available_equipment_by_uuid(db, uuid, user_id)
    if not existing:
        return False
    await db.delete(existing)
    await db.commit()
    return True


# Supprimer tous les équipements disponibles d'un utilisateur
async def clear_all_available_equipments(db: AsyncSession, user_id: int):
    await db.execute(
        delete(models.AvailableEquipment).where(models.AvailableEquipment.user_id == user_id)
    )
    await db.commit()
