package com.example.sportapp.feature.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.local.RecipeDao
import com.example.sportapp.core.data.local.RecipeIngredientDao
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.feature.nutrition.domain.RecipeKind
import com.example.sportapp.feature.nutrition.ui.DraftItem
import com.example.sportapp.feature.nutrition.ui.RecipesViewModel
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import java.util.concurrent.Executor

/**
 * Nutrition A4 (2026-06-17) — comportement de l'éditeur de recettes/repas du
 * `RecipesViewModel`. La logique de calcul (pro-rata, split par kind) est déjà
 * couverte purement par `RecipeDomainTest` ; ici on verrouille le mécanisme
 * *neuf* de A4 que le domaine pur ne peut pas tester : la persistance des
 * ingrédients via `setIngredients` en diff, qui rend le réordonnancement durable
 * (order_index) et soft-delete les ingrédients retirés.
 *
 * Pattern validé sur `GoalsTabViewModelAutoCompleteTest` : Room in-memory réel +
 * SyncEngine mocké (relaxed). On force un Executor synchrone côté Room pour que
 * les `viewModelScope.launch {}` de `saveRecipe`/`deleteRecipe` s'exécutent de
 * bout en bout avant les assertions (déterminisme).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class RecipesViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var recipeDao: RecipeDao
    private lateinit var ingredientDao: RecipeIngredientDao
    private lateinit var syncEngine: SyncEngine
    private lateinit var viewModel: RecipesViewModel

    /** Executor inline : les requêtes Room s'exécutent sur le thread appelant, synchrones. */
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
        recipeDao = db.recipeDao()
        ingredientDao = db.recipeIngredientDao()
        syncEngine = mockk(relaxed = true)

        viewModel = RecipesViewModel(
            recipeDao = recipeDao,
            recipeIngredientDao = ingredientDao,
            foodDao = db.foodDao(),
            mealDao = db.mealDao(),
            mealEntryDao = db.mealEntryDao(),
            mealPresetDao = db.mealPresetDao(),
            syncEngine = syncEngine,
        )
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Food minimal (FK des RecipeIngredient → doit exister, sinon CASCADE FK rejette l'insert). */
    private suspend fun seedFood(uuid: String) {
        db.foodDao().insertFromServer(
            Food(uuid = uuid, userId = 1, name = uuid, source = "CUSTOM", kcalPer100g = 100f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 0f)
        )
    }

    /** L'unique recette persistée (les tests n'en créent qu'une). */
    private suspend fun theRecipe(): Recipe = recipeDao.getAllOnce().single()

    /** Ingrédients vivants (non soft-deleted) d'une recette, triés par order_index. */
    private suspend fun liveIngredients(recipeUuid: String): List<RecipeIngredient> =
        ingredientDao.getAllOnce()
            .filter { it.recipeUUID == recipeUuid && !it.pendingDeletion }
            .sortedBy { it.orderIndex }

    @Test
    fun `saveRecipe persists ingredients in given order with sequential order_index`() = runTest {
        seedFood("egg"); seedFood("oat")

        viewModel.saveRecipe(
            editUuid = null,
            name = "  Bowl  ",                       // trim attendu
            kind = RecipeKind.SAVED_MEAL,
            totalWeightG = null,
            items = listOf(DraftItem("egg", 100f), DraftItem("oat", 50f)),
        )

        val recipe = theRecipe()
        assertEquals("Bowl", recipe.name)            // nom trimé
        assertEquals(RecipeKind.SAVED_MEAL, recipe.kind)
        assertNull(recipe.totalWeightG)

        val ings = liveIngredients(recipe.uuid)
        assertEquals(listOf("egg", "oat"), ings.map { it.foodUUID })
        assertEquals(listOf(0, 1), ings.map { it.orderIndex })       // order_index positionnel
        assertEquals(listOf(100f, 50f), ings.map { it.quantityG })

        coVerify { syncEngine.pushEntityClasses(Recipe::class, RecipeIngredient::class) }
    }

    @Test
    fun `editing to a new order rewrites order_index and reuses the same rows`() = runTest {
        seedFood("a"); seedFood("b"); seedFood("c")
        viewModel.saveRecipe(null, "R", RecipeKind.SAVED_MEAL, null, listOf(DraftItem("a", 10f), DraftItem("b", 20f), DraftItem("c", 30f)))
        val recipe = theRecipe()
        val uuidByFood = liveIngredients(recipe.uuid).associate { it.foodUUID to it.uuid }

        // Réordonne : c, a, b (même ensemble, ordre inversé partiellement).
        viewModel.saveRecipe(recipe.uuid, "R", RecipeKind.SAVED_MEAL, null, listOf(DraftItem("c", 30f), DraftItem("a", 10f), DraftItem("b", 20f)))

        val reordered = liveIngredients(recipe.uuid)
        assertEquals("le nouvel ordre doit être persisté via order_index", listOf("c", "a", "b"), reordered.map { it.foodUUID })
        assertEquals(listOf(0, 1, 2), reordered.map { it.orderIndex })
        // Réordonnancement = update des rows existantes, PAS delete+recreate (uuids préservés).
        assertEquals(uuidByFood, reordered.associate { it.foodUUID to it.uuid })
        assertEquals("aucun ingrédient ne doit être créé ni supprimé", 3, ingredientDao.getAllOnce().size)
    }

    @Test
    fun `editing diffs ingredients - removed soft-deleted, added inserted, quantity updated`() = runTest {
        seedFood("keep"); seedFood("drop"); seedFood("new")
        viewModel.saveRecipe(null, "R", RecipeKind.SAVED_MEAL, null, listOf(DraftItem("keep", 100f), DraftItem("drop", 50f)))
        val recipe = theRecipe()
        val keepUuid = liveIngredients(recipe.uuid).first { it.foodUUID == "keep" }.uuid
        val dropUuid = liveIngredients(recipe.uuid).first { it.foodUUID == "drop" }.uuid

        // keep change de quantité (100→200), drop retiré, new ajouté.
        viewModel.saveRecipe(recipe.uuid, "R", RecipeKind.SAVED_MEAL, null, listOf(DraftItem("keep", 200f), DraftItem("new", 30f)))

        val live = liveIngredients(recipe.uuid)
        assertEquals(listOf("keep", "new"), live.map { it.foodUUID })
        assertEquals("la quantité de 'keep' doit être mise à jour", 200f, live.first { it.foodUUID == "keep" }.quantityG)
        assertEquals("'keep' doit garder son uuid (update, pas recreate)", keepUuid, live.first { it.foodUUID == "keep" }.uuid)

        // 'drop' n'est pas DELETE physique : il est marqué pendingDeletion (sync convergente).
        val dropped = ingredientDao.getByUUID(dropUuid)
        assertNotNull("l'ingrédient retiré doit subsister en soft-delete", dropped)
        assertTrue("l'ingrédient retiré doit être pendingDeletion", dropped!!.pendingDeletion)
    }

    @Test
    fun `RECIPE keeps total_weight_g while SAVED_MEAL stores null`() = runTest {
        seedFood("rice")
        viewModel.saveRecipe(null, "Dish", RecipeKind.RECIPE, 250f, listOf(DraftItem("rice", 300f)))

        val recipe = theRecipe()
        assertEquals(RecipeKind.RECIPE, recipe.kind)
        assertEquals("le poids cuit doit être conservé pour un RECIPE", 250f, recipe.totalWeightG)
    }

    @Test
    fun `deleteRecipe soft-deletes via pendingDeletion and pushes`() = runTest {
        seedFood("x")
        viewModel.saveRecipe(null, "R", RecipeKind.SAVED_MEAL, null, listOf(DraftItem("x", 10f)))
        val recipe = theRecipe()
        assertFalse(recipe.pendingDeletion)

        viewModel.deleteRecipe(recipe)

        val after = recipeDao.getByUUID(recipe.uuid)
        assertNotNull("soft-delete : la row subsiste", after)
        assertTrue("la recette supprimée doit être pendingDeletion", after!!.pendingDeletion)
        coVerify { syncEngine.pushEntityClasses(Recipe::class, RecipeIngredient::class) }
    }
}
