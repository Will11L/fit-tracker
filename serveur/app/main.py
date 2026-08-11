import json
import logging
import os

# T2.4 (2026-05-06) : config logging projet uniforme. F5b avait sweepé
# `print → logger.getLogger(__name__)` partout (pg_listener, ws_hub, ws_router,
# rate_limit) mais pas de config root explicite — les logs partaient dans le
# default uvicorn (formatter coloré, niveau WARNING par défaut sur certains
# loggers root). force=True écrase la config uvicorn quand l'app est lancée
# via `uvicorn app.main:app` (uvicorn configure son logging au CLI startup
# AVANT l'import de app.main, donc basicConfig est no-op sans force).
#
# Format projet : timestamp ISO + niveau + nom du logger + message.
# Niveau INFO par défaut (override en dev via env LOG_LEVEL=DEBUG).
#
# T4.3 (2026-05-07) : toggle JSON via env LOG_FORMAT=json (default "text").
# Stdlib json.dumps suffit, pas de dépendance pip ajoutée. Utile si on
# branche un agrégateur (Loki/ELK) — sinon `journalctl` reste lisible
# en text. Champs JSON : ts, level, logger, msg (+ exc si exception).
class _JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        d = {
            "ts": self.formatTime(record, self.datefmt),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
        }
        if record.exc_info:
            d["exc"] = self.formatException(record.exc_info)
        return json.dumps(d, default=str, ensure_ascii=False)


_LOG_LEVEL = getattr(logging, os.environ.get("LOG_LEVEL", "INFO").upper(), logging.INFO)
if os.environ.get("LOG_FORMAT", "text").lower() == "json":
    _handler = logging.StreamHandler()
    _handler.setFormatter(_JsonFormatter(datefmt="%Y-%m-%dT%H:%M:%S"))
    logging.basicConfig(level=_LOG_LEVEL, handlers=[_handler], force=True)
else:
    logging.basicConfig(
        level=_LOG_LEVEL,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        force=True,
    )

from datetime import datetime, timezone

from fastapi import FastAPI, Request
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import HTMLResponse
from contextlib import asynccontextmanager
from pathlib import Path
from sqlalchemy import text
import asyncio, contextlib

from app.database import AsyncSessionLocal

# F6-5 (2026-05-06) : pages HTML extraites dans app/static/ (avant : ~100 lignes
# inline dans main.py). Pas de StaticFiles mount — on lit le fichier à la demande
# via HTMLResponse pour rester sur des pages déjà publiques sans config.
_STATIC_DIR = Path(__file__).parent / "static"

from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

# Settings / DB / Auth / WS
from app.pg_listener import pg_listen_task

# Middleware pour extraire le client_id des requêtes HTTP
from app.middlewares import ClientIdMiddleware

# V8.2-4 : rate limiter partage par les endpoints sensibles (auth_router).
from app.rate_limit import limiter, _rate_limit_handler

# ---- Routers ----
from app.routers import (
    actual_workout_exercise_router,
    actual_workout_router,
    actual_workout_set_router,
    available_equipment_router,
    cycle_workout_router,
    equipment_router,
    exercise_equipment_router,
    exercise_muscle_router,
    exercise_router,
    food_portion_router,
    food_router,
    health_goal_router,
    health_metric_router,
    health_step_count_router,
    meal_entry_router,
    meal_preset_router,
    meal_router,
    muscle_goal_router,
    muscle_router,
    notification_router,
    nutrition_goal_router,
    nutrition_off_router,
    planned_workout_exercise_router,
    planned_workout_router,
    quote_router,
    recipe_ingredient_router,
    recipe_router,
    routine_period_router,
    superset_exercise_router,
    superset_group_router,
    task_router,
    task_check_router,
    training_cycle_router,
    user_router,
    water_intake_router,
    auth_router,
    ws_router,
    agent_router,
)

@asynccontextmanager
async def lifespan(app: FastAPI):
    task = asyncio.create_task(pg_listen_task())
    try:
        # MCP SDK session_manager — mounted sub-app lifespans ne sont pas
        # propagés par FastAPI, on run le manager au niveau root.
        try:
            from app.mcp.server import mcp as _mcp_instance
            async with _mcp_instance.session_manager.run():
                # Audit log retention (design Q7) : purge best-effort au démarrage.
                # Le redéploiement fréquent de la Pi suffit à borner la table.
                with contextlib.suppress(Exception):
                    from app.mcp.audit import purge_old_audit_logs
                    await purge_old_audit_logs(30)
                yield
        except ImportError:
            yield
    finally:
        task.cancel()
        with contextlib.suppress(Exception):
            await task

app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None)

# CORS retire 2026-05-05 (V7.2) : l'app est Android via Retrofit (HTTP direct,
# pas de navigateur), donc CORS n'est jamais evalue. Si on ajoute un client web
# plus tard, re-introduire CORSMiddleware avec ALLOWED_ORIGINS pertinent.

# V8.2-4 : rate limiter slowapi attache a l'app + handler 429.
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_handler)
app.add_middleware(SlowAPIMiddleware)

app.add_middleware(ClientIdMiddleware)

# Compression gzip pour les responses > 1 KB. OkHttp Android décompresse auto.
# Gain typique ~70% sur GET /exercises (instructions JSONB) et autres listes.
app.add_middleware(GZipMiddleware, minimum_size=1000)


# ---- Montage des routers ----
ROUTERS = [
    actual_workout_exercise_router,
    actual_workout_router,
    actual_workout_set_router,
    available_equipment_router,
    cycle_workout_router,
    equipment_router,
    exercise_equipment_router,
    exercise_muscle_router,
    exercise_router,
    food_portion_router,
    food_router,
    health_goal_router,
    health_metric_router,
    health_step_count_router,
    meal_entry_router,
    meal_preset_router,
    meal_router,
    muscle_goal_router,
    muscle_router,
    notification_router,
    nutrition_goal_router,
    nutrition_off_router,
    planned_workout_exercise_router,
    planned_workout_router,
    quote_router,
    recipe_ingredient_router,
    recipe_router,
    routine_period_router,
    superset_exercise_router,
    superset_group_router,
    task_router,
    task_check_router,
    training_cycle_router,
    user_router,
    water_intake_router,
    auth_router,
    ws_router,
    agent_router,
]
for r in ROUTERS:
    app.include_router(r, prefix="/api/v1")

# ---- MCP sub-app (scaffold 2026-05-27, cf. docs/MCP_DESIGN.md) ----
# Mount sous /mcp/. Encapsulé dans un try/except large : si le SDK MCP plante
# à l'import ou si build_subapp() lève, l'API REST reste up — on perd juste
# l'endpoint MCP. Cf. design doc §9 (déploiement Pi-direct + politique safety).
try:
    from app.mcp.subapp import build_subapp as _build_mcp_subapp
    app.mount("/mcp", _build_mcp_subapp())
    logging.getLogger(__name__).info("MCP sub-app mounted at /mcp/")
except Exception as _mcp_exc:  # pragma: no cover  defensive
    logging.getLogger(__name__).exception(
        "MCP sub-app failed to mount (API REST reste up): %s", _mcp_exc
    )

# ---- Secure docs ----

@app.get("/secure-docs", include_in_schema=False)
def get_secure_docs():
    return HTMLResponse((_STATIC_DIR / "secure_docs.html").read_text(encoding="utf-8"))


@app.get("/token-helper", response_class=HTMLResponse, include_in_schema=False)
def token_helper():
    return (_STATIC_DIR / "token_helper.html").read_text(encoding="utf-8")


# ---- Healthcheck ----
# Public, sans auth, sans rate limit. Pour monitoring externe (uptime checker,
# futur CI/CD). `db` checke un SELECT 1 trivial.
@app.get("/healthz")
async def healthz():
    db_status = "ok"
    try:
        async with AsyncSessionLocal() as s:
            await s.execute(text("SELECT 1"))
    except Exception:
        db_status = "ko"
    return {
        "status": "ok",
        "db": db_status,
        "ts": datetime.now(timezone.utc).isoformat(),
    }


# ---- OAuth discovery au domain-root (clients MCP standard : Claude Code/Desktop) ----
# Les métadonnées OAuth "vraies" vivent sous /mcp/.well-known/ (cf. routes_oauth),
# mais les clients MCP sondent la RACINE du domaine (RFC 9728 / RFC 8414). On les
# expose donc aussi ici, pointant vers les endpoints réels /mcp/oauth/*. Les
# variantes path-aware (.../oauth-protected-resource/<resource-path>) sont gérées
# par le paramètre `rest`. Issuer dérivé de la requête (Tailscale préserve le Host).
def _oauth_root(request: Request) -> str:
    return f"{request.url.scheme}://{request.url.netloc}"


def _scopes_supported() -> list[str]:
    from app.mcp import auth as _mcp_auth
    return list(_mcp_auth.SCOPES.keys())


@app.get("/.well-known/oauth-protected-resource", include_in_schema=False)
@app.get("/.well-known/oauth-protected-resource/{rest:path}", include_in_schema=False)
async def root_oauth_protected_resource(request: Request, rest: str = ""):
    root = _oauth_root(request)
    return {
        "resource": f"{root}/mcp",
        "authorization_servers": [root],
        "scopes_supported": _scopes_supported(),
        "bearer_methods_supported": ["header"],
    }


@app.get("/.well-known/oauth-authorization-server", include_in_schema=False)
@app.get("/.well-known/oauth-authorization-server/{rest:path}", include_in_schema=False)
async def root_oauth_authorization_server(request: Request, rest: str = ""):
    root = _oauth_root(request)
    return {
        "issuer": root,
        "authorization_endpoint": f"{root}/mcp/oauth/authorize",
        "token_endpoint": f"{root}/mcp/oauth/token",
        "registration_endpoint": f"{root}/mcp/oauth/register",
        "scopes_supported": _scopes_supported(),
        "response_types_supported": ["code"],
        "grant_types_supported": ["authorization_code", "refresh_token"],
        "code_challenge_methods_supported": ["S256"],
        "token_endpoint_auth_methods_supported": ["client_secret_post", "none"],
    }


# --- Service du client web Angular (SPA), same-origin que l'API ---
# DOIT rester le DERNIER mount du fichier (catch-all). Les routes API (/api/v1),
# MCP (/mcp), et utilitaires (/healthz, /secure-docs, /token-helper, /.well-known)
# déclarées au-dessus ont la priorité ; ce mount n'attrape que le reste -> le SPA.
from starlette.exceptions import HTTPException as _StarletteHTTPException
from starlette.staticfiles import StaticFiles

_WEB_DIST = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "..", "appli-web", "dist", "appli-web", "browser")
)

# Bundles au nom hashé par le build Angular (ex. main-GLXFERI4.js, styles-AB12CD34.css) : le contenu
# est immuable pour un nom donné -> cache agressif sûr (un nouveau build = un nouveau nom de fichier).
import re as _re  # noqa: E402

_HASHED_ASSET = _re.compile(r"-[A-Za-z0-9]{8,}\.(?:js|css)$")


class _SpaStaticFiles(StaticFiles):
    """StaticFiles avec fallback SPA : sert le fichier demandé, sinon index.html
    (pour que le routing client Angular, ex. /muscles, ne renvoie pas 404).

    Politique de cache (corrige la page blanche après un redeploy) :
    - index.html (page d'entrée + fallback SPA) -> `no-cache` : le navigateur revalide toujours,
      donc il récupère toujours la carte de bundles à jour (sinon un index.html caché pointe vers
      d'anciens *.js supprimés par le build -> 404 -> app qui ne monte pas) ;
    - bundles hashés -> `immutable` 1 an (chargement rapide, sûr car le nom change à chaque build)."""

    async def get_response(self, path: str, scope):
        try:
            resp = await super().get_response(path, scope)
        except _StarletteHTTPException as exc:
            if exc.status_code == 404:
                resp = await super().get_response("index.html", scope)
            else:
                raise
        media = resp.headers.get("content-type", "")
        if media.startswith("text/html"):
            resp.headers["Cache-Control"] = "no-cache"
        elif _HASHED_ASSET.search(path):
            resp.headers["Cache-Control"] = "public, max-age=31536000, immutable"
        return resp


# Monté seulement si le build existe (évite de crasher le boot avant le 1er build).
if os.path.isdir(_WEB_DIST):
    app.mount("/", _SpaStaticFiles(directory=_WEB_DIST, html=True), name="spa")
