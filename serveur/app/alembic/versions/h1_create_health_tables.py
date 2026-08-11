"""h1 — Santé / Health Connect V1 : création des 3 tables

Tables : health_step_counts, health_metrics, health_goals.

- Type A user-scoped (cascade ownership politique 8) : les 3 ont user_id FK CASCADE.
- États UPPER_CASE (politique 11) : health_metrics.type (HEART_RATE/SLEEP/DISTANCE/
  ACTIVE_CALORIES), health_goals.type (STEPS v1).
- Dates/heures en String (convention projet) : date "YYYY-MM-DD",
  bucket_start/start_time "HH:MM", effective_from "YYYY-MM-DD".

Trigger NOTIFY : `attach_triggers.sql` ré-attache automatiquement
`notify_row_change()` à toute table avec colonnes id+uuid. Les 3 fragments
sont dans PER_TABLE_FRAGMENTS et les 3 cases sont ajoutés à `get_user_id_for()`.
On recharge les 3 helpers SQL en fin de migration (politique 15) pour que la
Pi pousse les events WS avec userId top-level.

Revision ID: h1_create_health
Revises: n4_meals_time
"""

from alembic import op
import sqlalchemy as sa


revision = "h1_create_health"
down_revision = "n4_meals_time"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "health_step_counts",
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
        sa.Column("bucket_start", sa.String(), nullable=False),
        sa.Column("steps", sa.Integer(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "health_metrics",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("type", sa.String(), nullable=False),
        sa.Column("value", sa.Float(), nullable=False),
        sa.Column("unit", sa.String(), nullable=False),
        sa.Column("date", sa.String(), nullable=False, index=True),
        sa.Column("start_time", sa.String(), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "health_goals",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("type", sa.String(), nullable=False),
        sa.Column("target", sa.Float(), nullable=False),
        sa.Column("effective_from", sa.String(), nullable=False, index=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    # Politique 15 : recharger les helpers + la fonction notify_row_change()
    # + ré-attacher les triggers pour que les 3 tables poussent leurs events WS.
    from app.triggers_loader import (
        compose_function_sql,
        attach_triggers_sql,
        user_id_helper_sql,
    )
    op.execute(user_id_helper_sql())
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())


def downgrade():
    op.drop_table("health_goals")
    op.drop_table("health_metrics")
    op.drop_table("health_step_counts")

    # Recharger la fonction sans les fragments santé (le loader devra aussi
    # avoir été remis en arrière pour un vrai downgrade ; best-effort).
    from app.triggers_loader import compose_function_sql, attach_triggers_sql
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())
