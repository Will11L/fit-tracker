"""n2 — Nutrition : lien stable repas↔periode via meals.preset_uuid

Fix bug Functional review V5 (2026-06-12) : le journal appariait Meal↔MealPreset
par NOM, donc renommer une periode (« Dejeuner »→« Dejeuner4 ») orphelinait les
repas existants. On ajoute un lien stable par uuid : `meals.preset_uuid` (FK
meal_presets.uuid ON DELETE SET NULL) — le repas suit le renommage partout et
survit a la suppression du preset (bascule en « ad hoc »).

Le fragment trigger `meals` broadcast maintenant `presetUuid` (politique 15) :
on recharge `notify_row_change()` en fin de migration.

Revision ID: n2_meals_preset_uuid
Revises: n1_create_nutrition
"""

from alembic import op
import sqlalchemy as sa


revision = "n2_meals_preset_uuid"
down_revision = "n1_create_nutrition"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("meals", sa.Column("preset_uuid", sa.String(), nullable=True))
    op.create_index("ix_meals_preset_uuid", "meals", ["preset_uuid"])
    op.create_foreign_key(
        "fk_meals_preset_uuid",
        "meals",
        "meal_presets",
        ["preset_uuid"],
        ["uuid"],
        ondelete="SET NULL",
    )

    # Politique 15 : le fragment trigger `meals` broadcast desormais `preset_uuid`
    # -> recharger la fonction PG en memoire, sinon le 1er UPDATE/INSERT crashe en
    # 500 (UndefinedColumnError) ou n'inclut pas le champ dans l'event WS.
    from app.triggers_loader import compose_function_sql, attach_triggers_sql

    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())


def downgrade():
    op.drop_constraint("fk_meals_preset_uuid", "meals", type_="foreignkey")
    op.drop_index("ix_meals_preset_uuid", table_name="meals")
    op.drop_column("meals", "preset_uuid")

    from app.triggers_loader import compose_function_sql, attach_triggers_sql

    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())
