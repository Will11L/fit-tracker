from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import crud, schemas
from app.database import get_session
from app.dependencies import get_current_user_id

superset_exercise_router = APIRouter(tags=["superset_exercises"])

@superset_exercise_router.get(
    "/superset-exercises",
    response_model=list[schemas.SupersetExerciseOut]
)
async def get_all_superset_exercises(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    exercises = await crud.get_all_superset_exercises(db, user_id)
    return jsonable_encoder(exercises, by_alias=True)

@superset_exercise_router.get(
    "/superset-exercises/{uuid}",
    response_model=schemas.SupersetExerciseOut
)
async def read_superset_exercise(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    db_exercise = await crud.get_superset_exercise_by_uuid(db, uuid, user_id)
    if not db_exercise or not await crud.is_superset_exercise_owned_by_user(db, db_exercise, user_id):
        raise HTTPException(status_code=403, detail="Superset Exercise not found or unauthorized")
    return jsonable_encoder(db_exercise, by_alias=True)

@superset_exercise_router.put(
    "/superset-exercises/bulk",
    response_model=list[schemas.SupersetExerciseOut]
)
async def bulk_upsert_superset_exercises(
    exercises: list[schemas.SupersetExerciseCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    results = await crud.bulk_upsert_superset_exercises(db, exercises, user_id)
    return jsonable_encoder(results, by_alias=True)

@superset_exercise_router.put(
    "/superset-exercises/{uuid}",
    response_model=schemas.SupersetExerciseOut
)
async def upsert_superset_exercise(
    uuid: str,
    superset_exercise: schemas.SupersetExerciseCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_superset_exercise(db, uuid, superset_exercise, user_id)
    return jsonable_encoder(result, by_alias=True)

@superset_exercise_router.delete("/superset-exercises/{uuid}")
async def delete_superset_exercise(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.delete_superset_exercise(db, uuid, user_id)
    return jsonable_encoder(result, by_alias=True)
