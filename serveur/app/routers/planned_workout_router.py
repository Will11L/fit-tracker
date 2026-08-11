from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

planned_workout_router = APIRouter(tags=["planned_workouts"])

@planned_workout_router.get(
    "/planned-workouts",
    response_model=list[schemas.PlannedWorkoutOut]
)
async def list_planned_workouts(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    workouts = await crud.get_user_planned_workouts(db, user_id)
    return jsonable_encoder(workouts, by_alias=True)

@planned_workout_router.get(
    "/planned-workouts/{uuid}",
    response_model=schemas.PlannedWorkoutOut
)
async def get_planned_workout_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    workout = await crud.get_planned_workout_by_uuid(db, uuid, user_id)
    if not workout:
        raise HTTPException(status_code=404, detail="Séance planifiée non trouvée")
    return jsonable_encoder(workout, by_alias=True)

@planned_workout_router.put(
    "/planned-workouts/bulk",
    response_model=list[schemas.PlannedWorkoutOut]
)
async def bulk_upsert_planned_workouts(
    workouts: list[schemas.PlannedWorkoutCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    results = await crud.bulk_upsert_planned_workouts(db, workouts, user_id)
    return jsonable_encoder(results, by_alias=True)

@planned_workout_router.put(
    "/planned-workouts/{uuid}",
    response_model=schemas.PlannedWorkoutOut
)
async def upsert_planned_workout(
    uuid: str,
    planned_workout: schemas.PlannedWorkoutCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_planned_workout(db, uuid, planned_workout, user_id)
    return jsonable_encoder(result, by_alias=True)

@planned_workout_router.delete("/planned-workouts/{uuid}")
async def delete_planned_workout(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    success = await crud.delete_planned_workout_by_uuid(db, uuid, user_id)
    if not success:
        raise HTTPException(status_code=404, detail="Séance non trouvée ou non supprimable")
    return jsonable_encoder({"detail": "Séance supprimée"}, by_alias=True)
