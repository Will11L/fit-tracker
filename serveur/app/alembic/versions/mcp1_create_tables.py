"""mcp1 — create MCP tables (sessions, audit log, oauth codes, clients)

Crée 4 tables pour le scaffold MCP (cf. docs/MCP_DESIGN.md §10 décisions Q1-Q8) :
- mcp_clients : clients DCR (Claude Desktop, Claude Code) avec client_id+secret
- mcp_oauth_codes : authorization codes single-use 10 min
- mcp_sessions : refresh tokens long-lived 30 jours
- mcp_audit_log : trace tool calls (purge 30j via job futur)

Revision ID: mcp1_create_tables
Revises: b4_add_excluded_dates
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB


revision = "mcp1_create_tables"
down_revision = "b4_add_excluded_dates"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "mcp_clients",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("client_id", sa.String(), nullable=False, unique=True),
        sa.Column("client_secret_hash", sa.String(), nullable=False),
        sa.Column("client_name", sa.String(), nullable=True),
        sa.Column("redirect_uris", JSONB(), nullable=False),
        sa.Column("grant_types", JSONB(), nullable=True),
        sa.Column("token_endpoint_auth_method", sa.String(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_mcp_clients_client_id", "mcp_clients", ["client_id"], unique=True)

    op.create_table(
        "mcp_oauth_codes",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("code", sa.String(), nullable=False, unique=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("client_id", sa.String(), nullable=False),
        sa.Column("redirect_uri", sa.String(), nullable=False),
        sa.Column("code_challenge", sa.String(), nullable=True),
        sa.Column("code_challenge_method", sa.String(), nullable=True),
        sa.Column("scopes", sa.String(), nullable=False, server_default=""),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("consumed_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_mcp_oauth_codes_code", "mcp_oauth_codes", ["code"], unique=True)

    op.create_table(
        "mcp_sessions",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("client_id", sa.String(), nullable=False),
        sa.Column("client_name", sa.String(), nullable=True),
        sa.Column("refresh_token_hash", sa.String(), nullable=False),
        sa.Column("scopes", sa.String(), nullable=False, server_default=""),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_used_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_mcp_sessions_user_id", "mcp_sessions", ["user_id"])
    op.create_index("ix_mcp_sessions_client_id", "mcp_sessions", ["client_id"])
    op.create_index(
        "ix_mcp_sessions_user_revoked", "mcp_sessions", ["user_id", "revoked_at"]
    )

    op.create_table(
        "mcp_audit_log",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("client_id", sa.String(), nullable=True),
        sa.Column("tool_name", sa.String(), nullable=False),
        sa.Column("args", JSONB(), nullable=True),
        sa.Column("result_summary", sa.Text(), nullable=True),
        sa.Column("status", sa.String(), nullable=False, server_default="ok"),
        sa.Column("error_message", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_mcp_audit_log_user_id", "mcp_audit_log", ["user_id"])
    op.create_index("ix_mcp_audit_log_tool_name", "mcp_audit_log", ["tool_name"])
    op.create_index("ix_mcp_audit_log_created_at", "mcp_audit_log", ["created_at"])


def downgrade():
    op.drop_table("mcp_audit_log")
    op.drop_table("mcp_sessions")
    op.drop_table("mcp_oauth_codes")
    op.drop_table("mcp_clients")
