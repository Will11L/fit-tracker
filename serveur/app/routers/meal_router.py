from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

meal_router = APIRouter(tags=["meals"])

@meal_router.get(
    "/meals",
    response_model=list[schemas.MealOut]
)
async def get_all_meals(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_meals(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_router.get(
    "/meals/{uuid}",
    response_model=schemas.MealOut
)
async def get_meal_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    meal = await crud.get_meal_by_uuid(db, uuid)
    if not meal or meal.user_id != user_id:
        raise HTTPException(status_code=404, detail="Repas non trouvé")
    return jsonable_encoder(meal, by_alias=True)

@meal_router.put(
    "/meals/bulk",
    response_model=list[schemas.MealOut]
)
async def bulk_upsert_meals(
    items: list[schemas.MealCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_meals(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_router.put(
    "/meals/{uuid}",
    response_model=schemas.MealOut
)
async def upsert_meal(
    uuid: str,
    item: schemas.MealCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_meal(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_router.delete("/meals/{uuid}")
async def delete_meal(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_meal(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Repas non trouvé")
    return jsonable_encoder({"detail": "Repas supprimé"}, by_alias=True)
