package com.example.sportapp.feature.exercises.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.projections.ActualWorkoutExerciseWithWorkoutDateAndSets
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.EditDescriptionEquipmentDialog
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.EditInstructionsDialog
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.EditSetsRepsRestDialog
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.ExerciseEditDraft
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.ExerciseMoreOptionsBottomSheet
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.ExerciseScreenDetails
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.ExerciseActionBar
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.LastSessionsSection
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.StatsSection
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.buildUpdatedExercise
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.core.utils.formatRestTime
import com.example.sportapp.feature.exercises.viewmodel.ExerciseScreenViewModel
import java.util.Locale

@Composable
fun ExerciseScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: ExerciseScreenViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val exercise by viewModel.exercise.collectAsState()

    var showMoreOptions by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // ✅ 3 dialogs séparés
    var showEditStatsDialog by remember { mutableStateOf(false) }          // sets/reps/rest
    var showEditInfoDialog by remember { mutableStateOf(false) }           // description + equipment
    var showEditInstructionsDialog by remember { mutableStateOf(false) }   // instructions

    if (exercise == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(appColors.bgScreen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.exercise_not_found), color = redMedium)
        }
        return
    }

    // ✅ flows dépendants de l’exercice
    val musclesForExercise by viewModel.musclesByExercise(exercise!!.uuid).collectAsState()
    val equipmentsForExercise by viewModel.equipmentsByExercise(exercise!!.uuid).collectAsState()
    val lastSessions by viewModel.lastSessions(exercise!!.uuid).collectAsState()

    val allEquipments by viewModel.allEquipments.collectAsState()
    val equipmentNames = allEquipments.map { it.name }.sorted()

    // ✅ draft partagé (recréé si l’exo change)
    val draft = remember(exercise!!.uuid) {
        val reps = exercise!!.recommendedReps
            ?.split("-")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: listOf(8, 12)

        ExerciseEditDraft(
            name = exercise!!.name,
            description = exercise!!.description ?: "",
            instructionFields = mutableStateListOf<String>().apply {
                val existing = exercise!!.instructions.orEmpty().filter { it.isNotBlank() }
                if (existing.isEmpty()) add("") else addAll(existing)
            },
            repsMin = reps.firstOrNull() ?: 8,
            repsMax = reps.lastOrNull() ?: 12,
            sets = exercise!!.recommendedSets ?: 3,
            restTimeLabel = formatRestTime(exercise!!.restTimeSeconds),
            selectedMuscles = mutableStateListOf<Muscle>().apply {
                addAll(musclesForExercise)
            },
            selectedEquipments = mutableStateListOf<Equipment>().apply {
                addAll(equipmentsForExercise)
            }
        )
    }

    // --- BottomSheet options ---
    if (showMoreOptions) {
        ExerciseMoreOptionsBottomSheet(
            onDismissRequest = { showMoreOptions = false },
            onEditClick = {
                showMoreOptions = false
                // au choix: ouvrir un dialog précis
                showEditInfoDialog = true
            },
            onDelavierMethodClick = {
                showMoreOptions = false
                navController.navigate(Routes.DELAVIER_METHOD)
            },
            onDeleteClick = {
                showMoreOptions = false
                showDeleteConfirmation = true
            }
        )
    }

    // --- Delete confirmation ---
    if (showDeleteConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.exercise_list_delete_title),
            message = stringResource(R.string.exercise_list_delete_message, exercise!!.name),
            onConfirm = {
                showDeleteConfirmation = false
                viewModel.markExerciseForDeletion(exercise!!)
                navController.navigate(Routes.exercise(exercise?.uuid.toString())) {
                    popUpTo(Routes.exercise(exercise?.uuid.toString())) { inclusive = true }
                }
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }

    // --- ✅ Dialog 1: sets / reps / rest ---
    if (showEditStatsDialog) {
        EditSetsRepsRestDialog(
            exercise = exercise!!,
            draft = draft,
            onDismiss = { showEditStatsDialog = false },
            onConfirm = { updatedDraft ->
                val updatedExercise = buildUpdatedExercise(
                    original = exercise!!,
                    draft = updatedDraft
                )
                viewModel.updateExercise(updatedExercise, musclesForExercise, equipmentsForExercise)
                showEditStatsDialog = false
            }
        )
    }

    // --- ✅ Dialog 2: description + equipment ---
    if (showEditInfoDialog) {
        EditDescriptionEquipmentDialog(
            exercise = exercise!!,
            draft = draft,
            allEquipments = allEquipments,
            equipmentNames = equipmentNames,
            onDismiss = { showEditInfoDialog = false },
            onConfirm = { updatedDraft ->
                val updatedExercise = buildUpdatedExercise(
                    original = exercise!!,
                    draft = updatedDraft
                )
                viewModel.updateExercise(updatedExercise, musclesForExercise, updatedDraft.selectedEquipments)
                showEditInfoDialog = false
            }
        )
    }

    // --- ✅ Dialog 3: instructions ---
    if (showEditInstructionsDialog) {
        EditInstructionsDialog(
            draft = draft,
            onDismiss = { showEditInstructionsDialog = false },
            onConfirm = { updatedDraft ->
                val updatedExercise = buildUpdatedExercise(
                    original = exercise!!,
                    draft = updatedDraft
                )
                viewModel.updateExercise(updatedExercise, musclesForExercise, equipmentsForExercise)
                showEditInstructionsDialog = false
            }
        )
    }

    // --- UI principale ---
    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {

        ScreenTitleBar(title = exercise!!.name)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { TitledDivider("Actions") }

            item {
                ExerciseActionBar(
                    exercise = exercise!!,
                    onBack = { navController.popBackStack() },
                    onFavoriteClick = { viewModel.toggleFavorite(exercise!!) },
                    onSyncClick = { viewModel.syncExercises() },
                    onDelavierMethodClick = { navController.navigate(Routes.DELAVIER_METHOD) },
                    onMoreClick = { showMoreOptions = true }
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                ExerciseScreenDetails(
                    exercise = exercise!!,
                    equipment = equipmentsForExercise
                        .map { it.name }
                        .sortedBy { it.lowercase(Locale.getDefault()) },

                    // ✅ callbacks boutons "Edit" dans chaque colonne
                    onEditStatsClick = { showEditStatsDialog = true },
                    onEditInfoClick = { showEditInfoDialog = true },
                    onEditInstructionsClick = { showEditInstructionsDialog = true }
                )
            }

            item {
                ExerciseMainContent(
                    exercise = exercise!!,
                    lastSessions = lastSessions,
                    onOpenSession = { aweUuid ->
                        navController.navigate(Routes.sessionExercise(aweUuid))
                    }
                )
            }
        }
    }
}

@Composable
fun ExerciseMainContent(
    exercise: Exercise,
    lastSessions: List<ActualWorkoutExerciseWithWorkoutDateAndSets>,
    onOpenSession: (actualWorkoutExerciseUUID: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TitledDivider("Stats")

        StatsSection(exerciseUUID = exercise.uuid)

        Spacer(modifier = Modifier.height(24.dp))

        TitledDivider("Last Sessions")

        LastSessionsSection(
            lastSessions = lastSessions,
            onOpenSession = onOpenSession
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
