from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

food_portion_router = APIRouter(tags=["food_portions"])

@food_portion_router.get(
    "/food-portions",
    response_model=list[schemas.FoodPortionOut]
)
async def get_all_food_portions(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_food_portions(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@food_portion_router.get(
    "/food-portions/{uuid}",
    response_model=schemas.FoodPortionOut
)
async def get_food_portion_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    portion = await crud.get_food_portion_by_uuid(db, uuid)
    if not portion:
        raise HTTPException(status_code=404, detail="Portion non trouvée")
    owned = await crud.is_food_owned_by_user_uuid(db, portion.food_uuid, user_id)
    if not owned:
        raise HTTPException(status_code=404, detail="Portion non trouvée")
    return jsonable_encoder(portion, by_alias=True)

@food_portion_router.put(
    "/food-portions/bulk",
    response_model=list[schemas.FoodPortionOut]
)
async def bulk_upsert_food_portions(
    items: list[schemas.FoodPortionCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_food_portions(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@food_portion_router.put(
    "/food-portions/{uuid}",
    response_model=schemas.FoodPortionOut
)
async def upsert_food_portion(
    uuid: str,
    item: schemas.FoodPortionCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_food_portion(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@food_portion_router.delete("/food-portions/{uuid}")
async def delete_food_portion(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_food_portion(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Portion non trouvée")
    return jsonable_encoder({"detail": "Portion supprimée"}, by_alias=True)
