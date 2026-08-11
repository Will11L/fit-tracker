package com.example.sportapp.feature.home.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.utils.CustomDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actualWorkoutDao: ActualWorkoutDao,
    private val plannedWorkoutDao: PlannedWorkoutDao,
    private val plannedWorkoutExerciseDao: PlannedWorkoutExerciseDao,
    private val actualWorkoutExerciseDao: ActualWorkoutExerciseDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) : ViewModel() {

    val userId: StateFlow<Int?> = CurrentUserManager.userIdFlow

    var loading = mutableStateOf(true)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    // ✅ planned today
    val plannedToday: StateFlow<PlannedWorkout?> =
        plannedWorkoutDao
            .observeWorkoutForToday(CustomDateUtils.getTodayDayOfWeek())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    /** True dès le 1er emit du Flow Room `todaySession`. Avant ça, on ne sait
     *  pas si `null` veut dire "pas de session aujourd'hui" ou "Flow pas
     *  encore re-query après instanciation du VM". HomeScreen attend ce flag
     *  avant de basculer entre SessionTab et NoSessionFallbackScreen (sinon
     *  flash "Currently sleeping" -> SessionTab au 1er render). */
    private val _initialSessionLoaded = MutableStateFlow(false)
    val initialSessionLoaded: StateFlow<Boolean> = _initialSessionLoaded.asStateFlow()

    val todaySession: StateFlow<ActualWorkout?> =
        actualWorkoutDao.observeActualWorkoutByDay(CustomDateUtils.getTodayIsoDay())
            .onEach { _initialSessionLoaded.value = true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessionUUID: StateFlow<String?> =
        todaySession
            .map { it?.uuid }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)


    init {
        loading.value = false
    }


    // ✅ optionnel : si tu veux démarrer une session depuis un planned
    fun startActualWorkoutFromPlanned(planned: PlannedWorkout) {
        if (userId.value == null){
            showSnackbar(message = context.getString(R.string.vm_user_id_missing), type = SnackbarType.ERROR)
            return
        }
        val uid = userId.value ?: return

        viewModelScope.launch {
            loading.value = true
            errorMessage.value = null

            try {
                val today = CustomDateUtils.getTodayIsoDay()

                // Optionnel: éviter doublon session du jour
                val existing = actualWorkoutDao.getActualWorkoutByDay(today)
                if (existing != null) {
                    //sessionUUID.value = existing.uuid
                    return@launch
                }

                // 1) Create ActualWorkout
                val workoutUUID = UUID.randomUUID().toString()
                val actual = ActualWorkout(
                    uuid = workoutUUID,
                    userId = uid,
                    name = planned.name,
                    date = today,
                    notes = null,
                    location = null,
                    isDone = false,
                    synced = false,
                    pendingDeletion = false
                )
                actualWorkoutDao.insert(actual)

                // 2) Fetch planned exercises
                val plannedExercises = plannedWorkoutExerciseDao
                    .getPlannedWorkoutExercisesByPlannedWorkoutUUID(planned.uuid)
                    .filter { !it.ignored && !it.pendingDeletion }
                    .sortedBy { it.order }

                // 3) Copy PlannedWorkoutExercise -> ActualWorkoutExercise + sets
                plannedExercises.forEach { pwe ->
                    val aweUUID = UUID.randomUUID().toString()

                    val awe = ActualWorkoutExercise(
                        uuid = aweUUID,
                        actualWorkoutUUID = workoutUUID,
                        exerciseUUID = pwe.exerciseUUID,
                        sets = pwe.sets,
                        reps = pwe.reps,            // String obligatoire chez toi
                        phase = pwe.phase,
                        status = "NOT_STARTED",
                        order = pwe.order,
                        addedManually = false,
                        synced = false,
                        pendingDeletion = false
                    )
                    actualWorkoutExerciseDao.insert(awe)

                    val setsToInsert = (1..pwe.sets).map { setIndex ->
                        ActualWorkoutSet(
                            uuid = UUID.randomUUID().toString(),
                            actualWorkoutExerciseUUID = aweUUID,
                            setOrder = setIndex,
                            reps = 0,
                            weight = 0f,
                            isDropset = false,
                            notes = null,
                            recommendation = null,
                            status = "NOT_STARTED",
                            synced = false,
                            pendingDeletion = false
                        )
                    }
                    actualWorkoutSetDao.insertAll(setsToInsert)
                }

                // 4) Update UI
                //sessionUUID.value = workoutUUID

                // 5) Sync (selon ta stratégie)
                syncEngine.pushEntityClass(ActualWorkout::class)
                syncEngine.pushEntityClass(ActualWorkoutExercise::class)
                syncEngine.pushEntityClass(ActualWorkoutSet::class)

            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to start planned session"
                showSnackbar(message = errorMessage.value ?: context.getString(R.string.vm_error_generic), type = SnackbarType.ERROR)
            } finally {
                loading.value = false
            }
        }
    }

    fun startNewActualWorkout(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (userId.value == null) return

        viewModelScope.launch {
            loading.value = true
            errorMessage.value = null
            try {
                val uuid = UUID.randomUUID().toString()
                val date = CustomDateUtils.getTodayIsoDay()
                val actual = ActualWorkout(
                    uuid = uuid,
                    userId = userId.value!!,
                    name = trimmed,
                    date = date,
                    notes = null,
                    location = null,
                    isDone = false,
                    synced = false,
                    pendingDeletion = false,
                    updatedAt = CustomDateUtils.getNowISO8601()
                )
                actualWorkoutDao.insert(actual)
                //sessionUUID.value = uuid

                syncEngine.pushEntityClass(ActualWorkout::class)

            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to create session"
                showSnackbar(message = errorMessage.value ?: context.getString(R.string.vm_error_generic), type = SnackbarType.ERROR)
            } finally {
                loading.value = false
            }
        }
    }
}
