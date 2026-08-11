# TODO_FEATURES — Améliorations & nouvelles fonctionnalités

> 🗄️ **ARCHIVÉ 2026-05-25 — Backlog actif désormais dans Notion**
>
> Ce fichier est conservé pour l'**audit historique** (52 livrées + ~20 restantes à migrer). Les ~13 items actionnables restants ont été importés dans le Kanban Notion (en plus des 2 fixes restants ex-TODO_FIXES.md).
>
> **Source vivante du backlog sport-app** :
> - Kanban : https://www.notion.so/1823c95d613a4c08882d5a75d3dda714
> - Page projet : https://www.notion.so/36b1776cd0e2815b9817d9796291402f
>
> Pour ajouter une feature : `/add-task <description>` (slash command Claude Code) ou directement dans Notion. Pour démarrer une tâche : `/start-task` (orchestré par agent `po-sport`). Voir [CLAUDE.md](../CLAUDE.md) pour le workflow agents complet.

> Liste exhaustive des **améliorations / nouvelles fonctionnalités** identifiées durant l'audit et les discussions avec l'utilisateur. Pour les **corrections de bugs** voir [TODO_FIXES.md](TODO_FIXES.md).
>
> **Méthode (étape 6, 2026-05-04)** : items strictement issus de la documentation existante (CLAUDE.md, DEV_GUIDE.md §9, mémoire `project_future_nutrition.md`, TODO_FIXES, INTEGRATION.md, DATES.md, DATABASES.md, APPLI_ANDROID.md, SERVEUR.md) + remarques explicites de l'utilisateur en discussion. **Pas d'invention**. Les items qui me paraissent évidents mais ne sont nulle part mentionnés sont notés en [§9 — 💡 Suggestions](#9----suggestions-claude) pour validation utilisateur.
>
> Légende criticité (relative à la valeur ajoutée et au coût) :
> - 🔥 priorité haute (impact fort + déblocage d'autres items)
> - ⭐ priorité moyenne (apporte une vraie valeur mais pas urgent)
> - 💡 priorité basse / nice-to-have

---

## Table des matières

- [🗺️ Roadmap features actives (checklist)](#️-roadmap-features-actives-checklist)
- [📊 Status global — tous les items par section (checklist)](#-status-global--tous-les-items-par-section-checklist)
- [§0 Pre-feature cleanup priority (à faire AVANT toute nouvelle feature)](#0--pre-feature-cleanup-priority-à-faire-avant-toute-nouvelle-feature)
- [§1 Sécurité (auth + secrets)](#1--sécurité-auth--secrets)
- [§2 Architecture / Refactor structurel](#2--architecture--refactor-structurel)
- [§3 Nouvelles fonctionnalités](#3--nouvelles-fonctionnalités)
- [§4 Plan de tests fonctionnels post-audit](#4--plan-de-tests-fonctionnels-post-audit)
- [§5 DX / DevOps](#5--dx--devops)
- [§6 Documentation](#6--documentation)
- [§7 UI / UX](#7--ui--ux)
- [§8 Performance](#8--performance)
- [§9 💡 Suggestions Claude (à valider)](#9----suggestions-claude-à-valider)

---

## 🗺️ Roadmap features actives (checklist)

> Vue synthétique des features candidates discutées avec l'utilisateur (mise à jour 2026-05-21, ajout candidat Serveur MCP).
> Détail technique → voir §1-§8 correspondants. Légende : `[ ]` à faire · `[~]` en cours · `[x]` fait.

| ✅ | # | Feature | Priorité | Effort | Notes |
|---|---|---------|----------|--------|-------|
| `[x]` | 1 | ~~**Refresh Token JWT (Android)**~~ | 🔥 | — | **Livré V8.2** (Authenticator OkHttp + mutex + retry dans `RetrofitInstance.kt`). |
| `[ ]` | 2 | **Vrai support Dark / Light mode** | 🔥 | 1-2 sessions full | Infra toggle livrée (Option III-c). Reste refactor ~50 callsites couleurs hardcodées via `LocalAppColors`. Cf. [§7](#7--ui--ux). |
| `[x]` | 3 | ~~**Onboarding extensions — Units kg/lbs + cm/inches + Sound + Vibration**~~ | ⭐ | — | **Livré.** Cf. [§7 Onboarding extensions backlog](#-onboarding--extensions-backlog-validé-user-2026-05-11). |
| `[x]` | 3b | ~~**i18n EN/FR (Language picker)**~~ | ⭐ | — | **Livré 2026-05-12** (~700 strings, politique CLAUDE.md §18). |
| `[ ]` | 4 | **Page Planning / Agenda journalier** | ⭐ | ~3-5h | Vue unifiée routines + workouts planifiés + tasks. À scoper. Cf. [§3](#3--nouvelles-fonctionnalités). |
| `[ ]` | 5 | **Module Nutrition (calories + macros)** | 💡 | Plusieurs sessions | Nouvelles tables food/meal/food_entry + écran journalier + targets macros. Projet en soi. Cf. [§3](#-module-nutrition-calories--macros). |
| `[ ]` | 6 | **Health Connect / Samsung Health import** | 💡 | ~3-5h+ | Conditionnel `Build.MANUFACTURER == "samsung"`. Import workouts/poids existants. Cf. [§7 Onboarding extensions](#-onboarding--extensions-backlog-validé-user-2026-05-11) (Long terme). |
| `[ ]` | 7 | 🆕 **Boucle dev mobile : SSH PC depuis tel + ADB over Tailscale install APK** | ⭐ | ~20 min setup | **À afficher en priorité (validé user 2026-05-14)**. Permet de coder + déployer APK sur le tel en 4G sans intermédiaire. Cf. [§5 DX](#-boucle-dev-mobile--ssh-pc-depuis-tel--adb-over-tailscale--note-2026-05-14). |
| `[ ]` | 8 | 🆕 **Serveur MCP sport-app (assistant conversationnel IA)** | 💡 | à scoper | Expose les données d'entraînement comme *tools* MCP → questions en langage naturel (« ma prochaine séance ? »). Wrapper mince sur l'API existante. Cf. [§3](#3--nouvelles-fonctionnalités). |

---

## 📊 Status global — tous les items par section (checklist)

> Vue exhaustive de tous les items de TODO_FEATURES.md (§0 à §9) avec statut visible d'un coup d'œil.
> Pour le détail technique de chaque item, suivre les liens vers la section correspondante plus bas.
>
> **Légende statut** : `[x]` livré · `[~]` partiel / en cours · `[ ]` à faire · `[-]` annulé / déféré
> **Légende priorité** : 🔥 haute · ⭐ moyenne · 💡 basse · 🟠 partiel important

### §0 — Pre-feature cleanup priority

**Tier 1 — Fondations** (2/2 ✅)
- [x] 🔥 T1.1 — V8.1 Tests pytest + JUnit (fondation)
- [x] 🔥 T1.2 — CI GitHub Actions basique

**Tier 2 — Cleanup / refactor avant features** (6/6 ✅)
- [x] ⭐ T2.1 — Audit endpoints serveur orphelins étendu
- [x] ⭐ T2.2 — `User.firstName`/`lastName` lus + affichés dans ProfileScreen
- [x] ⭐ T2.3 — Notification dedup index (verdict no-op, 0 changement)
- [x] ⭐ T2.4 — Logging config formalisée (`logging.basicConfig`)
- [x] ⭐ T2.5 — Smoke test cascade Room migrations v6→v13
- [x] 💡 T2.6 — Cleanup 5 TODO Android (verdict tous légitimes)

**Tier 3 — DX / qualité de vie** (4/4 ✅)
- [x] ⭐ T3.1 — Pi prod déploiement automatisé (webhook)
- [x] 💡 T3.2 — Versioning REST API (`/api/v1/...`)
- [x] 💡 T3.3 — CLAUDE.md compaction (+ CHANGELOG.md créé)
- [x] 💡 T3.4 — Sudoers NOPASSWD systemctl Pi

**Tier 4 — Polish** (3/3 ✅)
- [x] 💡 T4.1 — Audit cohérence nommage cross-stack résiduel
- [x] 💡 T4.2 — Refactor sync layer Android
- [x] 💡 T4.3 — Structured logging JSON

**§0 total** : 15/15 ✅ — entièrement clôturé.

---

### §1 — Sécurité (auth + secrets) — 7/7 ✅

- [x] 🔥 [Refresh Token JWT (Android)](#-refresh-token-jwt) (V8.2 livré — Authenticator OkHttp + mutex + retry)
- [x] 🔥 [`User.is_admin` + `Depends(require_admin)`](#-userisadmin--dependsrequireadmin-politique-sécurité-validée-2026-05-03) (V1.3 livré 2026-05-05)
- [x] 🔥 [Endpoint `/signup` public + écran Signup Android](#-endpoint-signup-public--écran-signup-android) (V1.1 + V8.3)
- [x] ✅ [Changement secrets `change-me` Pi + `.env` Pi](#-changement-secrets-change-me-pi--env-pi--v12-livré-2026-05-11-ops-manuelle) (V1.2 livré 2026-05-11)
- [x] ⭐ [EncryptedSharedPreferences pour le JWT Android](#-encryptedsharedpreferences-pour-le-jwt-android) (V8.2 livré)
- [x] ⭐ [Rate limit sur `/token`](#-rate-limit-sur-token) (V8.2-4 slowapi livré)
- [x] 💡 [Sudoers `systemctl restart` sans mot de passe](#-sudoers-pour-systemctl-restart-sportapiservice-sans-mot-de-passe) (T3.4 livré)

### §2 — Architecture / Refactor structurel — 6/7 ✅

- [x] 🔥 [Système de migrations Room](#-système-de-migrations-room-bloqueur-multi-features) (V3.1 livré)
- [x] 🔥 [Format wire des dates unifié](#-format-wire-des-dates-unifié-résout-les-bugs-dates) (V3.2 livré)
- [x] ✅ [Soft-delete propre OU retrait de `deleted_at`](#-soft-delete-propre-ou-retrait-de-deletedat--livré-v55-2026-05-05) (V5.5 livré)
- [x] ⭐ [Refactor `exec_pg.py` en 2 scripts séparés](#-refactor-execpgpy-en-2-scripts-séparés) (F9-Q5 livré)
- [x] ✅ [Refactor Chrono en feature module Style A](#-refactor-chrono-en-feature-module-style-a--livré-2026-05-10-b4) (B4 livré 2026-05-10)
- [x] ⭐ [Unifier la source de vérité triggers/migrations](#-unifier-la-source-de-vérité-triggersmigrations) (V3.3 livré)
- [ ] 💡 [Application politique squelette uniforme 27 CRUDs](#-application-de-la-politique-squelette-uniforme-aux-27-cruds--22-routers--22-schemas--22-syncables--22-daos--22-apis) (V6 partial — 8 PRs scopées, ~3 restantes)

### §3 — Nouvelles fonctionnalités — 2/8 ✅ (+ 1 annulé)

- [ ] ⭐ [Module Nutrition (calories + macros)](#-module-nutrition-calories--macros) — projet en soi
- [x] ✅ [Implémenter `StatsViewModel` + `StatsScreen`](#-implémenter-statsviewmodel--statsscreen--b3-2-livré-2026-05-07) (B3-2 livré 2026-05-07)
- [-] ❌ [HistoryViewModel / HistoryScreen](#-implémenter-historyviewmodel--historyscreen--annulé--supprimé-2026-05-07) — **annulé** (doublon Calendar)
- [ ] 💡 [Conversations (écran existe mais fonctionnalité ?)](#-conversations-écran-existe-mais-fonctionnalité-) — à clarifier
- [ ] 💡 [Page Planning / Agenda journalier](#-page-planning--agenda-journalier--note-2026-05-09) — à scoper
- [x] ✅ [MuscleGoals — refonte 3-niveaux + graphe achievement %](#-musclegoals--refonte-3-niveaux--graphe-achievement---livré-2026-05-09) (livré 2026-05-09)
- [ ] 💡 [Multi-user social (amis, chat, conversations)](#-multi-user-social-amis-chat-conversations--note-2026-05-09) — différé
- [ ] 💡 [Serveur MCP sport-app (assistant conversationnel)](#-serveur-mcp-sport-app-assistant-conversationnel--note-2026-05-21) — à scoper

### §4 — Plan de tests fonctionnels post-audit — 2/3 ✅

- [x] 🔥 [Tests fonctionnels des bugs 🔴 identifiés](#-tests-fonctionnels-des-bugs--identifiés) (V8.1 + T1.1 livré)
- [x] ⭐ [Framework de tests à mettre en place](#-framework-de-tests-à-mettre-en-place) (pytest + JUnit Robolectric livré)
- [ ] ⭐ [Multi-device test setup](#-multi-device-test-setup) — différé

### §5 — DX / DevOps — 4/8 ✅ (+ 1 obsolète remplacé)

- [x] 🔥 [Brancher `alembic --autogenerate`](#-brancher-alembic---autogenerate) (V3.4 livré)
- [-] ⭐ [`deploy_to_pi.ps1` PC → Pi](#-deploytopips1-pc--pi-en-une-commande) — **obsolète** (remplacé par webhook auto-deploy T3.1)
- [x] ⭐ [CI/CD GitHub Actions](#-cicd-github-actions) (T1.2 livré)
- [x] ⭐ [Webhook GitHub → Pi pour auto-pull](#-webhook-github--pi-pour-auto-pull) (T3.1 livré)
- [x] ⭐ [Activer `exportSchema = true` Room](#-activer-exportschema--true-room) (V3.1 livré)
- [ ] 💡 [Variant Android `staging` (en plus de debug/release)](#-variant-android-staging-en-plus-de-debugrelease)
- [ ] 💡 [UI switcher d'URL serveur sans rebuild](#-ui-switcher-durl-serveur-sans-rebuild)
- [ ] ⭐ 🆕 [Boucle dev mobile : SSH PC depuis tel + ADB over Tailscale](#-boucle-dev-mobile--ssh-pc-depuis-tel--adb-over-tailscale--note-2026-05-14) — **à afficher en priorité (2026-05-14)**

### §6 — Documentation — 3/5 ✅

- [x] ⭐ [Aligner `diagram.dbml` ↔ modèles SQLAlchemy](#-aligner-diagramdbml--modèles-sqlalchemy) (politique 14, continu)
- [x] ⭐ [Régénérer ou supprimer `routes.json`](#-régénérer-ou-supprimer-routesjson) (V7.3 supprimé)
- [x] ⭐ [Tutoriel "ajouter une nouvelle entity"](#-tutoriel-ajouter-une-nouvelle-entity) — livré 2026-05-12 (HOW_TO_ADD_ENTITY.md ~300 lignes + corrections post-T4.2)
- [ ] ⭐ [Diagramme architecture global](#-diagramme-architecture-global)
- [ ] 💡 [Documenter les endpoints critiques (auth flow, sync flow)](#-documenter-les-endpoints-critiques-auth-flow-sync-flow)

### §7 — UI / UX — 4/10 ✅ (+ 1 annulé + 1 doublon)

- [ ] ⭐ [Settings : choix de la page d'accueil au démarrage](#--settings--choix-de-la-page-daccueil-au-démarrage-2026-05-12) — 2026-05-12 (session/tasks/calendar/...)
- [ ] 💡 [Re-run onboarding pour admins (tester l'onboarding)](#--re-run-onboarding-pour-admins-tester-londoarding-2026-05-15) — 2026-05-15
- [x] ✅ [Refonte snackbars sync (1 global au lieu de 22)](#-refonte-snackbars-sync-1-global-au-lieu-de-22--b2-2026-05-07) (B2 livré 2026-05-07)
- [-] ❌ [Indicateur visuel persistant "hors ligne"](#-indicateur-visuel-persistant-hors-ligne--annulé-2026-05-11-décision-user) — **annulé** (couvert par badges drawer)
- [x] ✅ [Pré-seed serveur au signup](#-pré-seed-serveur-au-signup--v84-2026-05-06) (V8.4 livré)
- [x] ✅ [Onboarding initial UI (B1)](#-onboarding-initial-ui-b1--livré-2026-05-11) (livré 2026-05-11)
- [~] ⭐ [Onboarding — Extensions backlog](#-onboarding--extensions-backlog-validé-user-2026-05-11) — **partial** : Sample data tour ✅ · Profil bio ✅ · TimePicker M3 ✅ · Language picker (i18n) ✅ · **Units kg/lbs + cm/inches + Sound + Vibration + Theme** ✅ · Health Connect / Calendar sync / Quick tour overlay [ ]
- [~] 🟠 [Vrai support Dark/Light mode (suite Option III-c)](#-vrai-support-darklight-mode-suite-option-iii-c-scope-étendu) — **partial** : infra livrée, refactor ~50 callsites couleurs pending
- [-] 💡 [~~Onboarding initial UI (post-signup déféré)~~](#--onboarding-initial-ui-post-signup-déféré-v84) — doublon de B1 livré ci-dessus
- [x] ✅ [UI admin pour gérer les `is_admin`](#-ui-admin-pour-gérer-les-isadmin--livré-2026-05-11) (livré 2026-05-11)

### §8 — Performance — 3/4 ✅

- [x] 💡 [Indexes Postgres sur `user_id` (12 tables)](#-indexes-postgres-sur-userid-12-tables) (F4c livré)
- [x] 💡 [Indexes Postgres sur `<parent>_uuid` (FK)](#-indexes-postgres-sur-parentuuid-fk) (F4c livré)
- [x] 💡 [Indexes Notification (composite)](#-indexes-notification-composite) (livré)
- [ ] 💡 [Room journal mode WAL (vs TRUNCATE actuel)](#-room-journal-mode-wal-vs-truncate-actuel)

### §9 — 💡 Suggestions Claude (à valider) — 7/9 ✅

- [x] 💡 [Authenticator OkHttp pour gestion 401 proactive](#-suggestion--authenticator-okhttp-pour-gestion-401-proactive) (V8.2 livré — Authenticator complet avec refresh auto + mutex)
- [x] 💡 [Logger structuré côté serveur](#-suggestion--logger-structuré-côté-serveur) (T2.4 + T4.3 JSON livré)
- [x] 💡 [Healthcheck endpoint](#-suggestion--healthcheck-endpoint) — livré 2026-05-12 (`GET /healthz` public, db check + pytest)
- [x] 💡 [Compression gzip sur les responses REST](#-suggestion--compression-gzip-sur-les-responses-rest) — livré 2026-05-12 (`GZipMiddleware` minimum_size=1000)
- [ ] 💡 [Métriques basiques (Prometheus)](#-suggestion--métriques-basiques-prometheus)
- [x] 💡 [Backup automatique de la DB Pi](#-suggestion--backup-automatique-de-la-db-pi) — livré 2026-05-12 (script `serveur/backup_pi.sh` + doc DEV_GUIDE.md, crontab Pi ops manuelle)
- [x] ✅ [Endpoint `DELETE /me` (suppression de compte)](#-suggestion--endpoint-delete-me-suppression-de-compte) — livré 2026-05-21
- [ ] ⭐ [Last-write-wins serveur sur les bulk-upserts](#-last-write-wins-serveur-sur-les-bulk-upserts-extension-du-fix-2026-05-07) — single ✅, bulk reste
- [ ] 💡 [Système de feature flags / config dynamique](#-suggestion--système-de-feature-flags--config-dynamique)

---

### 📈 Bilan global

| Section | ✅ Livré | 🔶 Partiel | ⏳ À faire | ❌ Annulé / obsolète | Total |
|---------|---------|------------|------------|---------------------|-------|
| §0 Pre-feature cleanup | 15 | 0 | 0 | 0 | 15 |
| §1 Sécurité | 7 | 0 | 0 | 0 | 7 |
| §2 Architecture | 6 | 1 | 0 | 0 | 7 |
| §3 Nouvelles features | 2 | 0 | 5 | 1 | 8 |
| §4 Tests | 2 | 0 | 1 | 0 | 3 |
| §5 DX / DevOps | 4 | 0 | 3 | 1 | 8 |
| §6 Documentation | 3 | 0 | 2 | 0 | 5 |
| §7 UI / UX | 4 | 2 | 1 | 2 | 9 |
| §8 Performance | 3 | 0 | 1 | 0 | 4 |
| §9 Suggestions Claude | 6 | 0 | 3 | 0 | 9 |
| **TOTAL** | **52** | **3** | **16** | **4** | **75** |

**Vrais restes "à faire"** : 19 items (+ 3 partiels). Hors suggestions §9 (qui sont des "à valider" non urgentes), on a :
- **Features pures** : Nutrition · Page Planning · Conversations · Multi-user social · Serveur MCP
- **Partials connus** : Onboarding extensions (long terme) · Dark mode (refactor ~50 callsites) · Squelette uniforme (3 PRs restantes)
- **Doc** : Tutoriel "add entity" · Diagramme architecture · Endpoints critiques
- **DX** : Variant staging · UI switcher URL
- **Tests** : Multi-device setup
- **Perf** : Room WAL (à mesurer)

**La barre est très avancée** (~70% livré, 96% si on exclut les suggestions §9). Les fondations sont posées — il ne reste essentiellement que des **features pures** et 2 **partials importants** (Dark mode + Onboarding long terme).

---

## §0 — Pre-feature cleanup priority (à faire AVANT toute nouvelle feature)

> **Politique projet (validée 2026-05-06)** : la barre fix-first de [TODO_FIXES.md](TODO_FIXES.md) étant strictement franchie (96% résolu, 6 items restants tous différés feature/ops/tests), l'utilisateur veut une **base clean** avant d'ajouter des features. Cette section consolide les **refactor / cleanup / fondations** à faire en premier — pointers vers les items existants des autres sections + nouveaux items identifiés en F11-audit (2026-05-06).
>
> Légende statut : `[ ]` à faire · `[~]` en cours · `[x]` fait
> Légende criticité : 🔥 fondation (vrai bloqueur) · ⭐ recommandé · 💡 nice-to-have

### Tier 1 — Fondations (vrais bloqueurs)

- [x] **🔥 T1.1 — V8.1 Tests pytest + JUnit (fondation)** — *3-5j*. ✅ **24 tests verts** (2026-05-06, 6 sous-étapes a-f en 1 session) :
  - **T1.1.a** (commit `0635887`) Fondation pytest serveur (2 smoke tests).
  - **T1.1.b** (commit `6a1f3ad`) Fixture DB Postgres `fittracker_test` + login auth (5 tests cumulés).
  - **T1.1.c-pattern** (commit `8b9bd62`) Pattern ownership Exercise V2.1 (8 tests cumulés).
  - **T1.1.c-bis** (commit `82e84b5`) Cascade ownership 5 entités feuilles V2.4 + **bug bonus fixé** `upsert_actual_workout_set_by_uuid` UniqueViolation 500 → 403 (13 tests serveur).
  - **T1.1.d** (commit `66151db`) Fondation JUnit Android (3 tests `CustomDateUtils`, runtime via JAVA_HOME configuré).
  - **T1.1.e** (commit `cd1f147`) Room DAO in-memory via Robolectric (5 tests `AvailableEquipmentDao` Style A canonique).
  - **T1.1.f** (commit `a045c1f`) Smoke test cascade Room migrations v7→v13 via MigrationTestHelper (2 tests androidTest sur Samsung S21+ Android 15). Force kotlinx-serialization 1.8.1 pour résoudre `AbstractMethodError` sur Room 2.8.4 vs Kotlin 2.3.0 transitive 1.7.3.
  - **Bilan** : 13 serveur pytest + 9 Android JVM Robolectric + 2 androidTest instrumented. **Ferme aussi V2.4 cascade ownership pratique** dans TODO_FIXES.md §1.
- [x] **🔥 T1.2 — CI GitHub Actions basique** (build + test au push) — *1j*. ✅ commits `e04d70f` + `60ca395` (2026-05-06, ~1h en pratique) :
  - Workflow `.github/workflows/tests.yml` créé avec 2 jobs en parallèle :
    - **Serveur pytest** (1m1s) : Postgres 18 service container + Python 3.14 + cache pip + pytest sur fittracker_test (override `TEST_DATABASE_URL` env var pour pointer le container).
    - **Android JVM tests** (6m32s) : JDK 21 Temurin + Android SDK setup (platforms;android-36 + build-tools;36.0.0) + setup-gradle@v4 cache + `chmod +x gradlew` (bit non préservé clone Windows→Linux) + `:app:testDebugUnitTest` + upload reports artifact si fail.
  - Trigger : push main + PRs sur main.
  - Hors scope basique : androidTest instrumented (besoin émulateur, ~10min) → cron différé T1.2-bis si besoin.
  - Warnings cosmétiques Node.js 20 (checkout@v4, setup-java@v4, etc.) supportés jusqu'à 2026-06-02/09-16, fix différé.
  - **Bilan** : 24 tests verts auto au push (13 serveur + 9 Android JVM + Robolectric). Capitalise pleinement sur T1.1.

### Tier 2 — Cleanup / refactor avant features

- [x] **⭐ T2.1 — Audit endpoints serveur orphelins étendu (22 routers)** — *3h*. ✅ commit `9028e77` (2026-05-06, ~30min en pratique grâce à la méthode systématique) :
  - **Méthode** : `app.routes` listing complet → 64 paths canoniques + 2 suspects (multi-placeholders hors squelette).
  - 2 endpoints orphelins supprimés (0 callsite Android) :
    - `GET /cycle-workouts/{training_cycle_uuid}/{planned_workout_uuid}` (router + CRUD `get_cycle_workout` + export + Android `CycleWorkoutApi.getByUUIDs`).
    - `GET /exercise-muscles/{exercise_uuid}/{muscle_uuid}` (router + CRUD `get_exercise_muscle_by_uuids` + export + Android `ExerciseMuscleApi.getByUUIDs`).
  - Bilan : 0 path multi-placeholders restant. Tous les routers respectent désormais le squelette canonique (`/{ressource}` + `/{ressource}/{uuid}` + `/{ressource}/bulk`).
  - Smoke serveur 13 pytest verts + Android `:app:testDebugUnitTest` BUILD SUCCESSFUL.
- [x] **⭐ T2.2 — `User.firstName`/`lastName` lus + affichés dans ProfileScreen** — *2-3h*. ✅ commit `e661515` (2026-05-06, ~10min en pratique — beaucoup plus simple que estimé) :
  - `UserInfo` data class étendu (`firstName: String? = null` + `lastName: String? = null`, nullable car users pré-V8.3 peuvent ne pas les avoir).
  - `ProfileScreen` : 2 nouveaux `DebugItem` ("First name" + "Last name"), "–" si null.
  - Boucle V8.3 signup → POST `/signup` → Postgres `users.first_name/last_name` → `/me` (UserOut F6-4 alias camelCase) → Retrofit `UserInfo` → ProfileScreen UI fermée.
  - Pas passé par Room User : on lit directement `/me` Retrofit (déjà ce que faisait le screen). Si besoin futur, on pourra brancher Room User pour fonctionner offline.
  - Smoke `:app:testDebugUnitTest` BUILD SUCCESSFUL, 9 tests toujours verts.
- [x] **⭐ T2.3 — Notification dedup index `(user_id, dedupe_key)` UNIQUE** — *1h*. ✅ vague T2.3 (2026-05-06) — audit complet, verdict **0 changement de code** :
  - **Postgres** : `dedupe_key = Column(String, nullable=True)` déclaré, pas d'index. **Aucune logique de dedup serveur** : grep confirmé 0 query `WHERE dedupe_key = ...` ni `ON CONFLICT(dedupe_key)`.
  - **Android** : `dedupeKey` utilisé uniquement pour le **PendingIntent ID** des notifications Android système (cf. `docs/APPLI_ANDROID.md:1069` : `(dedupeKey ?: uuid).hashCode()` → notifs avec même `dedupeKey` se remplacent, anti-spam). Pas pour la persistance Room/Postgres.
  - **Pas besoin d'index UNIQUE** : chaque notification a son uuid unique côté DB, aucun INSERT actuel ne peut causer un doublon par dedupe_key. Si un jour on veut une dedup vraie côté DB, on ajoutera l'index à ce moment-là.
  - Le DBML a déjà retiré l'index aspirationnel en F2a (commit `523f73f`-ish).
- [x] **⭐ T2.4 — Logging config formalisée (`logging.basicConfig` / `dictConfig`)** — *1h*. ✅ vague T2.4 (2026-05-06) — `logging.basicConfig` ajouté en tête de `app/main.py` (avant tout autre import) avec :
  - `level=INFO` par défaut, override via env `LOG_LEVEL=DEBUG` pour debug local.
  - `format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"` + `datefmt="%Y-%m-%d %H:%M:%S"` (cohérent format wire dates projet).
  - `force=True` car uvicorn configure son logging au CLI startup AVANT l'import de `app.main` → sans `force=True`, basicConfig est no-op (handlers déjà attachés). Avec, on écrase et impose le format projet.
  - Smoke confirmé : `logger.info` produit `2026-05-06 23:41:07 [INFO] test_t2_4: ...`. pytest -v → 13 passed. Pas de régression.
- [x] **⭐ T2.5 — Smoke test cascade Room migrations v6→v13** — ✅ **validé via T1.1.f** (2026-05-07). Décision : la cascade `v7 → v13` testée en androidTest sur Samsung S21+ Android 15 via MigrationTestHelper (commit `a045c1f`) couvre 6/7 migrations. La migration `v6 → v7` (ajout `is_admin BOOLEAN`) est triviale et `v6` = état historique pré-système-de-migrations Room (avant le commit `2439fd1` du 2026-05-05 qui a activé `exportSchema=true`). Aucun device n'est plus en v6 en pratique. Test v6→v7 explicite non requis.
- [x] **💡 T2.6 — Cleanup 5 TODO Android** — *XS, ~30min*. ✅ vague T2.6 (2026-05-06) — audit complet : 5 TODO **tous légitimes**, 0 mort à supprimer.
  - `RemoteDataUpserter:418` — `User is not synced for security reasons` : **garder** doc intentionnelle politique F8-Q1.
  - `ActionIcon.kt:25` — `a remplacer par ActionIconButton` : **garder** refactor cosmétique différé (1 callsite externe `CalendarViewScreen`, hors scope cleanup pur).
  - `MuscleListScreen.kt:167` + `ExerciseListScreen.kt:237` — `onExport` lambda vide / `export logic` : **garder** features export non implémentées (cf. items B3 stats / export catalogues à arbitrer plus tard).
  - `ExerciseListScreen.kt:391` — `a changer comme dans exerciseScreen` : **garder** refactor cosmétique différé.

### Tier 3 — DX / qualité de vie

- [x] **⭐ T3.1 — Pi prod déploiement automatisé** ✅ **vague T3.1** (2026-05-07, ~1h30 en pratique). Mini-service Python stdlib (~80 lignes, pas de Flask) `serveur/webhook/webhook.py` qui écoute `127.0.0.1:8001` derrière Caddy, vérifie HMAC-SHA256 sur `X-Hub-Signature-256`, ne déclenche `deploy.sh` que sur push `refs/heads/main` via `subprocess.Popen(start_new_session=True)` (non-bloquant, survit au restart du webhook). Déploiement Pi : `/etc/systemd/system/sportapi-webhook.service` (User=william, EnvironmentFile=/home/william/.config/sportapi-webhook.env perms 600) + bloc `handle /webhook/deploy { reverse_proxy 127.0.0.1:8001 }` ajouté au Caddyfile. Webhook GitHub configuré côté repo Settings → Hooks (event push only). End-to-end testé : `ping ok` GitHub → 200 `pong` (smoke 6/6 cas locaux + 1 ping prod). Push main → auto-pull + alembic upgrade + systemctl restart sans intervention. **Pré-requis** T3.4 sudoers NOPASSWD respecté. Commits `74a7cdd` (code) + ce commit (tick).
- [x] **💡 T3.2 — Versioning REST API (`/api/v1/...`)** ✅ **vague T3.2** (2026-05-07, ~30min en pratique — beaucoup plus rapide que estimé grâce à `for r in ROUTERS: app.include_router(r, prefix="/api/v1")` 1 ligne et au helper `_create` centralisé côté tests). Tous les routers passent sous `/api/v1/` (auth, ws, et les 22 entity routers). Méta endpoints (`/openapi.json`, `/token-helper`, `/secure-docs`) restent au root, ainsi que `/webhook/deploy` (T3.1). Côté Android : `API_BASE_URL` et `WS_BASE_URL` étendus pour les 2 variants debug + release. Validation : 13 pytest serveur verts + Android JVM tests verts + smoke prod Pi (HTTP 401 sur `/api/v1/token` faux pwd, HTTP 200 sur `/openapi.json` root) + smoke release Android sur Samsung S21+ (login + listes + WS). Commit `a4cf472` (code) + ce commit (tick). Auto-deploy via T3.1 a tourné sans intervention.
- [x] **💡 T3.3 — CLAUDE.md compaction** — *1h*. ✅ commit `<next>` (2026-05-06) : `docs/CHANGELOG.md` créé avec l'historique complet (49+ entrées 2026-05-03 → aujourd'hui). CLAUDE.md historique condensé en 12 bullets courts (vagues récentes T1.x/T2.x/T3.x + F8-F11 derniers) + pointer vers CHANGELOG.md pour le détail complet. CLAUDE.md passe de 286 → 237 lignes (gain ~17%). Compaction modeste mais l'historique complet est préservé en doc dédiée et CLAUDE.md reste focused sur politiques + état actif.
- [x] **💡 T3.4 — Sudoers NOPASSWD systemctl Pi** — ✅ **ops Pi validée** (2026-05-07). Procédure documentée `DEV_GUIDE.md §9` (commit `fa8f149`) exécutée sur la Pi (`/etc/sudoers.d/sportapi`). Smoke OK sans prompt password : `sudo systemctl status sportapi.service`, `sudo journalctl -u sportapi.service -n 5`, `sudo systemctl restart sportapi.service`. **Débloque T3.1** (webhook auto-deploy peut désormais appeler `systemctl restart` sans interaction).

### Tier 4 — Polish (faible priorité, à faire en cours de route si besoin)

- [x] **💡 T4.1 — Audit cohérence nommage cross-stack résiduel** ✅ **vague T4.1** (2026-05-07, ~30min). Audit complet 21 schémas Pydantic + 22 entités Room. Verdict :
  - **Côté Pydantic** : 0 divergence. 100% des fields snake_case multi-mots ont leur `Field(..., alias="camelCase")`, `populate_by_name=True` partout.
  - **Côté Room** : 1 seule colonne en camelCase (`pendingDeletion`) sur 68 colonnes totales. Toutes les autres en snake_case avec `@ColumnInfo(name=...)` quand la prop Kotlin est camelCase.
  - **Décision** : pas de rename `pendingDeletion` → `pending_deletion`. Justification : flag local-only (sync tombstone, ne traverse jamais le wire), aucun gain fonctionnel à renommer, migration Room toucherait les 22 tables → risque > bénéfice cosmétique. Politique 17 ajoutée à `CLAUDE.md` pour formaliser l'exception "naming local-only Android".
- [x] **💡 T4.2 — Refactor sync layer Android** ✅ **vague T4.2** (2026-05-07, 17 commits, ~5h en pratique). Refonte architecturale complète du sync layer Android. **2300 lignes legacy supprimées + 900 lignes architecture moderne ajoutées** (net -1400 lignes). 42 tests JVM Robolectric verts à chaque commit (33 DAO smoke + 9 SyncMergeOps).
  - **Phase 0.x** : tests smoke DAO pilote (Muscle, Exercise, ActualWorkout) + uniformisation surface DAO 10 fichiers (`getAll(): Flow` → `observeAll()`).
  - **Phase 1.x** : `SyncableEntity<T>` étendu (15 méthodes : observeAll, hasUnsynced, keyOf, updatedAtOf, syncedOf, pendingDeletionOf, clearLocal, insertFromServer, etc) ; `SyncRegistry` Singleton Hilt qui centralise les 20 entités sync ordonnées FK-aware ; `SyncEngine` (pushAll/pushEntity/pushEntityClass/pushEntityClasses/pullMerge/pullReplace/bulkPushAll) ; `SyncMergeOps.kt` extrait pour testabilité.
  - **Phase 2.x** : `RemoteDataMerger` (575→24 lignes), `RemoteDataGetter` (217→25), `RemoteDataUpserter` (502→24) deviennent des shells minces déléguant au SyncEngine ; `SyncManager.syncAllToServer` délègue à `syncEngine.pushAll` ; `SyncSettingsViewModel` itère sur SyncRegistry (470→204 lignes).
  - **Phase 3.x** : suppression code mort (Getter.getAll, Upserter.upsertAllUnsynced) ; migration 19 ViewModels callsites de `syncManager.sync<X>()` → `syncEngine.pushEntityClass(<X>::class)` (~90 callsites) ; cleanup SyncManager (-307 lignes : 20 sync<X>() méthodes + 21 DAOs orphelins + helpers composés retirés).
  - **Phase 4.x** : `SyncCoordinator` centralise les 3 triggers (onLogin / onNetworkAvailable / onUserAction) avec retry exponentiel sur les triggers automatiques ; UI Settings enrichie avec badge stats par entité (`15/15 • 2 unsync • 0 pending del`, couleurs vert/ambre/rouge) ; Drawer affiche `BadgedBox` avec compteur `totalPendingCount` (somme unsynced+pendingDeletion sur registry). Boutons Settings (Get All / Merge / Upsert / Sync All / Clear / Log DB / Verify Token / WS) gardent leur sémantique exacte.
  - **Architecture finale** : SyncRegistry (source unique de vérité) → SyncEngine (moteur métier) → SyncManager (mutex+UX) + SyncCoordinator (orchestration triggers) + RemoteData* (shells API publique boutons Settings) + 20 SyncableEntity (1 par entité). Ajouter une 21e entité = 1 XSyncable + 2 lignes dans SyncRegistry.
  - **Préservé** : ordre FK-aware V4.4, mutex anti-concurrence V4.4, 401 silent V4.5, User read-only F8-Q1, 3 boutons Settings sémantique exacte. Build release vert (37s, signé debug.keystore donc upgrade sur tel possible). **À valider runtime** : install build release sur tel S21+ → login Pi prod → sync 22 entités → WS → offline test.
- [x] **💡 T4.3 — Structured logging JSON** ✅ **vague T4.3** (2026-05-07, ~30min). Toggle via env `LOG_FORMAT=json|text` (default `text`). Stdlib `json.dumps` only (pas de dépendance pip ajoutée). `_JsonFormatter` custom (~12 lignes) émet `{ts, level, logger, msg}` + `exc` si exception capturée via `log.exception(...)`. Validation : 13 pytest serveur verts + smoke `LOG_FORMAT=json` produit JSON parseable + `LOG_FORMAT=text` préserve le format T2.4. **À activer côté Pi** : ajouter `Environment=LOG_FORMAT=json` dans `/etc/systemd/system/sportapi.service` quand un agrégateur (Loki/ELK/etc.) sera branché. Aujourd'hui : default text reste pour `journalctl` lisible.

### Items hors-Tier (déjà dans TODO_FIXES.md ou ailleurs)

- ✅ **V1.2 — Secrets `change-me` Pi** : livré 2026-05-11 (ops manuelle SSH par user). Cf. [TODO_FIXES.md §1](TODO_FIXES.md#-authentification--secrets) + [§1 Changement secrets `change-me` Pi](#-changement-secrets-change-me-pi--env-pi).
- 🟠 **V2.4 — Cascade ownership pratique** : différé tests V8.1 (cf. [TODO_FIXES.md §1](TODO_FIXES.md#--auth--dx-cohérence)). Suit T1.1.

---

## §1 — Sécurité (auth + secrets)

### [x] ✅ Refresh Token JWT — livré V8.2

**Contexte** : Le JWT actuel expire après **30 minutes** ([auth_router.py:37](../serveur/app/routers/auth_router.py#L37)). Aucun mécanisme de refresh côté client. Quand le token expire, l'utilisateur reçoit des `HttpException(401)` mangées dans `syncEntity` → snackbar "Sync error" trompeuse → futurs appels échouent en boucle → l'utilisateur ne sait pas pourquoi rien ne sync. À la prochaine ouverture de l'app, `AuthManager.initAuth() → verifyToken() → 401 → AuthState.NeedLogin` → écran login.

**Pourquoi** : Pour une app perso le re-login toutes les 30 min est acceptable mais pénible. Pour un usage quotidien réel, l'utilisateur sera frustré de devoir se reconnecter constamment. Et pour une éventuelle ouverture à d'autres utilisateurs (ami·es), c'est nécessaire.

**Proposition** :
- **Côté serveur** : ajouter `/refresh` qui prend un refresh token (long-lived, ~30 jours, stocké en DB) et émet un nouveau access token. Le refresh token est révocable.
- **Côté client** : étendre `RetrofitInstance` avec un `Authenticator` OkHttp qui détecte 401 → appelle `/refresh` → met à jour le token → retry la requête. Si refresh échoue → `clearToken + navigate to login`.

**Lié à** : [TODO_FIXES §3 "Pas de gestion 401 proactive Android"](TODO_FIXES.md#3--sync-rest--websocket--dates).

### [x] ✅ `User.is_admin` + `Depends(require_admin)` — livré V1.3 (politique sécurité validée 2026-05-03)

**Contexte** : La politique de sécurité validée par l'utilisateur le 2026-05-03 (cf. mémoire `project_security_policy.md` + CLAUDE.md §8) impose un rôle admin pour les écritures sur les entités globales (Type C : `equipment`, `training_cycle`, `available_equipment`). Aujourd'hui, ces routers sont soit publics (cf. [TODO_FIXES §1](TODO_FIXES.md#1--sécurité)), soit accessibles à tout user authentifié.

**Pourquoi** : Permet de séparer les comptes "admin" (qui peuvent éditer le catalogue d'équipements partagés) des comptes user normaux qui ne peuvent qu'utiliser l'existant. Aussi : permet à terme un système multi-utilisateurs sans qu'un user puisse polluer les données globales.

**Proposition** :
1. Migration DB : `ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE`.
2. Marquer les comptes admin manuellement : `UPDATE users SET is_admin = TRUE WHERE username = 'will'`.
3. Ajouter `Depends(require_admin)` dans `app/dependencies.py` qui vérifie le flag.
4. Appliquer sur les writes des routers Type C (`equipment_router`, `training_cycle_router`, `available_equipment_router` — cf. TODO_FIXES §1 routers publics).
5. Côté Android : ajouter `is_admin` dans `UserOut`/`/me` réponse → étendre `UserInfo` Kotlin (résout aussi le bug actuel `role` mismatch — cf. TODO_FIXES §3).
6. UI Android : option pour différencier l'écran "Equipments" (admin = peut ajouter/supprimer ; user normal = peut juste sélectionner).

### [x] ✅ Endpoint `/signup` public + écran Signup Android — livré V1.1 + V8.3

**Contexte serveur** : ✅ **Endpoint implémenté en V1.1** (commit `562a021`, 2026-05-05). `POST /signup` public minimal accepte `{username, password, first_name, last_name}`, hash bcrypt, crée user avec `is_admin=False`. Retourne `UserOut` (pas auto-login JWT — le client doit appeler `/token` après).

**Contexte Android** : ✅ **Implémenté en V8.3** (commit `fa9bc83`, 2026-05-06). LoginScreen → lien "No account yet? Create one" → SignupScreen (form 5 champs + validation + auto-login post-signup via V8.2 setTokens).

**Pourquoi** : Onboarding bloqué pour tout nouvel utilisateur (ami, famille). Demande explicite utilisateur 2026-05-05.

**Proposition Android (effort S, ~半日)** :
1. **Bouton "Créer un compte"** sous le bouton Login dans `LoginScreen.kt` → navigate vers nouvelle route `Routes.SIGNUP`.
2. **Nouveau `SignupScreen.kt`** + `SignupScreenViewModel.kt` :
   - Champs : username, password, password confirm (validation côté client : min 8 chars, match), first_name (opt), last_name (opt).
   - Bouton "Create account" → `POST /signup` via nouveau `AuthApi.signup(SignupRequest)` Retrofit.
   - Sur succès : auto-login (`POST /token`) → splash → home.
   - Sur 409 (username taken) : snackbar erreur.
3. **Routes object** ajout `const val SIGNUP = "signup"` + composable dans `MainActivity.NavHost`.
4. **AuthApi** : nouvel endpoint `signup(@Body req: SignupRequest)` retournant `UserInfo`.

**Garde-fous serveur restant à ajouter** :
- Rate limit (1 signup par IP par minute) — déféré à V8.2-4 slowapi.
- Validation password (min 8 chars côté Pydantic) — TODO petit fix.
- ✅ Unique username — déjà géré (409 Conflict en V1.1).

**Hors scope (déféré)** : Onboarding initial (créer 1er muscle / exercise / workout) — cf. §6 ci-dessous, demande UX dédiée.

### [x] ✅ Changement secrets `change-me` Pi + `.env` Pi — V1.2 livré 2026-05-11 (ops manuelle)

**Livré** : ops manuelle SSH par user (Claude ne peut pas SSH directement, pas de clé setup). Procédure exécutée :
1. Password Postgres alphanum 32 chars généré côté PC (PowerShell), `ALTER USER fittracker WITH PASSWORD '...'` exécuté en `sudo -u postgres psql` sur la Pi.
2. JWT_SECRET_KEY hex 64 chars (256-bit) généré côté PC, `.env` créé sur la Pi `~/Applications/sport-app/serveur/.env` avec `chmod 600` (perms `-rw-------`).
3. `sudo systemctl restart sportapi.service` OK -- sudoers NOPASSWD T3.4 -> pas de prompt.
4. Smoke : `curl /openapi.json` retourne JSON OK + `POST /api/v1/token will/<password>` retourne `{"access_token":"...","refresh_token":"...","token_type":"bearer"}`. Token décodé : HS256, sub=will, signé avec le NOUVEAU JWT_SECRET_KEY.

**Conséquences** : tous les anciens JWT invalidés -> re-login obligatoire S21+ (1 fois). Authenticator OkHttp V4.5 gère le redirect 401 -> LoginScreen automatiquement à la prochaine requête API.

**Sécurité** : DB Pi maintenant isolée des secrets `change-me`. PC dev pas affecté (son propre `.env` distinct). `git log` n'a JAMAIS contenu ces secrets (gitignored + jamais commités). Les anciens secrets restent visibles dans la conversation Claude qui a généré la rotation -- session à fermer une fois l'op confirmée.

**Lié à** : [TODO_FIXES §1 "Mots de passe `change-me`"](TODO_FIXES.md#1--sécurité) ✅.

### [x] ✅ EncryptedSharedPreferences pour le JWT Android — livré V8.2-3

**Contexte** : Le token JWT est stocké en clair dans SharedPreferences `auth_prefs` ([TokenManager.kt](../appli-android/app/src/main/java/com/example/sportapp/network/TokenManager.kt)). Lisible si le device est rooté ou via backup ADB.

**Pourquoi** : Bonne pratique sécurité Android. Acceptable pour app perso, mais bonne hygiène pour la suite.

**Proposition** : Migrer vers `androidx.security:security-crypto` `EncryptedSharedPreferences` avec `MasterKey.Builder.setKeyScheme(AES256_GCM)`. API identique, drop-in replacement.

### [x] ✅ Rate limit sur `/token` — livré V8.2-4 (slowapi, 3 routes limitées)

**Contexte** : L'API étant exposée publiquement (`<public-dns>`), le bruteforce sur `/token` est libre — un attaquant peut tester des milliers de mots de passe par seconde.

**Pourquoi** : Bonne pratique sécurité de base pour un endpoint d'auth.

**Proposition** : Ajouter `slowapi` (équivalent FastAPI de `flask-limiter`) avec une limite de 5 tentatives par IP par minute sur `/token`. Renvoyer 429 Too Many Requests au-delà.

### [x] ✅ Sudoers pour `systemctl restart sportapi.service` sans mot de passe — livré T3.4 (2026-05-07)

**Contexte** : Aujourd'hui le script `deploy.sh` Pi demande le mot de passe sudo pour `systemctl restart`. Pénible si on déploie souvent.

**Pourquoi** : DX. Le script est censé être un one-shot fluide.

**Proposition** : Ajouter une règle sudoers `william ALL=NOPASSWD: /bin/systemctl restart sportapi.service` (et `status`, `stop`, `start`).

---

## §2 — Architecture / Refactor structurel

### [x] ✅ Système de migrations Room (bloqueur multi-features) — livré V3.1

**Contexte** : `AppDatabase.kt` a `version = 6` et `AppModule.kt:51` `fallbackToDestructiveMigration(false)`. Aucune `Migration` enregistrée. **Tout futur bump de schéma fait crasher l'app pour les utilisateurs existants** (cf. TODO_FIXES §2).

**Pourquoi** : C'est un **bloqueur** pour de nombreux fixes : suppression `MuscleWeeklySummary` (v6→v7), renommage colonnes camelCase (`userId → user_id` pour ActualWorkout, `isFavorite → is_favorite` pour Muscle, etc.), changement nullable de `SupersetGroup.userId`, `SupersetExercise.orderInGroup`, ajout `target_reps` ou retrait, etc.

**Proposition** :
1. Activer `exportSchema = true` dans `@Database` annotation pour générer des fichiers JSON `app/schemas/<version>.json` (versionnés en git).
2. Créer un fichier `data/local/migrations/Migrations.kt` qui regroupe les migrations.
3. Première migration : `Migration(6, 7) { db -> /* DROP TABLE muscle_weekly_summary, etc. */ }`.
4. Enregistrer dans `AppModule.provideDatabase` : `.addMigrations(MIGRATION_6_7, MIGRATION_7_8, ...)`.
5. **Doc** : tutoriel "ajouter une nouvelle migration Room" dans `DEV_GUIDE.md`.

### [x] ✅ Format wire des dates unifié (résout les bugs DATES) — livré V3.2

**Contexte** : 3 formats wire coexistent (`Z` Android, `+00:00` Pydantic REST, `+01:00` trigger Postgres) — cause racine des bugs date (cf. [docs/DATES.md](DATES.md)).

**Pourquoi** : Résout d'un coup les 4 🔴 du sujet dates (réécriture en sync, crashs `Instant.parse`, query SQLite cassée, comparaison fausse).

**Proposition (4 fixes ciblés)** :
1. **Côté Postgres trigger** : remplacer `'updatedAt', rec.updated_at` par `'updatedAt', to_char(rec.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')` dans les 17 triggers (un seul fragment SQL réutilisé via une fonction `iso_utc(timestamptz)` à créer).
2. **Côté Pydantic** : ajouter un `field_serializer` global qui force le suffixe `Z` au lieu de `+00:00`.
3. **Côté Android** : `getNowISO8601()` → `Instant.now().truncatedTo(ChronoUnit.MICROS).toString()` (truncation à microsec pour aligner précision Pydantic/Postgres = 6 décimales).
4. **`isRemoteNewer`** : parser en `Instant` avant comparaison + try/catch fallback sur format Postgres legacy.

**Lié à** : [TODO_FIXES §3 dates](TODO_FIXES.md#3--sync-rest--websocket--dates).

### [x] ✅ Soft-delete propre OU retrait de `deleted_at` — livré V5.5 (2026-05-05)

**Décision** : Option A (simplification) retenue et appliquée 2026-05-05 dans la vague V5.5 / Phase 4 sub-vague E+F.

**Pourquoi cette décision** : `deleted_at` était un chemin aspirationnel jamais terminé -- aucun CRUD ne posait `deleted_at`, aucun SELECT ne filtrait `WHERE deleted_at IS NULL`, et `RemoteDataMerger.if (item.deletedAt != null)` était dead code car le serveur n'envoyait jamais `deletedAt` non-null. Le rationale historique (interface Angular qui aurait reçu la date de delete via GET/sync pour supprimer localement) n'est plus pertinent : l'app Android utilise WS push pour propager les deletes en realtime + le mécanisme tombstone `pendingDeletion` Room côté client pour les deletes locaux.

**Livré** :
- **Serveur** : Alembic migration `20260505_v5_5_drop_deleted_at` DROP COLUMN sur les 21 tables user-scoped. Models SQLAlchemy + schemas Pydantic nettoyés.
- **Android** : Room migration v8→v9 (cf. [Migrations.kt](../appli-android/app/src/main/java/com/example/sportapp/data/local/migrations/Migrations.kt)) `ALTER TABLE ... DROP COLUMN deleted_at` sur les 21 tables. Modèles Room + DTO Kotlin + RemoteDataMerger nettoyés (dead code retiré).
- **DBML** + **docs/DATABASES.md** mis à jour synchrone.

**Verifs (2026-05-11)** : `grep deleted_at` retourne 0 résultat dans `serveur/app/models/`, `serveur/app/schemas/`, `appli-android/app/src/main/java/com/example/sportapp/data/`. Seules références restantes : la migration Alembic V5.5 (historique) + Room migration v8→v9 (idem) — normal, ce sont les commits de drop.

**Doc référence (2026-05-11)** : voir [SYNC_PATTERN.md](SYNC_PATTERN.md) pour le protocole complet (3 contrats client, scénario multi-device offline, trade-offs Option A vs Option B, tuto pour nouveaux clients Angular/iOS/Flutter/etc.).

**Complément multi-device offline -- AUSSI livré (T4.2 2026-05-07)** :
La purge des locaux absents du remote (équivalent fonctionnel de `deleteLocalsAbsentFromRemote`)
est implémentée dans [SyncMergeOps.kt:37-40](../appli-android/app/src/main/java/com/example/sportapp/sync/base/SyncMergeOps.kt#L37-L40)
sous le nom `pruneStaleLocals` :

```kotlin
val remoteKeys = remote.map(typed::keyOf).toSet()
local.values
    .filter { typed.syncedOf(it) && typed.keyOf(it) !in remoteKeys }
    .forEach { typed.deleteLocal(it) }
```

Garde safety `synced=true` : on supprime UNIQUEMENT les rows qui ont été synced
au moins une fois (donc connues du serveur à un moment) ET qui ne sont plus
côté serveur (= deleted par un autre device). Les rows `synced=false` (créations
locales en attente de push) sont préservées.

→ Scénario validé : Device A delete row X → push REST → serveur hard DELETE +
WS push. Device B offline rate l'event WS, mais quand il revient online, son
prochain `mergeFromRemote()` détecte que row X est `synced=true && absent du remote`
→ delete local. Convergence sans `deleted_at`.

### [x] ✅ Refactor `exec_pg.py` en 2 scripts séparés — livré F9-Q5 (setup_db.py + reset_db.py)

**Contexte** : `exec_pg.py` fait : (1) drop toutes les tables, (2) recrée le schéma, (3) charge les triggers, (4) lance uvicorn. Destructif et tout-en-un. Le DEV_GUIDE.md §9 propose déjà cette amélioration.

**Pourquoi** : Permet de séparer "setup DB" (idempotent, sûr) de "lancer le serveur" (juste uvicorn).

**Proposition** :
- `setup_db.py` : crée le schéma + triggers de manière **idempotente** (ne drop pas si déjà existant). Détecte les tables manquantes via `inspect_schema` et les crée. Charge les triggers. Pas destructif.
- `run_server.py` : lance uvicorn uniquement.
- `reset_db.py` (nouveau, optionnel) : version destructive séparée pour les cas où on veut vraiment repartir de zéro.

**Lié à** : [TODO_FIXES §2 exec_pg.py destructif](TODO_FIXES.md#2--stabilité-production).

### [x] ✅ Refactor Chrono en feature module Style A — livré 2026-05-10 (B4)

**État précédent** : Le Chrono était éclaté entre `viewmodel/ChronoViewModel.kt` (295 lignes monolithique), `ui/components/chronoScreen/` (composants), `ui/screens/ChronoScreen.kt`, et MainActivity (qui hostait le VM). Pas un feature module Style A comme `auth/`, `notifications/`, `settings/`.

**Livré (10 commits, ~1h30 dev)** : refonte complète en module Style A maximal selon directive utilisateur "max structure / extensible / 3 jours OK". Logique pure + tests JVM (au lieu d'une simple réorganisation).

**Architecture finale `chrono/`** :
- `data/` (3 fichiers) : `ChronoSettings` data class + `ChronoSettingsDataStore` (persiste `lastTimerName`, `lastTimerDurationMillis`, `lastActiveTab` via DataStore) + `ChronoSettingsRepository` @Singleton.
- `domain/` (5 fichiers) : `Clock` interface + `SystemClockImpl` (Android) + `Lap` data class + `StopwatchStateMachine` pure (~106 lignes) + `TimerStateMachine` pure (~134 lignes). `name: StateFlow<String>` ajouté au TimerStateMachine pour fix bug "Rest 90s".
- `ui/` : `ChronoScreen` + `ChronoScreenViewModel` (renommé de `ChronoViewModel` selon V7.4-1A naming = filename).
- `ui/components/` (7 fichiers) : `ChronoScreenHeader` + `StopwatchPage` + `TimerPage` + `TimerDurationDialog` + extraction publique de `LapsHeader`, `LapRow`, `PresetTile` (étaient `private fun` internes).
- `ui/overlay/` (2 fichiers) : `MiniChronoOverlay` + `MiniTimerOverlay`. État partagé Screen ↔ Overlays préservé via VM hoisté à MainActivity (politique B.4a Phase 1).
- `utils/ChronoFormatters.kt` : 4 helpers consolidés (`formatTimeWithCentiseconds`, `formatTimeFull`, `formatTimeCompact`, `timerNameForDuration`) — avant ce refactor, 3 formats co-existaient en privé dans 4 fichiers avec duplications partielles.
- `ChronoModule.kt` Hilt : `@Provides` DataStore + `@Binds` Clock → SystemClockImpl.

**Tests JVM (B.2c Phase 1)** : 19 tests verts (8 stopwatch + 11 timer) via TestScope(UnconfinedTestDispatcher) + FakeClock. Couvrent : initial state, transitions IDLE↔RUNNING↔PAUSED↔FINISHED, lap delta + index, ticker FINISHED, name persiste cross pause-resume, etc. Total tests JVM projet : 71 verts (52 existants + 19 chrono), 0 failure.

**Bug fix "Rest 90s"** (TODO_FIXES §5) : `notificationCenter.notifyTimerDone(userId, "Rest", 90)` hardcodé → `notifyTimerDone(userId, timer.name.value.ifBlank{"Timer"}, timer.durationMillis.value/1000)`. Notif affiche maintenant "Timer finished — 1 min (60 s)" pour preset, "Timer finished — 1 min 30 s (90 s)" pour custom dialog.

**Bonus** : persistance `lastActiveTab` (Stopwatch/Timer) — la prochaine ouverture du ChronoScreen rouvre sur le bon onglet.

**Commits** : `d932564` P2.1 domain pur · `517ac60` P2.2 StopwatchStateMachine · `4b45629` P2.3 TimerStateMachine · `0ed7b53` P2.4 data layer · `a350c33` P2.5 ChronoFormatters · `e4408a3` P2.6 ChronoScreenViewModel + fix bug · `e47f6ce` P2.7 nouveaux fichiers UI · `9578efd` P2.8 nouveaux overlays · `f08290b` P2.9 swap MainActivity + delete legacy (-1338 lignes) · `915595e` P2.10 tests JVM.

**À tester runtime Samsung S21+** : install release APK (signed debug.keystore) par-dessus le debug. Vérifier : (1) ChronoScreen ouvre sans crash ; (2) stopwatch start/lap/pause/reset ; (3) timer preset "1 min" → start → attendre 60s → notif "Timer finished — 1 min (60 s)" (PAS "Rest 90s") ; (4) timer custom dialog 90s → start → notif "Timer finished — 1 min 30 s (90 s)" ; (5) MiniChronoOverlay + MiniTimerOverlay apparaissent quand on quitte ChronoScreen ; (6) reload app → ChronoScreen ouvre sur dernier tab + dernière durée timer chargée. Cf. CLAUDE.md historique 2026-05-10.

### [x] ✅ Unifier la source de vérité triggers/migrations — livré V3.3 (helper `app/triggers_loader.py` partagé)

**Contexte** : `Base.metadata.create_all` + Alembic en parallèle (cf. TODO_FIXES §5). Deux sources de vérité pour le schéma. La migration Alembic redéfinit `notify_row_change()` avec un payload différent de `db_triggers/`.

**Pourquoi** : Plus c'est ambigu, plus on aura des bugs comme "selon ce qu'on lance en dernier, le format change" (déjà arrivé en pratique).

**Proposition** :
- **Recommandé** : tout passer par Alembic. `setup_db.py` = `alembic upgrade head`. La migration crée les tables (autogenerate) ET pose les triggers (en chargeant les `.sql` via `op.execute(open(path).read())`).
- **Alternative** : tout passer par `setup_db.py` + `db_triggers/`, supprimer entièrement la migration Alembic existante.

**Lié à** : [TODO_FIXES §3 divergence Alembic ↔ db_triggers](TODO_FIXES.md#3--sync-rest--websocket--dates), [TODO_FIXES §5 Base.metadata.create_all + Alembic](TODO_FIXES.md#5--architecture--squelette-uniforme--refactor).

### [~] 💡 Application de la politique squelette uniforme aux 27 CRUDs / 22 routers / 22 schemas / 22 syncables / 22 DAOs / 22 APIs — **partiel V6 (5/8 PRs livrées)**

**Contexte** : La politique squelette uniforme est définie (mémoire 2026-05-03 + précision 2026-05-04) mais pas encore appliquée à 100%. ~5 axes de divergence sur 27 modules.

**Pourquoi** : Cohérence + maintenance. Ajouter une nouvelle entité doit être trivialement réplicable depuis le canonique.

**Proposition** : refactor systématique en plusieurs PRs (1 PR par axe) :
1. PR1 : ordre des paramètres CRUD `(db, uuid, dto, user_id)` partout.
2. PR2 : gestion ownership uniforme (403 partout).
3. PR3 : style update (écrasement total via `model_dump`).
4. PR4 : type d'entrée bulk (`list[XCreate]` partout, jamais `XOut`).
5. PR5 : `delete_X` retournent `bool`.
6. PR6 : `tags=` + `prefix=` sur tous les routers.
7. PR7 : Style DAO unifié (Style A, migrer les 2 Style B).
8. PR8 : `model_config = {"populate_by_name": True}` sur tous les `XBase`.

---

## §3 — Nouvelles fonctionnalités

### [ ] ⭐ Module Nutrition (calories + macros) — **à faire** (projet en soi, plusieurs sessions)

**Contexte** : Mentionné explicitement par l'utilisateur le 2026-05-03 : *"On pourra aussi ajouter la gestion de la nutrition avec du tracking de calorie et grammes de macronutriment (à faire après dans le futur)"*. L'app est conçue comme un "compagnon vie de tous les jours" qui dépasse le sport. Cf. mémoire `project_future_nutrition.md`.

**Pourquoi** : Étend le scope de l'app au-delà du sport pur, vers le bien-être global (alimentation + sport + routines).

**Proposition (esquisse, à affiner)** :
- **Entités serveur** : `Food` (catalogue d'aliments avec calories/protéines/lipides/glucides par 100g), `Meal` (un repas avec date + nom), `MealEntry` (lien Meal × Food avec quantité en g), `NutritionGoal` (objectifs caloriques/macros par user, par semaine).
- **Entités Android** : symétriques + DAOs + Syncables + Handlers (pattern existant à suivre).
- **Écrans** : `NutritionScreen` (vue jour/semaine), `MealScreen` (composer un repas), `FoodSearchScreen` (recherche dans le catalogue).
- **VM** : `NutritionViewModel` calculs cumulés jour/semaine, comparaison avec `NutritionGoal`.
- **Triggers WS** : push temps-réel comme les autres entités.
- **Hors scope initial** : intégration avec une API externe type Open Food Facts (à faire en v2).

**Bloqueur** : Politique de migration Room en place (§2) avant de bumper version pour ajouter les entités.

**Quand** : Après stabilisation du tracking sport (= après les fixes critiques actuels). Pas urgent.

### [x] ✅ Implémenter `StatsViewModel` + `StatsScreen` — B3-2 livré (2026-05-07)

**État précédent** : `StatsViewModel.kt` 11 lignes vide, `StatsScreen` stub 3 cards bidons.

**Livré B3-2 (2026-05-07, 6 commits, ~6h)** : 1 écran central + 2 sous-écrans navigables, 3 ViewModels, 4 DAO queries Flow agrégées + 4 indexes Room (migration v13→v14), Vico chart branché sur Room.

- **Étape 2** — `commit 9448890` : indexes Room sur `actual_workouts.date`, `actual_workout_exercises.actual_workout_uuid` + `.exercise_uuid`, `actual_workout_sets.actual_workout_exercise_uuid`. 4 DAO queries (`observeExerciseDailyStats`, `observeMuscleWeeklyVolume`, `observeExerciseAllTimeStats`, `observeActiveDaysCount`). 3 data class projection.
- **Étape 3** — `commit 4733229` : sealed class `StatsRange` (5 raccourcis + Custom) + `StatsRangeState` singleton Hilt + `StatsViewModel` overview (range partagé, frequence, listes muscles/exercises).
- **Étape 4** — `commit 2820ea2` : refonte `StatsScreen` Overview (FilterChip range + DateRangePicker M3 custom + FrequencyCard + listes navigables) + 2 placeholders `MuscleStatsScreen` / `ExerciseStatsScreen` + 2 nouvelles routes.
- **Étape 5** — `commit 33ca2bc` : `ExerciseStatsScreen` complet — Header back + Card all-time (top set / total sets / volume) + RangeChipsRow + MultiLineChart Vico (Weight + Volume) + ChartLegend. `ExerciseStatsViewModel` paramétré via `setExerciseUUID` + LaunchedEffect. Composant commun extrait `ui/components/stats/StatsRangePicker.kt`.
- **Étape 6** — `commit 4ffae90` : `MuscleStatsScreen` complet — Card "Objectif semaine" (MuscleGoal courant) + RangeChipsRow + LineChart Vico (volume hebdo) + Liste exercises liés navigables. `MuscleStatsViewModel` (4 StateFlow). +1 query `ExerciseMuscleDao.observeExercisesByMuscle`.
- **Étape 7** — Tests JVM (8 tests `StatsRangeTest`) + build release APK 70 MB OK.

**Architecture** :
- Range partagé via `StatsRangeState` singleton Hilt (commun aux 3 VMs).
- Pattern observe + flatMapLatest sur (uuid, range) → DAO Flow → StateFlow `WhileSubscribed(5_000)`.
- Navigation : Routes plates `muscle_stats/{uuid}` + `exercise_stats/{uuid}`. UUID via `LaunchedEffect(uuid) { vm.setUuid(uuid) }`.
- Perf : queries SQL agrégées (SUM/MAX/GROUP BY) + indexes Room v13→v14, pas d'agrégation Kotlin en mémoire (point E user "beaucoup de données").

**À tester runtime tel** : install release par-dessus le debug → upgrade Room v13→v14 (CREATE INDEX IF NOT EXISTS, idempotent) → naviguer Stats → choisir range → cliquer muscle/exercise → vérifier charts + données.

**Lié à** : [TODO_FIXES §5 2 ViewModels VIDES](TODO_FIXES.md#5--architecture--squelette-uniforme--refactor) — 1 sur 2 résolu (StatsViewModel). Reste : ChronoViewModel hardcode B4.

### [-] ❌ Implémenter `HistoryViewModel` + `HistoryScreen` — **annulé / supprimé** (2026-05-07, doublon Calendar)

**Verdict** : feature **abandonnée**. Audit B3-1 (2026-05-07) a confirmé un doublon fonctionnel :
- Consultation séance par date détaillée (exercises + sets) → **déjà couvert par CalendarViewScreen** (clic sur un jour → BottomSheet → écran détail).
- Recherche par exercise/muscle ("dernière perf sur Bench ?") → **sera couvert par B3-2 Stats** (qui a vraiment besoin de filtres exercise/muscle).
- Chronologie simple sans filtre → version dégradée de Calendar.

**Action 2026-05-07** : `HistoryViewModel.kt` (12 lignes orphelines, 0 référence) supprimé. `HistoryScreen` jamais créé. Plan revu : B2 ✅ → ~~B3-1~~ → **B3-2 Stats directement**.

### [ ] 💡 Conversations (écran existe mais fonctionnalité ?) — **à clarifier**

**Contexte** : Un écran `ConversationsScreen.kt` existe ([appli-android/.../ui/screens/](../appli-android/app/src/main/java/com/example/sportapp/ui/screens/)) — déjà repéré en cartographie initiale (cf. PROJECT_MAP.md §1.7). Usage à clarifier.

**Pourquoi** : Si c'est une feature prévue (chat ? messages ? notes de coach ?), l'expliciter. Si c'est un placeholder, le retirer.

**Proposition** : Demander à l'utilisateur ce qu'il avait en tête. Hypothèses : (a) chat avec un coach virtuel/IA, (b) messagerie multi-user (si l'app s'ouvre à plusieurs users), (c) notes/journal, (d) placeholder à supprimer.

### [ ] 💡 Page Planning / Agenda journalier — note 2026-05-09 — **à scoper**

**Idée** : nouvelle page **Planning** qui affiche le planning de la journée — agenda journalier, ce que tu fais aujourd'hui (ou cette semaine).

**À scoper** : qu'est-ce qu'on met dedans ?
- Sessions workout planned du jour (avec heure ?)
- Routine tasks (à compléter dans les RoutinePeriods Morning/Midday/Evening)
- Notifications à venir / rappels (chrono, timer rest)
- Goals weekly avec status d'avancement
- Calorie intake / macros (futur quand nutrition sera là)
- Free-form notes / journal du jour ?
- Vue 1 jour vs 1 semaine (toggle) ?

**Pourquoi** : aujourd'hui les routines + sessions + goals sont éparpillés sur plusieurs écrans (Calendar, Goals, Routine, Workout). Une page "ma journée" centralise.

**Statut** : à scoper. Décision : quel scope minimum pour V1, et placement dans le drawer.

### [x] ✅ MuscleGoals — refonte 3-niveaux + graphe achievement % — livré 2026-05-09

**Livré** : refonte complète de la page MuscleGoals dans le style Stats (palette par zone, toggles permanents) + ajout du graphe footer en %.

**Décisions actées** :
- **Période** : semaine sélectionnée par le `GoalsHeader` (1 semaine fixe, pas de range — la page reste fondamentalement hebdo).
- **3 niveaux d'affichage** (toggle `GoalsViewModeToggle`) qui contrôlent **simultanément** la liste ET le graphe :
  - `MUSCLE` : liste flat de `GoalRow` + 1 bar par muscle goal (jusqu'à 35).
  - `GROUP` : cards `ZoneGoalsCard` regroupées par muscle_group + 1 bar par group (jusqu'à 17).
  - `ZONE` : cards `ZoneGoalsCard` regroupées par zone + 1 bar par zone (6 max).
- **5 modes de tri** (`GoalsSortToggle`) : ALPHA / PALETTE (par zone, cohérence Stats) / PERCENT_DESC / PERCENT_ASC / PRIORITY.
- **% cap-free** : on laisse dépasser 100 (lisibilité de l'effort) + ligne pointillée horizontale à y=100 (target line en couleur primaire) toujours visible (`rawMax.coerceAtLeast(100f)`).
- **SKIPPED affichés** mais en alpha 0.4 sur la bar + label X axis en alpha 0.5 ("était prévu mais zappé").
- **target=0 / muscle introuvable** exclus en amont dans `goalsWithPercent`.
- **Chart BAR uniquement** (1 seule semaine = 1 bucket = LineChart impossible) — toggle Line/Bar non porté sur cette page.
- **Placement** : footer sticky 250dp en bas, liste scrollable (weight 1f) au milieu.
- **Palette par zone** alignée `StatsScreen.groupColors` + `paletteForZone(zoneColor, count)` pour les nuances (cohérence cross-screens).

**Bonus correctif** : le mode `BY_ZONE` legacy (avant 2026-05-09) était cassé depuis le refactor 3-niveaux 2026-05-08 — `normalizeZone()` ne reconnaissait pas les 6 nouvelles zones canoniques (Chest/Back/Shoulders/Arms/Legs/Core), tout tombait sur `OTHER` (sauf Core). Réparé étape 1 (commit `3269ae1`).

**Fichiers** :
- Nouveaux : `ui/components/goalsTabContent/{GoalsAchievementChart,GoalsViewModeToggle,GoalsSortToggle}.kt`.
- Modifiés : `viewmodel/GoalsTabViewModel.kt` (+ nouveaux enums `GoalsViewMode`/`GoalsSortMode` + data classes `GoalWithPercent`/`GoalsChartBar` + 4 nouveaux StateFlows + helpers de tri unifié), `ui/screens/GoalsTabContent.kt` (refonte structure principale), `ui/components/goalsTabContent/GoalsBottomSheet.kt` (cleanup actions doublons).

**Commits** : 5 commits (`3269ae1` → `9599bc8`).

**Différé feature séparée** : refonte UI globale par accordion (collapse `Chest > Mid Chest, Upper Chest, Lower Chest`) — l'idée reste pertinente mais découplée de la livraison du graphe.

### [ ] 💡 Multi-user social (amis, chat, conversations) — note 2026-05-09 — **différé**

**Contexte** : Aujourd'hui (politique F8-Q1) la table Room `users` est volontairement vide côté client : on stocke uniquement le user courant en mémoire (TokenManager + UserInfo DTO via `/me`). Si on veut introduire un volet social (amis, chats, conversations cross-user), il faudra :

- Côté serveur : tables `friendships(user_id_a, user_id_b, status)` + `conversations(...)` + `messages(...)` + endpoints CRUD + WS push pour live chat.
- Côté Android : décider quelles infos d'**autres users** stocker en local Room (pseudo, avatar, last_seen ?), et lever l'invariant F8-Q1 (User read-only client) pour cette colonne — soit ajouter une nouvelle table `peer_users` séparée pour ne pas mélanger avec le user courant, soit étendre `users` avec un flag `is_self`.
- Privacy : décider du périmètre (un user voit-il les workouts d'un ami ? juste son nom ? son weekly volume ?).
- Auth : un user qui en bloque un autre — comment révoquer instantanément ?

**Pourquoi** : La feature `ConversationsScreen` actuelle pourrait ré-emerger avec ce volet. Bien anticiper ce que ça implique côté table `users` AVANT de re-coder pour ne pas avoir à re-migrer.

**Statut** : différé. À reprendre quand l'app dépasse l'usage perso → famille/amis ou usage public.

### [ ] 💡 Serveur MCP sport-app (assistant conversationnel) — note 2026-05-21

**Idée** : un **serveur MCP** (Model Context Protocol) qui expose le domaine entraînement de sport-app sous forme de *tools*, pour qu'un client IA (Claude Desktop, etc.) puisse répondre en langage naturel à des questions sur les données d'entraînement de l'utilisateur.

**Cas d'usage discutés (2026-05-21)** :
- « Claude, quelle est ma prochaine séance ? »
- « Qu'est-ce que je dois apporter comme matériel aujourd'hui ? »
- « Comment va ma progression pecs ce mois-ci ? »

**Ce qu'est MCP, en bref** : un protocole standard où un serveur expose un *catalogue de tools* (nom + description + schéma d'entrée). Le LLM lit le catalogue et choisit/appelle les tools. C'est du **pull / conversationnel** : un tool ne s'exécute que quand l'utilisateur parle à l'IA.

**Proposition (esquisse, à scoper)** :
- **Serveur MCP mince** : un nouveau petit service qui expose ~5-6 tools, chacun étant un **wrapper sur les endpoints existants** `/api/v1/...`. Quasi zéro nouvelle logique métier — l'API FastAPI fait déjà le travail. Tools majoritairement en lecture : `get_next_planned_workout`, `get_planned_workout(date)`, `get_exercise_equipment(exercise)`, `get_muscle_stats(...)`, `get_weekly_progress(...)`.
- **Composition** : exposer des briques fines plutôt qu'un tool par question. Ex. « quel matériel aujourd'hui ? » = l'IA enchaîne elle-même `get_planned_workout(today)` → `get_exercise_equipment(...)` pour chaque exercice. Pas besoin d'un tool dédié par question.
- **Génération** : envisager une lib type `fastapi-mcp` qui auto-génère le serveur MCP depuis l'app FastAPI existante (à évaluer vs SDK MCP officiel + tools écrits à la main).
- **Hébergement** : tourne sur la Pi, à côté de l'API (transport HTTP, derrière Caddy). Agit « en tant que will » via un token. Mono-utilisateur → simple ; multi-user demanderait de repenser l'auth.

**⚠️ Hors périmètre MCP — les alertes proactives** : « alerte automatique si baisse de rythme » **n'est pas réalisable via MCP** (MCP est *pull*, pas *push* : un serveur MCP ne peut pas notifier de lui-même). Une alerte proactive = **tâche planifiée (cron) côté serveur** qui détecte la baisse et écrit une `notification` → poussée via le système de notifs / triggers WS **déjà en place**. La logique de détection « rythme en baisse ? » peut être partagée entre un tool MCP (`get_rhythm_status`, interrogé à la demande) et le job cron (qui pousse). → item séparé, à traiter avec le système de notifications, pas ici.

**Pourquoi** : aligné avec la vision « compagnon vie quotidienne » de l'app. Surface produit nouvelle (parler à ses données d'entraînement) à faible coût technique (l'API existe déjà).

**Statut** : feature pure, **non urgente** (user : « on fera ça plus tard »). Derrière le §0 cleanup et la barre fix-first. À scoper : liste exacte des tools, génération auto vs manuelle, déploiement Pi.

---

## §4 — Plan de tests fonctionnels post-audit

> **Note utilisateur 2026-05-04** : *"des tests devront être faits durant le développement après"*. Tous les bugs identifiés en analyse statique doivent être validés en pratique.

### [x] ✅ Tests fonctionnels des bugs 🔴 identifiés — livré V8.1 + T1.1 (24 tests verts)

**Contexte** : L'audit (étapes 1 → 5) a identifié ~33 bugs 🔴 par lecture statique. Aucun n'a été reproduit en pratique. Avant fix, valider qu'ils se produisent réellement (parfois la lecture statique est plus pessimiste que la réalité, ou inversement).

**Liste prioritaire (analyse statique → validation pratique)** :

1. **`cycleWorkoutApi.upsert(cw)` retourne 405** — créer un workout, créer un planned_workout, lier les deux via UI, vérifier que le sync individuel échoue avec 405. Vérifier que `synced` reste à 0.
2. **`exerciseMuscleApi.upsert(exerciseUUID, muscleUUID)` retourne 404** — créer un exercise + muscle + lien, vérifier sync individuel.
3. **`SupersetExerciseApi.getByUUID` retourne 422** — créer un superset_exercise, appeler `getByUUID` côté Android, vérifier le retour.
4. **`PUT /superset-groups/{uuid}` retourne 422** — modifier un superset_group via UI, vérifier réponse serveur.
5. **`isRemoteNewer` réécriture intempestive** — modifier un exercise sur device A, sync, vérifier que device B reçoit. Modifier sur B, sync, vérifier que A ne réécrit pas par erreur. Tester avec 3 formats de date côté serveur (`Z`, `+00:00`, `+01:00`) en jouant avec le timezone Postgres.
6. **`Instant.parse` crash sur format Postgres legacy** — provoquer un format avec espace dans la DB et déclencher `formatRelativeTime` ou `startOfWeek` côté Android. Vérifier le crash.
7. **`date()` SQLite retourne NULL** — insérer un workout avec `date = "2025-01-15T14:30:00.123Z"` (au lieu de `"2025-01-15"`) en Room, exécuter `getDoneSetsForMuscleInWeek`, vérifier que la query retourne 0.
8. **3 triggers sans `userId` → broadcast à tous** — connecter 2 devices avec 2 users différents, modifier un `exercise_muscle` sur user A, vérifier que user B reçoit la notification (alors qu'il ne devrait pas).
9. **JWT expiré → snackbar trompeuse** — forcer le `JWT_SECRET_KEY` à expirer rapidement (1 min en dev), faire patienter, déclencher une sync, vérifier le comportement Android.
10. **`mergeAllFromServer` non déclenché après login** — login sur un device fraîchement installé (Room vide), vérifier si les données du serveur arrivent en Room sans réseau interruption.

### [x] ✅ Framework de tests à mettre en place — livré T1.1 (pytest serveur + JUnit Robolectric Android + androidTest MigrationTestHelper)

**Pourquoi** : L'audit a montré que les tests existants (`test_api.py`, `tests/full_test.py`) sont obsolètes et trop lâches (assertion `status_code in [200, 201, 204, 404, 422]` accepte presque tout). Pour pérenniser les fixes, il faut un vrai framework.

**Proposition** :
- **Côté serveur** : pytest + httpx async client + factory_boy (ou pytest-fixtures simples). Tests d'intégration sur une DB Postgres de test (Docker compose). Couverture cible : tous les endpoints critiques + flows bypass ownership + cas d'erreur (401/403/404/422/500).
- **Côté Android** : JUnit + Mockk + Robolectric pour les VMs. Espresso pour les flows UI critiques (login → home → ajouter exercise → sync). Tests d'intégration Room (in-memory DB).
- **Tests cross-stack (E2E)** : un script qui démarre un serveur de test + une instance Android (via emulator) et joue les scénarios sync. Plus lourd, à faire après les unitaires.

### [ ] ⭐ Multi-device test setup — **à faire** (différé, pas encore investi)

**Contexte** : Plusieurs bugs concernent le scénario multi-device (filtre `userId` côté WS, propagation des suppressions, écrasement par merger). Pas testable avec un seul device.

**Proposition** : Setup permettant de tester avec 2+ devices simultanés :
- 2 émulateurs Android avec 2 users différents (ou même user en double)
- Scripts pour reproduire les scénarios : "user A modifie X, user B reçoit / ne reçoit pas selon trigger"
- Documentation de cas pratiques dans `DEV_GUIDE.md` sous "Tests multi-device"

---

## §5 — DX / DevOps

### [x] ✅ Brancher `alembic --autogenerate` — livré V3.4

**Contexte** : `alembic/env.py:28` a `target_metadata = None` ([TODO_FIXES §7](TODO_FIXES.md#7--documentation)) → `alembic revision --autogenerate` ne fonctionne pas. Toutes les migrations doivent être écrites à la main.

**Pourquoi** : DX. Permet de générer automatiquement les migrations à partir des changements du modèle SQLAlchemy.

**Proposition** :
```python
# env.py
from app.database import Base
import app.models  # enregistre tous les models
target_metadata = Base.metadata
```

Workflow : modifier un model SQLAlchemy → `alembic revision --autogenerate -m "add X column"` → revoir le fichier généré → `alembic upgrade head`.

**Lié à** : [§2 Unifier source de vérité triggers/migrations](#2--architecture--refactor-structurel) — si on choisit "tout via Alembic", autogenerate devient critique.

### [-] ⭐ `deploy_to_pi.ps1` PC → Pi en une commande — **obsolète** (remplacé par webhook auto-deploy T3.1, plus besoin d'un script PC)

**Contexte** : Aujourd'hui le déploiement est manuel : `git push` côté PC + `ssh <ssh-user>@<pi-lan-ip> ~/Applications/sport-app/serveur/deploy.sh` côté Pi. Mentionné dans DEV_GUIDE.md §9 TODO.

**Pourquoi** : DX. Réduit les frictions du déploiement à 1 commande.

**Proposition** : Script PowerShell PC qui :
1. `git status` (avorte si pas clean ou si commits locaux non pushés non confirmés)
2. `git push`
3. `ssh <ssh-user>@<pi-lan-ip> ~/Applications/sport-app/serveur/deploy.sh`
4. Affiche les logs du `journalctl -u sportapi.service -n 50` après le restart pour vérifier qu'il a bien démarré.

### [x] ✅ CI/CD GitHub Actions — livré T1.2 (workflow tests serveur + Android JVM en parallèle)

**Contexte** : Mentionné dans DEV_GUIDE.md §9 TODO.

**Pourquoi** : Automatiser : (a) tests à chaque push, (b) auto-déploiement Pi sur push `main`.

**Proposition** :
1. **Workflow `tests.yml`** : run pytest sur push/PR, fail si tests cassent.
2. **Workflow `deploy.yml`** : sur push `main` après tests OK, SSH vers la Pi et exécute `deploy.sh`. Nécessite stocker la clé SSH en GitHub Secrets.
3. **Workflow `android.yml`** (optionnel) : build l'APK release sur push tag `v*.*.*`, attache à la release GitHub.

### [x] ✅ Webhook GitHub → Pi pour auto-pull — livré T3.1 (2026-05-07)

**Contexte** : Alternative à CI/CD (DEV_GUIDE.md §9 TODO). La Pi écoute un webhook et fait `git pull + deploy.sh` quand GitHub lui envoie un push event.

**Pourquoi** : Pas besoin d'exposer la Pi à GitHub Actions (pas de credentials SSH dans GitHub). Plus simple à mettre en place.

**Proposition** : Petit serveur Flask/FastAPI sur la Pi (port différent du serveur principal) qui écoute `/webhook`. Vérifie la signature HMAC GitHub. Exécute `deploy.sh`. Caddy reverse proxy `/webhook` → ce mini-service.

### [x] ✅ Activer `exportSchema = true` Room — livré V3.1 (couplé migrations Room)

**Contexte** : Aujourd'hui `exportSchema = false` ([AppDatabase.kt:38](../appli-android/app/src/main/java/com/example/sportapp/data/local/AppDatabase.kt#L38)) → impossible de générer le schéma Room en JSON pour les revues / migrations.

**Proposition** : Activer + `app/schemas/` versionné en git. Permet de visualiser les diffs de schéma Room version par version. Couplé à [§2 Système de migrations Room](#2--architecture--refactor-structurel).

### [ ] 💡 Variant Android `staging` (en plus de debug/release) — **à faire**

**Contexte** : Aujourd'hui 2 variants : `debug` (PC `<pc-lan-ip>`) et `release` (Pi `<public-dns>`). DEV_GUIDE.md §9 TODO mentionne un variant `staging`.

**Pourquoi** : Tester avant déploiement prod final. Par ex. pointer vers une copie de la prod (ou la prod en read-only) pour valider une nouvelle feature sans risquer de casser l'app perso.

**Proposition** : Variant `staging` qui pointe vers une URL `staging.<public-dns>` (ou un sous-chemin `/staging/`). Build séparé avec son propre JWT secret.

### [ ] 💡 UI switcher d'URL serveur sans rebuild — **à faire**

**Contexte** : Aujourd'hui l'URL serveur est figée par variant (`buildConfigField`). Si on change d'IP PC dev (par ex. on travaille depuis un autre wifi), il faut rebuild.

**Pourquoi** : DX en dev itinérant.

**Proposition** : Écran admin caché (long press sur le logo de splash ?) qui permet de saisir une URL custom. Stockée en SharedPrefs. `AppConfig` lit la préf avant le `BuildConfig`. Reset via menu admin.

### [ ] ⭐ 🆕 Boucle dev mobile : SSH PC depuis tel + ADB over Tailscale — note 2026-05-14

> 📌 **À afficher en priorité la prochaine fois que l'utilisateur demande TODO_FEATURES** (validé 2026-05-14).

---

#### 🎯 L'idée en une phrase

**Tu es n'importe où dans le monde, avec ton téléphone et de la 4G. Tu veux modifier l'app sport-app et la tester sur ce même téléphone. Tu tapes une demande à Claude depuis le tel, et 2-3 minutes plus tard l'APK modifiée s'installe toute seule sur ton tel.**

Aucune manip côté tel après la demande initiale. Aucun déplacement physique vers le PC. Aucun outil de release Play Store / Firebase. Juste : Termius + Tailscale + ADB + un script.

---

#### 🧩 Pourquoi cette boucle existe

Aujourd'hui, pour tester une modif Android tu dois :
1. Être physiquement devant le PC
2. Modifier le code (manuellement ou avec Claude)
3. `./gradlew assembleRelease` (35-45s sur ton PC)
4. Transférer l'APK sur le tel (USB ou wifi) **physiquement présent**
5. Installer manuellement

→ **Tu ne peux pas itérer sur l'app sport-app quand tu es chez quelqu'un, dans le métro, au boulot, en vacances**. Le serveur, lui, a déjà sa boucle (Webhook auto-deploy T3.1 : push GitHub → Pi pull + restart automatique). Cette feature étend le pattern **au client Android**.

---

#### 🎬 Scénario concret : "Le bouton manquant dans le métro"

**14h30 — Tu es dans le métro**. Tu réalises que sur l'écran HomeScreen, le bouton "Démarrer séance" devrait avoir une icône ⏱️ devant le texte au lieu d'être nu. Tu as 20 minutes avant ton rendez-vous.

**T+0min** — Tu sors le tel. Tu ouvres **Termius**.
   - Tap sur l'hôte "PC Windows maison" → SSH s'ouvre via Tailscale → tu es dans un prompt PowerShell sur ton PC à la maison.

**T+30s** — Dans Termius, tu tapes :
```
cd Documents\Applications\sport-app
claude
```
→ Claude Code démarre sur le PC, voit le repo sport-app.

**T+1min** — Tu tapes au clavier mobile (court, c'est l'avantage de déléguer à Claude) :
```
ajoute une icone clock devant le texte du bouton "Démarrer séance"
dans HomeScreen, puis push l'APK sur mon tel
```

**T+1min10s → T+2min30s** — Claude (sur le PC) :
1. Lit `HomeScreen.kt` → trouve le bouton concerné
2. Modifie le Composable pour ajouter `Icon(Icons.Default.Schedule)` avant le `Text`
3. Si nécessaire : ajoute la string i18n (politique 18 CLAUDE.md)
4. Lance `.\gradlew :app:assembleRelease` (~35s sur ton PC)
5. Exécute `adb connect 100.x.x.x:5555` (IP Tailscale du tel)
6. `adb -s 100.x.x.x:5555 install -r app-release.apk`
7. → Pendant ce temps, **ton tel télécharge l'APK** via Tailscale en 4G (~5-10s pour 70 MB en 4G correcte)

**T+2min45s** — Notification Android : *"Sport-app installée"*. Tu ouvres l'app, l'icône ⏱️ est bien là devant "Démarrer séance".

**T+3min** — Tu commit + push via Claude. Auto-deploy serveur pas concerné ici (modif Android pure).

→ **3 minutes total**. Sans bouger du métro. C'est ça la boucle.

---

#### 🗺️ Schéma technique

```
                          INTERNET (4G)
                              │
                              ▼
   ┌──────────────────────────────────────────────────────┐
   │                                                      │
   │   ┌──────────────┐         ┌────────────────────┐    │
   │   │   📱 TEL     │  Termius│   💻 PC Windows    │    │
   │   │              │   SSH   │  (maison, allumé)  │    │
   │   │  - Termius   │◄────────┤  - OpenSSH Server  │    │
   │   │  - Tailscale ├────┐    │  - Tailscale       │    │
   │   │  - Wireless  │    │    │  - Claude Code     │    │
   │   │    Debug ON  │    │    │  - ADB             │    │
   │   │  - APK app   │    │    │  - Gradle/JDK      │    │
   │   │              │    │    │                    │    │
   │   └──────▲───────┘    │    └────────┬───────────┘    │
   │          │            │             │                │
   │          │       Tailscale mesh     │                │
   │          │      (réseau privé       │                │
   │          │       chiffré WireGuard) │                │
   │          │            │             │                │
   │          │            ▼             │                │
   │          │  ┌──────────────────┐    │                │
   │          └──┤ ADB push APK     │◄───┘                │
   │             │ via :5555 TCP    │                     │
   │             └──────────────────┘                     │
   │                                                      │
   └──────────────────────────────────────────────────────┘

   2 canaux TCP, tous deux à l'intérieur du tunnel Tailscale :
   1. SSH (port 22) : tel → PC, pour parler à Claude
   2. ADB (port 5555) : PC → tel, pour installer l'APK
```

**Pourquoi Tailscale et pas du SSH direct ?**
- Sans Tailscale, exposer SSH sur internet = ouvrir port 22 du routeur + risque permanent de bruteforce + IP publique dynamique chez toi
- Sans Tailscale, ADB port 5555 sur le tel est carrément exploitable par n'importe qui dès que tu es sur un wifi public
- Tailscale = **VPN mesh privé** chiffré (WireGuard). Aucun port ouvert sur internet. Seuls **tes propres devices** se voient.
- Bonus : marche identique en wifi maison, 4G, wifi public, café — l'IP Tailscale du tel et du PC ne bougent jamais

---

#### 🛠️ Setup requis (one-time, ~20 min)

##### 1. Côté PC Windows
- **OpenSSH Server** : `Settings → System → Optional features → Add → OpenSSH Server` (téléchargement ~5 MB)
- Activer le service : `Get-Service sshd | Set-Service -StartupType Automatic; Start-Service sshd`
- Pare-feu : règle entrante port 22 (auto-créée par l'install OpenSSH)
- Ajouter la clé publique Termius dans `C:\Users\William\.ssh\authorized_keys` (créer le dossier si besoin, perms restreintes au compte)
- **Tailscale** ✅ déjà installé
- **ADB** ✅ déjà installé (vient avec Android Studio, accessible via `adb` dans PATH si SDK platform-tools est dans la variable PATH)

##### 2. Côté téléphone Samsung S21+
- **Tailscale** déjà à installer (servira aussi pour SSH Termius cf. note du jour 2026-05-14)
- **Termius** comme client SSH (cf. note du jour 2026-05-14) avec hôte "PC Windows maison" configuré (IP Tailscale du PC + user `William` + clé SSH)
- **Developer options** activées (Réglages → À propos → tap 7× sur "Numéro de build")
- **Wireless debugging** ON (Developer options → Wireless debugging → toggle)
- **Pairing initial** : une seule fois, via USB connecté au PC OU via QR code (option dans Wireless debugging). Cette étape autorise le PC comme client ADB de confiance.

##### 3. Côté repo (à coder une fois, ~30 lignes)
Créer `scripts/install_apk_to_phone.ps1` :
```powershell
# Usage: .\scripts\install_apk_to_phone.ps1
# Pré-requis: PHONE_TAILSCALE_IP en variable d'env utilisateur

$phoneIp = $env:PHONE_TAILSCALE_IP
if (-not $phoneIp) {
    Write-Error "PHONE_TAILSCALE_IP non défini. Tape: tailscale ip -4 sur le tel puis [System.Environment]::SetEnvironmentVariable('PHONE_TAILSCALE_IP', '100.x.x.x', 'User')"
    exit 1
}

Push-Location appli-android
try {
    Write-Host "🔨 Build APK release..."
    .\gradlew :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Build échoué" }

    $apk = "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path $apk)) { throw "APK introuvable: $apk" }

    Write-Host "🔌 Connexion ADB au tel ($phoneIp:5555)..."
    adb connect "${phoneIp}:5555"

    Write-Host "📲 Installation APK..."
    adb -s "${phoneIp}:5555" install -r $apk

    Write-Host "✅ Installé."
} finally {
    Pop-Location
}
```

Claude pourra l'appeler en un seul tool call après chaque modif Android.

---

#### ⚠️ Pièges connus

| Piège | Mitigation |
|---|---|
| Wireless debugging port 5555 change au reboot du tel sur certains Android | Vérifier à chaque reboot, ou mettre l'IP+port en var env. Sur Samsung S21+ Android 15 : à tester, semble stable tant que pas de reboot |
| Signature APK doit rester identique pour upgrade in-place | OK chez toi : `debug.keystore` signe debug ET release (cf. `build.gradle.kts`). Si keystore change : `adb uninstall com.example.sportapp` puis réinstaller |
| Consommation 4G du téléchargement APK | ~70 MB par push. 5 pushes = ~350 MB. Si forfait limité : connecter le tel au wifi pendant les pushes (le SSH lui-même reste sur 4G, négligeable). **Le SSH du Termius en lui-même consomme < 1 MB/heure** |
| PC en veille / éteint | Le PC doit rester allumé. Activer Wake-on-LAN ou désactiver la mise en veille pendant les sessions mobiles. Alternative : laisser le PC tourner en permanence (faible conso à vide) |
| Pairing ADB perdu (rare) | Reconnaître depuis Wireless debugging → "Forget all" + re-pair. ~30s |
| Conflit `:5555` déjà connecté à un autre device | `adb disconnect` puis `adb connect 100.x.x.x:5555` |

---

#### 🎁 Bonus possibles

- **Diagnostic crash en live** : `adb -s 100.x.x.x:5555 logcat -v time` → Claude peut tail les logs juste après install, voir les `FATAL EXCEPTION`, te dire ce qui plante avant même que tu ouvres l'app
- **Pull une DB Room pour debug** : `adb -s ... exec-out run-as com.example.sportapp cat databases/sport-app.db > local.db` (en mode debug uniquement)
- **Screenshot programmatique** : `adb -s ... shell screencap -p > screen.png` → Claude peut me retourner l'écran actuel du tel pour vérifier un layout
- **Hot rebuild incrémental** : `./gradlew installRelease` au lieu de `assembleRelease` + install séparé (gain ~5s par cycle)

---

#### 🚫 Limites de la boucle

- **Pas adapté aux gros refactors** : taper 200 lignes de spec au clavier mobile est pénible. Idéal pour **demandes courtes** : "ajoute X", "fix le crash de Y", "change la couleur Z"
- **Pas de hot reload Compose** : c'est un install complet à chaque cycle. Pour du tweak visuel rapide, le PC reste mieux (Android Studio preview)
- **Modifs Android uniquement** : pour modifs serveur, la boucle webhook T3.1 existe déjà (push GitHub → Pi auto-deploy). Pour les deux ensemble (full-stack), tu fais les deux : Claude commit + push (déclenche webhook serveur) + script install_apk (push tel)
- **Le PC doit rester allumé** : limite physique, pas technique

---

#### 📍 Comparaison alternatives (rappel)

| Alternative | Manip côté tel | Marche en 4G | Boucle Claude ↔ tel | Verdict |
|---|---|---|---|---|
| **Firebase App Distribution** | Clic notif + install | ✅ | ❌ (upload manuel) | Lourd pour usage perso |
| **Caddy serve APK sur Pi** | Navigateur + clic install | ✅ | ⚠️ (semi-auto) | OK mais clic manuel chaque fois |
| **ADB USB classique** | ❌ (USB obligatoire) | ❌ | ❌ | Setup actuel, pas mobile |
| **🎯 ADB over Tailscale** | **0 manip** | ✅ | ✅ **full auto** | **Cible** |

---

#### 📝 Effort estimé

- Setup initial **~20 min** : 10 min PC (OpenSSH + clé) + 5 min tel (Wireless debugging + pairing) + 5 min script PowerShell
- **Pas de code applicatif** à modifier dans sport-app
- Maintenance : 0. Le script ne touchera plus une fois écrit

---

#### 🔗 Liens et notes

- **Pré-requis SSH PC + Termius + Tailscale** : déjà discutés dans la session 2026-05-14 (cf. CLAUDE.md historique)
- **Pattern parallèle côté serveur** : Webhook auto-deploy T3.1 (push GitHub → Pi pull + restart). Cette boucle Android est le pendant client.
- **Claude Code pas sur Pi** : `which claude` vide sur la Pi (vérifié 2026-05-14). Si fallback Pi voulu un jour : `npm i -g @anthropic-ai/claude-code` + login. Pas requis pour cette boucle.
- **Pas une dépendance critique** : à tout moment tu peux revenir au flow USB classique si tu es chez toi devant le PC. Cette boucle est un **complément** pour le mobile, pas un remplacement.

---

## §6 — Documentation

### [x] ✅ Aligner `diagram.dbml` ↔ modèles SQLAlchemy — politique 14 (continu, synchro à chaque commit DB)

**Contexte** : Le DBML annonce des indexes et types non implémentés ([TODO_FIXES §4](TODO_FIXES.md#4--schéma-db-postgres--pydantic--room)). Politique de traçabilité validée 2026-05-04 : la doc doit refléter l'état réel.

**Proposition** : Soit (a) implémenter les indexes/types annoncés dans les modèles SQLAlchemy (préférable pour les performances), soit (b) rétrograder le DBML pour refléter l'état actuel. Recommandation : (a).

### [x] ✅ Régénérer ou supprimer `routes.json` — supprimé V7.3 (source vivante = Swagger `/secure-docs`)

**Contexte** : Obsolète (cite `/sessions`, manque `routine_*`, `notification`, `/ws`).

**Proposition** : Soit régénérer automatiquement depuis OpenAPI (`/openapi.json` → script `python tools/gen_routes.py`), soit retirer si pas utilisé par un outil externe.

### [x] ✅ Tutoriel "ajouter une nouvelle entity" — livré (`docs/HOW_TO_ADD_ENTITY.md` ~300 lignes, corrections post-T4.2 + politique 14 ajoutées 2026-05-12)

**Contexte** : Aujourd'hui ajouter une nouvelle entité (ex: pour le module Nutrition) implique de modifier 8+ fichiers en gardant la cohérence (squelette uniforme). Pas de doc qui décrit la procédure.

**Pourquoi** : Onboarding + cohérence. Une checklist évite les oublis.

**Proposition** : Doc dans `DEV_GUIDE.md` ou nouveau `docs/HOW_TO_ADD_ENTITY.md` :
1. Créer le model SQLAlchemy
2. Créer le schéma Pydantic (Base/Create/Out)
3. Créer le CRUD (canonique)
4. Créer le router (canonique avec auth)
5. Créer le trigger SQL
6. Étendre `user_id_helper.sql` si user-scoped
7. Ajouter au `models/__init__.py`, `schemas/__init__.py`, `crud/__init__.py`, `routers/__init__.py`, `main.py` (tuple ROUTERS), `exec_pg.py` (`load_sql_parts`)
8. Côté Android : model Room → DAO → Api Retrofit → Syncable → SyncHandler → SyncManager liste → AppDatabase entities → AppModule provides
9. Migration Room (bumper version + écrire `Migration(N, N+1)`)
10. Ajouter à `seed_database.py`
11. Mettre à jour `diagram.dbml` + `diagram.dbdiagram`

### [ ] ⭐ Diagramme architecture global — **à faire** (Mermaid dans PROJECT_MAP.md ou ARCHITECTURE.md)

**Pourquoi** : `PROJECT_MAP.md` a déjà des diagrammes ASCII mais un diagramme propre (PNG ou Mermaid rendu) serait plus accessible.

**Proposition** : Diagramme Mermaid dans `PROJECT_MAP.md` ou nouveau `docs/ARCHITECTURE.md` qui montre :
- Stack haut niveau (Android ↔ FastAPI ↔ Postgres)
- Composants principaux (RetrofitInstance, SyncManager, Syncables, RemoteDataMerger, *SyncHandler côté Android ; routers, CRUDs, triggers, pg_listener, ws_hub côté serveur)
- Flux de données (sync montante REST, sync descendante WS, sync descendante REST batch)
- Couches Android (UI Compose → VM → Repository → DAO Room ; UI Compose → ViewModel → Api Retrofit → serveur)

### [ ] 💡 Documenter les endpoints critiques (auth flow, sync flow) — **à faire**

**Pourquoi** : Mentionné dans DEV_GUIDE.md §9 TODO. Onboarding.

**Proposition** : Section dans `INTEGRATION.md` (déjà présente — étape 5) ou enrichie avec exemples curl/HTTPie + payloads JSON réels.

---

## §7 — UI / UX

### [ ] 💡 Thème Subnautica (note 2026-05-22)

**Idée** : ajouter un thème visuel « Subnautica » à l'application — palette de couleurs
inspirée de l'univers du jeu (bleus profonds, reflets aqua, ambiance sous-marine).

**À discuter** : ce qu'on met exactement dedans — palette de couleurs précise, accents /
dégradés éventuels, périmètre (couleurs `AppColors` seules ? icônes ? fonds d'écran ?).

**Statut** : simple reminder — feature à scoper et implémenter plus tard (l'utilisateur veut
l'ajouter après). Lié au système de thèmes (cf. dark mode plus bas dans §7).

### [ ] ⭐ Settings : choix de la page d'accueil au démarrage (2026-05-12)

**Contexte** : aujourd'hui le HomeScreen affiche systématiquement la "session du jour" (SessionTab via DualTabMenu) au cold start. Avec l'arrivée de l'écran Tasks unifié (Phase 1-3, 2026-05-12), certains users pourraient préférer atterrir directement sur Tasks (Daily ou Agenda) ou un autre écran (Calendar workouts, Stats, etc.).

**Proposition** :
- Nouveau setting `startupTarget` dans `OnboardingPreferences` (DataStore), enum `SESSION` (default) / `TASKS` / `CALENDAR_WORKOUTS` / `STATS` / autres.
- UI Settings : RadioGroup "Page d'accueil au démarrage" → l'user choisit.
- `SplashScreenViewModel` lit le setting et oriente la nav vers la route choisie (au lieu de `Routes.HOME` systématique).
- Si setting = `SESSION`, comportement actuel préservé (default).

**Effort estimé** : ~1-2h (1 setting + UI + branche dans Splash + i18n EN/FR).

**Quand** : nice-to-have, à faire quand on touche aux Settings (e.g., en même temps qu'autres options onboarding). Pas urgent.

**Justification user (2026-05-12)** : "faire en sorte que dans les paramètres, on puisse choisir quelle page le HomeScreen pointe directement au démarrage : session du jour, tâches, autres etc."

### [ ] 💡 Re-run onboarding pour admins (tester l'onboarding) — 2026-05-15

**Contexte** : aujourd'hui, pour re-tester le flux d'onboarding sur device, il faut soit `clear app data` (Settings Android → Apps → SportApp → Storage → Clear data), soit désinstaller+réinstaller. Le second ne marche **pas** en pratique parce que `android:allowBackup="true"` + Auto Backup Google restaure les SharedPreferences `onboarding_flags` à la réinstallation → flag `onboarding_done_user_<id>` revient à `true` → splash file directement vers home. Friction forte pour les sessions de design/test UI (cf. test screenshots Figma 2026-05-15).

**Proposition** :
- Bouton "Re-run onboarding" dans `SettingsScreen` (ou nouvelle section "Admin" du même écran), **conditionné à `CurrentUserManager.isAdminFlow == true`** (idem section Admin du drawer ajoutée le 2026-05-11).
- Au tap : `OnboardingRepository.markAsNotDone(userId)` (efface le flag SharedPreferences) + navigate `Routes.ONBOARDING` (popUpTo current inclusive).
- L'admin parcourt à nouveau les 4 étapes (Welcome/Bio/Preferences/Permissions). À la fin (Finish), flag re-posé à `true` → comportement normal.

**Effort estimé** : ~30 min (1 button + 1 méthode `markAsNotDone` dans `OnboardingRepository` + nav + i18n EN/FR + visibilité conditionnelle).

**Justification user (2026-05-15)** : "ajoute aux to-do features : faire une option re-run onboarding si le user est un admin pour tester le onboarding".

**Note connexe** : le problème "désinstaller+réinstaller ne réaffiche pas l'onboarding" est dû à Android Auto Backup (`allowBackup="true"` + `backup_rules.xml` sans `<exclude>`). Cette feature contourne le problème côté UX (re-run en 1 tap) sans toucher au backup global. Si on voulait fixer en amont, alternative = ajouter `<exclude domain="sharedpref" path="onboarding_flags"/>` dans `backup_rules.xml` ET `data_extraction_rules.xml` — à arbitrer séparément (préserve le backup pour le reste, mais perd l'état onboarding au reinstall pour tous les users, pas que les admins).

### [x] ✅ Refonte snackbars sync (1 global au lieu de 22) — B2 livré (2026-05-07)

**Contexte** : `SyncManager.syncAllToServer` pouvait afficher 22 snackbars (1 par entité via `safe*WithSnackbar`). UX bruyante.

**Résolution** : déjà résolu **par T4.2** (refactor sync layer 2026-05-07) qui a éliminé les 22 callsites en migrant les ViewModels de `syncManager.sync<X>()` → `syncEngine.pushEntityClass(<X>::class)` (silencieux). Le snackbar global vit maintenant dans `SyncManager.syncAllToServer()` : 1 message "Starting automatic synchronization..." + 1 message "completed" / "Error" (cf. [SyncManager.kt:51-101](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncManager.kt#L51)). B2 cleanup (2026-05-07) : suppression des 2 fonctions orphelines `safeSyncEntityWithSnackbar` + `safeSyncEntityDeletionsWithSnackbar` dans [EntitySyncUtils.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/base/EntitySyncUtils.kt) (62 lignes mortes + 4 imports inutilisés). 42 tests JVM verts.

### [-] ❌ Indicateur visuel persistant "hors ligne" — **annulé** 2026-05-11 (décision user, couvert par badges drawer)

Feature retirée du backlog : couvert fonctionnellement par les badges existants
dans le drawer (icône signal_cellular_alt vert/rouge selon `isConnected`,
icône router vert/orange selon `isWsConnected`, badge compteur `totalPending`
sur l'icône cloud_upload). L'utilisateur a déjà l'info "online/offline + sync
en attente" en ouvrant le drawer, pas besoin d'un bandeau persistant
supplémentaire.

### [x] ✅ Pré-seed serveur au signup — V8.4 livré (2026-05-06)

**Contexte** : Demande utilisateur 2026-05-06. Aujourd'hui `equipment` + `available_equipment` sont globaux (Type C, partagés par tous les users), mais `muscle` et `exercise` sont user-scoped (Type A, vide pour un nouvel user). Résultat : un nouvel user ne peut rien faire.

**Proposition** :
- **Créer un user dédié `starter_template`** (non-admin, password aléatoire fort, jamais utilisé pour login UI). Sa raison d'être : héberger le **catalogue de référence** des muscles + exercises + relations qu'on copie aux nouveaux users au signup. Géré comme une "fixture data" — l'admin (`will` ou autre) édite les données du `starter_template` via un script ou un écran admin pour mettre à jour le starter pack.
- **Pourquoi pas `will` directement** : `will` = user de test perso de l'admin, ses données changent au gré des sessions de test. Le `starter_template` est figé, contrôlé, prévisible.
- **Setup initial** : ajouter dans `seed_database.py` la création de `starter_template` + son catalogue (muscles standards : Pectoraux, Dos, Quadriceps, Ischio, Fessiers, Biceps, Triceps, Épaules, Abdos, Mollets, ... + exercises populaires : Bench Press, Squat, Deadlift, Pull-up, OH Press, Curl, Dips, ... + relations `exercise_muscle` correspondantes Bench → Pec+Tri+Sho, etc.).
- **Au signup** : `auth_router.signup()` après création du user fait un INSERT...SELECT depuis le `starter_template` user_id vers le nouveau user_id, avec UUIDs régénérés mais noms/coefficients préservés.
- **Données copiées** : muscle, exercise, exercise_muscle (relations). Pas equipment/available_equipment (déjà globaux).
- **Données NON copiées** : workouts, plans, routines, goals (le user les crée lui-même).
- **Protection contre la modif** : copies user-scoped → le nouveau user PEUT modifier/supprimer/ajouter à sa guise. Pas de "lock", juste un point de départ.
- **Maintenance du catalogue starter** : un admin se connecte en tant que `starter_template` (via mot de passe spécial connu de l'admin uniquement) pour éditer la liste, OU un écran admin (cf. "UI admin pour gérer is_admin") permet d'éditer les données de ce user spécifique sans login direct. À décider.
- **Idempotence** : si on relance signup pour un user existant (race condition), ne pas re-copier. Check trivial : si user a déjà des muscles, skip.

**Settings serveur** : `STARTER_TEMPLATE_USERNAME = "starter_template"` (env var, configurable). Le user_id est résolu au runtime au moment du signup.

**Implémentation estimée** : ~80 lignes Python dans `auth_router.signup()` + ~50 lignes dans `seed_database.py` pour créer le starter user et seeder son catalogue.

**Statut V8.4 (commits `d15ce1b` + `d50246e`)** : ✅ livré.
- `app/settings.py` : `STARTER_TEMPLATE_USERNAME` ajoutée.
- `app/seed_database.py` : user fixture id=99999 + 12 muscles FR + 20 exercises EN (3 lignes d'instructions chaque) + 43 relations + fix resync `users_id_seq` (exclut le template).
- `app/starter_pack.py` (nouveau) : helper `copy_starter_pack(db, new_user_id)` ORM Python avec mapping UUID old→new, idempotent, sans commit interne.
- `app/routers/auth_router.py` : `signup()` flush user → copy → commit atomique. Si template absent → rollback + HTTP 503.
- Bonus `app/fill_database.py` : charge maintenant les 4 helpers SQL (iso_utc + user_id + notify_row_change + attach_triggers) après drop_all/create_all. Idempotent (CREATE OR REPLACE). Évite le piège vécu : iso_utc droppé manuellement sur la DB locale → fill_database crashait au 1er INSERT trigger.
- Catalogue : Pectoraux, Dos, Trapèzes, Deltoïdes, Biceps, Triceps, Avant-bras, Quadriceps, Ischio-jambiers, Fessiers, Mollets, Abdominaux + Bench/Incline/Push-Up/Dips/Pull-Up/LatPulldown/Row/Deadlift/RDL/Squat/LegPress/Lunges/LegExt/LegCurl/Calf/OHP/LatRaise/Curl/TriExt/Plank.
- Smoke E2E PC dev : POST /signup → user id=9 (sequence en band, pas saut à 100k), POST /token → JWT, GET /muscles=12, GET /exercises=20, relations Bench Press → Pec 1.0/Tri 0.5/Delt 0.5 vers UUIDs du nouveau user (pas template).
- **Hors scope V8.4 (différé)** : maintenance du catalogue starter (édition via login spécial OU écran admin).
- **À déployer Pi prod** : `git pull` + `python -m app.fill_database` au prochain reset (action manuelle, destructive sur tables non-users).

### [x] ✅ Onboarding initial UI (B1) — livré 2026-05-11

**Livré** : 2 commits (`bec30b7` serveur + `1b031ab` Android).

**Serveur** :
- `PATCH /api/v1/me/profile` self-only (sans require_admin) pour étape Welcome
  ("How should we call you?") + futur ProfileScreen edit.
- Schema `MeProfileUpdate` Pydantic (firstName/lastName optionnels).
- 3 tests pytest verts (update + partial update + 401 sans auth). Suite 25/25.

**Android** : module `onboarding/` Style A flat, 12 fichiers (~1093 lignes net) :
- `data/` : OnboardingPreferences (data class + enum WeekStart UPPER_CASE) +
  OnboardingDataStore (DataStore "onboarding_settings") + OnboardingRepository
  @Singleton (flag `onboarding_done_user_<userId>` per-user via SharedPreferences).
- `domain/OnboardingStep.kt` enum 5 étapes WELCOME/PREFERENCES/MUSCLES/
  EXERCISES/PERMISSIONS + helpers next/previous.
- `ui/OnboardingViewModel.kt` @HiltViewModel : currentStep StateFlow + drafts
  (firstName, prefs, selectedMuscleUuids, selectedExerciseUuids) + confirmAndNext
  apply changes puis advance (apply = PATCH /me/profile pour Welcome ; setPreferences
  DataStore ; markAsPendingDeletion + syncEngine.pushEntityClass pour Muscles/
  Exercises). + skipOnboarding (mark done sans apply).
- `ui/OnboardingScreen.kt` : router 5 sub-écrans + BackHandler système -> previous
  step + LaunchedEffect init firstName depuis /me.
- `ui/components/` : OnboardingHeader (title + LinearProgressIndicator "Step X of 5")
  + OnboardingFooter (Back + Skip onboarding + Next/Finish).
- `ui/steps/` : 5 écrans (Welcome OutlinedTextField first name / Preferences
  RadioGroup weekStart + morning routine time + Switch autoSyncOnWifi /
  Muscles LazyVerticalGrid 3 cols toggle / Exercises LazyColumn rows + Switch /
  Permissions check NotificationManagerCompat + intent ACTION_APP_NOTIFICATION_SETTINGS).
- `OnboardingModule.kt` Hilt @Provides DataStore.

**Réseau** : ApiUserService étendu avec PATCH /me/profile (data class
MeProfileUpdateRequest).

**Branchement** :
- `Routes.ONBOARDING = "onboarding"`.
- `SplashScreenViewModel` injecte OnboardingRepository -> après sync, check
  isDone(userId) -> nextRoute = "onboarding" sinon "home". Per-user via
  CurrentUserManager.userId.
- `MainActivity` NavHost : composable Routes.ONBOARDING -> OnboardingScreen
  avec onFinish navigate HOME popUpTo ONBOARDING inclusive.

**Décisions Phase 1 (validées user)** :
- Skippable à chaque étape.
- LinearProgressIndicator 5 segments en haut.
- Pas de persistance partielle (quit -> retour étape 1 au prochain run).
- Multi-select muscles/exercises + Apply au Next (gain perf vs delete réseau
  par toggle).
- Permissions notif non-bloquantes (warning si off).
- Users existants : flag par-user défaut false -> au prochain login,
  l'onboarding s'affiche aussi pour eux (acceptable, ils peuvent skip).

**À tester runtime S21+** : (1) install release ; (2) flag `onboarding_done_user_1`
n'existe pas pour `will` -> au login, splash -> onboarding (au lieu de home) ;
(3) parcours 5 étapes : modifier first name (sera push via PATCH), changer
weekStart, désélectionner 2-3 muscles, désélectionner 2-3 exercises, vérifier
permissions notif state, Finish ; (4) après Finish, home. Re-login -> direct
home (skip splash to home flow). Pour re-tester l'onboarding : clear app data
ou supprimer le flag SharedPreferences.

**Différé** : (a) bouton "Suggest popular exercises" dans étape 4 (placeholder
non implémenté pour MVP) ; (b) TimePicker M3 vrai dans étape 2 (actuellement
juste affichage du default 06:00 sans édition) ; (c) "Re-run onboarding" dans
Settings pour les users qui veulent re-faire le tour.

### [~] ⭐ Onboarding — Extensions backlog (validé user 2026-05-11) — **partiel** (Quick wins ✅, Long terme [ ])

Liste filtrée par l'user des extensions à apporter à l'onboarding au fil
des sessions. Triées par effort. Cf. mémoire `feedback_onboarding_extension.md`
(politique d'ajout systématique aux nouveaux settings/permissions).

**Quick wins (~30 min - 1h chaque)** :
- ⭐ **Units kg / lbs** (step 2 RadioGroup) — impacte affichage poids partout (sets, stats, body weight). Enum + setting persisté DataStore + propagation dans le code d'affichage.
- ⭐ **Units cm / inches** (step 2 RadioGroup) — pour mensurations + height (si bio livré).
- ⭐ **Theme Dark / Light / System** (step 2 RadioGroup) — toggle exposed Settings + onboarding. Nécessite un `MaterialTheme(darkColors / lightColors)` switch côté SportAppTheme.
- ⭐ **Sound on/off** (step 3 CustomSwitch) — déjà dans `AppSettings.soundOnInAppNotification`, juste exposer.
- ⭐ **Vibration on/off** (step 3 CustomSwitch) — idem `AppSettings.vibrateOnInAppNotification`.
- ✅ **Sample data toggle + tour visuel** (step 3 4ème card "Sample workouts") — **scope C livré bout-en-bout 2026-05-11** : `SampleDataInserter` + `DemoTourRepository` + toggle UI + `DemoTourViewModel` orchestrateur (6 steps : WELCOME / STATS / CALENDAR / SESSION / CHRONO / GOODBYE) + `DemoCaptionOverlay` bottom card (thirdBlue + firstBlue border, animated slide+fade) + auto-navigation NavController via LaunchedEffect dans MainActivity + cleanup à 3 triggers (GOODBYE Next, Skip tour, ColdStart Splash fallback crash-safe). Session 3 polish optionnelle (animations refinement, copy tweaks).

**Medium (~2-3h chaque)** :
- ✅ **Profil bio** livré 2026-05-11 (commits `ba350e9` serveur + `18b52f9` Android) — nouveau step BIO entre WELCOME et PREFERENCES, 4 fields nullable (birthDate ISO date / sex UPPER_CASE MALE/FEMALE/OTHER / heightCm canonique / weightKg canonique). Serveur : Alembic `bio1_user_bio_fields` (4 ADD COLUMN nullable). Android : Room v15→v16 + `OnboardingBioScreen` + `BirthDatePickerDialog` M3 stylé app. Tous optionnels (skippable).
- ⭐ **Language picker** (step 2 RadioGroup) — EN / FR. Aujourd'hui app EN-only. Nécessite : i18n strings.xml (extraire toutes les strings hardcodées) + locale switching runtime via `AppCompatDelegate.setApplicationLocales`. **Gros chantier ~3-5h** (extraction strings massive ~100-200 strings dispersées).
- ✅ **TimePicker M3 vrai** déjà livré antérieurement -- `MorningTimePickerDialog.kt` M3 stylé app, exposé via tap dans `OnboardingPreferencesScreen` card "Default routine time".

**Long terme (~3-5h+ chaque, vraies sessions)** :
- 💡 **Health Connect / Samsung Health** (step 3 conditionnel) — détecter si device Samsung **avant** d'afficher l'option (cf. `Build.MANUFACTURER == "samsung"`). Sinon ne rien afficher. Nécessite : Health Connect SDK + permissions manifest + flow consent + import workouts/poids/stats existants.
- 💡 **Calendar sync** (step 3 toggle) — permission `WRITE_CALENDAR` + intégration `CalendarContract` write-only des planned_workouts dans le calendrier système. UX "tu vois tes workouts à côté de tes meetings".
- 💡 **Quick tour overlay** (post-onboarding, EN) — tooltip-style guide qui highlight drawer / chrono / stats au 1er run après onboarding. Bibliothèque type ShowcaseView ou custom Compose. Skippable.

**Refusés explicitement par user 2026-05-11** :
- ❌ Fitness level (Beginner/Intermediate/Advanced)
- ❌ Goal type (Lose weight / Build muscle / etc.)
- ❌ Body measurements baseline détaillées
- ❌ Import data (Strava, MyFitnessPal, Hevy, etc.)
- ❌ Privacy / RGPD consent (pas pertinent mono-user)
- ❌ Avatar / profile pic
- ❌ Connect with friends

### [~] 🟠 Vrai support Dark/Light mode (suite Option III-c, scope étendu) — **partiel** (infra livrée, refactor ~50 callsites couleurs pending)

**Contexte 2026-05-11** : Option III-c a livré l'infrastructure du toggle theme (`ThemeMode` enum LIGHT/DARK/SYSTEM persisté + UI Settings + Onboarding step 2 + `MainActivity` observe `themeMode` et passe à `SportAppTheme(darkTheme=...)`). MAIS : visuellement le toggle n'a quasi aucun effet aujourd'hui car **toutes les couleurs de l'app sont hardcodées en mode dark** (`ButtonPrimaryColor`, `thirdBlue`, `boxBlue`, `firstBlue`, `SessionTabBackground`, `SessionExerciseScreenBackground`, `darkGray`, `lightGrayBlue`, `redMedium`, `mediumGreen`, `orangeMedium`, etc.). Le `SportAppTheme` switch entre `DarkColorScheme`/`LightColorScheme` M3 mais ces schemes ne sont consommés que par les widgets M3 par défaut (Switches non-Custom, TextFields non-Custom, dialogs M3, status bar) -- pas par les écrans custom.

**Pourquoi** : Pour un vrai switch dark/light visible, il faut que **chaque couleur** affichée par l'app soit déclinée en 2 variantes (light + dark) et résolue selon le `themeMode` courant. Aujourd'hui l'app est conçue 100% dark mode et un user qui choisit "Light" ne verra qu'une infime différence cosmétique.

**Scope du chantier** :
1. **Audit complet** des couleurs custom dans [ui/theme/Color.kt](../appli-android/app/src/main/java/com/example/sportapp/ui/theme/Color.kt) -- déterminer pour chacune sa variante light équivalente (souvent : inverser saturation/luminance, garder la teinte).
2. **Refactor `Color.kt`** : pour chaque couleur custom, créer 2 vals (`xxxDark` + `xxxLight`) ou un Composable `getXxx()` qui lit `MaterialTheme.colorScheme.isLight` (ou un CompositionLocal `LocalAppColors`).
3. **Pattern recommandé** : créer un `data class AppColors` qui contient toutes les couleurs custom, avec 2 instances `appColorsDark` + `appColorsLight`, exposé via un `CompositionLocal LocalAppColors`. Chaque composable remplace les imports `import com.example.sportapp.ui.theme.thirdBlue` par `LocalAppColors.current.thirdBlue` (ou helper `appColors().thirdBlue`).
4. **Scan + refactor des callsites** : ~50 fichiers UI (chrono, admin, onboarding, settings, stats, etc.) qui utilisent les couleurs hardcodées. **Estimation : 1-2 sessions full**.
5. **Tester runtime tel** : toggle Dark→Light dans Settings doit basculer **toute l'UI** instantanément (pas juste les widgets M3).

**Risque "tout casser"** : énorme si fait à l'arrache. Le moindre composant manqué reste en dark sur fond light = illisible. Approche pragmatique : faire le refactor par modules indépendants (1 module à la fois, build + smoke à chaque), pas d'un coup.

**Livrables Option III-c (déjà faits)** :
- ThemeMode enum + DataStore + setters VM + UI Settings + UI Onboarding ✅
- `MainActivity` observe + passe `darkTheme` à `SportAppTheme` ✅

**Reste à faire (cette feature)** :
- Audit + refactor des ~50 callsites couleurs hardcodées.
- LocalAppColors CompositionLocal pattern.
- Test runtime toggle dark/light visible end-to-end.

**Note** : on peut aussi décider que l'app reste **dark only** et retirer le toggle Theme (cohérent avec design choisi). Décision à prendre avant le chantier.

---

### [-] 💡 ~~Onboarding initial UI (post-signup, déféré V8.4+)~~ — **doublon** (remplacé par B1 livré ci-dessus)

**Contexte initial (avant livraison)** : Si on ajoute un signup public (cf. §1) ET le pré-seed automatique, l'onboarding UI devient un "polish" optionnel pour personnaliser l'expérience. Sans onboarding mais avec pré-seed, le user peut au moins commencer à utiliser l'app.

**Proposition initiale** : Flow d'onboarding court (3-5 écrans) après le 1er signup, avec navigation claire et possibilité de skip à chaque étape :

1. **Écran 1 — Bienvenue** : message + champ "Comment veux-tu qu'on t'appelle ?" (édite le `first_name`).
2. **Écran 2 — Préférences temporelles** : jour de début de semaine (lundi/dimanche), heure matinale par défaut pour les routines (06:00 par exemple), zone horaire auto-détectée mais affichée pour validation.
3. **Écran 3 — Sélection de muscles** : grille des muscles pré-seedés (cf. feature ci-dessus), user peut désélectionner ce qu'il ne veut pas tracker (ex. mollets si CrossFit pur). Action = soft-delete (pendingDeletion=true) sur les muscles non sélectionnés.
4. **Écran 4 — Sélection d'exercises** : pareil, liste pré-seedée, user désélectionne ceux qu'il ne pratique pas. Bouton "Suggérer plus" qui montre une liste populaire complémentaire.
5. **Écran 5 — Permissions** : "Recevoir des notifs ? (rappels routines, fin de timer, etc.)" + redirige vers Settings Android pour le toggle système. + "Activer le sync auto en background ?" (paramètre app).

À la fin → home directement.

**Côté technique** :
- Détecter "1er run après signup" via flag `SharedPreferences("onboarding_done", false)`. Set true après l'écran 5 (ou skip explicite).
- Nouveaux écrans : `OnboardingWelcomeScreen`, `OnboardingPreferencesScreen`, `OnboardingMusclesScreen`, `OnboardingExercisesScreen`, `OnboardingPermissionsScreen` + `OnboardingViewModel` partagé.
- Routes : `Routes.ONBOARDING_*` (5 routes ou 1 route paramétrique avec step index).
- Hors scope MVP, à faire après V8.4 (Stats / refonte snackbars). *(History supprimé — cf. B3-1 ci-dessus.)*

### [x] ✅ UI admin pour gérer les `is_admin` — livré 2026-05-11

**Livré** : 2 commits (serveur + Android), ~3h.

**Serveur** (commit `b7d19b2`) :
- Schema `UserAdminToggle` Pydantic (`isAdmin: bool` alias).
- Helpers crud : `count_admins()` + `set_user_admin(db, user_id, is_admin)`.
- Endpoint `PATCH /api/v1/users/{user_id}/admin` retourne `UserOut` (avec isAdmin).
- Idempotent : valeur identique → 200 no-op.
- Self-protect : refuse 400 si current_admin tente de se demote (anti-bricking).
- Last-admin protect : refuse 400 si `count(admins) <= 1`.
- 404 user_id inexistant. 403 caller pas admin (require_admin guard).
- `GET /api/v1/users` passé de `UserPublic` -> `UserOut` pour exposer isAdmin par row.
- 6 tests pytest verts (test_admin_endpoints.py) : 22 total (16+6) 0 failure.

**Android** (commit `28fae74`) :
- Module `admin/` Style A flat : `data/AdminUserDto.kt` + `data/AdminApi.kt` +
  `ui/AdminUsersViewModel.kt` (@HiltViewModel sealed UiState + toggleMessage flow) +
  `ui/AdminUsersScreen.kt` (LazyColumn + Snackbar + AlertDialog) +
  `ui/components/AdminUserRow.kt` (Switch désactivé sur la row currentUser, label "(vous)") +
  `ui/components/AdminToggleDialog.kt` (confirmation explicite avant toggle).
- `CurrentUserManager` étendu avec `isAdminFlow: StateFlow<Boolean>` + `setUserAdmin()` +
  persistance `KEY_IS_ADMIN`. 3 callsites de `setUserId` updated (verifyToken,
  NetworkMonitor reconnect, ProfileScreen refresh).
- Drawer : section "Admin" conditionnelle (visible si `isAdminFlow == true`)
  avec item "Manage users" -> `Routes.ADMIN_USERS`.
- Routes + MainActivity NavHost : route ADMIN_USERS branché avec slide horizontal.
- `assembleRelease` OK 27s. APK installé S21+.

**À tester runtime S21+** : (1) re-login (pour que `/me` peuple isAdmin) ; (2) ouvrir drawer → vérifier la section "Admin" apparaît ; (3) "Manage users" → liste avec Switch par user, "(vous)" sur la propre row + Switch grisé ; (4) toggle un autre user → dialog confirmation → snackbar succès, ligne refresh ; (5) tester self-demote (Switch grisé, pas de moyen UI) ; (6) tester demote du dernier admin → 400 (mais nécessite scenario rare).

---

## §8 — Performance

### [x] ✅ Indexes Postgres sur `user_id` (12 tables) — livré F4c (28 indexes)

**Contexte** : Aucun index Postgres sur `user_id` ([TODO_FIXES §10](TODO_FIXES.md#10--performance)). Queries `WHERE user_id = ?` font seq scan.

**Proposition** : Ajouter `Index('ix_<table>_user_id', user_id)` sur les 12 tables Type A (`actual_workouts`, `exercises`, `muscles`, `muscle_goals`, `notifications`, `planned_workouts`, `routine_periods`, `routine_tasks`, `routine_task_checks`, `superset_groups`, `muscle_weekly_summary` (à supprimer), `users` lui-même). Coût marginal en stockage, gain perfo significatif au-delà de quelques milliers de lignes par table.

### [x] ✅ Indexes Postgres sur `<parent>_uuid` (FK) — livré F4c

**Contexte** : Postgres ne crée pas d'index automatique sur le côté `referencing` d'une FK. Queries `WHERE actual_workout_uuid = ?` (très fréquentes pour récupérer les exercises d'un workout) font seq scan.

**Proposition** : Ajouter explicitement les indexes sur les FK `<parent>_uuid` pour les 9 tables qui en ont (`actual_workout_exercises.actual_workout_uuid`, `actual_workout_exercises.exercise_uuid`, `actual_workout_sets.actual_workout_exercise_uuid`, etc.).

### [x] ✅ Indexes Notification (composite) — livré

**Contexte** : DBML annonce `(user_id, created_at)`, `(user_id, read_at)`, `(user_id, dedupe_key) [unique]` mais non implémentés. Queries fréquentes : `observeAll() ORDER BY created_at DESC`, `observeUnreadCount` filtre par `read_at IS NULL` + `pendingDeletion = 0`.

**Proposition** : Implémenter les 3 indexes côté SQLAlchemy + Room pour aligner avec le DBML.

### [ ] 💡 Room journal mode WAL (vs TRUNCATE actuel) — **à mesurer puis décider**

**Contexte** : Aujourd'hui Room utilise `setJournalMode(RoomDatabase.JournalMode.TRUNCATE)`. WAL est généralement plus performant en lectures concurrentes.

**Pourquoi** : Si l'app fait beaucoup de lectures parallèles (Compose + sync simultané), WAL réduit les contentions.

**Proposition** : Tester WAL sur un device et mesurer impact sur le storage + perfs (WAL utilise plus d'espace temporairement). Décider après mesure.

---

## §9 — 💡 Suggestions Claude (à valider)

> Items non explicitement mentionnés dans le projet (docs / mémoire / discussion) mais découlant logiquement des findings d'audit. À **valider par l'utilisateur** avant intégration aux sections principales.

### [x] ✅ Suggestion : `Authenticator` OkHttp pour gestion 401 proactive — livré V8.2 (Authenticator complet avec refresh auto + mutex)

Découle directement de [TODO_FIXES §1 + §3 "Pas de gestion 401 proactive Android"](TODO_FIXES.md#1--sécurité). Pas explicitement formulé mais logique :

**Proposition** : Implémenter `Authenticator` OkHttp dans `RetrofitInstance` :
```kotlin
private val authAuthenticator = Authenticator { _, response ->
    if (response.code == 401) {
        TokenManager.clearToken(appContext)
        CurrentUserManager.clearUserId(appContext)
        // Trigger navigate to login via SnackbarController + auth event flow
        SyncEvents.onTokenExpired.emit(Unit)
    }
    null  // Don't retry the request
}
```

**Lié à** : [§1 Refresh Token](#1--sécurité-auth--secrets) — l'`Authenticator` peut aussi gérer le refresh automatique si refresh token disponible.

### [x] ✅ Suggestion : Logger structuré côté serveur — livré T2.4 + T4.3 (basicConfig + format JSON toggle via env)

Plusieurs items TODO_FIXES mentionnent `print` au lieu de `logger`. Pas d'item global "mettre en place un logging propre".

**Proposition** : Adopter `structlog` ou `loguru` côté serveur. Niveau configurable via env var `LOG_LEVEL`. Format JSON en prod (parseable), format human-readable en dev. Tags par module (`logger = get_logger("ws_hub")`).

### [x] ✅ Suggestion : Healthcheck endpoint — livré 2026-05-12 (`GET /healthz` public, SELECT 1 + pytest)

**Pourquoi** : Pour le monitoring (CI/CD, uptime checker, etc.).

**Proposition** : `GET /healthz` qui retourne `{"status": "ok", "db": "ok|ko", "ts": "..."}`. `db` checke une simple `SELECT 1`. Public (pas d'auth).

### [x] ✅ Suggestion : Compression gzip sur les responses REST — livré 2026-05-12 (`GZipMiddleware` minimum_size=1000)

**Pourquoi** : Les responses peuvent être grosses (par ex. `getAll() exercises` + `instructions` JSONB → 50-100 KB). Gzip réduit de ~70%.

**Proposition** : Ajouter `GZipMiddleware` FastAPI sur `/exercises`, `/actual-workouts`, etc. (au-dessus de 1 KB).

### [ ] 💡 Suggestion : Métriques basiques (Prometheus) — **à faire**

**Pourquoi** : Visibilité sur le système en prod (nombre de WS connectés, latence requêtes, erreurs 5xx).

**Proposition** : `prometheus-fastapi-instrumentator` (drop-in middleware). Endpoint `/metrics`. Grafana sur la Pi pour visualisation. À faire seulement si l'utilisateur veut investir dans le monitoring.

### [x] ✅ Suggestion : Backup automatique de la DB Pi — livré 2026-05-12 (script `serveur/backup_pi.sh` + doc DEV_GUIDE §9, crontab Pi reste ops manuelle)

**Pourquoi** : Aujourd'hui aucun backup. Si la Pi crash ou la DB se corrompt, toutes les données sont perdues.

**Proposition** : Cron quotidien sur la Pi : `pg_dump fittracker | gzip > /home/william/backups/fittracker-$(date +%F).sql.gz`. Rotation : garder 7 jours + 1 par mois pour 12 mois. Optionnel : copier vers un cloud (rsync vers OneDrive/GDrive ou backup Borg sur un autre serveur).

### [x] ✅ Suggestion : Endpoint `DELETE /me` (suppression de compte) — **livré 2026-05-21**

**Pourquoi** : RGPD si l'app s'ouvre à plusieurs users + bonne hygiène pour app perso (tester un nouveau seed sans avoir à drop la DB).

**Proposition initiale** : `DELETE /me` (auth obligatoire) qui : (a) supprime le user (ON DELETE CASCADE supprimera tout son contenu), (b) invalide le token courant.

**Livré — serveur** : `DELETE /api/v1/me` dans `auth_router.py`, à côté de `/me` et `/me/profile` (même pattern self-only : décodage JWT, pas de `require_admin`). Décisions validées avec l'utilisateur :
- **Confirmation par mot de passe** : body `MeDeleteRequest {password}` re-vérifié via `verify_password` avant suppression (évite suppression accidentelle / JWT volé). Mauvais mot de passe → **403** (et non 401 : l'user est authentifié, et un 401 serait intercepté par l'Authenticator OkHttp Android qui boucle refresh+retry).
- **Garde last-admin** : si le caller est admin et seul admin restant → 400 (cohérent avec la protection demote de `PATCH /users/{id}/admin`).
- **Réponse** : `UserOut` du compte supprimé (snapshot Pydantic figé avant le commit du delete).
- La suppression réutilise `crud.delete_user` ; les FK `user_id` étant toutes `ON DELETE CASCADE` (y compris `refresh_tokens`), Postgres supprime toutes les données user-scoped.

5 tests pytest (`tests/test_delete_me.py`) : succès + UserOut + relogin 401 · cascade `refresh_tokens` · mauvais mot de passe 403 · sans token 401 · last-admin 400. Suite serveur : **44 pytest verts**.

**Livré — Android** : bouton "Delete account" (zone sensible) dans `ProfileScreen`.
- `ApiUserService.deleteMe` (`@HTTP DELETE "me" hasBody=true` + DTO `MeDeleteRequest`).
- `ProfileScreenViewModel.deleteAccount(password, onDeleted)` : appelle `deleteMe`, **purge le Room local** (`registry.reversed.forEach { clearLocal() }` + `userDao.clearAll()` — comme l'outil "Clear DB"), puis déclenche la navigation. Mapping erreurs : 403 → mauvais mot de passe, 400 → dernier admin, autre → générique.
- `ProfileScreen` : `AlertDialog` de confirmation (avertissement irréversible + champ mot de passe `CustomTextField` masqué) → succès → snackbar + `navigate(Routes.LOGOUT)` (réutilise le flow logout existant : `stopAuth` → clear tokens/WS → LoginScreen).
- i18n : 9 clés `profile_delete_*` en EN + FR (politique 18).
- `compileDebugKotlin` OK + `testDebugUnitTest` OK (aucune régression).

**À tester runtime S21+** : ProfileScreen → "Delete account" → dialog → mauvais mot de passe (erreur inline) → bon mot de passe → compte supprimé + retour LoginScreen + re-login impossible.

### [ ] ⭐ Last-write-wins serveur sur les bulk-upserts (extension du fix 2026-05-07) — **partiel** (single ✅, bulk reste)

**Contexte** : 2026-05-07, le fix optimistic concurrency (`is_payload_stale`) a été appliqué aux **20 single-upserts** (`PUT /xxx/{uuid}`) — la voie chaude utilisée par `syncEngine.pushEntityClass`. Les **bulk-upserts** (`PUT /xxx/bulk`) n'ont pas le check : si on push 100 rows en bulk avec un payload plus ancien que le serveur, l'écrasement passe.

**Pourquoi** : Cohérence sémantique. Aujourd'hui le client utilise principalement le single-upsert (T4.2 SyncEngine), mais le bouton dev "Bulk push" et certains flows futurs utilisent le bulk → asymétrie.

**Proposition** : Étendre le check `is_payload_stale` aux fonctions `bulk_upsert_X` et `upsert_many_X` des CRUDs concernés. Pattern : iter sur le payload, skip les rows plus anciennes que leurs `existing` respectifs.

**Effort** : moyen (~20 endroits à patcher, mais le helper est en place). Ajouter pytest qui couvre le bulk.

**Quand** : si on ouvre l'app à plusieurs devices simultanés OU si on commence à utiliser le bulk en path chaud.

### [ ] 💡 Suggestion : Système de feature flags / config dynamique — **à faire** (si experimental needed)

**Pourquoi** : Activer/désactiver des features sans rebuild (par ex. tester une refonte sync en parallèle de l'ancienne, A/B test, killswitch d'une feature buggée).

**Proposition** : Table `feature_flags` côté serveur avec `key` + `enabled` + `user_id NULL` (global) ou spécifique. Endpoint `GET /flags` côté Android. Utiliser sparingly (seulement pour features expérimentales).

---

*TODO_FEATURES.md créé lors de l'étape 6 (2026-05-04). Étape suivante : 7 (REVIEW.md — plan d'amélioration classé par criticité, basé sur la combinaison TODO_FIXES + TODO_FEATURES).*
