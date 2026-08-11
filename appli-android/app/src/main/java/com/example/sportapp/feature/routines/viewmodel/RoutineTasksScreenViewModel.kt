package com.example.sportapp.feature.routines.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.data.local.RoutinePeriodDao
import com.example.sportapp.core.data.local.TaskCheckDao
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.utils.CustomDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.core.data.model.TaskCheck
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.domain.tasks.ScheduledTaskExpander
import com.example.sportapp.core.domain.tasks.TaskReminderScheduler
import com.example.sportapp.core.domain.routines.RoutinePeriodStartScheduler
import com.example.sportapp.feature.settings.AppSettingsRepository
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 4 D4 (2026-05-12) : VM unifie pour RoutineTasksScreen.
 *
 * Affiche, pour la `selectedDate`, TOUTES les Tasks visibles :
 * - DAILY : rendues dans leur period (via task.periodUUID)
 * - WEEKLY/MONTHLY/YEARLY/NONE avec occurrence ce jour :
 *     * Si dueTime != null ET match une period (startTime <= dueTime <= endTime)
 *       => rendues dans la period matchee
 *     * Sinon => section "Other tasks today" (cachee si vide)
 *
 * L'UI utilise task.recurrenceKind pour differencier visuellement les 3 types
 * (DAILY / recurrente periodique / ponctuelle one-off).
 */
data class TaskRowUi(
    val task: Task,
    val isChecked: Boolean,
    val checkedAt: String? = null,
    val isCheckSynced: Boolean = false
)

data class RoutinePeriodSectionUi(
    val period: RoutinePeriod,
    val rows: List<TaskRowUi>
)

@HiltViewModel
class RoutineTasksScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val periodDao: RoutinePeriodDao,
    private val taskDao: TaskDao,
    private val checkDao: TaskCheckDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager,
    private val reminderScheduler: TaskReminderScheduler,
    private val periodStartScheduler: RoutinePeriodStartScheduler,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    /** Rappel par défaut global (null = "Aucun") -> pré-remplit le sélecteur
     *  "avant le début" à la création d'une période. */
    val defaultReminderMinutes: StateFlow<Int?> =
        appSettingsRepository.settings
            .map { it.defaultReminderMinutesBefore }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                appSettingsRepository.settings.value.defaultReminderMinutesBefore,
            )

    init {
        // 2026-06-08 : bootstrap des notifs "debut de periode" (ROUTINE_PERIOD_START).
        // (Re)planifie toutes les periods actives a l'ouverture de l'ecran, ce qui
        // couvre les periods recues via sync (jamais passees par addPeriod local).
        // Idempotent (WorkManager unique work + REPLACE).
        viewModelScope.launch {
            try {
                periodStartScheduler.scheduleAll(periodDao.getAllActive())
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "bootstrap period-start schedule failed: ${e.message}", e)
            }
        }
    }

    private val _selectedDate = MutableStateFlow(CustomDateUtils.getTodayIsoDay())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // 1) periodes
    val periods: StateFlow<List<RoutinePeriod>> =
        periodDao.observeAll()
            .map { list -> list.filter { !it.pendingDeletion } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 2) checks du jour (sur task_checks)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val checksByDate: StateFlow<List<TaskCheck>> =
        selectedDate.flatMapLatest { date ->
            checkDao.observeByDate(date)
        }.map { list -> list.filter { !it.pendingDeletion } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 3) toutes les taches actives non supprimees (toutes recurrences)
    private val allTasks: StateFlow<List<Task>> =
        taskDao.observeAll()
            .map { list ->
                list.filter { !it.pendingDeletion && it.isActive }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 3b) tasks visibles a la `selectedDate` : DAILY toujours, non-DAILY si occurrence
    @OptIn(ExperimentalCoroutinesApi::class)
    private val tasksVisibleToday: StateFlow<List<Task>> =
        combine(allTasks, selectedDate) { tasks, dateISO ->
            val date = runCatching { LocalDate.parse(dateISO) }.getOrNull()
                ?: return@combine emptyList()
            tasks.filter { task ->
                if (task.recurrenceKind == "DAILY") true
                else ScheduledTaskExpander.occurrencesInRange(task, date, date).isNotEmpty()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 4) Partition : sections par period + "Other tasks today" (sans period assignable)
    private data class TasksPartition(
        val sections: List<RoutinePeriodSectionUi>,
        val others: List<TaskRowUi>,
    )

    private val tasksPartition: StateFlow<TasksPartition> =
        combine(periods, tasksVisibleToday, checksByDate) { pList, tList, cList ->
            val checksMap = cList.associateBy { it.taskUUID }
            val sortedPeriods = pList.sortedBy { it.startTime }

            // Pre-assigne chaque task non-DAILY a une period (ou null si pas de match)
            val nonDailyTasks = tList.filter { it.recurrenceKind != "DAILY" }
            val periodAssignment: Map<String, RoutinePeriod?> = nonDailyTasks.associate { task ->
                task.uuid to periodForDueTime(sortedPeriods, task.dueTime)
            }

            // Build sections : non-DAILY EN PREMIER (planifiees a heure precise =
            // visuellement prioritaires) trie par dueTime, puis DAILY trie par order.
            val sections = sortedPeriods.map { period ->
                val dailyInPeriod = tList
                    .filter { it.recurrenceKind == "DAILY" && it.periodUUID == period.uuid }
                    .sortedBy { it.order }

                val nonDailyInPeriod = nonDailyTasks
                    .filter { periodAssignment[it.uuid]?.uuid == period.uuid }
                    .sortedBy { it.dueTime ?: "" }

                val rows = (nonDailyInPeriod + dailyInPeriod).map { task ->
                    val check = checksMap[task.uuid]
                    val rowSynced = if (check != null) task.synced && check.synced else task.synced
                    TaskRowUi(
                        task = task,
                        isChecked = check?.isChecked == true,
                        checkedAt = check?.checkedAt,
                        isCheckSynced = rowSynced,
                    )
                }
                RoutinePeriodSectionUi(period = period, rows = rows)
            }

            // "Other tasks today" : non-DAILY sans period assignable, trie par titre
            val others = nonDailyTasks
                .filter { periodAssignment[it.uuid] == null }
                .sortedBy { it.title.lowercase() }
                .map { task ->
                    val check = checksMap[task.uuid]
                    val rowSynced = if (check != null) task.synced && check.synced else task.synced
                    TaskRowUi(
                        task = task,
                        isChecked = check?.isChecked == true,
                        checkedAt = check?.checkedAt,
                        isCheckSynced = rowSynced,
                    )
                }

            TasksPartition(sections, others)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksPartition(emptyList(), emptyList()))

    val sectionsUi: StateFlow<List<RoutinePeriodSectionUi>> =
        tasksPartition.map { it.sections }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val otherTasksToday: StateFlow<List<TaskRowUi>> =
        tasksPartition.map { it.others }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 5) progress global (sections + others)
    val progress: StateFlow<Float> =
        tasksPartition.map { p ->
            val rows = p.sections.flatMap { it.rows } + p.others
            val total = rows.size
            if (total == 0) 0f else rows.count { it.isChecked }.toFloat() / total.toFloat()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val totalTasksCount: StateFlow<Int> =
        tasksPartition.map { p -> p.sections.sumOf { it.rows.size } + p.others.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val checkedTasksCount: StateFlow<Int> =
        tasksPartition.map { p ->
            p.sections.sumOf { s -> s.rows.count { it.isChecked } } + p.others.count { it.isChecked }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val unsyncedCount: StateFlow<Int> =
        combine(tasksVisibleToday, checksByDate) { tasks, checks ->
            val unsyncedTasks = tasks.count { !it.synced }
            val unsyncedChecks = checks.count { !it.synced }
            unsyncedTasks + unsyncedChecks
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Retourne la period qui contient `dueTime` ("HH:MM"), en comparaison string
     * lexicographique (formats "HH:MM" zero-padded). Periods deja triees par startTime,
     * premier match gagne (overlap accepte). Null si dueTime null ou hors de toute period.
     */
    private fun periodForDueTime(sortedPeriods: List<RoutinePeriod>, dueTime: String?): RoutinePeriod? {
        if (dueTime.isNullOrBlank()) return null
        return sortedPeriods.firstOrNull { dueTime >= it.startTime && dueTime <= it.endTime }
    }

    // ========== Methods ==========
    fun setDate(dateISO: String) {
        _selectedDate.value = dateISO
    }

    fun toggleTask(taskUUID: String, isNowChecked: Boolean) {
        viewModelScope.launch {
            val userID = CurrentUserManager.userId
            if (userID == null) {
                showSnackbar(message = context.getString(R.string.vm_routine_user_null), type = SnackbarType.ERROR)
                return@launch
            }

            try {
                val date = selectedDate.value
                val checkedAt = if (isNowChecked) CustomDateUtils.getNowISO8601() else null
                val existing = checkDao.getByTaskAndDate(taskUUID, date)

                // DEBUG temporaire (2026-05-12) : tracer le path pour comprendre
                // pourquoi le compteur TaskCheck augmente a chaque toggle.
                Log.d(
                    "RoutineTasksScreenVM",
                    "toggleTask path: taskUUID=$taskUUID date=$date " +
                        "existing=${existing?.uuid ?: "<null>"} " +
                        "existing.userId=${existing?.userId} currentUserID=$userID " +
                        "existing.pendingDeletion=${existing?.pendingDeletion} " +
                        "-> action=${if (existing != null) "UPDATE" else "INSERT"}"
                )

                if (existing != null) {
                    checkDao.update(existing.copy(isChecked = isNowChecked, checkedAt = checkedAt))
                } else {
                    checkDao.insert(
                        TaskCheck(
                            uuid = UUID.randomUUID().toString(),
                            userId = userID,
                            taskUUID = taskUUID,
                            occurrenceDate = date,
                            isChecked = isNowChecked,
                            checkedAt = checkedAt,
                        )
                    )
                }

                // Push : Task d'abord (au cas ou parent pas encore sync) puis TaskCheck.
                // pushEntityClass retourne Boolean ; on log + snackbar si failure.
                val taskOk = syncEngine.pushEntityClass(Task::class)
                val checkOk = syncEngine.pushEntityClass(TaskCheck::class)
                if (!taskOk || !checkOk) {
                    Log.w("RoutineTasksScreenVM", "toggleTask sync partial fail (task=$taskOk check=$checkOk) -- local state preserved, will retry")
                    showSnackbar(
                        message = context.getString(R.string.vm_routine_sync_failed),
                        type = SnackbarType.WARNING,
                    )
                }
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenVM", "toggleTask failed: ${e.message}", e)
                showSnackbar(
                    message = context.getString(R.string.vm_routine_sync_failed),
                    type = SnackbarType.ERROR,
                )
            }
        }
    }

    fun goToPreviousDay() {
        _selectedDate.value = CustomDateUtils.shiftIsoDay(_selectedDate.value, -1)
    }

    fun goToNextDay() {
        val next = CustomDateUtils.shiftIsoDay(_selectedDate.value, 1)
        val today = CustomDateUtils.getTodayIsoDay()
        _selectedDate.value = if (next > today) today else next
    }

    fun syncAll() {
        viewModelScope.launch {
            try {
                syncEngine.pushEntityClass(RoutinePeriod::class)
                syncEngine.pushEntityClass(Task::class)
                syncEngine.pushEntityClass(TaskCheck::class)
            } catch (e: Exception) {
                showSnackbar(
                    message = context.getString(R.string.vm_routine_sync_failed),
                    type = SnackbarType.ERROR
                )
                Log.e("RoutineTasksScreenViewModel", "syncAll failed: ${e.message}")
            }
        }
    }

    private suspend fun reorderPeriodsByStartTime() {
        val list = periodDao.getActiveOrderByStartTime()
        list.forEachIndexed { index, p ->
            val desired = index + 1
            if (p.order != desired) {
                periodDao.update(p.copy(order = desired))
            }
        }
    }

    private suspend fun reorderTasksInPeriod(periodUUID: String?) {
        if (periodUUID == null) return
        val tasks = taskDao.getActiveDailyByPeriod(periodUUID)
        tasks.forEachIndexed { index, task ->
            val desiredOrder = index + 1
            if (task.order != desiredOrder) {
                taskDao.update(task.copy(order = desiredOrder))
            }
        }
    }

    // ============================
    // ✅ DRAG & DROP : move / insert
    // ============================

    /**
     * Drop entre header et 1ère task => place la task en PREMIÈRE position de la période.
     */
    fun moveTaskToPeriodTop(taskUUID: String, targetPeriodUUID: String) {
        viewModelScope.launch {
            try {
                val dragged = taskDao.getByUUID(taskUUID) ?: return@launch
                val oldPeriodUUID = dragged.periodUUID

                val targetTasks = taskDao.getActiveDailyByPeriod(targetPeriodUUID)
                    .sortedBy { it.order }
                    .toMutableList()

                targetTasks.removeAll { it.uuid == taskUUID }

                targetTasks.add(0, dragged.copy(periodUUID = targetPeriodUUID, synced = false))

                targetTasks.forEachIndexed { index, t ->
                    val desiredOrder = index + 1
                    val current = taskDao.getByUUID(t.uuid) ?: return@forEachIndexed

                    val desiredPeriod = if (t.uuid == taskUUID) targetPeriodUUID else current.periodUUID
                    val changed = current.order != desiredOrder || current.periodUUID != desiredPeriod
                    if (changed) {
                        taskDao.update(
                            current.copy(
                                periodUUID = desiredPeriod,
                                order = desiredOrder,
                                synced = false
                            )
                        )
                    }
                }

                reorderTasksInPeriod(oldPeriodUUID)
                if (targetPeriodUUID != oldPeriodUUID) reorderTasksInPeriod(targetPeriodUUID)

                syncEngine.pushEntityClass(Task::class)
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "moveTaskToPeriodTop failed", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_move), SnackbarType.ERROR)
            }
        }
    }

    /**
     * Drop sur un header de période => place la task à la FIN de cette période.
     */
    fun moveTaskToPeriodEnd(taskUUID: String, targetPeriodUUID: String) {
        viewModelScope.launch {
            try {
                val task = taskDao.getByUUID(taskUUID) ?: return@launch
                val oldPeriodUUID = task.periodUUID

                val maxOrder = taskDao.getMaxOrderForPeriod(targetPeriodUUID) ?: 0
                val newOrder = maxOrder + 1

                taskDao.update(
                    task.copy(
                        periodUUID = targetPeriodUUID,
                        order = newOrder,
                        synced = false
                    )
                )

                reorderTasksInPeriod(oldPeriodUUID)
                if (targetPeriodUUID != oldPeriodUUID) {
                    reorderTasksInPeriod(targetPeriodUUID)
                }

                syncEngine.pushEntityClass(Task::class)
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "moveTaskToPeriodEnd failed", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_move), SnackbarType.ERROR)
            }
        }
    }


    /**
     * Drop sur une task (anchor) => insère la task DRAGGÉE AVANT l'anchor
     * dans targetPeriodUUID.
     */
    fun moveTaskBeforeAnchor(
        draggedTaskUUID: String,
        anchorTaskUUID: String,
        targetPeriodUUID: String
    ) {
        if (draggedTaskUUID == anchorTaskUUID) return

        viewModelScope.launch {
            try {
                val dragged = taskDao.getByUUID(draggedTaskUUID) ?: return@launch
                val anchor = taskDao.getByUUID(anchorTaskUUID) ?: return@launch

                val oldPeriodUUID = dragged.periodUUID

                val targetTasks = taskDao.getActiveDailyByPeriod(targetPeriodUUID)
                    .sortedBy { it.order }
                    .toMutableList()

                targetTasks.removeAll { it.uuid == draggedTaskUUID }

                val anchorIndex = targetTasks.indexOfFirst { it.uuid == anchorTaskUUID }
                if (anchorIndex == -1) {
                    moveTaskToPeriodEnd(draggedTaskUUID, targetPeriodUUID)
                    return@launch
                }

                targetTasks.add(anchorIndex, dragged.copy(periodUUID = targetPeriodUUID, synced = false))

                targetTasks.forEachIndexed { index, t ->
                    val desiredOrder = index + 1
                    val current = taskDao.getByUUID(t.uuid) ?: return@forEachIndexed

                    val desiredPeriod = if (t.uuid == draggedTaskUUID) targetPeriodUUID else current.periodUUID
                    val changed = current.order != desiredOrder || current.periodUUID != desiredPeriod

                    if (changed) {
                        taskDao.update(
                            current.copy(
                                periodUUID = desiredPeriod,
                                order = desiredOrder,
                                synced = false
                            )
                        )
                    }
                }

                reorderTasksInPeriod(oldPeriodUUID)
                if (targetPeriodUUID != oldPeriodUUID) {
                    reorderTasksInPeriod(targetPeriodUUID)
                }

                syncEngine.pushEntityClass(Task::class)
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "moveTaskBeforeAnchor failed", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_reorder), SnackbarType.ERROR)
            }
        }
    }

    /**
     * Drop sur un gap "après task X" => insère la task juste APRÈS anchor.
     */
    fun moveTaskAfterAnchor(
        draggedTaskUUID: String,
        anchorTaskUUID: String,
        targetPeriodUUID: String
    ) {
        if (draggedTaskUUID == anchorTaskUUID) return

        viewModelScope.launch {
            try {
                val dragged = taskDao.getByUUID(draggedTaskUUID) ?: return@launch
                val oldPeriodUUID = dragged.periodUUID

                val targetTasks = taskDao.getActiveDailyByPeriod(targetPeriodUUID)
                    .sortedBy { it.order }
                    .toMutableList()

                targetTasks.removeAll { it.uuid == draggedTaskUUID }

                val anchorIndex = targetTasks.indexOfFirst { it.uuid == anchorTaskUUID }
                if (anchorIndex == -1) {
                    moveTaskToPeriodEnd(draggedTaskUUID, targetPeriodUUID)
                    return@launch
                }

                targetTasks.add(anchorIndex + 1, dragged.copy(periodUUID = targetPeriodUUID, synced = false))

                targetTasks.forEachIndexed { index, t ->
                    val desiredOrder = index + 1
                    val current = taskDao.getByUUID(t.uuid) ?: return@forEachIndexed

                    val desiredPeriod = if (t.uuid == draggedTaskUUID) targetPeriodUUID else current.periodUUID
                    val changed = current.order != desiredOrder || current.periodUUID != desiredPeriod
                    if (changed) {
                        taskDao.update(
                            current.copy(
                                periodUUID = desiredPeriod,
                                order = desiredOrder,
                                synced = false
                            )
                        )
                    }
                }

                reorderTasksInPeriod(oldPeriodUUID)
                if (targetPeriodUUID != oldPeriodUUID) reorderTasksInPeriod(targetPeriodUUID)

                syncEngine.pushEntityClass(Task::class)
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "moveTaskAfterAnchor failed", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_reorder), SnackbarType.ERROR)
            }
        }
    }


    // ====== add/update/delete ======

    fun addPeriod(
        name: String,
        startTime: String,
        endTime: String,
        reminderBeforeStart: Int?,
        reminderBeforeEnd: Int?,
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        fun toMinutes(hhmm: String): Int? {
            val parts = hhmm.split(":")
            if (parts.size != 2) return null
            val hh = parts[0].toIntOrNull() ?: return null
            val mm = parts[1].toIntOrNull() ?: return null
            if (hh !in 0..23) return null
            if (mm !in 0..59) return null
            return hh * 60 + mm
        }

        val startMin = toMinutes(startTime)
        val endMin = toMinutes(endTime)

        if (startMin == null || endMin == null || startMin >= endMin) {
            showSnackbar(context.getString(R.string.vm_routine_invalid_time_range), SnackbarType.ERROR)
            return
        }

        viewModelScope.launch {
            try {
                val userId = periods.value.firstOrNull()?.userId
                    ?: periodDao.getAllOnce().firstOrNull()?.userId

                if (userId == null) {
                    showSnackbar(context.getString(R.string.vm_routine_no_user_found), SnackbarType.ERROR)
                    return@launch
                }

                val maxOrder = periodDao.getMaxOrderIndex() ?: 0
                val nextOrder = maxOrder + 1

                val newPeriod = RoutinePeriod(
                    uuid = UUID.randomUUID().toString(),
                    userId = userId,
                    name = trimmed,
                    startTime = startTime,
                    endTime = endTime,
                    order = nextOrder,
                    reminderBeforeStartMinutes = reminderBeforeStart,
                    reminderBeforeEndMinutes = reminderBeforeEnd,
                    synced = false,
                    pendingDeletion = false
                )

                periodDao.insert(newPeriod)

                reorderPeriodsByStartTime()
                periodStartScheduler.schedule(newPeriod)
                syncEngine.pushEntityClass(RoutinePeriod::class)

                showSnackbar(
                    message = context.getString(R.string.vm_routine_period_added),
                    type = SnackbarType.INFO,
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "addPeriod failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_add_period), SnackbarType.ERROR)
            }
        }
    }

    fun updatePeriod(
        periodUUID: String,
        newName: String,
        newStartTime: String,
        newEndTime: String,
        reminderBeforeStart: Int?,
        reminderBeforeEnd: Int?,
    ) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            showSnackbar(context.getString(R.string.vm_routine_period_name_empty), SnackbarType.ERROR)
            return
        }

        fun toMinutes(hhmm: String): Int? {
            val parts = hhmm.split(":")
            if (parts.size != 2) return null
            val hh = parts[0].toIntOrNull() ?: return null
            val mm = parts[1].toIntOrNull() ?: return null
            if (hh !in 0..23 || mm !in 0..59) return null
            return hh * 60 + mm
        }

        val startMin = toMinutes(newStartTime)
        val endMin = toMinutes(newEndTime)

        if (startMin == null || endMin == null || startMin >= endMin) {
            showSnackbar(context.getString(R.string.vm_routine_invalid_time_range), SnackbarType.ERROR)
            return
        }

        viewModelScope.launch {
            try {
                val period = periodDao.getByUUID(periodUUID)
                if (period == null) {
                    showSnackbar(context.getString(R.string.vm_routine_period_not_found), SnackbarType.ERROR)
                    return@launch
                }

                val updated = period.copy(
                    name = trimmedName,
                    startTime = newStartTime,
                    endTime = newEndTime,
                    reminderBeforeStartMinutes = reminderBeforeStart,
                    reminderBeforeEndMinutes = reminderBeforeEnd,
                )

                periodDao.update(updated)
                reorderPeriodsByStartTime()
                periodStartScheduler.schedule(updated)
                syncEngine.pushEntityClass(RoutinePeriod::class)

                showSnackbar(
                    message = context.getString(R.string.vm_routine_period_updated),
                    type = SnackbarType.INFO,
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "updatePeriod failed", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_update_period), SnackbarType.ERROR)
            }
        }
    }

    fun addTask(periodUUID: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            try {
                val period = periods.value.firstOrNull { it.uuid == periodUUID }
                    ?: periodDao.getByUUID(periodUUID)

                if (period == null) {
                    showSnackbar(context.getString(R.string.vm_routine_period_not_found), SnackbarType.ERROR)
                    return@launch
                }

                val maxOrder = taskDao.getMaxOrderForPeriod(periodUUID) ?: -1
                val nextOrder = maxOrder + 1

                val today = CustomDateUtils.getTodayIsoDay()
                val newTask = Task(
                    uuid = UUID.randomUUID().toString(),
                    userId = period.userId,
                    title = trimmed,
                    notes = null,
                    isActive = true,
                    order = nextOrder,
                    recurrenceKind = "DAILY",
                    periodUUID = periodUUID,
                    recurrenceStartDate = today,
                    synced = false,
                    pendingDeletion = false,
                )

                taskDao.insert(newTask)

                // 2026-05-12 : retire l'insert TaskCheck initial isChecked=false
                // (row fantome inutile, consomme une slot avec un uuid different
                // de celui que toggleTask creera plus tard a cause du timing).
                // Le TaskCheck est cree au premier toggle reel via toggleTask.

                syncEngine.pushEntityClass(Task::class)

                showSnackbar(
                    message = context.getString(R.string.vm_routine_task_added),
                    type = SnackbarType.INFO,
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                showSnackbar(context.getString(R.string.vm_routine_failed_add_task), SnackbarType.ERROR)
                Log.e("RoutineTasksScreenViewModel", "addTask failed: ${e.message}")
            }
        }
    }

    /**
     * Edit d'une Task DAILY depuis Quotidien : title + periodUUID uniquement
     * (cf. revert 2026-05-13 par user : "c'est une tache routine donc pas plus
     * d'infos"). Si periode change : reorder ancien + nouveau.
     */
    fun updateTask(
        taskUUID: String,
        newPeriodUUID: String,
        newTitle: String
    ) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            try {
                val task = taskDao.getByUUID(taskUUID) ?: return@launch

                val oldPeriodUUID = task.periodUUID
                val periodChanged = oldPeriodUUID != newPeriodUUID

                if (!periodChanged) {
                    if (task.title != trimmed) {
                        taskDao.update(task.copy(title = trimmed))
                    }
                } else {
                    val maxOrderNew = taskDao.getMaxOrderForPeriod(newPeriodUUID) ?: 0
                    val nextOrderNew = maxOrderNew + 1

                    taskDao.update(
                        task.copy(
                            title = trimmed,
                            periodUUID = newPeriodUUID,
                            order = nextOrderNew
                        )
                    )

                    reorderTasksInPeriod(oldPeriodUUID)
                    reorderTasksInPeriod(newPeriodUUID)
                }

                syncEngine.pushEntityClass(Task::class)

                showSnackbar(
                    message = context.getString(R.string.vm_routine_task_updated),
                    type = SnackbarType.INFO,
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "updateTask failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_edit_task), SnackbarType.ERROR)
            }
        }
    }

    /**
     * B.4 (2026-05-13) : edit complet d'une Task non-DAILY depuis Quotidien.
     * Mode "All occurrences" -- modifie la Task entiere (champs recurrence
     * inclus). Mirror de TasksCalendarViewModel.updateTask, sans periode/order
     * (qui ne changent pas via ce dialog).
     */
    fun updateTaskFull(
        taskUUID: String,
        newTitle: String,
        newRecurrenceKind: String,
        newDueDate: java.time.LocalDate?,
        newDueTime: String?,
        newRecurrenceWeekdays: List<Int>?,
        newRecurrenceStartDate: java.time.LocalDate?,
        newRecurrenceEndDate: java.time.LocalDate?,
        newReminderMinutesBefore: Int? = null,
    ) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val task = taskDao.getByUUID(taskUUID) ?: return@launch
                val updated = task.copy(
                    title = trimmed,
                    recurrenceKind = newRecurrenceKind,
                    dueDate = if (newRecurrenceKind == "NONE") newDueDate?.toString() else null,
                    dueTime = newDueTime?.takeIf { it.isNotBlank() },
                    recurrenceWeekdays = if (newRecurrenceKind == "WEEKLY") newRecurrenceWeekdays else null,
                    recurrenceStartDate = if (newRecurrenceKind != "NONE") newRecurrenceStartDate?.toString() else null,
                    recurrenceEndDate = if (newRecurrenceKind != "NONE") newRecurrenceEndDate?.toString() else null,
                    reminderMinutesBefore = newReminderMinutesBefore,
                )
                taskDao.update(updated)
                reminderScheduler.schedule(updated)
                syncEngine.pushEntityClass(Task::class)
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "updateTaskFull failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_edit_task), SnackbarType.ERROR)
            }
        }
    }

    /**
     * B.4 (2026-05-13) : "Only this occurrence" depuis Quotidien. Edite UNE
     * occurrence d'une task W/M/Y sur `occurrenceDate`. Original task : la
     * date est ajoutee a excludedDates. Nouvelle Task NONE creee avec
     * dueDate=occurrenceDate + les valeurs editees. Mirror de
     * TasksCalendarViewModel.updateTaskOnlyThis.
     */
    fun updateTaskOnlyThis(
        originalTaskUUID: String,
        occurrenceDate: java.time.LocalDate,
        newTitle: String,
        newDueTime: String?,
        newReminderMinutesBefore: Int? = null,
    ) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val userId = CurrentUserManager.userId ?: return@launch
            try {
                val original = taskDao.getByUUID(originalTaskUUID) ?: return@launch
                val dateIso = occurrenceDate.toString()
                val newExcluded = (original.excludedDates + dateIso).distinct()
                taskDao.update(original.copy(excludedDates = newExcluded, synced = false))

                val forked = Task(
                    uuid = UUID.randomUUID().toString(),
                    userId = userId,
                    title = trimmed,
                    notes = original.notes,
                    isActive = true,
                    order = 0,
                    recurrenceKind = "NONE",
                    dueDate = dateIso,
                    dueTime = newDueTime?.takeIf { it.isNotBlank() },
                    periodUUID = null,
                    recurrenceWeekdays = null,
                    recurrenceStartDate = null,
                    recurrenceEndDate = null,
                    excludedDates = emptyList(),
                    reminderMinutesBefore = newReminderMinutesBefore,
                    synced = false,
                    pendingDeletion = false,
                )
                taskDao.insert(forked)
                reminderScheduler.schedule(forked)
                syncEngine.pushEntityClass(Task::class)
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "updateTaskOnlyThis failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_edit_task), SnackbarType.ERROR)
            }
        }
    }

    fun markPeriodForDeletion(periodUUID: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val period = periodDao.getByUUID(periodUUID) ?: run {
                    onDone?.invoke()
                    return@launch
                }

                val tasks = taskDao.getActiveDailyByPeriod(periodUUID)
                tasks.forEach { task ->
                    val checkUUIDs = checkDao.getActiveUUIDsByTask(task.uuid)
                    checkUUIDs.forEach { uuid ->
                        checkDao.markAsPendingDeletion(uuid)
                        checkDao.markAsUnsynced(uuid)
                    }

                    taskDao.markAsPendingDeletion(task.uuid)
                    taskDao.markAsUnsynced(task.uuid)
                }

                periodDao.markAsPendingDeletion(periodUUID)
                periodDao.markAsUnsynced(periodUUID)
                periodStartScheduler.cancel(periodUUID)

                reorderPeriodsByStartTime()

                syncEngine.pushEntityClass(RoutinePeriod::class)
                syncEngine.pushEntityClass(Task::class)
                syncEngine.pushEntityClass(TaskCheck::class)

                showSnackbar(
                    message = context.getString(R.string.vm_routine_period_deleted),
                    type = SnackbarType.INFO,
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "markPeriodForDeletion failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_delete_period), SnackbarType.ERROR)
            } finally {
                onDone?.invoke()
            }
        }
    }

    fun markTaskForDeletion(taskUUID: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val task = taskDao.getByUUID(taskUUID) ?: run {
                    onDone?.invoke()
                    return@launch
                }

                val checkUUIDs = checkDao.getActiveUUIDsByTask(taskUUID)
                checkUUIDs.forEach { uuid ->
                    checkDao.markAsPendingDeletion(uuid)
                    checkDao.markAsUnsynced(uuid)
                }

                taskDao.markAsPendingDeletion(taskUUID)
                taskDao.markAsUnsynced(taskUUID)

                reorderTasksInPeriod(task.periodUUID)

                syncEngine.pushEntityClass(Task::class)
                syncEngine.pushEntityClass(TaskCheck::class)

            } catch (e: Exception) {
                Log.e("RoutineTasksScreenViewModel", "markTaskForDeletion failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.vm_routine_failed_delete_task), SnackbarType.ERROR)
            } finally {
                onDone?.invoke()
            }
        }
    }
}
