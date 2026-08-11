"""add optional email column to users

Vrai email optionnel (2026-06-06). Le login reste username -- l'email n'est
qu'un champ de contact nullable. ADD COLUMN nullable, pas de backfill (les
users existants restent email=NULL, affiches "—" cote client).

Revision ID: em1_user_email
Revises: q1_create_quotes
"""

from alembic import op
import sqlalchemy as sa

revision = "em1_user_email"
down_revision = "q1_create_quotes"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "users",
        sa.Column("email", sa.String(), nullable=True),
    )


def downgrade():
    op.drop_column("users", "email")
