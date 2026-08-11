#!/bin/bash
# Se place dans le dossier de ce script, peu importe d'ou il est appele
cd "$(dirname "$(readlink -f "$0")")"
source env_api/bin/activate

# --timeout-graceful-shutdown 3 : au restart (deploy), uvicorn force la fermeture du WebSocket
# apres 3s au lieu d'attendre 90s (SIGTERM timeout systemd) -> coupure ~5s au lieu de ~90s
exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --proxy-headers --timeout-graceful-shutdown 3
