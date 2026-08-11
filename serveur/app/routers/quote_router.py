from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

quote_router = APIRouter(tags=["quotes"])

@quote_router.get(
    "/quotes",
    response_model=list[schemas.QuoteOut]
)
async def get_all_quotes(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.get_all_quotes(db, user_id)
    return jsonable_encoder(result, by_alias=True)

@quote_router.get(
    "/quotes/{uuid}",
    response_model=schemas.QuoteOut
)
async def get_quote_by_uuid(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    quote = await crud.get_quote_by_uuid(db, uuid)
    if not quote or quote.user_id != user_id:
        raise HTTPException(status_code=404, detail="Quote non trouvée")
    return jsonable_encoder(quote, by_alias=True)

@quote_router.put(
    "/quotes/bulk",
    response_model=list[schemas.QuoteOut]
)
async def bulk_upsert_quotes(
    items: list[schemas.QuoteCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.bulk_upsert_quotes(db, items, user_id)
    return jsonable_encoder(result, by_alias=True)

@quote_router.put(
    "/quotes/{uuid}",
    response_model=schemas.QuoteOut
)
async def upsert_quote(
    uuid: str,
    item: schemas.QuoteCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    if item.uuid != uuid:
        raise HTTPException(status_code=400, detail="UUID path/body mismatch")
    result = await crud.upsert_quote(db, uuid, item, user_id)
    return jsonable_encoder(result, by_alias=True)

@quote_router.delete("/quotes/{uuid}")
async def delete_quote(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    ok = await crud.delete_quote(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Quote non trouvée")
    return jsonable_encoder({"detail": "Quote supprimée"}, by_alias=True)
