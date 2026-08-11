# Architecture — sport-app

Diagrammes Mermaid haut niveau de la stack et des flux.

> ⚠️ **Document vivant à maintenir en continu** — Ce diagramme d'architecture doit être tenu à jour automatiquement. Dès qu'un changement d'architecture est détecté (nouvelle entité majeure, nouveau service, refonte sync, changement réseau Tailscale/Caddy, nouveau flow auth, nouveau build variant), le mettre à jour dans le même commit que la modif technique, **sans attendre que l'utilisateur le demande**. Politique #19 (validée 2026-05-27).

> **À jour 2026-05-27.** Diagramme overview synthétique §0 ajouté (vue globale Android + Pi + PC dev + sync + WS + auto-deploy). Refonte sync layer Android post-T4.2 (2026-05-07) intégrée. Migration Tailscale §7 (2026-05-21) intégrée. Pour le tuto cross-stack d'ajout d'entité, voir [HOW_TO_ADD_ENTITY.md](HOW_TO_ADD_ENTITY.md). Pour le protocole sync, voir [SYNC_PATTERN.md](SYNC_PATTERN.md).

> **Maj 2026-05-31.** Migration Android **package-by-layer → package-by-feature** : top-level `app/ core/ designsystem/ feature/` (détail dans §5). Les couches (UI → ViewModel → Data) sont inchangées ; c'est leur organisation en packages qui change. Aucune dépendance feature↔feature.

> **Maj 2026-06-10.** Client web **`appli-web/` (Angular 22)** intégré : SPA servi par FastAPI **same-origin** à `/` sur la Pi (mount statique catch-all en fin de `main.py`, fallback `index.html`), build inséré dans `deploy.sh` (npm ci + build, Node 22). Déployé en prod le 2026-06-08. MCP : 26 tools (et plus 18).

> **Maj 2026-07-03.** Domaine **Santé / Health Connect** : 3 tables serveur (`health_step_counts`, `health_metrics`, `health_goals`) + endpoints `/api/v1/health-*` + miroir Room/sync Android (v23) ; l'app lit Health Connect (source effective : Samsung Health) et importe vers la Pi à chaque sync. Nouveau module **`:wear`** (app Galaxy Watch, Wear OS Compose + Health Services) : canal **Wearable Data Layer** montre ↔ téléphone — stream 1×/s au premier plan + pull à la demande (`WearableListenerService`, réveil app fermée), **affichage-only** (Health Connect reste la source persistée, anti double-comptage).

## §0 — Overview synthétique (vue globale)

Diagramme global qui rassemble en une seule vue : Android client (2 build variants) + client web Angular (SPA servi par FastAPI same-origin) ↔ Pi prod (Tailscale-only) + PC dev (HTTP LAN) + flow auth JWT + sync REST pull/push + WebSocket realtime + auto-deploy webhook GitHub + serveur MCP (sous-app `/mcp/`, 26 tools, OAuth DCR, clients Claude Desktop/Code via tailnet). Les §1-§7 ci-dessous détaillent chaque facette.

```mermaid
flowchart LR
    subgraph Mobile["📱 Android (Kotlin/Compose/Hilt/Room/Retrofit)"]
        Debug["build debug<br/>→ PC dev"]
        Release["build release<br/>→ Pi prod"]
        Auth["TokenManager<br/>(EncryptedSharedPrefs)<br/>JWT + refresh"]
        SyncLayer["SyncEngine<br/>+ SyncCoordinator<br/>+ WebSocketManager"]
    end

    subgraph PCDev["💻 PC Windows (dev)"]
        DevAPI["uvicorn :8000<br/>http://<pc-lan-ip>"]
        DevPG[("Postgres 18 local")]
        DevAPI --> DevPG
    end

    subgraph PiProd["🍓 Raspberry Pi 5 (prod)"]
        TSServe["tailscale serve :443<br/>(privé tailnet)"]
        TSFunnel["tailscale funnel :8443<br/>(public, HMAC only)"]
        Uvicorn["uvicorn :8000<br/>(FastAPI + Alembic)"]
        MCP["MCP server /mcp/<br/>26 tools (read/write/<br/>destructive/ops)<br/>(OAuth DCR + PKCE)"]
        WebDist["SPA Angular (appli-web)<br/>dist/ servi par FastAPI à /<br/>(fallback index.html)"]
        Webhook["webhook.py :8001"]
        Deploy["deploy.sh<br/>(git pull + alembic<br/>+ build web + restart)"]
        ProdPG[("Postgres 18<br/>+ triggers NOTIFY")]
        Caddy["Caddy + DDNS<br/>(dormant)"]
        TSServe --> Uvicorn
        TSFunnel --> Webhook
        Webhook --> Deploy
        Deploy --> Uvicorn
        Uvicorn --> ProdPG
        Uvicorn --> MCP
        Uvicorn --> WebDist
        MCP --> ProdPG
    end

    Watch["⌚ Galaxy Watch (module :wear)<br/>Wear Compose + Health Services"]
    HC["🩺 Health Connect (module système)<br/>pas / FC / sommeil / SpO2<br/>(écrits par Samsung Health)"]

    GitHub["🐙 GitHub<br/>(push main)"]
    Tailnet["🔒 Tailnet privé<br/><pi-fqdn>"]
    MCPClient["🤖 Claude Desktop / Code<br/>(client MCP)"]
    Browser["🌐 Navigateur (tailnet)<br/>client web Angular"]

    Debug -->|HTTP LAN<br/>/api/v1/| DevAPI
    Release -->|HTTPS Tailscale<br/>/api/v1/| Tailnet
    Tailnet -->|:443| TSServe
    Release -.->|WS /api/v1/ws<br/>?access_token&client_id| Tailnet
    SyncLayer -.->|sync REST pull/push<br/>last-write-wins| Release
    Auth -.->|Bearer JWT<br/>+ refresh 401| Release
    GitHub -->|push event<br/>HMAC-SHA256| TSFunnel
    ProdPG -.->|LISTEN row_change<br/>broadcast par user_id| TSServe
    MCPClient -->|OAuth DCR + Streamable HTTP<br/>/mcp/| Tailnet
    Browser -->|"HTTPS / (SPA) + /api/v1/<br/>same-origin, zéro CORS"| Tailnet
    Watch -.->|"Wearable Data Layer<br/>live 1/s + pull à la demande<br/>(affichage-only)"| Mobile
    HC -.->|"lecture read-only<br/>import santé → sync"| Mobile
```

Légende :
- Trait plein = HTTP/HTTPS standard
- Trait pointillé = flux applicatif (sync, WS, auth refresh, broadcast realtime)
- `tailscale serve` = endpoint **privé** (tailnet uniquement, accessible aux devices liés au compte)
- `tailscale funnel` = endpoint **public** (Internet) limité au webhook GitHub avec HMAC obligatoire
- Public `<public-dns>` + Caddy : dormants depuis migration Tailscale (2026-05-21)

## §1 — Stack haut niveau

```mermaid
flowchart LR
    subgraph Android["Android — Kotlin / Jetpack Compose"]
        UI["UI Compose<br/>Screens + ViewModels"]
        Room[("Room SQLite<br/>v18 — 21 entités")]
        Sync["SyncEngine + SyncRegistry<br/>+ SyncCoordinator<br/>+ 20 SyncableEntity"]
        Net["Retrofit + OkHttp<br/>+ Authenticator (401)"]
        WS["WebSocketManager<br/>+ 20 SyncHandlers"]
    end

    subgraph Server["Serveur — FastAPI / Python 3.14"]
        Routers["22 entity routers + auth + ws<br/>(REST CRUD, prefix /api/v1)"]
        CRUD["21 CRUDs canoniques (V6.2)"]
        Auth["JWT auth<br/>(HS256, exp 30 min, refresh V8.2)"]
        WSHub["ws_hub<br/>(broadcast par user_id)"]
        PgListener["pg_listener<br/>(LISTEN row_change)"]
    end

    subgraph PG["PostgreSQL 18"]
        Tables[("20+ tables user-scoped")]
        Triggers["notify_row_change()<br/>+ 20 fragments"]
        Func["Fonctions:<br/>iso_utc, get_user_id_for"]
    end

    UI --> Room
    UI --> Sync
    Sync --> Net
    Sync --> Room
    WS --> Sync
    Net <-->|REST JSON<br/>JWT Bearer| Routers
    WS <-->|WebSocket<br/>access_token query| WSHub
    Routers --> CRUD
    Routers --> Auth
    CRUD --> Tables
    Tables --> Triggers
    Triggers -->|NOTIFY row_change| PgListener
    PgListener --> WSHub
    WSHub -->|broadcast| WS
```

Stack :
- **Mobile** : Kotlin · Jetpack Compose · Hilt · Room · Retrofit · OkHttp · DataStore
- **Web** : Angular 22 (zoneless) · TypeScript · Dexie (offline) · echarts — SPA buildé dans `appli-web/dist/`, servi par FastAPI same-origin à `/` (mount statique, fallback `index.html`)
- **Backend** : FastAPI · Python 3.14 · SQLAlchemy 2 (async) · asyncpg · Alembic · WebSockets natifs · slowapi (rate limit) · GZipMiddleware
- **DB** : PostgreSQL 18 (prod : Raspberry Pi 5)

## §2 — Flux sync montante (Android → Serveur)

```mermaid
sequenceDiagram
    participant UI as UI / VM Android
    participant DAO as Room DAO
    participant SE as SyncEngine
    participant SY as SyncableEntity<X>
    participant API as XApi (Retrofit)
    participant R as Router serveur
    participant CR as CRUD
    participant DB as Postgres

    UI->>DAO: insert(item) [synced=false]
    DAO-->>UI: OK (item local)
    UI->>SE: pushEntityClass(X::class)
    SE->>SY: getAllUnsynced()
    SY->>DAO: getAllUnsynced()
    DAO-->>SY: [items synced=false]
    SY->>API: PUT /api/v1/xs (bulk) puis fallback PUT /api/v1/xs/{uuid}
    API->>R: HTTPS + JWT Bearer
    R->>CR: upsert_x(db, uuid, dto, user_id)
    CR->>CR: is_payload_stale(payload, existing) ?
    CR->>DB: INSERT / UPDATE
    DB-->>CR: row
    CR-->>R: XOut
    R-->>API: 200 XOut
    API-->>SY: success
    SY->>DAO: markAsSynced(uuid)
```

## §3 — Flux sync descendante WebSocket (push realtime)

```mermaid
sequenceDiagram
    participant DB as Postgres
    participant T as Trigger notify_row_change()
    participant L as pg_listener
    participant H as ws_hub
    participant WS as WebSocketManager Android
    participant SH as XSyncHandler
    participant DAO as Room DAO

    DB->>T: INSERT/UPDATE/DELETE on table X
    T->>T: jsonb_build_object(event, table, userId,<br/>clientId, data) via fragment X_trigger.sql
    T->>L: NOTIFY row_change, payload
    L->>H: broadcast(user_id, payload)
    H->>WS: ws.send(payload JSON)
    WS->>SH: handle(table=X, event, data)
    SH->>DAO: insertFromServer(item) ou delete(uuid)
    Note over SH,DAO: insertFromServer force<br/>synced=true (vient du serveur)
```

Garanties :
- `clientId` du payload = client qui a déclenché le change. Les autres devices du même user reçoivent l'event ; le client source peut l'ignorer (déjà à jour localement).
- Si le client est déconnecté : pas de catch-up automatique → `SyncEngine.pullMerge()` au reconnect (V4.4).

## §4 — Flux sync descendante REST batch (catch-up)

```mermaid
sequenceDiagram
    participant App as App Android (login / reconnect)
    participant SC as SyncCoordinator
    participant SE as SyncEngine
    participant API as XApi Retrofit
    participant R as Router serveur
    participant DAO as Room DAO

    App->>SC: onLogin() / onNetworkAvailable()
    SC->>SE: pullMerge() (ordre FK-aware via SyncRegistry)
    Note over SE: Pour chaque entité (20 fois) :
    SE->>API: GET /api/v1/xs
    API->>R: HTTPS + JWT
    R-->>API: [XOut, ...]
    API-->>SE: List<X>
    SE->>DAO: getAllOnce()
    DAO-->>SE: List<X local>
    Note over SE: Pour chaque remote :<br/>isRemoteNewer(remote, local)<br/>→ insertFromServer si oui
    Note over SE: pruneStaleLocals(remote, local) :<br/>delete les locaux synced=true<br/>absents du payload remote
```

Déclencheurs :
- `AuthManager.onLogin` après login (V4.4)
- `NetworkMonitor.onAvailable` après reconnexion (V4.4)
- Boutons explicites dans Settings (`RemoteData*` shells)

## §5 — Couches Android (architecture interne)

```mermaid
flowchart TD
    subgraph UI["UI Layer (Compose)"]
        Feat["feature/* ui/ + ui/components/<br/>(19 features : home, session, planning,<br/>stats, goals, routines, chrono, auth, …)"]
        DS["designsystem/<br/>(common_components, theme, icons,<br/>drawer, bottomNav = composants Figma)"]
    end

    subgraph VM["ViewModel Layer (Hilt)"]
        VMs["ViewModels<br/>(feature/*/viewmodel/)"]
    end

    subgraph Domain["Domain / Repository"]
        NotifCenter["NotificationCenter<br/>OnboardingRepository<br/>CurrentUserManager"]
    end

    subgraph Data["Data Layer"]
        DAOs["20+ DAOs Room (Style A V6.2-IV)"]
        Apis["Retrofit Apis"]
        WS["WebSocketManager"]
        Sync["SyncEngine + SyncRegistry<br/>+ SyncCoordinator + SyncableEntity<br/>+ RemoteData* (shells)"]
    end

    subgraph Local["Local"]
        Room[("Room SQLite v18")]
        Prefs["DataStore + Encrypted SP<br/>(TokenManager, CurrentUserManager,<br/>OnboardingPreferences)"]
    end

    subgraph Remote["Remote"]
        HTTP["Retrofit + OkHttp<br/>+ Authenticator (refresh V8.2)"]
        WSConn["OkHttp WebSocket"]
    end

    Feat --> VMs
    Feat --> DS
    VMs --> NotifCenter
    VMs --> DAOs
    VMs --> Sync
    Sync --> DAOs
    Sync --> Apis
    WS --> DAOs
    DAOs --> Room
    Apis --> HTTP
    WS --> WSConn
    NotifCenter --> Prefs
    HTTP --> Prefs
```

Conventions :
- **Organisation des packages (package-by-feature, depuis 2026-05-31)** : `app/` (shell : MainActivity, SportApp, SnackbarController, navigation) · `core/` (data, network, sync, domain, utils, di, stats — tout le non-UI partagé, ex-`data/`/`sync/`/`utils/`/`domain/`) · `designsystem/` (UI réutilisable = composants Figma) · `feature/*` (19 features auto-contenues : chacune `ui/` + `ui/components/` + `viewmodel/`). `R`/`BuildConfig` restent au package racine `com.example.sportapp`. Zéro dépendance feature↔feature (un type partagé comme `StatsRange` vit dans `core/stats`).
- ViewModels n'importent **jamais** de la couche network (Authenticator), Room types primitifs uniquement.
- DAOs Style A V6.2-IV : wrappers publics posent `synced=false + updatedAt = getNowISO8601()`, délégation à `*Internal` Room-annoté ; `*FromServer` pour préserver les payloads serveur (force `synced=true`).
- i18n EN/FR obligatoire (politique 18) : `stringResource(R.string.xxx)` partout dans Compose ; locale switching via `CompositionLocalProvider` dans `MainActivity`.

## §6 — Couches serveur (architecture interne)

```mermaid
flowchart TD
    subgraph Routes["Routes (prefix /api/v1 sauf utility)"]
        AuthR["auth_router<br/>(/signup /token /me /me/profile)"]
        WSR["ws_router (/ws)"]
        Routers22["22 entity routers<br/>(GET / PUT / DELETE)"]
        Util["/healthz /secure-docs<br/>/token-helper (root)"]
    end

    subgraph Layer["Application Layer"]
        Deps["deps.py<br/>get_current_user_id, require_admin"]
        CRUD["21 CRUDs canoniques (V6.2)"]
        Schemas["22 Schemas Pydantic"]
        Concurrency["_concurrency.py<br/>is_payload_stale"]
    end

    subgraph Models["Models / Persistence"]
        SAModels["22 SQLAlchemy models<br/>(Base.metadata)"]
        Alembic["Alembic migrations<br/>(autogenerate enabled V3.4)"]
    end

    subgraph DB["PostgreSQL"]
        Tables[("20+ tables user-scoped")]
        Triggers["notify_row_change()<br/>+ 20 fragments"]
        Helpers["iso_utc()<br/>get_user_id_for()"]
    end

    subgraph Realtime["Realtime"]
        PGL["pg_listener<br/>(asyncpg LISTEN)"]
        Hub["ws_hub<br/>(register/broadcast)"]
    end

    AuthR --> Deps
    Routers22 --> Deps
    Routers22 --> CRUD
    CRUD --> Concurrency
    CRUD --> SAModels
    CRUD --> Schemas
    SAModels --> Tables
    Alembic --> Tables
    Alembic --> Triggers
    Tables --> Triggers
    Triggers --> Helpers
    Triggers -->|NOTIFY| PGL
    PGL --> Hub
    WSR --> Hub
```

Politiques cross-cutting (cf. [CLAUDE.md](../CLAUDE.md)) :
- §8 sécurité : auth obligatoire + cascade ownership + `require_admin` pour Type C
- §9 squelette uniforme par module type
- §10 defaults DB sémantiques uniquement
- §11 UPPER_CASE pour les états (cross-stack)
- §16 Alembic source de vérité (pas de `Base.metadata.create_all` hors bootstrap)
- §17 snake_case wire serveur + alias camelCase JSON

## §7 — Topologie déploiement (post-migration Tailscale 2026-05-21)

```mermaid
flowchart LR
    subgraph PC["PC Windows (dev local)"]
        DevServer["uvicorn :8000<br/>(local Postgres)"]
        DevApp["App Android<br/>build debug<br/>→ <pc-lan-ip>:8000"]
    end

    subgraph Pi["Raspberry Pi 5 (prod)"]
        TS_Serve["tailscale serve :443<br/>(privé, tailnet)"]
        TS_Funnel["tailscale funnel :8443<br/>(public, HMAC only)"]
        Systemd["systemd<br/>sportapi.service"]
        Uvicorn["uvicorn :8000"]
        Webhook["webhook.py :8001<br/>(deploy auto)"]
        ProdPG[("PostgreSQL 18<br/>local")]
        Caddy["Caddy (dormant)<br/>public coupé NAT/PAT"]
        TS_Serve --> Uvicorn
        TS_Funnel --> Webhook
        Systemd --> Uvicorn
        Uvicorn --> ProdPG
    end

    Tailnet["Tailnet privé<br/>(<pi-fqdn>)"]
    GitHub["GitHub Webhooks<br/>(public, HMAC SHA-256)"]
    Phone["App Android<br/>build release<br/>(Tailscale always-on)"]
    WebClient["Navigateur (tailnet)<br/>client web Angular"]

    Phone -->|HTTPS via Tailscale<br/>JWT| Tailnet
    WebClient -->|"HTTPS / (SPA)<br/>+ /api/v1/ same-origin"| Tailnet
    Tailnet -->|:443| TS_Serve
    GitHub -->|push event<br/>:8443 + HMAC| TS_Funnel
    DevApp -->|HTTP<br/>JWT| DevServer
```

- **Build debug** Android → `http://<pc-lan-ip>:8000/api/v1/` (PC dev local sur LAN)
- **Build release** Android → `https://<pi-fqdn>/api/v1/` (Pi via Tailscale serve, port 443)
- **Client web** → `https://<pi-fqdn>/` (SPA Angular servi par FastAPI same-origin : zéro CORS, WS `/api/v1/ws` direct ; build via étape npm de `deploy.sh`, Node 22 requis sur la Pi)
- **Public coupé** : NAT/PAT Livebox port 80/443 désactivés ; Caddy + DDNS `<public-dns>` restent dormants (réactivables en 2 clics).
- **Webhook GitHub** : `https://<pi-fqdn>:8443/webhook/deploy` (via `tailscale funnel`, HMAC vérifié par `webhook.py`).
- Setup détaillé : [`DEV_GUIDE.md`](../DEV_GUIDE.md) §10 · [`TAILSCALE_MIGRATION.md`](TAILSCALE_MIGRATION.md).
