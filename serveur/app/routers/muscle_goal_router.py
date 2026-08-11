from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

muscle_goal_router = APIRouter(tags=["muscle_goals"])

@muscle_goal_router.get(
    "/muscle-goals",
    response_model=list[schemas.MuscleGoalOut]
)
async def get_all_muscle_goals(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_muscle_goals(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@muscle_goal_router.get(
    "/muscle-goals/{uuid}",
    response_model=schemas.MuscleGoalOut
)
async def get_muscle_goal_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    goal = await crud.get_muscle_goal_by_uuid(db, uuid, user_id)
    if not goal:
        raise HTTPException(status_code=404, detail="Objectif non trouvé")
    return jsonable_encoder(goal, by_alias=True)

@muscle_goal_router.put(
    "/muscle-goals/bulk",
    response_model=list[schemas.MuscleGoalOut]
)
async def bulk_upsert_muscle_goals(
    goals: list[schemas.MuscleGoalCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_muscle_goals(db, goals, user_id)
    return jsonable_encoder(result, by_alias=True)

@muscle_goal_router.put(
    "/muscle-goals/{uuid}",
    response_model=schemas.MuscleGoalOut
)
async def upsert_muscle_goal(
    uuid: str,
    muscle_goal: schemas.MuscleGoalCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_muscle_goal(db, uuid, muscle_goal, user_id)
    return jsonable_encoder(result, by_alias=True)

@muscle_goal_router.delete("/muscle-goals/{uuid}")
async def delete_muscle_goal(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    deleted = await crud.delete_muscle_goal(db, uuid, user_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Objectif non trouvé")
    return jsonable_encoder({"detail": "Supprimé"}, by_alias=True)
