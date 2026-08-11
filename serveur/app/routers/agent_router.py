"""Cas C — Agent IA in-app : POST /api/v1/agent/chat (Phase 2 MCP).

L'app envoie l'historique de conversation + son JWT REST. Le serveur dérive
user_id du JWT (Depends(get_current_user_id)), pose le contexte MCP avec les
scopes read+write seulement, puis lance la boucle tool-use Anthropic qui
réutilise 100% les tools MCP existants. Réponse complète (pas de streaming au
MVP — décision 2026-05-31).

Sécurité :
- Cascade ownership (politique 8) : les tools lisent user_id du contextvar MCP,
  jamais du payload Claude.
- Scopes restreints : sport:read + sport:write -> destructive/ops lèvent
  PermissionError côté tool même si le modèle tentait de les appeler.
- Rate limit slowapi (settings.AGENT_RATE_LIMIT) : garde-fou anti cost-bomb.
- Clé Anthropic jamais dans l'APK : 503 si ANTHROPIC_API_KEY absente.
"""
from fastapi import APIRouter, Depends, HTTPException, Request, status

from app.agent.chat import run_agent_chat
from app.dependencies import get_current_user_id
from app.mcp.context import set_mcp_context
from app.rate_limit import limiter
from app.schemas.agent_schema import AgentChatRequest, AgentChatResponse, ToolCallSummary
from app.settings import settings

agent_router = APIRouter(prefix="/agent", tags=["agent"])

# Scopes accordés à l'agent in-app : lecture + écriture non destructive.
_AGENT_SCOPES = ["sport:read", "sport:write"]
_AGENT_CLIENT_ID = "in-app-agent"


@agent_router.post("/chat", response_model=AgentChatResponse)
@limiter.limit(settings.AGENT_RATE_LIMIT)
async def agent_chat(
    request: Request,
    payload: AgentChatRequest,
    user_id: int = Depends(get_current_user_id),
):
    if not settings.ANTHROPIC_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Agent unavailable: ANTHROPIC_API_KEY not configured",
        )

    # Pose le contexte MCP pour cette requête : les tools (et l'audit log) le
    # lisent via les contextvars. /agent/chat vit sous /api/v1/, donc la
    # middleware Bearer du sous-app /mcp/ ne tourne pas ici -> pose manuelle.
    set_mcp_context(user_id=user_id, client_id=_AGENT_CLIENT_ID, scopes=_AGENT_SCOPES)

    messages = [{"role": m.role, "content": m.content} for m in payload.messages]
    result = await run_agent_chat(messages)

    return AgentChatResponse(
        reply=result["reply"],
        tool_calls=[ToolCallSummary(tool_name=n) for n in result["tool_calls"]],
    )
