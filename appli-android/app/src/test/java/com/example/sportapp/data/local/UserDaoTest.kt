package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.User
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Couverture du comportement observable de l'entite User pour la feature
 * "email reel optionnel" (commit 803b700, Room v19->v20).
 *
 * Le User Room est read-only client (UserDao : pas de sync montante) -- il est
 * alimente exclusivement depuis le serveur via `insertFromServer` (merge apres
 * /me). On verifie donc le chemin reel :
 *  - un user pulle AVEC email -> l'email persiste localement (relu par getUserById).
 *  - un user pulle SANS email (signup sans email) -> email = null persiste
 *    (jamais l'ancien synthetique {username}@sportapp.com).
 *
 * La migration v19->v20 elle-meme (ADD COLUMN email) n'est pas validable ici :
 * Room.inMemoryDatabaseBuilder construit le schema directement depuis les @Entity
 * (v20), sans rejouer les migrations. Elle doit etre testee sur device
 * (androidTest MigrationTest). Ce test valide le mapping entite<->colonne `email`.
 *
 * Pattern aligne sur QuoteDaoTest / MuscleDaoTest (Room in-memory + Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class UserDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insertFromServer preserves real email pulled from server`() = runTest {
        dao.insertFromServer(
            User(id = 1, username = "will", email = "will@example.com")
        )

        val stored = dao.getUserById(1)
        assertNotNull(stored)
        assertEquals("will@example.com", stored!!.email)
        assertEquals("will", stored.username)
    }

    @Test
    fun `insertFromServer accepts null email (signup without email)`() = runTest {
        dao.insertFromServer(
            User(id = 2, username = "bob", email = null)
        )

        val stored = dao.getUserById(2)
        assertNotNull(stored)
        assertNull(
            "Un user sans email doit rester null localement (jamais le synthetique)",
            stored!!.email,
        )
    }
}
