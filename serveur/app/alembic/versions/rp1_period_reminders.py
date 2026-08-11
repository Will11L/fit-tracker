"""add reminder offsets to routine_periods

Rappels notifs sur les périodes (2026-06-08) : 2 colonnes nullable
`reminder_before_start_minutes` + `reminder_before_end_minutes`.
Convention : NULL = rappel désactivé, 0 = pile à l'heure, N = N min avant.

Backfill (décision utilisateur) : les périodes EXISTANTES gardent leur notif de
début actuelle (ROUTINE_PERIOD_START pile au début) -> on pose
`reminder_before_start_minutes = 0` pour toutes les lignes existantes. La colonne
`reminder_before_end_minutes` reste NULL (rappel de fin opt-in). Pas de régression
du comportement livré par le déclencheur de début de période.

ADD COLUMN de colonnes NON référencées par le fragment trigger `routine_periods`
-> pas besoin de recharger `notify_row_change()` (politique #15 : reload requis
seulement sur RENAME/DROP/type d'une col mentionnée dans un trigger).

Revision ID: rp1_period_reminders
Revises: em1_user_email
"""

from alembic import op
import sqlalchemy as sa

revision = "rp1_period_reminders"
down_revision = "em1_user_email"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "routine_periods",
        sa.Column("reminder_before_start_minutes", sa.Integer(), nullable=True),
    )
    op.add_column(
        "routine_periods",
        sa.Column("reminder_before_end_minutes", sa.Integer(), nullable=True),
    )
    # Backfill : les périodes existantes gardent leur notif de début pile à l'heure.
    op.execute(
        "UPDATE routine_periods SET reminder_before_start_minutes = 0"
    )


def downgrade():
    op.drop_column("routine_periods", "reminder_before_end_minutes")
    op.drop_column("routine_periods", "reminder_before_start_minutes")
