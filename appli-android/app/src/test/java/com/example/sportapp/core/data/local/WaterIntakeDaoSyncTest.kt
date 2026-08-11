package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.WaterIntake
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
 * Hydratation (2026-07-05) — smoke DAO + flags de sync de la table `water_intakes`.
 *
 * Construire l'AppDatabase v24 (et obtenir le DAO) valide déjà que la nouvelle
 * `@Entity` produit un schéma Room cohérent (sinon Room crashe à l'ouverture — et
 * la migration MIGRATION_23_24 est cross-checkée par ce même schéma). Au-delà, on
 * verrouille les comportements observables du squelette DAO Style A :
 *  - wrapper `insert` force synced=false + rafraîchit updatedAt en préservant createdAt ;
 *  - `insertFromServer` force synced=true + pendingDeletion=false — classe de
 *    régression du 2026-05-07 (payload serveur désérialisé sans champ synced).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WaterIntakeDaoSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WaterIntakeDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.waterIntakeDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun sample(
        uuid: String = "w-1",
        amountMl: Int = 250,
        synced: Boolean = false,
        pendingDeletion: Boolean = false,
        createdAt: String? = "2026-07-05T08:15:00.000000Z",
    ) = WaterIntake(
        uuid = uuid,
        userId = 1,
        date = "2026-07-05",
        amountMl = amountMl,
        synced = synced,
        pendingDeletion = pendingDeletion,
        createdAt = createdAt,
    )

    @Test
    fun `water intake round-trips through the v24 schema`() = runTest {
        dao.insertFromServer(sample())

        val stored = dao.getByUUID("w-1")
        assertNotNull("le schéma v24 doit accepter un WaterIntake", stored)
        assertEquals(250, stored!!.amountMl)
        assertEquals("2026-07-05", stored.date)
        assertEquals("2026-07-05T08:15:00.000000Z", stored.createdAt)
    }

    @Test
    fun `insert wrapper forces synced false, refreshes updatedAt, preserves createdAt`() = runTest {
        // Payload "édité client" avec synced=true volontairement faux : le wrapper
        // doit l'écraser à false (sinon le push suivant ne le repousserait jamais).
        dao.insert(sample(synced = true).copy(updatedAt = null))

        val stored = dao.getByUUID("w-1")!!
        assertFalse("insert wrapper doit forcer synced=false", stored.synced)
        assertNotNull("insert wrapper doit poser updatedAt", stored.updatedAt)
        assertEquals("insert wrapper ne doit pas toucher createdAt", "2026-07-05T08:15:00.000000Z", stored.createdAt)
    }

    @Test
    fun `insertFromServer forces synced true and pendingDeletion false (regression class)`() = runTest {
        // Payload désérialisé sans champ synced (default Kotlin false) + pendingDeletion bruité.
        dao.insertFromServer(sample(synced = false, pendingDeletion = true))

        val stored = dao.getByUUID("w-1")!!
        assertTrue("payload serveur doit être stocké synced=true", stored.synced)
        assertFalse("payload serveur doit être stocké pendingDeletion=false", stored.pendingDeletion)
    }

    @Test
    fun `unsynced local rows are surfaced for push`() = runTest {
        dao.insert(sample())                       // synced=false via wrapper
        dao.insertFromServer(sample(uuid = "w-2")) // synced=true

        assertTrue(dao.hasUnsynced())
        val unsynced = dao.getAllUnsynced()
        assertEquals(1, unsynced.size)
        assertEquals("w-1", unsynced.first().uuid)
    }

    @Test
    fun `markAsPendingDeletion flags row and unsyncs it`() = runTest {
        dao.insertFromServer(sample())             // synced=true, pendingDeletion=false
        dao.markAsPendingDeletion("w-1")

        val pending = dao.getPendingDeletions()
        assertEquals(1, pending.size)
        assertEquals("w-1", pending.first().uuid)
        assertFalse("pendingDeletion doit repasser synced=false pour être poussé", pending.first().synced)
    }
}
