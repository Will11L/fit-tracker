package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.designsystem.common_components.CustomSelect
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.HorizontalNumberPicker
import com.example.sportapp.core.utils.formatRestTime

@Composable
fun EditSetsRepsRestDialog(
    exercise: Exercise,
    draft: ExerciseEditDraft,
    onDismiss: () -> Unit,
    onConfirm: (ExerciseEditDraft) -> Unit
) {
    val restTimeOptions = listOf(30, 45, 60, 90, 120, 180).map { formatRestTime(it) }

    FormDialog(
        title = stringResource(R.string.exercise_edit_sets_title),
        confirmText = stringResource(R.string.exercise_edit_confirm),
        onConfirm = { onConfirm(draft) },
        onDismiss = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                HorizontalNumberPicker(
                    label = stringResource(R.string.exercise_list_field_reps_min),
                    selected = draft.repsMin,
                    onValueChange = { draft.repsMin = it },
                    range = 1..100,
                    scrollOnSelect = true
                )
            }
            Column(Modifier.weight(1f)) {
                HorizontalNumberPicker(
                    label = stringResource(R.string.exercise_list_field_reps_max),
                    selected = draft.repsMax,
                    onValueChange = { draft.repsMax = it },
                    range = 1..100,
                    scrollOnSelect = true
                )
            }
        }

        HorizontalNumberPicker(
            label = stringResource(R.string.exercise_list_field_sets),
            selected = draft.sets,
            onValueChange = { draft.sets = it },
            range = 1..10,
            scrollOnSelect = true
        )

        CustomSelect(
            label = stringResource(R.string.exercise_edit_rest_time),
            selected = draft.restTimeLabel,
            options = restTimeOptions,
            onSelect = { draft.restTimeLabel = it }
        )
    }
}
