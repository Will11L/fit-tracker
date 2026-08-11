from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

food_router = APIRouter(tags=["foods"])

@food_router.get(
    "/foods",
    response_model=list[schemas.FoodOut]
)
async def get_all_foods(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_foods(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@food_router.get(
    "/foods/{uuid}",
    response_model=schemas.FoodOut
)
async def get_food_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    food = await crud.get_food_by_uuid(db, uuid)
    if not food or food.user_id != user_id:
        raise HTTPException(status_code=404, detail="Aliment non trouvé")
    return jsonable_encoder(food, by_alias=True)

@food_router.put(
    "/foods/bulk",
    response_model=list[schemas.FoodOut]
)
async def bulk_upsert_foods(
    items: list[schemas.FoodCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_foods(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@food_router.put(
    "/foods/{uuid}",
    response_model=schemas.FoodOut
)
async def upsert_food(
    uuid: str,
    item: schemas.FoodCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_food(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@food_router.delete("/foods/{uuid}")
async def delete_food(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_food(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Aliment non trouvé")
    return jsonable_encoder({"detail": "Aliment supprimé"}, by_alias=True)
