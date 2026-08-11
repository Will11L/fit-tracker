"""setup_db.py — Bootstrap idempotent du schema DB.

Safe a relancer : cree les tables manquantes (checkfirst=True), recharge
les helpers SQL (CREATE OR REPLACE -> idempotents) et attache les triggers
(DROP IF EXISTS + CREATE via attach_triggers_sql -> idempotents).

NE drop JAMAIS. Pour un reset destructif, voir reset_db.py.
Pour les changements de schema incrementaux, voir Alembic (cf. CLAUDE.md §16) :
    alembic revision --autogenerate -m "..."
    alembic upgrade head

Usage :
    python setup_db.py
"""
import asyncio
from app.database import Base, engine
from app.triggers_loader import (
    compose_function_sql,
    attach_triggers_sql,
    user_id_helper_sql,
    iso_utc_helper_sql,
)
import app.models  # noqa: F401 -- enregistre tous les modeles dans Base.metadata


async def setup_schema():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all, checkfirst=True)
        print("OK Tables creees (manquantes uniquement, checkfirst=True)")

        await conn.exec_driver_sql(iso_utc_helper_sql())
        print("OK Fonction iso_utc() (CREATE OR REPLACE)")

        await conn.exec_driver_sql(user_id_helper_sql())
        print("OK Fonction get_user_id_for() (CREATE OR REPLACE)")

        await conn.exec_driver_sql(compose_function_sql())
        print("OK Fonction notify_row_change() (CREATE OR REPLACE)")

        await conn.exec_driver_sql(attach_triggers_sql())
        print("OK Triggers attaches (DROP IF EXISTS + CREATE)")


if __name__ == "__main__":
    asyncio.run(setup_schema())
