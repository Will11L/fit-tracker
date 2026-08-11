"""w2 — Hydratation : +is_water sur foods (auto-comptage boissons eau)

Ajoute `foods.is_water` (bool, default false). Marque un aliment "boisson eau"
pour l'auto-comptage d'hydratation côté client (1 g = 1 ml) : total du jour =
SUM(water_intakes) + SUM(meal_entries dont l'aliment est is_water × quantity_g).
Posé à l'import OFF (categoriesTags contient `en:waters`) ou coché manuellement.

Le fragment `foods_trigger.sql` référence désormais `rec.is_water` → on recharge
`notify_row_change()` en fin de migration (politique 15) pour que la Pi pousse le
champ dans le payload WS `food_updated`.

Revision ID: w2_foods_is_water
Revises: w1_create_water_intakes
"""

from alembic import op
import sqlalchemy as sa


revision = "w2_foods_is_water"
down_revision = "w1_create_water_intakes"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "foods",
        sa.Column("is_water", sa.Boolean(), nullable=False, server_default=sa.text("false")),
    )

    # Politique 15 : le fragment foods_trigger référence rec.is_water → reload de
    # notify_row_change() (+ ré-attache, best-effort) pour le payload WS.
    from app.triggers_loader import compose_function_sql, attach_triggers_sql
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())


def downgrade():
    op.drop_column("foods", "is_water")

    # Recharger la fonction sans la référence is_water (le loader devra aussi
    # avoir été remis en arrière pour un vrai downgrade ; best-effort).
    from app.triggers_loader import compose_function_sql, attach_triggers_sql
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())
