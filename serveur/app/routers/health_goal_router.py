from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

health_goal_router = APIRouter(tags=["health_goals"])

@health_goal_router.get(
    "/health-goals",
    response_model=list[schemas.HealthGoalOut]
)
async def get_all_health_goals(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_health_goals(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_goal_router.get(
    "/health-goals/{uuid}",
    response_model=schemas.HealthGoalOut
)
async def get_health_goal_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    goal = await crud.get_health_goal_by_uuid(db, uuid)
    if not goal or goal.user_id != user_id:
        raise HTTPException(status_code=404, detail="Objectif santé non trouvé")
    return jsonable_encoder(goal, by_alias=True)

@health_goal_router.put(
    "/health-goals/bulk",
    response_model=list[schemas.HealthGoalOut]
)
async def bulk_upsert_health_goals(
    items: list[schemas.HealthGoalCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_health_goals(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_goal_router.put(
    "/health-goals/{uuid}",
    response_model=schemas.HealthGoalOut
)
async def upsert_health_goal(
    uuid: str,
    item: schemas.HealthGoalCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_health_goal(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@health_goal_router.delete("/health-goals/{uuid}")
async def delete_health_goal(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_health_goal(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Objectif santé non trouvé")
    return jsonable_encoder({"detail": "Objectif santé supprimé"}, by_alias=True)
