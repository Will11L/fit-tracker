from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

task_check_router = APIRouter(tags=["task_checks"])


@task_check_router.get(
    "/task-checks",
    response_model=list[schemas.TaskCheckOut],
)
async def get_all_task_checks(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.get_user_accessible_task_checks(db, user_id)
    return jsonable_encoder(result, by_alias=True)


@task_check_router.get(
    "/task-checks/{uuid}",
    response_model=schemas.TaskCheckOut,
)
async def get_task_check_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    check = await crud.get_task_check_by_uuid(db, uuid)
    if not check or check.user_id != user_id:
        raise HTTPException(status_code=404, detail="TaskCheck non trouve")
    return jsonable_encoder(check, by_alias=True)


@task_check_router.put(
    "/task-checks/bulk",
    response_model=list[schemas.TaskCheckOut],
)
async def bulk_upsert_task_checks(
    items: list[schemas.TaskCheckCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.bulk_upsert_task_checks(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)


@task_check_router.put(
    "/task-checks/{uuid}",
    response_model=schemas.TaskCheckOut,
)
async def upsert_task_check(
    uuid: str,
    item: schemas.TaskCheckCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_task_check(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)


@task_check_router.delete("/task-checks/{uuid}")
async def delete_task_check(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    ok = await crud.delete_task_check(db, uuid, user_id)
    return jsonable_encoder(ok, by_alias=True)
