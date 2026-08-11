from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

meal_preset_router = APIRouter(tags=["meal_presets"])

@meal_preset_router.get(
    "/meal-presets",
    response_model=list[schemas.MealPresetOut]
)
async def get_all_meal_presets(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_meal_presets(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_preset_router.get(
    "/meal-presets/{uuid}",
    response_model=schemas.MealPresetOut
)
async def get_meal_preset_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    preset = await crud.get_meal_preset_by_uuid(db, uuid)
    if not preset or preset.user_id != user_id:
        raise HTTPException(status_code=404, detail="Preset de repas non trouvé")
    return jsonable_encoder(preset, by_alias=True)

@meal_preset_router.put(
    "/meal-presets/bulk",
    response_model=list[schemas.MealPresetOut]
)
async def bulk_upsert_meal_presets(
    items: list[schemas.MealPresetCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_meal_presets(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_preset_router.put(
    "/meal-presets/{uuid}",
    response_model=schemas.MealPresetOut
)
async def upsert_meal_preset(
    uuid: str,
    item: schemas.MealPresetCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_meal_preset(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@meal_preset_router.delete("/meal-presets/{uuid}")
async def delete_meal_preset(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_meal_preset(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Preset de repas non trouvé")
    return jsonable_encoder({"detail": "Preset de repas supprimé"}, by_alias=True)
