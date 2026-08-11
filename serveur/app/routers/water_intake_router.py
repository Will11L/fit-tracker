from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

water_intake_router = APIRouter(tags=["water_intakes"])

@water_intake_router.get(
    "/water-intakes",
    response_model=list[schemas.WaterIntakeOut]
)
async def get_all_water_intakes(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_water_intakes(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@water_intake_router.get(
    "/water-intakes/{uuid}",
    response_model=schemas.WaterIntakeOut
)
async def get_water_intake_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    row = await crud.get_water_intake_by_uuid(db, uuid)
    if not row or row.user_id != user_id:
        raise HTTPException(status_code=404, detail="Prise d'eau non trouvée")
    return jsonable_encoder(row, by_alias=True)

@water_intake_router.put(
    "/water-intakes/bulk",
    response_model=list[schemas.WaterIntakeOut]
)
async def bulk_upsert_water_intakes(
    items: list[schemas.WaterIntakeCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_water_intakes(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@water_intake_router.put(
    "/water-intakes/{uuid}",
    response_model=schemas.WaterIntakeOut
)
async def upsert_water_intake(
    uuid: str,
    item: schemas.WaterIntakeCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_water_intake(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@water_intake_router.delete("/water-intakes/{uuid}")
async def delete_water_intake(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_water_intake(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Prise d'eau non trouvée")
    return jsonable_encoder({"detail": "Prise d'eau supprimée"}, by_alias=True)
