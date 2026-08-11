package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.ActualWorkout
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
 * T4.2 Phase 0.1 (2026-05-07) : tests smoke pilote ActualWorkout (entité user-scoped Type A).
 * Cf. [MuscleDaoTest] pour la justification du pattern.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ActualWorkoutDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ActualWorkoutDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.actualWorkoutDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert wrapper poses synced false and updatedAt automatiquement`() = runTest {
        val item = ActualWorkout(
            uuid = "aw-1",
            userId = 1,
            name = "Push Day",
            date = "2025-01-15",
            synced = true,
            pendingDeletion = false,
            updatedAt = null,
        )
        dao.insert(item)

        val stored = dao.getActualWorkoutByUUID("aw-1")
        assertNotNull(stored)
        assertFalse(stored!!.synced)
        assertNotNull(stored.updatedAt)
        assertEquals("Push Day", stored.name)
    }

    @Test
    fun `insertFromServer preserves explicit synced flag and updatedAt`() = runTest {
        val explicitDate = "2025-01-15T10:30:00Z"
        val item = ActualWorkout(
            uuid = "aw-srv",
            userId = 1,
            name = "FromServer",
            date = "2025-01-15",
            synced = true,
            updatedAt = explicitDate,
        )
        dao.insertFromServer(item)

        val stored = dao.getActualWorkoutByUUID("aw-srv")
        assertNotNull(stored)
        assertTrue(stored!!.synced)
        assertEquals(explicitDate, stored.updatedAt)
    }

    @Test
    fun `insertAll wrapper forces synced false even if input has synced true`() = runTest {
        val items = listOf(
            ActualWorkout(uuid = "a1", userId = 1, name = "A", date = "2025-01-15", synced = true),
            ActualWorkout(uuid = "a2", userId = 1, name = "B", date = "2025-01-16", synced = true),
        )
        dao.insertAll(items)

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        all.forEach { assertFalse(it.synced) }
    }

    @Test
    fun `markAsSynced filters getAllUnsynced`() = runTest {
        dao.insert(ActualWorkout(uuid = "u1", userId = 1, name = "A", date = "2025-01-15"))
        dao.insert(ActualWorkout(uuid = "u2", userId = 1, name = "B", date = "2025-01-16"))
        assertEquals(2, dao.getAllUnsynced().size)

        dao.markAsSynced("u1")

        val unsynced = dao.getAllUnsynced()
        assertEquals(1, unsynced.size)
        assertEquals("u2", unsynced[0].uuid)
    }

    @Test
    fun `markAsPendingDeletion sets flag and advances updatedAt`() = runTest {
        dao.insert(ActualWorkout(uuid = "to-del", userId = 1, name = "Old", date = "2025-01-15"))
        val before = dao.getActualWorkoutByUUID("to-del")!!
        Thread.sleep(2)

        dao.markAsPendingDeletion("to-del")

        val after = dao.getActualWorkoutByUUID("to-del")!!
        assertTrue(after.pendingDeletion)
        assertNotNull(after.updatedAt)
        assertTrue(after.updatedAt!! > before.updatedAt!!)
    }

    @Test
    fun `getPendingDeletions returns only flagged items`() = runTest {
        dao.insert(ActualWorkout(uuid = "keep", userId = 1, name = "Keep", date = "2025-01-15"))
        dao.insert(ActualWorkout(uuid = "del", userId = 1, name = "Del", date = "2025-01-16"))
        dao.markAsPendingDeletion("del")

        val pendings = dao.getPendingDeletions()
        assertEquals(1, pendings.size)
        assertEquals("del", pendings[0].uuid)
    }

    @Test
    fun `clearAll removes all rows`() = runTest {
        dao.insert(ActualWorkout(uuid = "u1", userId = 1, name = "A", date = "2025-01-15"))
        dao.insert(ActualWorkout(uuid = "u2", userId = 1, name = "B", date = "2025-01-16"))
        assertEquals(2, dao.getAllOnce().size)

        dao.clearAll()

        assertEquals(0, dao.getAllOnce().size)
        assertNull(dao.getActualWorkoutByUUID("u1"))
    }

    @Test
    fun `getAllOnce includes pendingDeletion items`() = runTest {
        dao.insert(ActualWorkout(uuid = "active", userId = 1, name = "Active", date = "2025-01-15"))
        dao.insert(ActualWorkout(uuid = "pending-del", userId = 1, name = "Pending", date = "2025-01-16"))
        dao.markAsPendingDeletion("pending-del")

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        assertTrue(all.any { it.uuid == "pending-del" && it.pendingDeletion })
    }
}
