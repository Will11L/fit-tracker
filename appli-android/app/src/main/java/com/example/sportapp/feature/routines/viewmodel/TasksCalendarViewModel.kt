package com.example.sportapp.feature.routines.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.data.local.TaskCheckDao
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.core.data.model.TaskCheck
import com.example.sportapp.core.domain.tasks.ScheduledTaskExpander
import com.example.sportapp.core.domain.tasks.TaskReminderScheduler
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils
import com.example.sportapp.feature.settings.AppSettingsRepository
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

/**
 * Phase 1 (2026-05-12) : VM pour TasksCalendarScreen.
 *
 * Affiche les Tasks `recurrence_kind=NONE` (one-off datees) sur une grille
 * mensuelle. Les autres recurrences (DAILY/WEEKLY/MONTHLY/YEARLY) viendront
 * en Phase 2.
 *
 * Etat d'une cellule jour :
 *   - Aucune tache : gris
 *   - Tasks pending (>=1 non checked) : bleu
 *   - Toutes done : vert
 *   - >=1 overdue (date passee + non checked) : rouge
 */
data class TaskRowDayUi(
    val task: Task,
    val isChecked: Boolean,
    val checkUUID: String? = null,
)

data class DayCellUi(
    val date: LocalDate,
    val totalCount: Int,
    val doneCount: Int,
    val hasOverdue: Boolean,
)

@HiltViewModel
class TasksCalendarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val checkDao: TaskCheckDao,
    private val syncEngine: SyncEngine,
    private val reminderScheduler: TaskReminderScheduler,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    /** Rappel par défaut global (null = "Aucun") -> pré-remplit le sélecteur à la
     *  création d'une tâche. */
    val defaultReminderMinutes: StateFlow<Int?> =
        appSettingsRepository.settings
            .map { it.defaultReminderMinutesBefore }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                appSettingsRepository.settings.value.defaultReminderMinutesBefore,
            )

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    // C.2 (2026-05-12) : barre de recherche -- filtre allCalendarTasks par title.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** Toutes les Tasks visibles dans Calendar (NONE + recurrences hors DAILY).
     *  DAILY routines reste dans le Daily tab (RoutineTasksScreen). */
    private val allCalendarTasks: StateFlow<List<Task>> =
        taskDao.observeAll()
            .map { list ->
                list.filter { !it.pendingDeletion && it.isActive && it.recurrenceKind != "DAILY" }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tous les TaskChecks (filtres en Map indexed par taskUUID dans dayCells). */
    private val checksAll: StateFlow<List<TaskCheck>> =
        checkDao.observeAll()
            .map { list -> list.filter { !it.pendingDeletion } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Map LocalDate -> DayCellUi (compteurs + flag overdue) pour la grille.
     *  Pour les NONE : une occurrence = due_date. Pour les WEEKLY/MONTHLY/YEARLY :
     *  occurrences calculees a la volee via ScheduledTaskExpander. */
    val dayCells: StateFlow<Map<LocalDate, DayCellUi>> =
        combine(allCalendarTasks, checksAll, _currentMonth) { tasks, checks, ym ->
            val today = LocalDate.now()
            val checksByTaskUuid = checks.groupBy { it.taskUUID }

            // Pour chaque task, generer (date_d_occurrence, task) dans le mois courant.
            val occurrences: List<Pair<LocalDate, Task>> = tasks.flatMap { t ->
                ScheduledTaskExpander.occurrencesForMonth(t, ym).map { d -> d to t }
            }

            occurrences
                .groupBy({ it.first }, { it.second })  // Map<LocalDate, List<Task>>
                .mapValues { (date, dayTasks) ->
                    val total = dayTasks.size
                    val done = dayTasks.count { t ->
                        val tChecks = checksByTaskUuid[t.uuid].orEmpty()
                        val dateStr = date.toString()
                        tChecks.any { c -> c.occurrenceDate == dateStr && c.isChecked }
                    }
                    val hasOverdue = dayTasks.any { t ->
                        val tChecks = checksByTaskUuid[t.uuid].orEmpty()
                        val dateStr = date.toString()
                        val isDone = tChecks.any { c -> c.occurrenceDate == dateStr && c.isChecked }
                        !isDone && date.isBefore(today)
                    }
                    DayCellUi(
                        date = date,
                        totalCount = total,
                        doneCount = done,
                        hasOverdue = hasOverdue,
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Liste des tasks pour le jour selectionne (sous le BottomSheet).
     *  Inclut NONE (due_date == selectedDate) + recurrences dont une occurrence
     *  tombe sur selectedDate. */
    val selectedDayTasks: StateFlow<List<TaskRowDayUi>> =
        combine(_selectedDate, allCalendarTasks, checksAll) { date, allTasks, checks ->
            if (date == null) return@combine emptyList<TaskRowDayUi>()

            val dateStr = date.toString()
            val checksByTask = checks
                .filter { it.occurrenceDate == dateStr && !it.pendingDeletion }
                .associateBy { it.taskUUID }

            // Filtre les tasks dont une occurrence tombe sur date
            allTasks
                .filter { t ->
                    ScheduledTaskExpander.occurrencesInRange(t, date, date).isNotEmpty()
                }
                .sortedWith(compareBy({ it.dueTime ?: "99:99" }, { it.title }))
                .map { t ->
                    val check = checksByTask[t.uuid]
                    TaskRowDayUi(
                        task = t,
                        isChecked = check?.isChecked == true,
                        checkUUID = check?.uuid,
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Compteurs agreges sur le MOIS courant (progress + done + total + unsynced).
     *  Pattern aligne sur RoutineTasksScreen Daily tab. */
    val monthDoneCount: StateFlow<Int> = dayCells
        .map { cells -> cells.values.sumOf { it.doneCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val monthTotalCount: StateFlow<Int> = dayCells
        .map { cells -> cells.values.sumOf { it.totalCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val monthProgress: StateFlow<Float> = combine(monthDoneCount, monthTotalCount) { done, total ->
        if (total == 0) 0f else done.toFloat() / total.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** Nb unsynced (TaskCheck + Tasks) pour le badge sync. */
    val monthUnsyncedCount: StateFlow<Int> = combine(allCalendarTasks, checksAll) { tasks, checks ->
        val unsyncedTasks = tasks.count { !it.synced }
        val unsyncedChecks = checks.count { !it.synced }
        unsyncedTasks + unsyncedChecks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** C.2 : resultats de recherche filtres par title (case-insensitive). Trie
     *  par title alphabetique. Vide si query.isBlank() (sentinelle UI pour
     *  decider d'afficher la grille mensuelle au lieu de la liste). */
    val searchResults: StateFlow<List<Task>> =
        combine(allCalendarTasks, _searchQuery) { tasks, query ->
            val q = query.trim()
            if (q.isBlank()) emptyList()
            else tasks.filter { it.title.contains(q, ignoreCase = true) }
                .sortedBy { it.title.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ============ Navigation mois ============
    fun setMonth(ym: YearMonth) { _currentMonth.value = ym }
    fun previousMonth() { _currentMonth.value = _currentMonth.value.minusMonths(1) }
    fun nextMonth() { _currentMonth.value = _currentMonth.value.plusMonths(1) }

    // ============ Selection jour ============
    fun selectDate(date: LocalDate?) { _selectedDate.value = date }

    /** Force push Task + TaskCheck (utilise par le bouton sync du header). */
    fun syncAll() {
        viewModelScope.launch {
            try {
                syncEngine.pushEntityClass(Task::class)
                syncEngine.pushEntityClass(TaskCheck::class)
            } catch (e: Exception) {
                Log.e("TasksCalendarVM", "syncAll failed: ${e.message}", e)
                showSnackbar(
                    message = context.getString(R.string.tasks_calendar_update_failed),
                    type = SnackbarType.ERROR,
                )
            }
        }
    }

    // ============ CRUD Task ============

    /**
     * Cree une Task. Si [recurrenceKind] = "NONE", il faut fournir [dueDate]
     * uniquement. Sinon, fournir [recurrenceStartDate] (+ weekdays si WEEKLY)
     * et eventuellement [recurrenceEndDate].
     */
    fun addTask(
        title: String,
        recurrenceKind: String,
        dueDate: LocalDate?,
        dueTime: String?,
        recurrenceWeekdays: List<Int>?,
        recurrenceStartDate: LocalDate?,
        recurrenceEndDate: LocalDate?,
        reminderMinutesBefore: Int? = null,
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            val userId = CurrentUserManager.userId
            if (userId == null) {
                showSnackbar(context.getString(R.string.vm_routine_user_null), SnackbarType.ERROR)
                return@launch
            }

            try {
                val task = Task(
                    uuid = UUID.randomUUID().toString(),
                    userId = userId,
                    title = trimmed,
                    notes = null,
                    isActive = true,
                    order = 0,
                    recurrenceKind = recurrenceKind,
                    dueDate = if (recurrenceKind == "NONE") dueDate?.toString() else null,
                    dueTime = dueTime?.takeIf { it.isNotBlank() },
                    recurrenceWeekdays = if (recurrenceKind == "WEEKLY") recurrenceWeekdays else null,
                    recurrenceStartDate = if (recurrenceKind != "NONE") recurrenceStartDate?.toString() else null,
                    recurrenceEndDate = if (recurrenceKind != "NONE") recurrenceEndDate?.toString() else null,
                    reminderMinutesBefore = reminderMinutesBefore,
                    synced = false,
                    pendingDeletion = false,
                )
                taskDao.insert(task)
                reminderScheduler.schedule(task)
                syncEngine.pushEntityClass(Task::class)

                showSnackbar(
                    message = context.getString(R.string.tasks_calendar_task_added),
                    type = SnackbarType.INFO,
                    duration = SnackbarDuration.Short,
                )
            } catch (e: Exception) {
                Log.e("TasksCalendarVM", "addTask failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.tasks_calendar_add_failed), SnackbarType.ERROR)
            }
        }
    }

    /**
     * Update full d'une Task (option C "All occurrences" — toutes les fields
     * sont remplacees). Pas de mode "this only / future" pour MVP Phase 2.
     */
    fun updateTask(
        taskUUID: String,
        newTitle: String,
        newRecurrenceKind: String,
        newDueDate: LocalDate?,
        newDueTime: String?,
        newRecurrenceWeekdays: List<Int>?,
        newRecurrenceStartDate: LocalDate?,
        newRecurrenceEndDate: LocalDate?,
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
                Log.e("TasksCalendarVM", "updateTask failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.tasks_calendar_update_failed), SnackbarType.ERROR)
            }
        }
    }

    /**
     * B.4 (2026-05-12) : "Only this occurrence" mode. L'utilisateur edite UNE
     * occurrence d'une task recurrente (W/M/Y) sur la date `occurrenceDate`.
     * - Original task : la date est ajoutee a excludedDates (l'occurrence
     *   disparait de la serie).
     * - Nouvelle Task NONE : creee avec recurrenceKind="NONE", dueDate=occurrenceDate,
     *   les nouveaux champs edites par l'user (title, dueTime, reminder).
     *
     * Note : les champs recurrence_* du formData sont ignores ici (on force
     * NONE). Si l'user veut changer la recurrence elle-meme, il utilise mode ALL.
     */
    fun updateTaskOnlyThis(
        originalTaskUUID: String,
        occurrenceDate: LocalDate,
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
                val updatedOriginal = original.copy(
                    excludedDates = newExcluded,
                    synced = false,
                )
                taskDao.update(updatedOriginal)

                // Spawn une nouvelle Task NONE sur la date excluse, avec les
                // champs edites. La recurrence est forcee a NONE (one-off).
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
                Log.e("TasksCalendarVM", "updateTaskOnlyThis failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.tasks_calendar_update_failed), SnackbarType.ERROR)
            }
        }
    }

    fun deleteTask(taskUUID: String) {
        viewModelScope.launch {
            try {
                taskDao.markAsPendingDeletion(taskUUID)
                taskDao.markAsUnsynced(taskUUID)
                reminderScheduler.cancel(taskUUID)
                syncEngine.pushEntityClass(Task::class)
            } catch (e: Exception) {
                Log.e("TasksCalendarVM", "deleteTask failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.tasks_calendar_delete_failed), SnackbarType.ERROR)
            }
        }
    }

    fun toggleTaskDone(task: Task, isNowChecked: Boolean) {
        viewModelScope.launch {
            val userId = CurrentUserManager.userId ?: return@launch

            // Date d'occurrence = selectedDate (jour ouvert dans le BottomSheet).
            // Pour NONE c'est task.dueDate ; pour recurring c'est la date d'occurrence.
            // selectedDate.value est non-null car le BottomSheet est ouvert.
            val occDate = _selectedDate.value?.toString()
                ?: task.dueDate
                ?: return@launch

            try {
                val checkedAt = if (isNowChecked) CustomDateUtils.getNowISO8601() else null
                val existing = checkDao.getByTaskAndDate(task.uuid, occDate)

                if (existing != null) {
                    checkDao.update(existing.copy(isChecked = isNowChecked, checkedAt = checkedAt))
                } else {
                    checkDao.insert(
                        TaskCheck(
                            uuid = UUID.randomUUID().toString(),
                            userId = userId,
                            taskUUID = task.uuid,
                            occurrenceDate = occDate,
                            isChecked = isNowChecked,
                            checkedAt = checkedAt,
                        )
                    )
                }

                // Push Task d'abord (au cas ou parent pas encore sync) puis TaskCheck.
                syncEngine.pushEntityClass(Task::class)
                syncEngine.pushEntityClass(TaskCheck::class)
            } catch (e: Exception) {
                Log.e("TasksCalendarVM", "toggleTaskDone failed: ${e.message}", e)
                showSnackbar(context.getString(R.string.tasks_calendar_toggle_failed), SnackbarType.ERROR)
            }
        }
    }
}
