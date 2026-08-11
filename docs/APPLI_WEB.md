# APPLI_WEB.md — client web Angular (`appli-web/`)

> Doc de code/archi du **3ᵉ module du monorepo** : le client web Angular, jumeau du client Android, **offline-first**, servi par FastAPI same-origin sur la Pi (zéro CORS). Pendant web de [`APPLI_ANDROID.md`](APPLI_ANDROID.md). Document vivant — à maintenir quand l'archi web bouge (nouvelle couche sync, nouveau pattern, nouvelle feature majeure).
>
> Créé le 2026-06-15 (premier état des lieux après livraison du module Nutrition complet).

## En une phrase

SPA Angular 22 **zoneless**, offline-first via **Dexie** (IndexedDB), qui réplique le modèle de données et la logique de sync du client Android, parle à la même API FastAPI (`/api/v1`) que lui, reçoit le temps-réel par le même WebSocket (`/api/v1/ws`), et est servi en statique par le serveur (même origine → pas de CORS, pas de proxy).

## Stack

| Brique | Choix |
|---|---|
| Framework | Angular 22 (standalone components, **zoneless** — pas de `zone.js`, change detection par signals) |
| Langage | TypeScript 6 (strict) |
| Réactivité | Signals Angular (`signal`, `computed`, `effect`) + RxJS `Observable` pour les flux live Dexie |
| Persistance locale | **Dexie** (wrapper IndexedDB) — schéma versionné, actuellement **v9** |
| HTTP | `provideHttpClient` + 2 intercepteurs (`clientIdInterceptor`, `authInterceptor`) |
| Temps-réel | WebSocket natif piloté par l'état d'auth |
| Graphes | ECharts (via `multi-line-chart`, `nutrition-stats-chart`) |
| Build | `@angular/build:application` → `dist/appli-web/browser/` |
| Styles | SCSS + design tokens CSS custom properties (`designsystem/theme/`) |
| Tests | Vitest/Karma-less via le runner Angular (`ng test`, specs `*.spec.ts`) |

**Path aliases** (`tsconfig.json`) : `@core/*`, `@designsystem/*`, `@features/*`.

**Bootstrap** : `app.config.ts` (providers : router avec `withComponentInputBinding`, http + intercepteurs, enregistrement multi des `SYNCABLE_STORES`).

## Architecture — package-by-feature

Calquée sur l'archi Android (`designsystem/` + `feature/` + `core/` + shell). Arborescence `src/app/` :

```
app/
├── app.ts / app.config.ts / app.routes.ts   # racine : bootstrap + 27 routes (lazy loadComponent)
├── shell/                                     # coquille applicative
│   ├── app-shell.ts                           #   drawer (rail/déplié) + bottom-nav + mode Sport/Nutrition
│   └── nav-mode.ts                            #   couleurs d'accent + items de nav par mode
├── core/                                      # logique transverse (109 fichiers .ts)
│   ├── api/        (31)                        #   1 service Retrofit-like par entité → /api/v1/<entité>
│   ├── models/     (38)                        #   types Remote* (wire) + Local* (Dexie) par entité
│   ├── sync/                                   #   ⭐ cœur offline-first (voir §Sync)
│   │   ├── dexie-db.ts                         #     schéma IndexedDB versionné (v9)
│   │   ├── syncable-store.ts                   #     contrat + BaseDexieStore
│   │   ├── stores/ (29)                        #     1 store par entité syncable
│   │   ├── sync-engine.ts                      #     orchestration pushAll/pullMerge/pullReplace
│   │   ├── sync-merge.ts                       #     merge last-write-wins (updated_at)
│   │   └── ws.service.ts                       #     WebSocket realtime (NOTIFY → maj Dexie)
│   ├── auth/                                   #   token-store, guard, 2 intercepteurs, client-id
│   ├── snackbar/                               #   service de feedback
│   └── utils/                                  #   uuid, etc.
├── designsystem/                              # design system (65 fichiers .ts)
│   ├── common_components/ (60)                 #   atoms/molecules (Custom*, drawer-*, *-bottom-sheet…)
│   ├── icons/
│   └── theme/                                  #   _colors.scss, _spacing.scss, _typography.scss, theme.service
├── features/  (17 modules)                    # auth, home, session, planning, calendar, routines,
│   │                                          # exercises, muscles, equipment, goals, stats, chrono,
│   │                                          # notifications, profile, settings, quotes, nutrition
│   └── nutrition/                             #   ⭐ module le plus riche (journal, catalogue, stats,
│                                              #     objectifs, recettes, presets, OFF, CIQUAL micros)
└── showcase/                                  # vitrine des composants du design system
```

## Sync — le cœur offline-first

Réplique le protocole de sync convergent multi-device d'Android (cf. [`SYNC_PATTERN.md`](SYNC_PATTERN.md)). Principe : **l'UI lit/écrit toujours Dexie en local** (instantané, marche hors-ligne), et la couche sync réconcilie avec le serveur en arrière-plan.

### Contrat `SyncableStore<T>` (`syncable-store.ts`)

Chaque entité a un store qui implémente le même contrat, factorisé dans `BaseDexieStore` :

- **Local (Dexie)** : `getAllLocal`, `bulkPutLocal`, `bulkDeleteLocal`, `markSyncedLocal`, `deleteLocal`, `clearLocal`, `liveStats()` (flux RxJS du compteur synced/unsynced/pendingDeletion).
- **Remote (API)** : `fetchRemote`, `pushUpsert`, `pushUpsertBulk`, `pushDelete`.
- Chaque store déclare `name` (table Dexie) + `wsKey` (clé d'entité dans les payloads WS).

Les 29 stores sont enregistrés en `multi` via le token `SYNCABLE_STORES` → le `SyncEngine` itère dessus sans les connaître individuellement (FK-aware via l'ordre d'enregistrement).

### `SyncEngine` (`sync-engine.ts`)

Orchestre les opérations sur l'ensemble des stores (avec file d'attente pour sérialiser) :

- `syncAll()` — push des locaux non-sync puis pull/merge.
- `bulkPushAll()` — pousse tout en masse.
- `pullMerge()` — récupère le serveur et fusionne (last-write-wins).
- `pullReplace()` — remplace le local par le serveur (reset).
- `getAllAsUnsynced()` / `clearAll()` — utilitaires (boutons Settings).

### Merge (`sync-merge.ts`)

Last-write-wins sur `updated_at` (même règle que le client Android et le serveur, cf. politique `_concurrency.py`). ⚠️ **Conséquence prod connue** : une donnée modifiée directement en base (script SQL) ne se propage aux clients web que si son `updated_at` est bumpé.

### Temps-réel (`ws.service.ts`)

WebSocket `/api/v1/ws?access_token=…&client_id=…`, **piloté par l'auth** via un `effect` : connecte quand authentifié, coupe au logout, reconnexion auto. Un message NOTIFY (déclenché par les triggers SQL serveur) met à jour la table Dexie concernée → l'UI (qui observe Dexie en live) se rafraîchit toute seule.

## Auth

- `token-store.ts` — JWT access + refresh en `localStorage`, signal `isAuthenticated()`.
- `auth.interceptor.ts` — ajoute le `Bearer`, gère le 401 (refresh / redirect login).
- `client-id.interceptor.ts` + `client-id.ts` — identifiant device stable (pour le WS et l'anti-écho de ses propres writes).
- `auth.guard.ts` — protège les routes ; valide la session via `/me` au cold-start (évite le faux-négatif du 401 initial).

## Design system

60 composants standalone dans `designsystem/common_components/`, réplique des atoms/molecules Figma & Android. Familles : `Custom*` (Switch, RadioButton, TextField, Select, Checkbox, DatePickerDialog…), `drawer-*`, `*-bottom-sheet`, `action-icon*`, pickers (`wheel-picker`, `hms-wheel-picker`, `horizontal-number-picker`), graphes (`multi-line-chart`), barres de progression (`progress-bar-primitive`, `labeled-progress-bar`, `concentric-rings`, `progress-ring`), `titled-divider`, etc.

**Tokens** (`theme/_colors.scss`) — CSS custom properties, alignées sur la palette app (jamais de valeurs Material brutes) :
`--c-third-blue #091216` (fonds en creux), `--c-box-blue #1e2a3c` (surfaces), `--c-light-blue #4fc3f7`, `--c-gray-blue #5e78a0` (dividers), `--c-light-purple #6c2ae7`, `--c-orange-medium`/`--c-dark-orange`, `--c-turquoise #15bcab`, `--c-medium-green #008444`. Gouttière de page : `--page-gutter: clamp(20px,3vw,64px)`.

## Shell & navigation

- `app-shell.ts` — drawer en **rail (replié) par défaut** sur desktop pour gagner de la place ; bottom-nav avec bouton **Menu** (gauche) + **bascule de mode Sport/Nutrition** (droite). 4 sections de drawer (Général / Sport / Nutrition / Compte & paramètres).
- `nav-mode.ts` — accent de couleur et items de nav selon le mode courant (Nutrition → `--c-dark-orange`).
- `app.routes.ts` — 27 routes en `loadComponent` (lazy).

## Module Nutrition (le plus riche)

`features/nutrition/` — module MyFitnessPal-like personnalisé (cf. [`NUTRITION_DESIGN.md`](NUTRITION_DESIGN.md) pour les décisions produit D1-D12) :

- **Journal** (`nutrition-page.ts`) — master-detail : calendrier mensuel à anneaux concentriques (kcal/macros) à gauche, détail du jour (repas + aliments) à droite.
- **Catalogue** (`food-catalogue-page.ts`) — 2 colonnes (Récents+Favoris / Tous), recherche locale + Open Food Facts.
- **Stats** (`nutrition-stats-page.ts`) — grille de cartes par nutriment (kcal/G/L/P/fibres), graphe ligne + top aliments.
- **Objectifs** (`nutrition-goals-page.ts`) — cibles quotidiennes manuelles (macros-first → kcal dérivées via Atwater).
- **Recettes** (`recipes-page.ts`) + presets de repas + micronutriments (`micros.ts`, VNR EU).
- Repositories dédiés (`food`/`meal`/`recipe`/`nutrition-goal`.repository.ts) au-dessus des stores Dexie.

Données serveur : CIQUAL importé (~2298 aliments génériques) + Open Food Facts pour le scan.

## Build & déploiement

- **Build** : `npm run build` → `dist/appli-web/browser/`.
- **Prod** : FastAPI monte le SPA en catch-all à `/` (`_SpaStaticFiles`, fallback `index.html` pour le routing client), l'API reste sous `/api/v1`. `tailscale serve → uvicorn` sert web + API sur la même URL `https://<pi-fqdn>/`.
- **Auto-deploy** : `serveur/deploy.sh` fait `npm ci && npm run build` (étape web) entre Alembic et le restart `sportapi`. Déclenché par le webhook GitHub au push (Node ≥ 22 requis sur la Pi).

## Conventions

1. **Offline-first** : l'UI ne parle jamais à l'API directement pour lire — elle lit Dexie. Les écritures vont dans Dexie (flag unsynced) puis la sync pousse.
2. **Nommage wire** : snake_case côté serveur ↔ camelCase côté TS (mêmes alias que la politique cross-stack 17).
3. **Toute nouvelle entité syncable** = 1 model (Remote/Local) + 1 api + 1 store enregistré dans `SYNCABLE_STORES` + 1 bump de version Dexie.
4. **Tokens, pas de Material brut** : tout composant utilise les `Custom*` / la palette CSS (cohérent avec la politique widget Android).
5. **Zoneless** : pas de `setTimeout`/mutation hors signal pour déclencher un refresh — passer par signals/`computed`/flux Dexie.
