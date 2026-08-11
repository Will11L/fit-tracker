from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id
from app.schemas import ExerciseMuscleCreate, ExerciseMuscleOut

exercise_muscle_router = APIRouter(tags=["exercise_muscles"])

@exercise_muscle_router.get(
    "/exercise-muscles",
    response_model=list[schemas.ExerciseMuscleOut]
)
async def get_all_links(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_exercise_muscles(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@exercise_muscle_router.put(
    "/exercise-muscles/bulk",
    response_model=list[schemas.ExerciseMuscleOut]
)
async def bulk_upsert(
    links: list[schemas.ExerciseMuscleCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_exercise_muscles(db, links, user_id)
    return jsonable_encoder(result, by_alias=True)

@exercise_muscle_router.put(
    "/exercise-muscles/{uuid}",
    response_model=schemas.ExerciseMuscleOut
)
async def upsert_exercise_muscle(
    uuid: str,
    exercise_muscle: schemas.ExerciseMuscleCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_exercise_muscle(db, uuid, exercise_muscle, user_id)
    return jsonable_encoder(result, by_alias=True)


@exercise_muscle_router.delete("/exercise-muscles/{uuid}")
async def delete_exercise_muscle(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_exercise_muscle_by_uuid(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="ExerciseMuscle not found")
    return {"ok": True}


