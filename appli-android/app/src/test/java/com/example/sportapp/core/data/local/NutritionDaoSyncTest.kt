package com.example.sportapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
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
 * Nutrition A1 (2026-06-17) — smoke DAO + sync des nouvelles tables nutrition.
 *
 * Le simple fait de construire l'AppDatabase v22 (et d'obtenir les DAO) valide
 * que les 8 nouvelles `@Entity` produisent un schéma Room cohérent (sinon Room
 * crashe à l'ouverture). Au-delà, on verrouille les comportements observables :
 *  - flags de sync sur Food (wrapper `insert` force synced=false ; `insertFromServer`
 *    force synced=true + pendingDeletion=false — classe de régression du 2026-05-07) ;
 *  - sémantique FK miroir du serveur : CASCADE (food_portions) vs SET NULL
 *    (meal_entries.food_uuid, snapshot D5 qui doit survivre à la suppression de
 *    l'aliment source).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NutritionDaoSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var foodDao: FoodDao
    private lateinit var foodPortionDao: FoodPortionDao
    private lateinit var mealDao: MealDao
    private lateinit var mealEntryDao: MealEntryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        foodDao = db.foodDao()
        foodPortionDao = db.foodPortionDao()
        mealDao = db.mealDao()
        mealEntryDao = db.mealEntryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun sampleFood(uuid: String = "f-1", synced: Boolean = false, pendingDeletion: Boolean = false) =
        Food(
            uuid = uuid,
            userId = 1,
            name = "Egg",
            source = "CIQUAL",
            foodGroup = "PROTEINS",
            kcalPer100g = 143f,
            proteinPer100g = 12.6f,
            carbsPer100g = 0.7f,
            fatPer100g = 9.9f,
            vitaminB12Per100g = 1.1f,
            ironPer100g = 1.8f,
            synced = synced,
            pendingDeletion = pendingDeletion,
        )

    @Test
    fun `Food round-trips with micros through the v22 schema`() = runTest {
        foodDao.insertFromServer(sampleFood())

        val stored = foodDao.getByUUID("f-1")
        assertNotNull("le schéma v22 doit accepter un Food avec micros", stored)
        assertEquals(143f, stored!!.kcalPer100g)
        assertEquals(1.1f, stored.vitaminB12Per100g)
        assertEquals(1.8f, stored.ironPer100g)
        assertEquals("PROTEINS", stored.foodGroup)
    }

    @Test
    fun `Food insert wrapper forces synced false and poses updatedAt`() = runTest {
        // Payload "édité client" avec synced=true volontairement faux : le wrapper
        // doit l'écraser à false (sinon le push suivant ne le repousserait jamais).
        foodDao.insert(sampleFood(synced = true).copy(updatedAt = null))

        val stored = foodDao.getByUUID("f-1")!!
        assertFalse("insert wrapper doit forcer synced=false", stored.synced)
        assertNotNull("insert wrapper doit poser updatedAt", stored.updatedAt)
    }

    @Test
    fun `Food insertFromServer forces synced true and pendingDeletion false (regression class)`() = runTest {
        // Payload désérialisé sans champ synced (default Kotlin false) + pendingDeletion bruité.
        foodDao.insertFromServer(sampleFood(synced = false, pendingDeletion = true))

        val stored = foodDao.getByUUID("f-1")!!
        assertTrue("payload serveur doit être stocké synced=true", stored.synced)
        assertFalse("payload serveur doit être stocké pendingDeletion=false", stored.pendingDeletion)
    }

    @Test
    fun `deleting a Food cascades to its food portions`() = runTest {
        foodDao.insertFromServer(sampleFood())
        foodPortionDao.insertFromServer(
            FoodPortion(uuid = "fp-1", foodUUID = "f-1", label = "1 oeuf", grams = 60f, synced = true)
        )
        assertNotNull(foodPortionDao.getByUUID("fp-1"))

        foodDao.delete(sampleFood())

        assertNull("la portion doit être supprimée en CASCADE avec son Food parent", foodPortionDao.getByUUID("fp-1"))
    }

    @Test
    fun `deleting a Food nulls meal_entry food_uuid but keeps the snapshot row`() = runTest {
        mealDao.insertFromServer(
            Meal(uuid = "m-1", userId = 1, date = "2026-06-17", name = "Petit-dej", orderIndex = 0, synced = true)
        )
        foodDao.insertFromServer(sampleFood())
        mealEntryDao.insertFromServer(
            MealEntry(
                uuid = "me-1",
                mealUUID = "m-1",
                foodUUID = "f-1",
                displayName = "Egg",       // snapshot D5 figé
                quantityG = 120f,
                kcalPer100g = 143f,
                proteinPer100g = 12.6f,
                carbsPer100g = 0.7f,
                fatPer100g = 9.9f,
                synced = true,
            )
        )

        foodDao.delete(sampleFood())

        val entry = mealEntryDao.getByUUID("me-1")
        assertNotNull("la meal_entry doit survivre (snapshot) à la suppression de l'aliment", entry)
        assertNull("food_uuid doit passer à NULL (SET NULL)", entry!!.foodUUID)
        assertEquals("le snapshot display_name doit être préservé", "Egg", entry.displayName)
        assertEquals(143f, entry.kcalPer100g)
    }
}
