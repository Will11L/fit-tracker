package com.example.sportapp.feature.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.network.OffProduct
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.feature.nutrition.domain.FoodSource
import com.example.sportapp.feature.nutrition.ui.FoodCatalogueViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Nutrition — scan code-barres : verrouille le mapping barcode → import du
 * `FoodCatalogueViewModel`. Le scan résout un code-barres via le proxy OFF puis
 * enchaîne sur `importOff`, qui dédup par `sourceRef` (= code-barres) : un
 * re-scan du même produit ne doit PAS créer de doublon mais réutiliser le Food
 * déjà présent (critère d'acceptation).
 *
 * Pattern (cf. RecipesViewModelTest) : Room in-memory réel + SyncEngine mocké +
 * Executor synchrone pour que les `viewModelScope.launch {}` d'`importOff`
 * s'exécutent de bout en bout avant les assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FoodCatalogueBarcodeImportTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: FoodCatalogueViewModel
    private lateinit var syncEngine: SyncEngine

    private val directExecutor = Executor { it.run() }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        syncEngine = mockk(relaxed = true)
        viewModel = FoodCatalogueViewModel(
            foodDao = db.foodDao(),
            foodPortionDao = db.foodPortionDao(),
            syncEngine = syncEngine,
        )
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Produit OFF normalisé tel que renvoyé par `GET /nutrition/off/product/{barcode}`. */
    private fun offProduct(barcode: String) = OffProduct(
        sourceRef = barcode,
        name = "Nutella",
        brand = "Ferrero",
        kcalPer100g = 539f,
        proteinPer100g = 6.3f,
        carbsPer100g = 57.5f,
        fatPer100g = 30.9f,
    )

    @Test
    fun `importing a scanned product creates an OFF food keyed by its barcode`() = runTest {
        val product = offProduct("3017620422003")
        var picked: Food? = null

        viewModel.importOff(product) { picked = it }

        val foods = db.foodDao().getAllOnce().filter { !it.pendingDeletion }
        assertEquals("un aliment doit être importé", 1, foods.size)
        assertEquals(FoodSource.OFF, foods[0].source)
        assertEquals("le code-barres devient le sourceRef", "3017620422003", foods[0].sourceRef)
        assertEquals("l'aliment importé remonte à onDone (pick)", "3017620422003", picked?.sourceRef)
    }

    @Test
    fun `re-scanning the same barcode dedups - reuses existing food, no duplicate`() = runTest {
        val product = offProduct("3017620422003")

        var first: Food? = null
        viewModel.importOff(product) { first = it }
        var second: Food? = null
        viewModel.importOff(product) { second = it }

        val foods = db.foodDao().getAllOnce().filter {
            it.source == FoodSource.OFF && it.sourceRef == "3017620422003" && !it.pendingDeletion
        }
        assertEquals("dédup par sourceRef : un seul Food pour ce code-barres", 1, foods.size)
        assertEquals("le re-scan renvoie le Food déjà présent (même row)", first?.uuid, second?.uuid)
    }

    @Test
    fun `importing a product with a serving size creates a portion whose label has no doubled grams`() = runTest {
        // serving_size OFF contient déjà « (15 g) » : le label stocké doit être
        // nettoyé pour que le chip n'affiche pas « 1 portion (15 g) (15 g) ».
        val product = offProduct("3017620422003").copy(servingSize = "1 portion (15 g)", servingQuantityG = 15f)

        viewModel.importOff(product)

        val portions = db.foodPortionDao().getAllOnce().filter { !it.pendingDeletion }
        assertEquals("la portion serving_size doit être créée", 1, portions.size)
        assertEquals(15f, portions[0].grams)
        assertEquals("le grammage ne doit pas être dupliqué dans le label", "1 portion", portions[0].label)
    }
}
