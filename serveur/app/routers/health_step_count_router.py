from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

health_step_count_router = APIRouter(tags=["health_step_counts"])

@health_step_count_router.get(
    "/health-step-counts",
    response_model=list[schemas.HealthStepCountOut]
)
async def get_all_health_step_counts(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_health_step_counts(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_step_count_router.get(
    "/health-step-counts/{uuid}",
    response_model=schemas.HealthStepCountOut
)
async def get_health_step_count_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    row = await crud.get_health_step_count_by_uuid(db, uuid)
    if not row or row.user_id != user_id:
        raise HTTPException(status_code=404, detail="Compteur de pas non trouvé")
    return jsonable_encoder(row, by_alias=True)

@health_step_count_router.put(
    "/health-step-counts/bulk",
    response_model=list[schemas.HealthStepCountOut]
)
async def bulk_upsert_health_step_counts(
    items: list[schemas.HealthStepCountCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_health_step_counts(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_step_count_router.put(
    "/health-step-counts/{uuid}",
    response_model=schemas.HealthStepCountOut
)
async def upsert_health_step_count(
    uuid: str,
    item: schemas.HealthStepCountCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_health_step_count(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_step_count_router.delete("/health-step-counts/{uuid}")
async def delete_health_step_count(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_health_step_count(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Compteur de pas non trouvé")
    return jsonable_encoder({"detail": "Compteur de pas supprimé"}, by_alias=True)
