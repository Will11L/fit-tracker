"""Instance MCP server + registration des tools.

Phase 1 scaffold : 3 tools read POC (cf. design doc §3.1 pour la liste complète
à atteindre Phase 1 finale).

Le SDK `mcp` Python officiel expose `FastMCP` qui wrappe la registration des
tools + le transport Streamable HTTP. On le mount comme sous-app dans
app/main.py via :func:`build_mcp_subapp`.
"""

from __future__ import annotations

import logging

from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings
from mcp.types import ToolAnnotations

from app.mcp.context import get_current_mcp_user_id, require_scope
from app.mcp.tools import sport_data, sport_destructive, sport_dev, sport_write

logger = logging.getLogger(__name__)

mcp = FastMCP(
    name="sport-app",
    instructions=(
        "Assistant données sport-app — tracking d'entraînement (workouts, "
        "exercices, sets, muscles, programmation hebdo). Use the read tools "
        "to query the user's training data. user_id is derived from the "
        "Bearer token automatically — never ask the user for it."
    ),
    # DNS rebinding protection désactivée : déploiement tailnet-only (accès via
    # Tailscale auth uniquement, pas de surface browser/LAN attaquable). Bearer
    # JWT reste obligatoire sur tous les calls protocol via MCPBearerAuthMiddleware.
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)


@mcp.tool()
async def get_next_workout() -> dict:
    """Prochaine séance planifiée de l'utilisateur (rolling 7 jours).

    Retourne le PlannedWorkout dont `day_of_week` est le plus proche
    aujourd'hui. `is_today=true` si c'est aujourd'hui.
    """
    require_scope("sport:read")
    return await sport_data.get_next_planned_workout(get_current_mcp_user_id())


@mcp.tool()
async def list_recent_workouts(days_back: int = 30, limit: int = 20) -> dict:
    """Liste les séances effectivement réalisées sur les N derniers jours.

    Args:
        days_back: Nombre de jours à remonter (1-365, défaut 30).
        limit: Nombre max de séances à retourner (1-100, défaut 20).
    """
    require_scope("sport:read")
    return await sport_data.list_recent_workouts(
        get_current_mcp_user_id(), days_back=days_back, limit=limit
    )


@mcp.tool()
async def search_exercises(query: str, limit: int = 20) -> dict:
    """Recherche dans le catalogue d'exercices de l'utilisateur.

    Args:
        query: Texte à chercher (min 2 chars, ilike substring).
        limit: Max résultats (1-50, défaut 20).
    """
    require_scope("sport:read")
    return await sport_data.search_exercises(
        get_current_mcp_user_id(), query=query, limit=limit
    )


@mcp.tool()
async def get_workout(workout_uuid: str) -> dict:
    """Détail d'une séance réalisée : exercices ordonnés + sets de chacun.

    Args:
        workout_uuid: UUID de la séance (cf. list_recent_workouts).
    """
    require_scope("sport:read")
    return await sport_data.get_workout(get_current_mcp_user_id(), workout_uuid=workout_uuid)


@mcp.tool()
async def get_weekly_volume(muscle_name: str, weeks: int = 4) -> dict:
    """Volume hebdomadaire pour un muscle sur les N dernières semaines.

    Volume pondéré par coefficient muscle, sets DONE uniquement (même calcul
    que l'écran Stats). Buckets ISO week.

    Args:
        muscle_name: Nom du muscle (match insensible à la casse, ex. "Mid Chest").
        weeks: Nombre de semaines à remonter (1-52, défaut 4).
    """
    require_scope("sport:read")
    return await sport_data.get_weekly_volume(
        get_current_mcp_user_id(), muscle_name=muscle_name, weeks=weeks
    )


@mcp.tool()
async def get_exercise_history(exercise_name: str, limit: int = 20) -> dict:
    """Dernières séances où un exercice a été réalisé, sets inclus.

    Args:
        exercise_name: Nom de l'exercice (match insensible à la casse).
        limit: Nombre max de séances à retourner (1-100, défaut 20).
    """
    require_scope("sport:read")
    return await sport_data.get_exercise_history(
        get_current_mcp_user_id(), exercise_name=exercise_name, limit=limit
    )


@mcp.tool()
async def get_muscle_goals_progress(week_offset: int = 0) -> dict:
    """% d'atteinte des objectifs hebdo par muscle.

    Args:
        week_offset: 0 = semaine courante, -1 = semaine passée, etc.
    """
    require_scope("sport:read")
    return await sport_data.get_muscle_goals_progress(
        get_current_mcp_user_id(), week_offset=week_offset
    )


@mcp.tool()
async def get_available_equipment() -> dict:
    """Équipement disponible déclaré par l'utilisateur."""
    require_scope("sport:read")
    return await sport_data.get_available_equipment(get_current_mcp_user_id())


@mcp.tool()
async def list_muscles() -> dict:
    """Catalogue des muscles de l'utilisateur (niveau précis + groupe + zone)."""
    require_scope("sport:read")
    return await sport_data.list_muscles(get_current_mcp_user_id())


# ---- Write tools (scope sport:write, non destructifs) ----


@mcp.tool()
async def mark_set_done(set_uuid: str, reps: int, weight: float) -> dict:
    """Termine un set : enregistre les reps + charge réalisés et passe status=DONE.

    Args:
        set_uuid: UUID du set (cf. get_workout).
        reps: Répétitions réalisées.
        weight: Charge réalisée (kg).
    """
    require_scope("sport:write")
    return await sport_write.mark_set_done(
        get_current_mcp_user_id(), set_uuid=set_uuid, reps=reps, weight=weight
    )


@mcp.tool()
async def create_actual_workout(name: str, date: str | None = None,
                                location: str | None = None, notes: str | None = None) -> dict:
    """Crée une séance réalisée (vide). Ajoute ensuite des exercices via add_exercise_to_workout.

    Args:
        name: Nom de la séance.
        date: Date ISO YYYY-MM-DD (défaut aujourd'hui).
        location: Lieu (optionnel).
        notes: Notes (optionnel).
    """
    require_scope("sport:write")
    return await sport_write.create_actual_workout(
        get_current_mcp_user_id(), name=name, workout_date=date, location=location, notes=notes
    )


@mcp.tool()
async def add_exercise_to_workout(workout_uuid: str, exercise_uuid: str,
                                  sets: int = 3, reps: str = "8-12") -> dict:
    """Ajoute un exercice à une séance (à la fin de la liste).

    Args:
        workout_uuid: UUID de la séance.
        exercise_uuid: UUID de l'exercice (cf. search_exercises).
        sets: Nombre de séries cible (défaut 3).
        reps: Répétitions cible, format texte ex. "8-12" (défaut "8-12").
    """
    require_scope("sport:write")
    return await sport_write.add_exercise_to_workout(
        get_current_mcp_user_id(), workout_uuid=workout_uuid,
        exercise_uuid=exercise_uuid, sets=sets, reps=reps,
    )


@mcp.tool()
async def update_muscle_goal(muscle_uuid: str, target: int, priority: str | None = None) -> dict:
    """Crée ou met à jour l'objectif hebdo (semaine courante) d'un muscle.

    Args:
        muscle_uuid: UUID du muscle (cf. list_muscles).
        target: Objectif de séries pour la semaine.
        priority: HIGH | MEDIUM | LOW | NONE (défaut MEDIUM à la création).
    """
    require_scope("sport:write")
    return await sport_write.update_muscle_goal(
        get_current_mcp_user_id(), muscle_uuid=muscle_uuid, target=target, priority=priority
    )


@mcp.tool()
async def tick_routine_task(task_uuid: str, date: str | None = None,
                            is_checked: bool = True) -> dict:
    """Coche (ou décoche) une tâche routine pour une date.

    Args:
        task_uuid: UUID de la tâche.
        date: Date ISO YYYY-MM-DD de l'occurrence (défaut aujourd'hui).
        is_checked: True = cocher (défaut), False = décocher.
    """
    require_scope("sport:write")
    return await sport_write.tick_routine_task(
        get_current_mcp_user_id(), task_uuid=task_uuid, occurrence_date=date, is_checked=is_checked
    )


# ---- Destructive tools (scope sport:destructive, destructiveHint=true) ----


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True))
async def delete_actual_workout(workout_uuid: str) -> dict:
    """Supprime une séance réalisée (exercices + sets en cascade). Irréversible.

    Args:
        workout_uuid: UUID de la séance à supprimer.
    """
    require_scope("sport:destructive")
    return await sport_destructive.delete_actual_workout(
        get_current_mcp_user_id(), workout_uuid=workout_uuid
    )


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True))
async def delete_exercise(exercise_uuid: str) -> dict:
    """Supprime un exercice du catalogue. ⚠️ Cascade : efface aussi tout
    l'historique de cet exercice dans les séances réalisées. Irréversible.

    Args:
        exercise_uuid: UUID de l'exercice à supprimer.
    """
    require_scope("sport:destructive")
    return await sport_destructive.delete_exercise(
        get_current_mcp_user_id(), exercise_uuid=exercise_uuid
    )


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True))
async def delete_muscle_goal(muscle_uuid: str) -> dict:
    """Supprime l'objectif hebdo de la semaine courante pour un muscle.

    Args:
        muscle_uuid: UUID du muscle (cf. list_muscles).
    """
    require_scope("sport:destructive")
    return await sport_destructive.delete_muscle_goal(
        get_current_mcp_user_id(), muscle_uuid=muscle_uuid
    )


@mcp.tool(annotations=ToolAnnotations(destructiveHint=True))
async def bulk_delete_notifications(scope: str = "read") -> dict:
    """Supprime en masse les notifications. Irréversible.

    Args:
        scope: "read" (lues, défaut) | "archived" (archivées) | "all" (toutes).
    """
    require_scope("sport:destructive")
    return await sport_destructive.bulk_delete_notifications(
        get_current_mcp_user_id(), scope=scope
    )


# ---- Cas B1 — dev runtime read-only (scope ops:read + require_admin) ----


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def get_service_status(name: str = "sportapi") -> dict:
    """État d'un service systemd ("sportapi" ou "sportapi-webhook").

    Args:
        name: Nom du service (défaut "sportapi").
    """
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.get_service_status(name=name)


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def get_recent_logs(unit: str = "sportapi", since: str = "5min", limit: int = 200) -> dict:
    """Logs récents d'un service (journalctl), secrets redactés.

    Args:
        unit: "sportapi" ou "sportapi-webhook".
        since: fenêtre relative, entier + s|min|h|d (ex. "5min", "1h").
        limit: nb max de lignes (1-1000, défaut 200).
    """
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.get_recent_logs(unit=unit, since=since, limit=limit)


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def healthcheck() -> dict:
    """Santé runtime du serveur (connexion DB)."""
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.healthcheck()


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def get_alembic_status() -> dict:
    """Version de schéma Alembic courante."""
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.get_alembic_status()


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def get_table_row_count(table: str) -> dict:
    """Compte de lignes d'une table mappée (whitelist).

    Args:
        table: Nom de la table (ex. "actual_workouts", "users").
    """
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.get_table_row_count(table=table)


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def db_schema_info(table: str | None = None) -> dict:
    """Liste les tables mappées, ou les colonnes d'une table donnée.

    Args:
        table: Nom de table pour ses colonnes ; omis = liste des tables.
    """
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.db_schema_info(table=table)


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def get_db_size() -> dict:
    """Taille totale de la base + top 10 des tables (octets)."""
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.get_db_size()


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def get_user_activity_summary(user_id: int | None = None) -> dict:
    """Résumé d'activité sur 30 jours (séances + sets), un user ou agrégé.

    Args:
        user_id: ID utilisateur, ou omis pour l'agrégat global.
    """
    require_scope("ops:read")
    await sport_dev.require_admin()
    return await sport_dev.get_user_activity_summary(user_id=user_id)


def build_mcp_subapp():
    """Retourne le sous-app Starlette/FastAPI à monter dans /mcp/.

    Encapsule l'appel `streamable_http_app()` du SDK pour rester resilient
    aux changements d'API mineurs entre versions du SDK MCP.
    """
    # Audit logging (design §7) : hook central sur tool_manager.call_tool.
    from app.mcp.audit import install_audit_hook
    install_audit_hook(mcp)
    # Le SDK MCP v1.2+ expose streamable_http_app() qui retourne un app
    # Starlette routé sur "/" — c'est ce qu'on mount sous /mcp/.
    return mcp.streamable_http_app()
