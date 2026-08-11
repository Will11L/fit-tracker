# Serveur sport-app (FastAPI)

Pour le setup complet (PC dev + Pi prod), commandes, troubleshooting,
et architecture, voir la **[source de vérité dans `DEV_GUIDE.md`](../DEV_GUIDE.md)**
à la racine du repo.

Pour l'analyse détaillée du serveur (routers, modèles, auth, WS, triggers),
voir [`docs/SERVEUR.md`](../docs/SERVEUR.md).

## Quickstart (résumé)

```powershell
cd serveur
.\venv\Scripts\Activate.ps1
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Doc API : `/secure-docs` (Swagger custom, login via `/token-helper`)
- Auth : `POST /token` (form-urlencoded) → JWT
- WebSocket : `/ws?access_token=...&client_id=...`
