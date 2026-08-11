"""fg1 — Categories d'aliments : +food_group sur foods (nullable)

Sous-tache serveur 1/4 de la feature [Categories d'aliments] (Notion). Ajoute
la colonne `food_group` (String, nullable — UI custom l'exige, legacy safe) au
catalogue foods. Le regne (ANIMALE/VEGETALE/COMPLEMENT/AUTRE) n'est PAS stocke :
il se derive du groupe (app.food_taxonomy.realm_of).

ADD COLUMN nullable = safe, pas de backfill ici. Le peuplement se fait en OPS
post-merge (hors-loop dev) : re-import CIQUAL (scripts/import_ciqual.py, mapping
etendu) puis `python scripts/backfill_nutrition.py --food-group` (propage aux
users deja crees).

Le fragment trigger foods_trigger.sql broadcaste desormais foodGroup au payload
WS -> on recharge notify_row_change() en fin de migration (politique 15) pour
eviter un UndefinedColumnError au 1er INSERT/UPDATE apres deploiement Pi.

Revision ID: fg1_add_food_group
Revises: n3_nutrition_micros
"""

from alembic import op
import sqlalchemy as sa


revision = "fg1_add_food_group"
down_revision = "n3_nutrition_micros"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("foods", sa.Column("food_group", sa.String(), nullable=True))

    # Politique 15 : foods_trigger.sql reference maintenant food_group -> reload
    # de notify_row_change() pour que la fonction PG en memoire connaisse la col.
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())


def downgrade():
    op.drop_column("foods", "food_group")

    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())
