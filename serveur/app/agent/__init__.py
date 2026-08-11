"""Cas C — Agent IA in-app (Phase 2 MCP).

Réutilise 100% l'instance FastMCP existante (`app.mcp.server.mcp`) : les
définitions de tools (`tool_bridge`) et la boucle tool-use Anthropic (`chat`)
s'appuient sur les mêmes tools read+write que le Cas A. Aucune logique métier
dupliquée — voir `docs/MCP_DESIGN.md` §5.
"""
