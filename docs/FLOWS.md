# Flows critiques — sport-app

Documentation pratique des 3 flows transverses serveur ↔ client : **auth**, **sync REST**, **WebSocket realtime**. Exemples curl + payloads JSON tels qu'échangés en prod.

> **À jour 2026-05-23.** L'API serveur est désormais **tailnet-only** (`<pi-fqdn>`) depuis la migration Tailscale du 2026-05-21 — cf. [TAILSCALE_MIGRATION.md](TAILSCALE_MIGRATION.md). Tous les endpoints applicatifs sont préfixés `/api/v1/` (T3.2, 2026-05-07) ; `/healthz`, `/secure-docs`, `/token-helper`, `/webhook/deploy` restent au root.

## §1 — Auth flow (JWT)

```
┌─────────┐  POST /api/v1/signup  ┌────────────┐
│ Android ├──────────────────────▶│  serveur   │  (1) Création compte (public)
│         │◀─── 201 UserOut ──────┤            │
│         │                       │            │
│         │  POST /api/v1/token   │            │  (2) Login form-urlencoded
│         ├──────────────────────▶│            │
│         │◀── access_token ──────┤            │      → JWT HS256, expire 30 min
│         │                       │            │      payload: sub, user_id, exp, iss, aud
│         │                       │            │
│         │  GET / PUT / DELETE   │            │  (3) Toute requête : header
│         │  Authorization: Bearer│            │      Authorization: Bearer <token>
│         ├──────────────────────▶│            │
│         │◀── 200 / 401 / 403 ───┤            │
└─────────┘                       └────────────┘
```

### (1) Signup — création de compte

**Endpoint** : `POST /api/v1/signup` (public, pas d'auth requise)
**Source** : [`auth_router.py`](../serveur/app/routers/auth_router.py)

```bash
curl -X POST "https://<pi-fqdn>/api/v1/signup" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "secret123",
    "first_name": "Alice",
    "last_name": "Dupont"
  }'
```

Réponse `201 Created` :
```json
{
  "id": 7,
  "username": "alice",
  "isAdmin": false
}
```

Erreur `409 Conflict` si username déjà pris. Tout nouvel user créé avec `is_admin=False`. Le `/signup` déclenche aussi le pre-seed (V8.4) : copie du catalogue starter (muscles + exercises + relations) dans les tables user-scoped.

### (2) Login — récupérer un JWT

**Endpoint** : `POST /api/v1/token` (form-urlencoded, **pas JSON**)

```bash
curl -X POST "https://<pi-fqdn>/api/v1/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=will&password=<password>"
```

Réponse `200 OK` :
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "...",
  "token_type": "bearer"
}
```

`refresh_token` ajouté V8.2 (long-lived, stocké côté Android en `EncryptedSharedPreferences`). Erreur `401 Unauthorized` si username/password incorrects. Rate-limited (slowapi).

**JWT payload** (HS256, expire 30 min) :
```json
{
  "sub": "will",
  "user_id": 1,
  "exp": 1746543600,
  "iss": "fittracker-api",
  "aud": "fittracker-clients"
}
```

### (3) Auth sur tout endpoint

```bash
TOKEN="eyJ..."
curl -X GET "https://<pi-fqdn>/api/v1/training-cycles" \
  -H "Authorization: Bearer $TOKEN"
```

Codes possibles :
- `200` : OK
- `401` : token absent / expiré / invalide → côté Android, `Authenticator` OkHttp tente d'abord un refresh ; si échec, clear tokens + redirect login (V4.5 + V8.2)
- `403` : token valide mais user n'a pas accès (cross-user, ou non-admin sur Type C)

### (4) `/me` — récupérer le user du token

```bash
curl -X GET "https://<pi-fqdn>/api/v1/me" \
  -H "Authorization: Bearer $TOKEN"
```

Réponse :
```json
{
  "id": 1,
  "username": "will",
  "isAdmin": true,
  "firstName": "Will",
  "lastName": null
}
```

### (5) `/me/profile` — patch profile (self-only)

`PATCH /api/v1/me/profile` — body partiel `{firstName?, lastName?}`. Self-only (pas besoin d'`is_admin`). Utilisé par l'onboarding step Welcome + ProfileScreen.

### (6) Endpoints admin

`PATCH /api/v1/users/{user_id}/admin` (body `{isAdmin: bool}`) — promote/demote un user. Guard `require_admin`. Self-protect 400 + last-admin protect 400. UI Drawer → Admin → Manage users (visible si `isAdmin=true`).

## §2 — Sync REST flow

> Pour le mapping complet 22 routers ↔ 22 Retrofit Apis, voir Swagger `/secure-docs` (source vivante).

### Patterns canoniques

| Op | Verbe | Path | Body | Réponse |
|---|---|---|---|---|
| Lire tous | `GET` | `/api/v1/<entities>` | — | `[XOut, ...]` |
| Lire un | `GET` | `/api/v1/<entities>/{uuid}` | — | `XOut` |
| Upsert (PUT) | `PUT` | `/api/v1/<entities>/{uuid}` | `XCreate` | `XOut` |
| Bulk upsert | `PUT` | `/api/v1/<entities>` | `[XCreate, ...]` | `[XOut, ...]` |
| Delete | `DELETE` | `/api/v1/<entities>/{uuid}` | — | `{"ok": true}` |

**Convention V6.2** : signature CRUD canonique = `(db, uuid, dto, user_id)`, écrasement total via `model_dump`, ownership check renvoyant `403` si cross-user, delete renvoyant `bool` (404 si False). Concurrency : `is_payload_stale(payload, existing)` rejette les push older-than-existing pour éviter lost-update (single-upserts, depuis 2026-05-07).

### Exemple — upsert d'un training cycle

```bash
curl -X PUT "https://<pi-fqdn>/api/v1/training-cycles/abc-123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "uuid": "abc-123",
    "name": "Cycle hypertrophie",
    "weeks": 8,
    "description": "Bloc 8 semaines focus volume",
    "synced": true,
    "pendingDeletion": false,
    "updatedAt": "2026-05-05T14:32:18.123456Z"
  }'
```

Notes :
- `userId` n'est **jamais** lu du payload client (V2.2). Le serveur l'injecte depuis `Depends(get_current_user_id)`.
- Ownership : `403` si `uuid` existe et appartient à un autre user (V2.1).
- Format des dates : `YYYY-MM-DDTHH:MM:SS.UUUUUUZ` (UTC strict 6 décimales — V3.2 du 2026-05-05).

### Côté Android — sync push (post-T4.2)

`SyncEngine.pushEntityClass(<Entity>::class)` (depuis un ViewModel après mutation) :
1. Lookup `<Entity>Syncable` via `SyncRegistry`.
2. Récupère les locaux `synced=false` (`dao.getAllUnsynced()`).
3. PUT bulk si possible, sinon fallback PUT par-uuid.
4. Marque comme synced si OK (`dao.markAsSynced(uuid)`).
5. Pour les `pendingDeletion` : DELETE remote, puis `dao.delete(item)` local.

Fichiers : [`SyncEngine.kt`](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncEngine.kt), [`SyncRegistry.kt`](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncRegistry.kt).

### Côté Android — sync pull (catch-up)

`SyncEngine.pullMerge()` ou `pullReplace()` :
1. GET `/api/v1/<entities>` (full snapshot pour cet user).
2. Pour chaque item remote : `isRemoteNewer(remote, local)` → upsert local si vrai (préserve modifs locales en cours).
3. `pruneStaleLocals` : delete les locaux `synced=true` qui ne sont plus dans le payload remote (= deleted ailleurs).

Déclenché par `SyncCoordinator` après login (`AuthManager.onLogin`), reconnexion réseau (`NetworkMonitor.onAvailable`), changement d'user. Retry exponentiel.

## §3 — WebSocket realtime

```
┌─────────┐                                          ┌────────────┐
│ Android │  WS /api/v1/ws?access_token=…&client_id= │  serveur   │
│         ├─────────────────────────────────────────▶│            │
│         │◀── {"type":"client_id","clientId":…}    ─┤            │
│         │                                          │            │
│         │     ─────── Postgres NOTIFY ──────────── │            │
│         │     trigger sur INSERT/UPDATE/DELETE     │            │
│         │     ws_hub broadcast au user_id          │            │
│         │◀── {"event":"upsert","table":...,        │            │
│         │      "data":{...},"clientId":…}          │            │
│         │                                          │            │
│         │ "ping" ─────────────────────────────────▶│            │
│         │◀───────────────── {"type":"pong"} ──────┤            │
└─────────┘                                          └────────────┘
```

### Connexion

**Endpoint** : `WS /api/v1/ws` ([`ws_router.py`](../serveur/app/routers/ws_router.py))

Query params :
- `access_token` (obligatoire) : JWT comme pour REST
- `client` : `"android"` ou `"web"` (label info)
- `client_id` : UUID stable côté client (sinon serveur en génère un et le renvoie via `{"type":"client_id"}`)
- `v` : version protocole (actuellement `"1"`)

Erreurs : close code `1008` si token invalide/expiré.

### Format des events serveur → client

Quand une row change en DB (INSERT/UPDATE/DELETE sur une table user-scoped), le trigger `notify_row_change()` push un payload JSON via Postgres `NOTIFY row_change`. `pg_listener` le re-broadcaste via `ws_hub` à toutes les sockets du user concerné.

Format payload (composé par les fragments `app/db_triggers/*_trigger.sql`) :

```json
{
  "event": "upsert",
  "table": "training_cycles",
  "userId": 1,
  "clientId": "abc-789-original-source-client",
  "data": {
    "uuid": "abc-123",
    "name": "Cycle hypertrophie",
    "weeks": 8,
    "...": "...",
    "updatedAt": "2026-05-05T14:32:18.123456Z"
  }
}
```

- `clientId` = identifiant du client qui a déclenché le change (permet aux autres devices d'éviter le re-merge inutile de leur propre push).
- `event` ∈ `{"upsert", "delete"}`.

### Anti-flood / keepalive

- Client → "ping" (texte) ou `{"type":"ping"}` → serveur répond `{"type":"pong"}`.
- Côté Android : `WebSocketManager` gère reconnexion automatique avec backoff après `onLost`.

### Tables avec push WS

20 entités user-scoped ont un fragment trigger dans `app/db_triggers/`. Inventaire à jour : voir `PER_TABLE_FRAGMENTS` dans [`triggers_loader.py`](../serveur/app/triggers_loader.py). Groupes :
- Workouts : `actual_workouts`, `actual_workout_exercises`, `actual_workout_sets`, `planned_workouts`, `planned_workout_exercises`, `cycle_workouts`
- Refs : `exercises`, `muscles`, `equipments`, `available_equipments`, `exercise_muscle`, `exercise_equipment`
- Goals : `muscle_goals`
- Supersets : `superset_groups`, `superset_exercises`
- Cycles : `training_cycles`
- Routines : `routine_periods`, `tasks`, `task_checks`
- Notifications : `notifications`

## §4 — Outillage / debug

- **Swagger live** : [`https://<pi-fqdn>/secure-docs`](https://<pi-fqdn>/secure-docs) — login via `/token-helper`. Source de vérité du contrat OpenAPI (62+ paths).
- **Healthcheck public** : `GET /healthz` (root, sans auth, sans rate limit) → `{status, db, ts}`.
- **Tester un trigger** : `psql ... -c "UPDATE training_cycles SET name = 'test' WHERE uuid = '...';"` puis observer `LISTEN row_change` dans une autre session psql.
- **Debug Android sync** : `Log.d("SyncEngine"|"SyncCoordinator"|"WebSocketManager", ...)` filterable via Logcat.
