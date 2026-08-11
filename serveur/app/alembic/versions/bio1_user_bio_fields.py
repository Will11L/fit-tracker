"""add bio fields (birth_date, sex, height_cm, weight_kg) to users

Revision ID: bio1_user_bio_fields
Revises: m3l2_reload_trg
"""

from alembic import op
import sqlalchemy as sa

revision = "bio1_user_bio_fields"
down_revision = "m3l2_reload_trg"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "users",
        sa.Column("birth_date", sa.Date(), nullable=True),
    )
    op.add_column(
        "users",
        sa.Column("sex", sa.String(), nullable=True),
    )
    op.add_column(
        "users",
        sa.Column("height_cm", sa.Float(), nullable=True),
    )
    op.add_column(
        "users",
        sa.Column("weight_kg", sa.Float(), nullable=True),
    )


def downgrade():
    op.drop_column("users", "weight_kg")
    op.drop_column("users", "height_cm")
    op.drop_column("users", "sex")
    op.drop_column("users", "birth_date")
