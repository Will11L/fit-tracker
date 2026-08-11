# scripts/fix_ciqual_fiber.py
#
# Reparation one-shot (bug 2026-06-13) : l'import CIQUAL initial a mappe la
# colonne "Energie, N x facteur Jones, avec fibres (kJ/100 g)" sur fiber_per_100g
# (le mot "fibres" matchait avant la vraie colonne "Fibres alimentaires") -> tous
# les foods CIQUAL ont l'energie kJ a la place des fibres (ex. Avoine = 1570 g).
#
# Corrige import_ciqual.py (mapping) ne suffit pas : les ~17 000 rows deja en base
# (template + copies par user) + les snapshots meal_entries gardent la valeur fausse.
# Ce script relit le fichier CIQUAL avec le mapping CORRIGE et repose la bonne
# valeur de fibres, par source_ref, sur :
#   - foods (tous users, source='CIQUAL')
#   - meal_entries (snapshot D5) dont le food est un CIQUAL
#
# Idempotent (repose la valeur correcte). Usage (depuis serveur/, venv actif) :
#     python scripts/fix_ciqual_fiber.py <table_ciqual.csv|.xlsx>

from __future__ import annotations

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


async def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage : python scripts/fix_ciqual_fiber.py <table_ciqual.csv|.xlsx>")
    path = Path(sys.argv[1])
    if not path.is_file():
        raise SystemExit(f"Fichier introuvable : {path}")

    from sqlalchemy import text

    from app.database import AsyncSessionLocal
    from scripts.import_ciqual import extract_food_specs, read_table

    headers, rows = read_table(path)
    specs = extract_food_specs(headers, rows)
    # {source_ref: fibres correctes} (None si non determine dans CIQUAL).
    fiber_by_ref = {s["source_ref"]: s["fiber_per_100g"] for s in specs}
    print(f"📄 {len(fiber_by_ref)} aliments CIQUAL relus (fibres corrigees).")

    async with AsyncSessionLocal() as db:
        # 1) foods : repose la bonne valeur par source_ref (tous users).
        foods_fixed = 0
        for ref, fiber in fiber_by_ref.items():
            res = await db.execute(
                text(
                    "UPDATE foods SET fiber_per_100g = :f "
                    "WHERE source = 'CIQUAL' AND source_ref = :r "
                    "AND fiber_per_100g IS DISTINCT FROM :f"
                ),
                {"f": fiber, "r": ref},
            )
            foods_fixed += res.rowcount or 0

        # 2) meal_entries : re-derive le snapshot fibres depuis le food CIQUAL lie.
        res = await db.execute(
            text(
                "UPDATE meal_entries e SET fiber_per_100g = f.fiber_per_100g "
                "FROM foods f "
                "WHERE e.food_uuid = f.uuid AND f.source = 'CIQUAL' "
                "AND e.fiber_per_100g IS DISTINCT FROM f.fiber_per_100g"
            )
        )
        entries_fixed = res.rowcount or 0

        await db.commit()
        print(f"✅ Repare : {foods_fixed} foods, {entries_fixed} meal_entries.")


if __name__ == "__main__":
    asyncio.run(main())
