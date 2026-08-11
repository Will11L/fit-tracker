from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

health_metric_router = APIRouter(tags=["health_metrics"])

@health_metric_router.get(
    "/health-metrics",
    response_model=list[schemas.HealthMetricOut]
)
async def get_all_health_metrics(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_health_metrics(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_metric_router.get(
    "/health-metrics/{uuid}",
    response_model=schemas.HealthMetricOut
)
async def get_health_metric_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    row = await crud.get_health_metric_by_uuid(db, uuid)
    if not row or row.user_id != user_id:
        raise HTTPException(status_code=404, detail="Métrique santé non trouvée")
    return jsonable_encoder(row, by_alias=True)

@health_metric_router.put(
    "/health-metrics/bulk",
    response_model=list[schemas.HealthMetricOut]
)
async def bulk_upsert_health_metrics(
    items: list[schemas.HealthMetricCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_health_metrics(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_metric_router.put(
    "/health-metrics/{uuid}",
    response_model=schemas.HealthMetricOut
)
async def upsert_health_metric(
    uuid: str,
    item: schemas.HealthMetricCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_health_metric(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_metric_router.delete("/health-metrics/{uuid}")
async def delete_health_metric(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_health_metric(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Métrique santé non trouvée")
    return jsonable_encoder({"detail": "Métrique santé supprimée"}, by_alias=True)
