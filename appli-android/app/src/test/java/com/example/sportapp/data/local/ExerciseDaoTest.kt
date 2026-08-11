package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.Exercise
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
 * T4.2 Phase 0.1 (2026-05-07) : tests smoke pilote Exercise.
 * Cf. [MuscleDaoTest] pour la justification du pattern.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ExerciseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.exerciseDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert wrapper poses synced false and updatedAt automatiquement`() = runTest {
        val item = Exercise(
            uuid = "ex-1",
            userId = 1,
            name = "Squat",
            synced = true,
            pendingDeletion = false,
            updatedAt = null,
        )
        dao.insert(item)

        val stored = dao.getExerciseByUUID("ex-1")
        assertNotNull(stored)
        assertFalse(stored!!.synced)
        assertNotNull(stored.updatedAt)
        assertEquals("Squat", stored.name)
    }

    @Test
    fun `insertFromServer preserves explicit synced flag and updatedAt`() = runTest {
        val explicitDate = "2025-01-15T10:30:00Z"
        val item = Exercise(
            uuid = "ex-srv",
            userId = 1,
            name = "FromServer",
            synced = true,
            updatedAt = explicitDate,
        )
        dao.insertFromServer(item)

        val stored = dao.getExerciseByUUID("ex-srv")
        assertNotNull(stored)
        assertTrue(stored!!.synced)
        assertEquals(explicitDate, stored.updatedAt)
    }

    @Test
    fun `insertAll wrapper forces synced false even if input has synced true`() = runTest {
        val items = listOf(
            Exercise(uuid = "e1", userId = 1, name = "A", synced = true),
            Exercise(uuid = "e2", userId = 1, name = "B", synced = true),
        )
        dao.insertAll(items)

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        all.forEach { assertFalse(it.synced) }
    }

    @Test
    fun `markAsSynced filters getAllUnsynced`() = runTest {
        dao.insert(Exercise(uuid = "u1", userId = 1, name = "A"))
        dao.insert(Exercise(uuid = "u2", userId = 1, name = "B"))
        assertEquals(2, dao.getAllUnsynced().size)

        dao.markAsSynced("u1")

        val unsynced = dao.getAllUnsynced()
        assertEquals(1, unsynced.size)
        assertEquals("u2", unsynced[0].uuid)
    }

    @Test
    fun `markAsPendingDeletion sets flag and advances updatedAt`() = runTest {
        dao.insert(Exercise(uuid = "to-del", userId = 1, name = "Old"))
        val before = dao.getExerciseByUUID("to-del")!!
        Thread.sleep(2)

        dao.markAsPendingDeletion("to-del")

        val after = dao.getExerciseByUUID("to-del")!!
        assertTrue(after.pendingDeletion)
        assertNotNull(after.updatedAt)
        assertTrue(after.updatedAt!! > before.updatedAt!!)
    }

    @Test
    fun `getPendingDeletions returns only flagged items`() = runTest {
        dao.insert(Exercise(uuid = "keep", userId = 1, name = "Keep"))
        dao.insert(Exercise(uuid = "del", userId = 1, name = "Del"))
        dao.markAsPendingDeletion("del")

        val pendings = dao.getPendingDeletions()
        assertEquals(1, pendings.size)
        assertEquals("del", pendings[0].uuid)
    }

    @Test
    fun `clearAll removes all rows`() = runTest {
        dao.insert(Exercise(uuid = "u1", userId = 1, name = "A"))
        dao.insert(Exercise(uuid = "u2", userId = 1, name = "B"))
        assertEquals(2, dao.getAllOnce().size)

        dao.clearAll()

        assertEquals(0, dao.getAllOnce().size)
        assertNull(dao.getExerciseByUUID("u1"))
    }

    @Test
    fun `getAllOnce includes pendingDeletion items`() = runTest {
        dao.insert(Exercise(uuid = "active", userId = 1, name = "Active"))
        dao.insert(Exercise(uuid = "pending-del", userId = 1, name = "Pending"))
        dao.markAsPendingDeletion("pending-del")

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        assertTrue(all.any { it.uuid == "pending-del" && it.pendingDeletion })
    }
}
