# scripts/ — outils de validation MCP

Scripts de smoke/validation du serveur MCP (`/mcp/`), exécutables après un
déploiement pour vérifier que la chaîne OAuth + protocole + tools tient.

Lancer depuis `serveur/` avec le venv activé. Par défaut ils tapent
`http://127.0.0.1:8000/mcp` avec le user de seed `will/<password>`.

## `mcp_smoke.py`
Chaîne complète : DCR → authorize (login + PKCE) → token → `initialize` →
`tools/list` → quelques `tools/call` read-only (sûrs en prod : `list_muscles`,
`get_service_status`, `get_alembic_status`). Affiche le nombre de tools exposés
et les flags `destructiveHint`.

```bash
python scripts/mcp_smoke.py                       # localhost, will/<password>
python scripts/mcp_smoke.py https://<pi-fqdn>/mcp will <password>
```

## `mcp_scope_check.py`
Vérif sécurité : obtient un token `sport:read` **seul** et confirme que les
tools write / destructive / ops sont **refusés** (scope manquant), tandis qu'un
read passe. Args volontairement inexistants → aucune mutation même si
l'enforcement était cassé. Exit code 1 si un tool hors-scope n'est PAS refusé.

```bash
python scripts/mcp_scope_check.py
```

> Les deux scripts créent un client OAuth éphémère par run (ligne dans
> `mcp_clients`) — normal, pas de cleanup nécessaire.
