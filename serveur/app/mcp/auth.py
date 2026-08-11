"""JWT MCP tokens + scopes.

Décisions Q2/Q3 design doc :
- Format : JWT signé HS256, même `JWT_SECRET_KEY` que /api/v1/token (cohérence).
- Audience dédiée "mcp-clients" pour distinguer des JWT API REST.
- TTL access = 60 min, refresh = 30 jours (cf. settings MCP_*_TTL_*).
"""

from __future__ import annotations

import secrets
import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import HTTPException, status
from jose import JWTError, jwt

from app.settings import settings

logger = logging.getLogger(__name__)

MCP_JWT_AUD = "mcp-clients"
MCP_JWT_ISS = settings.JWT_ISS  # "fittracker-api" — cohérent avec REST API

# TTLs — surchargeable via env (MCP_ACCESS_TOKEN_TTL_MINUTES, MCP_REFRESH_TOKEN_TTL_DAYS)
# Fallback aux valeurs décidées dans design doc §10 Q3.
import os

MCP_ACCESS_TTL_MINUTES = int(os.environ.get("MCP_ACCESS_TOKEN_TTL_MINUTES", "60"))
MCP_REFRESH_TTL_DAYS = int(os.environ.get("MCP_REFRESH_TOKEN_TTL_DAYS", "30"))
MCP_OAUTH_CODE_TTL_MINUTES = 10  # spec OAuth recommend court

# Scopes (cf. design doc §6.3)
SCOPES = {
    "sport:read": "Lecture data sport (workouts, exercices, stats)",
    "sport:write": "Écriture non destructive (créer/modifier workouts)",
    "sport:destructive": "Suppression + opérations admin",
    "ops:read": "Lecture runtime Pi (logs, status systemd)",
    "ops:destructive": "Opérations Pi destructives (restart, alembic upgrade)",
}


def create_mcp_access_token(
    user_id: int,
    client_id: str,
    scopes: list[str],
    expires_delta: Optional[timedelta] = None,
) -> str:
    """Émet un JWT access_token MCP.

    Payload : sub (user_id str), client_id, scopes (space-sep), iss, aud, exp.
    Validé via :func:`verify_mcp_access_token`.
    """
    now = datetime.now(timezone.utc)
    expire = now + (expires_delta or timedelta(minutes=MCP_ACCESS_TTL_MINUTES))
    payload = {
        "sub": str(user_id),
        "user_id": user_id,
        "client_id": client_id,
        "scope": " ".join(scopes),
        "iss": MCP_JWT_ISS,
        "aud": MCP_JWT_AUD,
        "exp": expire,
        "iat": now,
    }
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def verify_mcp_access_token(token: str) -> dict:
    """Valide un JWT MCP access_token et retourne le payload.

    Raises HTTPException 401 si invalide/expiré.
    """
    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM],
            audience=MCP_JWT_AUD,
            issuer=MCP_JWT_ISS,
        )
        if not payload.get("user_id"):
            raise JWTError("Missing user_id claim")
        return payload
    except JWTError as exc:
        logger.warning("MCP access token rejected: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired MCP token",
            headers={"WWW-Authenticate": "Bearer"},
        )


def generate_refresh_token() -> str:
    """Refresh token = 32 bytes urlsafe (~43 chars). Stocké hashé bcrypt."""
    return secrets.token_urlsafe(32)


def generate_authorization_code() -> str:
    """Authorization code OAuth, single-use, 10 min."""
    return secrets.token_urlsafe(32)


def generate_client_credentials() -> tuple[str, str]:
    """DCR : génère (client_id, client_secret) pour un nouveau client.

    client_id préfixé "mcpcli_" pour debug visuel.
    client_secret = 32 bytes urlsafe.
    """
    client_id = "mcpcli_" + secrets.token_urlsafe(12)
    client_secret = secrets.token_urlsafe(32)
    return client_id, client_secret


def parse_scopes(scope_string: str) -> list[str]:
    """Parse une string space-separated en liste de scopes validés."""
    if not scope_string:
        return []
    requested = [s.strip() for s in scope_string.split() if s.strip()]
    return [s for s in requested if s in SCOPES]
