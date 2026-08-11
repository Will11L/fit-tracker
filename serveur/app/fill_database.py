import asyncio
import sys

# Force stdout en UTF-8 pour les emojis (sinon crash cp1252 sur Windows).
# `seed_database.py` utilise des ✅ dans ses prints. cf. F6-3 (inspect_schema)
# pour le pattern.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

from sqlalchemy import text
from app.database import Base, engine, AsyncSessionLocal
from app.clear_database import clear_all_tables_except_users
from app.seed_database import seed_all
from app.triggers_loader import (
    attach_triggers_sql,
    compose_function_sql,
    iso_utc_helper_sql,
    user_id_helper_sql,
)

async def main():
    async with engine.begin() as conn:
        # lister toutes les tables sauf "users"
        tables_to_drop = [t for t in Base.metadata.sorted_tables if t.name != "users"]

        # drop uniquement celles-là
        await conn.run_sync(Base.metadata.drop_all, tables=tables_to_drop)

        # recreate toutes les autres
        await conn.run_sync(Base.metadata.create_all, checkfirst=True)

        # Re-charger tous les helpers SQL + la fonction notify_row_change() +
        # attacher les triggers. Tous CREATE OR REPLACE => idempotent.
        # Ordre important : iso_utc + user_id_helper sont references par
        # notify_row_change (compose_function), elle-meme referencee par
        # attach_triggers. Sans ce ordre, si un helper manque sur une DB
        # fraiche (cf. piege rencontre 2026-05-06), l'INSERT echoue.
        await conn.execute(text(iso_utc_helper_sql()))
        await conn.execute(text(user_id_helper_sql()))
        await conn.execute(text(compose_function_sql()))
        await conn.execute(text(attach_triggers_sql()))

    async with AsyncSessionLocal() as db:
        # nettoyage des autres tables
        await clear_all_tables_except_users(db)
        await seed_all(db)

    print("✅ Base de test remplie avec succès.")

if __name__ == "__main__":
    asyncio.run(main())
