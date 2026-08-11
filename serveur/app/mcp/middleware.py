"""Middleware Bearer auth pour le sous-app MCP.

Décodes le `Authorization: Bearer <jwt>` MCP, populate les ContextVars du
module `context.py`. Si pas de token / token invalide → 401.

Exemptions : les routes OAuth (/oauth/*) et le metadata (.well-known) doivent
être accessibles sans auth.
"""

from __future__ import annotations

import logging

from jose import JWTError
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse

from app.mcp.auth import verify_mcp_access_token
from app.mcp.context import set_mcp_context

logger = logging.getLogger(__name__)

_PUBLIC_PREFIXES = (
    "/oauth/",
    "/.well-known/",
    "/authorize",
    "/register",
    "/token",
    "/__ping__",
)


def _is_public_path(path: str) -> bool:
    return any(path.startswith(p) or path == p.rstrip("/") for p in _PUBLIC_PREFIXES)


class MCPBearerAuthMiddleware(BaseHTTPMiddleware):
    """Valide Bearer token MCP sur les routes non-publiques."""

    async def dispatch(self, request: Request, call_next):
        path = request.url.path
        if _is_public_path(path):
            return await call_next(request)

        # RFC 9728 §5.1 : pointe le client vers les métadonnées de la resource
        # (servies au domain-root, cf. main.py) pour la découverte OAuth.
        base = f"{request.url.scheme}://{request.url.netloc}"
        www_auth = f'Bearer resource_metadata="{base}/.well-known/oauth-protected-resource"'

        auth_header = request.headers.get("Authorization") or ""
        if not auth_header.lower().startswith("bearer "):
            return JSONResponse(
                {"error": "missing_token", "error_description": "Authorization Bearer token required"},
                status_code=401,
                headers={"WWW-Authenticate": www_auth},
            )

        token = auth_header[7:].strip()
        try:
            payload = verify_mcp_access_token(token)
        except Exception as exc:
            logger.info("MCP auth rejected on %s: %s", path, exc)
            return JSONResponse(
                {"error": "invalid_token", "error_description": "Token invalid or expired"},
                status_code=401,
                headers={"WWW-Authenticate": www_auth},
            )

        scopes_str = payload.get("scope", "")
        scopes = scopes_str.split() if scopes_str else []
        set_mcp_context(
            user_id=payload["user_id"],
            client_id=payload.get("client_id", ""),
            scopes=scopes,
        )
        return await call_next(request)
