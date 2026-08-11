"""SQLAlchemy models pour les tables MCP : sessions + audit log.

Décisions Q2/Q7 design doc :
- mcp_sessions : 1 row par couple (client_id MCP, user_id). Stocke le hash
  sha256 du refresh_token, jamais le brut. Permet révocation via UI admin
  (`revoked_at`). Les access_tokens sont des JWT stateless signés HS256,
  ils ne sont PAS stockés ici — la table sert au refresh + à la révocation.
- mcp_audit_log : trace de tous les tool calls (user_id, tool, args, summary).
  Purge auto 30 jours via job APScheduler (décision Q7).
"""

from sqlalchemy import Column, DateTime, ForeignKey, Index, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSONB

from app.database import Base


class MCPSession(Base):
    """Session MCP émise après OAuth réussi.

    `client_id` = identifiant DCR du client MCP (ex: "claude-desktop-abc123").
    `refresh_token_hash` = sha256 du refresh_token brut (jamais stocké en clair).
    Lookup au refresh = requête indexée O(1) sur refresh_token_hash (aligné sur
    app/refresh_tokens.py, fix 2026-06-25). Le client_secret garde bcrypt.
    """

    __tablename__ = "mcp_sessions"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    client_id = Column(String, nullable=False, index=True)
    client_name = Column(String, nullable=True)  # "Claude Desktop", "Claude Code", etc.
    refresh_token_hash = Column(String, nullable=False)
    scopes = Column(String, nullable=False, default="")  # space-separated, ex "sport:read sport:write"
    created_at = Column(DateTime(timezone=True), nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    revoked_at = Column(DateTime(timezone=True), nullable=True)
    last_used_at = Column(DateTime(timezone=True), nullable=True)

    __table_args__ = (
        # Lookup O(1) du refresh token au refresh (sha256 déterministe, unique).
        Index("ix_mcp_sessions_token_hash", "refresh_token_hash", unique=True),
        Index("ix_mcp_sessions_user_revoked", "user_id", "revoked_at"),
    )


class MCPAuditLog(Base):
    """Log des tool calls MCP. Purge 30 jours (décision Q7)."""

    __tablename__ = "mcp_audit_log"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    client_id = Column(String, nullable=True)
    tool_name = Column(String, nullable=False, index=True)
    args = Column(JSONB, nullable=True)
    result_summary = Column(Text, nullable=True)  # tronqué à 500 chars
    status = Column(String, nullable=False, default="ok")  # "ok" | "error"
    error_message = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, index=True)


class MCPOAuthCode(Base):
    """Authorization codes éphémères (single-use, 10 min) émis pendant le
    flow OAuth après login utilisateur. Échangés contre access+refresh tokens.

    `code` est un secret aléatoire (32 bytes urlsafe), stocké en clair car
    durée de vie courte et bind à un seul échange. `code_challenge` pour PKCE.
    """

    __tablename__ = "mcp_oauth_codes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    code = Column(String, nullable=False, unique=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    client_id = Column(String, nullable=False)
    redirect_uri = Column(String, nullable=False)
    code_challenge = Column(String, nullable=True)
    code_challenge_method = Column(String, nullable=True)  # "S256"
    scopes = Column(String, nullable=False, default="")
    expires_at = Column(DateTime(timezone=True), nullable=False)
    consumed_at = Column(DateTime(timezone=True), nullable=True)


class MCPClient(Base):
    """Clients MCP enregistrés via Dynamic Client Registration (RFC 7591).

    Chaque client (Claude Desktop, Claude Code, etc.) se déclare au serveur,
    reçoit un `client_id` + `client_secret`. Réutilisé sur tous les flows
    OAuth ultérieurs.
    """

    __tablename__ = "mcp_clients"

    id = Column(Integer, primary_key=True, autoincrement=True)
    client_id = Column(String, nullable=False, unique=True, index=True)
    client_secret_hash = Column(String, nullable=False)  # bcrypt
    client_name = Column(String, nullable=True)
    redirect_uris = Column(JSONB, nullable=False)  # list of allowed redirect URIs
    grant_types = Column(JSONB, nullable=True)  # ["authorization_code", "refresh_token"]
    token_endpoint_auth_method = Column(String, nullable=True)  # "client_secret_post" | "none"
    created_at = Column(DateTime(timezone=True), nullable=False)
