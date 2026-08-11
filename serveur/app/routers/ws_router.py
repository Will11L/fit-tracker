# app/routers/ws_router.py
import logging
import uuid

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, HTTPException

from app.ws_hub import ws_hub
from app.auth import verify_token

logger = logging.getLogger(__name__)

ws_router = APIRouter()

@ws_router.websocket("/ws")
async def ws_endpoint(
    websocket: WebSocket,
    access_token: str = Query(...),
    client: str = Query("web"),
    v: str = Query("1"),
    client_id: str | None = Query(None),   # ✅ NOUVEAU
):
    # 1) Décoder le token -> récupérer user_id
    try:
        payload = verify_token(access_token)  # lève HTTPException si invalide/expiré
    except HTTPException as e:
        await websocket.close(code=1008, reason=e.detail or "invalid token")
        return

    user_id_raw = payload.get("user_id")
    if user_id_raw is None:
        await websocket.close(code=1008, reason="user_id manquant dans le token")
        return
    try:
        user_id = int(user_id_raw)
    except (TypeError, ValueError):
        await websocket.close(code=1008, reason="user_id invalide dans le token")
        return

    # 2) Normaliser un client_id si absent
    # NOTE F5a-6 : `client_id` est essentiel pour `exclude_client_id` lors du
    # broadcast (sinon l'émetteur reçoit son propre event en retour). Le client
    # Android l'envoie toujours via `ClientIdProvider`. Si absent ici, c'est un
    # signal anormal (client tiers non conforme) → on log et on génère un fallback
    # qui ne matchera aucun X-Client-Id REST → exclude origin sera de toute façon
    # cassé pour ce client. Acceptable pour un test ad-hoc.
    if not client_id:
        logger.warning(
            "ws: client_id absent (user_id=%s, client=%s), fallback generated",
            user_id, client,
        )
        client_id = str(uuid.uuid4())

    # 3) Enregistrer la socket avec le user_id ET le client_id
    await ws_hub.register(websocket, user_id, client_id)  # ✅
    logger.info(
        "ws: open user_id=%s client=%s v=%s client_id=%s",
        user_id, client, v, client_id,
    )

    # informer le client de son id (utile si généré côté serveur)
    await websocket.send_json({"type": "client_id", "clientId": client_id})

    try:
        while True:
            msg = await websocket.receive_text()
            if msg.strip().lower() in ("ping", '{"type":"ping"}'):
                await websocket.send_json({"type": "pong"})
    except WebSocketDisconnect:
        await ws_hub.unregister(websocket)
        logger.info("ws: close user_id=%s client_id=%s", user_id, client_id)
