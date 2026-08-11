from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

recipe_router = APIRouter(tags=["recipes"])

@recipe_router.get(
    "/recipes",
    response_model=list[schemas.RecipeOut]
)
async def get_all_recipes(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_recipes(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@recipe_router.get(
    "/recipes/{uuid}",
    response_model=schemas.RecipeOut
)
async def get_recipe_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    recipe = await crud.get_recipe_by_uuid(db, uuid)
    if not recipe or recipe.user_id != user_id:
        raise HTTPException(status_code=404, detail="Recette non trouvée")
    return jsonable_encoder(recipe, by_alias=True)

@recipe_router.put(
    "/recipes/bulk",
    response_model=list[schemas.RecipeOut]
)
async def bulk_upsert_recipes(
    items: list[schemas.RecipeCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_recipes(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@recipe_router.put(
    "/recipes/{uuid}",
    response_model=schemas.RecipeOut
)
async def upsert_recipe(
    uuid: str,
    item: schemas.RecipeCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_recipe(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@recipe_router.delete("/recipes/{uuid}")
async def delete_recipe(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_recipe(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Recette non trouvée")
    return jsonable_encoder({"detail": "Recette supprimée"}, by_alias=True)
