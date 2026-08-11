"""Pont entre les tools MCP existants et le format outils de l'API Anthropic.

L'agent in-app (Cas C) n'expose QUE les tools read + write (scopes sport:read +
sport:write = 14 tools), jamais destructive ni ops (décision 2026-05-31). On
filtre par liste explicite de noms — robuste et lisible, cohérent avec le
périmètre figé (pas de filtrage par annotation : les write tools n'en portent
pas, et un nouveau tool destructif ne fuiterait pas par défaut).

- `anthropic_tool_specs()` : convertit `mcp.list_tools()` (filtré) vers le
  format `{"name", "description", "input_schema"}` attendu par messages.create.
- `execute_tool(name, args)` : exécute via `mcp._tool_manager.call_tool`, qui
  retourne directement le dict du tool (convert_result=False par défaut) ET
  déclenche le hook d'audit MCP (`mcp_audit_log`) — bonus gratuit. La cascade
  ownership est garantie côté tool (user_id lu du contextvar MCP, jamais du
  payload Claude — politique 8).
"""
from __future__ import annotations

import json
import logging
from typing import Any

from app.mcp.server import mcp

logger = logging.getLogger(__name__)

# Périmètre agent in-app : 9 read + 5 write (cf. server.py). Liste explicite =
# allow-list ; tout tool hors de cette liste (destructive, ops) est invisible
# pour l'agent.
AGENT_READ_TOOLS = frozenset({
    "get_next_workout",
    "list_recent_workouts",
    "search_exercises",
    "get_workout",
    "get_weekly_volume",
    "get_exercise_history",
    "get_muscle_goals_progress",
    "get_available_equipment",
    "list_muscles",
})
AGENT_WRITE_TOOLS = frozenset({
    "mark_set_done",
    "create_actual_workout",
    "add_exercise_to_workout",
    "update_muscle_goal",
    "tick_routine_task",
})
AGENT_TOOL_NAMES = AGENT_READ_TOOLS | AGENT_WRITE_TOOLS


async def anthropic_tool_specs() -> list[dict[str, Any]]:
    """Retourne les définitions des 14 tools read+write au format Anthropic.

    Les `inputSchema` MCP sont déjà des JSON Schema (générés par FastMCP depuis
    les signatures), directement réutilisables comme `input_schema` Anthropic.
    """
    tools = await mcp.list_tools()
    specs: list[dict[str, Any]] = []
    for tool in tools:
        if tool.name not in AGENT_TOOL_NAMES:
            continue
        specs.append({
            "name": tool.name,
            "description": tool.description or "",
            "input_schema": tool.inputSchema,
        })
    return specs


async def execute_tool(name: str, arguments: dict[str, Any]) -> str:
    """Exécute un tool MCP et retourne son résultat sérialisé JSON (pour le
    tool_result Anthropic).

    Défense en profondeur : refuse tout nom hors allow-list même si le modèle
    l'invente (ne devrait pas, vu qu'on ne lui donne que les 14 specs).
    Le contexte MCP (user_id + scopes) doit être posé en amont par l'appelant.
    """
    if name not in AGENT_TOOL_NAMES:
        return json.dumps({"ok": False, "message": f"Tool non autorisé: {name}"})
    try:
        result = await mcp._tool_manager.call_tool(name, arguments)
    except Exception as exc:  # surface l'erreur au modèle sans casser la boucle
        logger.warning("Agent tool %s failed: %s", name, exc)
        return json.dumps({"ok": False, "message": str(exc)})
    return json.dumps(result, default=str, ensure_ascii=False)
