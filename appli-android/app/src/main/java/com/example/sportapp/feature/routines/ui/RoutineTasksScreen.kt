@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.sportapp.feature.routines.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.AddRoutineItemBottomSheet
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutinePeriodFormDialog
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutineTaskFormDialog
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.GapDropZone
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.DateNavBar
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutineEmptyPeriodDropItem
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutinePeriodHeaderDropItem
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutinePeriodOptionsBottomSheet
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutineTaskDropRow
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutineTaskRow
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutineTasksProgressBar
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.iconForNonDailyTask
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.rowColorsForTask
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.TaskOptionsBottomSheet
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.dropTargetForGap
import androidx.compose.ui.graphics.Color
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.core.utils.CustomDateUtils
import com.example.sportapp.feature.routines.viewmodel.RoutineTasksScreenViewModel

// ============================
// ✅ Drop indicator (gap visuel)
// ============================
sealed class DropIndicator {
    data object None : DropIndicator()
    data class PeriodTop(val periodUUID: String) : DropIndicator()
    data class AfterTask(val periodUUID: String, val anchorTaskUUID: String) : DropIndicator()
    data class PeriodEnd(val periodUUID: String) : DropIndicator()
}

// iconForNonDailyTask + rowColorsForTask + couleurs hardcodees deplaces
// dans com.example.sportapp.feature.routines.ui.components.routineTasksScreen.TaskTypeStyle.kt
// (partage avec Agenda DayTasksBottomSheet).

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RoutineTasksScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: RoutineTasksScreenViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val sections by viewModel.sectionsUi.collectAsState()
    val otherTasks by viewModel.otherTasksToday.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    val total by viewModel.totalTasksCount.collectAsState()
    val done by viewModel.checkedTasksCount.collectAsState()
    val unsynced by viewModel.unsyncedCount.collectAsState()
    val isSync = unsynced == 0

    var showSyncSheet by remember { mutableStateOf(false) }

    val periods by viewModel.periods.collectAsState()
    val defaultReminderMinutes by viewModel.defaultReminderMinutes.collectAsState()
    var periodForOptions by remember { mutableStateOf<RoutinePeriod?>(null) }

    var showAddMenuSheet by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskForOptions by remember { mutableStateOf<Task?>(null) }
    var showAddPeriodDialog by remember { mutableStateOf(false) }

    var showEditRoutinePeriodDialog by remember { mutableStateOf(false) }
    var routinePeriodToEdit by remember { mutableStateOf<RoutinePeriod?>(null) }
    var showEditRoutineTaskDialog by remember { mutableStateOf(false) }
    var routineTaskToEdit by remember { mutableStateOf<Task?>(null) }

    // B.4 (2026-05-13) : edit complet pour non-DAILY (W/M/Y/NONE) avec
    // dialog mode recurrence puis TaskCreateEditDialog.
    var taskToEditAdvanced by remember { mutableStateOf<Task?>(null) }
    var pendingEditMode by remember {
        mutableStateOf<com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.RecurrenceEditMode?>(null)
    }
    var editOccurrenceDate by remember { mutableStateOf<java.time.LocalDate?>(null) }

    var showDeletePeriodConfirm by remember { mutableStateOf(false) }
    var periodToDelete by remember { mutableStateOf<RoutinePeriod?>(null) }
    var showDeleteTaskConfirm by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    var dropIndicator by remember { mutableStateOf<DropIndicator>(DropIndicator.None) }

    // ✅ gap visible (quand hover)
    val gapHeight = 12.dp
    // ✅ hitbox invisible (toujours là pour capter onEntered)
    val gapHitbox = 30.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        val today = CustomDateUtils.getTodayIsoDay()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            DateNavBar(
                dateIso = selectedDate,
                isToday = selectedDate == today,
                onPrevDay = { viewModel.goToPreviousDay() },
                onNextDay = { viewModel.goToNextDay() },
                onClickDate = { viewModel.setDate(today) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TitledDivider(title = stringResource(R.string.routine_progress_overview))
            Spacer(modifier = Modifier.height(6.dp))

            RoutineTasksProgressBar(
                progress = progress,
                doneCount = done,
                totalCount = total,
                isSync = isSync,
                onSyncClick = { showSyncSheet = true },
                onAddClick = { showAddMenuSheet = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                sections.forEach { section ->

                    // ===== HEADER (drop => TOP) =====
                    item(key = "header_${section.period.uuid}") {
                        RoutinePeriodHeaderDropItem(
                            period = section.period,
                            title = stringResource(R.string.routine_period_label, section.period.name, section.period.startTime, section.period.endTime),
                            onClickHeader = { periodForOptions = section.period },
                            onDropTaskUUID = { draggedTaskUUID ->
                                viewModel.moveTaskToPeriodTop(
                                    taskUUID = draggedTaskUUID,
                                    targetPeriodUUID = section.period.uuid
                                )
                                dropIndicator = DropIndicator.None
                            },
                            onHover = { dropIndicator = DropIndicator.PeriodTop(section.period.uuid) },
                            onExit = {
                                val cur = dropIndicator
                                if (cur is DropIndicator.PeriodTop && cur.periodUUID == section.period.uuid) {
                                    dropIndicator = DropIndicator.None
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // ✅ GAP top : toujours droppable (hitbox), s'ouvre en hover
                    item(key = "gap_top_${section.period.uuid}") {
                        val isTop = dropIndicator is DropIndicator.PeriodTop
                                && (dropIndicator as DropIndicator.PeriodTop).periodUUID == section.period.uuid

                        GapDropZone(
                            key = "gap_top_drop_${section.period.uuid}",
                            isActive = isTop,
                            visualHeight = 14.dp,   // ce que tu veux voir quand hover
                            hitBoxHeight = 44.dp,   // ✅ zone large qui mord sur header + 1ère row
                            onHover = { dropIndicator = DropIndicator.PeriodTop(section.period.uuid) },
                            onExit = {
                                val cur = dropIndicator
                                if (cur is DropIndicator.PeriodTop && cur.periodUUID == section.period.uuid) {
                                    dropIndicator = DropIndicator.None
                                }
                            },
                            onDropTaskUUID = { draggedTaskUUID ->
                                viewModel.moveTaskToPeriodTop(draggedTaskUUID, section.period.uuid)
                                dropIndicator = DropIndicator.None
                            }
                        )
                    }

                    if (section.rows.isEmpty()) {
                        item(key = "empty_${section.period.uuid}") {
                            RoutineEmptyPeriodDropItem(
                                period = section.period,
                                isHovering = dropIndicator is DropIndicator.PeriodTop
                                        && (dropIndicator as DropIndicator.PeriodTop).periodUUID == section.period.uuid,
                                onHover = { dropIndicator = DropIndicator.PeriodTop(section.period.uuid) },
                                onExit = {
                                    val cur = dropIndicator
                                    if (cur is DropIndicator.PeriodTop && cur.periodUUID == section.period.uuid) {
                                        dropIndicator = DropIndicator.None
                                    }
                                },
                                onDropTaskUUID = { draggedTaskUUID ->
                                    viewModel.moveTaskToPeriodTop(
                                        taskUUID = draggedTaskUUID,
                                        targetPeriodUUID = section.period.uuid
                                    )
                                    dropIndicator = DropIndicator.None
                                }
                            )
                        }

                        item(key = "spacer_${section.period.uuid}") {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    } else {
                        items(
                            items = section.rows,
                            key = { row -> row.task.uuid }
                        ) { row ->

                            if (row.task.recurrenceKind == "DAILY") {
                                // DAILY : row draggable, pas d'accent (style historique)
                                RoutineTaskDropRow(
                                    row = row,
                                    onClickOptions = { taskForOptions = it },
                                    onToggleChecked = { taskUUID, nowChecked ->
                                        viewModel.toggleTask(taskUUID, nowChecked)
                                    }
                                )

                                // GAP after task : uniquement pour DAILY (drop reordering DAILY only)
                                val isAfterThis = dropIndicator is DropIndicator.AfterTask
                                        && (dropIndicator as DropIndicator.AfterTask).periodUUID == section.period.uuid
                                        && (dropIndicator as DropIndicator.AfterTask).anchorTaskUUID == row.task.uuid

                                GapDropZone(
                                    key = "gap_after_${row.task.uuid}",
                                    isActive = isAfterThis,
                                    visualHeight = 16.dp,
                                    hitBoxHeight = 52.dp,
                                    onHover = {
                                        dropIndicator = DropIndicator.AfterTask(
                                            periodUUID = section.period.uuid,
                                            anchorTaskUUID = row.task.uuid
                                        )
                                    },
                                    onExit = {
                                        val cur = dropIndicator
                                        if (cur is DropIndicator.AfterTask
                                            && cur.periodUUID == section.period.uuid
                                            && cur.anchorTaskUUID == row.task.uuid
                                        ) {
                                            dropIndicator = DropIndicator.None
                                        }
                                    },
                                    onDropTaskUUID = { draggedTaskUUID ->
                                        viewModel.moveTaskAfterAnchor(
                                            draggedTaskUUID = draggedTaskUUID,
                                            anchorTaskUUID = row.task.uuid,
                                            targetPeriodUUID = section.period.uuid
                                        )
                                        dropIndicator = DropIndicator.None
                                    }
                                )
                            } else {
                                // Non-DAILY (W/M/Y/NONE) integre via dueTime match :
                                // pas de drag, icone a gauche + row tintee par type.
                                val iconTint = iconForNonDailyTask(row.task)
                                val (bg, nameBg) = rowColorsForTask(row.task) ?: (appColors.bgRecessed to appColors.bgSurface)
                                RoutineTaskRow(
                                    task = row.task,
                                    isChecked = row.isChecked,
                                    isCheckSynced = row.isCheckSynced,
                                    backgroundColor = bg,
                                    nameBoxColor = nameBg,
                                    uncheckedIconColor = iconTint?.second ?: appColors.divider,
                                    onClickOptions = { taskForOptions = it },
                                    onToggleChecked = { t, nowChecked ->
                                        viewModel.toggleTask(t.uuid, nowChecked)
                                    },
                                    dragHandle = if (iconTint != null) {
                                        {
                                            androidx.compose.foundation.layout.Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    painter = painterResource(iconTint.first),
                                                    contentDescription = null,
                                                    tint = iconTint.second,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    } else null,
                                )
                            }
                        }

                        item(key = "spacer_${section.period.uuid}") {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // D4 : section "Other tasks today" -- tasks non-DAILY sans
                // period assignable (dueTime null OU hors range periods).
                // Cachee si vide pour ne pas alourdir l'UI.
                if (otherTasks.isNotEmpty()) {
                    item(key = "other_today_header") {
                        TitledDivider(title = stringResource(R.string.routine_other_tasks_today))
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    items(
                        items = otherTasks,
                        key = { row -> "other_${row.task.uuid}" }
                    ) { row ->
                        val iconTint = iconForNonDailyTask(row.task)
                        val (bg, nameBg) = rowColorsForTask(row.task) ?: (appColors.bgRecessed to appColors.bgSurface)
                        RoutineTaskRow(
                            task = row.task,
                            isChecked = row.isChecked,
                            isCheckSynced = row.isCheckSynced,
                            backgroundColor = bg,
                            nameBoxColor = nameBg,
                            uncheckedIconColor = iconTint?.second ?: appColors.divider,
                            onClickOptions = { taskForOptions = it },
                            onToggleChecked = { t, nowChecked ->
                                viewModel.toggleTask(t.uuid, nowChecked)
                            },
                            dragHandle = if (iconTint != null) {
                                {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(iconTint.first),
                                            contentDescription = null,
                                            tint = iconTint.second,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            } else null,
                        )
                    }
                    item(key = "other_today_spacer") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    // ===== Sheets / Dialogs (inchangés) =====

    fun closeSyncSheet() { showSyncSheet = false }
    if (showSyncSheet) {
        ConfirmationDialog(
            title = stringResource(R.string.routine_sync_title),
            message = stringResource(R.string.routine_sync_message),
            confirmButtonText = stringResource(R.string.common_sync),
            confirmButtonColor = appColors.primaryAction,
            onConfirm = {
                viewModel.syncAll()
                closeSyncSheet()
            },
            onDismiss = { closeSyncSheet() }
        )
    }

    fun closeAddMenuSheet() { showAddMenuSheet = false }
    if (showAddMenuSheet) {
        AddRoutineItemBottomSheet(
            onDismissRequest = { closeAddMenuSheet() },
            onAddTask = {
                closeAddMenuSheet()
                showAddTaskDialog = true
            },
            onAddPeriod = {
                closeAddMenuSheet()
                showAddPeriodDialog = true
            }
        )
    }

    fun closeAddPeriodDialog() { showAddPeriodDialog = false }
    if (showAddPeriodDialog) {
        RoutinePeriodFormDialog(
            defaultReminderMinutes = defaultReminderMinutes,
            onConfirm = { name, startTime, endTime, reminderBeforeStart, reminderBeforeEnd ->
                viewModel.addPeriod(name, startTime, endTime, reminderBeforeStart, reminderBeforeEnd)
                closeAddPeriodDialog()
            },
            onDismiss = { closeAddPeriodDialog() }
        )
    }

    fun closeAddTaskDialog() { showAddTaskDialog = false }
    if (showAddTaskDialog) {
        RoutineTaskFormDialog(
            periods = periods,
            onConfirm = { periodUUID, title ->
                viewModel.addTask(periodUUID, title)
                closeAddTaskDialog()
            },
            onDismiss = { closeAddTaskDialog() }
        )
    }

    fun closePeriodOptionsSheet() { periodForOptions = null }
    if (periodForOptions != null) {
        RoutinePeriodOptionsBottomSheet(
            period = periodForOptions!!,
            onDismissRequest = { closePeriodOptionsSheet() },
            onEditPeriod = {
                routinePeriodToEdit = periodForOptions
                showEditRoutinePeriodDialog = true
                closePeriodOptionsSheet()
            },
            onDeletePeriod = {
                periodToDelete = periodForOptions
                showDeletePeriodConfirm = true
                closePeriodOptionsSheet()
            }
        )
    }

    fun closeTaskOptionsSheet() { taskForOptions = null }
    if (taskForOptions != null) {
        TaskOptionsBottomSheet(
            task = taskForOptions!!,
            onDismissRequest = { closeTaskOptionsSheet() },
            onEditTask = {
                val t = taskForOptions!!
                closeTaskOptionsSheet()
                if (t.recurrenceKind == "DAILY") {
                    // DAILY : dialog simple title + period (existant)
                    routineTaskToEdit = t
                    showEditRoutineTaskDialog = true
                } else {
                    // B.4 : non-DAILY -> full TaskCreateEditDialog (+ mode
                    // recurrence dialog avant si W/M/Y).
                    taskToEditAdvanced = t
                    editOccurrenceDate = runCatching { java.time.LocalDate.parse(selectedDate) }.getOrNull()
                    pendingEditMode = null
                }
            },
            onDeleteTask = {
                taskToDelete = taskForOptions
                showDeleteTaskConfirm = true
                closeTaskOptionsSheet()
            }
        )
    }

    fun closeEditRoutineTaskDialog() {
        showEditRoutineTaskDialog = false
        routineTaskToEdit = null
    }
    if (showEditRoutineTaskDialog && routineTaskToEdit != null) {
        RoutineTaskFormDialog(
            task = routineTaskToEdit!!,
            periods = periods,
            onConfirm = { newPeriodUUID, newTitle ->
                viewModel.updateTask(
                    taskUUID = routineTaskToEdit!!.uuid,
                    newPeriodUUID = newPeriodUUID,
                    newTitle = newTitle
                )
                closeEditRoutineTaskDialog()
            },
            onDismiss = { closeEditRoutineTaskDialog() }
        )
    }

    fun closeEditRoutinePeriodDialog() {
        showEditRoutinePeriodDialog = false
        routinePeriodToEdit = null
    }
    if (showEditRoutinePeriodDialog && routinePeriodToEdit != null) {
        RoutinePeriodFormDialog(
            period = routinePeriodToEdit!!,
            onConfirm = { name, start, end, reminderBeforeStart, reminderBeforeEnd ->
                viewModel.updatePeriod(
                    periodUUID = routinePeriodToEdit!!.uuid,
                    newName = name,
                    newStartTime = start,
                    newEndTime = end,
                    reminderBeforeStart = reminderBeforeStart,
                    reminderBeforeEnd = reminderBeforeEnd,
                )
                closeEditRoutinePeriodDialog()
            },
            onDismiss = { closeEditRoutinePeriodDialog() }
        )
    }

    fun closeDeletePeriodDialog() {
        showDeletePeriodConfirm = false
        periodToDelete = null
    }
    if (showDeletePeriodConfirm && periodToDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.routine_delete_period_title),
            message = stringResource(R.string.routine_delete_period_message),
            confirmButtonText = stringResource(R.string.common_delete),
            dismissButtonText = stringResource(R.string.common_cancel),
            confirmButtonColor = redMedium,
            onConfirm = {
                val uuid = periodToDelete!!.uuid
                closeDeletePeriodDialog()
                viewModel.markPeriodForDeletion(periodUUID = uuid)
            },
            onDismiss = { closeDeletePeriodDialog() }
        )
    }

    fun closeDeleteTaskDialog() {
        showDeleteTaskConfirm = false
        taskToDelete = null
    }
    if (showDeleteTaskConfirm && taskToDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.routine_delete_task_title),
            message = stringResource(R.string.routine_delete_task_message),
            confirmButtonText = stringResource(R.string.common_delete),
            dismissButtonText = stringResource(R.string.common_cancel),
            confirmButtonColor = redMedium,
            onConfirm = {
                val uuid = taskToDelete!!.uuid
                closeDeleteTaskDialog()
                viewModel.markTaskForDeletion(uuid)
            },
            onDismiss = { closeDeleteTaskDialog() }
        )
    }

    // B.4 (2026-05-13) : full edit dialog for non-DAILY tasks (W/M/Y/NONE)
    // depuis Quotidien. Si W/M/Y et mode non choisi -> RecurrenceEditModeDialog
    // (Only this / All). Sinon (NONE OU mode choisi) -> TaskCreateEditDialog.
    val today = java.time.LocalDate.now()
    taskToEditAdvanced?.let { task ->
        val isRecurring = task.recurrenceKind in setOf("WEEKLY", "MONTHLY", "YEARLY")
        val needsModeChoice = isRecurring && pendingEditMode == null

        if (needsModeChoice) {
            com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.RecurrenceEditModeDialog(
                onConfirm = { mode -> pendingEditMode = mode },
                onDismiss = {
                    taskToEditAdvanced = null
                    pendingEditMode = null
                    editOccurrenceDate = null
                },
            )
        } else {
            com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.TaskFormDialog(
                existing = task,
                defaultDate = task.dueDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: today,
                onConfirm = { data ->
                    if (pendingEditMode == com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.RecurrenceEditMode.ONLY_THIS
                        && editOccurrenceDate != null) {
                        viewModel.updateTaskOnlyThis(
                            originalTaskUUID = task.uuid,
                            occurrenceDate = editOccurrenceDate!!,
                            newTitle = data.title,
                            newDueTime = data.dueTime,
                            newReminderMinutesBefore = data.reminderMinutesBefore,
                        )
                    } else {
                        viewModel.updateTaskFull(
                            taskUUID = task.uuid,
                            newTitle = data.title,
                            newRecurrenceKind = data.recurrenceKind,
                            newDueDate = data.dueDate,
                            newDueTime = data.dueTime,
                            newRecurrenceWeekdays = data.recurrenceWeekdays,
                            newRecurrenceStartDate = data.recurrenceStartDate,
                            newRecurrenceEndDate = data.recurrenceEndDate,
                            newReminderMinutesBefore = data.reminderMinutesBefore,
                        )
                    }
                    taskToEditAdvanced = null
                    pendingEditMode = null
                    editOccurrenceDate = null
                },
                onDismiss = {
                    taskToEditAdvanced = null
                    pendingEditMode = null
                    editOccurrenceDate = null
                },
            )
        }
    }
}
