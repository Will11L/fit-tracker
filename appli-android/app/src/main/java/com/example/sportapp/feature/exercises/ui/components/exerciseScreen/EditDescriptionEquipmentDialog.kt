package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.MultiSelectDropdown

@Composable
fun EditDescriptionEquipmentDialog(
    exercise: Exercise,
    draft: ExerciseEditDraft,
    allEquipments: List<Equipment>,
    equipmentNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (ExerciseEditDraft) -> Unit
) {
    val selectedEquipmentNames by remember {
        derivedStateOf { draft.selectedEquipments.map { it.name } }
    }

    FormDialog(
        title = stringResource(R.string.exercise_edit_desc_equip_title),
        confirmText = stringResource(R.string.exercise_edit_confirm),
        onConfirm = { onConfirm(draft) },
        onDismiss = onDismiss,
    ) {
        CustomTextField(
            label = stringResource(R.string.exercise_edit_description),
            value = draft.description,
            onValueChange = { draft.description = it },
            placeholder = stringResource(R.string.exercise_edit_description),
            singleLine = false,
        )

        MultiSelectDropdown(
            label = stringResource(R.string.exercise_list_field_select_equipment),
            options = equipmentNames,
            selectedItems = selectedEquipmentNames.toMutableStateList(),
            onSelectionChange = { selectedNames ->
                draft.selectedEquipments.clear()
                draft.selectedEquipments.addAll(allEquipments.filter { it.name in selectedNames })
            }
        )
    }
}
