"""Audit logging des tool calls MCP — design §7, table `mcp_audit_log`.

Hook central installé sur `ToolManager.call_tool` (FastMCP y délègue
dynamiquement) : chaque appel de tool écrit une ligne (user_id, client_id,
tool_name, args, result_summary tronqué, status, error_message).

Best-effort : un échec d'écriture d'audit ne casse JAMAIS le tool call (le
résultat/erreur du tool est propagé tel quel, l'audit est dans un `finally`
protégé).

Rétention : `purge_old_audit_logs(days=30)` (décision Q7), appelée au démarrage
(lifespan main.py) — le redéploiement fréquent de la Pi suffit à borner la table.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Any

from sqlalchemy import delete

from app.database import AsyncSessionLocal
from app.mcp.context import get_current_mcp_client_id, get_current_mcp_user_id
from app.mcp.models import MCPAuditLog

logger = logging.getLogger(__name__)

_SUMMARY_MAX = 500


async def write_audit(
    tool_name: str,
    arguments: Any,
    status: str,
    error_message: str | None,
    result: Any,
) -> None:
    """Écrit une ligne d'audit. Silencieux si pas de contexte utilisateur."""
    try:
        user_id = get_current_mcp_user_id()
    except Exception:
        return  # pas de principal MCP (ne devrait pas arriver sur un tool call)

    summary = str(result)[:_SUMMARY_MAX] if result is not None else None
    async with AsyncSessionLocal() as db:
        db.add(MCPAuditLog(
            user_id=user_id,
            client_id=get_current_mcp_client_id(),
            tool_name=tool_name,
            args=arguments if isinstance(arguments, dict) else None,
            result_summary=summary,
            status=status,
            error_message=(error_message[:_SUMMARY_MAX] if error_message else None),
            created_at=datetime.now(timezone.utc),
        ))
        await db.commit()


def install_audit_hook(mcp) -> None:
    """Wrappe `mcp._tool_manager.call_tool` pour auditer chaque appel. Idempotent."""
    tm = mcp._tool_manager
    if getattr(tm, "_audit_installed", False):
        return
    original_call = tm.call_tool

    async def audited_call_tool(name, arguments, *args, **kwargs):
        status, err, result = "ok", None, None
        try:
            result = await original_call(name, arguments, *args, **kwargs)
            return result
        except Exception as exc:
            status, err = "error", str(exc)
            raise
        finally:
            try:
                await write_audit(name, arguments, status, err, result)
            except Exception as audit_exc:  # best-effort, ne casse pas le tool
                logger.warning("MCP audit write failed for %s: %s", name, audit_exc)

    tm.call_tool = audited_call_tool
    tm._audit_installed = True
    logger.info("MCP audit hook installed on tool_manager.call_tool")


async def purge_old_audit_logs(days: int = 30) -> int:
    """Supprime les lignes d'audit plus vieilles que `days`. Retourne le nb supprimé."""
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    async with AsyncSessionLocal() as db:
        result = await db.execute(
            delete(MCPAuditLog).where(MCPAuditLog.created_at < cutoff)
        )
        await db.commit()
        return result.rowcount
