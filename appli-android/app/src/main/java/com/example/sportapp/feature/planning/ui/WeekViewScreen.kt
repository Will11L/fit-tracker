package com.example.sportapp.feature.planning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.feature.demo_tour.ui.components.demoHighlight
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.LabeledProgressBar
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.DialogValidationReason
import com.example.sportapp.designsystem.theme.*
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.feature.planning.viewmodel.WeekViewViewModel
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.feature.planning.ui.components.weekViewScreen.CopyPlannedWorkoutDialog
import com.example.sportapp.feature.planning.ui.components.weekViewScreen.CreatePlannedWorkoutDialog
import com.example.sportapp.feature.planning.ui.components.weekViewScreen.PlannedDayProgressBar
import com.example.sportapp.feature.planning.ui.components.weekViewScreen.WeekSessionOptionsBottomSheet
import com.example.sportapp.feature.planning.ui.components.weekViewScreen.WeekCompletionBottomSheet
import com.example.sportapp.core.utils.CustomDateUtils.getDayOfWeekFromDate
import com.example.sportapp.core.utils.CustomDateUtils.isDateInCurrentWeek
import com.example.sportapp.core.utils.CustomDateUtils.isToday
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.localizedDayOfWeek
import com.example.sportapp.core.utils.showSnackbar

import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: WeekViewViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val userId by viewModel.userId.collectAsState()

    val actualWorkouts by viewModel.actualWorkoutsForThisWeek.collectAsStateWithLifecycle()
    val plannedWorkouts by viewModel.plannedWorkouts.collectAsStateWithLifecycle()

    val plannedWorkoutExercisesAll by viewModel.plannedWorkoutExercisesAll.collectAsStateWithLifecycle()
    val actualWorkoutExercisesForWeek by viewModel.actualWorkoutExercisesForThisWeek.collectAsStateWithLifecycle()
    val actualWorkoutSetsForWeek by viewModel.actualWorkoutSetsForThisWeek.collectAsStateWithLifecycle()

    val weekProgress by viewModel.weekProgress.collectAsStateWithLifecycle()

    // Cles EN canoniques pour matching DB (storage). Affichage via localizedDayOfWeek.
    val allDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val completePlannedList = allDays.map { day ->
        val workout = plannedWorkouts.findLast {
            it.dayOfWeek.equals(day, ignoreCase = true)
        }
        workout ?: PlannedWorkout(
            uuid = UUID.randomUUID().toString(),
            userId = 1,
            name = "Rest Day",
            dayOfWeek = day,
            synced = true
        )
    }

    // ========== State management for bottom sheets and dialogs ========== //

    val showEditBottomSheet = remember { mutableStateOf(false) }
    var selectedPlannedWorkout = remember { mutableStateOf<PlannedWorkout?>(null) }

    val showRenameDialog = remember { mutableStateOf(false) }
    val renameText = remember { mutableStateOf("") }

    val showWeekCompletionBottomSheet = remember { mutableStateOf(false) }

    val showCreateDialog = remember { mutableStateOf(false) }

    val showReplaceDialog = remember { mutableStateOf(false) }
    val pendingWorkoutToInsert = remember { mutableStateOf<PlannedWorkout?>(null) }

    // Copy Dialog
    val showCopyDialog = remember { mutableStateOf(false) }
    val copySourceWorkout = remember { mutableStateOf<PlannedWorkout?>(null) }


    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        TitledDivider(stringResource(R.string.week_completion))
        Box(modifier = Modifier.demoHighlight("program.header")) {
            WeekViewProgressBar(
                progress = weekProgress,
                onMoreOptionsClick = {
                    showEditBottomSheet.value = false
                    showWeekCompletionBottomSheet.value = true
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        TitledDivider(stringResource(R.string.week_days))
        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .demoHighlight("program.list")
                .padding(bottom = 10.dp)
        ) {
            itemsIndexed(completePlannedList, key = { _, workout -> workout.dayOfWeek }) { index, plannedWorkout ->

                val isFiller = plannedWorkout.isFiller()
                val isToday = isToday(plannedWorkout.dayOfWeek)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(
                            if (isToday)
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = blueMedium,
                                    shape = RoundedCornerShape(6.dp)
                                )
                            else Modifier
                        ),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.bgRecessed)
                ) {
                Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 🟦 Partie gauche : Jour + Session dans une colonne
                        Column(modifier = Modifier.weight(1f)) {

                            val result = calcDayProgressResult(
                                planned = plannedWorkout,
                                actualWorkoutsForWeek = actualWorkouts,
                                plannedWorkoutExercisesAll = plannedWorkoutExercisesAll,
                                actualWorkoutExercisesForWeek = actualWorkoutExercisesForWeek,
                                actualWorkoutSetsForWeek = actualWorkoutSetsForWeek,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(appColors.bgSurface, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(text = localizedDayOfWeek(plannedWorkout.dayOfWeek), color = appColors.textPrimary, fontSize = 14.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (!isFiller) {
                                    when {
                                        result.isBuilding -> {
                                            ActionIconButton(
                                                R.drawable.ic_rounded_construction,
                                                tint = darkOrange,
                                                hasBackground = false,
                                                clickable = false
                                            )
                                        }
                                        result.completed -> {
                                            ActionIconButton(
                                                R.drawable.ic_rounded_check,
                                                tint = mediumGreen,
                                                hasBackground = false,
                                                clickable = false
                                            )
                                        }
                                        result.hasActual -> {
                                            ActionIconButton(
                                                R.drawable.ic_arrow_progress,
                                                tint = blueMedium,
                                                hasBackground = false,
                                                clickable = false
                                            )
                                        }
                                        else -> {
                                            ActionIconButton(
                                                R.drawable.ic_rounded_autoplay,
                                                tint = redDark,
                                                hasBackground = false,
                                                clickable = false
                                            )
                                        }
                                    }

                                    ActionIconButton(
                                        if (plannedWorkout.synced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                                        tint = if (plannedWorkout.synced) blueMedium else yellowMedium,
                                        hasBackground = false,
                                        clickable = !plannedWorkout.synced,
                                        onClick = {
                                            viewModel.syncAllPlannedWorkouts()
                                        }
                                    )
                                } else {
                                    ActionIconWithTextButton(
                                        R.drawable.ic_rounded_bedtime,
                                        text = stringResource(R.string.week_rest_zzz),
                                        tint = blueMedium,
                                        textColor = blueMedium,
                                        hasBackground = false
                                    )
                                }

                            }

                            PlannedDayProgressBar(
                                // "Rest Day" filler -> traduit ; nom user-typed -> tel quel.
                                label = localizedDayOfWeek(plannedWorkout.name),
                                progress = result.progress,
                                showProgressBar = !isFiller && !result.isBuilding,
                                labelColor = when {
                                    isFiller -> appColors.textTertiary
                                    result.isBuilding -> darkOrange
                                    else -> appColors.primaryAction
                                },
                                // troughColor = bgSurface (boxBlue) car la Card parent a
                                // containerColor = bgRecessed (thirdBlue) -> sinon la trough
                                // est invisible.
                                troughColor = appColors.bgSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp, top = 6.dp, end = 18.dp, bottom = 0.dp)
                            )

                        }

                        // 🟨 Partie droite : Icônes en ligne, centrées verticalement
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ActionIconButton(
                                R.drawable.ic_rounded_more_vert,
                                onClick = {
                                    showWeekCompletionBottomSheet.value = false // Fermer l’autre si elle est ouverte
                                    selectedPlannedWorkout.value = plannedWorkout
                                    showEditBottomSheet.value = true
                                }
                            )
                            if (!isFiller) {
                                ActionIconButton(
                                    R.drawable.ic_arrow_right_alt,
                                    tint = appColors.textPrimary,
                                    customBackgroundColor = blueMedium,
                                    onClick = {
                                        navController.navigate(Routes.plannedWorkout(plannedWorkout.uuid))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showEditBottomSheet.value && selectedPlannedWorkout.value != null) {
            WeekSessionOptionsBottomSheet(
                plannedWorkout = selectedPlannedWorkout.value!!,
                isFiller = selectedPlannedWorkout.value?.isFiller() == true,
                isDoneThisWeek = selectedPlannedWorkout.value?.let {
                    isPlannedWorkoutDone(it, actualWorkouts)
                } ?: false,
                onDismissRequest = {
                    showEditBottomSheet.value = false
                    selectedPlannedWorkout.value = null
                },
                onRename = {
                    renameText.value = selectedPlannedWorkout.value?.name ?: ""
                    showRenameDialog.value = true
                    showEditBottomSheet.value = false
                },
                onDuplicate = {
                    copySourceWorkout.value = selectedPlannedWorkout.value
                    showCopyDialog.value = true
                    showEditBottomSheet.value = false
                },
                onToggleDone = {
                    selectedPlannedWorkout.value?.let { planned ->
                        viewModel.toggleDoneForPlannedWorkout(planned)
                    }
                    showEditBottomSheet.value = false
                },
                onCreatePlannedWorkout = {
                    val day = selectedPlannedWorkout.value!!.dayOfWeek
                    showEditBottomSheet.value = false
                    showCreateDialog.value = true
                },
                onDelete = {
                    val plannedWorkout = selectedPlannedWorkout.value!!
                    viewModel.deletePlannedWorkout(plannedWorkout)
                    showEditBottomSheet.value = false
                }
            )
        }

        if (showRenameDialog.value && selectedPlannedWorkout.value != null) {
            val errorRestDay = stringResource(R.string.week_error_rest_day_name)
            val canRename = renameText.value.isNotBlank()
            AlertDialog(
                onDismissRequest = {
                    showRenameDialog.value = false
                },
                confirmButton = {
                    DialogPrimaryButton(
                        text = stringResource(R.string.common_rename),
                        enabled = canRename,
                        onClick = onRename@{
                        val newName = renameText.value.trim()

                        if (newName.equals("Rest Day", ignoreCase = true)) {
                            renameText.value = ""
                            showSnackbar(
                                message = errorRestDay,
                                type = SnackbarType.INFO,
                                duration = SnackbarDuration.Short
                            )
                            showRenameDialog.value = false
                            return@onRename
                        }

                        if (newName.isNotEmpty() && selectedPlannedWorkout.value != null) {
                            viewModel.renamePlannedWorkout(selectedPlannedWorkout.value!!.uuid, newName)
                        }
                        showRenameDialog.value = false
                    })
                },
                dismissButton = {
                    DialogSecondaryButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { showRenameDialog.value = false },
                    )
                },
                title = { Text(stringResource(R.string.week_rename_title), color = appColors.primaryAction) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CustomTextField(
                            value = renameText.value,
                            onValueChange = { renameText.value = it },
                            placeholder = stringResource(R.string.week_rename_placeholder)
                        )
                        DialogValidationReason(
                            reason = if (!canRename) stringResource(R.string.form_error_name_required) else null,
                        )
                    }
                },
                containerColor = appColors.bgScreen
            )
        }

        if (showWeekCompletionBottomSheet.value) {
            WeekCompletionBottomSheet(
                onDismissRequest = {
                    showWeekCompletionBottomSheet.value = false
                },
                // 🟦 Ajout des actions globales :
                onSyncAll = {
                    viewModel.syncAllPlannedWorkouts()
                    showWeekCompletionBottomSheet.value = false
                },
                onMarkAllDone = {
                    viewModel.markAllActualWorkoutsAsDone()
                    showWeekCompletionBottomSheet.value = false
                },
                onMarkAllUndone = {
                    viewModel.markAllActualWorkoutsAsUndone()
                    showWeekCompletionBottomSheet.value = false
                }
            )
        }

        selectedPlannedWorkout.value?.dayOfWeek?.let { day ->
            if (showCreateDialog.value && userId != null) {
                val errorRestDayCreate = stringResource(R.string.week_error_rest_day_name)
                CreatePlannedWorkoutDialog(
                    dayOfWeek = day,
                    onDismiss = { showCreateDialog.value = false },
                    onCreate = { workoutName ->
                        val normalizedName = workoutName.trim()

                        if (normalizedName.equals("Rest Day", ignoreCase = true)) {
                            showSnackbar(
                                message = errorRestDayCreate,
                                type = SnackbarType.INFO,
                                duration = SnackbarDuration.Short
                            )
                            return@CreatePlannedWorkoutDialog
                        }

                        val newWorkout = PlannedWorkout(
                            uuid = UUID.randomUUID().toString(),
                            userId = userId!!,
                            name = normalizedName,
                            dayOfWeek = day,
                            synced = false,
                        )

                        val exists = plannedWorkouts.any {
                            it.dayOfWeek.equals(newWorkout.dayOfWeek, ignoreCase = true)
                        }

                        if (exists) {
                            pendingWorkoutToInsert.value = newWorkout
                            showReplaceDialog.value = true
                        } else {
                            viewModel.insertPlannedWorkout(newWorkout)
                            selectedPlannedWorkout.value = null
                            showCreateDialog.value = false
                        }

                    }
                )

            }
        }

        if (showCopyDialog.value && copySourceWorkout.value != null) {
            val source = copySourceWorkout.value!!
            CopyPlannedWorkoutDialog(
                currentDay = source.dayOfWeek,
                onDismiss = {
                    showCopyDialog.value = false
                    copySourceWorkout.value = null
                },
                onConfirm = { targetDay ->
                    viewModel.copyPlannedWorkoutToDay(source, targetDay)
                    showCopyDialog.value = false
                    copySourceWorkout.value = null
                }
            )
        }


    }
}

@Composable
fun WeekViewProgressBar(
    progress: Float,
    onMoreOptionsClick: () -> Unit
) {
    LabeledProgressBar(
        progress = progress,
        showPercent = true,
        rightContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(appColors.bgSurface)
                    .clickable { onMoreOptionsClick() },
                contentAlignment = Alignment.Center
            ) {
                ActionIconButton(
                    R.drawable.ic_rounded_more_vert,
                    tint = appColors.textPrimary,
                    onClick = onMoreOptionsClick
                )
            }
        }
    )
}

fun isPlannedWorkoutDone(planned: PlannedWorkout, actuals: List<ActualWorkout>): Boolean {
    return actuals.any { actual ->
        val actualDay = getDayOfWeekFromDate(actual.date)
        isDateInCurrentWeek(actual.date) &&
                actual.name == planned.name &&
                actualDay.equals(planned.dayOfWeek, ignoreCase = true) &&
                actual.isDone
    }
}

fun isPlannedWorkoutMissingInActuals(planned: PlannedWorkout, actuals: List<ActualWorkout>): Boolean {
    return actuals.none { actual ->
        isDateInCurrentWeek(actual.date) &&
                actual.name == planned.name &&
                getDayOfWeekFromDate(actual.date).equals(planned.dayOfWeek, ignoreCase = true)
    }
}

fun PlannedWorkout.isFiller(): Boolean {
    return this.name == "Rest Day"
}

fun plannedDayProgress(planned: PlannedWorkout, actuals: List<ActualWorkout>): Float {
    if (planned.isFiller()) return 0f

    val match = actuals.firstOrNull { actual ->
        isDateInCurrentWeek(actual.date) &&
                actual.name == planned.name &&
                getDayOfWeekFromDate(actual.date).equals(planned.dayOfWeek, ignoreCase = true)
    }

    return when {
        match == null -> 0f
        match.isDone -> 1f
        else -> 0.5f
    }
}

data class DayProgressResult(
    val progress: Float,
    val hasActual: Boolean,
    val completed: Boolean,
    val isBuilding: Boolean = false,
)

fun calcDayProgressResult(
    planned: PlannedWorkout,
    actualWorkoutsForWeek: List<ActualWorkout>,
    plannedWorkoutExercisesAll: List<PlannedWorkoutExercise>,
    actualWorkoutExercisesForWeek: List<ActualWorkoutExercise>,
    actualWorkoutSetsForWeek: List<ActualWorkoutSet>,
): DayProgressResult {

    if (planned.isFiller()) return DayProgressResult(progress = 0f, hasActual = false, completed = false, isBuilding = false)

    // 1) planned exercises valides
    val plannedExercises = plannedWorkoutExercisesAll
        .filter { it.plannedWorkoutUUID == planned.uuid && !it.pendingDeletion && !it.ignored }

    val plannedTotalSets = plannedExercises.sumOf { it.sets }.coerceAtLeast(0)
    if (plannedTotalSets == 0) {
        return DayProgressResult(
            progress = 0f,
            hasActual = false,
            completed = false,   // si tu veux que "done" gagne
            isBuilding = true
        )
    }

    // Map: exerciseUUID -> plannedSets (si doublons, on prend le dernier)
    val plannedSetsByExercise: Map<String, Int> =
        plannedExercises
            .groupBy { it.exerciseUUID.trim() }
            .mapValues { (_, items) -> items.last().sets }

    val plannedExerciseUUIDs = plannedSetsByExercise.keys

    // 2) trouver l'actual du jour (match "jour + nom + dayOfWeek")
    val actual = actualWorkoutsForWeek.firstOrNull { aw ->
        isDateInCurrentWeek(aw.date) &&
                aw.name == planned.name &&
                getDayOfWeekFromDate(aw.date).equals(planned.dayOfWeek, ignoreCase = true) &&
                !aw.pendingDeletion
    } ?: return DayProgressResult(progress = 0f, hasActual = false, completed = false, isBuilding = false)

    // 3) actual exercises du workout, valides,
    val actualExercisesForWorkout = actualWorkoutExercisesForWeek
        .filter { it.actualWorkoutUUID == actual.uuid }
        .filter { !it.pendingDeletion }

    // 4) done sets capés par exercice planifié
    var cappedDoneTotal = 0

    plannedExerciseUUIDs.forEach { plannedExUUID ->
        val plannedSetsForThisExercise = plannedSetsByExercise[plannedExUUID] ?: 0
        if (plannedSetsForThisExercise <= 0) return@forEach

        // Tous les ActualWorkoutExercise qui correspondent à cet exercice planifié (souvent 1, mais safe)
        val matchingAweUuids = actualExercisesForWorkout
            .filter { it.exerciseUUID.trim() == plannedExUUID }
            .map { it.uuid }
            .toSet()

        val doneSetsForThisExercise = actualWorkoutSetsForWeek.count { s ->
            s.actualWorkoutExerciseUUID in matchingAweUuids &&
                    !s.pendingDeletion &&
                    s.status.trim().equals("DONE", ignoreCase = true)
        }

        // ✅ cap: on ne dépasse jamais ce qui est prévu
        val capped = minOf(doneSetsForThisExercise, plannedSetsForThisExercise)
        cappedDoneTotal += capped
    }

    val progress = (cappedDoneTotal.toFloat() / plannedTotalSets.toFloat()).coerceIn(0f, 1f)
    val completed = actual.isDone || progress >= 0.999f

    return DayProgressResult(progress, hasActual = true, completed = completed, isBuilding = false)
}
