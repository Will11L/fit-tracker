"""mcp_rt_sha256 — MCP sessions : refresh token en SHA-256 indexe (aligne sur rt1)

Aligne mcp_sessions.refresh_token_hash sur le meme schema que app/refresh_tokens.py
(migration rt1_sha256_token_lookup) : bcrypt -> sha256 deterministe + index UNIQUE,
lookup O(1) au refresh au lieu d'iterer + bcrypt.checkpw. Le lookup MCP filtrait
deja par client_id (volume faible, pas le DoS de l'app) mais on aligne pour la
coherence + future-proof. Le client_secret (mcp_clients) garde bcrypt (inchange).

Les hashes bcrypt existants ne matchent pas un lookup sha256 -> purge des sessions
(les clients MCP refont le flow OAuth une fois ; quelques sessions seulement).

Revision ID: mcp_rt_sha256
Revises: rt1_sha256_token_lookup
"""
from alembic import op


revision = "mcp_rt_sha256"
down_revision = "rt1_sha256_token_lookup"
branch_labels = None
depends_on = None


def upgrade():
    # Hashes bcrypt inutilisables avec le lookup sha256 -> purge (re-OAuth une fois).
    op.execute("DELETE FROM mcp_sessions")
    op.create_index(
        "ix_mcp_sessions_token_hash",
        "mcp_sessions",
        ["refresh_token_hash"],
        unique=True,
    )


def downgrade():
    op.drop_index("ix_mcp_sessions_token_hash", table_name="mcp_sessions")
