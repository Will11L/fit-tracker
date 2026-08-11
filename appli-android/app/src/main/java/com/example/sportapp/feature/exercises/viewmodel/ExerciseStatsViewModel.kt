package com.example.sportapp.feature.exercises.viewmodel
import com.example.sportapp.core.stats.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.projections.ExerciseAllTimeStatsRow
import com.example.sportapp.core.data.model.projections.ExerciseDailyStatsRow
import com.example.sportapp.core.utils.CustomDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

/**
 * VM du sous-ecran ExerciseStatsScreen. Range partage via [StatsRangeState]
 * singleton (commun avec StatsViewModel + MuscleStatsViewModel).
 *
 * Recoit l'exerciseUUID via [setExerciseUUID] (pattern SessionTabViewModel) :
 * UUID arrive dans le NavBackStackEntry et est pose par un LaunchedEffect.
 *
 * Cf. CLAUDE.md historique 2026-05-07 (B3-2 Etape 5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseStatsViewModel @Inject constructor(
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val rangeState: StatsRangeState,
    private val onboardingRepo: com.example.sportapp.feature.onboarding.data.OnboardingRepository,
) : ViewModel() {

    /** Unité poids choisie par l'user. */
    val weightUnit: StateFlow<com.example.sportapp.feature.onboarding.data.WeightUnit> =
        onboardingRepo.preferences
            .map { it.weightUnit }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.example.sportapp.feature.onboarding.data.WeightUnit.KG
            )

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _exerciseUUID = MutableStateFlow<String?>(null)

    val range: StateFlow<StatsRange> = rangeState.range

    val exercise: StateFlow<Exercise?> =
        _exerciseUUID
            .flatMapLatest { uuid ->
                if (uuid == null) flowOf(null) else exerciseDao.observeByUUID(uuid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dailyStats: StateFlow<List<ExerciseDailyStatsRow>> =
        combine(_exerciseUUID, range) { uuid, r -> uuid to r }
            .flatMapLatest { (uuid, r) ->
                if (uuid == null) {
                    flowOf(emptyList())
                } else {
                    val (start, end) = r.computeBounds(CustomDateUtils.getTodayLocalDate(zone))
                    actualWorkoutSetDao.observeExerciseDailyStats(uuid, start, end)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTimeStats: StateFlow<ExerciseAllTimeStatsRow?> =
        _exerciseUUID
            .flatMapLatest { uuid ->
                if (uuid == null) flowOf(null) else actualWorkoutSetDao.observeExerciseAllTimeStats(uuid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setExerciseUUID(uuid: String) {
        _exerciseUUID.value = uuid
    }

    fun setRange(range: StatsRange) {
        rangeState.setRange(range)
    }
}
