package com.example.sportapp.feature.calendar.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.feature.demo_tour.ui.components.demoHighlight
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.feature.calendar.ui.components.calendarScreen.CalendarDay
import com.example.sportapp.feature.calendar.ui.components.calendarScreen.CalendarSummaryRow
import com.example.sportapp.feature.calendar.ui.components.calendarScreen.DayOptionsBottomSheet
import com.example.sportapp.feature.calendar.ui.components.calendarScreen.MonthViewProgressBar
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CalendarMonthGrid
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.core.utils.CustomDateUtils
import com.example.sportapp.feature.calendar.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CalendarViewScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val zone = ZoneId.systemDefault()
    val today = CustomDateUtils.getTodayLocalDate(zone)

    val currentMonth by viewModel.currentMonth.collectAsState()
    val actualByDay by viewModel.actualByDay.collectAsState()
    val monthProgress by viewModel.monthProgress.collectAsState()

    // 👇 important: on observe aussi le planned map (sinon .value peut être pas à jour)
    val plannedByWeekdayName by viewModel.plannedByWeekdayName.collectAsState()

    val firstDayOfWeek = (currentMonth.atDay(1).dayOfWeek.value + 6) % 7 // Monday-first

    // Summary
    val summary by viewModel.calendarSummary.collectAsState()

    // DayOptionsBottomSheet
    var showDayOptionsBottomSheet by remember { mutableStateOf(false) }
    var selectedDateForSheet by remember { mutableStateOf<LocalDate?>(null) }

    // Create session dialog (name)
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingCreateDate by remember { mutableStateOf<LocalDate?>(null) }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        TitledDivider(stringResource(R.string.calendar_month_completion))
        MonthViewProgressBar(progress = monthProgress)

        CalendarSummaryRow(
            perfectWeeksTotal = summary.perfectWeeksTotal,
            completedDays = summary.completedDays,
            nextWorkoutDate = summary.nextWorkoutDate
        )

        TitledDivider(stringResource(R.string.calendar_title))
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Column {
                // Header mois
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .demoHighlight("calendar.header", expand = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionIconButton(iconRes = R.drawable.ic_arrow_left_alt, onClick = {
                        viewModel.setMonth(currentMonth.minusMonths(1))
                    })

                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                        fontSize = 16.sp,
                        color = appColors.textTertiary,
                        fontWeight = FontWeight.Medium
                    )

                    ActionIconButton(iconRes = R.drawable.ic_arrow_right_alt, onClick = {
                        viewModel.setMonth(currentMonth.plusMonths(1))
                    })
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Labels jours -- ordre Mon..Sun (Monday-first politique app)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
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
                            color = lightGrayBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Grille
                CalendarMonthGrid(
                    month = currentMonth,
                    firstDayOffset = firstDayOfWeek,
                    modifier = Modifier.demoHighlight("calendar.grid"),
                ) { date, cellSize ->
                    val aw = actualByDay[date]

                    val isToday = date == today
                    val isPast = date.isBefore(today)
                    val isFuture = date.isAfter(today)

                    // ---------- PLANNED ----------
                    val weekdayKey = viewModel.weekdayName(date)
                    val planned = plannedByWeekdayName[weekdayKey]

                    val plannedIsRest =
                        planned == null || planned.name.equals("Rest Day", ignoreCase = true)

                    val plannedHasSession =
                        planned != null && !planned.name.equals("Rest Day", ignoreCase = true)

                    // ---------- ACTUAL ----------
                    val hasActual = aw != null
                    val actualDone = aw?.isDone == true
                    val actualNotDone = hasActual && !actualDone

                    val showCloudDone = hasActual && (aw?.synced == true)
                    val showCloudOff = hasActual && (aw?.synced == false)

                    // ✅ 1) Completed = actual done
                    val isCompleted = hasActual && actualDone

                    // ✅ 2) Croix rouge = actual exists but not done (passé),
                    //    et aussi si jour planned REST + actual bonus pas fini (même aujourd'hui)
                    val isSkipped = when {
                        actualNotDone && isPast -> true
                        actualNotDone && isToday && plannedIsRest -> true
                        else -> false
                    }

                    // ✅ 3) Rest day icon seulement si PAS d'actual et planned rest (ou rien)
                    val isRestDay = !hasActual && plannedIsRest

                    // ✅ 4) "-" rouge si session planned mais aucun actual et la date est passée
                    val isMissedPlanned = !hasActual && isPast && plannedHasSession

                    // ✅ 5) Arrow progress seulement :
                    //    - futur (ou aujourd'hui) avec session planned et pas d'actual
                    //    - aujourd'hui avec actual pas fini MAIS seulement si ce n'est PAS un rest day planned
                    val isInProgress = when {
                        !hasActual && (isToday || isFuture) && plannedHasSession -> true
                        actualNotDone && isToday && !plannedIsRest -> true
                        else -> false
                    }

                    CalendarDay(
                        dayNumber = date.dayOfMonth,
                        cellSize = cellSize,
                        isToday = isToday,
                        isSynced = showCloudDone,
                        showCloudOff = showCloudOff,
                        isCompleted = isCompleted,
                        isSkipped = isSkipped,
                        isRestDay = isRestDay,
                        isMissedPlanned = isMissedPlanned,
                        isInProgress = isInProgress,
                        onClick = {
                            if (aw != null) {
                                navController.navigate(Routes.session(aw.uuid))
                            } else {
                                selectedDateForSheet = date
                                showDayOptionsBottomSheet = true
                            }
                        }
                    )
                }

            }
        }

        selectedDateForSheet?.let { pickedDate ->
            if (showDayOptionsBottomSheet) {
                DayOptionsBottomSheet(
                    selectedDate = pickedDate,
                    onDismissRequest = {
                        showDayOptionsBottomSheet = false
                        selectedDateForSheet = null
                    },
                    onAddNewActualWorkoutClick = {
                        val picked = pickedDate

                        showDayOptionsBottomSheet = false
                        selectedDateForSheet = null

                        pendingCreateDate = picked
                        showCreateDialog = true
                    },
                    onAddFromPlannedClick = {
                        val date = pickedDate

                        showDayOptionsBottomSheet = false
                        selectedDateForSheet = null

                        viewModel.startActualWorkoutFromPlannedOnDate(date) { createdUuid ->
                            if (createdUuid != null) {
                                navController.navigate(Routes.session(createdUuid))
                            }
                        }
                    }
                )
            }
        }

        if (showCreateDialog && pendingCreateDate != null) {
            com.example.sportapp.feature.session.ui.components.sessionTab.CreateActualWorkoutDialog(
                onDismiss = {
                    showCreateDialog = false
                    pendingCreateDate = null
                },
                onCreate = { name ->
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty() && !trimmed.equals("Rest Day", ignoreCase = true)) {
                        val date = pendingCreateDate!!

                        showCreateDialog = false
                        pendingCreateDate = null

                        viewModel.createNewActualWorkoutForDate(
                            date = date,
                            name = trimmed
                        ) { uuid ->
                            if (uuid != null) {
                                navController.navigate(Routes.session(uuid))
                            }
                        }
                    }
                }
            )
        }


    }
}
