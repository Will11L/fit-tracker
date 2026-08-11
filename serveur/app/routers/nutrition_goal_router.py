from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

nutrition_goal_router = APIRouter(tags=["nutrition_goals"])

@nutrition_goal_router.get(
    "/nutrition-goals",
    response_model=list[schemas.NutritionGoalOut]
)
async def get_all_nutrition_goals(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_nutrition_goals(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@nutrition_goal_router.get(
    "/nutrition-goals/{uuid}",
    response_model=schemas.NutritionGoalOut
)
async def get_nutrition_goal_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    goal = await crud.get_nutrition_goal_by_uuid(db, uuid)
    if not goal or goal.user_id != user_id:
        raise HTTPException(status_code=404, detail="Objectif nutrition non trouvé")
    return jsonable_encoder(goal, by_alias=True)

@nutrition_goal_router.put(
    "/nutrition-goals/bulk",
    response_model=list[schemas.NutritionGoalOut]
)
async def bulk_upsert_nutrition_goals(
    items: list[schemas.NutritionGoalCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_nutrition_goals(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@nutrition_goal_router.put(
    "/nutrition-goals/{uuid}",
    response_model=schemas.NutritionGoalOut
)
async def upsert_nutrition_goal(
    uuid: str,
    item: schemas.NutritionGoalCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_nutrition_goal(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@nutrition_goal_router.delete("/nutrition-goals/{uuid}")
async def delete_nutrition_goal(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_nutrition_goal(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Objectif nutrition non trouvé")
    return jsonable_encoder({"detail": "Objectif nutrition supprimé"}, by_alias=True)
