"""add is_admin column to users + promote 'will' as admin

Revision ID: 20260505_add_is_admin_to_users
Revises: 2025xxxx_notify_triggers
"""

from alembic import op
import sqlalchemy as sa

revision = "20260505_add_is_admin_to_users"
down_revision = "2025xxxx_notify_triggers"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "users",
        sa.Column(
            "is_admin",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("false"),
        ),
    )
    # Promotion du compte principal (cf. REVIEW.md §4 Vague 1.3).
    # Les autres comptes restent non-admin par defaut, a promouvoir manuellement
    # au besoin : UPDATE users SET is_admin = TRUE WHERE username = '...'
    op.execute("UPDATE users SET is_admin = TRUE WHERE username = 'will'")


def downgrade():
    op.drop_column("users", "is_admin")
