# SYNC_FLOW — Diagramme séquence

Flow de synchronisation client ↔ serveur post-login : pull/merge des 20 entités user-scoped (FK-aware) puis push des locaux unsynced, puis bascule sur le canal WebSocket pour les updates incrémentales en temps réel. Complète [SYNC_PATTERN.md](SYNC_PATTERN.md) (protocole 3-contrats sans tombstones) avec une vue temporelle des acteurs.

## Diagramme

```mermaid
sequenceDiagram
    autonumber
    participant App as Android App
    participant Coord as SyncCoordinator
    participant Engine as SyncEngine
    participant Room as Room DB
    participant REST as FastAPI REST /api/v1
    participant Trig as Postgres triggers<br/>(notify_row_change)
    participant WS as WebSocket /api/v1/ws
    participant Hub as ws_hub + pg_listener

    Note over App,Hub: 1. Trigger post-login (AuthManager.initAuth ✓)
    App->>Coord: onLogin() (retry expo 1s/2s/4s)
    Coord->>Engine: pullMerge() (merge AVANT push, V4.4-B3)

    Note over Engine,REST: 2. Pull merge — itération SyncRegistry FK-aware
    loop pour chacune des 20 entités (parents avant enfants)
        Engine->>REST: GET /api/v1/<entities><br/>Bearer access_token
        REST-->>Engine: 200 [XOut, ...] (snapshot complet user-scoped)
        Engine->>Room: getAllOnce() -> Map<uuid, local>
        Note over Engine: pour chaque remote :<br/>isRemoteNewer(local.updated_at, remote.updated_at)
        Engine->>Room: insertFromServer(r)<br/>.copy(synced=true, pendingDeletion=false)
        Note over Engine: pruneStaleLocals :<br/>local.synced=true && uuid ∉ remoteKeys<br/>-> deleteLocal (convergence delete)
    end

    Note over Engine,REST: 3. Push pending — ordre INVERSE FK pour deletes
    Coord->>Engine: syncManager.syncAllToServer()<br/>(mutex + snackbar UX globale)
    loop deletes (enfants avant parents, ON DELETE CASCADE-safe)
        Engine->>Room: dao.getAllPendingDeletion()
        Engine->>REST: DELETE /api/v1/<entities>/{uuid}
        REST-->>Engine: 200 {ok:true} ou 404 (tolérant)
        Engine->>Room: dao.delete(item) local
    end
    loop upserts (parents avant enfants)
        Engine->>Room: dao.getAllUnsynced()
        Engine->>REST: PUT /api/v1/<entities><br/>[XCreate, ...] (bulk) ou /uuid (fallback)
        REST->>REST: is_payload_stale(payload, existing)<br/>(skip si remote plus récent — last-write-wins)
        REST-->>Engine: 200 [XOut, ...]
        Engine->>Room: dao.markAsSynced(uuid) -> synced=true
    end

    Note over App,Hub: 4. Bascule realtime — WebSocketManager déjà branché par AuthManager
    App->>WS: WSS /api/v1/ws?access_token=...&client_id=...
    WS-->>App: {"type":"client_id","clientId":"..."}
    loop heartbeat
        App->>WS: ping
        WS-->>App: {"type":"pong"}
    end

    Note over Trig,App: 5. Push incrémental serveur → clients (depuis n'importe quel device)
    Note over REST: device X fait un PUT/DELETE
    REST->>Trig: INSERT/UPDATE/DELETE sur table user-scoped
    Trig->>Hub: NOTIFY row_change {event, table, userId, clientId, data}
    Hub->>Hub: pg_listener route par user_id<br/>broadcast vers toutes les sockets de cet user
    Hub-->>App: {"event":"upsert","table":"...","userId":1,<br/>"clientId":"src","data":{...}}
    App->>Room: SyncHandler dispatch par table<br/>insertFromServer(.copy(synced=true))
    Note over App: si clientId == self -> skip<br/>(évite re-merge de son propre push)

    Note over App,Hub: 6. Reconnect réseau (NetworkMonitor.onAvailable)
    App->>Coord: onNetworkAvailable() (retry expo)
    Coord->>Engine: pushAll() PUIS pullMerge()
    Note over Coord: ordre INVERSE de onLogin :<br/>push d'abord pour ne pas perdre les modifs<br/>locales accumulées hors-ligne
```

## Notes

- **Ordre `merge-puis-push` au login (V4.4-B3)** : au login, l'user vient potentiellement d'un autre device → on affiche le state serveur en premier, puis on push les rares pending locaux (souvent vides). Inversé sur reconnect réseau : `push` d'abord pour ne pas perdre les modifs offline.
- **FK-aware** (`SyncRegistry.all` / `SyncRegistry.reversed`) : `pullMerge` et `pushAll` (upserts) itèrent dans l'ordre parents → enfants. `pushAll` (deletes) itère dans l'ordre inverse pour respecter `ON DELETE CASCADE` Postgres (enfants avant parents → évite 404 cascade).
- **Last-write-wins symétrique** : côté client `isRemoteNewer(local.updatedAt, remote.updatedAt)` (cf. [SYNC_PATTERN.md](SYNC_PATTERN.md)) ; côté serveur `is_payload_stale(payload, existing)` rejette les push older-than-existing (depuis 2026-05-07).
- **Convergence delete sans tombstone** : `pruneStaleLocals` (Phase 2 du merge) supprime les locaux `synced=true` absents du snapshot remote → propage les deletes d'un autre device sans `deleted_at`. Garde `synced=true` cruciale : préserve les créations locales offline jamais push (`synced=false`).
- **Retry exponentiel** (`SyncCoordinator.runWithBackoff`) : 3 tentatives 1s → 2s → 4s sur `onLogin` et `onNetworkAvailable` (triggers automatiques). `onUserAction` (bouton drawer / Settings) : pas de retry, snackbar erreur immédiate.
- **`clientId` WS** : chaque client génère un UUID stable et le passe via header `X-Client-Id` (POST/PUT/PATCH/DELETE) → le serveur le re-broadcast dans le payload WS → les autres devices savent qui a déclenché le change (et le device source peut skip son propre echo).

## Sources

- `appli-android/.../sync/SyncCoordinator.kt` — orchestration triggers (login / network / user) + backoff.
- `appli-android/.../sync/SyncEngine.kt` — `pushAll`, `pullMerge`, `pullReplace`, `bulkPushAll`, `pushEntityClass`.
- `appli-android/.../sync/SyncRegistry.kt` + 20 `SyncableEntity` — ordre FK-aware.
- `appli-android/.../sync/base/SyncMergeOps.kt` — `mergeFromRemote` (3-way merge + prune), `pullThenReplace`, `bulkPush`.
- `serveur/app/triggers_loader.py` + `app/db_triggers/*_trigger.sql` (20 fragments) — composition de `notify_row_change()`.
- `serveur/app/routers/ws_router.py` + `ws_hub.py` + `pg_listener.py` — broadcast par `user_id`.
- `serveur/app/crud/_concurrency.py` — `is_payload_stale` (last-write-wins serveur).
- [SYNC_PATTERN.md](SYNC_PATTERN.md) — protocole 3-contrats détaillé.
- [FLOWS.md §2-§3](FLOWS.md) — variante curl/JSON + format payload WS.
