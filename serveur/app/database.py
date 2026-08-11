# app/database.py
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import declarative_base, Session
from sqlalchemy import event, text   # ✅ text pour bind SQL

from app.context import client_id_ctx
from app.settings import settings

# --- Engine & session ---
engine = create_async_engine(settings.DATABASE_URL, pool_pre_ping=True)
AsyncSessionLocal = async_sessionmaker(bind=engine, expire_on_commit=False, class_=AsyncSession)
Base = declarative_base()

async def get_session() -> AsyncSession:
    async with AsyncSessionLocal() as session:
        yield session

# --- Event global ---
# À CHAQUE début de transaction, pose app.client_id si présent
@event.listens_for(Session, "after_begin", propagate=True)
def _set_client_id_on_begin(session, transaction, connection):
    cid = client_id_ctx.get()
    if cid:
        # ✅ utilise text() + binds (SQLAlchemy gère asyncpg proprement)
        connection.execute(
            text("select set_config('app.client_id', :cid, true)"),
            {"cid": cid},
        )
