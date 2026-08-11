"""Inspecte le schéma effectif de la base Postgres (tables + colonnes).

Le `engine` du projet est `AsyncEngine` (asyncpg) ; `inspect(engine)` synchrone
ne marche pas. On passe par `await conn.run_sync(...)` pour exécuter
l'inspecteur SQLAlchemy synchrone à l'intérieur d'une connexion async.
"""
import asyncio

from sqlalchemy import inspect

from app.database import engine


async def _amain() -> None:
    async with engine.connect() as conn:
        tables, by_table = await conn.run_sync(_collect_schema)

    print(f"\nTables in database ({len(tables)} total):\n")
    for table in tables:
        print(f"- Table: {table}")
        for col in by_table[table]:
            print(f"    {col['name']}: {col['type']}")
        print()


def _collect_schema(sync_conn):
    """Exécuté dans un thread synchrone via `conn.run_sync`."""
    inspector = inspect(sync_conn)
    tables = inspector.get_table_names()
    by_table = {t: inspector.get_columns(t) for t in tables}
    return tables, by_table


def main() -> None:
    asyncio.run(_amain())


if __name__ == "__main__":
    main()
