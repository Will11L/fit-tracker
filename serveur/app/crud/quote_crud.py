# app/crud/quote_crud.py
from typing import Sequence
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.quote import Quote
from app.schemas import QuoteCreate
from app.crud._concurrency import is_payload_stale


async def get_all_quotes(db: AsyncSession, user_id: int) -> Sequence[Quote]:
    res = await db.execute(select(Quote).where(Quote.user_id == user_id))
    return res.scalars().all()


async def get_quote_by_uuid(db: AsyncSession, uuid: str) -> Quote | None:
    res = await db.execute(select(Quote).where(Quote.uuid == uuid))
    return res.scalar_one_or_none()


# Upsert (signature canonique : db, uuid, dto, user_id)
async def upsert_quote(db: AsyncSession, uuid: str, dto: QuoteCreate, user_id: int) -> Quote:
    res = await db.execute(select(Quote).where(Quote.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à cette citation")
        # Last-write-wins : skip si payload plus ancien que serveur (2026-05-07)
        if is_payload_stale(dto.updated_at, existing.updated_at):
            return existing
        # Écrasement total via model_dump (canonique)
        for key, value in dto.model_dump().items():
            if key not in ("uuid", "user_id"):
                setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Création : injecte user_id depuis l'auth (politique sécurité V2.2)
    data = dto.model_dump()
    data["user_id"] = user_id
    data["uuid"] = uuid
    q = Quote(**data)
    db.add(q)
    await db.commit()
    await db.refresh(q)
    return q


# Bulk upsert (signature canonique : db, items, user_id)
async def bulk_upsert_quotes(
    db: AsyncSession,
    items: list[QuoteCreate],
    user_id: int,
) -> list[Quote]:
    out: list[Quote] = []
    for dto in items:
        out.append(await upsert_quote(db, dto.uuid, dto, user_id))
    return out


async def delete_quote(db: AsyncSession, uuid: str, user_id: int) -> bool:
    res = await db.execute(
        select(Quote).where(Quote.uuid == uuid, Quote.user_id == user_id)
    )
    quote = res.scalar_one_or_none()
    if not quote:
        return False
    await db.delete(quote)
    await db.commit()
    return True
