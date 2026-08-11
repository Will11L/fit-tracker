# app/ws_hub.py
import asyncio
import logging
from typing import Set, Dict, Optional
from fastapi import WebSocket

logger = logging.getLogger(__name__)


class WebSocketHub:
    def __init__(self):
        self._clients: Set[WebSocket] = set()
        self._map_user: Dict[WebSocket, int] = {}
        self._map_client: Dict[WebSocket, str] = {}   # ✅ NOUVEAU
        self._lock = asyncio.Lock()

    async def register(self, ws: WebSocket, user_id: int | None, client_id: Optional[str] = None):  # ✅ SIGNATURE
        await ws.accept()
        async with self._lock:
            self._clients.add(ws)
            if user_id is not None:
                self._map_user[ws] = user_id
            if client_id:
                self._map_client[ws] = client_id  # ✅
            logger.info("ws_hub: registered userId=%s clientId=%s", user_id, client_id)

    async def unregister(self, ws: WebSocket):
        async with self._lock:
            self._clients.discard(ws)
            self._map_user.pop(ws, None)
            self._map_client.pop(ws, None)  # ✅
            logger.info("ws_hub: unregistered a client")

    async def broadcast(self, message: dict, exclude_client_id: Optional[str] = None):  # ✅ NOUVEAU PARAM
        target_uid = message.get("userId")
        dead = []
        total = len(self._clients)
        delivered = 0

        # F5b-2 (2026-05-06) : snapshot pris hors-await sous asyncio mono-thread
        # → safe vs register/unregister concurrents (qui prennent le lock).
        # Si on passe à threadpool/multiprocessing un jour, prendre le lock ici aussi.
        for ws in list(self._clients):
            try:
                if target_uid is not None:
                    uid = self._map_user.get(ws)
                    if uid != target_uid:
                        continue
                if exclude_client_id is not None:
                    cid = self._map_client.get(ws)
                    if cid == exclude_client_id:
                        continue
                await ws.send_json(message)
                delivered += 1
            except Exception:
                dead.append(ws)
        for d in dead:
            await self.unregister(d)

        logger.info(
            "ws_hub: delivered %d/%d to userId=%s, excluded=%s",
            delivered, total, target_uid, exclude_client_id,
        )

ws_hub = WebSocketHub()
