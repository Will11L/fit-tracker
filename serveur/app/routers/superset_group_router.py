from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import crud, schemas
from app.database import get_session
from app.dependencies import get_current_user_id

superset_group_router = APIRouter(tags=["superset_groups"])

@superset_group_router.get(
    "/superset-groups",
    response_model=list[schemas.SupersetGroupOut]
)
async def list_superset_groups(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    groups = await crud.get_all_superset_groups(db, user_id)
    return jsonable_encoder(groups, by_alias=True)

@superset_group_router.get(
    "/superset-groups/{uuid}",
    response_model=schemas.SupersetGroupOut
)
async def read_superset_group(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    group = await crud.get_superset_group_by_uuid(db, uuid)
    if not group or group.user_id != user_id:
        raise HTTPException(status_code=404, detail="Superset group not found or unauthorized")
    return jsonable_encoder(group, by_alias=True)

@superset_group_router.put(
    "/superset-groups/bulk",
    response_model=list[schemas.SupersetGroupOut]
)
async def bulk_upsert_superset_groups(
    groups: list[schemas.SupersetGroupCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    results = await crud.bulk_upsert_superset_groups(db, groups, user_id)
    return jsonable_encoder(results, by_alias=True)

@superset_group_router.put(
    "/superset-groups/{uuid}",
    response_model=schemas.SupersetGroupOut
)
async def upsert_superset_group(
    uuid: str,
    group: schemas.SupersetGroupCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if group.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_superset_group(db, uuid, group, user_id)
    return jsonable_encoder(result, by_alias=True)

@superset_group_router.delete("/superset-groups/{uuid}")
async def delete_superset_group(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_superset_group(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Superset group not found or unauthorized")
    return {"ok": True}
