from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

meal_entry_router = APIRouter(tags=["meal_entries"])

@meal_entry_router.get(
    "/meal-entries",
    response_model=list[schemas.MealEntryOut]
)
async def get_all_meal_entries(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_meal_entries(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_entry_router.get(
    "/meal-entries/{uuid}",
    response_model=schemas.MealEntryOut
)
async def get_meal_entry_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    entry = await crud.get_meal_entry_by_uuid(db, uuid)
    if not entry:
        raise HTTPException(status_code=404, detail="Entrée de repas non trouvée")
    owned = await crud.is_meal_owned_by_user_uuid(db, entry.meal_uuid, user_id)
    if not owned:
        raise HTTPException(status_code=404, detail="Entrée de repas non trouvée")
    return jsonable_encoder(entry, by_alias=True)

@meal_entry_router.put(
    "/meal-entries/bulk",
    response_model=list[schemas.MealEntryOut]
)
async def bulk_upsert_meal_entries(
    items: list[schemas.MealEntryCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_meal_entries(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_entry_router.put(
    "/meal-entries/{uuid}",
    response_model=schemas.MealEntryOut
)
async def upsert_meal_entry(
    uuid: str,
    item: schemas.MealEntryCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_meal_entry(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_entry_router.delete("/meal-entries/{uuid}")
async def delete_meal_entry(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_meal_entry(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Entrée de repas non trouvée")
    return jsonable_encoder({"detail": "Entrée de repas supprimée"}, by_alias=True)
