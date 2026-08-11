package com.example.sportapp.feature.session.ui

import androidx.activity.compose.BackHandler
import com.example.sportapp.feature.session.ui.components.sessionExerciseScreen.ExerciseNoteSection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.HorizontalNumberPicker
import com.example.sportapp.designsystem.common_components.LabeledProgressBar
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.session.ui.components.sessionExerciseScreen.*
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.session.viewmodel.SessionExerciseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionExerciseScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: SessionExerciseViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            //.padding(16.dp)
    ) {
        val actualWorkoutExercise by viewModel.actualWorkoutExercise.collectAsState()
        val setsToDo = actualWorkoutExercise?.sets
        val recommendedRepsFormatted = actualWorkoutExercise?.reps
            ?.split("-")
            ?.joinToString(" - ") { it.trim() }
            ?: "-"
        val recommendedRepsRange = parseRepsRange(actualWorkoutExercise?.reps)

        val actualWorkoutSets by viewModel.actualWorkoutSets.collectAsState()
        val weightUnit by viewModel.weightUnit.collectAsState()
        val setsToShow = actualWorkoutSets
            .sortedBy { it.setOrder }

        val checkedCount = setsToShow.count { it.status == "DONE" }
        val progress = if ((setsToDo ?: 0) > 0) {
            checkedCount.toFloat() / setsToDo!!
        } else 0f

        val exercise by viewModel.exercise.collectAsState()
        var exerciseNote by remember(exercise?.description) {
            mutableStateOf(exercise?.description ?: "")
        }

        val isDone = actualWorkoutSets.count { it.status == "DONE" } >= (setsToDo ?: 0)

        val allSetsSynced = actualWorkoutSets.all { it.synced }
        val actualWorkoutExerciseOrder: String = actualWorkoutExercise?.order?.toString() ?: "-"

        // ==================== Remember ==================== //

        var showSheet by remember { mutableStateOf(false) }
        var selectedSetIndex by remember { mutableStateOf<Int?>(null) }

        var editingSet by remember { mutableStateOf<ActualWorkoutSet?>(null) }
        var editingField by remember { mutableStateOf<String?>(null) } // "reps" ou "weight"

        var showNoteSheet by remember { mutableStateOf(false) }
        var noteDraft by remember { mutableStateOf("") }
        var selectedNoteSetIndex by remember { mutableStateOf<Int?>(null) }

        var editingNoteSet by remember { mutableStateOf<ActualWorkoutSet?>(null) }
        var tempNote by remember { mutableStateOf("") }

        var showDeleteDialog by remember { mutableStateOf(false) }

        var showChangeSetStatusDialog = remember { mutableStateOf(false) }

        var showSyncDialog by remember { mutableStateOf(false) }

        ScreenTitleBar(title = exercise?.name ?: stringResource(R.string.session_exercise_fallback_name))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            //verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 🔹 Fixed Header
            TitledDivider(title = stringResource(R.string.session_exercise_details))

            HeaderInputs(
                onSyncClick = { showSyncDialog = true },
                onGoToExerciseClick = {
                    val exerciseUUID = exercise?.uuid ?: return@HeaderInputs
                    navController.navigate(Routes.exercise(exerciseUUID)) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                checkedSets = checkedCount,
                totalSets = setsToDo ?: 0,
                recommendedReps = recommendedRepsFormatted,
                isDone = isDone,
                allSetsSynced = allSetsSynced,
                actualWorkoutExerciseOrder = actualWorkoutExerciseOrder
            )

            Spacer(modifier = Modifier.height(20.dp))

            TitledDivider(title = stringResource(R.string.session_exercise_progress))

            LabeledProgressBar(
                progress = progress,
                rightContent = {
                    Row(
                        modifier = Modifier
                            .height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        //horizontalArrangement = Arrangement.spacedBy(6.dp) // 👈 spacing optionnel
                    ) {
                        //CustomSpacer()  // dead code — to clean later
                        // « + » d'ajout de série : fond primaire (demande user 2026-07-15, parité web).
                        ActionIconButton(
                            iconRes = R.drawable.ic_add,
                            customBackgroundColor = appColors.primaryAction,
                            onClick = { viewModel.insertNewSetAtEnd() }
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 🔹 Only this part scrolls
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    ExerciseBlock(
                        sets = setsToShow,
                        targetRepsRange = recommendedRepsRange ?: IntRange(8, 12),
                        weightUnit = weightUnit,
                        onIndexClick = { set ->
                            selectedSetIndex = set.setOrder
                            editingSet = set
                            showSheet = true
                        },
                        onEditRepsClick = { set ->
                            editingSet = set
                            editingField = "reps"
                        },
                        onEditWeightClick = { set ->
                            editingSet = set
                            editingField = "weight"
                        },
                        onDeleteClick = { set ->
                            editingSet = set
                            showDeleteDialog = true
                        },
                        onAddNoteClick = { set ->
                            selectedNoteSetIndex = set.setOrder
                            noteDraft = set.notes ?: ""
                            showNoteSheet = true
                            editingNoteSet = set
                            tempNote = set.notes ?: ""
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item { TitledDivider(title = stringResource(R.string.session_exercise_notes)) }

                item {
                    ExerciseNoteSection(
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        note = exerciseNote,
                        onNoteChange = { newValue ->
                            exerciseNote = newValue
                            exercise?.uuid?.let { uuid ->
                                viewModel.updateExerciseDescription(uuid, newValue)
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item { TitledDivider(title = stringResource(R.string.session_exercise_instructions)) }

                item {
                    ExerciseInstructionsSection(
                        instructions = exercise?.instructions.orEmpty() // List<String> attendu
                    )
                }
            }
        }

        if (editingSet != null && editingField != null) {
            // Pour weight : initial value en unit display (LBS si user en LBS) ;
            // range adapté (KG: 0..200, LBS: 0..440 = ~200kg). Sur Save, on
            // reconvertit en kg avant viewModel.updateWeight (storage canonique).
            val isWeight = editingField == "weight"
            val weightInDisplayUnit: Int = if (isWeight) {
                if (weightUnit == com.example.sportapp.feature.onboarding.data.WeightUnit.LBS)
                    com.example.sportapp.feature.onboarding.data.kgToLbs(editingSet!!.weight).toInt()
                else
                    editingSet!!.weight.toInt()
            } else 0
            val pickerRange: IntRange = when {
                !isWeight -> 0..100
                weightUnit == com.example.sportapp.feature.onboarding.data.WeightUnit.LBS -> 0..440
                else -> 0..200
            }

            var tempValue by remember(editingSet, editingField, weightUnit) {
                mutableIntStateOf(
                    if (editingField == "reps") editingSet!!.reps else weightInDisplayUnit
                )
            }

            AlertDialog(
                onDismissRequest = {
                    editingSet = null
                    editingField = null
                },
                containerColor = appColors.bgScreen,
                title = {
                    val unitLabel = com.example.sportapp.feature.onboarding.data.weightLabel(weightUnit)
                    Text(
                        text = if (isWeight) stringResource(R.string.session_exercise_edit_weight, unitLabel)
                               else stringResource(R.string.session_exercise_edit_reps),
                        color = appColors.primaryAction,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    HorizontalNumberPicker(
                        range = pickerRange,
                        selected = tempValue,
                        targetRange = if (editingField == "reps") recommendedRepsRange else null,
                        scrollOnSelect = false,
                        onValueChange = { newValue ->
                            tempValue = newValue
                        }
                    )
                },
                confirmButton = {
                    DialogPrimaryButton(
                        text = stringResource(R.string.common_ok),
                        onClick = {
                        when (editingField) {
                            "reps" -> viewModel.updateReps(editingSet!!.uuid, tempValue)
                            "weight" -> {
                                // Reconversion lbs -> kg si user en LBS (storage canonique kg).
                                val kg = if (weightUnit == com.example.sportapp.feature.onboarding.data.WeightUnit.LBS)
                                    com.example.sportapp.feature.onboarding.data.lbsToKg(tempValue.toFloat())
                                else
                                    tempValue.toFloat()
                                viewModel.updateWeight(editingSet!!.uuid, kg)
                            }
                        }

                        editingSet = null
                        editingField = null
                    })
                },
                dismissButton = {
                    DialogSecondaryButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = {
                            editingSet = null
                            editingField = null
                        },
                    )
                }
            )
        }

        if (showSyncDialog) {
            ConfirmationDialog(
                title = stringResource(R.string.session_exercise_sync_title),
                message = stringResource(R.string.session_exercise_sync_message),
                confirmButtonText = stringResource(R.string.common_sync),
                confirmButtonColor = appColors.primaryAction,
                onConfirm = {
                    viewModel.syncActualWorkoutSets(
                        onSynced = { allSetsSynced ->
                            if (allSetsSynced) {
                                viewModel.syncActualWorkoutExercise()
                            }
                        }
                    )
                    showSyncDialog = false
                },
                onDismiss = {
                    showSyncDialog = false
                }
            )
        }


        if (showSheet) {
            SetOptionsBottomSheet(
                onDismissRequest = { showSheet = false },
                onSupersetClick = {
                    // 👉 Ajoute ici ta logique pour "Superset"
                    showSheet = false
                },
                onDropSetClick = {
                    selectedSetIndex?.let { order ->
                        val baseSet = actualWorkoutSets.firstOrNull { it.setOrder == order } ?: return@let
                        viewModel.insertDropSet(order, baseSet)
                    }
                    showSheet = false
                },
                onBonusSetClick = {
                    selectedSetIndex?.let { order ->
                        val baseSet = actualWorkoutSets.firstOrNull { it.setOrder == order } ?: return@let
                        viewModel.insertBonusSet(baseSet)
                    }
                    showSheet = false
                },
                onChangeStatusClick = {
                    showChangeSetStatusDialog.value = true // 👉 variable que tu définis avec remember { mutableStateOf(false) }
                    showSheet = false
                }
            )
        }

        if (showChangeSetStatusDialog.value && editingSet != null) {
            ChangeSetStatusDialog(
                showDialog = showChangeSetStatusDialog,
                currentSet = editingSet!!,
                onStatusSelected = { newStatus ->
                    viewModel.updateActualWorkoutSetStatus(editingSet!!.uuid, newStatus)
                }
            )
        }

        if (showDeleteDialog && editingSet != null) {
            ConfirmationDialog(
                title = stringResource(R.string.common_confirm_deletion),
                message = stringResource(R.string.session_exercise_delete_set_message),
                onConfirm = {
                    viewModel.markSetAsPendingDeletion(editingSet!!.uuid)
                    editingSet = null
                    showDeleteDialog = false
                },
                onDismiss = {
                    editingSet = null
                    showDeleteDialog = false
                }
            )
        }

        if (editingNoteSet != null) {
            NoteBottomSheet(
                note = tempNote,
                onNoteChange = { tempNote = it },
                onSave = {
                    viewModel.updateNotes(editingNoteSet!!.uuid, tempNote)
                    editingNoteSet = null
                    tempNote = ""
                },
                onCancel = {
                    editingNoteSet = null
                    tempNote = ""
                },
                onDelete = {
                    viewModel.updateNotes(editingNoteSet!!.uuid, "")
                    editingNoteSet = null
                    tempNote = ""
                }
            )
        }

    }
}

