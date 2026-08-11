package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.data.model.HealthStepCount
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Santé / Health Connect V1 (2026-06-17) — smoke DAO + flags de sync des 3 tables santé.
 *
 * Construire l'AppDatabase v23 (et obtenir les DAO) valide déjà que les 3 nouvelles
 * `@Entity` produisent un schéma Room cohérent (sinon Room crashe à l'ouverture — et
 * la migration MIGRATION_22_23 est cross-checkée par ce même schéma). Au-delà, on
 * verrouille les comportements observables du squelette DAO Style A :
 *  - wrapper `insert` force synced=false + pose updatedAt (sinon le push suivant ne
 *    repousserait jamais la row) ;
 *  - `insertFromServer` force synced=true + pendingDeletion=false — classe de
 *    régression du 2026-05-07 (payload serveur désérialisé sans champ synced).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class HealthDaoSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var stepDao: HealthStepCountDao
    private lateinit var metricDao: HealthMetricDao
    private lateinit var goalDao: HealthGoalDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        stepDao = db.healthStepCountDao()
        metricDao = db.healthMetricDao()
        goalDao = db.healthGoalDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun sampleStep(uuid: String = "s-1", synced: Boolean = false, pendingDeletion: Boolean = false) =
        HealthStepCount(
            uuid = uuid,
            userId = 1,
            date = "2026-06-17",
            bucketStart = "08:00",
            steps = 1234,
            synced = synced,
            pendingDeletion = pendingDeletion,
        )

    private fun sampleMetric(uuid: String = "m-1", synced: Boolean = false) =
        HealthMetric(
            uuid = uuid,
            userId = 1,
            type = "HEART_RATE",
            value = 72.5f,
            unit = "bpm",
            date = "2026-06-17",
            startTime = null,
            synced = synced,
        )

    private fun sampleGoal(uuid: String = "g-1", synced: Boolean = false) =
        HealthGoal(
            uuid = uuid,
            userId = 1,
            type = "STEPS",
            target = 10000f,
            effectiveFrom = "2026-06-01",
            synced = synced,
        )

    @Test
    fun `step count round-trips through the v23 schema`() = runTest {
        stepDao.insertFromServer(sampleStep())

        val stored = stepDao.getByUUID("s-1")
        assertNotNull("le schéma v23 doit accepter un HealthStepCount", stored)
        assertEquals(1234, stored!!.steps)
        assertEquals("08:00", stored.bucketStart)
        assertEquals("2026-06-17", stored.date)
    }

    @Test
    fun `metric and goal round-trip through the v23 schema`() = runTest {
        metricDao.insertFromServer(sampleMetric())
        goalDao.insertFromServer(sampleGoal())

        val metric = metricDao.getByUUID("m-1")!!
        assertEquals("HEART_RATE", metric.type)
        assertEquals(72.5f, metric.value)
        assertEquals("bpm", metric.unit)

        val goal = goalDao.getByUUID("g-1")!!
        assertEquals("STEPS", goal.type)
        assertEquals(10000f, goal.target)
        assertEquals("2026-06-01", goal.effectiveFrom)
    }

    @Test
    fun `insert wrapper forces synced false and poses updatedAt`() = runTest {
        // Payload "édité client" avec synced=true volontairement faux : le wrapper
        // doit l'écraser à false (sinon le push suivant ne le repousserait jamais).
        stepDao.insert(sampleStep(synced = true).copy(updatedAt = null))

        val stored = stepDao.getByUUID("s-1")!!
        assertFalse("insert wrapper doit forcer synced=false", stored.synced)
        assertNotNull("insert wrapper doit poser updatedAt", stored.updatedAt)
    }

    @Test
    fun `insertFromServer forces synced true and pendingDeletion false (regression class)`() = runTest {
        // Payload désérialisé sans champ synced (default Kotlin false) + pendingDeletion bruité.
        stepDao.insertFromServer(sampleStep(synced = false, pendingDeletion = true))
        metricDao.insertFromServer(sampleMetric(synced = false))
        goalDao.insertFromServer(sampleGoal(synced = false))

        val step = stepDao.getByUUID("s-1")!!
        assertTrue("payload serveur doit être stocké synced=true", step.synced)
        assertFalse("payload serveur doit être stocké pendingDeletion=false", step.pendingDeletion)
        assertTrue(metricDao.getByUUID("m-1")!!.synced)
        assertTrue(goalDao.getByUUID("g-1")!!.synced)
    }

    @Test
    fun `unsynced local rows are surfaced for push`() = runTest {
        stepDao.insert(sampleStep())        // synced=false via wrapper
        stepDao.insertFromServer(sampleStep(uuid = "s-2"))  // synced=true

        assertTrue(stepDao.hasUnsynced())
        val unsynced = stepDao.getAllUnsynced()
        assertEquals(1, unsynced.size)
        assertEquals("s-1", unsynced.first().uuid)
    }
}
