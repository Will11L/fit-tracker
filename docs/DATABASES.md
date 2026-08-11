# DATABASES — Schéma & conventions

Résumé concis du schéma de données du projet **sport-app** : 33 tables Postgres côté serveur, 21 entités Room côté Android, conventions de nommage et de versionning. Pour le tuto cross-stack d'ajout d'entité, voir [HOW_TO_ADD_ENTITY.md](HOW_TO_ADD_ENTITY.md). Pour les flux de sync, voir [SYNC_PATTERN.md](SYNC_PATTERN.md) + [FLOWS.md](FLOWS.md).

> **À jour 2026-05-23, étendu 2026-06-12 (Nutrition V1 : +8 tables), étendu 2026-06-17 (Santé V1 : +3 tables)** — Postgres : 33 tables (cf. `serveur/app/diagram.dbml`, généré depuis les modèles SQLAlchemy de `serveur/app/models/`). Room : `DATABASE_VERSION = 18` (cf. `appli-android/app/src/main/java/com/example/sportapp/data/local/AppDatabase.kt`), 21 entités (`refresh_tokens` reste serveur-only ; les 3 tables santé sont serveur-only tant que la couche Room n'est pas livrée — tâche 3).

## §1 — Sources de vérité

| Source | Rôle | Synchronisé avec |
|---|---|---|
| `serveur/app/models/*.py` (22 fichiers) | Schéma effectif Postgres (lu par Alembic via `target_metadata = Base.metadata`) | Postgres prod via `alembic upgrade head` |
| `serveur/app/alembic/versions/*.py` | Migrations incrémentales (autogenerate enabled depuis V3.4) | **Source de vérité du schéma effectif** : `deploy.sh` Pi exécute `alembic upgrade head` |
| `serveur/app/diagram.dbml` | Visuel projet (politique 14 : MAJ dans le **même commit** que les modèles) | À synchroniser à la main avec les modèles SQLAlchemy |
| `appli-android/app/src/main/java/com/example/sportapp/data/model/*.kt` | Entités Room (annoter `@Entity`) | Postgres via le wire JSON (snake_case ↔ camelCase) |
| `appli-android/app/schemas/<package>/<N>.json` | Schémas Room exportés par KSP au build (auto-générés) | Source de vérité Room (commités) |

**Règle d'or** : `Base.metadata.create_all` n'est autorisé que dans les scripts bootstrap/reset/seed (`setup_db.py`, `reset_db.py`, `fill_database.py`). Toute modification de schéma incrémentale → `alembic revision --autogenerate -m "..."`. Cf. politique 16 dans [CLAUDE.md](../CLAUDE.md).

## §2 — Inventaire des 33 tables (par domaine)

### Auth & user (2)

- **`users`** — compte utilisateur. Champs : `username` (unique), `hashed_password` (bcrypt), `first_name`, `last_name`, `is_admin` (V1.3), `birth_date`, `sex` (UPPER_CASE MALE/FEMALE/OTHER), `height_cm`, `weight_kg` (canoniques cm/kg).
- **`refresh_tokens`** — refresh tokens long-lived (V8.2). bcrypt-hashed (jamais le token brut), `expires_at`, `revoked_at` (NULL = actif). Index `(user_id, revoked_at)`. **Serveur-only** (pas mappé côté Room).

### Workouts (6)

- **`actual_workouts`** — séances effectives. `name`, `date`, `is_done`, `notes`, `location`.
- **`actual_workout_exercises`** — exos d'une séance. `phase` (WARMUP/TRAINING/POST_TRAINING), `status` (NOT_STARTED/...), `order`, `sets`, `reps` (default `"0-1"`).
- **`actual_workout_sets`** — sets d'un exo de séance. `set_order`, `reps`, `weight`, `is_dropset`, `status` (default `NOT_STARTED`, F2c-1).
- **`planned_workouts`** — modèles de séances par jour. `name`, `day_of_week` (Monday/.../Sunday).
- **`planned_workout_exercises`** — exos d'une séance planifiée. `phase`, `status` (default `PLANNED`), `order`, `ignored`.
- **`cycle_workouts`** — junction `training_cycles ↔ planned_workouts`.

### Catalogue (6 — refs)

- **`exercises`** — exos custom user-scoped. `name` unique par user (`uq_exercises_user_id_name`), `instructions` (jsonb), `recommended_sets`, `recommended_reps`, `gif_url`.
- **`muscles`** — refactor 3-niveaux (2026-05-08). `name` (niveau précis : Triceps Long head, Mid Chest…), `muscle_group` (intermédiaire : Triceps, Pecs…), `zone` (haut : Arms, Chest…). User-scoped.
- **`exercise_muscles`** — junction. `coefficient` float (1.0 = muscle ciblé prioritairement).
- **`equipments`** — catalogue global d'équipements (Type C, modifiable par admin uniquement). `name` unique global.
- **`exercise_equipment`** — junction.
- **`available_equipments`** — équipements possédés par l'user (Type A user-scoped, F8-Q2 2026-05-06). `(user_id, name)` unique.

### Goals & routines (5)

- **`muscle_goals`** — objectif hebdo par muscle. `priority`, `target` (string), `done` (int), `week_iso` (wire JSON garde `"weekISO"` via alias Pydantic), `status` (IN_PROGRESS/...).
- **`training_cycles`** — programmes de plusieurs semaines (V5.7 : Type C → Type A user-scoped). `name`, `start_date`, `end_date`.
- **`routine_periods`** — fenêtres horaires (Matin / Midi / Soir). `name`, `start_time` (HH:MM string), `end_time`, `order_index` (col Postgres `order_index`, wire JSON garde `"order"` via Pydantic, F7-1).
- **`tasks`** — tâches récurrentes (Phase 0 2026-05-12 : unification de `routine_tasks` + tâches one-shot). `recurrence_kind` ∈ NONE/DAILY/WEEKLY/MONTHLY/YEARLY. Champs conditionnels nullable validés par Pydantic selon le kind. `excluded_dates` (jsonb array, mode "Only this").
- **`task_checks`** — checks par jour par tâche. UNIQUE `(user_id, task_uuid, occurrence_date)` = 1 check max par jour.

### Supersets (2)

- **`superset_groups`** — groupes de supersets. `user_id` nullable (cas legacy).
- **`superset_exercises`** — junction.

### Notifications (1)

- **`notifications`** — notif persistées côté serveur, push WS. `type` (ROUTINE_PERIOD_START / TIMER_DONE / WORKOUT_RESULT / …), `level` (info/success/warning/error), `data` (jsonb payload libre), `dedupe_key` (anti-doublon côté Android pour PendingIntent ID).

### Nutrition (9 — Nutrition V1, 2026-06-12, cf. `docs/NUTRITION_DESIGN.md` §3 ; hydratation 2026-07-05)

- **`foods`** — catalogue d'aliments user-scoped (Type A). `source` UPPER_CASE (CUSTOM/CIQUAL/OFF), `source_ref` (code CIQUAL ou barcode OFF), macros `*_per_100g` (kcal/protein/carbs/fat obligatoires + 4 micros nullables D11), `is_favorite`, `archived`, `is_water` (boisson eau → auto-comptage hydratation 1 g = 1 ml, 2026-07-05 ; posé à l'import OFF via `en:waters` ou coché manuellement).
- **`food_portions`** — portions nommées d'un aliment (« 1 œuf = 60 g »). Ownership indirect : FoodPortion → Food → User. `label`, `grams`.
- **`recipes`** — plats composés ET repas enregistrés (D7). Type A. `kind` UPPER_CASE (RECIPE/SAVED_MEAL), `total_weight_g` (ratio cru/cuit, kind=RECIPE seulement).
- **`recipe_ingredients`** — ingrédient d'une recette. Ownership indirect : RecipeIngredient → Recipe → User. `food_uuid` référence vivante (CASCADE), `quantity_g`, `order_index` sans default (politique 10).
- **`meal_presets`** — périodes habituelles du journal (« Petit-déj »...), Type A (D10). `name`, `order_index`, `default_time` ("HH:MM" string).
- **`meals`** — repas du journal quotidien, Type A. Créé à la première entry seulement. `date` ("YYYY-MM-DD" string), `name` user-typed, `order_index`.
- **`meal_entries`** — table centrale : une consommation dans un repas. Ownership indirect : MealEntry → Meal → User. Snapshot D5 (macros per-100g + `display_name` + `portion_label` figés) ; FK `food_uuid`/`recipe_uuid` SET NULL informatives. `quantity_g`.
- **`nutrition_goals`** — cibles quotidiennes kcal + macros, Type A. `effective_from` ("YYYY-MM-DD"), `day_kind` UPPER_CASE (ALL en v1, default légitime politique 10), `kcal`, `protein_g`, `carbs_g`, `fat_g`.
- **`water_intakes`** — prises d'eau horodatées (hydratation), Type A. `date` ("YYYY-MM-DD" jour local), `amount_ml` (int > 0), `created_at` (instant de la prise), `updated_at`. Une row = un verre/une bouteille ; total du jour = `SUM(amount_ml)` sur la date (côté client). Objectif journalier versionné via `health_goals` (`type` WATER_ML).

### Santé (3 — Health Connect V1, 2026-06-17)

Métriques passives (pas + objectif, distance, calories actives, FC, sommeil). Pas d'import séances, pas de write-back. 3 tables Type A user-scoped (`user_id` FK CASCADE).

- **`health_step_counts`** — pas en buckets intraday, Type A. `date` ("YYYY-MM-DD"), `bucket_start` ("HH:MM" début de tranche), `steps`. Total quotidien = `SUM(steps)` sur la date ; le bucketing permet le near-real-time (ré-upsert du bucket courant en cours de journée).
- **`health_metrics`** — métriques passives génériques, vendor-agnostiques, Type A. `type` UPPER_CASE (HEART_RATE/SLEEP/DISTANCE/ACTIVE_CALORIES), `value`, `unit` self-describing (bpm/min/m/km/kcal…), `date` ("YYYY-MM-DD"), `start_time` ("HH:MM" optionnel pour les mesures intraday).
- **`health_goals`** — objectifs versionnés génériques (`type` + `target` + `effective_from`), Type A. `type` UPPER_CASE : **STEPS** (pas/jour), **WATER_ML** (hydratation, ml/jour — 2026-07-05) ; extensible. `target` float, `effective_from` ("YYYY-MM-DD"). Objectif actif d'un `type` un jour J = `max(effective_from <= J)`. NB : l'objectif d'hydratation vit ici (pas dans `nutrition_goals`, qui n'a que kcal + macros).

### Total : 34 tables

(2 auth/user + 6 workouts + 6 catalogue + 5 goals/routines + 2 supersets + 1 notif + 9 nutrition + 3 santé)

## §3 — Relations & ownership

Toutes les FK `user_id` sont en `delete: cascade` (suppression du user → suppression totale de ses données). La sécurité applicative (cf. politique 8) impose la **cascade ownership** : un set appartient à un exo qui appartient à une séance qui appartient à un user — toute op sur le set valide la chaîne jusqu'à User.

```
users
├── actual_workouts ─→ actual_workout_exercises ─→ actual_workout_sets
├── planned_workouts ─→ planned_workout_exercises
├── exercises ─→ exercise_muscles ─→ muscles
│           └─→ exercise_equipment ─→ equipments (global, admin)
│
├── muscle_goals ─→ muscles
├── training_cycles ─→ cycle_workouts ─→ planned_workouts
├── superset_groups ─→ superset_exercises ─→ exercises
├── available_equipments
├── routine_periods
├── tasks ─→ task_checks (CASCADE), task ─→ routine_periods (SET NULL)
├── notifications
└── refresh_tokens (serveur-only)
```

Visuel détaillé : ouvrir `serveur/app/diagram.dbml` dans [dbdiagram.io](https://dbdiagram.io/).

## §4 — Conventions cross-stack

### Nommage (politique 17)

- **Postgres + SQLAlchemy attr Python + Room column** : `snake_case` partout (ex. `user_id`, `updated_at`, `is_admin`, `muscle_group`).
- **Wire JSON (Pydantic alias + Kotlin property)** : `camelCase` (ex. `userId`, `updatedAt`, `isAdmin`, `muscleGroup`). Posé via `Field(..., alias="camelCase")` + `model_config = {"populate_by_name": True}` côté Pydantic ; côté Room, `@ColumnInfo(name = "snake_case")` aligne la colonne sur la convention DB.
- **Exception local-only** : `synced`, `pendingDeletion` côté Room restent en convention Kotlin (camelCase) sans `@ColumnInfo` — ne traversent jamais le wire. Audit T4.1 (2026-05-07).

### Valeurs d'états (politique 11)

Toutes les colonnes `status`, `phase`, `priority`, etc. sont en **UPPER_CASE** cohérent serveur + Android : `NOT_STARTED`, `IN_PROGRESS`, `DONE`, `WARMUP`, `TRAINING`, `POST_TRAINING`, `HIGH`, `MEDIUM`, `LOW`, etc. Helpers d'affichage côté Android pour traduction (`localizedDayOfWeek`, `localizedZone`).

### Dates (V3.2)

Format wire canonique : `YYYY-MM-DDTHH:MM:SS.UUUUUUZ` (UTC strict 6 décimales).

- Postgres : `iso_utc()` SQL helper (chargé via `iso_utc_helper.sql`)
- Pydantic : `UTCDateTime` (sérialisation custom)
- Android : `getNowISO8601()` (kotlinx-datetime, tronqué microsec)

### Defaults DB (politique 10)

Un `default` SQLAlchemy n'est légitime que si la valeur reflète une **sémantique métier majoritaire** : ex. `actual_workout_sets.status = "NOT_STARTED"` (set créé, pas encore commencé), `actual_workout_exercises.phase = "TRAINING"` (exo classique vs WARMUP/POST_TRAINING). Pas de default sur les positions positionnelles (`set_order`, `order_index`).

### `user_id` jamais lu du payload (politique 8)

Tous les CRUDs (post-V2.1+V2.2) injectent `user_id` depuis `Depends(get_current_user_id)`. Si `dto.userId` est envoyé par le client, il est ignoré côté serveur. Cross-user ownership renvoie `403`.

### Soft-delete : aucun (V5.5)

Pas de colonne `deleted_at`. Les deletes sont **hard** côté Postgres. La convergence cross-device est assurée par le client : au pull/merge, le client supprime tous ses locaux `synced=true` dont la `key NOT in remote_response`. Cf. [SYNC_PATTERN.md](SYNC_PATTERN.md).

## §5 — Lifecycle des migrations

### Postgres (Alembic)

1. Modifier `serveur/app/models/<entity>.py`.
2. `alembic revision --autogenerate -m "<vague>_<change>"` génère le fichier migration.
3. **Toujours relire la migration** (autogenerate peut rater certains cas : RENAME COLUMN devient DROP + ADD, etc.).
4. `alembic upgrade head` localement pour tester.
5. **Reload triggers si la migration RENAME/DROP une col référencée par un fragment SQL** (politique 15, F7-fix) :
   ```python
   from app.triggers_loader import compose_function_sql
   op.execute(compose_function_sql())
   ```
6. MAJ `serveur/app/diagram.dbml` dans le **même commit** (politique 14).
7. MAJ `serveur/app/seed_database.py` / `fill_database.py` si la migration ajoute des champs obligatoires (politique 13).
8. Pi prod applique automatiquement via `deploy.sh` (`git pull && alembic upgrade head && systemctl restart sportapi`).

### Room (Android)

1. Modifier `data/model/<Entity>.kt` (ajout col, etc.).
2. Bumper `DATABASE_VERSION` dans `data/local/AppDatabase.kt`.
3. Écrire `MIGRATION_<old>_<new>` dans `data/local/migrations/Migrations.kt` (CREATE INDEX / ALTER TABLE).
4. KSP régénère `app/schemas/<package>/<new>.json` au build — commiter le fichier.
5. Au cold start de l'app installée, Room exécute la migration in-place.

## §6 — Quick reference

| Info | Valeur |
|---|---|
| Total tables Postgres | 22 |
| Total entités Room | 21 (= 22 − `refresh_tokens` serveur-only) |
| Version Room actuelle | `DATABASE_VERSION = 18` |
| Tables Type C (modif admin only) | `equipments` |
| Tables Type A (user-scoped) | toutes les autres sauf `refresh_tokens` |
| Tables sans push WS | `users`, `refresh_tokens` |
| Triggers `notify_row_change` | 20 fragments (cf. `triggers_loader.py:PER_TABLE_FRAGMENTS`) |

## §7 — Pour aller plus loin

- **Visuel** : `serveur/app/diagram.dbml` → coller dans [dbdiagram.io](https://dbdiagram.io/).
- **Modèles SQLAlchemy** : `serveur/app/models/*.py` (22 fichiers).
- **Migrations Alembic** : `serveur/app/alembic/versions/*.py`.
- **Schémas Room exportés** : `appli-android/app/schemas/com.example.sportapp.data.local.AppDatabase/<N>.json` (auto-générés KSP, sources de vérité Room).
- **DAOs Room** : `appli-android/app/src/main/java/com/example/sportapp/data/local/*Dao.kt` (Style A V6.2-IV : wrappers publics qui posent `synced=false`, délégation à `*Internal`, `*FromServer` pour préserver payload serveur).
- **Politiques cross-cutting** : [CLAUDE.md](../CLAUDE.md) §8 sécurité, §9 squelette, §10 defaults, §11 UPPER_CASE, §14 traçabilité DBML, §15 reload triggers, §16 Alembic, §17 nommage.
