"""Add excluded_dates JSONB column to tasks (B.4 recurrence edit modes)

Revision ID: b4_add_excluded_dates
Revises: p0_unify_tasks

Phase 4 polish B.4 (2026-05-12) : permet l'edition d'une occurrence unique
d'une task recurrente (W/M/Y) sans casser la serie. Stocke les dates ISO
"YYYY-MM-DD" a exclure des occurrences generees par ScheduledTaskExpander.

JSONB array, NOT NULL, default '[]'. Ignore par les recurrences NONE/DAILY
(politique applicative, pas DB).

Reload notify_row_change() apres l'ajout car tasks_trigger.sql broadcaste
maintenant `excludedDates` dans le payload WS (politique CLAUDE.md §15).
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB


revision = "b4_add_excluded_dates"
down_revision = "p0_unify_tasks"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "tasks",
        sa.Column(
            "excluded_dates",
            JSONB(),
            nullable=False,
            server_default=sa.text("'[]'::jsonb"),
        ),
    )

    # Politique 15 : reload notify_row_change() apres modif du fragment
    # tasks_trigger.sql (qui broadcaste maintenant `excludedDates`).
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())


def downgrade():
    op.drop_column("tasks", "excluded_dates")

    # Reload notify_row_change() pour qu'il ne reference plus excluded_dates.
    # Le fragment tasks_trigger.sql doit etre revert AVANT de runner ce downgrade.
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())
