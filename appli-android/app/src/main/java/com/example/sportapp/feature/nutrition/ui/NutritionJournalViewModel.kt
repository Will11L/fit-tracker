package com.example.sportapp.feature.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.FoodDao
import com.example.sportapp.core.data.local.FoodPortionDao
import com.example.sportapp.core.data.local.HealthGoalDao
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.local.MealPresetDao
import com.example.sportapp.core.data.local.NutritionGoalDao
import com.example.sportapp.core.data.local.WaterIntakeDao
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.data.model.WaterIntake
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import com.example.sportapp.core.utils.CustomDateUtils.getTodayIsoDay
import com.example.sportapp.core.utils.CustomDateUtils.shiftIsoDay
import com.example.sportapp.feature.nutrition.domain.JournalSection
import com.example.sportapp.feature.nutrition.domain.MacroTotals
import com.example.sportapp.feature.nutrition.domain.MicroRow
import com.example.sportapp.feature.nutrition.domain.DayRingTotals
import com.example.sportapp.feature.nutrition.domain.PastMeal
import com.example.sportapp.feature.nutrition.domain.activeGoalFor
import com.example.sportapp.feature.nutrition.domain.WATER_GOAL_TYPE
import com.example.sportapp.feature.nutrition.domain.activeWaterGoalMl
import com.example.sportapp.feature.nutrition.domain.buildSections
import com.example.sportapp.feature.nutrition.domain.dailyTotalsForMonth
import com.example.sportapp.feature.nutrition.domain.dayHydrationMl
import com.example.sportapp.feature.nutrition.domain.waterGoalUuid
import com.example.sportapp.feature.nutrition.domain.microRows
import com.example.sportapp.feature.nutrition.domain.pastMeals
import com.example.sportapp.feature.nutrition.domain.sumMicroTotals
import com.example.sportapp.feature.nutrition.domain.sumSugarG
import com.example.sportapp.feature.nutrition.domain.sumTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

/** État d'hydratation du jour affiché sur la card du Journal. */
data class HydrationUi(
    val consumedMl: Int = 0,
    /** Objectif ml/jour (HealthGoal WATER_ML actif), null si non défini. */
    val goalMl: Int? = null,
)

/**
 * ViewModel du Journal nutrition (A2). Source de vérité = Room (réactif) → les
 * cumuls/anneaux se recalculent en live à chaque écriture. Écrit via les DAOs
 * Style A (pattern identique aux autres VMs : GoalsTabViewModel) puis pousse au
 * serveur via `SyncEngine.pushEntityClass(...)`.
 *
 * Soft-delete : les suppressions passent par `markAsPendingDeletion` (sync
 * convergente) ; tous les flows filtrent `!pendingDeletion` pour un retrait
 * immédiat à l'écran. Snapshot D5 : une entry fige les macros de l'aliment.
 */
@HiltViewModel
class NutritionJournalViewModel @Inject constructor(
    private val mealDao: MealDao,
    private val mealEntryDao: MealEntryDao,
    private val mealPresetDao: MealPresetDao,
    private val nutritionGoalDao: NutritionGoalDao,
    private val foodDao: FoodDao,
    private val foodPortionDao: FoodPortionDao,
    private val waterIntakeDao: WaterIntakeDao,
    private val healthGoalDao: HealthGoalDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    // ─── Sélection (jour + mois affiché) ─────────────────────────────────────
    private val _selectedDay = MutableStateFlow(getTodayIsoDay())
    val selectedDay: StateFlow<String> = _selectedDay

    val today: String = getTodayIsoDay()

    private val _monthCursor = MutableStateFlow(YearMonth.now())
    val monthCursor: StateFlow<YearMonth> = _monthCursor

    // ─── Sources Room (filtrées !pendingDeletion) ────────────────────────────
    private val meals = mealDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val entries = mealEntryDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val presets = mealPresetDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }

    /** Repas récurrents triés (pour la sheet « Gérer les repas »). */
    val presetsList: StateFlow<List<MealPreset>> = presets
        .map { list -> list.sortedBy { it.orderIndex } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val goals = nutritionGoalDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }

    /** Catalogue de l'utilisateur (non archivé), pour le picker d'aliments. */
    val foods: StateFlow<List<Food>> =
        foodDao.observeAll()
            .map { list -> list.filter { !it.pendingDeletion && !it.archived } }
            .stateIn(viewModelScope, started, emptyList())

    /** Portions nommées (toutes) — le picker filtre par foodUUID. */
    val portions: StateFlow<List<FoodPortion>> =
        foodPortionDao.observeAll()
            .map { list -> list.filter { !it.pendingDeletion } }
            .stateIn(viewModelScope, started, emptyList())

    // ─── Vue du jour sélectionné ─────────────────────────────────────────────
    val sections: StateFlow<List<JournalSection>> =
        combine(presets, meals, entries, _selectedDay) { presetList, mealList, entryList, day ->
            val dayMeals = mealList.filter { it.date == day }.sortedBy { it.orderIndex }
            buildSections(presetList, dayMeals, entryList)
        }.stateIn(viewModelScope, started, emptyList())

    val dayTotals: StateFlow<MacroTotals> =
        combine(meals, entries, _selectedDay) { mealList, entryList, day ->
            val dayMealUuids = mealList.filter { it.date == day }.map { it.uuid }.toSet()
            sumTotals(entryList.filter { dayMealUuids.contains(it.mealUUID) })
        }.stateIn(viewModelScope, started, MacroTotals())

    val activeGoal: StateFlow<NutritionGoal?> =
        combine(goals, _selectedDay) { goalList, day -> activeGoalFor(goalList, day) }
            .stateIn(viewModelScope, started, null)

    /** Total sucres du jour (g) depuis les snapshots per-100g — recalcul réactif, comme dayTotals. */
    val daySugarG: StateFlow<Float> =
        combine(meals, entries, _selectedDay) { mealList, entryList, day ->
            val dayMealUuids = mealList.filter { it.date == day }.map { it.uuid }.toSet()
            sumSugarG(entryList.filter { dayMealUuids.contains(it.mealUUID) })
        }.stateIn(viewModelScope, started, 0f)

    val microRows: StateFlow<List<MicroRow>> =
        combine(meals, entries, _selectedDay) { mealList, entryList, day ->
            val dayMealUuids = mealList.filter { it.date == day }.map { it.uuid }.toSet()
            microRows(sumMicroTotals(entryList.filter { dayMealUuids.contains(it.mealUUID) }))
        }.stateIn(viewModelScope, started, emptyList())

    /** Cumuls + progression par jour du mois affiché (4 anneaux des cases). */
    val monthRingData: StateFlow<Map<String, DayRingTotals>> =
        combine(_monthCursor, entries, meals, goals) { cursor, entryList, mealList, goalList ->
            dailyTotalsForMonth(monthDayIsos(cursor), entryList, mealList, goalList)
        }.stateIn(viewModelScope, started, emptyMap())

    /** Repas non vides des jours passés (duplication). */
    val pastMeals: StateFlow<List<PastMeal>> =
        combine(meals, entries, _selectedDay) { mealList, entryList, day ->
            pastMeals(mealList, entryList, day)
        }.stateIn(viewModelScope, started, emptyList())

    // ─── Hydratation ─────────────────────────────────────────────────────────
    // Total du jour = prises manuelles (water_intakes) + boissons eau journalisées
    // (meal_entries dont l'aliment est is_water, 1 g = 1 ml). Objectif = HealthGoal
    // WATER_ML actif (versionné effective_from).
    private val waterIntakes = waterIntakeDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val healthGoals = healthGoalDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val waterFoodUuids = foodDao.observeAll()
        .map { list -> list.filter { !it.pendingDeletion && it.isWater }.map { it.uuid }.toSet() }

    private data class HydrationInputs(
        val intakes: List<WaterIntake>,
        val mealList: List<Meal>,
        val entryList: List<MealEntry>,
        val waterUuids: Set<String>,
    )

    private val hydrationInputs =
        combine(waterIntakes, meals, entries, waterFoodUuids) { i, m, e, w -> HydrationInputs(i, m, e, w) }

    /** État d'hydratation du jour sélectionné (consommé + objectif). */
    val hydration: StateFlow<HydrationUi> =
        combine(hydrationInputs, healthGoals, _selectedDay) { inp, goals, day ->
            val dayMealUuids = inp.mealList.filter { it.date == day }.map { it.uuid }.toSet()
            HydrationUi(
                consumedMl = dayHydrationMl(day, inp.intakes, dayMealUuids, inp.entryList, inp.waterUuids),
                goalMl = activeWaterGoalMl(goals, day),
            )
        }.stateIn(viewModelScope, started, HydrationUi())

    /** Y a-t-il au moins une prise manuelle annulable ce jour ? (pilote le bouton Annuler) */
    val canUndoWater: StateFlow<Boolean> =
        combine(waterIntakes, _selectedDay) { intakes, day -> intakes.any { it.date == day } }
            .stateIn(viewModelScope, started, false)

    /** Ajoute une prise d'eau manuelle (ml) au jour sélectionné + push. */
    fun addWater(amountMl: Int) {
        if (amountMl <= 0) return
        viewModelScope.launch {
            waterIntakeDao.insert(
                WaterIntake(
                    uuid = UUID.randomUUID().toString(),
                    userId = CurrentUserManager.userId ?: 0,
                    date = _selectedDay.value,
                    amountMl = amountMl,
                )
            )
            syncEngine.pushEntityClass(WaterIntake::class)
        }
    }

    /** Annule la dernière prise MANUELLE du jour (soft-delete). Les boissons eau
     *  journalisées ne sont pas des prises manuelles → se retirent en supprimant l'entrée. */
    fun undoLastWater() {
        viewModelScope.launch {
            val day = _selectedDay.value
            val last = waterIntakeDao.getAllOnce()
                .filter { it.date == day && !it.pendingDeletion }
                .maxByOrNull { it.createdAt ?: it.updatedAt ?: "" }
                ?: return@launch
            waterIntakeDao.markAsPendingDeletion(last.uuid)
            syncEngine.pushEntityClass(WaterIntake::class)
        }
    }

    /** Règle l'objectif d'hydratation du jour (ml/jour) : upsert HealthGoal WATER_ML
     *  (uuid déterministe user+type+jour, pattern STEPS) + push. Édité depuis la card. */
    fun setWaterGoal(amountMl: Int) {
        if (amountMl <= 0) return
        viewModelScope.launch {
            val userId = CurrentUserManager.userId ?: 0
            val uuid = waterGoalUuid(userId, today)
            val goal = HealthGoal(
                uuid = uuid,
                userId = userId,
                type = WATER_GOAL_TYPE,
                target = amountMl.toFloat(),
                effectiveFrom = today,
            )
            if (healthGoalDao.getByUUID(uuid) == null) healthGoalDao.insert(goal) else healthGoalDao.update(goal)
            syncEngine.pushEntityClass(HealthGoal::class)
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────────
    fun selectDay(iso: String) {
        _selectedDay.value = iso
        _monthCursor.value = YearMonth.from(java.time.LocalDate.parse(iso))
    }

    fun shiftDay(delta: Long) = selectDay(shiftIsoDay(_selectedDay.value, delta))

    fun goToday() = selectDay(getTodayIsoDay())

    fun prevMonth() { _monthCursor.value = _monthCursor.value.minusMonths(1) }
    fun nextMonth() { _monthCursor.value = _monthCursor.value.plusMonths(1) }

    // ─── CRUD entries / repas ────────────────────────────────────────────────

    /** Ajoute une entry (snapshot D5 de l'aliment) dans la section, créant le meal au besoin. */
    fun addEntryFromFood(section: JournalSection, food: Food, quantityG: Float, portionLabel: String?) {
        viewModelScope.launch {
            val mealUuid = ensureMeal(section)
            val entry = MealEntry(
                uuid = UUID.randomUUID().toString(),
                mealUUID = mealUuid,
                foodUUID = food.uuid,
                recipeUUID = null,
                displayName = food.name,
                quantityG = quantityG,
                portionLabel = portionLabel,
                kcalPer100g = food.kcalPer100g,
                proteinPer100g = food.proteinPer100g,
                carbsPer100g = food.carbsPer100g,
                fatPer100g = food.fatPer100g,
                fiberPer100g = food.fiberPer100g,
                sugarPer100g = food.sugarPer100g,
                satFatPer100g = food.satFatPer100g,
                saltPer100g = food.saltPer100g,
                ironPer100g = food.ironPer100g,
                calciumPer100g = food.calciumPer100g,
                magnesiumPer100g = food.magnesiumPer100g,
                zincPer100g = food.zincPer100g,
                potassiumPer100g = food.potassiumPer100g,
                sodiumPer100g = food.sodiumPer100g,
                vitaminCPer100g = food.vitaminCPer100g,
                vitaminDPer100g = food.vitaminDPer100g,
                vitaminB12Per100g = food.vitaminB12Per100g,
                vitaminAPer100g = food.vitaminAPer100g,
            )
            mealEntryDao.insert(entry)
            pushMeals()
        }
    }

    /** Met à jour la quantité d'une entry (le snapshot per-100 g D5 reste figé). */
    fun updateEntryQuantity(entry: MealEntry, quantityG: Float) {
        viewModelScope.launch {
            mealEntryDao.update(entry.copy(quantityG = quantityG, portionLabel = null))
            pushMeals()
        }
    }

    fun deleteEntry(entry: MealEntry) {
        viewModelScope.launch {
            mealEntryDao.markAsPendingDeletion(entry.uuid)
            pushMeals()
        }
    }

    /**
     * Ajoute un REPAS récurrent (meal_preset) : la section apparaît chaque jour
     * (distinction produit : repas = récurrents, collations = ponctuelles).
     */
    fun addMealPreset(name: String) {
        viewModelScope.launch {
            val maxOrder = mealPresetDao.getAllOnce()
                .filter { !it.pendingDeletion }
                .maxOfOrNull { it.orderIndex } ?: -1
            mealPresetDao.insert(
                MealPreset(
                    uuid = UUID.randomUUID().toString(),
                    userId = CurrentUserManager.userId ?: 0,
                    name = name.trim(),
                    orderIndex = maxOrder + 1,
                )
            )
            syncEngine.pushEntityClass(MealPreset::class)
        }
    }

    /** Renomme un repas récurrent (les repas déjà journalisés gardent leur nom). */
    fun renameMealPreset(preset: MealPreset, name: String) {
        viewModelScope.launch {
            mealPresetDao.update(
                preset.copy(name = name.trim(), synced = false, updatedAt = getNowISO8601())
            )
            syncEngine.pushEntityClass(MealPreset::class)
        }
    }

    /** Supprime un repas récurrent (soft-delete local ; les Meals journalisés survivent). */
    fun deleteMealPreset(preset: MealPreset) {
        viewModelScope.launch {
            mealPresetDao.markAsPendingDeletion(preset.uuid)
            syncEngine.pushEntityClass(MealPreset::class)
        }
    }

    /** Déplace un repas récurrent (↑/↓) : orderIndex resérialisés 0..n. */
    fun moveMealPreset(preset: MealPreset, delta: Int) {
        viewModelScope.launch {
            val list = mealPresetDao.getAllOnce()
                .filter { !it.pendingDeletion }
                .sortedBy { it.orderIndex }
            val from = list.indexOfFirst { it.uuid == preset.uuid }
            val to = from + delta
            if (from < 0 || to < 0 || to >= list.size) return@launch
            val reordered = list.toMutableList().apply { add(to, removeAt(from)) }
            reordered.forEachIndexed { idx, p ->
                if (p.orderIndex != idx) {
                    mealPresetDao.update(
                        p.copy(orderIndex = idx, synced = false, updatedAt = getNowISO8601())
                    )
                }
            }
            syncEngine.pushEntityClass(MealPreset::class)
        }
    }

    /** Ajoute une COLLATION (repas ad hoc, hors preset) au jour courant. `time` "HH:MM" facultatif. */
    fun addAdHocMeal(name: String, time: String? = null) {
        viewModelScope.launch {
            val maxOrder = sections.value.maxOfOrNull { it.orderIndex } ?: -1
            createMeal(
                date = _selectedDay.value,
                name = name.trim(),
                orderIndex = maxOrder + 1,
                presetUuid = null,
                time = time?.takeIf { it.isNotBlank() },
            )
            pushMeals()
        }
    }

    /** Supprime un repas et toutes ses entries (cascade serveur ; soft-delete local). */
    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            val mealEntries = mealEntryDao.getAllOnce().filter { it.mealUUID == meal.uuid }
            mealEntries.forEach { mealEntryDao.markAsPendingDeletion(it.uuid) }
            mealDao.markAsPendingDeletion(meal.uuid)
            pushMeals()
        }
    }

    /** Copie un repas passé sur le jour courant : nouveau Meal + entries re-snapshotées. */
    fun duplicateMeal(source: Meal) {
        viewModelScope.launch {
            val sourceEntries = mealEntryDao.getAllOnce()
                .filter { it.mealUUID == source.uuid && !it.pendingDeletion }
            val maxOrder = sections.value.maxOfOrNull { it.orderIndex } ?: -1
            val newMealUuid = createMeal(
                date = _selectedDay.value,
                name = source.name,
                orderIndex = maxOrder + 1,
                presetUuid = null,
            )
            sourceEntries.forEach { e ->
                mealEntryDao.insert(
                    e.copy(
                        uuid = UUID.randomUUID().toString(),
                        mealUUID = newMealUuid,
                        synced = false,
                        pendingDeletion = false,
                    )
                )
            }
            pushMeals()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Meal de la section, créé à la 1ʳᵉ entry seulement (§3.4 — pas de rows fantômes). */
    private suspend fun ensureMeal(section: JournalSection): String {
        section.meal?.let { return it.uuid }
        return createMeal(
            date = _selectedDay.value,
            name = section.name,
            orderIndex = section.orderIndex,
            presetUuid = section.presetUuid,
        )
    }

    private suspend fun createMeal(
        date: String,
        name: String,
        orderIndex: Int,
        presetUuid: String?,
        time: String? = null,
    ): String {
        val uuid = UUID.randomUUID().toString()
        mealDao.insert(
            Meal(
                uuid = uuid,
                userId = CurrentUserManager.userId ?: 0,
                date = date,
                name = name,
                orderIndex = orderIndex,
                presetUuid = presetUuid,
                time = time,
            )
        )
        return uuid
    }

    private suspend fun pushMeals() {
        syncEngine.pushEntityClasses(Meal::class, MealEntry::class)
    }

    private fun monthDayIsos(month: YearMonth): List<String> =
        (1..month.lengthOfMonth()).map { day -> month.atDay(day).toString() }
}
