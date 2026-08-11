"""Add time column to meals (heure reelle du repas, facultative)

Revision ID: n4_meals_time
Revises: mcp_rt_sha256

Nutrition (2026-06-26) : heure reelle d'un repas du journal, distincte de
l'heure *indicative* recurrente d'une periode (meal_presets.default_time).
Saisie facultative dans le dialog « Ajouter un repas » ; surclasse le
default_time du preset a l'affichage quand elle est renseignee.

String "HH:MM", nullable (les repas existants restent sans heure).

Reload notify_row_change() apres l'ajout car meals_trigger.sql broadcaste
maintenant `time` dans le payload WS (politique CLAUDE.md §15).
"""

from alembic import op
import sqlalchemy as sa


revision = "n4_meals_time"
down_revision = "mcp_rt_sha256"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("meals", sa.Column("time", sa.String(), nullable=True))

    # Politique 15 : reload notify_row_change() apres modif du fragment
    # meals_trigger.sql (qui broadcaste maintenant `time`).
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())


def downgrade():
    op.drop_column("meals", "time")

    # Reload notify_row_change() pour qu'il ne reference plus meals.time.
    # Le fragment meals_trigger.sql doit etre revert AVANT de runner ce downgrade.
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())
