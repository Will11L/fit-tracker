from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import crud, schemas
from app.database import get_session
from app.dependencies import get_current_user_id

training_cycle_router = APIRouter(tags=["training_cycles"])

# Type A user-scoped (V5.7, 2026-05-05) : chaque user voit/modifie ses propres cycles.
# user_id injecte depuis l'auth via Depends(get_current_user_id) - jamais lu du payload.

@training_cycle_router.get("/training-cycles", response_model=list[schemas.TrainingCycleOut])
async def list_cycles(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    cycles = await crud.get_all_training_cycles(db, user_id)
    return jsonable_encoder(cycles, by_alias=True)

@training_cycle_router.get("/training-cycles/{uuid}", response_model=schemas.TrainingCycleOut)
async def get_cycle(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    cycle = await crud.get_training_cycle_by_uuid(db, uuid, user_id)
    if not cycle:
        raise HTTPException(status_code=404, detail="Cycle not found")
    return jsonable_encoder(cycle, by_alias=True)

@training_cycle_router.put("/training-cycles/bulk", response_model=list[schemas.TrainingCycleOut])
async def bulk_upsert_cycles(
    cycles: list[schemas.TrainingCycleCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    results = await crud.bulk_upsert_training_cycles(db, cycles, user_id)
    return jsonable_encoder(results, by_alias=True)

@training_cycle_router.put("/training-cycles/{uuid}", response_model=schemas.TrainingCycleOut)
async def upsert_cycle(
    uuid: str,
    cycle_data: schemas.TrainingCycleCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    if cycle_data.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_training_cycle(db, uuid, cycle_data, user_id)
    return jsonable_encoder(result, by_alias=True)

@training_cycle_router.delete("/training-cycles/{uuid}")
async def delete_cycle(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    ok = await crud.delete_training_cycle(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Cycle not found")
    return {"ok": True}

# Decision archi V4.1 (2026-05-05) : la gestion du lien planned_workout
# <-> training_cycle se fait via le router /cycle-workouts (entite junction
# avec son propre uuid, deja securisee V2.1). Les 3 endpoints
# /training-cycles/{cycle_uuid}/workouts/{workout_uuid} (POST/GET/DELETE)
# etaient jamais appeles cote Android (verifie par grep) -> supprimes.
# CRUDs associes (link_workout_to_cycle, get_cycle_workouts,
# delete_cycle_workout) supprimes en meme temps.
