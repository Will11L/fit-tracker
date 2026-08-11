"""V5.4 nullable=False + defaults manquants sur 4 modeles SQLAlchemy

Revision ID: 20260505_v5_4_nullable_defaults
Revises: 20260505_add_is_admin_to_users

Resout les mismatches entre SQLAlchemy (nullable=True implicite) et
Pydantic (champs obligatoires) + ajoute 1 default metier manquant
(actual_workout_exercises.phase). Voir CLAUDE.md politique 10 pour
le critere "default = semantique metier majoritaire".
"""

from alembic import op
import sqlalchemy as sa


revision = "20260505_v5_4_nullable_defaults"
down_revision = "20260505_add_is_admin_to_users"
branch_labels = None
depends_on = None


def upgrade():
    # exercise_muscles.coefficient : NOT NULL + default 1
    # Semantique : muscle cible en priorite (a 100%) - cas majoritaire.
    op.alter_column(
        "exercise_muscles", "coefficient",
        existing_type=sa.Float(),
        nullable=False,
        server_default=sa.text("1"),
    )

    # muscle_goals.uuid : NOT NULL (default Python uuid4 deja en place cote ORM).
    op.alter_column(
        "muscle_goals", "uuid",
        existing_type=sa.String(),
        nullable=False,
    )

    # actual_workout_sets.is_dropset : NOT NULL + default false.
    # Semantique : set normal (non dropset) - ~95% des cas.
    op.alter_column(
        "actual_workout_sets", "is_dropset",
        existing_type=sa.Boolean(),
        nullable=False,
        server_default=sa.text("false"),
    )

    # actual_workout_exercises.phase : default "TRAINING" (nullable=False deja en place).
    # Semantique : exercice d'entrainement classique vs WARMUP/POST_TRAINING particuliers.
    op.alter_column(
        "actual_workout_exercises", "phase",
        existing_type=sa.String(),
        server_default=sa.text("'TRAINING'"),
    )


def downgrade():
    # phase : drop default
    op.execute("ALTER TABLE actual_workout_exercises ALTER COLUMN phase DROP DEFAULT")

    # is_dropset : drop NOT NULL + drop default
    op.alter_column(
        "actual_workout_sets", "is_dropset",
        existing_type=sa.Boolean(),
        nullable=True,
    )
    op.execute("ALTER TABLE actual_workout_sets ALTER COLUMN is_dropset DROP DEFAULT")

    # muscle_goals.uuid : drop NOT NULL
    op.alter_column(
        "muscle_goals", "uuid",
        existing_type=sa.String(),
        nullable=True,
    )

    # exercise_muscles.coefficient : drop NOT NULL + drop default
    op.alter_column(
        "exercise_muscles", "coefficient",
        existing_type=sa.Float(),
        nullable=True,
    )
    op.execute("ALTER TABLE exercise_muscles ALTER COLUMN coefficient DROP DEFAULT")
