package com.example.sportapp.feature.muscles.viewmodel
import com.example.sportapp.core.stats.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.ExerciseMuscleDao
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.local.MuscleGoalDao
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.data.model.projections.MuscleWeeklyVolumeRow
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
 * VM du sous-ecran MuscleStatsScreen. Range partage via [StatsRangeState].
 *
 * Cf. CLAUDE.md historique 2026-05-07 (B3-2 Etape 6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MuscleStatsViewModel @Inject constructor(
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val muscleDao: MuscleDao,
    private val exerciseMuscleDao: ExerciseMuscleDao,
    muscleGoalDao: MuscleGoalDao,
    private val rangeState: StatsRangeState,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _muscleUUID = MutableStateFlow<String?>(null)

    val range: StateFlow<StatsRange> = rangeState.range

    val muscle: StateFlow<Muscle?> =
        _muscleUUID
            .flatMapLatest { uuid ->
                if (uuid == null) flowOf(null) else muscleDao.observeMuscleByUUID(uuid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weeklyVolume: StateFlow<List<MuscleWeeklyVolumeRow>> =
        combine(_muscleUUID, range) { uuid, r -> uuid to r }
            .flatMapLatest { (uuid, r) ->
                if (uuid == null) {
                    flowOf(emptyList())
                } else {
                    val (start, end) = r.computeBounds(CustomDateUtils.getTodayLocalDate(zone))
                    actualWorkoutSetDao.observeMuscleWeeklyVolume(uuid, start, end)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val relatedExercises: StateFlow<List<Exercise>> =
        _muscleUUID
            .flatMapLatest { uuid ->
                if (uuid == null) flowOf(emptyList())
                else exerciseMuscleDao.observeExercisesByMuscle(uuid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Goal pour la semaine actuelle. Snapshot a l'init du VM (semaine ne change
     * pas pendant l'usage de l'ecran). MuscleGoal.target = String libre, on
     * l'affiche tel quel ; MuscleGoal.done = Int compteur cote serveur.
     */
    val currentWeekGoal: StateFlow<MuscleGoal?> =
        _muscleUUID
            .flatMapLatest { uuid ->
                if (uuid == null) {
                    flowOf(null)
                } else {
                    val weekISO = CustomDateUtils.getCurrentWeekISO()
                    muscleGoalDao
                        .observeGoalsForWeek(weekISO)
                        .map { list -> list.firstOrNull { it.muscleUUID == uuid } }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMuscleUUID(uuid: String) {
        _muscleUUID.value = uuid
    }

    fun setRange(range: StatsRange) {
        rangeState.setRange(range)
    }
}
