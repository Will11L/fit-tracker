"""reset_db.py — RESET DESTRUCTIF du schema DB.

ATTENTION : DROP toutes les tables et les recree from scratch. A n'utiliser
QUE pour repartir de zero (schema corrompu, dev complet reset, etc.).

Demande confirmation interactive : tape 'reset' pour confirmer (autre =
abandonner). NE lance PAS uvicorn — la separation des concerns est explicite,
relance le serveur manuellement apres :
    uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

Pour un setup idempotent (sans drop), voir setup_db.py.
Pour les changements de schema incrementaux, voir Alembic (cf. CLAUDE.md §16) :
    alembic revision --autogenerate -m "..."
    alembic upgrade head

La Pi prod n'execute JAMAIS ce script (deploy.sh utilise alembic upgrade head).

Usage :
    python reset_db.py
"""
import asyncio
import sys
from app.database import Base, engine
from app.triggers_loader import (
    compose_function_sql,
    attach_triggers_sql,
    user_id_helper_sql,
    iso_utc_helper_sql,
)
import app.models  # noqa: F401 -- enregistre tous les modeles dans Base.metadata


async def recreate_schema():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
        print("OK Tables recreees (DROP ALL + CREATE)")

        await conn.exec_driver_sql(iso_utc_helper_sql())
        print("OK Fonction iso_utc() creee")

        await conn.exec_driver_sql(user_id_helper_sql())
        print("OK Fonction get_user_id_for() creee")

        await conn.exec_driver_sql(compose_function_sql())
        print("OK Fonction notify_row_change() creee")

        await conn.exec_driver_sql(attach_triggers_sql())
        print("OK Triggers attaches")


def confirm() -> bool:
    print("ATTENTION RESET DESTRUCTIF — toutes les tables vont etre supprimees.")
    print("Tape 'reset' pour confirmer (autre = abandonner) :")
    try:
        answer = input("> ").strip()
    except EOFError:
        return False
    return answer == "reset"


if __name__ == "__main__":
    if not confirm():
        print("Abandonne.")
        sys.exit(1)
    asyncio.run(recreate_schema())
    print()
    print("La DB est vide. Etapes suivantes :")
    print("  1. python -m app.fill_database          # seed avec les users de test")
    print("  2. uvicorn app.main:app --reload --host 0.0.0.0 --port 8000")
