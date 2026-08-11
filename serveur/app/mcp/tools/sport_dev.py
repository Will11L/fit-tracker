"""Tools MCP Cas B1 — dev runtime read-only (design doc §4).

4 tools cœur pour l'auto-validation post-deploy (status service, logs, health,
version Alembic). Tous gated `require_admin()` + scope `ops:read` côté wrapper
(server.py). Pas de SQL libre (décision Q4), pas d'ops destructive (Cas B2 différé).

Le service `sportapi` tourne en `User=william` (groupe `adm`) → `journalctl` /
`systemctl show` sont lisibles sans sudo. Les units interrogeables sont
whitelistées et les logs passent par une redaction de secrets (défense en
profondeur — les secrets ne sont pas censés être loggés).

Tools data-ops (row_count, db_size, user_activity…) et audit log : différés.
"""

from __future__ import annotations

import asyncio
import logging
import re
from typing import Any, Optional

from datetime import date, timedelta

from sqlalchemy import select, text

from app.database import AsyncSessionLocal, Base
from app.mcp.context import get_current_mcp_user_id
from app.models.user import User

# Tables consultables = uniquement les tables mappées par SQLAlchemy (pas
# information_schema ni pg_* arbitraires). Whitelist auto-à-jour (décision Q4 §4.2).
_ALLOWED_TABLES = frozenset(Base.metadata.tables.keys())

logger = logging.getLogger(__name__)

# Units systemd interrogeables (pas d'injection de unit arbitraire).
_ALLOWED_UNITS = {"sportapi", "sportapi-webhook"}

# `since` accepté : entier + suffixe (ex. "5min", "1h", "30s", "2d").
_SINCE_RE = re.compile(r"^(\d{1,4})(s|min|h|d)$")
_SINCE_WORD = {"s": "sec", "min": "min", "h": "hour", "d": "day"}

# Redaction défensive : masque la valeur après une clé sensible dans les logs.
# Matche tout identifiant CONTENANT un mot sensible (ex. JWT_SECRET_KEY, API_KEY,
# ACCESS_TOKEN) suivi de = ou :, puis remplace la valeur.
_SECRET_RE = re.compile(
    r"(?i)([\w-]*(?:password|passwd|secret|token|jwt|api[_-]?key|authorization)[\w-]*)"
    r"(\s*[:=]\s*)(\S+)"
)


async def require_admin() -> None:
    """Vérifie que le principal MCP courant est admin (User.is_admin).

    Raise PermissionError sinon. Gating des tools ops (Cas B1) — appelé depuis
    les wrappers @mcp.tool() en plus de require_scope("ops:read").
    """
    user_id = get_current_mcp_user_id()
    async with AsyncSessionLocal() as db:
        is_admin = (
            await db.execute(select(User.is_admin).where(User.id == user_id))
        ).scalar()
    if not is_admin:
        raise PermissionError("Tool réservé aux admins (ops:read).")


async def _run(cmd: list[str]) -> tuple[int, str]:
    """Exécute une commande (sans shell) et retourne (returncode, stdout+stderr)."""
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    out, _ = await proc.communicate()
    return proc.returncode, out.decode(errors="replace")


def _redact(s: str) -> str:
    return _SECRET_RE.sub(r"\1\2[REDACTED]", s)


async def get_service_status(name: str = "sportapi") -> dict[str, Any]:
    """État d'un service systemd whitelisté (active state, restarts, uptime)."""
    if name not in _ALLOWED_UNITS:
        return {"ok": False, "message": f"Unit non autorisée : {name}"}

    rc, out = await _run([
        "systemctl", "show", f"{name}.service",
        "-p", "ActiveState,SubState,NRestarts,ExecMainStartTimestamp,MainPID",
    ])
    props = dict(line.split("=", 1) for line in out.splitlines() if "=" in line)
    return {
        "ok": rc == 0,
        "name": name,
        "active_state": props.get("ActiveState"),
        "sub_state": props.get("SubState"),
        "restarts": int(props.get("NRestarts") or 0),
        "started_at": props.get("ExecMainStartTimestamp") or None,
        "main_pid": props.get("MainPID") or None,
    }


async def get_recent_logs(
    unit: str = "sportapi",
    since: str = "5min",
    limit: int = 200,
) -> dict[str, Any]:
    """Logs récents d'un service whitelisté (journalctl), secrets redactés.

    Args:
        unit: "sportapi" ou "sportapi-webhook".
        since: fenêtre relative — entier + s|min|h|d (ex. "5min", "1h").
        limit: nb max de lignes (1-1000).
    """
    if unit not in _ALLOWED_UNITS:
        return {"ok": False, "message": f"Unit non autorisée : {unit}"}
    m = _SINCE_RE.match(since)
    if not m:
        raise ValueError("since doit matcher un entier + s|min|h|d (ex. '5min', '1h')")
    if limit < 1 or limit > 1000:
        raise ValueError("limit doit être entre 1 et 1000")

    since_arg = f"{m.group(1)} {_SINCE_WORD[m.group(2)]} ago"
    rc, out = await _run([
        "journalctl", "-u", f"{unit}.service",
        "--since", since_arg, "-n", str(limit), "--no-pager", "-o", "short-iso",
    ])
    lines = _redact(out).splitlines()
    return {"ok": rc == 0, "unit": unit, "since": since, "line_count": len(lines), "lines": lines}


async def healthcheck() -> dict[str, Any]:
    """Santé runtime : connexion DB (SELECT 1)."""
    db_ok = True
    try:
        async with AsyncSessionLocal() as db:
            await db.execute(text("SELECT 1"))
    except Exception as exc:  # pragma: no cover - chemin d'erreur infra
        logger.warning("healthcheck DB KO: %s", exc)
        db_ok = False
    return {"ok": db_ok, "db": "ok" if db_ok else "ko"}


async def get_alembic_status() -> dict[str, Any]:
    """Version de schéma Alembic courante (table alembic_version)."""
    async with AsyncSessionLocal() as db:
        try:
            versions = list(
                (await db.execute(text("SELECT version_num FROM alembic_version"))).scalars().all()
            )
        except Exception:
            # fittracker_test est créée via create_all (pas Alembic) → table absente.
            return {"ok": True, "current": None, "versions": [], "note": "alembic_version absente"}
    return {
        "ok": True,
        "current": versions[0] if len(versions) == 1 else None,
        "versions": versions,
    }


async def get_table_row_count(table: str) -> dict[str, Any]:
    """Compte de lignes d'une table (whitelist tables mappées uniquement)."""
    if table not in _ALLOWED_TABLES:
        return {"ok": False, "message": f"Table non autorisée : {table}"}
    async with AsyncSessionLocal() as db:
        count = (await db.execute(text(f"SELECT count(*) FROM {table}"))).scalar()
    return {"ok": True, "table": table, "count": int(count)}


async def db_schema_info(table: Optional[str] = None) -> dict[str, Any]:
    """Liste les tables mappées, ou les colonnes d'une table whitelistée."""
    if table is None:
        return {"ok": True, "tables": sorted(_ALLOWED_TABLES)}
    if table not in _ALLOWED_TABLES:
        return {"ok": False, "message": f"Table non autorisée : {table}"}
    async with AsyncSessionLocal() as db:
        rows = (await db.execute(
            text(
                "SELECT column_name, data_type, is_nullable "
                "FROM information_schema.columns "
                "WHERE table_schema = 'public' AND table_name = :t "
                "ORDER BY ordinal_position"
            ),
            {"t": table},
        )).all()
    return {
        "ok": True,
        "table": table,
        "columns": [
            {"name": r.column_name, "type": r.data_type, "nullable": r.is_nullable == "YES"}
            for r in rows
        ],
    }


async def get_db_size() -> dict[str, Any]:
    """Taille totale de la DB + top 10 des tables mappées (octets)."""
    async with AsyncSessionLocal() as db:
        total = (await db.execute(text("SELECT pg_database_size(current_database())"))).scalar()
        rows = (await db.execute(
            text(
                "SELECT table_name, pg_total_relation_size(quote_ident(table_name)) AS bytes "
                "FROM information_schema.tables WHERE table_schema = 'public'"
            )
        )).all()
    sizes = sorted(
        ((r.table_name, int(r.bytes)) for r in rows if r.table_name in _ALLOWED_TABLES),
        key=lambda x: x[1],
        reverse=True,
    )
    return {
        "ok": True,
        "total_bytes": int(total),
        "tables": [{"table": t, "bytes": b} for t, b in sizes[:10]],
    }


async def get_user_activity_summary(user_id: Optional[int] = None) -> dict[str, Any]:
    """Résumé d'activité sur 30j : séances + sets (un user, ou agrégé si None)."""
    cutoff = (date.today() - timedelta(days=30)).isoformat()
    clause = "AND aw.user_id = :uid" if user_id is not None else ""
    params: dict[str, Any] = {"cutoff": cutoff}
    if user_id is not None:
        params["uid"] = user_id

    async with AsyncSessionLocal() as db:
        row = (await db.execute(
            text(
                f"""
                SELECT count(DISTINCT aw.uuid) AS workouts,
                       count(s.id) AS sets,
                       max(aw.date) AS last_workout
                FROM actual_workouts aw
                LEFT JOIN actual_workout_exercises awe ON awe.actual_workout_uuid = aw.uuid
                LEFT JOIN actual_workout_sets s ON s.actual_workout_exercise_uuid = awe.uuid
                WHERE aw.date >= :cutoff {clause}
                """
            ),
            params,
        )).one()
    return {
        "ok": True,
        "scope": "user" if user_id is not None else "all",
        "user_id": user_id,
        "days": 30,
        "workouts": int(row.workouts),
        "sets": int(row.sets),
        "last_workout": row.last_workout,
    }
