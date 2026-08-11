package com.example.sportapp.feature.session.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.feature.demo_tour.ui.components.demoHighlight
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.feature.session.ui.components.sessionTab.SessionSummaryRow
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.session.ui.components.sessionTab.ExerciseOptionsBottomSheet
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.session.viewmodel.SessionTabViewModel
import com.example.sportapp.designsystem.common_components.ExercisePickerBottomSheet
import com.example.sportapp.designsystem.common_components.PhasePickerDialog
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.feature.session.ui.components.sessionTab.RenameActualWorkoutDialog
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.feature.session.ui.components.sessionTab.SessionExerciseRow
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.feature.session.ui.components.sessionTab.SessionOptionsBottomSheet
import com.example.sportapp.feature.session.ui.components.sessionTab.SessionTabProgressBar


@Composable
fun SessionTab(
    navController: NavHostController,
    sessionUUID: String,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: SessionTabViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    LaunchedEffect(sessionUUID) {
        viewModel.setSessionUUID(sessionUUID)
    }

    val sessionUUID by rememberUpdatedState(newValue = sessionUUID)

    val allExercises by viewModel.allExercises.collectAsState()
    val allEquipments by viewModel.allEquipments.collectAsState()
    val allExerciseEquipments by viewModel.allExerciseEquipments.collectAsState()

    val allActualWorkoutExercise by viewModel.allActualWorkoutExercises.collectAsState()

    val exercisesNotInSession = allExercises.filter { exercise ->
        allActualWorkoutExercise.none { it.exerciseUUID == exercise.uuid && !it.pendingDeletion }
    }

    val plannedWorkout by viewModel.plannedWorkout.collectAsState()

    val plannedWarmUp by viewModel.plannedWarmUp.collectAsState()
    val plannedTraining by viewModel.plannedTraining.collectAsState()
    val plannedPostTraining by viewModel.plannedPostTraining.collectAsState()

    val plannedWorkoutExercises by viewModel.plannedWorkoutExercises.collectAsState()

    val actualWorkoutTitle by viewModel.actualWorkoutTitle.collectAsState()
    val isActualWorkoutSynced by viewModel.actualWorkoutSynced.collectAsState()
    val isActualWorkoutDone by viewModel.actualWorkoutIsDone.collectAsState()

    val allActualWorkoutSets by viewModel.allActualWorkoutSets.collectAsState()

    // Liste des sets à faire et faits pour chaque actualWorkoutExercise
    val exerciseStats = allActualWorkoutExercise
        .filter { !it.pendingDeletion }
        .mapNotNull { actual ->
            val exercise = allExercises.firstOrNull { it.uuid == actual.exerciseUUID } ?: return@mapNotNull null

            val setsDoneRaw = allActualWorkoutSets.count {
                it.actualWorkoutExerciseUUID == actual.uuid &&
                        it.status.equals("DONE", ignoreCase = true)
            }

            val setsToDo = if (actual.addedManually) {
                exercise.recommendedSets ?: 0
            } else {
                plannedWorkoutExercises.firstOrNull { it.exerciseUUID == actual.exerciseUUID }?.sets
                    ?: (exercise.recommendedSets ?: 0)
            }

            val setsDoneCapped = minOf(setsDoneRaw, setsToDo)

            Triple(actual, setsToDo, setsDoneCapped)
        }

    // Stats globales
    val totalSets = exerciseStats.sumOf { it.second }
    val completedSets = exerciseStats.sumOf { it.third }

    val sessionProgress = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f

    var showAddSheet by remember { mutableStateOf(false) }
    var showSyncSheet by remember { mutableStateOf(false) }

    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var showPhaseDialog by remember { mutableStateOf(false) }

    var showDeleteExerciseConfirm by remember { mutableStateOf(false) }
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }

    var workoutExerciseForSheet by remember { mutableStateOf<Exercise?>(null) }
    var actualWorkoutExerciseForSheet by remember { mutableStateOf<ActualWorkoutExercise?>(null) }

    // Session Sheet
    var showSessionOptionsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    var actualWorkoutExerciseToDelete by remember { mutableStateOf<ActualWorkoutExercise?>(null) }

    var showDeleteActualWorkoutConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(appColors.bgScreen)) {

        Box(modifier = Modifier.demoHighlight("session.header", expand = 0.dp)) {
            ScreenTitleBar(
                title = actualWorkoutTitle,
                onClick = { showSessionOptionsSheet = true }
            )
        }

        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            TitledDivider(stringResource(R.string.session_completion))

            SessionTabProgressBar(
                progress = sessionProgress,
                isSync = isActualWorkoutSynced,
                isDone = isActualWorkoutDone,
                onSyncClick = { showSyncSheet = true },
                onToggleDoneClick = { viewModel.toggleActualWorkoutDone() },
                onAddClick = { showAddSheet = true },
            )

            Spacer(modifier = Modifier.height(6.dp))

            val validExercises = allActualWorkoutExercise.filter { !it.pendingDeletion }
            val totalExercises = validExercises.size
            val completedExercises = validExercises.count { it.status.equals("DONE", ignoreCase = true) }

            SessionSummaryRow(
                setsCompleted = completedSets,
                totalSets = totalSets,
                exercisesDone = completedExercises,
                totalExercises = totalExercises
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn (
                modifier = Modifier
                    .fillMaxSize()
                    .demoHighlight("session.exercises")
                    .padding(bottom = 10.dp)
            ) {
                item { TitledDivider(stringResource(R.string.session_phase_warmup)) }
                val warmUpExercises = allActualWorkoutExercise
                    .filter { it.phase.equals("WARMUP", ignoreCase = true) || it.phase.equals("WARM_UP", ignoreCase = true) }
                    .sortedBy { it.order }

                if (warmUpExercises.isEmpty()) {
                    item {
                        EmptyListRow(stringResource(R.string.session_phase_empty_warmup))
                    }
                }

                items(warmUpExercises.size) { index ->
                    val actualWorkoutExercise = warmUpExercises[index]
                    val exercise = allExercises.firstOrNull { it.uuid == actualWorkoutExercise.exerciseUUID }

                    if (exercise != null) {
                        val relatedSets = allActualWorkoutSets.filter {
                            it.actualWorkoutExerciseUUID == actualWorkoutExercise.uuid
                        }

                        val plannedSets = plannedWorkoutExercises
                            .firstOrNull { it.exerciseUUID == exercise.uuid }
                            ?.sets
                        val setsToDo = plannedSets ?: (exercise.recommendedSets ?: 0)           // If no planned sets, use recommended sets if available else 0
                        val setsDone = relatedSets.count { it.status.equals("DONE", ignoreCase = true) }

                        SessionExerciseRow(
                            exercise = exercise,
                            actualWorkoutExercise = actualWorkoutExercise,
                            setsToDo = setsToDo,
                            setsDone = setsDone,
                            backgroundColor = appColors.bgRecessed,
                            nameBoxColor = appColors.bgRecessed,
                            onClickOptions = { selectedWorkoutExercise ->
                                workoutExerciseForSheet = selectedWorkoutExercise
                                actualWorkoutExerciseForSheet = allActualWorkoutExercise.firstOrNull { it.exerciseUUID == selectedWorkoutExercise.uuid }
                            },
                            onClickDetails = {
                                navController.navigate(Routes.sessionExercise(actualWorkoutExercise.uuid))
                            }
                        )
                    }
                }

                item { TitledDivider(stringResource(R.string.session_phase_training)) }
                val trainingExercises = allActualWorkoutExercise
                    .filter { it.phase.equals("TRAINING", ignoreCase = true) }
                    .sortedBy { it.order }

                if (trainingExercises.isEmpty()) {
                    item {
                        EmptyListRow(stringResource(R.string.session_phase_empty_training))
                    }
                }

                items(trainingExercises.size) { index ->
                    val actualWorkoutExercise = trainingExercises[index]
                    val exercise = allExercises.firstOrNull { it.uuid == actualWorkoutExercise.exerciseUUID }

                    if (exercise != null) {
                        val relatedSets = allActualWorkoutSets.filter {
                            it.actualWorkoutExerciseUUID == actualWorkoutExercise.uuid
                        }

                        val plannedSets = plannedWorkoutExercises
                            .firstOrNull { it.exerciseUUID == exercise.uuid }
                            ?.sets
                        val setsToDo = plannedSets ?: (exercise.recommendedSets ?: 0)           // If no planned sets, use recommended sets if available else 0
                        val setsDone = relatedSets.count { it.status.equals("DONE", ignoreCase = true) }

                        SessionExerciseRow(
                            exercise = exercise,
                            actualWorkoutExercise = actualWorkoutExercise,
                            setsToDo = setsToDo,
                            setsDone = setsDone,
                            backgroundColor = appColors.bgRecessed,
                            nameBoxColor = appColors.bgSurface,
                            onClickOptions = { selectedWorkoutExercise ->
                                workoutExerciseForSheet = selectedWorkoutExercise
                                actualWorkoutExerciseForSheet = allActualWorkoutExercise.firstOrNull { it.exerciseUUID == selectedWorkoutExercise.uuid }
                            },
                            onClickDetails = {
                                navController.navigate(Routes.sessionExercise(actualWorkoutExercise.uuid))
                            }
                        )
                    }
                }

                item { TitledDivider(stringResource(R.string.session_phase_posttraining)) }
                val postTrainingExercises = allActualWorkoutExercise
                    .filter { it.phase.equals("POST_TRAINING", ignoreCase = true) }
                    .sortedBy { it.order }

                if (postTrainingExercises.isEmpty()) {
                    item {
                        EmptyListRow(stringResource(R.string.session_phase_empty_posttraining))
                    }
                }

                items(postTrainingExercises.size) { index ->
                    val actualWorkoutExercise = postTrainingExercises[index]
                    val exercise = allExercises.firstOrNull { it.uuid == actualWorkoutExercise.exerciseUUID }

                    if (exercise != null) {
                        val relatedSets = allActualWorkoutSets.filter {
                            it.actualWorkoutExerciseUUID == actualWorkoutExercise.uuid
                        }

                        val plannedSets = plannedWorkoutExercises
                            .firstOrNull { it.exerciseUUID == exercise.uuid }
                            ?.sets
                        val setsToDo = plannedSets ?: (exercise.recommendedSets ?: 0)           // If no planned sets, use recommended sets if available else 0
                        val setsDone = relatedSets.count { it.status.equals("DONE", ignoreCase = true) }

                        SessionExerciseRow(
                            exercise = exercise,
                            actualWorkoutExercise = actualWorkoutExercise,
                            setsToDo = setsToDo,
                            setsDone = setsDone,
                            backgroundColor = appColors.bgRecessed,
                            nameBoxColor = appColors.bgRecessed,
                            onClickOptions = { selectedWorkoutExercise ->
                                workoutExerciseForSheet = selectedWorkoutExercise
                                actualWorkoutExerciseForSheet = allActualWorkoutExercise.firstOrNull { it.exerciseUUID == selectedWorkoutExercise.uuid }
                            },
                            onClickDetails = {
                                navController.navigate(Routes.sessionExercise(actualWorkoutExercise.uuid))
                            }
                        )
                    }
                }

            }
        }
    }

    // Session Options Sheet
    fun closeSessionOptionsSheet() {
        showSessionOptionsSheet = false
    }
    if (showSessionOptionsSheet) {
        SessionOptionsBottomSheet(
            title = actualWorkoutTitle,
            isDone = isActualWorkoutDone,
            onDismissRequest = { closeSessionOptionsSheet() },
            onRenameActualWorkout = {
                closeSessionOptionsSheet()
                showRenameDialog = true
            },
            onToggleDone = {
                viewModel.toggleActualWorkoutDone()
                closeSessionOptionsSheet()
            },
            onSeeTodayPlannedWorkout = {
                closeSessionOptionsSheet()
                val plannedWorkoutUUID = plannedWorkout?.uuid
                if (!plannedWorkoutUUID.isNullOrEmpty()) {
                    navController.navigate(Routes.plannedWorkout(plannedWorkoutUUID))
                }
            },
            onDelete = {
                closeSessionOptionsSheet()
                showDeleteActualWorkoutConfirm = true
            }
        )
    }

    // Rename Dialog
    fun closeRenameDialog() {
        showRenameDialog = false
    }
    if (showRenameDialog) {
        RenameActualWorkoutDialog(
            initialName = actualWorkoutTitle,
            onDismiss = { closeRenameDialog() },
            onConfirm = { newName ->
                closeRenameDialog()
                viewModel.renameActualWorkout(sessionUUID, newName)
            }
        )
    }

    // Exercise Options Sheet
    fun closeExerciseOptionsSheet() {
        workoutExerciseForSheet = null
        actualWorkoutExerciseForSheet = null
    }
    if (workoutExerciseForSheet != null && actualWorkoutExerciseForSheet != null)
        ExerciseOptionsBottomSheet(
            exercise = workoutExerciseForSheet!!,
            onDismissRequest = {
                closeExerciseOptionsSheet()
            },
            onViewDetails = {
                val exerciseUUID = workoutExerciseForSheet!!.uuid
                navController.navigate(Routes.exercise(exerciseUUID))
                closeExerciseOptionsSheet()
            },
            onRemoveFromSession = {
                exerciseToDelete = workoutExerciseForSheet
                actualWorkoutExerciseToDelete = actualWorkoutExerciseForSheet
                showDeleteExerciseConfirm = true
                closeExerciseOptionsSheet()
            }
        )

    // Sync Confirmation Sheet
    fun closeSyncSheet() {
        showSyncSheet = false
    }
    if (showSyncSheet) {
        ConfirmationDialog(
            title = stringResource(R.string.session_sync_title),
            message = stringResource(R.string.session_sync_message),
            confirmButtonText = stringResource(R.string.common_sync),
            confirmButtonColor = appColors.primaryAction,
            onConfirm = {
                viewModel.syncAll()
                closeSyncSheet()
            },
            onDismiss = {
                closeSyncSheet()
            }
        )
    }

    // Add Exercise Bottom Sheet
    fun closeAddSheet() {
        showAddSheet = false
    }
    if (showAddSheet) {
        ExercisePickerBottomSheet(
            title = stringResource(R.string.sheet_add_exercise_title),
            allExercises = exercisesNotInSession,
            allEquipments = allEquipments,
            allExerciseEquipments = allExerciseEquipments,
            onSelectExercise = { exercise ->
                selectedExercise = exercise

                closeAddSheet()
                showPhaseDialog = true
            },
            onViewExercise = { exercise ->
                val exerciseUUID = exercise.uuid
                navController.navigate(Routes.exercise(exerciseUUID))
                closeAddSheet()
            },
            onDismiss = {
                closeAddSheet()
                selectedExercise = null
            }
        )
    }

    // Phase selection dialog
    fun closePhaseDialog() {
        showPhaseDialog = false
        selectedExercise = null
    }
    if (showPhaseDialog && selectedExercise != null) {
        PhasePickerDialog(
            onPhaseSelected = { phase ->
                viewModel.addExerciseToPhase(
                    exercise = selectedExercise!!,
                    phase = phase,
                    workoutUUID = sessionUUID
                )
                closePhaseDialog()
            },
            onDismiss = { closePhaseDialog() }
        )
    }

    // Delete confirmation dialog
    fun closeDeleteExerciseDialog() {
        showDeleteExerciseConfirm = false
        exerciseToDelete = null
        actualWorkoutExerciseToDelete  = null
    }
    if (showDeleteExerciseConfirm && exerciseToDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.common_confirm_deletion),
            message = stringResource(R.string.session_remove_exercise_message, exerciseToDelete?.name ?: ""),
            confirmButtonText = stringResource(R.string.common_delete),
            dismissButtonText = stringResource(R.string.common_cancel),
            onConfirm = {
                actualWorkoutExerciseToDelete?.let {
                    viewModel.markActualWorkoutExerciseForDeletion(it)
                    navController.navigate(Routes.HOME)
                }
                closeDeleteExerciseDialog()
            },
            onDismiss = { closeDeleteExerciseDialog() }
        )
    }

    // ✅ Delete actual workout confirmation
    fun closeDeleteActualWorkoutDialog() {
        showDeleteActualWorkoutConfirm = false
    }

    if (showDeleteActualWorkoutConfirm) {
        ConfirmationDialog(
            title = stringResource(R.string.session_delete_title),
            message = stringResource(R.string.session_delete_message),
            confirmButtonText = stringResource(R.string.common_delete),
            dismissButtonText = stringResource(R.string.common_cancel),
            confirmButtonColor = redMedium,
            onConfirm = {
                closeDeleteActualWorkoutDialog()

                viewModel.markActualWorkoutForDeletion(sessionUUID) {
                    // ✅ après suppression -> revenir en arrière (ou nav home)
                    navController.popBackStack()
                    // ou: navController.navigate("home") { popUpTo("home"){ inclusive = true } }
                }
            },
            onDismiss = { closeDeleteActualWorkoutDialog() }
        )
    }

}