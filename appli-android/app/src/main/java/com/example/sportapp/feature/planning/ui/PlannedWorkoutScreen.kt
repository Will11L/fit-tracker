package com.example.sportapp.feature.planning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.utils.localizedStatus
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.ExercisePickerBottomSheet
import com.example.sportapp.designsystem.common_components.PhasePickerDialog
import com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen.PlannedExerciseOptionsBottomSheet
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen.PlannedWorkoutProgressBar
import com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen.PlannedWorkoutSummaryRow
import com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen.PlannedExerciseRow
import com.example.sportapp.designsystem.common_components.StatusOption
import com.example.sportapp.designsystem.common_components.StatusPickerDialog
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.planning.viewmodel.PlannedWorkoutViewModel

@Composable
fun PlannedWorkoutScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: PlannedWorkoutViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val plannedWorkout by viewModel.plannedWorkout.collectAsState()

    val allPlannedWorkoutExercises by viewModel.allPlannedWorkoutExercises.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()
    val allEquipments by viewModel.allEquipments.collectAsState()
    val allExerciseEquipments by viewModel.allExerciseEquipments.collectAsState()

    val exercisesNotInPlannedWorkout = allExercises.filter { exercise ->
        allPlannedWorkoutExercises.none { it.exerciseUUID == exercise.uuid && !it.pendingDeletion }
    }

    val totalSets = allPlannedWorkoutExercises.sumOf { it.sets }
    val totalReps = allPlannedWorkoutExercises.sumOf {
        it.reps.toIntOrNull() ?: 0 // au cas où le champ `reps` serait une string
    }

    // ========== Remember Sheets ========== //

    val showAddSheet = remember { mutableStateOf(false) }
    val showSyncSheet = remember { mutableStateOf(false) }

    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var showPhaseDialog by remember { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }

    var workoutExerciseForSheet by remember { mutableStateOf<Exercise?>(null) }
    var plannedWorkoutExerciseForSheet by remember { mutableStateOf<PlannedWorkoutExercise?>(null) }

    val showChangePlannedWorkoutStatusDialog = remember { mutableStateOf(false) }


    plannedWorkout?.let { workout ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(appColors.bgScreen)
        ) {
            ScreenTitleBar(title = workout.name)

            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                TitledDivider("Planned workout completion")

                PlannedWorkoutProgressBar(
                    progress = 0f,
                    isSynced = true,
                    onSyncClick = { showSyncSheet.value = true },
                    onAddClick = { showAddSheet.value = true },
                )
                Spacer(modifier = Modifier.height(6.dp))

                PlannedWorkoutSummaryRow(
                    totalSets = totalSets,
                    totalReps = totalReps
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 10.dp)
                ) {
                    val phases = listOf(
                        "Warm-Up" to listOf("WARMUP", "WARM_UP"),
                        "Training" to listOf("TRAINING"),
                        "Post-Training" to listOf("POST_TRAINING")
                    )

                    phases.forEach { (title, phaseNames) ->
                        item { TitledDivider(title) }

                        val exercisesInPhase = allPlannedWorkoutExercises
                            .filter { it.phase.uppercase() in phaseNames }
                            .sortedBy { it.order }

                        items(exercisesInPhase.size) { index ->
                            val planned = exercisesInPhase[index]
                            val exercise = allExercises.firstOrNull { it.uuid == planned.exerciseUUID }

                            if (exercise != null) {
                                // 🎨 Appliquer les couleurs en fonction de la phase
                                val (bgColor, nameBoxColor) = when (planned.phase.uppercase()) {
                                    "WARMUP", "WARM_UP" -> appColors.bgRecessed to appColors.bgRecessed
                                    "TRAINING" -> appColors.bgRecessed to appColors.bgSurface
                                    "POST_TRAINING" -> appColors.bgRecessed to appColors.bgRecessed
                                    else -> appColors.textSecondary to appColors.textTertiary
                                }

                                PlannedExerciseRow(
                                    exercise = exercise,
                                    plannedWorkoutExercise = planned,
                                    backgroundColor = bgColor,
                                    nameBoxColor = nameBoxColor,
                                    onClickOptions = { selectedPlannedWorkoutExercise ->
                                        workoutExerciseForSheet = selectedPlannedWorkoutExercise
                                        plannedWorkoutExerciseForSheet = allPlannedWorkoutExercises.firstOrNull {
                                            it.exerciseUUID == selectedPlannedWorkoutExercise.uuid
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    } ?: Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }


    if (workoutExerciseForSheet != null && plannedWorkoutExerciseForSheet != null)
        PlannedExerciseOptionsBottomSheet(
            exercise = workoutExerciseForSheet!!,
            onDismissRequest = {
                workoutExerciseForSheet = null
                plannedWorkoutExerciseForSheet = null
            },
            onViewDetails = {
                // Fix 2026-06-11 : la route attend l'UUID (exercise/{exerciseUUID}), pas le nom
                // encodé (vestige de l'ancienne route par nom -> "exercice introuvable").
                navController.navigate(Routes.exercise(workoutExerciseForSheet!!.uuid))
                workoutExerciseForSheet = null
            },
            onChangeStatus = {
                showChangePlannedWorkoutStatusDialog.value = true
            },
            onRemoveFromPlannedWorkout = {
                exerciseToDelete = workoutExerciseForSheet
                showDeleteConfirm = true
                workoutExerciseForSheet = null
            }
        )

    if (showChangePlannedWorkoutStatusDialog.value && plannedWorkoutExerciseForSheet != null) {
        StatusPickerDialog(
            title = stringResource(R.string.planned_change_status_title),
            options = listOf(
                StatusOption("DONE", localizedStatus("DONE"), R.drawable.ic_rounded_check, mediumGreen),
                StatusOption("PLANNED", localizedStatus("PLANNED"), R.drawable.ic_arrow_progress, appColors.primaryAction),
                StatusOption("NOT_STARTED", localizedStatus("NOT_STARTED"), R.drawable.ic_rounded_not_started, orangeMedium),
                StatusOption("SKIPPED", localizedStatus("SKIPPED"), R.drawable.ic_rounded_cancel, redMedium),
            ),
            selected = plannedWorkoutExerciseForSheet!!.status.replace(" ", "_").uppercase(),
            onConfirm = { newStatus ->
                viewModel.updatePlannedWorkoutExerciseStatus(
                    plannedWorkoutExerciseUUID = plannedWorkoutExerciseForSheet!!.uuid,
                    newStatus = newStatus,
                )
                showChangePlannedWorkoutStatusDialog.value = false
                plannedWorkoutExerciseForSheet = null
            },
            onDismiss = {
                showChangePlannedWorkoutStatusDialog.value = false
                plannedWorkoutExerciseForSheet = null
            },
        )
    }

    if (showSyncSheet.value) {
        ConfirmationDialog(
            title = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.planned_sync_title),
            message = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.planned_sync_message),
            confirmButtonText = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.common_sync),
            confirmButtonColor = appColors.primaryAction,
            onConfirm = {
                viewModel.syncPlannedWorkoutExerciseAndPlannedWorkoutExercise()
                showSyncSheet.value = false
            },
            onDismiss = {
                showSyncSheet.value = false
            }
        )
    }

    if (showAddSheet.value) {
        ExercisePickerBottomSheet(
            title = stringResource(R.string.sheet_add_exercise_to_plan_title),
            allExercises = exercisesNotInPlannedWorkout,
            allEquipments = allEquipments,
            allExerciseEquipments = allExerciseEquipments,
            onSelectExercise = { exercise ->
                selectedExercise = exercise
                showAddSheet.value = false
                showPhaseDialog = true
            },
            onViewExercise = { exercise ->
                showAddSheet.value = false
                navController.navigate(Routes.exercise(exercise.uuid))
            },
            onDismiss = {
                showAddSheet.value = false
                selectedExercise = null
            }
        )
    }

    if (showPhaseDialog && selectedExercise != null) {
        PhasePickerDialog(
            onPhaseSelected = { phase ->
                viewModel.addPlannedExerciseToPhase(
                    exercise = selectedExercise!!,
                    phase = phase,
                )
                showPhaseDialog = false
                selectedExercise = null
            },
            onDismiss = { showPhaseDialog = false }
        )
    }

    if (showDeleteConfirm && exerciseToDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.common_confirm_deletion),
            message = stringResource(R.string.planned_remove_exercise_message),
            confirmButtonText = stringResource(R.string.common_delete),
            dismissButtonText = stringResource(R.string.common_cancel),
            onConfirm = {
                plannedWorkoutExerciseForSheet?.let {
                    viewModel.markPlannedWorkoutExerciseForDeletion(plannedWorkoutExercise = it)
                }
                showDeleteConfirm = false
                exerciseToDelete = null
                plannedWorkoutExerciseForSheet = null
            },
            onDismiss = {
                showDeleteConfirm = false
                exerciseToDelete = null
                plannedWorkoutExerciseForSheet = null
            }
        )
    }
}
