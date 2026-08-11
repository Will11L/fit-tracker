from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

task_router = APIRouter(tags=["tasks"])


@task_router.get(
    "/tasks",
    response_model=list[schemas.TaskOut],
)
async def get_all_tasks(
    period_uuid: str | None = Query(default=None, alias="periodUuid"),
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    """Liste des taches du user.

    Si `periodUuid` fourni : retourne uniquement les taches DAILY de cette
    periode (utilise par RoutineTasksScreen pour grouper par periode).
    """
    if period_uuid:
        result = await crud.get_tasks_by_period_uuid(db, user_id, period_uuid)
    else:
        result = await crud.get_user_accessible_tasks(db, user_id)
    return jsonable_encoder(result, by_alias=True)


@task_router.get(
    "/tasks/{uuid}",
    response_model=schemas.TaskOut,
)
async def get_task_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    task = await crud.get_task_by_uuid(db, uuid)
    if not task or task.user_id != user_id:
        raise HTTPException(status_code=404, detail="Task non trouvee")
    return jsonable_encoder(task, by_alias=True)


@task_router.put(
    "/tasks/bulk",
    response_model=list[schemas.TaskOut],
)
async def bulk_upsert_tasks(
    items: list[schemas.TaskCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.bulk_upsert_tasks(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)


@task_router.put(
    "/tasks/{uuid}",
    response_model=schemas.TaskOut,
)
async def upsert_task(
    uuid: str,
    item: schemas.TaskCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_task(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)


@task_router.delete("/tasks/{uuid}")
async def delete_task(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    ok = await crud.delete_task(db, uuid, user_id)
    return jsonable_encoder(ok, by_alias=True)
