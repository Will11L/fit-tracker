from fastapi import APIRouter, Depends, HTTPException, Body
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

actual_workout_router = APIRouter(tags=["actual_workouts"])

@actual_workout_router.get(
    "/actual-workouts",
    response_model=list[schemas.ActualWorkoutOut]
)
async def list_actual_workouts(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_user_actual_workouts(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_router.get(
    "/actual-workouts/{uuid}",
    response_model=schemas.ActualWorkoutOut
)
async def get_actual_workout(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    workout = await crud.get_actual_workout_by_uuid(db, uuid)
    if not workout or workout.user_id != user_id:
        raise HTTPException(status_code=404, detail="Séance réalisée non trouvée")
    return jsonable_encoder(workout, by_alias=True)

@actual_workout_router.put(
    "/actual-workouts/bulk",
    response_model=list[schemas.ActualWorkoutOut]
)
async def upsert_actual_workouts_bulk(
    workouts: list[schemas.ActualWorkoutCreate] = Body(default=[]),
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_many_actual_workouts(db, workouts, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_router.put(
    "/actual-workouts/{uuid}",
    response_model=schemas.ActualWorkoutOut
)
async def upsert_actual_workout(
    uuid: str,
    workout: schemas.ActualWorkoutCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_actual_workout(db, uuid, workout, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_router.delete(
    "/actual-workouts/{uuid}",
    response_model=dict
)
async def delete_actual_workout(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    success = await crud.delete_actual_workout(db, uuid, user_id)
    if not success:
        raise HTTPException(status_code=404, detail="Séance non trouvée")
    return jsonable_encoder({"message": "Séance supprimée"}, by_alias=True)
