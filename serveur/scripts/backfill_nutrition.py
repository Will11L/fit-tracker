# scripts/backfill_nutrition.py
#
# Backfill du catalogue nutrition (foods + portions + meal_presets) pour les
# users EXISTANTS (crees avant Nutrition V2 ou avant l'import CIQUAL) : le
# copy ne se fait normalement qu'au /signup (copy_starter_pack).
#
# Reutilise app.starter_pack.copy_nutrition_pack — gardes d'idempotence
# separees : un user qui a deja des foods ne les recoit pas en double, un user
# qui a deja ses presets garde les siens (renommages preserves).
#
# Usage (depuis serveur/, venv active) :
#     python scripts/backfill_nutrition.py will bob     # users cibles
#     python scripts/backfill_nutrition.py --all        # tous (sauf template)
#     python scripts/backfill_nutrition.py --food-group # propage food_group
#
# Prerequis : l'import CIQUAL a deja ete lance (scripts/import_ciqual.py),
# sinon le template n'a pas de foods et seuls les presets sont crees.
#
# Mode --food-group (feature Categories d'aliments) : ne copie RIEN, mais propage
# la colonne `food_group` posee sur le catalogue du template (apres re-import
# CIQUAL avec le mapping etendu) vers les foods CIQUAL deja copies chez les users,
# par appariement source_ref. Bump updated_at -> remontee sync + push WS NOTIFY.
# Idempotent (IS DISTINCT FROM : le 2e run ne bump rien).

from __future__ import annotations

import asyncio
import sys
from pathlib import Path

# Lancable depuis serveur/ (python scripts/backfill_nutrition.py) : ajoute
# serveur/ au path pour importer app.*.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


async def _backfill_food_group(db) -> None:
    """Propage food_group du template -> foods CIQUAL des users (par source_ref).

    Lit {source_ref: food_group} sur le catalogue CIQUAL du template (pose par le
    re-import) puis UPDATE par source_ref sur TOUS les users (template inclus, mais
    deja a jour -> no-op), en bumpant updated_at pour la sync. Idempotent.
    """
    from sqlalchemy import select, text

    from app.models.food import Food
    from app.models.user import User
    from app.settings import settings

    template_id = (await db.execute(
        select(User.id).where(User.username == settings.STARTER_TEMPLATE_USERNAME)
    )).scalar_one_or_none()
    if template_id is None:
        raise SystemExit(
            f"User '{settings.STARTER_TEMPLATE_USERNAME}' introuvable. "
            "Lancer `python -m app.fill_database` (dev) pour le seeder."
        )

    template_rows = (await db.execute(
        select(Food.source_ref, Food.food_group).where(
            Food.user_id == template_id, Food.source == "CIQUAL"
        )
    )).all()
    group_by_ref = {ref: grp for ref, grp in template_rows if ref and grp is not None}
    if not group_by_ref:
        raise SystemExit(
            "Le template n'a aucun food_group CIQUAL : relancer d'abord "
            "`python scripts/import_ciqual.py <fichier>` (mapping etendu)."
        )
    print(f"📄 {len(group_by_ref)} food_group CIQUAL lus depuis le template.")

    fixed = 0
    for ref, grp in group_by_ref.items():
        res = await db.execute(
            text(
                "UPDATE foods SET food_group = :grp, updated_at = now() "
                "WHERE source = 'CIQUAL' AND source_ref = :ref "
                "AND food_group IS DISTINCT FROM :grp"
            ),
            {"grp": grp, "ref": ref},
        )
        fixed += res.rowcount or 0
    await db.commit()
    print(f"✅ food_group pose/propage sur {fixed} foods CIQUAL (template + users).")


async def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(
            "Usage : python scripts/backfill_nutrition.py "
            "<username>... | --all | --food-group"
        )

    from sqlalchemy import func, select

    from app.database import AsyncSessionLocal
    from app.models.food import Food
    from app.models.meal_preset import MealPreset
    from app.models.user import User
    from app.settings import settings
    from app.starter_pack import copy_nutrition_pack

    if sys.argv[1] == "--food-group":
        async with AsyncSessionLocal() as db:
            await _backfill_food_group(db)
        return

    async with AsyncSessionLocal() as db:
        if sys.argv[1] == "--all":
            users = (await db.execute(
                select(User).where(
                    User.username != settings.STARTER_TEMPLATE_USERNAME
                )
            )).scalars().all()
        else:
            usernames = sys.argv[1:]
            users = (await db.execute(
                select(User).where(User.username.in_(usernames))
            )).scalars().all()
            missing = set(usernames) - {u.username for u in users}
            if missing:
                raise SystemExit(f"Users introuvables : {', '.join(sorted(missing))}")

        for user in users:
            await copy_nutrition_pack(db, user.id)
            await db.commit()
            n_foods = (await db.execute(
                select(func.count()).select_from(Food).where(Food.user_id == user.id)
            )).scalar_one()
            n_presets = (await db.execute(
                select(func.count()).select_from(MealPreset)
                .where(MealPreset.user_id == user.id)
            )).scalar_one()
            print(f"✅ {user.username}: {n_foods} foods, {n_presets} meal_presets")


if __name__ == "__main__":
    asyncio.run(main())
