package com.example.sportapp.feature.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.local.NutritionGoalDao
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils.getTodayIsoDay
import com.example.sportapp.core.utils.CustomDateUtils.shiftIsoDay
import com.example.sportapp.feature.nutrition.domain.MacroTotals
import com.example.sportapp.feature.nutrition.domain.activeGoalFor
import com.example.sportapp.feature.nutrition.domain.averageDailyConsumed
import com.example.sportapp.feature.nutrition.domain.deriveGoalFromMacros
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel des Objectifs nutrition (A5). Source de vérité = Room (réactif). La
 * cible active un jour J = celle au plus grand `effectiveFrom` ≤ J (activeGoalFor,
 * §3.7) — mêmes données que le bandeau du Journal (A2). « Nouvelle cible » crée une
 * NOUVELLE entrée d'historique (nouvel uuid + effectiveFrom) ; « Modifier » édite
 * une entrée existante en place. Saisie macro-first (D12) : kcal/fibres dérivés.
 */
@HiltViewModel
class NutritionGoalsViewModel @Inject constructor(
    private val nutritionGoalDao: NutritionGoalDao,
    private val mealDao: MealDao,
    private val mealEntryDao: MealEntryDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    val today: String = getTodayIsoDay()
    private val weekStart: String = shiftIsoDay(today, -6)

    private val goals = nutritionGoalDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val meals = mealDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val entries = mealEntryDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }

    /** Historique des cibles, plus récentes d'abord (par effectiveFrom décroissant). */
    val history: StateFlow<List<NutritionGoal>> =
        goals.map { list -> list.sortedByDescending { it.effectiveFrom } }
            .stateIn(viewModelScope, started, emptyList())

    /** Cible active aujourd'hui (null si aucune définie pour la date). */
    val activeGoal: StateFlow<NutritionGoal?> =
        goals.map { activeGoalFor(it, today) }
            .stateIn(viewModelScope, started, null)

    /** Moyenne consommée /jour sur les 7 derniers jours (today-6 .. today). */
    val weekAvg: StateFlow<MacroTotals> =
        combine(meals, entries) { m, e -> averageDailyConsumed(m, e, weekStart, today) }
            .stateIn(viewModelScope, started, MacroTotals())

    /** Poids (kg) du user courant — source /me via CurrentUserManager (la table
     *  `users` Room n'est pas peuplée, UserSyncable supprimé F8-Q1). Base g/kg. */
    val weightKg: StateFlow<Float?> = CurrentUserManager.weightKgFlow

    /** Crée une NOUVELLE cible (nouvelle entrée d'historique) — kcal/fibres dérivés. */
    fun createGoal(effectiveFrom: String, proteinG: Float, carbsG: Float, fatG: Float) {
        viewModelScope.launch {
            val derived = deriveGoalFromMacros(proteinG, carbsG, fatG)
            nutritionGoalDao.insert(
                NutritionGoal(
                    uuid = UUID.randomUUID().toString(),
                    userId = CurrentUserManager.userId ?: 0,
                    effectiveFrom = effectiveFrom,
                    kcal = derived.kcal,
                    proteinG = proteinG,
                    carbsG = carbsG,
                    fatG = fatG,
                )
            )
            push()
        }
    }

    /** Modifie une entrée d'historique existante en place (corrige une cible passée). */
    fun updateGoal(goal: NutritionGoal, effectiveFrom: String, proteinG: Float, carbsG: Float, fatG: Float) {
        viewModelScope.launch {
            val derived = deriveGoalFromMacros(proteinG, carbsG, fatG)
            nutritionGoalDao.update(
                goal.copy(
                    effectiveFrom = effectiveFrom,
                    kcal = derived.kcal,
                    proteinG = proteinG,
                    carbsG = carbsG,
                    fatG = fatG,
                )
            )
            push()
        }
    }

    /** Supprime une cible (soft-delete convergent). Les jours retombent sur la précédente. */
    fun deleteGoal(goal: NutritionGoal) {
        viewModelScope.launch {
            nutritionGoalDao.markAsPendingDeletion(goal.uuid)
            push()
        }
    }

    private suspend fun push() {
        syncEngine.pushEntityClass(NutritionGoal::class)
    }
}
