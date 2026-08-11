package com.example.sportapp.feature.routines.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CalendarMonthGrid
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.StyledSearchField
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.RoutineTasksProgressBar
import com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.CalendarTaskDay
import com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.DayTasksBottomSheet
import com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.RecurrenceEditMode
import com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.RecurrenceEditModeDialog
import com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen.TaskFormDialog
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.routines.viewmodel.TasksCalendarViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun TasksCalendarScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: TasksCalendarViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val today = LocalDate.now()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val dayCells by viewModel.dayCells.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedDayTasks by viewModel.selectedDayTasks.collectAsState()
    val monthProgress by viewModel.monthProgress.collectAsState()
    val monthDone by viewModel.monthDoneCount.collectAsState()
    val monthTotal by viewModel.monthTotalCount.collectAsState()
    val monthUnsynced by viewModel.monthUnsyncedCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val defaultReminderMinutes by viewModel.defaultReminderMinutes.collectAsState()
    var searchInput by remember { mutableStateOf(TextFieldValue("")) }

    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createDialogDefaultDate by remember { mutableStateOf(today) }
    // B.4 : mode d'edition pour les recurrences (null = pas encore choisi
    //       OU pas applicable -- NONE/DAILY skip le dialog). occurrenceDate =
    //       date d'occurrence cliquee (= selectedDate au moment du click Edit).
    var pendingEditMode by remember { mutableStateOf<RecurrenceEditMode?>(null) }
    var editOccurrenceDate by remember { mutableStateOf<LocalDate?>(null) }

    val firstDayOfWeek = (currentMonth.atDay(1).dayOfWeek.value + 6) % 7  // Monday-first

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        TitledDivider(stringResource(R.string.routine_progress_overview))
        Spacer(modifier = Modifier.height(6.dp))

        RoutineTasksProgressBar(
            progress = monthProgress,
            doneCount = monthDone,
            totalCount = monthTotal,
            isSync = monthUnsynced == 0,
            onSyncClick = { viewModel.syncAll() },
            onAddClick = {
                createDialogDefaultDate = selectedDate ?: today
                showCreateDialog = true
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // C.2 (2026-05-12) : search bar tout-en-haut. Si query non-blank,
        // remplace la grille mensuelle par la liste des resultats.
        StyledSearchField(
            value = searchInput,
            onValueChange = { tfv ->
                searchInput = tfv
                viewModel.setSearchQuery(tfv.text)
            },
            placeholderText = stringResource(R.string.tasks_calendar_search_placeholder),
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (searchQuery.isBlank()) {
            TitledDivider(stringResource(R.string.tasks_calendar_title))
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
            Column {
                // Header month nav
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionIconButton(iconRes = R.drawable.ic_arrow_left_alt, onClick = {
                        viewModel.previousMonth()
                    })
                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                        fontSize = 16.sp,
                        color = appColors.textTertiary,
                        fontWeight = FontWeight.Medium,
                    )
                    ActionIconButton(iconRes = R.drawable.ic_arrow_right_alt, onClick = {
                        viewModel.nextMonth()
                    })
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Weekday labels Mon..Sun
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(
                        stringResource(R.string.weekday_short_mon),
                        stringResource(R.string.weekday_short_tue),
                        stringResource(R.string.weekday_short_wed),
                        stringResource(R.string.weekday_short_thu),
                        stringResource(R.string.weekday_short_fri),
                        stringResource(R.string.weekday_short_sat),
                        stringResource(R.string.weekday_short_sun),
                    ).forEach {
                        Text(
                            text = it,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = lightGrayBlue,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Grid
                CalendarMonthGrid(
                    month = currentMonth,
                    firstDayOffset = firstDayOfWeek,
                ) { date, cellSize ->
                    val cell = dayCells[date]
                    CalendarTaskDay(
                        dayNumber = date.dayOfMonth,
                        cellSize = cellSize,
                        isToday = date == today,
                        totalCount = cell?.totalCount ?: 0,
                        doneCount = cell?.doneCount ?: 0,
                        hasOverdue = cell?.hasOverdue == true,
                        onClick = { viewModel.selectDate(date) },
                    )
                }
            }
        }
        } else {
            // C.2 : resultats de recherche
            if (searchResults.isEmpty()) {
                Text(
                    text = stringResource(R.string.tasks_calendar_search_no_results),
                    color = appColors.textTertiary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(searchResults, key = { it.uuid }) { task ->
                        TaskSearchResultRow(
                            task = task,
                            onClick = { taskToEdit = task },
                        )
                    }
                }
            }
        }
    }

    // BottomSheet : tasks du jour selectionne
    selectedDate?.let { date ->
        DayTasksBottomSheet(
            date = date,
            rows = selectedDayTasks,
            onDismissRequest = { viewModel.selectDate(null) },
            onAddTask = {
                createDialogDefaultDate = date
                showCreateDialog = true
            },
            onToggleDone = { task, isNowChecked ->
                viewModel.toggleTaskDone(task, isNowChecked)
            },
            onEditTask = { task ->
                taskToEdit = task
                editOccurrenceDate = date
                pendingEditMode = null  // sera resolu par RecurrenceEditModeDialog si recurrente
            },
            onDeleteTask = { task ->
                taskToDelete = task  // ouvre dialog confirm au lieu de delete direct
            },
        )
    }

    // Dialog confirm delete (pattern aligne sur RoutineTasksScreen)
    taskToDelete?.let { task ->
        ConfirmationDialog(
            title = stringResource(R.string.tasks_calendar_delete_confirm_title),
            message = stringResource(R.string.tasks_calendar_delete_confirm_message, task.title),
            onConfirm = {
                viewModel.deleteTask(task.uuid)
                taskToDelete = null
            },
            onDismiss = { taskToDelete = null },
        )
    }

    // Dialog create
    if (showCreateDialog) {
        TaskFormDialog(
            existing = null,
            defaultDate = createDialogDefaultDate,
            defaultReminderMinutes = defaultReminderMinutes,
            onConfirm = { data ->
                viewModel.addTask(
                    title = data.title,
                    recurrenceKind = data.recurrenceKind,
                    dueDate = data.dueDate,
                    dueTime = data.dueTime,
                    recurrenceWeekdays = data.recurrenceWeekdays,
                    recurrenceStartDate = data.recurrenceStartDate,
                    recurrenceEndDate = data.recurrenceEndDate,
                    reminderMinutesBefore = data.reminderMinutesBefore,
                )
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    // Dialog edit -- B.4 dispatch :
    //   1. Si task recurrente (W/M/Y) ET pendingEditMode null -> RecurrenceEditModeDialog
    //   2. Sinon (NONE OU mode deja choisi) -> TaskCreateEditDialog
    taskToEdit?.let { task ->
        val isRecurring = task.recurrenceKind in setOf("WEEKLY", "MONTHLY", "YEARLY")
        val needsModeChoice = isRecurring && pendingEditMode == null

        if (needsModeChoice) {
            RecurrenceEditModeDialog(
                onConfirm = { mode -> pendingEditMode = mode },
                onDismiss = {
                    taskToEdit = null
                    pendingEditMode = null
                    editOccurrenceDate = null
                },
            )
        } else {
            TaskFormDialog(
                existing = task,
                defaultDate = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today,
                onConfirm = { data ->
                    if (pendingEditMode == RecurrenceEditMode.ONLY_THIS && editOccurrenceDate != null) {
                        viewModel.updateTaskOnlyThis(
                            originalTaskUUID = task.uuid,
                            occurrenceDate = editOccurrenceDate!!,
                            newTitle = data.title,
                            newDueTime = data.dueTime,
                            newReminderMinutesBefore = data.reminderMinutesBefore,
                        )
                    } else {
                        viewModel.updateTask(
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
                    taskToEdit = null
                    pendingEditMode = null
                    editOccurrenceDate = null
                },
                onDismiss = {
                    taskToEdit = null
                    pendingEditMode = null
                    editOccurrenceDate = null
                },
            )
        }
    }
}

/**
 * C.2 (2026-05-12) : row de resultat de recherche Agenda. Clic ouvre
 * TaskCreateEditDialog pour edit. Affiche titre + petit label kind ainsi
 * que dueDate brute (NONE) ou recurrenceStartDate (W/M/Y) en helper.
 */
@Composable
private fun TaskSearchResultRow(task: Task, onClick: () -> Unit) {
    val kindLabel = when (task.recurrenceKind) {
        "NONE" -> task.dueDate ?: ""
        "WEEKLY" -> stringResource(R.string.tasks_calendar_recur_weekly)
        "MONTHLY" -> stringResource(R.string.tasks_calendar_recur_monthly)
        "YEARLY" -> stringResource(R.string.tasks_calendar_recur_yearly)
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = task.title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (kindLabel.isNotBlank()) {
            Text(
                text = kindLabel,
                color = blueMedium,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
