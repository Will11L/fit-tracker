"""Boucle tool-use Anthropic pour l'agent in-app (Cas C).

Flow (entièrement côté serveur, pas de streaming au MVP — décision 2026-05-31) :
  messages.create(tools=[14 read+write]) -> si stop_reason="tool_use", exécute
  chaque tool via le tool_bridge (qui passe par l'instance MCP + audit log),
  renvoie les tool_result, reboucle ; sinon agrège le texte final.

L'appelant (agent_router) doit avoir posé le contexte MCP (set_mcp_context)
AVANT d'appeler `run_agent_chat` : les tools dérivent user_id + scopes de ce
contexte (cascade ownership politique 8, scope read+write seulement).
"""
from __future__ import annotations

import logging
from typing import Any

from anthropic import AsyncAnthropic

from app.agent import tool_bridge
from app.settings import settings

logger = logging.getLogger(__name__)

# Cadre l'assistant : domaine sport-app, langue calquée sur l'utilisateur,
# concision (réponse affichée dans une bulle de chat mobile).
SYSTEM_PROMPT = (
    "Tu es l'assistant sport intégré à l'application sport-app (tracking "
    "d'entraînement : séances, exercices, séries, muscles, objectifs hebdo). "
    "Réponds à l'utilisateur dans sa langue, de façon concise et utile. "
    "Utilise les outils fournis pour lire ou modifier SES données "
    "d'entraînement ; ne demande jamais son identité (elle est déjà connue). "
    "N'invente pas de données : si un outil ne renvoie rien, dis-le simplement."
)


def _build_client() -> AsyncAnthropic:
    return AsyncAnthropic(api_key=settings.ANTHROPIC_API_KEY)


async def run_agent_chat(messages: list[dict[str, str]]) -> dict[str, Any]:
    """Exécute la boucle tool-use et retourne {"reply", "tool_calls"}.

    Args:
        messages: historique [{role, content}] fourni par l'app (se termine par
            un tour `user`). Converti tel quel en messages Anthropic ; au fil de
            la boucle on y ajoute les blocs tool_use / tool_result.
    """
    client = _build_client()
    tools = await tool_bridge.anthropic_tool_specs()

    # Copie mutable : on y empile les tours assistant (tool_use) + user (tool_result).
    convo: list[dict[str, Any]] = [dict(m) for m in messages]
    tool_calls: list[str] = []

    for _ in range(settings.AGENT_MAX_TOOL_ITERATIONS):
        response = await client.messages.create(
            model=settings.AGENT_MODEL,
            max_tokens=settings.AGENT_MAX_TOKENS,
            system=SYSTEM_PROMPT,
            tools=tools,
            messages=convo,
        )

        if response.stop_reason != "tool_use":
            return {"reply": _collect_text(response.content), "tool_calls": tool_calls}

        # Le modèle demande un ou plusieurs tools. On rejoue son tour assistant
        # (content brut) puis on renvoie les résultats dans un tour user.
        convo.append({"role": "assistant", "content": response.content})
        tool_results: list[dict[str, Any]] = []
        for block in response.content:
            if getattr(block, "type", None) != "tool_use":
                continue
            tool_calls.append(block.name)
            result_json = await tool_bridge.execute_tool(block.name, block.input or {})
            tool_results.append({
                "type": "tool_result",
                "tool_use_id": block.id,
                "content": result_json,
            })
        convo.append({"role": "user", "content": tool_results})

    # Garde-fou : trop d'allers-retours sans converger. On force une dernière
    # réponse sans tools pour obtenir un texte exploitable.
    logger.warning("Agent tool-use loop hit max iterations (%s tools called)", len(tool_calls))
    final = await client.messages.create(
        model=settings.AGENT_MODEL,
        max_tokens=settings.AGENT_MAX_TOKENS,
        system=SYSTEM_PROMPT,
        messages=convo,
    )
    return {"reply": _collect_text(final.content), "tool_calls": tool_calls}


def _collect_text(content: list[Any]) -> str:
    """Concatène les blocs texte d'une réponse Anthropic."""
    parts = [b.text for b in content if getattr(b, "type", None) == "text"]
    return "\n".join(p for p in parts if p).strip()
