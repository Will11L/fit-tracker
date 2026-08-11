from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import crud, schemas
from app.database import get_session
from app.dependencies import get_current_user_id

cycle_workout_router = APIRouter(tags=["cycle_workouts"])

@cycle_workout_router.get(
    "/cycle-workouts",
    response_model=list[schemas.CycleWorkoutOut]
)
async def get_all_cycle_workouts(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_cycle_workouts(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@cycle_workout_router.put(
    "/cycle-workouts/bulk",
    response_model=list[schemas.CycleWorkoutOut]
)
async def bulk_upsert_cycle_workouts(
    cws: list[schemas.CycleWorkoutCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_cycle_workouts(db, cws, user_id)
    return jsonable_encoder(result, by_alias=True)

@cycle_workout_router.put(
    "/cycle-workouts/{uuid}",
    response_model=schemas.CycleWorkoutOut
)
async def upsert_cycle_workout(
    uuid: str,
    data: schemas.CycleWorkoutCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_cycle_workout(db, uuid, data, user_id)
    return jsonable_encoder(result, by_alias=True)


@cycle_workout_router.delete("/cycle-workouts/{uuid}")
async def delete_cycle_workout(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_cycle_workout(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="CycleWorkout non trouvé")
    return {"ok": True}
