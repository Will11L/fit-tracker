from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException
from app.models import TrainingCycle
from app.schemas import TrainingCycleCreate
from app.crud._concurrency import is_payload_stale


# Récupérer tous les cycles d'un utilisateur
async def get_all_training_cycles(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(TrainingCycle).where(TrainingCycle.user_id == user_id)
    )
    return result.scalars().all()


# Récupérer un cycle par UUID (ownership check)
async def get_training_cycle_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(TrainingCycle).where(
            TrainingCycle.uuid == uuid,
            TrainingCycle.user_id == user_id,
        )
    )
    return result.scalars().first()


# Upsert un cycle (signature canonique : db, uuid, dto, user_id)
async def upsert_training_cycle(db: AsyncSession, uuid: str, cycle: TrainingCycleCreate, user_id: int):
    result = await db.execute(select(TrainingCycle).where(TrainingCycle.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce cycle")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(cycle.updated_at, existing.updated_at):
            return existing
        for key, value in cycle.model_dump().items():
            if key not in ("uuid", "user_id"):
                setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création : injecte user_id depuis l'auth (politique sécurité V2.2).
    data = cycle.model_dump()
    data["user_id"] = user_id
    new_cycle = TrainingCycle(**data)
    db.add(new_cycle)
    await db.commit()
    await db.refresh(new_cycle)
    return new_cycle


# Upsert en masse (filtre par user_id)
async def bulk_upsert_training_cycles(db: AsyncSession, cycles: list[TrainingCycleCreate], user_id: int):
    results = []
    for c in cycles:
        obj = await upsert_training_cycle(db, c.uuid, c, user_id)
        results.append(obj)
    return results


# Supprimer un cycle (ownership check, canonique : retourne bool)
async def delete_training_cycle(db: AsyncSession, uuid: str, user_id: int) -> bool:
    cycle = await get_training_cycle_by_uuid(db, uuid, user_id)
    if not cycle:
        return False
    await db.delete(cycle)
    await db.commit()
    return True


# Decision V4.1 (2026-05-05) : 3 fonctions CRUD lien cycle<->workout
# (link_workout_to_cycle, get_cycle_workouts, delete_cycle_workout)
# supprimees - les 3 endpoints router associes etaient orphelins (cf.
# training_cycle_router.py). La gestion passe par cycle_workout_crud
# (entite junction avec uuid).
