package com.example.sportapp.feature.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.stats.StatsRange
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.ui.NutritionStatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.Executor

/**
 * Nutrition A6 (2026-06-17, refonte pager 2026-07-06) — comportement du
 * `NutritionStatsViewModel`. La logique pure (top aliments, agrégat de série, granularité)
 * est déjà couverte par `NutritionStatsTest`. Ici on verrouille ce que SEUL le ViewModel
 * ajoute et que le domaine ne peut pas tester :
 *   - `macroCards` expose, pour CHAQUE macro à la fois (le pager lit sa propre page sans
 *     glitch), le graphe ET le top aliments cohérents avec ce macro ;
 *   - la période « Tout » (StatsRange.All) est clampée à la date du repas le plus ancien —
 *     sinon les buckets partiraient de l'an 2000 (centaines de barres vides) ;
 *   - les lignes pendingDeletion (soft-delete) sont exclues du graphe et du top.
 *
 * Pattern validé sur `NutritionGoalsViewModelTest` : Room in-memory réel + Executor
 * synchrone. On collecte les StateFlow `WhileSubscribed` dans le scope du test pour
 * forcer le calcul amont, puis on lit `.value`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NutritionStatsViewModelTest {

    private lateinit var db: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private val directExecutor = Executor { it.run() }

    // Date relative DANS la fenêtre par défaut du VM (Last30Days depuis LocalDate.now()).
    // Régression QA 2026-07-13 : les dates en dur ("2026-06-10") sortaient de la fenêtre
    // avec le temps → topFoods/série vides → 3 tests rouges par pourrissement, pas par bug.
    private val inWindowDate = LocalDate.now().minusDays(5).toString()

    private fun meal(uuid: String, date: String, pendingDeletion: Boolean = false) =
        Meal(uuid = uuid, userId = 1, date = date, name = "M", orderIndex = 0, pendingDeletion = pendingDeletion)

    // foodUUID/recipeUUID sont des FK réelles en base : on ne les renseigne pas (aucune
    // table foods/recipes seedée ici). topFoodsByMacro regroupe alors par displayName,
    // ce qui suffit : chaque aliment a un nom distinct.
    private fun entry(
        uuid: String, mealUuid: String, qty: Float,
        kcal: Float = 0f, p: Float = 0f, name: String = "E",
        pendingDeletion: Boolean = false,
    ) = MealEntry(
        uuid = uuid, mealUUID = mealUuid, displayName = name, quantityG = qty,
        kcalPer100g = kcal, proteinPer100g = p, carbsPer100g = 0f, fatPer100g = 0f,
        pendingDeletion = pendingDeletion,
    )

    private fun newViewModel() = NutritionStatsViewModel(
        mealDao = db.mealDao(),
        mealEntryDao = db.mealEntryDao(),
        nutritionGoalDao = db.nutritionGoalDao(),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `macroCards expose per-macro chart series AND top foods together`() = runTest(testDispatcher) {
        // Chicken domine en KCAL (300), Whey domine en PROTEIN (40g) : chaque carte de macro
        // doit porter un top ET une série cohérents avec SON macro (données précalculées par le pager).
        db.mealDao().insert(meal("m1", inWindowDate))
        db.mealEntryDao().insert(entry("e1", "m1", qty = 100f, kcal = 300f, p = 0f, name = "Chicken"))
        db.mealEntryDao().insert(entry("e2", "m1", qty = 100f, kcal = 10f, p = 40f, name = "Whey"))

        val vm = newViewModel()
        val job = launch { vm.macroCards.collect {} }
        runCurrent()

        val cards = vm.macroCards.value
        val kcal = cards.first { it.macro == MacroKey.KCAL }
        assertEquals("Chicken", kcal.topFoods.first().displayName)
        assertEquals(310f, kcal.chart.series.consumed.sum(), 0.01f)

        val protein = cards.first { it.macro == MacroKey.PROTEIN }
        assertEquals("le top de la carte PROTEIN doit être Whey", "Whey", protein.topFoods.first().displayName)
        assertEquals("la série de la carte PROTEIN doit compter les protéines", 40f, protein.chart.series.consumed.sum(), 0.01f)

        job.cancel()
    }

    @Test
    fun `All range clamps chart start to the earliest meal instead of year 2000`() = runTest(testDispatcher) {
        // Repas le plus ancien = today-10j (11 jours inclus -> granularité DAILY). Sans
        // clamp, StatsRange.All partirait de 2000-01-01 -> > 1300 buckets hebdo. Avec
        // clamp -> les 11 jours seulement, 1er bucket = la date du repas le plus ancien.
        val earliest = LocalDate.now().minusDays(10).toString()
        db.mealDao().insert(meal("m1", earliest))
        db.mealEntryDao().insert(entry("e1", "m1", qty = 100f, kcal = 200f))

        val vm = newViewModel()
        val job = launch { vm.macroCards.collect {} }
        runCurrent()

        vm.setRange(StatsRange.All)
        runCurrent()

        val buckets = vm.macroCards.value.first().chart.series.buckets
        assertTrue("« Tout » doit être clampé au 1er repas (pas l'an 2000)", buckets.size < 60)
        assertEquals(
            "le 1er bucket doit être la date du repas le plus ancien, pas l'an 2000",
            earliest, buckets.first(),
        )

        job.cancel()
    }

    @Test
    fun `pendingDeletion meals and entries are excluded from chart and top foods`() = runTest(testDispatcher) {
        db.mealDao().insert(meal("m-live", inWindowDate))
        db.mealDao().insert(meal("m-dead", inWindowDate, pendingDeletion = true))
        db.mealEntryDao().insert(entry("e-live", "m-live", qty = 100f, kcal = 100f, name = "Live"))
        // entry rattachée à un meal soft-deleted -> son meal disparaît -> entry orpheline ignorée
        db.mealEntryDao().insert(entry("e-on-dead-meal", "m-dead", qty = 100f, kcal = 999f, name = "OnDeadMeal"))
        // entry elle-même soft-deleted sur un meal vivant -> ignorée
        db.mealEntryDao().insert(entry("e-dead", "m-live", qty = 100f, kcal = 999f, name = "DeadEntry", pendingDeletion = true))

        val vm = newViewModel()
        val job = launch { vm.macroCards.collect {} }
        runCurrent()

        val kcal = vm.macroCards.value.first { it.macro == MacroKey.KCAL }
        val names = kcal.topFoods.map { it.displayName }
        assertEquals("seul l'aliment vivant doit rester", listOf("Live"), names)
        assertEquals("le graphe ne compte que l'entry vivante (100 kcal)", 100f, kcal.chart.series.consumed.sum(), 0.01f)

        job.cancel()
    }

    @Test
    fun `top foods per macro are capped at 5`() = runTest(testDispatcher) {
        db.mealDao().insert(meal("m1", inWindowDate))
        // 7 aliments distincts, kcal décroissants (Food1=700 … Food7=100) → seuls les 5 premiers restent.
        (1..7).forEach { i ->
            db.mealEntryDao().insert(entry("e$i", "m1", qty = 100f, kcal = (100 * (8 - i)).toFloat(), name = "Food$i"))
        }

        val vm = newViewModel()
        val job = launch { vm.macroCards.collect {} }
        runCurrent()

        val kcal = vm.macroCards.value.first { it.macro == MacroKey.KCAL }
        assertEquals("le top aliments est plafonné à 5 par macro", 5, kcal.topFoods.size)
        assertEquals(
            "ce sont les 5 plus caloriques (Food6/Food7 exclus)",
            listOf("Food1", "Food2", "Food3", "Food4", "Food5"),
            kcal.topFoods.map { it.displayName },
        )

        job.cancel()
    }
}
