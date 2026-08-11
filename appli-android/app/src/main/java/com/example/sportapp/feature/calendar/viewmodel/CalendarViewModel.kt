package com.example.sportapp.feature.calendar.viewmodel

import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.sportapp.core.utils.CustomDateUtils
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.example.sportapp.R
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class CalendarSummaryUi(
    val perfectWeeksTotal: Int = 0,
    val completedDays: Int = 0,
    val nextWorkoutDate: LocalDate? = null
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
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

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _currentMonth = MutableStateFlow(YearMonth.now(zone))
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private fun monthRange(month: YearMonth): Pair<String, String> {
        val start = month.atDay(1).format(dayFormatter)
        val end = month.atEndOfMonth().format(dayFormatter)
        return start to end
    }

    // ---------- ACTUALS ----------
    val actualWorkoutsThisMonth: StateFlow<List<ActualWorkout>> =
        currentMonth
            .flatMapLatest { ym: YearMonth ->
                val (start, end) = monthRange(ym)
                actualWorkoutDao.observeActualWorkoutsForRange(start, end)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val actualByDay: StateFlow<Map<LocalDate, ActualWorkout>> =
        actualWorkoutsThisMonth
            .map { list: List<ActualWorkout> ->  // ✅ type explicite
                // ✅ on calcule la clé de groupBy explicitement
                val grouped: Map<LocalDate, List<ActualWorkout>> =
                    list.groupBy { aw: ActualWorkout ->
                        CustomDateUtils.toLocalDateFromDb(aw.date, zone)
                    }

                val mapped: Map<LocalDate, ActualWorkout> =
                    grouped.mapValues { (_, items: List<ActualWorkout>) ->
                        items.last()
                    }

                mapped
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---------- PLANNED ----------
    val plannedWorkouts: StateFlow<List<PlannedWorkout>> =
        plannedWorkoutDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plannedByWeekdayName: StateFlow<Map<String, PlannedWorkout>> =
        plannedWorkouts
            .map { list: List<PlannedWorkout> ->
                val map: Map<String, PlannedWorkout> =
                    list.groupBy { it.dayOfWeek.trim() }
                        .mapValues { (_, items) -> items.last() }

                map
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Month Progress
    val monthProgress: StateFlow<Float> =
        combine(
            currentMonth,
            plannedByWeekdayName,
            actualByDay
        ) { ym, plannedMap, actualMap ->

            val today = CustomDateUtils.getTodayLocalDate(zone)

            // plage calcul : du 1er du mois -> min(fin du mois, today)
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()

            var plannedSessions = 0
            var doneSessions = 0

            var d = start
            while (!d.isAfter(end)) {
                val weekday = weekdayName(d)
                val planned = plannedMap[weekday]

                val plannedHasSession =
                    planned != null && !planned.name.equals("Rest Day", ignoreCase = true)

                if (plannedHasSession) {
                    plannedSessions++

                    val aw = actualMap[d]
                    if (aw?.isDone == true) doneSessions++
                }

                d = d.plusDays(1)
            }

            val p = if (plannedSessions == 0) 0f else doneSessions.toFloat() / plannedSessions.toFloat()
            p
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    // Calendar Summary
    val calendarSummary: StateFlow<CalendarSummaryUi> =
        combine(currentMonth, actualByDay, plannedByWeekdayName) { ym, actualMap, plannedMap ->

            val today = CustomDateUtils.getTodayLocalDate(zone)
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()

            // ✅ On évite de pénaliser le futur dans le mois courant
            val effectiveEnd =
                if (ym == YearMonth.now(zone)) minOf(end, today) else end

            // ----- Completed days -----
            val completedDays =
                actualMap.count { (date, aw) ->
                    !date.isBefore(start) && !date.isAfter(end) && aw.isDone
                }

            // ----- Next workout date -----
            // 1er jour (aujourd'hui inclus -> +7) dont le PlannedWorkout n'est pas
            // "Rest Day". Aujourd'hui est sauté si sa séance est déjà faite.
            val todayDone =
                actualWorkoutDao.getActualWorkoutByDay(CustomDateUtils.toIsoDay(today))?.isDone == true
            val nextWorkoutDate: LocalDate? =
                (0..7).asSequence()
                    .map { today.plusDays(it.toLong()) }
                    .firstOrNull { date ->
                        val planned = plannedMap[weekdayName(date)]
                        val isWorkoutDay =
                            planned != null && !planned.name.equals("Rest Day", ignoreCase = true)
                        isWorkoutDay && !(date == today && todayDone)
                    }

            // ----- Perfect weeks total (non consécutif) -----
            fun isPlannedSession(date: LocalDate): Boolean {
                val planned = plannedMap[weekdayName(date)]
                return planned != null && !planned.name.equals("Rest Day", ignoreCase = true)
            }

            fun hasDoneActual(date: LocalDate): Boolean =
                actualMap[date]?.isDone == true

            // On itère sur toutes les semaines (lundi->dimanche) qui intersectent le mois
            val firstMonday = start.minusDays(((start.dayOfWeek.value + 6) % 7).toLong()) // Monday-first
            val lastSunday = end.plusDays((7 - ((end.dayOfWeek.value + 6) % 7) - 1).toLong())

            var perfectWeeksTotal = 0
            var weekStart = firstMonday

            while (!weekStart.isAfter(lastSunday)) {
                val weekEnd = weekStart.plusDays(6)

                // Jours de la semaine qui comptent (dans le mois + <= effectiveEnd)
                val daysToCheck =
                    (0..6).map { weekStart.plusDays(it.toLong()) }
                        .filter { d -> !d.isBefore(start) && !d.isAfter(effectiveEnd) }

                // Une semaine sans jours à checker (ex: full futur) => on ne la compte pas
                if (daysToCheck.isNotEmpty()) {
                    val weekIsPerfect = daysToCheck.all { d ->
                        if (isPlannedSession(d)) hasDoneActual(d) else true
                    }

                    if (weekIsPerfect) perfectWeeksTotal++
                }

                weekStart = weekStart.plusWeeks(1)
            }

            CalendarSummaryUi(
                perfectWeeksTotal = perfectWeeksTotal,
                completedDays = completedDays,
                nextWorkoutDate = nextWorkoutDate
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarSummaryUi())


    // Methods
    fun setMonth(month: YearMonth) {
        _currentMonth.value = month
    }

    fun weekdayName(date: LocalDate): String =
        when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "Monday"
            DayOfWeek.TUESDAY -> "Tuesday"
            DayOfWeek.WEDNESDAY -> "Wednesday"
            DayOfWeek.THURSDAY -> "Thursday"
            DayOfWeek.FRIDAY -> "Friday"
            DayOfWeek.SATURDAY -> "Saturday"
            DayOfWeek.SUNDAY -> "Sunday"
        }

    fun startActualWorkoutFromPlannedOnDate(
        date: LocalDate,
        onCreated: (createdUuid: String?) -> Unit
    ) {
        val uid = userId.value
        if (uid == null) {
            showSnackbar(context.getString(R.string.vm_user_id_missing), SnackbarType.ERROR)
            onCreated(null)
            return
        }

        viewModelScope.launch {
            try {
                val dayName = weekdayName(date) // "Monday" ... comme ta méthode existante
                val planned = plannedWorkoutDao.getWorkoutByDay(dayName)

                if (planned == null || planned.name.equals("Rest Day", ignoreCase = true)) {
                    showSnackbar(context.getString(R.string.vm_calendar_no_planned_for_day, dayName), SnackbarType.INFO)
                    onCreated(null)
                    return@launch
                }

                val day = CustomDateUtils.toIsoDay(date) // "yyyy-MM-dd"

                // éviter doublon actual sur ce jour
                val existing = actualWorkoutDao.getActualWorkoutByDay(day)
                if (existing != null) {
                    onCreated(existing.uuid)
                    return@launch
                }

                // 1) Create ActualWorkout
                val workoutUUID = UUID.randomUUID().toString()
                val actual = ActualWorkout(
                    uuid = workoutUUID,
                    userId = uid,
                    name = planned.name,
                    date = day,
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

                // 3) Copy planned → actual exercises + sets
                plannedExercises.forEach { pwe ->
                    val aweUUID = UUID.randomUUID().toString()

                    val awe = ActualWorkoutExercise(
                        uuid = aweUUID,
                        actualWorkoutUUID = workoutUUID,
                        exerciseUUID = pwe.exerciseUUID,
                        sets = pwe.sets,
                        reps = pwe.reps,
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

                // 4) Sync
                syncEngine.pushEntityClass(ActualWorkout::class)
                syncEngine.pushEntityClass(ActualWorkoutExercise::class)
                syncEngine.pushEntityClass(ActualWorkoutSet::class)

                onCreated(workoutUUID)

            } catch (e: Exception) {
                showSnackbar(e.message ?: context.getString(R.string.vm_calendar_failed_create_session), SnackbarType.ERROR)
                onCreated(null)
            }
        }
    }

    fun createNewActualWorkoutForDate(
        date: LocalDate,
        name: String = "New Session",
        onCreated: (uuid: String?) -> Unit
    ) {
        val uid = userId.value
        if (uid == null) {
            showSnackbar(context.getString(R.string.vm_user_id_missing), SnackbarType.ERROR)
            onCreated(null)
            return
        }

        viewModelScope.launch {
            try {
                val day = CustomDateUtils.toIsoDay(date) // "2025-12-31"

                // ✅ optionnel: éviter doublon sur cette date
                val existing = actualWorkoutDao.getActualWorkoutByDay(day)
                if (existing != null) {
                    onCreated(existing.uuid)
                    return@launch
                }

                val uuid = UUID.randomUUID().toString()
                val actual = ActualWorkout(
                    uuid = uuid,
                    userId = uid,
                    name = name.trim().ifEmpty { "New Session" },
                    date = day,
                    notes = null,
                    location = null,
                    isDone = false,
                    synced = false,
                    pendingDeletion = false,
                    updatedAt = CustomDateUtils.getNowISO8601()
                )

                actualWorkoutDao.insert(actual)

                // juste après: actualWorkoutDao.insert(actual)

                val dayName = weekdayName(date)
                val planned = plannedWorkoutDao.getWorkoutByDay(dayName)

                // si pas de planned ou c'est Rest Day -> rien à ignorer
                if (planned != null && !planned.name.equals("Rest Day", ignoreCase = true)) {

                    val plannedExercises = plannedWorkoutExerciseDao
                        .getPlannedWorkoutExercisesByPlannedWorkoutUUID(planned.uuid)
                        .filter { !it.pendingDeletion }

                    plannedExercises.forEach { pwe ->
                        if (!pwe.ignored) {
                            plannedWorkoutExerciseDao.markAsIgnored(pwe.uuid)
                        }
                        plannedWorkoutExerciseDao.markAsUnsynced(pwe.uuid)
                    }

                    // Optionnel (si tu as une colonne + DAO method)
                    plannedWorkoutDao.markAsUnsynced(planned.uuid)

                    // 🔥 Sync : parent d’abord
                    syncEngine.pushEntityClass(ActualWorkout::class)
                    syncEngine.pushEntityClasses(ActualWorkoutExercise::class, PlannedWorkoutExercise::class, ActualWorkoutSet::class)
                    syncEngine.pushEntityClass(PlannedWorkout::class)
                } else {
                    // pas de planned ou Rest Day : juste sync actual
                    syncEngine.pushEntityClass(ActualWorkout::class)
                }

                onCreated(uuid)
            } catch (e: Exception) {
                showSnackbar(e.message ?: context.getString(R.string.vm_calendar_failed_create_session), SnackbarType.ERROR)
                onCreated(null)
            }
        }
    }

    // Methods utils
    private suspend fun tryIgnorePlannedExerciseOnDate(
        date: LocalDate,
        exerciseUUID: String
    ) {
        val dayName = weekdayName(date)
        val plannedWorkout = plannedWorkoutDao.getWorkoutByDay(dayName) ?: return

        val plannedExercise = plannedWorkoutExerciseDao
            .getPlannedWorkoutExerciseByExerciseAndWorkout(
                exerciseUUID = exerciseUUID,
                plannedWorkoutUUID = plannedWorkout.uuid
            )

        if (plannedExercise != null) {
            plannedWorkoutExerciseDao.markAsIgnored(plannedExercise.uuid)
            plannedWorkoutExerciseDao.markAsUnsynced(plannedExercise.uuid)
        }
    }

    private suspend fun tryUnignorePlannedExerciseOnDate(
        date: LocalDate,
        exerciseUUID: String
    ) {
        val dayName = weekdayName(date)
        val plannedWorkout = plannedWorkoutDao.getWorkoutByDay(dayName) ?: return

        val plannedExercise = plannedWorkoutExerciseDao
            .getPlannedWorkoutExerciseByExerciseAndWorkout(
                exerciseUUID = exerciseUUID,
                plannedWorkoutUUID = plannedWorkout.uuid
            )

        if (plannedExercise != null && plannedExercise.ignored) {
            plannedWorkoutExerciseDao.markAsNotIgnored(plannedExercise.uuid)
            plannedWorkoutExerciseDao.markAsUnsynced(plannedExercise.uuid)
        }
    }

    fun ignoreExerciseInPlanned(date: LocalDate, exerciseUUID: String) {
        viewModelScope.launch {
            tryIgnorePlannedExerciseOnDate(date, exerciseUUID)
            syncEngine.pushEntityClasses(ActualWorkoutExercise::class, PlannedWorkoutExercise::class, ActualWorkoutSet::class)
        }
    }

    fun unignoreExerciseInPlanned(date: LocalDate, exerciseUUID: String) {
        viewModelScope.launch {
            tryUnignorePlannedExerciseOnDate(date, exerciseUUID)
            syncEngine.pushEntityClasses(ActualWorkoutExercise::class, PlannedWorkoutExercise::class, ActualWorkoutSet::class)
        }
    }



}
