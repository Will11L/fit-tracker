from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

notification_router = APIRouter(tags=["notifications"])


@notification_router.get(
    "/notifications",
    response_model=list[schemas.NotificationOut]
)
async def get_all_notifications(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_notifications(db, user_id)
    return jsonable_encoder(result, by_alias=True)


@notification_router.get(
    "/notifications/{uuid}",
    response_model=schemas.NotificationOut
)
async def get_notification_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    notification = await crud.get_notification_by_uuid(db, uuid, user_id)
    if not notification:
        raise HTTPException(status_code=404, detail="Notification not found")
    return jsonable_encoder(notification, by_alias=True)


@notification_router.put(
    "/notifications/bulk",
    response_model=list[schemas.NotificationOut]
)
async def bulk_upsert_notifications(
    notifications: list[schemas.NotificationCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_notifications(db, notifications, user_id)
    return jsonable_encoder(result, by_alias=True)


@notification_router.put(
    "/notifications/{uuid}",
    response_model=schemas.NotificationOut
)
async def upsert_notification(
    uuid: str,
    notification: schemas.NotificationCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_notification(db, uuid, notification, user_id)
    return jsonable_encoder(result, by_alias=True)


@notification_router.delete("/notifications/{uuid}")
async def delete_notification(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.delete_notification(db, uuid, user_id)
    return jsonable_encoder(result, by_alias=True)
