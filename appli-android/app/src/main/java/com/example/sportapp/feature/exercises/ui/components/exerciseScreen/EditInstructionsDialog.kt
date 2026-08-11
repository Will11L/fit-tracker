package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redDark

@Composable
fun EditInstructionsDialog(
    draft: ExerciseEditDraft,
    onDismiss: () -> Unit,
    onConfirm: (ExerciseEditDraft) -> Unit
) {
    FormDialog(
        title = stringResource(R.string.exercise_edit_instructions_title),
        confirmText = stringResource(R.string.exercise_edit_confirm),
        onConfirm = { onConfirm(draft) },
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(40.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                onClick = { draft.instructionFields.add("") }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.instructionFields.forEachIndexed { index, value ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomTextField(
                        label = stringResource(R.string.exercise_edit_step, index + 1),
                        value = value,
                        onValueChange = { draft.instructionFields[index] = it },
                        placeholder = stringResource(R.string.exercise_edit_step_placeholder, index + 1),
                        singleLine = false,
                        modifier = Modifier.weight(1f)
                    )

                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_delete_sweep,
                        hasBackground = true,
                        customBackgroundColor = redDark,
                        onClick = {
                            if (draft.instructionFields.size > 1) draft.instructionFields.removeAt(index)
                            else draft.instructionFields[0] = ""
                        }
                    )
                }
            }
        }
    }
}
