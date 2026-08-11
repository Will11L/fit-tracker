package com.example.sportapp.core.data.local

import android.database.Cursor
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sportapp.core.data.local.migrations.Migrations
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T1.1.f (2026-05-06) : smoke test cascade Room migrations v7 → v13.
 *
 * Valide que la chaîne de 6 migrations (MIGRATION_7_8 → MIGRATION_12_13)
 * s'applique correctement sur une DB v7 vide, et que le schéma final
 * matche le schéma v13.json généré par KSP.
 *
 * Note : v6.json n'a jamais été commité (exportSchema activé en V3.1
 * commit `2439fd1`, à partir de v7) — on commence donc à v7. La migration
 * MIGRATION_6_7 ne peut pas être validée par MigrationTestHelper sans
 * v6.json, mais elle est testée en pratique au runtime sur les vieux
 * devices qui upgrade depuis v6.
 *
 * Exécution : `./gradlew :app:connectedDebugAndroidTest` (besoin device
 * Android branché ou émulateur démarré).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val DB_NAME = "test-migrations.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate_v7_to_v13_succeeds() {
        // 1. Crée DB vide v7 (les rows seront ajoutés par les migrations si besoin de
        //    backfill, ici on teste juste que les migrations s'enchaînent sans erreur).
        helper.createDatabase(DB_NAME, 7).close()

        // 2. Applique toutes les migrations jusqu'à v13. MigrationTestHelper filtre
        //    automatiquement celles dont fromVersion < startVersion.
        val finalDb = helper.runMigrationsAndValidate(
            DB_NAME,
            13,
            true,
            *Migrations.ALL
        )

        // 3. Sanity check : table users + available_equipments existent en v13.
        val usersCursor: Cursor = finalDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='users'")
        usersCursor.use {
            assertTrue("users table should exist after migration to v13", it.moveToFirst())
        }
        val aeCursor: Cursor = finalDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='available_equipments'")
        aeCursor.use {
            assertTrue("available_equipments table should exist", it.moveToFirst())
        }

        finalDb.close()
    }

    @Test
    fun openDatabase_with_all_migrations_succeeds() {
        // Cas réel : ouvre la DB via Room.databaseBuilder en partant de v7,
        // ce qui force la chaîne de migrations à s'appliquer.
        helper.createDatabase(DB_NAME, 7).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            DB_NAME
        ).addMigrations(*Migrations.ALL).build()

        // Open déclenche les migrations
        db.openHelper.writableDatabase
        db.close()
    }
}
