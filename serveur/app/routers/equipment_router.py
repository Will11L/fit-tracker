from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id, require_admin

equipment_router = APIRouter(tags=["equipments"])

@equipment_router.get(
    "/equipments",
    response_model=list[schemas.EquipmentOut]
)
async def get_all_equipments(
    db: AsyncSession = Depends(get_session),
    _user_id: int = Depends(get_current_user_id),
):
    result = await crud.get_all_equipments(db)
    return jsonable_encoder(result, by_alias=True)

@equipment_router.get(
    "/equipments/{uuid}",
    response_model=schemas.EquipmentOut
)
async def get_equipment_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    _user_id: int = Depends(get_current_user_id),
):
    equipment = await crud.get_equipment_by_uuid(db, uuid)
    if not equipment:
        raise HTTPException(status_code=404, detail="Équipement non trouvé")
    return jsonable_encoder(equipment, by_alias=True)

@equipment_router.put(
    "/equipments/bulk",
    response_model=list[schemas.EquipmentOut]
)
async def bulk_upsert_equipments(
    equipments: list[schemas.EquipmentCreate],
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    result = await crud.bulk_upsert_equipments(db, equipments)
    return jsonable_encoder(result, by_alias=True)

@equipment_router.put(
    "/equipments/{uuid}",
    response_model=schemas.EquipmentOut
)
async def upsert_equipment(
    uuid: str,
    equipment: schemas.EquipmentCreate,
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    if equipment.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_equipment(db, uuid, equipment)
    return jsonable_encoder(result, by_alias=True)

@equipment_router.delete("/equipments/{uuid}")
async def delete_equipment(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    success = await crud.delete_equipment(db, uuid)
    if not success:
        raise HTTPException(status_code=404, detail="Équipement non trouvé")
    return jsonable_encoder({"detail": "Équipement supprimé"}, by_alias=True)
