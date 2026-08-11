from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

available_equipment_router = APIRouter(tags=["available_equipments"])

# Type A user-scoped (F8-Q2 2026-05-06) : chaque user a ses propres
# AvailableEquipment ("parmi les equipements existants, j'ai celui-ci").

@available_equipment_router.get(
    "/available-equipments",
    response_model=list[schemas.AvailableEquipmentOut]
)
async def get_all_available_equipments(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.get_all_available_equipments(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@available_equipment_router.put(
    "/available-equipments/bulk",
    response_model=list[schemas.AvailableEquipmentOut]
)
async def bulk_upsert_available_equipments(
    equipments: list[schemas.AvailableEquipmentCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.bulk_upsert_available_equipments(db, equipments, user_id)
    return jsonable_encoder(result, by_alias=True)

@available_equipment_router.put(
    "/available-equipments/{uuid}",
    response_model=schemas.AvailableEquipmentOut
)
async def upsert_available_equipment(
    uuid: str,
    equipment: schemas.AvailableEquipmentCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.upsert_available_equipment(db, uuid, equipment, user_id)
    return jsonable_encoder(result, by_alias=True)

@available_equipment_router.delete("/available-equipments/{uuid}")
async def delete_available_equipment(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    ok = await crud.delete_available_equipment(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Available equipment not found")
    return {"ok": True}

@available_equipment_router.delete("/available-equipments")
async def delete_all_available_equipments(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    await crud.clear_all_available_equipments(db, user_id)
    return {"detail": "All available equipment entries deleted."}
