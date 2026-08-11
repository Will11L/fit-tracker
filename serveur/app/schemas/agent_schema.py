"""Schémas Pydantic du Cas C — Agent IA in-app (Phase 2 MCP).

Wire snake_case + alias camelCase + populate_by_name (politique 17), aligné
sur les autres schémas du projet.

L'app envoie l'historique complet de la conversation (persistance locale MVP,
0 message stocké côté serveur — décision 2026-05-31) ; le serveur renvoie la
réponse finale de l'assistant + un récap des tools appelés (UX/debug).
"""
from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """Un tour de conversation. `role` = "user" ou "assistant" (UPPER_CASE non
    requis ici : ce sont les rôles du protocole Anthropic, pas un état métier —
    politique 11 ne s'applique pas)."""

    role: Literal["user", "assistant"]
    content: str

    model_config = {"populate_by_name": True}


class AgentChatRequest(BaseModel):
    """Body de POST /api/v1/agent/chat.

    `messages` = historique complet (le serveur ne stocke rien). Doit se terminer
    par un message `user` (le tour courant à traiter)."""

    messages: list[ChatMessage] = Field(..., min_length=1)

    model_config = {"populate_by_name": True}


class ToolCallSummary(BaseModel):
    """Récap d'un tool MCP appelé pendant la boucle tool-use (transparence UX)."""

    tool_name: str = Field(..., alias="toolName")

    model_config = {"populate_by_name": True}


class AgentChatResponse(BaseModel):
    """Réponse de l'agent : texte assistant final + liste des tools appelés."""

    reply: str
    tool_calls: list[ToolCallSummary] = Field(default_factory=list, alias="toolCalls")

    model_config = {"populate_by_name": True}
