# app/crud/notification.py

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, desc
from app.models import Notification
from app import schemas
from fastapi import HTTPException
from app.crud._concurrency import is_payload_stale


# Récupérer toutes les notifications d'un utilisateur (récentes d'abord)
async def get_all_notifications(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(Notification)
        .where(Notification.user_id == user_id)
        .order_by(desc(Notification.created_at))
    )
    return result.scalars().all()


# Récupérer une notification par UUID
async def get_notification_by_uuid(db: AsyncSession, uuid: str, user_id: int):
    result = await db.execute(
        select(Notification).where(Notification.uuid == uuid, Notification.user_id == user_id)
    )
    return result.scalars().first()


# Upsert une notification (même pattern que Exercise)
async def upsert_notification(
    db: AsyncSession,
    uuid: str,
    notification_data: schemas.NotificationCreate,
    user_id: int
):
    result = await db.execute(select(Notification).where(Notification.uuid == uuid))
    existing = result.scalars().first()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cette notification")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(notification_data.updated_at, existing.updated_at):
            return existing
        for key, value in notification_data.model_dump().items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création
    new_notification = Notification(**notification_data.model_dump(), user_id=user_id)
    db.add(new_notification)
    await db.commit()
    await db.refresh(new_notification)
    return new_notification


# Upsert en masse (même pattern que Exercise)
async def bulk_upsert_notifications(
    db: AsyncSession,
    notifications: list[schemas.NotificationCreate],
    user_id: int
):
    result_list = []

    for notif in notifications:
        existing_result = await db.execute(
            select(Notification).where(Notification.uuid == notif.uuid, Notification.user_id == user_id)
        )
        existing = existing_result.scalars().first()

        if existing:
            for key, value in notif.model_dump().items():
                if key not in ("uuid", "user_id"):
                    setattr(existing, key, value)
            result_list.append(existing)
        else:
            data = notif.model_dump()
            data["user_id"] = user_id
            new_notification = Notification(**data)
            db.add(new_notification)
            result_list.append(new_notification)

    await db.commit()
    return result_list


# Supprimer une notification
async def delete_notification(db: AsyncSession, uuid: str, user_id: int):
    notif = await get_notification_by_uuid(db, uuid, user_id)
    if not notif:
        raise HTTPException(status_code=404, detail="Notification non trouvée ou accès interdit")
    await db.delete(notif)
    await db.commit()
    return True
