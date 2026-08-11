from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

actual_workout_exercise_router = APIRouter(tags=["actual_workout_exercises"])

@actual_workout_exercise_router.get(
    "/actual-workout-exercises",
    response_model=list[schemas.ActualWorkoutExerciseOut]
)
async def get_all_actual_workout_exercises(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_actual_workout_exercises(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_exercise_router.get(
    "/actual-workout-exercises/{uuid}",
    response_model=schemas.ActualWorkoutExerciseOut
)
async def get_actual_workout_exercise_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_actual_workout_exercise_by_uuid(db, uuid, user_id)
    if not result:
        raise HTTPException(status_code=404, detail="Exercise not found or access denied")
    return jsonable_encoder(result, by_alias=True)

@actual_workout_exercise_router.put(
    "/actual-workout-exercises/bulk",
    response_model=list[schemas.ActualWorkoutExerciseOut]
)
async def upsert_actual_workout_exercises_bulk(
    exercises: list[schemas.ActualWorkoutExerciseCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_many_actual_workout_exercises(db, exercises, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_exercise_router.put(
    "/actual-workout-exercises/{uuid}",
    response_model=schemas.ActualWorkoutExerciseOut
)
async def upsert_actual_workout_exercise(
    uuid: str,
    data: schemas.ActualWorkoutExerciseCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_actual_workout_exercise(db, uuid, data, user_id)
    return jsonable_encoder(result, by_alias=True)

@actual_workout_exercise_router.delete("/actual-workout-exercises/{uuid}")
async def delete_actual_workout_exercise(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_actual_workout_exercise(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="ActualWorkoutExercise not found or access denied")
    return {"ok": True}
