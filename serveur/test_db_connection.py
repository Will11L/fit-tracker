"""Mini-test pour isoler les problèmes de connexion asyncpg sur Windows."""
import asyncio
import asyncpg


async def main():
    try:
        conn = await asyncpg.connect(
            "postgresql://fittracker:fittracker@127.0.0.1:5432/fittracker"
        )
        result = await conn.fetchval("SELECT 1")
        print(f"OK : connexion reussie, SELECT 1 = {result}")
        await conn.close()
    except Exception as e:
        print(f"ERREUR : {type(e).__name__}: {e}")


asyncio.run(main())
