from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

exercise_equipment_router = APIRouter(tags=["exercise_equipments"])

@exercise_equipment_router.put(
    "/exercise-equipments/bulk",
    response_model=list[schemas.ExerciseEquipmentOut]
)
async def bulk_upsert_links(
    links: list[schemas.ExerciseEquipmentCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.bulk_upsert_exercise_equipment(db, links, user_id)
    return jsonable_encoder(result, by_alias=True)

@exercise_equipment_router.put(
    "/exercise-equipments/{uuid}",
    response_model=schemas.ExerciseEquipmentOut
)
async def upsert_link(
    uuid: str,
    link: schemas.ExerciseEquipmentCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.upsert_exercise_equipment(db, uuid, link, user_id)
    return jsonable_encoder(result, by_alias=True)

@exercise_equipment_router.get(
    "/exercise-equipments",
    response_model=list[schemas.ExerciseEquipmentOut]
)
async def get_all_links(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.get_all_exercise_equipment_links(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@exercise_equipment_router.get(
    "/exercise-equipments/{uuid}",
    response_model=schemas.ExerciseEquipmentOut
)
async def get_link_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    link = await crud.get_exercise_equipment_by_uuid(db, uuid, user_id)
    if not link:
        raise HTTPException(status_code=404, detail="Lien non trouvé")
    return jsonable_encoder(link, by_alias=True)

@exercise_equipment_router.delete("/exercise-equipments/{uuid}")
async def delete_link(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    success = await crud.delete_exercise_equipment(db, uuid, user_id)
    if not success:
        raise HTTPException(status_code=404, detail="Lien non trouvé")
    return jsonable_encoder({"detail": "Lien supprimé avec succès"}, by_alias=True)
