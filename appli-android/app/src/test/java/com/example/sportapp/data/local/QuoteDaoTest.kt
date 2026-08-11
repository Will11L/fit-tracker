package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.Quote
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
 * Couverture du comportement observable de l'entite Quote (citations motivantes,
 * commit 384bc33). On teste le DAO car c'est la surface load-bearing de la
 * feature :
 *
 *  - `getActive()` est la source du tirage aleatoire du SplashScreen
 *    ("affichage aleatoire au login"). Il DOIT masquer les citations
 *    pendingDeletion, sinon le splash peut afficher une citation que l'user
 *    vient de supprimer (pas encore poussee au serveur).
 *  - `insertFromServer` doit forcer synced=true / pendingDeletion=false : c'est
 *    le chemin emprunte par les citations pre-seedees qui arrivent au pull du
 *    1er login (cf. SplashScreenViewModel.loadRandomQuote re-tire apres sync).
 *    Regression class 2026-05-07 (payload JSON sans champ synced -> default
 *    Kotlin false -> re-push inutile).
 *  - `markAsPendingDeletion` doit retirer la citation de getActive() ET la
 *    marquer unsynced pour que la suppression soit poussee.
 *
 * Pattern aligne sur MuscleDaoTest (Room in-memory + Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class QuoteDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: QuoteDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.quoteDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `getActive returns visible quotes and preserves author`() = runTest {
        dao.insert(Quote(uuid = "q-1", userId = 1, text = "Just do it.", author = "Nike"))
        dao.insert(Quote(uuid = "q-2", userId = 1, text = "No pain no gain", author = null))

        val active = dao.getActive()

        assertEquals(2, active.size)
        val q1 = active.first { it.uuid == "q-1" }
        assertEquals("Just do it.", q1.text)
        assertEquals("Nike", q1.author)
        assertEquals(null, active.first { it.uuid == "q-2" }.author)
    }

    @Test
    fun `getActive excludes pendingDeletion quotes (splash draw source)`() = runTest {
        dao.insert(Quote(uuid = "keep", userId = 1, text = "Stay hungry"))
        dao.insert(Quote(uuid = "deleted", userId = 1, text = "Forget me"))
        dao.markAsPendingDeletion("deleted")

        val active = dao.getActive()

        assertEquals("getActive doit masquer les pendingDeletion", 1, active.size)
        assertEquals("keep", active[0].uuid)
        assertTrue(active.none { it.uuid == "deleted" })
    }

    @Test
    fun `getActive returns empty when no quote (splash fallback to default text)`() = runTest {
        // randomOrNull() cote VM -> null -> motivationalQuote reste null -> fallback.
        val active = dao.getActive()
        assertTrue(active.isEmpty())
    }

    @Test
    fun `insertFromServer forces synced=true and pendingDeletion=false (regression 2026-05-07)`() = runTest {
        // Payload deserialise depuis JSON serveur sans champ synced -> default
        // Kotlin false. pendingDeletion=true simule un payload bruite. Le pull du
        // 1er login (citations pre-seedees) passe par ce chemin.
        dao.insertFromServer(
            Quote(
                uuid = "q-srv", userId = 1, text = "From server",
                synced = false, pendingDeletion = true,
                updatedAt = "2025-01-15T10:00:00Z",
            )
        )

        val stored = dao.getByUUID("q-srv")
        assertNotNull(stored)
        assertTrue("insertFromServer doit forcer synced=true", stored!!.synced)
        assertFalse("insertFromServer doit forcer pendingDeletion=false", stored.pendingDeletion)
        // La citation serveur doit etre eligible au tirage splash.
        assertTrue(dao.getActive().any { it.uuid == "q-srv" })
    }

    @Test
    fun `markAsPendingDeletion flips flag and marks unsynced`() = runTest {
        dao.insert(Quote(uuid = "to-del", userId = 1, text = "Bye"))
        // insert() pose synced=false ; on simule un etat synced cote serveur pour
        // verifier que la suppression repasse bien la row en unsynced (re-push).
        dao.markAsSynced("to-del")
        assertTrue(dao.getByUUID("to-del")!!.synced)
        val before = dao.getByUUID("to-del")!!
        Thread.sleep(2)

        dao.markAsPendingDeletion("to-del")

        val after = dao.getByUUID("to-del")!!
        assertTrue("flag pendingDeletion pose", after.pendingDeletion)
        assertFalse("la suppression doit repasser la row unsynced pour etre poussee", after.synced)
        assertNotNull(after.updatedAt)
        assertTrue("updatedAt doit avancer", after.updatedAt!! > before.updatedAt!!)
        // getPendingDeletions la voit, getActive ne la voit plus.
        assertTrue(dao.getPendingDeletions().any { it.uuid == "to-del" })
        assertTrue(dao.getActive().none { it.uuid == "to-del" })
    }
}
