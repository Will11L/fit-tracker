from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException
from uuid import uuid4
from app.models import ExerciseEquipment, Exercise
from app.schemas import ExerciseEquipmentCreate
from app.crud._concurrency import is_payload_stale


# Vérifie que l'exercice appartient bien à l'utilisateur
async def assert_user_owns_exercise(db: AsyncSession, exercise_uuid: str, user_id: int):
    result = await db.execute(
        select(Exercise).where(Exercise.uuid == exercise_uuid)
    )
    exercise = result.scalars().first()

    if not exercise:
        raise HTTPException(status_code=404, detail="Exercice non trouvé")
    if exercise.user_id != user_id:
        raise HTTPException(status_code=403, detail="Accès interdit à cet exercice")
    return exercise


# Récupère tous les liens Exercise-Equipment pour un utilisateur
async def get_all_exercise_equipment_links(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(ExerciseEquipment)
        .join(Exercise, ExerciseEquipment.exercise_uuid == Exercise.uuid)
        .where(Exercise.user_id == user_id)
    )
    return result.scalars().all()


# Récupère un lien par UUID
async def get_exercise_equipment_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(ExerciseEquipment).where(ExerciseEquipment.uuid == uuid)
    )
    link = result.scalars().first()
    if not link:
        return None
    await assert_user_owns_exercise(db, link.exercise_uuid, user_id)
    return link


# Upsert un lien Exercise-Equipment
async def upsert_exercise_equipment(
    db: AsyncSession,
    uuid: str,
    link: ExerciseEquipmentCreate,
    user_id: int
):
    # Vérifier que l'user a bien le droit d'accéder à l'exercice
    await assert_user_owns_exercise(db, link.exercise_uuid, user_id)

    data = link.model_dump()
    data.pop("uuid", None)
    data["uuid"] = uuid

    # 1️⃣ Vérifier si le lien existe déjà par UUID
    existing = await get_exercise_equipment_by_uuid(db, uuid, user_id)
    if existing:
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(link.updated_at, existing.updated_at):
            return existing
        for key, value in data.items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # 2️⃣ Vérifier si un lien avec la même combinaison existe déjà
    existing_result = await db.execute(
        select(ExerciseEquipment).where(
            ExerciseEquipment.exercise_uuid == link.exercise_uuid,
            ExerciseEquipment.equipment_uuid == link.equipment_uuid
        )
    )
    existing_combo = existing_result.scalars().first()

    if existing_combo:
        for key, value in data.items():
            if key not in {"uuid", "exercise_uuid", "equipment_uuid"}:
                setattr(existing_combo, key, value)
        # NOTE : on ne mute PAS existing_combo.uuid (casserait les references sync
        # cross-device). Si le client envoie un uuid different pour la meme paire,
        # on garde l'uuid existant -> le client recevra l'uuid serveur en sync.
        await db.commit()
        await db.refresh(existing_combo)
        return existing_combo

    # 3️⃣ Sinon, créer un nouveau lien
    new_link = ExerciseEquipment(**data)
    db.add(new_link)
    await db.commit()
    await db.refresh(new_link)
    return new_link


# Upsert en masse
async def bulk_upsert_exercise_equipment(db: AsyncSession, links: list[ExerciseEquipmentCreate], user_id: int):
    result_list = []
    for link in links:
        new_uuid = str(link.uuid) if hasattr(link, "uuid") and link.uuid else str(uuid4())
        created_or_updated = await upsert_exercise_equipment(db, uuid=new_uuid, link=link, user_id=user_id)
        result_list.append(created_or_updated)
    return result_list


# Supprimer un lien Exercise-Equipment
async def delete_exercise_equipment(db: AsyncSession, uuid: str, user_id: int):
    link = await get_exercise_equipment_by_uuid(db, uuid, user_id)
    if not link:
        return False
    await db.delete(link)
    await db.commit()
    return True
