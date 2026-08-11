# scripts/fix_ciqual_micros.py
#
# Backfill one-shot (Nutrition micros, 2026-06-13) : pose les 10 vitamines &
# mineraux (pack essentiel, D11 etendu) sur les foods CIQUAL DEJA en base + les
# snapshots meal_entries lies, a partir du fichier CIQUAL relu.
#
# Contexte : la migration n3 ajoute les colonnes (nullable) mais ne backfille
# pas les ~17 000 rows existantes (template + copies par user) ni les snapshots.
# Comme fix_ciqual_fiber.py : on relit le fichier CIQUAL avec le mapping etendu
# et on repose, par source_ref, les micros sur :
#   - foods (tous users, source='CIQUAL')
#   - meal_entries (snapshot D5) dont le food est un CIQUAL
# On bump `updated_at = now()` sur les rows modifiees pour propager via la sync
# (last-write-wins) + le NOTIFY trigger pousse l'event WS.
#
# Idempotent (repose la valeur correcte ; IS DISTINCT FROM evite les updates
# inutiles donc le 2e run ne bump rien). Usage (depuis serveur/, venv actif) :
#     python scripts/fix_ciqual_micros.py <table_ciqual.csv|.xlsx>

from __future__ import annotations

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


# Les 10 micros (colonne DB <-> cle de spec, identiques).
_MICRO_COLUMNS = (
    "iron_per_100g",
    "calcium_per_100g",
    "magnesium_per_100g",
    "zinc_per_100g",
    "potassium_per_100g",
    "sodium_per_100g",
    "vitamin_c_per_100g",
    "vitamin_d_per_100g",
    "vitamin_b12_per_100g",
    "vitamin_a_per_100g",
)


async def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage : python scripts/fix_ciqual_micros.py <table_ciqual.csv|.xlsx>")
    path = Path(sys.argv[1])
    if not path.is_file():
        raise SystemExit(f"Fichier introuvable : {path}")

    from sqlalchemy import text

    from app.database import AsyncSessionLocal
    from scripts.import_ciqual import extract_food_specs, read_table

    headers, rows = read_table(path)
    specs = extract_food_specs(headers, rows)
    # {source_ref: {micro: valeur}} (None si non determine dans CIQUAL).
    micros_by_ref = {s["source_ref"]: {c: s[c] for c in _MICRO_COLUMNS} for s in specs}
    print(f"📄 {len(micros_by_ref)} aliments CIQUAL relus (micros).")

    set_clause = ", ".join(f"{c} = :{c}" for c in _MICRO_COLUMNS)
    # Ne bump updated_at que si AU MOINS un micro change (idempotence : 2e run = no-op).
    distinct_clause = " OR ".join(f"{c} IS DISTINCT FROM :{c}" for c in _MICRO_COLUMNS)

    async with AsyncSessionLocal() as db:
        # 1) foods : repose les micros par source_ref (tous users) + bump updated_at.
        foods_fixed = 0
        for ref, micros in micros_by_ref.items():
            res = await db.execute(
                text(
                    f"UPDATE foods SET {set_clause}, updated_at = now() "
                    "WHERE source = 'CIQUAL' AND source_ref = :ref "
                    f"AND ({distinct_clause})"
                ),
                {**micros, "ref": ref},
            )
            foods_fixed += res.rowcount or 0

        # 2) meal_entries : re-derive le snapshot micros depuis le food CIQUAL lie.
        entries_set = ", ".join(f"{c} = f.{c}" for c in _MICRO_COLUMNS)
        entries_distinct = " OR ".join(
            f"e.{c} IS DISTINCT FROM f.{c}" for c in _MICRO_COLUMNS
        )
        res = await db.execute(
            text(
                f"UPDATE meal_entries e SET {entries_set}, updated_at = now() "
                "FROM foods f "
                "WHERE e.food_uuid = f.uuid AND f.source = 'CIQUAL' "
                f"AND ({entries_distinct})"
            )
        )
        entries_fixed = res.rowcount or 0

        await db.commit()
        print(f"✅ Repare : {foods_fixed} foods, {entries_fixed} meal_entries (updated_at bumpe).")


if __name__ == "__main__":
    asyncio.run(main())
