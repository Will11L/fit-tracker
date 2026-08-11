package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CustomSelect
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.HorizontalNumberPicker
import com.example.sportapp.designsystem.common_components.MultiSelectDropdown
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.core.utils.formatRestTime
import com.example.sportapp.core.utils.parseRestTimeToSeconds

@Composable
fun EditExerciseDialog(
    exercise: Exercise,
    musclesByExercise: List<Muscle>,
    equipmentsByExercise: List<Equipment>,
    allMuscles: List<Muscle>,
    allEquipments: List<Equipment>,
    muscleNames: List<String>,
    equipmentNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Exercise, List<Muscle>, List<Equipment>) -> Unit
) {
    val nameState = remember { mutableStateOf(exercise.name) }
    val descriptionState = remember { mutableStateOf(exercise.description ?: "") }

    val selectedMuscles = remember {
        mutableStateListOf<Muscle>().apply {
            addAll(musclesByExercise)
        }
    }

    val selectedEquipments = remember {
        mutableStateListOf<Equipment>().apply {
            addAll(equipmentsByExercise)
        }
    }

    val selectedMuscleNames by remember {
        derivedStateOf { selectedMuscles.map { it.name } }
    }

    val selectedEquipmentNames by remember {
        derivedStateOf { selectedEquipments.map { it.name } }
    }

    val recommendedReps = exercise.recommendedReps
        ?.split("-")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?: listOf(8, 12)

    val repsMinState = remember { mutableIntStateOf(recommendedReps.firstOrNull() ?: 8) }
    val repsMaxState = remember { mutableIntStateOf(recommendedReps.lastOrNull() ?: 12) }
    val setsState = remember { mutableIntStateOf(exercise.recommendedSets ?: 3) }

    val restTimeOptions = listOf(
        30, 45, 60, 90, 120, 180
    ).map { formatRestTime(it) }

    val restTimeState = remember { mutableStateOf(formatRestTime(exercise.restTimeSeconds)) }

    val instructionFields = remember(exercise.uuid) {
        mutableStateListOf<String>().apply {
            val existing = exercise.instructions ?: emptyList()
            if (existing.isEmpty()) {
                add("") // au moins un champ affiché
            } else {
                addAll(existing)
            }
        }
    }

    val canConfirm = nameState.value.isNotBlank()

    FormDialog(
        title = stringResource(R.string.exercise_edit_title),
        confirmText = stringResource(R.string.exercise_edit_confirm),
        confirmEnabled = canConfirm,
        disabledReason = stringResource(R.string.form_error_name_required),
        onConfirm = {
            val cleanedInstructions = instructionFields
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .let { it.ifEmpty { null } }

            val updatedExercise = exercise.copy(
                name = nameState.value.trim(),
                description = descriptionState.value.trim().ifEmpty { null },
                instructions = cleanedInstructions,
                recommendedReps = "${minOf(repsMinState.intValue, repsMaxState.intValue)}-${maxOf(repsMinState.intValue, repsMaxState.intValue)}",
                recommendedSets = setsState.intValue,
                restTimeSeconds = parseRestTimeToSeconds(restTimeState.value)
            )
            onConfirm(updatedExercise, selectedMuscles, selectedEquipments)
        },
        onDismiss = onDismiss,
    ) {
        CustomTextField(
            label = stringResource(R.string.exercise_list_field_name),
            value = nameState.value,
            onValueChange = { nameState.value = it },
            placeholder = stringResource(R.string.exercise_list_field_name),
            textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction)
        )

        CustomTextField(
            label = stringResource(R.string.exercise_edit_description),
            value = descriptionState.value,
            onValueChange = { descriptionState.value = it },
            placeholder = stringResource(R.string.exercise_edit_description),
            singleLine = false,
            textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction)
        )

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Espace gauche = largeur du bouton icône
            Spacer(modifier = Modifier.width(40.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.exercise_edit_instructions),
                    color = appColors.primaryAction,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
            }

            ActionIconButton(
                iconRes = R.drawable.ic_add,
                tint = appColors.textPrimary,
                hasBackground = true,
                customBackgroundColor = blueMedium,
                onClick = { instructionFields.add("") }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            instructionFields.forEachIndexed { index, value ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomTextField(
                        label = stringResource(R.string.exercise_edit_step, index + 1),
                        value = value,
                        onValueChange = { newValue ->
                            instructionFields[index] = newValue
                        },
                        placeholder = stringResource(R.string.exercise_edit_step_placeholder, index + 1),
                        singleLine = false,
                        textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction),
                        modifier = Modifier.weight(1f)
                    )

                    // Bouton remove (garde au moins 1 champ)
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_delete_sweep,
                        hasBackground = true,
                        customBackgroundColor = redDark,
                        onClick = {
                            if (instructionFields.size > 1) instructionFields.removeAt(index)
                            else instructionFields[0] = ""
                        }
                    )
                }
            }
        }

        MultiSelectDropdown(
            label = stringResource(R.string.exercise_list_field_select_muscles),
            options = muscleNames,
            selectedItems = selectedMuscleNames.toMutableStateList(),
            onSelectionChange = { selectedNames ->
                selectedMuscles.clear()
                selectedMuscles.addAll(allMuscles.filter { it.name in selectedNames })
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                HorizontalNumberPicker(
                    label = stringResource(R.string.exercise_list_field_reps_min),
                    selected = repsMinState.intValue,
                    onValueChange = { repsMinState.intValue = it },
                    range = 1..100,
                    scrollOnSelect = true
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                HorizontalNumberPicker(
                    label = stringResource(R.string.exercise_list_field_reps_max),
                    selected = repsMaxState.intValue,
                    onValueChange = { repsMaxState.intValue = it },
                    range = 1..100,
                    scrollOnSelect = true
                )
            }
        }

        Column {
            HorizontalNumberPicker(
                label = stringResource(R.string.exercise_list_field_sets),
                selected = setsState.intValue,
                onValueChange = { setsState.intValue = it },
                range = 1..10,
                scrollOnSelect = true
            )
        }

        CustomSelect(
            label = stringResource(R.string.exercise_edit_rest_time),
            selected = restTimeState.value,
            options = restTimeOptions,
            onSelect = { selected ->
                restTimeState.value = selected
            }
        )

        MultiSelectDropdown(
            label = stringResource(R.string.exercise_list_field_select_equipment),
            options = equipmentNames,
            selectedItems = selectedEquipmentNames.toMutableStateList(),
            onSelectionChange = { selectedNames ->
                selectedEquipments.clear()
                selectedEquipments.addAll(allEquipments.filter { it.name in selectedNames })
            }
        )
    }
}
