# PROJECT_MAP — Cartographie haute du monorepo

> Vue d'ensemble structurelle du projet **sport-app**. Pour le détail : [SERVEUR.md](SERVEUR.md), [DATABASES.md](DATABASES.md), [ARCHITECTURE.md](ARCHITECTURE.md), [HOW_TO_ADD_ENTITY.md](HOW_TO_ADD_ENTITY.md), [TAILSCALE_MIGRATION.md](TAILSCALE_MIGRATION.md).

> **À jour 2026-05-23.**

## §1 — Domaine fonctionnel

L'app gère **6 domaines** :

1. **Entraînement (cœur)** — `actual_workouts` (séances effectives) + `planned_workouts` (modèles par jour) + `training_cycles` (programmes pluri-hebdo) + `superset_groups`. Junctions vers `exercises` / `actual_workout_sets`.
2. **Catalogue & ciblage** — `exercises` (user-scoped, instructions, gif) + `muscles` (refactor 3-niveaux 2026-05-08 : 35 muscles précis × 17 groupes × 6 zones) + `equipments` (global, admin) + `available_equipments` (user-scoped).
3. **Goals / progression** — `muscle_goals` (objectif hebdo par muscle), stats agrégées en SQL Room avec JOIN `exercise_muscles`.
4. **Routines & tâches** — `routine_periods` (Matin/Midi/Soir) + `tasks` (Phase 0 2026-05-12 : unification one-shot + récurrent NONE/DAILY/WEEKLY/MONTHLY/YEARLY) + `task_checks`.
5. **Notifications** — `notifications` push WS + persisté DB, deep-links Android.
6. **Outils locaux** — chrono/timer (refactor B4 2026-05-10), mini-overlays flottants ; NetworkMonitor offline indicator.

**Prévu plus tard** : Nutrition (calories + macros), Conversations (UI déjà esquissé).

## §2 — Stack

### Serveur (`serveur/`)
| Couche | Choix |
|---|---|
| HTTP | FastAPI · uvicorn (uvloop sur Linux) |
| ORM | SQLAlchemy 2 (async) · asyncpg |
| DB | PostgreSQL 18 |
| Migrations | Alembic (autogenerate enabled, source de vérité — politique 16) |
| Auth | JWT HS256 (30 min access + refresh token long-lived V8.2) · bcrypt |
| Realtime | Postgres `LISTEN/NOTIFY` → `pg_listener.py` → `ws_hub` WebSocket |
| Rate limit | slowapi |
| Compression | GZipMiddleware |
| Tests | pytest (T1.1 2026-05-06, ~13 tests + 6 admin endpoints, db fixture asyncpg) |

### Android (`appli-android/`)
| Couche | Choix |
|---|---|
| Langage | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Navigation | navigation-compose |
| HTTP | Retrofit + OkHttp · Authenticator (refresh V8.2 + 401 V4.5) |
| Persistance | Room (DATABASE_VERSION = 18) · DataStore (settings, onboarding) · EncryptedSharedPreferences (tokens) |
| Charts | Vico 2.4.1 |
| Sync layer | T4.2 (2026-05-07) : `SyncEngine` + `SyncRegistry` (FK-aware) + `SyncCoordinator` + `SyncableEntity<T>` |
| i18n | EN + FR via `res/values/` + `res/values-fr/` (politique 18) · live switching via `CompositionLocalProvider` |
| Tests | JUnit/Robolectric (T1.1 : 33 DAO smoke + 9 SyncMergeOps + 19 Chrono StateMachine + 8 StatsRange) |

### Déploiement
- **PC dev** : `serveur/venv/` · Postgres service Windows `postgresql-x64-18` · uvicorn `127.0.0.1:8000` · Android debug → `<pc-lan-ip>:8000`.
- **Pi 5 prod** : `serveur/venv/` · systemd `sportapi.service` · `tailscale serve` → `https://<pi-fqdn>` (tailnet-only depuis 2026-05-21) · webhook deploy via `tailscale funnel` port 8443 · Android release → tailnet via VPN always-on.
- **Public** : coupé NAT/PAT Livebox, Caddy + `<public-dns>` dormants (réactivables).
- **APK** : build release Android Studio → install manuel sur Samsung S21+.

## §3 — Arborescence (vue haute)

```
sport-app/
├── CLAUDE.md                    ← index sessions Claude
├── DEV_GUIDE.md                 ← setup utilisateur (source de vérité)
├── docs/                        ← cette doc
│
├── serveur/                     ← BACKEND (FastAPI / Python 3.14)
│   ├── app/
│   │   ├── main.py              ← FastAPI app + montage routers prefix /api/v1/
│   │   ├── settings.py          ← pydantic-settings (.env)
│   │   ├── database.py          ← engine async + AsyncSessionLocal + Base
│   │   ├── auth.py              ← JWT (HS256) + refresh token (V8.2)
│   │   ├── deps.py              ← get_current_user_id, require_admin
│   │   ├── utc_datetime.py      ← UTCDateTime Pydantic (V3.2)
│   │   ├── triggers_loader.py   ← PER_TABLE_FRAGMENTS + compose_function_sql
│   │   ├── ws_hub.py            ← WebSocket broadcast par user_id
│   │   ├── pg_listener.py       ← asyncpg LISTEN row_change
│   │   ├── models/              ← 22 SQLAlchemy models
│   │   ├── schemas/             ← Pydantic (Base/Create/Out, alias camelCase)
│   │   ├── crud/                ← 21 CRUDs canoniques V6.2 + _concurrency.py
│   │   ├── routers/             ← 22 entity routers + auth + ws
│   │   ├── db_triggers/         ← 20 fragments SQL + helpers
│   │   ├── alembic/             ← migrations incrémentales
│   │   ├── seed_database.py     ← catalogue starter
│   │   └── fill_database.py     ← données de test
│   ├── tests/                   ← pytest (T1.1)
│   ├── webhook/                 ← webhook.py + systemd service (T3.1)
│   ├── setup_db.py              ← idempotent bootstrap (checkfirst)
│   ├── reset_db.py              ← destructif avec confirm prompt
│   ├── deploy.sh                ← Pi : pull + alembic + restart
│   └── diagram.dbml             ← visuel projet (politique 14)
│
└── appli-android/               ← CLIENT (Kotlin/Compose)
    └── app/src/main/java/com/example/sportapp/
        ├── data/
        │   ├── model/           ← 21 entités Room
        │   ├── local/           ← DAOs Style A V6.2-IV + AppDatabase + migrations
        │   └── remote/          ← Retrofit Apis + SyncHandlers + WebSocketManager
        ├── sync/                ← T4.2 : SyncEngine + SyncRegistry + SyncCoordinator
        ├── ui/
        │   ├── screens/         ← écrans Compose
        │   └── components/      ← canoniques (common_components/)
        ├── modules feature Style A (flat) :
        │   ├── auth/            ← signup, login
        │   ├── onboarding/      ← B1 (2026-05-11) : 5 steps
        │   ├── settings/        ← langue, sync stats
        │   ├── chrono/          ← B4 refactor (2026-05-10)
        │   ├── admin/           ← UI admin is_admin + UI Showcase
        │   └── notifications/   ← NotificationCenter + UI list
        └── res/
            ├── values/strings.xml      ← EN canonique
            └── values-fr/strings.xml   ← FR (politique 18)
```

## §4 — Points d'entrée typiques

| Tâche | Premier fichier à lire |
|---|---|
| Ajouter une entité | [HOW_TO_ADD_ENTITY.md](HOW_TO_ADD_ENTITY.md) |
| Comprendre le schéma DB | [DATABASES.md](DATABASES.md) + `serveur/app/diagram.dbml` |
| Voir l'archi globale | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Voir un flux concret | [FLOWS.md](FLOWS.md) (auth + sync + WS) |
| Sync protocole | [SYNC_PATTERN.md](SYNC_PATTERN.md) |
| Setup dev | [DEV_GUIDE.md](../DEV_GUIDE.md) |
| Migration Tailscale | [TAILSCALE_MIGRATION.md](TAILSCALE_MIGRATION.md) |
| Refactor UI en cours | [REFACTOR_UI_COMPONENTS.md](REFACTOR_UI_COMPONENTS.md) |
| Liste TODO features | [TODO_FEATURES.md](TODO_FEATURES.md) |
