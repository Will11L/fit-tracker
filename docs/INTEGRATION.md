# INTEGRATION — Mapping serveur ↔ appli Android

Référence concise du mapping entre les APIs Retrofit Android et les routers FastAPI. **Source de vérité vivante du contrat OpenAPI : Swagger sur [`/secure-docs`](https://<pi-fqdn>/secure-docs).** Pour les flux d'exécution (auth, sync REST, WebSocket realtime), voir [FLOWS.md](FLOWS.md). Pour le squelette canonique CRUD/router, voir [HOW_TO_ADD_ENTITY.md](HOW_TO_ADD_ENTITY.md).

> **À jour 2026-05-23.** Tous les endpoints applicatifs sous **`/api/v1/`** (T3.2). Pi tailnet-only via `<pi-fqdn>` (cf. [TAILSCALE_MIGRATION.md](TAILSCALE_MIGRATION.md)).

## §1 — Mapping entité ↔ router ↔ Retrofit Api

22 routers entité + auth + ws côté serveur, mappés 1:1 avec des Retrofit Apis côté Android (sauf cas spéciaux). Le pattern CRUD canonique (`/api/v1/<entities>` GET/PUT bulk + `/{uuid}` GET/PUT/DELETE) est respecté par les 22 entity routers.

| Domaine | Entité | Router | Retrofit Api | Notes |
|---|---|---|---|---|
| Workouts | `actual_workouts` | `actual_workout_router.py` | `ActualWorkoutApi.kt` | |
| | `actual_workout_exercises` | `actual_workout_exercise_router.py` | `ActualWorkoutExerciseApi.kt` | |
| | `actual_workout_sets` | `actual_workout_set_router.py` | `ActualWorkoutSetApi.kt` | |
| | `planned_workouts` | `planned_workout_router.py` | `PlannedWorkoutApi.kt` | |
| | `planned_workout_exercises` | `planned_workout_exercise_router.py` | `PlannedWorkoutExerciseApi.kt` | |
| | `cycle_workouts` | `cycle_workout_router.py` | `CycleWorkoutApi.kt` | endpoints corrigés V4.1 |
| Catalogue | `exercises` | `exercise_router.py` | `ExerciseApi.kt` | UNIQUE `(user_id, name)` (F8-Q4) |
| | `muscles` | `muscle_router.py` | `MuscleApi.kt` | refactor 3-niveaux 2026-05-08 |
| | `equipments` | `equipment_router.py` | `EquipmentApi.kt` | **Type C** (admin pour écrire) |
| | `available_equipments` | `available_equipment_router.py` | `AvailableEquipmentApi.kt` | **Type A** (F8-Q2) |
| | `exercise_muscles` | `exercise_muscle_router.py` | `ExerciseMuscleApi.kt` | junction, endpoints PUT par paire corrigés V4.1 |
| | `exercise_equipment` | `exercise_equipment_router.py` | `ExerciseEquipmentApi.kt` | |
| Goals | `muscle_goals` | `muscle_goal_router.py` | `MuscleGoalApi.kt` | `week_iso` wire = `weekISO` |
| Cycles | `training_cycles` | `training_cycle_router.py` | `TrainingCycleApi.kt` | Type A depuis V5.7 |
| Supersets | `superset_groups` | `superset_group_router.py` | `SupersetGroupApi.kt` | `user_id` nullable (legacy) |
| | `superset_exercises` | `superset_exercise_router.py` | `SupersetExerciseApi.kt` | |
| Routines | `routine_periods` | `routine_period_router.py` | `RoutinePeriodApi.kt` | col `order_index`, wire `"order"` |
| | `tasks` | `task_router.py` | `TaskApi.kt` | unifié Phase 0 2026-05-12 |
| | `task_checks` | `task_check_router.py` | `TaskCheckApi.kt` | UNIQUE `(user_id, task_uuid, occurrence_date)` |
| Notif | `notifications` | `notification_router.py` | `NotificationApi.kt` | persisté + push WS |
| User | `users` | `user_router.py` | `UserApi.kt` | écriture admin only (politique 8) |

## §2 — Endpoints spéciaux (hors mapping canonique)

| Endpoint | Verbe | Auth | Description | Source |
|---|---|---|---|---|
| `/api/v1/signup` | POST | public | Création de compte (pre-seed catalogue starter V8.4) | `auth_router.py` |
| `/api/v1/token` | POST | public (form-urlencoded) | Login → JWT access + refresh (V8.2) | `auth_router.py` |
| `/api/v1/me` | GET | auth | User du token | `auth_router.py` |
| `/api/v1/me/profile` | PATCH | auth (self) | Modifier first_name / last_name | `auth_router.py` |
| `/api/v1/users/{user_id}/admin` | PATCH | admin | Promote/demote `is_admin` (self-protect + last-admin protect) | `user_router.py` (B1 UI admin 2026-05-11) |
| `/api/v1/ws` | WS | auth (query token) | WebSocket realtime (cf. FLOWS §3) | `ws_router.py` |
| `/healthz` | GET | public | Healthcheck (root, sans rate limit) | `main.py` |
| `/secure-docs` | GET | public (Swagger custom) | Doc OpenAPI live | `main.py` |
| `/token-helper` | GET | public | UI mini pour générer un token (login Swagger) | `main.py` |
| `/webhook/deploy` | POST | HMAC GitHub | Auto-deploy Pi (T3.1, via Tailscale Funnel port 8443) | `webhook/webhook.py` |

## §3 — Patterns canoniques REST (V6.2)

Pour chaque entité (sauf cas spéciaux ci-dessus) :

| Op | Verbe | Path | Body | Réponse |
|---|---|---|---|---|
| Lire tous | `GET` | `/api/v1/<entities>` | — | `[XOut]` |
| Lire un | `GET` | `/api/v1/<entities>/{uuid}` | — | `XOut` |
| Upsert simple | `PUT` | `/api/v1/<entities>/{uuid}` | `XCreate` | `XOut` |
| Bulk upsert | `PUT` | `/api/v1/<entities>` | `[XCreate]` | `[XOut]` |
| Delete | `DELETE` | `/api/v1/<entities>/{uuid}` | — | `{"ok": true}` |

Conventions appliquées (post-vagues V2 + V6.2) :
- `user_id` injecté serveur, jamais lu du payload.
- Ownership check : `403` si `uuid` appartient à un autre user.
- `is_payload_stale(payload, existing)` rejette les push older-than-existing (concurrency 2026-05-07).
- Type C (`equipments`) : writes gated par `Depends(require_admin)`.

## §4 — Contrat HTTP

| Code | Quand | Conséquence Android |
|---|---|---|
| `200` | OK | Continue (markAsSynced, etc.) |
| `201` | Création (signup uniquement) | Continue |
| `400` | Bad request (uuid mismatch, validation Pydantic) | Log + skip |
| `401` | Token absent/expiré/invalide | `Authenticator` OkHttp tente refresh ; si échec → clear tokens + redirect login (V4.5 + V8.2) |
| `403` | Cross-user ou non-admin sur Type C | Log + skip (anomalie côté client) |
| `404` | UUID inexistant | Log + skip |
| `409` | Conflict (signup username pris, contraintes UNIQUE) | Snackbar erreur |
| `429` | Rate limit (slowapi sur `/token`) | Retry après backoff |
| `500` | Bug serveur | Log + retry exponentiel (`SyncCoordinator`) |

## §5 — Côté Android : architecture sync (post-T4.2)

Toutes les Retrofit Apis ci-dessus sont consommées via :
- **`SyncEngine`** ([sync/SyncEngine.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncEngine.kt)) — `pushAll`, `pushEntity`, `pullMerge`, `pullReplace`, `bulkPushAll`.
- **`SyncRegistry`** ([sync/SyncRegistry.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncRegistry.kt)) — 20 entités FK-aware (ordre parents → enfants pour éviter les crash FK au push).
- **`SyncCoordinator`** ([sync/SyncCoordinator.kt](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncCoordinator.kt)) — orchestration triggers login/network/user + retry exponentiel.
- **`SyncableEntity<T>`** — 20 implémentations canoniques exposant `observeAll`, `hasUnsynced`, `getAllUnsynced`, `markAsSynced`, `insertFromServer`, etc.
- **`SyncManager`** — passe à 89 lignes (mutex + UX snackbar global "Starting/Completed/Error").

Les anciennes `safeSync*WithSnackbar` et `sync<Entity>s()` ont été supprimées (T4.2 + B2). Côté ViewModel après une mutation locale : `syncEngine.pushEntityClass(<Entity>::class)`.

## §6 — Pour aller plus loin

- **Swagger live** : [`/secure-docs`](https://<pi-fqdn>/secure-docs) — login via `/token-helper`.
- **Inventaire à jour** : `for r in ROUTERS` dans [`serveur/app/main.py`](../serveur/app/main.py) (montage avec `prefix="/api/v1"`).
- **Détail squelette** : [SERVEUR.md §2B-1](SERVEUR.md).
- **Flux sync** : [FLOWS.md](FLOWS.md).
- **Protocole convergent multi-device** : [SYNC_PATTERN.md](SYNC_PATTERN.md).
