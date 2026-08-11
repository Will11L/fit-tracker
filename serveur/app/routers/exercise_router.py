from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

exercise_router = APIRouter(tags=["exercises"])


@exercise_router.get(
    "/exercises",
    response_model=list[schemas.ExerciseOut]
)
async def get_all_exercises(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_exercises(db, user_id)
    return jsonable_encoder(result, by_alias=True)


@exercise_router.get(
    "/exercises/{uuid}",
    response_model=schemas.ExerciseOut
)
async def get_exercise_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    exercise = await crud.get_exercise_by_uuid(db, uuid, user_id)
    if not exercise:
        raise HTTPException(status_code=404, detail="Exercice non trouvé")
    return jsonable_encoder(exercise, by_alias=True)


@exercise_router.put(
    "/exercises/bulk",
    response_model=list[schemas.ExerciseOut]
)
async def bulk_upsert_exercises(
    exercises: list[schemas.ExerciseCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_exercises(db, exercises, user_id)
    return jsonable_encoder(result, by_alias=True)


@exercise_router.put(
    "/exercises/{uuid}",
    response_model=schemas.ExerciseOut
)
async def upsert_exercise(
    uuid: str,
    exercise: schemas.ExerciseCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_exercise(db, uuid, exercise, user_id)
    return jsonable_encoder(result, by_alias=True)


@exercise_router.delete("/exercises/{uuid}")
async def delete_exercise(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.delete_exercise(db, uuid, user_id)
    return jsonable_encoder(result, by_alias=True)
