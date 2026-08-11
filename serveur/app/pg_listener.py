# app/pg_listener.py
import asyncio
import json
import logging

import asyncpg
from sqlalchemy.engine.url import make_url

from .settings import settings
from .ws_hub import ws_hub

logger = logging.getLogger(__name__)


def _asyncpg_dsn() -> str:
    """Construit un DSN asyncpg-compatible depuis settings.DATABASE_URL.

    SQLAlchemy utilise `postgresql+asyncpg://...`, asyncpg attend
    `postgresql://...`. On utilise `make_url` pour decomposer/recomposer
    proprement au lieu de `replace("+asyncpg", "")` (fragile si le DSN
    contient '+' ailleurs, ex. mot de passe URL-encode).

    IMPORTANT : `str(url)` masque le password par `***` (rendering
    safe par defaut). On force `render_as_string(hide_password=False)`
    pour passer le vrai password a asyncpg.
    """
    url = make_url(settings.DATABASE_URL).set(drivername="postgresql")
    return url.render_as_string(hide_password=False)


async def pg_listen_task():
    dsn = _asyncpg_dsn()
    while True:
        try:
            conn = await asyncpg.connect(dsn)
            try:
                # Cap pour éviter la croissance illimitée si le consumer est lent
                # (ex. ws_hub.broadcast bloqué par un client lent). Si la queue
                # est pleine, on drop le payload entrant et on log.
                queue: asyncio.Queue[str] = asyncio.Queue(maxsize=1000)

                # Callback appelé à chaque NOTIFY sur 'db_events'
                def _on_notify(_conn, _pid, _channel, payload: str):
                    try:
                        queue.put_nowait(payload)  # non bloquant
                    except asyncio.QueueFull:
                        logger.warning(
                            "pg_listener: queue full (maxsize=1000), dropping NOTIFY payload"
                        )

                await conn.add_listener("db_events", _on_notify)

                # Boucle: consomme la queue et diffuse aux WS
                while True:
                    payload = await queue.get()
                    try:
                        data = json.loads(payload)
                    except Exception:
                        logger.warning("pg_listener: invalid JSON NOTIFY payload (skipped): %r", payload)
                        continue

                    origin = data.get("originClientId")  # ✅ récupéré du payload
                    if "type" in data:
                        logger.info(
                            "pg_listener NOTIFY %s (userId=%s, origin=%s)",
                            data["type"], data.get("userId"), origin,
                        )
                    else:
                        logger.info(
                            "pg_listener NOTIFY %s op=%s origin=%s",
                            data.get("table", "unknown"), data.get("op", "?"), origin,
                        )

                    await ws_hub.broadcast(data, exclude_client_id=origin)  # ✅

            finally:
                try:
                    await conn.close()
                except Exception:
                    logger.warning("pg_listener: error closing asyncpg conn (ignored)", exc_info=True)

        except asyncio.CancelledError:
            break  # arrêt propre au reload/stop
        except Exception:
            logger.exception("pg_listener: connection error, retrying in 2s")
            await asyncio.sleep(2)
