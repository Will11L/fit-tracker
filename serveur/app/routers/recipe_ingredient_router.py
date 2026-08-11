from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

recipe_ingredient_router = APIRouter(tags=["recipe_ingredients"])

@recipe_ingredient_router.get(
    "/recipe-ingredients",
    response_model=list[schemas.RecipeIngredientOut]
)
async def get_all_recipe_ingredients(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_recipe_ingredients(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@recipe_ingredient_router.get(
    "/recipe-ingredients/{uuid}",
    response_model=schemas.RecipeIngredientOut
)
async def get_recipe_ingredient_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ingredient = await crud.get_recipe_ingredient_by_uuid(db, uuid)
    if not ingredient:
        raise HTTPException(status_code=404, detail="Ingrédient non trouvé")
    owned = await crud.is_recipe_owned_by_user_uuid(db, ingredient.recipe_uuid, user_id)
    if not owned:
        raise HTTPException(status_code=404, detail="Ingrédient non trouvé")
    return jsonable_encoder(ingredient, by_alias=True)

@recipe_ingredient_router.put(
    "/recipe-ingredients/bulk",
    response_model=list[schemas.RecipeIngredientOut]
)
async def bulk_upsert_recipe_ingredients(
    items: list[schemas.RecipeIngredientCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_recipe_ingredients(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@recipe_ingredient_router.put(
    "/recipe-ingredients/{uuid}",
    response_model=schemas.RecipeIngredientOut
)
async def upsert_recipe_ingredient(
    uuid: str,
    item: schemas.RecipeIngredientCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_recipe_ingredient(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@recipe_ingredient_router.delete("/recipe-ingredients/{uuid}")
async def delete_recipe_ingredient(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_recipe_ingredient(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Ingrédient non trouvé")
    return jsonable_encoder({"detail": "Ingrédient supprimé"}, by_alias=True)
