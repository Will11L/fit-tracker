from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

routine_period_router = APIRouter(tags=["routine_periods"])

@routine_period_router.get(
    "/routine-periods",
    response_model=list[schemas.RoutinePeriodOut]
)
async def get_all_routine_periods(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_user_accessible_routine_periods(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@routine_period_router.get(
    "/routine-periods/{uuid}",
    response_model=schemas.RoutinePeriodOut
)
async def get_routine_period_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    period = await crud.get_routine_period_by_uuid(db, uuid)
    if not period or period.user_id != user_id:
        raise HTTPException(status_code=404, detail="RoutinePeriod non trouvé")
    return jsonable_encoder(period, by_alias=True)

@routine_period_router.put(
    "/routine-periods/bulk",
    response_model=list[schemas.RoutinePeriodOut]
)
async def bulk_upsert_routine_periods(
    items: list[schemas.RoutinePeriodCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_routine_periods(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@routine_period_router.put(
    "/routine-periods/{uuid}",
    response_model=schemas.RoutinePeriodOut
)
async def upsert_routine_period(
    uuid: str,
    item: schemas.RoutinePeriodCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_routine_period(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@routine_period_router.delete("/routine-periods/{uuid}")
async def delete_routine_period(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_routine_period(db, uuid, user_id)
    return jsonable_encoder(ok, by_alias=True)
