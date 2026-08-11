"""Construit le sous-app FastAPI MCP à monter dans app/main.py.

Structure des routes sous `/mcp/` :
- `/mcp/.well-known/oauth-authorization-server` — metadata RFC 8414 (public)
- `/mcp/.well-known/oauth-protected-resource` — metadata RFC 9728 (public)
- `/mcp/oauth/register` — DCR (public)
- `/mcp/oauth/authorize` (GET/POST) — consent screen + login (public)
- `/mcp/oauth/token` — exchange + refresh (public, validation client_secret)
- `/mcp/__ping__` — healthcheck du sous-app (public)
- `/mcp/...` (autres) — MCP protocol (Bearer auth requise)
"""

from __future__ import annotations

import logging

from fastapi import FastAPI

from app.mcp.middleware import MCPBearerAuthMiddleware
from app.mcp.routes_oauth import oauth_router

logger = logging.getLogger(__name__)


def build_subapp() -> FastAPI:
    """Assemble le sous-app MCP complet."""
    subapp = FastAPI(
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
        title="sport-app MCP",
    )

    # 1. Routes OAuth + metadata + ping (sans auth Bearer)
    subapp.include_router(oauth_router)

    # 2. MCP protocol mount — Streamable HTTP, Bearer auth requise
    # Le mount du SDK est wrappé dans une fonction pour isoler l'import :
    # si le SDK plante ou change d'API, on log un warning mais on garde au
    # moins les routes OAuth + ping fonctionnelles.
    try:
        from app.mcp.server import build_mcp_subapp

        mcp_proto_app = build_mcp_subapp()
        mcp_proto_app.add_middleware(MCPBearerAuthMiddleware)
        subapp.mount("/protocol", mcp_proto_app)
        logger.info("MCP protocol sub-app mounted at /mcp/protocol/")
    except Exception as exc:
        logger.exception("MCP protocol mount failed (OAuth + ping reste up): %s", exc)

    return subapp
