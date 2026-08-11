"""n3 — Nutrition micros : +10 vitamines & mineraux sur foods + meal_entries

Pack essentiel ~10 (D11 etendu, cf. docs/NUTRITION_DESIGN.md + tache Notion
2026-06-13). Colonnes nullable (affichage info, pas de cibles en v1) :
  MINERAUX (mg) : iron, calcium, magnesium, zinc, potassium, sodium
  VITAMINES     : vitamin_c (mg), vitamin_d (µg), vitamin_b12 (µg),
                  vitamin_a (µg RAE)

Ajoutees a foods (catalogue) ET meal_entries (snapshot D5, pour permettre les
totaux jour). Les fragments triggers foods/meal_entries broadcastent ces
colonnes -> on recharge notify_row_change() en fin de migration (politique 15)
pour que la Pi pousse les events WS avec les nouveaux champs.

Revision ID: n3_nutrition_micros
Revises: n2_meals_preset_uuid
"""

from alembic import op
import sqlalchemy as sa


revision = "n3_nutrition_micros"
down_revision = "n2_meals_preset_uuid"
branch_labels = None
depends_on = None


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


def upgrade():
    for table in ("foods", "meal_entries"):
        for col in _MICRO_COLUMNS:
            op.add_column(table, sa.Column(col, sa.Float(), nullable=True))

    # Politique 15 : les fragments triggers foods/meal_entries referencent les
    # nouvelles colonnes -> recharger la fonction notify_row_change() pour eviter
    # un UndefinedColumnError au 1er INSERT/UPDATE apres deploiement.
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())


def downgrade():
    for table in ("foods", "meal_entries"):
        for col in _MICRO_COLUMNS:
            op.drop_column(table, col)

    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())
