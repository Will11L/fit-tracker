"""n1 — Nutrition V1 : création des 8 tables (cf. docs/NUTRITION_DESIGN.md)

Tables : foods, food_portions, recipes, recipe_ingredients, meal_presets,
meals, meal_entries, nutrition_goals.

- Type A user-scoped (cascade ownership politique 8) : foods, recipes,
  meal_presets, meals, nutrition_goals (user_id FK CASCADE).
- Enfants (ownership indirect) : food_portions -> foods, recipe_ingredients
  -> recipes (+ FK foods CASCADE, référence vivante), meal_entries -> meals
  (+ FK foods/recipes SET NULL : snapshot D5, l'entry survit à sa source).
- États UPPER_CASE (politique 11) : Food.source CUSTOM/CIQUAL/OFF,
  Recipe.kind RECIPE/SAVED_MEAL, NutritionGoal.day_kind ALL.
- Dates/heures en String (convention projet) : meals.date,
  nutrition_goals.effective_from "YYYY-MM-DD", meal_presets.default_time "HH:MM".

Trigger NOTIFY : `attach_triggers.sql` ré-attache automatiquement
`notify_row_change()` à toute table avec colonnes id+uuid. Les 8 fragments
sont dans PER_TABLE_FRAGMENTS et les 8 cases sont ajoutés à
`get_user_id_for()`. On recharge les 3 helpers SQL en fin de migration
(politique 15) pour que la Pi pousse les events WS.

Revision ID: n1_create_nutrition
Revises: rp1_period_reminders
"""

from alembic import op
import sqlalchemy as sa


revision = "n1_create_nutrition"
down_revision = "rp1_period_reminders"
branch_labels = None
depends_on = None


def upgrade():
    # ---- Parents Type A user-scoped ----
    op.create_table(
        "foods",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("name", sa.String(), nullable=False),
        sa.Column("brand", sa.String(), nullable=True),
        sa.Column("source", sa.String(), nullable=False),
        sa.Column("source_ref", sa.String(), nullable=True),
        sa.Column("kcal_per_100g", sa.Float(), nullable=False),
        sa.Column("protein_per_100g", sa.Float(), nullable=False),
        sa.Column("carbs_per_100g", sa.Float(), nullable=False),
        sa.Column("fat_per_100g", sa.Float(), nullable=False),
        sa.Column("fiber_per_100g", sa.Float(), nullable=True),
        sa.Column("sugar_per_100g", sa.Float(), nullable=True),
        sa.Column("sat_fat_per_100g", sa.Float(), nullable=True),
        sa.Column("salt_per_100g", sa.Float(), nullable=True),
        sa.Column("is_favorite", sa.Boolean(), nullable=False, default=False),
        sa.Column("archived", sa.Boolean(), nullable=False, default=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "recipes",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("name", sa.String(), nullable=False),
        sa.Column("kind", sa.String(), nullable=False),
        sa.Column("total_weight_g", sa.Float(), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "meal_presets",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("name", sa.String(), nullable=False),
        sa.Column("order_index", sa.Integer(), nullable=False),
        sa.Column("default_time", sa.String(), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "meals",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("date", sa.String(), nullable=False, index=True),
        sa.Column("name", sa.String(), nullable=False),
        sa.Column("order_index", sa.Integer(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "nutrition_goals",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("effective_from", sa.String(), nullable=False, index=True),
        sa.Column("day_kind", sa.String(), nullable=False, default="ALL"),
        sa.Column("kcal", sa.Float(), nullable=False),
        sa.Column("protein_g", sa.Float(), nullable=False),
        sa.Column("carbs_g", sa.Float(), nullable=False),
        sa.Column("fat_g", sa.Float(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    # ---- Enfants (ownership indirect via parent) ----
    op.create_table(
        "food_portions",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column(
            "food_uuid",
            sa.String(),
            sa.ForeignKey("foods.uuid", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("label", sa.String(), nullable=False),
        sa.Column("grams", sa.Float(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "recipe_ingredients",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column(
            "recipe_uuid",
            sa.String(),
            sa.ForeignKey("recipes.uuid", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column(
            "food_uuid",
            sa.String(),
            sa.ForeignKey("foods.uuid", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("quantity_g", sa.Float(), nullable=False),
        sa.Column("order_index", sa.Integer(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "meal_entries",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column(
            "meal_uuid",
            sa.String(),
            sa.ForeignKey("meals.uuid", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column(
            "food_uuid",
            sa.String(),
            sa.ForeignKey("foods.uuid", ondelete="SET NULL"),
            nullable=True,
            index=True,
        ),
        sa.Column(
            "recipe_uuid",
            sa.String(),
            sa.ForeignKey("recipes.uuid", ondelete="SET NULL"),
            nullable=True,
            index=True,
        ),
        sa.Column("display_name", sa.String(), nullable=False),
        sa.Column("quantity_g", sa.Float(), nullable=False),
        sa.Column("portion_label", sa.String(), nullable=True),
        sa.Column("kcal_per_100g", sa.Float(), nullable=False),
        sa.Column("protein_per_100g", sa.Float(), nullable=False),
        sa.Column("carbs_per_100g", sa.Float(), nullable=False),
        sa.Column("fat_per_100g", sa.Float(), nullable=False),
        sa.Column("fiber_per_100g", sa.Float(), nullable=True),
        sa.Column("sugar_per_100g", sa.Float(), nullable=True),
        sa.Column("sat_fat_per_100g", sa.Float(), nullable=True),
        sa.Column("salt_per_100g", sa.Float(), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    # Politique 15 : recharger les helpers + la fonction notify_row_change()
    # + ré-attacher les triggers pour que les 8 tables poussent leurs events WS.
    from app.triggers_loader import (
        compose_function_sql,
        attach_triggers_sql,
        user_id_helper_sql,
    )
    op.execute(user_id_helper_sql())
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())


def downgrade():
    # Ordre inverse des FK : enfants d'abord.
    op.drop_table("meal_entries")
    op.drop_table("recipe_ingredients")
    op.drop_table("food_portions")
    op.drop_table("nutrition_goals")
    op.drop_table("meals")
    op.drop_table("meal_presets")
    op.drop_table("recipes")
    op.drop_table("foods")

    # Recharger la fonction sans les fragments nutrition (le loader devra
    # aussi avoir été remis en arrière pour un vrai downgrade ; best-effort).
    from app.triggers_loader import compose_function_sql, attach_triggers_sql
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())
