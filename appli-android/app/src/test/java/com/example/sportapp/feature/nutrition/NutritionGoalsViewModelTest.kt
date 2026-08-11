package com.example.sportapp.feature.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.local.NutritionGoalDao
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.feature.nutrition.ui.NutritionGoalsViewModel
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Nutrition A5 (2026-06-17) — comportement du `NutritionGoalsViewModel`. La logique
 * pure (dérivation macro-first, agrégat 7 j) est déjà couverte par `GoalAnalysisTest`.
 * Ici on verrouille le mécanisme *neuf* de A5 que le domaine ne peut pas tester :
 *   - « Nouvelle cible » crée une NOUVELLE entrée d'historique (nouvel uuid) au lieu
 *     d'écraser la cible active — c'est le coeur du versionnage par effectiveFrom.
 *   - le kcal stocké est DÉRIVÉ des macros (D12), pas la base brute saisie.
 *   - « Modifier » édite une entrée en place (même uuid), « Supprimer » soft-delete.
 *
 * Pattern validé sur `RecipesViewModelTest` : Room in-memory réel + SyncEngine mocké,
 * Executor synchrone côté Room pour que les `viewModelScope.launch {}` s'exécutent de
 * bout en bout avant les assertions (déterminisme).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NutritionGoalsViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var goalDao: NutritionGoalDao
    private lateinit var syncEngine: SyncEngine
    private lateinit var viewModel: NutritionGoalsViewModel

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
        goalDao = db.nutritionGoalDao()
        syncEngine = mockk(relaxed = true)

        viewModel = NutritionGoalsViewModel(
            nutritionGoalDao = goalDao,
            mealDao = db.mealDao(),
            mealEntryDao = db.mealEntryDao(),
            syncEngine = syncEngine,
        )
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Les cibles vivantes (non soft-deleted), plus récentes d'abord. */
    private suspend fun liveGoals(): List<NutritionGoal> =
        goalDao.getAllOnce().filter { !it.pendingDeletion }.sortedByDescending { it.effectiveFrom }

    @Test
    fun `createGoal stores a goal whose kcal is derived from macros, not the raw base`() = runTest {
        // P180 / G250 / L80 -> base 2440 -> dérivé ~2515 (espace fibres D12).
        viewModel.createGoal(effectiveFrom = "2026-06-17", proteinG = 180f, carbsG = 250f, fatG = 80f)

        val goals = liveGoals()
        assertEquals(1, goals.size)
        val g = goals.single()
        assertEquals(180f, g.proteinG)
        assertEquals(250f, g.carbsG)
        assertEquals(80f, g.fatG)
        assertEquals("le kcal doit être dérivé (macro-first D12)", 2515f, g.kcal, 1f)
        assertNotEquals("le kcal ne doit pas être la base brute 4P+4G+9L", 2440f, g.kcal)
        coVerify { syncEngine.pushEntityClass(NutritionGoal::class) }
    }

    @Test
    fun `createGoal adds a NEW history entry instead of overwriting the active one`() = runTest {
        viewModel.createGoal("2026-01-01", proteinG = 150f, carbsG = 200f, fatG = 60f)
        viewModel.createGoal("2026-06-17", proteinG = 180f, carbsG = 250f, fatG = 80f)

        val goals = liveGoals()
        assertEquals("« Nouvelle cible » doit créer une 2e entrée, pas écraser la 1re", 2, goals.size)
        // Deux entrées d'historique distinctes (uuids différents), triées récent d'abord.
        assertEquals(listOf("2026-06-17", "2026-01-01"), goals.map { it.effectiveFrom })
        assertEquals("les deux entrées doivent avoir des uuid distincts", 2, goals.map { it.uuid }.toSet().size)
    }

    @Test
    fun `updateGoal edits the same entry in place and re-derives kcal`() = runTest {
        viewModel.createGoal("2026-06-17", proteinG = 180f, carbsG = 250f, fatG = 80f)
        val original = liveGoals().single()

        viewModel.updateGoal(original, effectiveFrom = "2026-06-17", proteinG = 100f, carbsG = 100f, fatG = 30f)

        val goals = liveGoals()
        assertEquals("« Modifier » ne doit pas créer de nouvelle entrée", 1, goals.size)
        val updated = goals.single()
        assertEquals("l'uuid doit être préservé (update en place)", original.uuid, updated.uuid)
        assertEquals(100f, updated.proteinG)
        // base 1070 -> dérivé ~1103, différent de l'ancien ~2515.
        assertEquals(1103f, updated.kcal, 1f)
        assertNotEquals(original.kcal, updated.kcal)
    }

    @Test
    fun `deleteGoal soft-deletes via pendingDeletion so the day falls back to the previous goal`() = runTest {
        viewModel.createGoal("2026-06-17", proteinG = 180f, carbsG = 250f, fatG = 80f)
        val goal = liveGoals().single()

        viewModel.deleteGoal(goal)

        val after = goalDao.getByUUID(goal.uuid)
        assertNotNull("soft-delete : la row subsiste pour la convergence de sync", after)
        assertTrue("la cible supprimée doit être pendingDeletion", after!!.pendingDeletion)
        assertEquals("plus aucune cible vivante", 0, liveGoals().size)
        coVerify { syncEngine.pushEntityClass(NutritionGoal::class) }
    }
}
