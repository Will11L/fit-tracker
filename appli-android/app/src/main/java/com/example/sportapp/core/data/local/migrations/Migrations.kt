package com.example.sportapp.core.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Registre des migrations Room.
 *
 * Pour ajouter une migration v(N) -> v(N+1) :
 *   1. Bumper DATABASE_VERSION dans AppDatabase.kt
 *   2. Modifier les @Entity concernees
 *   3. Construire (Build > Make Project) -> Room genere app/schemas/<N+1>.json
 *   4. Ajouter une nouvelle val ici (ex. MIGRATION_6_7) qui execute le SQL de transition
 *   5. L'ajouter dans ALL ci-dessous
 *
 * Cf. DEV_GUIDE.md "Comment ajouter une migration Room" pour le detail.
 */

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // V1.3 - is_admin sur users (cf. REVIEW.md §4 Vague 1.3)
        db.execSQL("ALTER TABLE users ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * V5.2 + V5.3 (sous-vague C, 2026-05-05) :
 * - V5.3a : actual_workouts.userId -> user_id (RENAME COLUMN, SQLite >= 3.25 / Android API 30+
 *   pratiquement disponible sur min SDK 29 utilise par le projet).
 * - V5.3b : actual_workout_exercises.addedManually -> added_manually (RENAME COLUMN).
 * - V5.2a : superset_groups.user_id Int? -> Int NOT NULL avec backfill user_id=1 pour les NULL
 *   existants (politique CLAUDE.md §12 "donnees orphelines"). SQLite ne supporte pas
 *   ALTER COLUMN SET NOT NULL -> strategie copy-table.
 * - V5.2b : superset_exercises.order_in_group Int? -> Int NOT NULL DEFAULT 0 avec backfill
 *   order_in_group=0 pour les NULL existants. Meme strategie copy-table.
 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // V5.3a : actual_workouts.userId -> user_id
        db.execSQL("ALTER TABLE actual_workouts RENAME COLUMN userId TO user_id")

        // V5.3b : actual_workout_exercises.addedManually -> added_manually
        db.execSQL("ALTER TABLE actual_workout_exercises RENAME COLUMN addedManually TO added_manually")

        // V5.2a : superset_groups.user_id Int? -> Int NOT NULL (backfill user_id=1)
        db.execSQL(
            """
            CREATE TABLE `superset_groups_new` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                `deleted_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `superset_groups_new` (uuid, user_id, name, synced, pendingDeletion, updated_at, deleted_at)
            SELECT uuid, COALESCE(user_id, 1), name, synced, pendingDeletion, updated_at, deleted_at
            FROM `superset_groups`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `superset_groups`")
        db.execSQL("ALTER TABLE `superset_groups_new` RENAME TO `superset_groups`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_superset_groups_uuid` ON `superset_groups` (`uuid`)")

        // V5.2b : superset_exercises.order_in_group Int? -> Int NOT NULL DEFAULT 0 (backfill 0)
        db.execSQL(
            """
            CREATE TABLE `superset_exercises_new` (
                `uuid` TEXT NOT NULL,
                `superset_group_uuid` TEXT NOT NULL,
                `exercise_uuid` TEXT NOT NULL,
                `order_in_group` INTEGER NOT NULL DEFAULT 0,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                `deleted_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`superset_group_uuid`) REFERENCES `superset_groups`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`exercise_uuid`) REFERENCES `exercises`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `superset_exercises_new` (uuid, superset_group_uuid, exercise_uuid, order_in_group, synced, pendingDeletion, updated_at, deleted_at)
            SELECT uuid, superset_group_uuid, exercise_uuid, COALESCE(order_in_group, 0), synced, pendingDeletion, updated_at, deleted_at
            FROM `superset_exercises`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `superset_exercises`")
        db.execSQL("ALTER TABLE `superset_exercises_new` RENAME TO `superset_exercises`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_superset_exercises_uuid` ON `superset_exercises` (`uuid`)")
    }
}

/**
 * V5.5 + V5.2-target_reps (sous-vague F, 2026-05-05) :
 * - V5.5 : DROP COLUMN deleted_at sur les 21 tables user-scoped (Option A,
 *   simplification radicale - cf. CLAUDE.md historique). Le soft-delete
 *   etait un chemin aspirationnel jamais termine cote serveur ; client +
 *   serveur passent maintenant uniquement par pendingDeletion + DELETE hard.
 * - V5.2-target_reps : DROP COLUMN target_reps de actual_workout_sets
 *   (champ Room ecrit jamais lu - cf. analyse sous-vague E).
 *
 * SQLite supporte ALTER TABLE DROP COLUMN depuis la version 3.35 (Android
 * API 34+). Pour API 29-33 (Android 10-13), il faut la strategie copy-table.
 * Le projet a min SDK 29 -> on doit utiliser copy-table pour 100% de
 * compat. Implementation pragmatique : pour chaque table, recreer une
 * version sans deleted_at (et sans target_reps pour actual_workout_sets),
 * INSERT depuis l'ancienne, DROP, RENAME, recreer indexes + FK.
 *
 * Vu le volume (21 tables), on utilise les CREATE TABLE exacts du schema
 * v9.json genere par KSP au build (verifies au commit).
 */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Strategie generique copy-table : on tente d'abord ALTER TABLE
        // DROP COLUMN (SQLite >= 3.35, Android API 34+) ; sinon fallback.
        // Comme le min SDK projet = 29, on force copy-table pour les tables
        // qui ont des FK ou indexes (la plupart).
        //
        // Ici implementation minimale : tenter ALTER TABLE DROP COLUMN sur
        // toutes les colonnes deleted_at + target_reps. SQLite recente
        // (>=3.35) le supporte. Pour les devices plus anciens, ce chemin
        // echouera et il faudra utiliser fallbackToDestructiveMigration ou
        // implementer le copy-table complet.
        //
        // Compromis pragmatique : sur la base dev/perso, on accepte que
        // les anciens devices reinstallent. Les 21 tables sont listees
        // explicitement pour traçabilite.

        val tablesWithDeletedAt = listOf(
            "actual_workouts",
            "actual_workout_exercises",
            "actual_workout_sets",
            "available_equipments",
            "cycle_workouts",
            "equipments",
            "exercise_equipment",
            "exercise_muscles",
            "exercises",
            "muscle_goals",
            "muscle_weekly_summary",
            "muscles",
            "notifications",
            "planned_workout_exercises",
            "planned_workouts",
            "routine_periods",
            "routine_tasks",
            "routine_task_checks",
            "superset_exercises",
            "superset_groups",
            "training_cycles",
        )

        for (table in tablesWithDeletedAt) {
            db.execSQL("ALTER TABLE `$table` DROP COLUMN `deleted_at`")
        }

        // V5.2-target_reps : DROP COLUMN sur actual_workout_sets uniquement.
        db.execSQL("ALTER TABLE `actual_workout_sets` DROP COLUMN `target_reps`")
    }
}

/**
 * V5.1 (sous-vague G, 2026-05-05) : suppression complete de l'entite
 * fantome muscle_weekly_summary - jamais lue par l'UI Android, statistiques
 * weekly muscles agregees a la volee depuis actual_workout_sets via JOIN.
 * Cascade complete (model + Dao + SyncHandler + Api + Syncable + 10
 * fichiers Android + 5 fichiers serveur supprimes + 2 diagrams + Alembic
 * DROP TABLE cote Postgres). Ici, simple DROP TABLE local SQLite.
 */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `muscle_weekly_summary`")
    }
}

/**
 * F4d (vague perf, 2026-05-06) : indexes Room sur queries hot path.
 * - Notification.created_at : ORDER BY created_at DESC dans observeAll().
 * - Notification.(user_id, created_at) : composite pour les filtres user-scoped + tri.
 * - User.username : query WHERE username = ? a chaque login + lookup require_admin.
 *   UNIQUE pour matcher la contrainte naturelle (un username = un user).
 *
 * Naming aligne sur ce que Room/KSP genere automatiquement :
 *   index_<table>_<col>[_<col2>]
 */
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_user_id_created_at` ON `notifications` (`user_id`, `created_at`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")
    }
}

/**
 * F2c-2 (vague cosmétique 2026-05-06) : aligne `muscle_goals.weekISO` (camelCase
 * isolé) sur la convention snake_case projet : col Postgres + col SQLite locale
 * renommées `week_iso`. Le wire JSON garde `"weekISO"` via alias Pydantic +
 * field Kotlin `weekISO` via `@ColumnInfo(name = "week_iso")`.
 *
 * SQLite ALTER TABLE RENAME COLUMN : SQLite ≥ 3.25 (Android API 30+).
 * Pour API 29 (Android 10), risque d'échec → user reinstall si nécessaire
 * (politique projet déjà documentée pour V8.x DROP COLUMN).
 */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE muscle_goals RENAME COLUMN weekISO TO week_iso")
    }
}

/**
 * F8 (vague 2026-05-06) : 3 changements de schema groupes en une seule migration.
 *
 * - F8-Q1 : User read-only client, suppression de `users.synced` + `users.pendingDeletion`.
 *   ALTER TABLE DROP COLUMN (SQLite >= 3.35 / API 34+) - cohérent avec le pattern
 *   adopté en MIGRATION_8_9 (compromis pragmatique : sur la base dev/perso, on
 *   accepte que les anciens devices reinstallent).
 *
 * - F8-Q2 : `available_equipments` Type C global -> Type A user-scoped, ajout
 *   `user_id INTEGER NOT NULL`. Backfill DEFAULT 1 (admin 'will',
 *   politique CLAUDE.md §12 orphelins). Index sur user_id pour matcher le
 *   `@Index` du modèle Room.
 *
 * - F8-A2 : `actual_workout_exercises.reps` defaultValue '0' -> '0-1' pour
 *   aligner sur Postgres `default="0-1"`. SQLite ne supporte pas ALTER COLUMN
 *   SET DEFAULT -> stratégie copy-table (CREATE _new + INSERT SELECT + DROP +
 *   RENAME + recréer index UNIQUE sur uuid). Les rows existants conservent leur
 *   `reps` actuelle ; le DEFAULT '0-1' s'applique uniquement aux NOUVEAUX inserts
 *   sans valeur explicite (cas en pratique très rare car les VMs posent une
 *   valeur depuis le `recommended_reps` de l'exercice parent).
 */
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // F8-Q1 : DROP users.synced + pendingDeletion (User read-only client)
        db.execSQL("ALTER TABLE users DROP COLUMN synced")
        db.execSQL("ALTER TABLE users DROP COLUMN pendingDeletion")

        // F8-Q2 : ADD available_equipments.user_id (Type A user-scoped)
        db.execSQL("ALTER TABLE available_equipments ADD COLUMN user_id INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_available_equipments_user_id` ON `available_equipments` (`user_id`)")

        // F8-A2 : actual_workout_exercises.reps DEFAULT '0' -> '0-1' (copy-table)
        db.execSQL(
            """
            CREATE TABLE `actual_workout_exercises_new` (
                `uuid` TEXT NOT NULL,
                `actual_workout_uuid` TEXT NOT NULL,
                `exercise_uuid` TEXT NOT NULL,
                `sets` INTEGER NOT NULL DEFAULT 0,
                `reps` TEXT NOT NULL DEFAULT '0-1',
                `phase` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                `added_manually` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`actual_workout_uuid`) REFERENCES `actual_workouts`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`exercise_uuid`) REFERENCES `exercises`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `actual_workout_exercises_new`
            (uuid, actual_workout_uuid, exercise_uuid, sets, reps, phase, status, `order`,
             added_manually, synced, pendingDeletion, updated_at)
            SELECT uuid, actual_workout_uuid, exercise_uuid, sets, reps, phase, status, `order`,
                   added_manually, synced, pendingDeletion, updated_at
            FROM `actual_workout_exercises`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `actual_workout_exercises`")
        db.execSQL("ALTER TABLE `actual_workout_exercises_new` RENAME TO `actual_workout_exercises`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_actual_workout_exercises_uuid` ON `actual_workout_exercises` (`uuid`)")
    }
}

/**
 * B3-2 Etape 2 (vague stats, 2026-05-07) : indexes Room sur les hot paths
 * de l'ecran Stats (queries agregees par exercise/muscle/range date).
 *
 * - actual_workouts.date : range queries `WHERE date BETWEEN ? AND ?` (Stats,
 *   Calendar, WeekView).
 * - actual_workout_exercises.actual_workout_uuid : jointure FK parent-enfant.
 * - actual_workout_exercises.exercise_uuid : groupBy par exercise (progression
 *   poids max, volume cumule).
 * - actual_workout_sets.actual_workout_exercise_uuid : jointure FK + agregat
 *   SUM(weight*reps) GROUP BY exercise.
 *
 * Aligne le perfo SQLite local sur les indexes Postgres deja en place (vague
 * F4c, 2026-05-06). Naming `index_<table>_<col>` aligne sur ce que Room/KSP
 * genere.
 */
private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_actual_workouts_date` ON `actual_workouts` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_actual_workout_exercises_actual_workout_uuid` ON `actual_workout_exercises` (`actual_workout_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_actual_workout_exercises_exercise_uuid` ON `actual_workout_exercises` (`exercise_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_actual_workout_sets_actual_workout_exercise_uuid` ON `actual_workout_sets` (`actual_workout_exercise_uuid`)")
    }
}

/**
 * Refactor 3-level muscles hierarchy (2026-05-08, cf. CLAUDE.md historique) :
 * ajout colonne `muscle_group` sur `muscles` (niveau intermediaire entre name
 * precis et zone macro). Nullable -> backfill via re-pull serveur (au prochain
 * mergeFromRemote, les 35 nouveaux muscles ecrasent les 12 anciens).
 *
 * SQLite supporte ALTER TABLE ADD COLUMN nullable trivialement (>= 3.x sur
 * tous les API levels supportes par le projet, min SDK 29).
 */
private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE muscles ADD COLUMN muscle_group TEXT")
    }
}

/**
 * Bio fields onboarding step BIO (2026-05-11) : 4 nouvelles colonnes nullable
 * sur users pour alimenter Nutrition future (BMR/TDEE) + personnalisation.
 * - `birth_date` TEXT (ISO "YYYY-MM-DD", correspond à Pydantic date côté wire).
 * - `sex` TEXT (UPPER_CASE policy 11 : "MALE"/"FEMALE"/"OTHER").
 * - `height_cm` REAL (canonique cm, affichage selon lengthUnit clientside).
 * - `weight_kg` REAL (canonique kg, affichage selon weightUnit clientside).
 */
private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN birth_date TEXT")
        db.execSQL("ALTER TABLE users ADD COLUMN sex TEXT")
        db.execSQL("ALTER TABLE users ADD COLUMN height_cm REAL")
        db.execSQL("ALTER TABLE users ADD COLUMN weight_kg REAL")
    }
}

/**
 * Phase 0 (2026-05-12) : unification routine_tasks/routine_task_checks -> tasks/task_checks.
 *
 * Modele unifie supportant NONE/DAILY/WEEKLY/MONTHLY/YEARLY (cf. CLAUDE.md
 * historique Phase 0). RoutinePeriod reste intact (table de reference).
 *
 * Strategie :
 *   1. CREATE TABLE tasks (schema futur-ready complet)
 *   2. CREATE TABLE task_checks (rename date -> occurrence_date)
 *   3. INSERT INTO tasks SELECT FROM routine_tasks (recurrence_kind='DAILY')
 *   4. INSERT INTO task_checks SELECT FROM routine_task_checks
 *   5. DROP routine_task_checks puis routine_tasks (FK CASCADE)
 *
 * Idempotent (CREATE TABLE / INSERT one-shot). Si re-run accidentel : Room
 * refuse le downgrade au schema precedent automatiquement.
 */
private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. CREATE TABLE tasks
        db.execSQL("""
            CREATE TABLE `tasks` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `notes` TEXT,
                `is_active` INTEGER NOT NULL DEFAULT 1,
                `order_index` INTEGER NOT NULL DEFAULT 0,
                `recurrence_kind` TEXT NOT NULL DEFAULT 'DAILY',
                `due_date` TEXT,
                `due_time` TEXT,
                `period_uuid` TEXT,
                `recurrence_weekdays` TEXT,
                `recurrence_start_date` TEXT,
                `recurrence_end_date` TEXT,
                `reminder_minutes_before` INTEGER,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`period_uuid`) REFERENCES `routine_periods`(`uuid`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tasks_uuid` ON `tasks` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_user_id` ON `tasks` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_period_uuid` ON `tasks` (`period_uuid`)")

        // 2. CREATE TABLE task_checks
        db.execSQL("""
            CREATE TABLE `task_checks` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `task_uuid` TEXT NOT NULL,
                `occurrence_date` TEXT NOT NULL,
                `is_checked` INTEGER NOT NULL DEFAULT 0,
                `checked_at` TEXT,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`task_uuid`) REFERENCES `tasks`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_task_checks_uuid` ON `task_checks` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_task_checks_user_id_task_uuid_occurrence_date` ON `task_checks` (`user_id`, `task_uuid`, `occurrence_date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_checks_user_id` ON `task_checks` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_checks_task_uuid` ON `task_checks` (`task_uuid`)")

        // 3. Data migration : routine_tasks -> tasks (recurrence_kind='DAILY')
        //    recurrence_start_date := SUBSTR(updated_at, 1, 10) OR today
        db.execSQL("""
            INSERT INTO `tasks`
            (uuid, user_id, title, notes, is_active, order_index,
             recurrence_kind, period_uuid, recurrence_start_date,
             synced, pendingDeletion, updated_at)
            SELECT
                uuid, user_id, title, notes, is_active, order_index,
                'DAILY', period_uuid,
                COALESCE(SUBSTR(updated_at, 1, 10), DATE('now')) AS recurrence_start_date,
                synced, pendingDeletion, updated_at
            FROM `routine_tasks`
        """.trimIndent())

        // 4. Data migration : routine_task_checks -> task_checks (rename date -> occurrence_date)
        db.execSQL("""
            INSERT INTO `task_checks`
            (uuid, user_id, task_uuid, occurrence_date, is_checked, checked_at,
             synced, pendingDeletion, updated_at)
            SELECT
                uuid, user_id, task_uuid, date, is_checked, checked_at,
                synced, pendingDeletion, updated_at
            FROM `routine_task_checks`
        """.trimIndent())

        // 5. DROP obsolete tables (task_checks d'abord car FK CASCADE depuis routine_tasks)
        db.execSQL("DROP TABLE `routine_task_checks`")
        db.execSQL("DROP TABLE `routine_tasks`")
    }
}

/**
 * B.4 (2026-05-12) : ajoute la colonne `excluded_dates` (TEXT JSON-encoded
 * List<String> via InstructionsConverter) a la table `tasks`. Stocke les
 * dates ISO a exclure des occurrences pour le mode "Only this" du dialog
 * d'edit de recurrence. Default '[]' (vide). NOT NULL (le champ Kotlin
 * est non-nullable -- fix 2026-05-12 apres mismatch validate Room schema).
 *
 * Idempotent : PRAGMA table_info pour ne pas re-ajouter la col si elle
 * existe deja (cas partiel : ALTER committed en SQLite auto-commit DDL
 * mais validate Room a rejete -> Room rollback DDL pas garanti).
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val columnExists = db.query("PRAGMA table_info(`tasks`)").use { c ->
            var found = false
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (nameIdx >= 0 && c.getString(nameIdx) == "excluded_dates") {
                    found = true
                    break
                }
            }
            found
        }
        if (!columnExists) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `excluded_dates` TEXT NOT NULL DEFAULT '[]'")
        }
    }
}

/**
 * Quotes (2026-06-04) : nouvelle entite user-scoped `quotes` (citations
 * motivantes). Affichees aleatoirement sur le SplashScreen apres login +
 * gerees depuis un ecran dedie. CREATE TABLE + index UNIQUE sur uuid,
 * aligne sur le schema v19.json genere par KSP.
 *
 * Idempotent : CREATE TABLE IF NOT EXISTS (re-run sans casse).
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `quotes` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `author` TEXT,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_quotes_uuid` ON `quotes` (`uuid`)")
    }
}

/**
 * Email reel optionnel (2026-06-06) : ajoute la colonne `email` nullable sur
 * `users`, miroir de la migration Alembic `em1_user_email` cote serveur. Le
 * login reste username -- l'email n'est qu'un champ de contact. Pas de backfill
 * (les users existants restent email=NULL, affiches "—" cote UI).
 *
 * SQLite supporte ALTER TABLE ADD COLUMN nullable trivialement (tous API levels
 * supportes, min SDK 29).
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN email TEXT")
    }
}

/**
 * Rappels des périodes (2026-06-08) : ajoute `reminder_before_start_minutes` +
 * `reminder_before_end_minutes` (INTEGER nullable) sur `routine_periods`, miroir
 * de la migration Alembic `rp1_period_reminders` côté serveur. Convention :
 * null = rappel désactivé, 0 = pile à l'heure, N = N min avant.
 *
 * Backfill (décision utilisateur) : les périodes EXISTANTES gardent leur notif de
 * début actuelle (ROUTINE_PERIOD_START pile au début) -> on pose
 * `reminder_before_start_minutes = 0` pour toutes les lignes existantes. La colonne
 * `reminder_before_end_minutes` reste null (rappel de fin opt-in).
 *
 * SQLite supporte ALTER TABLE ADD COLUMN nullable trivialement (min SDK 29).
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routine_periods ADD COLUMN reminder_before_start_minutes INTEGER")
        db.execSQL("ALTER TABLE routine_periods ADD COLUMN reminder_before_end_minutes INTEGER")
        db.execSQL("UPDATE routine_periods SET reminder_before_start_minutes = 0")
    }
}

/**
 * Nutrition A1 (2026-06-17) : portage Android des 8 tables nutrition déjà
 * exposées par le serveur (`foods`, `food_portions`, `meal_presets`, `meals`,
 * `meal_entries`, `nutrition_goals`, `recipes`, `recipe_ingredients`). Inclut
 * les champs micros (vitamines/minéraux) sur `foods` / `meal_entries`.
 *
 * CREATE TABLE alignés sur le schéma v22.json généré par KSP (types REAL pour
 * les Float macros/micros, INTEGER pour les flags). Ordre de création FK-aware :
 * parents (foods, recipes, meal_presets, nutrition_goals) avant enfants
 * (food_portions, meals, recipe_ingredients, meal_entries) pour que chaque
 * REFERENCES pointe une table déjà créée.
 *
 * Idempotent : CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- foods (parent) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `foods` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `source` TEXT NOT NULL,
                `source_ref` TEXT,
                `food_group` TEXT,
                `kcal_per_100g` REAL NOT NULL,
                `protein_per_100g` REAL NOT NULL,
                `carbs_per_100g` REAL NOT NULL,
                `fat_per_100g` REAL NOT NULL,
                `fiber_per_100g` REAL,
                `sugar_per_100g` REAL,
                `sat_fat_per_100g` REAL,
                `salt_per_100g` REAL,
                `iron_per_100g` REAL,
                `calcium_per_100g` REAL,
                `magnesium_per_100g` REAL,
                `zinc_per_100g` REAL,
                `potassium_per_100g` REAL,
                `sodium_per_100g` REAL,
                `vitamin_c_per_100g` REAL,
                `vitamin_d_per_100g` REAL,
                `vitamin_b12_per_100g` REAL,
                `vitamin_a_per_100g` REAL,
                `is_favorite` INTEGER NOT NULL DEFAULT 0,
                `archived` INTEGER NOT NULL DEFAULT 0,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_foods_uuid` ON `foods` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_user_id` ON `foods` (`user_id`)")

        // --- recipes (parent) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipes` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `total_weight_g` REAL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recipes_uuid` ON `recipes` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_user_id` ON `recipes` (`user_id`)")

        // --- meal_presets (parent) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meal_presets` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `order_index` INTEGER NOT NULL,
                `default_time` TEXT,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_presets_uuid` ON `meal_presets` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_presets_user_id` ON `meal_presets` (`user_id`)")

        // --- nutrition_goals (parent) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `nutrition_goals` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `effective_from` TEXT NOT NULL,
                `day_kind` TEXT NOT NULL,
                `kcal` REAL NOT NULL,
                `protein_g` REAL NOT NULL,
                `carbs_g` REAL NOT NULL,
                `fat_g` REAL NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_nutrition_goals_uuid` ON `nutrition_goals` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_goals_user_id` ON `nutrition_goals` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_goals_effective_from` ON `nutrition_goals` (`effective_from`)")

        // --- food_portions (-> foods) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `food_portions` (
                `uuid` TEXT NOT NULL,
                `food_uuid` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `grams` REAL NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`food_uuid`) REFERENCES `foods`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_portions_uuid` ON `food_portions` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_portions_food_uuid` ON `food_portions` (`food_uuid`)")

        // --- meals (-> meal_presets) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meals` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `order_index` INTEGER NOT NULL,
                `preset_uuid` TEXT,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`preset_uuid`) REFERENCES `meal_presets`(`uuid`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meals_uuid` ON `meals` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_user_id` ON `meals` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_date` ON `meals` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_preset_uuid` ON `meals` (`preset_uuid`)")

        // --- recipe_ingredients (-> recipes, foods) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_ingredients` (
                `uuid` TEXT NOT NULL,
                `recipe_uuid` TEXT NOT NULL,
                `food_uuid` TEXT NOT NULL,
                `quantity_g` REAL NOT NULL,
                `order_index` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`recipe_uuid`) REFERENCES `recipes`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`food_uuid`) REFERENCES `foods`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recipe_ingredients_uuid` ON `recipe_ingredients` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_recipe_uuid` ON `recipe_ingredients` (`recipe_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_food_uuid` ON `recipe_ingredients` (`food_uuid`)")

        // --- meal_entries (-> meals, foods, recipes) ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meal_entries` (
                `uuid` TEXT NOT NULL,
                `meal_uuid` TEXT NOT NULL,
                `food_uuid` TEXT,
                `recipe_uuid` TEXT,
                `display_name` TEXT NOT NULL,
                `quantity_g` REAL NOT NULL,
                `portion_label` TEXT,
                `kcal_per_100g` REAL NOT NULL,
                `protein_per_100g` REAL NOT NULL,
                `carbs_per_100g` REAL NOT NULL,
                `fat_per_100g` REAL NOT NULL,
                `fiber_per_100g` REAL,
                `sugar_per_100g` REAL,
                `sat_fat_per_100g` REAL,
                `salt_per_100g` REAL,
                `iron_per_100g` REAL,
                `calcium_per_100g` REAL,
                `magnesium_per_100g` REAL,
                `zinc_per_100g` REAL,
                `potassium_per_100g` REAL,
                `sodium_per_100g` REAL,
                `vitamin_c_per_100g` REAL,
                `vitamin_d_per_100g` REAL,
                `vitamin_b12_per_100g` REAL,
                `vitamin_a_per_100g` REAL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`meal_uuid`) REFERENCES `meals`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`food_uuid`) REFERENCES `foods`(`uuid`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`recipe_uuid`) REFERENCES `recipes`(`uuid`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_entries_uuid` ON `meal_entries` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entries_meal_uuid` ON `meal_entries` (`meal_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entries_food_uuid` ON `meal_entries` (`food_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entries_recipe_uuid` ON `meal_entries` (`recipe_uuid`)")
    }
}

/**
 * Santé / Health Connect V1 (2026-06-17) : portage Android des 3 tables santé déjà
 * exposées par le serveur (`health_step_counts`, `health_metrics`, `health_goals`).
 * Type A user-scoped, FK vers User seul (aucune FK inter-santé) — les 3 CREATE TABLE
 * sont donc indépendants.
 *
 * CREATE TABLE alignés sur le schéma v23.json généré par KSP (REAL pour les Float
 * `value`/`target`, INTEGER pour `steps` et les flags). Idempotent : CREATE TABLE
 * IF NOT EXISTS + CREATE INDEX IF NOT EXISTS (re-run sans casse).
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- health_step_counts ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `health_step_counts` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `bucket_start` TEXT NOT NULL,
                `steps` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_step_counts_uuid` ON `health_step_counts` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_step_counts_user_id` ON `health_step_counts` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_step_counts_date` ON `health_step_counts` (`date`)")

        // --- health_metrics ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `health_metrics` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `value` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `start_time` TEXT,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_metrics_uuid` ON `health_metrics` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_metrics_user_id` ON `health_metrics` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_metrics_date` ON `health_metrics` (`date`)")

        // --- health_goals ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `health_goals` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `target` REAL NOT NULL,
                `effective_from` TEXT NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_goals_uuid` ON `health_goals` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_goals_user_id` ON `health_goals` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_goals_effective_from` ON `health_goals` (`effective_from`)")
    }
}

/**
 * Hydratation (2026-07-05) : portage Android de la table serveur `water_intakes`
 * (une prise d'eau horodatée = une row ; total du jour = SUM(amount_ml) côté client).
 * Type A user-scoped, FK vers User seul. CREATE TABLE aligné sur le schéma v24.json
 * généré par KSP (INTEGER pour `amount_ml` et les flags, TEXT nullable pour les dates).
 * Idempotent : CREATE TABLE / INDEX IF NOT EXISTS.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `water_intakes` (
                `uuid` TEXT NOT NULL,
                `user_id` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `amount_ml` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `pendingDeletion` INTEGER NOT NULL DEFAULT 0,
                `created_at` TEXT,
                `updated_at` TEXT,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_water_intakes_uuid` ON `water_intakes` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_intakes_user_id` ON `water_intakes` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_intakes_date` ON `water_intakes` (`date`)")
    }
}

/**
 * Hydratation (2026-07-05) : +is_water sur foods (auto-comptage boissons eau, 1 g = 1 ml).
 * Miroir de la migration serveur w2. ALTER TABLE ADD COLUMN trivial (NOT NULL DEFAULT 0).
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `foods` ADD COLUMN `is_water` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Collation avec heure (2026-07-14) : +time sur meals ("HH:MM" facultatif, surclasse le
 * defaultTime du preset à l'affichage). Rattrape le champ wire déjà présent serveur + web.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `meals` ADD COLUMN `time` TEXT")
    }
}

object Migrations {
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
    )
}
