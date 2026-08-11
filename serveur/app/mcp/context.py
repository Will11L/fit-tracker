"""Context var pour propager user_id authentifié aux tools MCP.

Pattern : la middleware Bearer (cf. middleware.py) décode le JWT MCP et
appelle `set_mcp_user_id(uid)` avant que le handler tools/call soit invoqué.
Les tools lisent via `get_current_mcp_user_id()`.

Pas de couplage avec l'API REST FastAPI : la chaîne est totalement isolée
côté `/mcp/` (le sous-app a sa propre middleware).
"""

from __future__ import annotations

import contextvars
from typing import Optional

_user_id_ctx: contextvars.ContextVar[Optional[int]] = contextvars.ContextVar(
    "mcp_user_id", default=None
)
_client_id_ctx: contextvars.ContextVar[Optional[str]] = contextvars.ContextVar(
    "mcp_client_id", default=None
)
_scopes_ctx: contextvars.ContextVar[Optional[list[str]]] = contextvars.ContextVar(
    "mcp_scopes", default=None
)


def set_mcp_context(user_id: int, client_id: str, scopes: list[str]) -> None:
    _user_id_ctx.set(user_id)
    _client_id_ctx.set(client_id)
    _scopes_ctx.set(scopes)


def get_current_mcp_user_id() -> int:
    uid = _user_id_ctx.get()
    if uid is None:
        raise RuntimeError("MCP user_id not set — middleware Bearer non exécutée ?")
    return uid


def get_current_mcp_client_id() -> Optional[str]:
    return _client_id_ctx.get()


def has_scope(scope: str) -> bool:
    scopes = _scopes_ctx.get() or []
    return scope in scopes


def require_scope(scope: str) -> None:
    if not has_scope(scope):
        raise PermissionError(f"Scope manquant : {scope}")
