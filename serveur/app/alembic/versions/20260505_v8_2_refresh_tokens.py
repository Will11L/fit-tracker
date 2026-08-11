"""V8.2 — Add refresh_tokens table for OAuth-like refresh flow.

Schema cf. app/models/refresh_token.py.
- token_hash : bcrypt hash du token brut (jamais stocke en clair)
- expires_at : durée 7j depuis création (cf. settings.REFRESH_TOKEN_EXPIRE_DAYS)
- revoked_at : NULL = actif ; non-NULL = revoke explicite ou rotation
- Index (user_id, revoked_at) pour revoke_all + lookup actifs
"""
from alembic import op
import sqlalchemy as sa


# Révisions
revision = "20260505_v8_2_refresh_tokens"
down_revision = "20260505_v5_1_drop_mws"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("token_hash", sa.String(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index(
        "ix_refresh_tokens_user_revoked",
        "refresh_tokens",
        ["user_id", "revoked_at"],
    )


def downgrade():
    op.drop_index("ix_refresh_tokens_user_revoked", table_name="refresh_tokens")
    op.drop_table("refresh_tokens")
