package com.example.sportapp.feature.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.feature.nutrition.ui.NutritionJournalViewModel
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Suivi des sucres (2026-07-13) — comportement « total du jour » du flow
 * `daySugarG` de [NutritionJournalViewModel]. Les helpers purs `sumSugarG` /
 * `sugarLimitsG` sont déjà couverts par `JournalDomainTest` ; ici on verrouille
 * ce que SEUL le ViewModel ajoute : le filtrage des entries par jour sélectionné
 * (réactif au changement de jour) et l'exclusion des lignes soft-deleted.
 *
 * Pattern validé sur `NutritionStatsViewModelTest` / `NutritionGoalsViewModelTest` :
 * Room in-memory réel + SyncEngine mocké + Executor synchrone, collecte des
 * StateFlow `WhileSubscribed` dans le scope du test puis lecture de `.value`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NutritionJournalSugarTest {

    private lateinit var db: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private val directExecutor = Executor { it.run() }

    private fun meal(uuid: String, date: String) =
        Meal(uuid = uuid, userId = 1, date = date, name = "M", orderIndex = 0)

    private fun entry(
        uuid: String, mealUuid: String, qty: Float, sugar: Float?,
        pendingDeletion: Boolean = false,
    ) = MealEntry(
        uuid = uuid, mealUUID = mealUuid, displayName = "E", quantityG = qty,
        kcalPer100g = 0f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 0f,
        sugarPer100g = sugar, pendingDeletion = pendingDeletion,
    )

    private fun newViewModel() = NutritionJournalViewModel(
        mealDao = db.mealDao(),
        mealEntryDao = db.mealEntryDao(),
        mealPresetDao = db.mealPresetDao(),
        nutritionGoalDao = db.nutritionGoalDao(),
        foodDao = db.foodDao(),
        foodPortionDao = db.foodPortionDao(),
        waterIntakeDao = db.waterIntakeDao(),
        healthGoalDao = db.healthGoalDao(),
        syncEngine = mockk(relaxed = true),
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
    fun `daySugarG sums only the selected day and follows day changes`() = runTest(testDispatcher) {
        // Jour A : 200 g à 10 g/100 g (20 g) + 50 g à 4 g/100 g (2 g) = 22 g.
        // Une entry soft-deleted très sucrée le même jour ne doit PAS compter.
        // Jour B : 100 g à 30 g/100 g = 30 g — ne doit compter qu'après selectDay(B).
        db.mealDao().insert(meal("mA", "2026-07-10"))
        db.mealDao().insert(meal("mB", "2026-07-11"))
        db.mealEntryDao().insert(entry("e1", "mA", qty = 200f, sugar = 10f))
        db.mealEntryDao().insert(entry("e2", "mA", qty = 50f, sugar = 4f))
        db.mealEntryDao().insert(entry("e-dead", "mA", qty = 100f, sugar = 99f, pendingDeletion = true))
        db.mealEntryDao().insert(entry("e3", "mB", qty = 100f, sugar = 30f))

        val vm = newViewModel()
        val job = launch { vm.daySugarG.collect {} }

        vm.selectDay("2026-07-10")
        runCurrent()
        assertEquals(
            "seules les entries vivantes du jour sélectionné comptent",
            22f, vm.daySugarG.value, 0.001f,
        )

        vm.selectDay("2026-07-11")
        runCurrent()
        assertEquals(
            "changer de jour recalcule le total sucres de CE jour",
            30f, vm.daySugarG.value, 0.001f,
        )

        job.cancel()
    }
}
