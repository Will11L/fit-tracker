from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

actual_workout_set_router = APIRouter(tags=["actual_workout_sets"])

@actual_workout_set_router.get(
    "/actual-workout-sets",
    response_model=list[schemas.ActualWorkoutSetOut]
)
async def get_all_sets(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_actual_workout_sets(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_set_router.get(
    "/actual-workout-sets/{uuid}",
    response_model=schemas.ActualWorkoutSetOut
)
async def get_set_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    set_obj = await crud.get_actual_workout_set_by_uuid(db, uuid, user_id)
    if not set_obj:
        raise HTTPException(status_code=404, detail="Set not found or access denied")
    return jsonable_encoder(set_obj, by_alias=True)

@actual_workout_set_router.put(
    "/actual-workout-sets/bulk",
    response_model=list[schemas.ActualWorkoutSetOut]
)
async def upsert_actual_workout_sets_bulk(
    sets: list[schemas.ActualWorkoutSetCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_many_actual_workout_sets(db, sets, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_set_router.put(
    "/actual-workout-sets/{uuid}",
    response_model=schemas.ActualWorkoutSetOut
)
async def upsert_actual_workout_set(
    uuid: str,
    set_data: schemas.ActualWorkoutSetCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if set_data.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_actual_workout_set_by_uuid(db, uuid, set_data, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_set_router.delete("/actual-workout-sets/{uuid}")
async def delete_set(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_set_from_actual_workout_by_uuid(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Set not found or access denied")
    return {"ok": True}
