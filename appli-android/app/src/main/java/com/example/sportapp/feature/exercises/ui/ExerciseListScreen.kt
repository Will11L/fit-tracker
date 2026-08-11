package com.example.sportapp.feature.exercises.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.sportapp.app.SnackbarController
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.feature.exercises.ui.components.exerciseListScreen.ExerciseCard
import com.example.sportapp.designsystem.common_components.ListSearchHeader
import com.example.sportapp.feature.exercises.ui.components.exerciseListScreen.ExerciseListOptionsBottomSheet
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.DialogValidationReason
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FilterDropdown
import com.example.sportapp.designsystem.common_components.HorizontalNumberPicker
import com.example.sportapp.designsystem.common_components.MultiSelectDropdown
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.EditExerciseDialog
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import com.example.sportapp.feature.exercises.viewmodel.ExerciseListScreenViewModel
import kotlinx.coroutines.launch

@Composable
fun ExerciseListScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: ExerciseListScreenViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val userId by viewModel.userId.collectAsState()
    LaunchedEffect(Unit) {
        SyncEvents.onReconnected.collect {
            viewModel.refreshUserId()
        }
    }

    val context = LocalContext.current

    val allExercises by viewModel.allExercises.collectAsState()
    val allSynced = allExercises.all { it.synced }

    val allMuscles by viewModel.allMuscles.collectAsState()
    val muscleNames = allMuscles.map { it.name }.sorted()

    val allEquipments by viewModel.allEquipments.collectAsState()
    val equipmentNames = allEquipments.map { it.name }.sorted()

    val equipmentsByExercise = viewModel.equipmentsByExercise.collectAsState()
    val musclesByExercise = viewModel.musclesByExercise.collectAsState()

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var sortOption by remember { mutableStateOf("Name (A-Z)") }

    val showOptionsSheet = remember { mutableStateOf(false) }

    val showSyncSheet = remember { mutableStateOf(false) }

    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }

    // New exercise form state
    val showAddExerciseDialog = remember { mutableStateOf(false) }

    // Edit exercise state
    var exerciseToEdit by remember { mutableStateOf<Exercise?>(null) }

    val newExerciseName = remember { mutableStateOf("") }
    val selectedNewMuscles = remember { mutableStateListOf<String>() }
    val selectedNewEquipments = remember { mutableStateListOf<String>() }

    val newRepsMin = remember { mutableIntStateOf(8) }
    val newRepsMax = remember { mutableIntStateOf(12) }
    val newSetsInt = remember { mutableIntStateOf(3) }

    var selectedZone by remember { mutableStateOf<String>("All") } // "All" par défaut
    val zoneOptions = remember(allMuscles) {
        val presentZones = allMuscles
            .map { normalizeZoneLabel(it.zone) }
            .distinct()
            .toSet()

        // garde un ordre stable + seulement celles présentes
        val ordered = ZONE_ORDER.filter { it in presentZones }

        listOf("All") + ordered
    }

    val filteredExercises = allExercises
        .asSequence()
        .filter { it.name.contains(searchQuery.text, ignoreCase = true) }
        .filter { exercise ->
            if (selectedZone == "All") return@filter true

            val muscles = musclesByExercise.value[exercise.uuid].orEmpty()
            muscles.any { m ->
                normalizeZoneLabel(m.zone) == selectedZone
            }
        }
        .sortedWith(
            when (sortOption) {
                "Name (A-Z)" -> compareBy { it.name.lowercase() }
                "Name (Z-A)" -> compareByDescending { it.name.lowercase() }
                else -> compareBy { it.name.lowercase() }
            }
        )
        .toList()

    LaunchedEffect(exerciseToEdit) {
        if (exerciseToEdit != null) {
            viewModel.viewModelScope.launch {
                viewModel.currentSnackbarId?.let { SnackbarController.dismissSnackbarById(it) }
                viewModel.currentSnackbarId = null
            }
        }
    }



    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {

        ScreenTitleBar(title = stringResource(R.string.exercise_list_title))

        // ✅ Content
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {

            Spacer(modifier = Modifier.height(8.dp))

            TitledDivider("Actions")

            Spacer(modifier = Modifier.height(8.dp))

            ListSearchHeader(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                searchPlaceholder = stringResource(R.string.exercise_list_search_short),
                allSynced = allSynced,
                onSyncClick = { showSyncSheet.value = true },
                onMoreClick = { showOptionsSheet.value = true },
                onSortChange = { sortOption = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilterDropdown(
                label = stringResource(R.string.exercise_list_filter_zone),
                options = zoneOptions,
                selected = selectedZone,
                onSelect = { selectedZone = it }
            )

            Spacer(modifier = Modifier.height(8.dp))


            Spacer(modifier = Modifier.height(8.dp))

            TitledDivider("Exercises")

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ✅ List of exercises
                items(filteredExercises, key = { it.uuid }) { exercise ->
                    val equipments = equipmentsByExercise.value[exercise.uuid].orEmpty()
                    val muscles = musclesByExercise.value[exercise.uuid].orEmpty()
                    ExerciseCard(
                        exercise = exercise,
                        equipments = equipments,
                        muscles = muscles,
                        onNavigate = {
                            navController.navigate(Routes.exercise(exercise.uuid))
                        },
                        onEdit = {
                            exerciseToEdit = exercise
                        },
                        onSync = {
                            showSyncSheet.value = true
                        },
                        onToggleFavorite = {
                            viewModel.toggleFavorite(exercise)
                        },
                        onDelete = {
                            exerciseToDelete = exercise
                        }
                    )
                }
            }
        }
    }

    if (showOptionsSheet.value) {
        ExerciseListOptionsBottomSheet(
            onDismissRequest = { showOptionsSheet.value = false },
            onAddSample = {
                showAddExerciseDialog.value = true
                showOptionsSheet.value = false
            },
            onClearAll = {
                viewModel.clearAllExercises()
                showOptionsSheet.value = false
            },
            onExport = {
                // TODO: export logic
                showOptionsSheet.value = false
            }
        )
    }

    if (showAddExerciseDialog.value) {
        val uid = userId
        val addUserIdMissingMsg = stringResource(R.string.vm_user_id_missing_add_exercise)
        if (uid == null) {
            showSnackbar(
                message = addUserIdMissingMsg,
                type = SnackbarType.ERROR,
            )
            showAddExerciseDialog.value = false
            return
        }
        val canAddExercise = newExerciseName.value.isNotBlank() &&
            selectedNewMuscles.isNotEmpty() && selectedNewEquipments.isNotEmpty()
        val addExerciseDisabledReason = when {
            newExerciseName.value.isBlank() -> stringResource(R.string.form_error_name_required)
            selectedNewMuscles.isEmpty() -> stringResource(R.string.form_error_muscles_required)
            selectedNewEquipments.isEmpty() -> stringResource(R.string.form_error_equipment_required)
            else -> null
        }
        AlertDialog(
            onDismissRequest = { showAddExerciseDialog.value = false },
            confirmButton = {
                DialogPrimaryButton(
                    text = stringResource(R.string.goals_add),
                    enabled = canAddExercise,
                    onClick = {
                    val name = newExerciseName.value.trim()
                    val selectedMuscleObjects = allMuscles.filter { it.name in selectedNewMuscles }
                    val min = minOf(newRepsMin.intValue, newRepsMax.intValue)
                    val max = maxOf(newRepsMin.intValue, newRepsMax.intValue)
                    val recommendedReps = "$min-$max"
                    val selectedEquipmentObjects = allEquipments.filter { it.name in selectedNewEquipments }

                    if (name.isNotEmpty() && selectedMuscleObjects.isNotEmpty() && selectedEquipmentObjects.isNotEmpty() && recommendedReps.isNotEmpty()) {
                        val newExercise = Exercise(
                            uuid = java.util.UUID.randomUUID().toString(),
                            userId = uid,
                            name = name,
                            description = null,
                            recommendedReps = recommendedReps,
                            recommendedSets = newSetsInt.intValue,
                            durationInSeconds = null,
                            restTimeSeconds = 60,
                            gifUrl = null,
                            isFavorite = false,
                            lastDone = null,
                            synced = false,
                        )

                        val alreadyExists = allExercises.any { it.name.equals(name, ignoreCase = true) }
                        if (alreadyExists) {
                            showSnackbar(
                                message = context.getString(R.string.vm_exercise_already_exists, name),
                                type = SnackbarType.INFO,
                                duration = SnackbarDuration.Short
                            )
                        } else {
                            viewModel.addExerciseManually(newExercise, selectedMuscleObjects, selectedEquipmentObjects)
                        }

                        // Reset
                        newExerciseName.value = ""
                        selectedNewMuscles.clear()
                        newRepsMin.intValue = 8
                        newRepsMax.intValue = 12
                        newSetsInt.intValue = 3
                        selectedNewEquipments.clear()

                        showAddExerciseDialog.value = false
                    }
                })
            },
            dismissButton = {
                DialogSecondaryButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showAddExerciseDialog.value = false },
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.exercise_list_add_dialog_title),
                    color = appColors.primaryAction
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CustomTextField(
                        value = newExerciseName.value,
                        onValueChange = { newExerciseName.value = it },
                        placeholder = stringResource(R.string.exercise_list_field_name),
                        textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction)
                    )

                    MultiSelectDropdown(
                        label = stringResource(R.string.exercise_list_field_select_muscles),
                        options = muscleNames,
                        selectedItems = selectedNewMuscles,
                        onSelectionChange = { selectedNewMuscles.clear(); selectedNewMuscles.addAll(it) }
                    )

                    // 🔢 Reps Min & Max (avec NumberPicker)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(stringResource(R.string.exercise_list_field_reps_min), color = appColors.textSecondary)
                            HorizontalNumberPicker(
                                selected = newRepsMin.intValue,
                                onValueChange = { newRepsMin.intValue = it },
                                range = 1..100,
                                scrollOnSelect = true
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.exercise_list_field_reps_max), color = appColors.textSecondary)
                            HorizontalNumberPicker(
                                selected = newRepsMax.intValue,
                                onValueChange = { newRepsMax.intValue = it },
                                range = 1..100,
                                scrollOnSelect = true
                            )
                        }
                    }


                    // 🔢 Sets Picker (simple Int field or number selector)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(text = stringResource(R.string.exercise_list_field_sets), color = appColors.textSecondary,)
                            HorizontalNumberPicker(
                                selected = newSetsInt.intValue,
                                onValueChange = { newSetsInt.intValue = it },
                                range = 1..10,
                                scrollOnSelect = true
                            )
                        }
                    }

                    MultiSelectDropdown(
                        label = stringResource(R.string.exercise_list_field_select_equipment),
                        options = equipmentNames,
                        selectedItems = selectedNewEquipments,
                        onSelectionChange = { newSelection ->
                            selectedNewEquipments.clear()
                            selectedNewEquipments.addAll(newSelection)
                        }
                    )

                    DialogValidationReason(reason = addExerciseDisabledReason)
                }
            },
            containerColor = appColors.bgScreen
        )
    }

    if (exerciseToEdit != null) {
        //TODO : a changer comme dans exerciseScreen
        val musclesForExercise = musclesByExercise.value[exerciseToEdit!!.uuid].orEmpty()
        val equipmentsForExercise = equipmentsByExercise.value[exerciseToEdit!!.uuid].orEmpty()

        EditExerciseDialog(
            exercise = exerciseToEdit!!,
            musclesByExercise = musclesForExercise,          // ✅ List<Muscle>
            equipmentsByExercise = equipmentsForExercise,    // ✅ List<Equipment>
            allMuscles = allMuscles,
            allEquipments = allEquipments,
            muscleNames = muscleNames,
            equipmentNames = equipmentNames,
            onDismiss = { exerciseToEdit = null },
            onConfirm = { updatedExercise, selectedMuscles, selectedEquipments ->
                val normalizedName = updatedExercise.name.trim()

                val alreadyExists = viewModel.allExercises.value.any {
                    it.name.equals(normalizedName, ignoreCase = true) &&
                            it.uuid != updatedExercise.uuid
                }

                if (alreadyExists) {
                    exerciseToEdit = null
                    viewModel.onShowDuplicateExerciseNameSnackbar(
                        onRetry = { exerciseToEdit = updatedExercise }
                    )
                    return@EditExerciseDialog
                }

                viewModel.updateExercise(updatedExercise, selectedMuscles, selectedEquipments)
                exerciseToEdit = null
            }
        )
    }


    if (showSyncSheet.value) {
        ConfirmationDialog(
            title = stringResource(R.string.exercise_list_sync_title),
            message = stringResource(R.string.exercise_list_sync_message),
            confirmButtonColor = appColors.primaryAction,
            confirmButtonText = stringResource(R.string.common_sync),
            onConfirm = {
                viewModel.syncExercises()
                showSyncSheet.value = false
            },
            onDismiss = {
                showSyncSheet.value = false
            }
        )
    }

    if (exerciseToDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.exercise_list_delete_title),
            message = stringResource(R.string.exercise_list_delete_message, exerciseToDelete?.name ?: ""),
            onConfirm = {
                exerciseToDelete?.let {
                    viewModel.deleteExercise(it)
                }
                exerciseToDelete = null
            },
            onDismiss = {
                exerciseToDelete = null
            }
        )
    }

}

private fun normalizeZoneLabel(raw: String?): String {
    val z = raw?.trim()?.lowercase().orEmpty()

    return when {
        z.isBlank() -> "Other"
        z.contains("upper") || z.contains("haut") -> "Upper Body"
        z.contains("lower") || z.contains("bas") || z.contains("leg") || z.contains("jambe") -> "Lower Body"
        z.contains("core") || z.contains("abs") || z.contains("ab") || z.contains("tronc") -> "Core"
        z.contains("full") || z.contains("whole") || z.contains("total") -> "Full Body"
        else -> "Other"
    }
}

private val ZONE_ORDER = listOf(
    "Upper Body",
    "Lower Body",
    "Core",
    "Full Body",
    "Other"
)
