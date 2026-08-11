package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.AvailableEquipment
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T1.1.e (2026-05-06) : tests Room DAOs in-memory via Robolectric.
 *
 * Cible : `AvailableEquipmentDao` (Style A canonique post F8-Q2). Valide :
 * - Le wrapper `insert(item)` pose automatiquement `synced=false + updatedAt`.
 * - `markAsSynced(uuid)` filtre correctement `getAllUnsynced`.
 * - `markAsPendingDeletion` pose `pendingDeletion=true + updatedAt` à jour.
 *
 * Pattern : `Room.inMemoryDatabaseBuilder` + `allowMainThreadQueries` (test
 * synchrone via runTest, pas de thread pool).
 *
 * Exécution : `./gradlew :app:testDebugUnitTest --tests "*.AvailableEquipmentDaoTest"`
 */
@RunWith(RobolectricTestRunner::class)
// application = vanilla Application : bypass @HiltAndroidApp qui démarre
// EncryptedSharedPreferences (V8.2-3) → Android Keystore indispo en JVM Robolectric.
// Room in-memory n'a pas besoin de Hilt, juste d'un Context.
@Config(sdk = [33], application = android.app.Application::class)
class AvailableEquipmentDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AvailableEquipmentDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.availableEquipmentDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert wrapper poses synced false and updatedAt automatiquement`() = runTest {
        val item = AvailableEquipment(
            uuid = "test-uuid-1",
            userId = 1,
            name = "Dumbbells",
            synced = true,           // Volontairement true → le wrapper doit forcer false
            pendingDeletion = false,
            updatedAt = null,        // Volontairement null → le wrapper doit poser getNowISO8601()
        )
        dao.insert(item)

        val stored = dao.getAvailableEquipmentByUUID("test-uuid-1")
        assertNotNull("Item should be inserted", stored)
        assertFalse("synced should be forced to false by insert wrapper", stored!!.synced)
        assertNotNull("updatedAt should be auto-set by insert wrapper", stored.updatedAt)
        assertEquals("Dumbbells", stored.name)
        assertEquals(1, stored.userId)
    }

    @Test
    fun `markAsSynced filters getAllUnsynced`() = runTest {
        dao.insert(AvailableEquipment(uuid = "u1", userId = 1, name = "Barbell"))
        dao.insert(AvailableEquipment(uuid = "u2", userId = 1, name = "Kettlebell"))

        val unsyncedBefore = dao.getAllUnsynced()
        assertEquals("Both items should be unsynced after insert", 2, unsyncedBefore.size)

        dao.markAsSynced("u1")
        val unsyncedAfter = dao.getAllUnsynced()
        assertEquals("Only u2 should remain unsynced", 1, unsyncedAfter.size)
        assertEquals("u2", unsyncedAfter[0].uuid)
    }

    @Test
    fun `markAsPendingDeletion sets pendingDeletion and updates updatedAt`() = runTest {
        dao.insert(AvailableEquipment(uuid = "to-delete", userId = 1, name = "OldEquipment"))
        val before = dao.getAvailableEquipmentByUUID("to-delete")!!

        // Petit sleep pour garantir un changement detectable de updatedAt (microsec resolution)
        Thread.sleep(2)

        dao.markAsPendingDeletion("to-delete")

        val after = dao.getAvailableEquipmentByUUID("to-delete")
        assertNotNull(after)
        assertTrue("pendingDeletion should be true", after!!.pendingDeletion)
        assertNotNull(after.updatedAt)
        assertTrue(
            "updatedAt should advance after markAsPendingDeletion",
            after.updatedAt!! > before.updatedAt!!
        )
    }

    @Test
    fun `getPendingDeletions returns only flagged items`() = runTest {
        dao.insert(AvailableEquipment(uuid = "keep", userId = 1, name = "KeepMe"))
        dao.insert(AvailableEquipment(uuid = "delete", userId = 1, name = "DeleteMe"))
        dao.markAsPendingDeletion("delete")

        val pendings = dao.getPendingDeletions()
        assertEquals(1, pendings.size)
        assertEquals("delete", pendings[0].uuid)
    }

    @Test
    fun `clearAll removes all rows`() = runTest {
        dao.insert(AvailableEquipment(uuid = "u1", userId = 1, name = "A"))
        dao.insert(AvailableEquipment(uuid = "u2", userId = 1, name = "B"))
        assertEquals(2, dao.getAllOnce().size)

        dao.clearAll()
        assertEquals(0, dao.getAllOnce().size)
        assertNull(dao.getAvailableEquipmentByUUID("u1"))
    }
}
