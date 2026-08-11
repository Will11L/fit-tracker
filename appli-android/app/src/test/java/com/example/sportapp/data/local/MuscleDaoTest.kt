package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.Muscle
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
 * T4.2 Phase 0.1 (2026-05-07) : tests smoke pilote Muscle.
 *
 * Garde-fou avant refactor SyncEngine : capture le comportement DAO actuel
 * (forced `synced=false` sur insert wrapper, préservation flags sur
 * insertFromServer, sémantique markAsSynced/markAsPendingDeletion) pour
 * détecter une régression introduite par les phases suivantes (uniformisation
 * surface DAO Phase 0.2, extraction SyncableEntity Phase 1.1, etc.).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MuscleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MuscleDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.muscleDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert wrapper poses synced false and updatedAt automatiquement`() = runTest {
        val item = Muscle(
            uuid = "m-1",
            userId = 1,
            name = "Biceps",
            synced = true,           // sera écrasé à false par le wrapper
            pendingDeletion = false,
            updatedAt = null,        // sera écrasé par getNowISO8601() par le wrapper
        )
        dao.insert(item)

        val stored = dao.getMuscleByUUID("m-1")
        assertNotNull(stored)
        assertFalse("insert wrapper doit forcer synced=false", stored!!.synced)
        assertNotNull("insert wrapper doit poser updatedAt", stored.updatedAt)
        assertEquals("Biceps", stored.name)
    }

    @Test
    fun `insertFromServer preserves explicit synced flag and updatedAt`() = runTest {
        // Simule le pull serveur : le payload arrive avec synced=true et
        // updatedAt explicite. SyncEngine.pullMerge utilisera insertFromServer
        // → ne doit JAMAIS toucher aux flags (différent du wrapper insert).
        val explicitDate = "2025-01-15T10:30:00Z"
        val item = Muscle(
            uuid = "m-srv",
            userId = 1,
            name = "FromServer",
            synced = true,
            pendingDeletion = false,
            updatedAt = explicitDate,
        )
        dao.insertFromServer(item)

        val stored = dao.getMuscleByUUID("m-srv")
        assertNotNull(stored)
        assertTrue("insertFromServer doit préserver synced=true", stored!!.synced)
        assertEquals("insertFromServer doit préserver updatedAt", explicitDate, stored.updatedAt)
    }

    @Test
    fun `insertAll wrapper forces synced false even if input has synced true`() = runTest {
        // Comportement attendu confirmé : `insertAll` est l'API "client a modifié",
        // pas l'API serveur. Le path serveur passe par `insertAllFromServer`.
        val items = listOf(
            Muscle(uuid = "m1", userId = 1, name = "A", synced = true),
            Muscle(uuid = "m2", userId = 1, name = "B", synced = true),
        )
        dao.insertAll(items)

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        all.forEach { assertFalse("insertAll wrapper doit forcer synced=false", it.synced) }
    }

    @Test
    fun `markAsSynced filters getAllUnsynced`() = runTest {
        dao.insert(Muscle(uuid = "u1", userId = 1, name = "A"))
        dao.insert(Muscle(uuid = "u2", userId = 1, name = "B"))
        assertEquals(2, dao.getAllUnsynced().size)

        dao.markAsSynced("u1")

        val unsynced = dao.getAllUnsynced()
        assertEquals(1, unsynced.size)
        assertEquals("u2", unsynced[0].uuid)
    }

    @Test
    fun `markAsPendingDeletion sets flag and advances updatedAt`() = runTest {
        dao.insert(Muscle(uuid = "to-del", userId = 1, name = "Old"))
        val before = dao.getMuscleByUUID("to-del")!!
        Thread.sleep(2)

        dao.markAsPendingDeletion("to-del")

        val after = dao.getMuscleByUUID("to-del")!!
        assertTrue(after.pendingDeletion)
        assertNotNull(after.updatedAt)
        assertTrue(after.updatedAt!! > before.updatedAt!!)
    }

    @Test
    fun `getPendingDeletions returns only flagged items`() = runTest {
        dao.insert(Muscle(uuid = "keep", userId = 1, name = "Keep"))
        dao.insert(Muscle(uuid = "del", userId = 1, name = "Del"))
        dao.markAsPendingDeletion("del")

        val pendings = dao.getPendingDeletions()
        assertEquals(1, pendings.size)
        assertEquals("del", pendings[0].uuid)
    }

    @Test
    fun `clearAll removes all rows`() = runTest {
        dao.insert(Muscle(uuid = "u1", userId = 1, name = "A"))
        dao.insert(Muscle(uuid = "u2", userId = 1, name = "B"))
        assertEquals(2, dao.getAllOnce().size)

        dao.clearAll()

        assertEquals(0, dao.getAllOnce().size)
        assertNull(dao.getMuscleByUUID("u1"))
    }

    @Test
    fun `getAllOnce includes pendingDeletion items`() = runTest {
        // Garde-fou : SyncEngine.pullMerge va utiliser getAllOnce() pour
        // construire le map local. Si on excluait les pendingDeletion, on
        // perdrait la trace des deletions en attente de push → bug data loss.
        dao.insert(Muscle(uuid = "active", userId = 1, name = "Active"))
        dao.insert(Muscle(uuid = "pending-del", userId = 1, name = "Pending"))
        dao.markAsPendingDeletion("pending-del")

        val all = dao.getAllOnce()
        assertEquals("getAllOnce doit inclure les pendingDeletion", 2, all.size)
        assertTrue(all.any { it.uuid == "pending-del" && it.pendingDeletion })
    }
}
