# app/routers/muscle_router.py
from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_session
from app.schemas import MuscleCreate, MuscleOut
from app.dependencies import get_current_user_id
from app.crud import muscle_crud as crud

muscle_router = APIRouter(tags=["muscles"])


@muscle_router.get("/muscles", response_model=list[MuscleOut])
async def list_muscles(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    muscles = await crud.get_user_accessible_muscles(db, user_id)
    return jsonable_encoder(muscles, by_alias=True)


@muscle_router.get("/muscles/{uuid}", response_model=MuscleOut)
async def get_muscle(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    muscle = await crud.get_muscle_by_uuid(db, uuid)
    if not muscle or muscle.user_id != user_id:
        raise HTTPException(status_code=404, detail="Muscle non trouvé")
    return jsonable_encoder(muscle, by_alias=True)


@muscle_router.put("/muscles/bulk", response_model=list[MuscleOut])
async def bulk_upsert_muscles(
    muscles: list[MuscleCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    results = await crud.bulk_upsert_muscles(db, muscles, user_id)
    return jsonable_encoder(results, by_alias=True)


@muscle_router.put("/muscles/{uuid}", response_model=MuscleOut)
async def upsert_muscle_route(
    uuid: str,
    muscle: MuscleCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if muscle.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_muscle(db, uuid, muscle, user_id)
    return jsonable_encoder(result, by_alias=True)


@muscle_router.delete("/muscles/{uuid}")
async def delete_muscle(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    success = await crud.delete_muscle(db, uuid, user_id)
    if not success:
        raise HTTPException(status_code=404, detail="Muscle non trouvé")
    return jsonable_encoder({"detail": "Muscle supprimé"}, by_alias=True)
