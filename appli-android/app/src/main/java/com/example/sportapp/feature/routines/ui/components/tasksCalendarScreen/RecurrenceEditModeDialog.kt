package com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomRadioButton
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.appColors

/**
 * B.4 (2026-05-12) : choix de la portee de l'edition d'une task recurrente
 * (WEEKLY/MONTHLY/YEARLY). Affiche AVANT TaskCreateEditDialog. 2 modes :
 *
 *  - ONLY_THIS : edit applique uniquement a l'occurrence cliquee. Cote VM,
 *                ajoute la date a Task.excludedDates ET cree une nouvelle
 *                Task NONE (one-off) avec les valeurs editees.
 *  - ALL       : edit applique a la serie entiere (comportement historique).
 *
 *  Note : pas de mode "This and future" pour MVP (deferred).
 */
enum class RecurrenceEditMode { ONLY_THIS, ALL }

@Composable
fun RecurrenceEditModeDialog(
    onConfirm: (RecurrenceEditMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(RecurrenceEditMode.ONLY_THIS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.bgScreen,
        title = {
            Text(
                text = stringResource(R.string.tasks_calendar_recur_edit_mode_title),
                color = appColors.primaryAction,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.tasks_calendar_recur_edit_mode_message),
                    color = appColors.textTertiary,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))

                ModeRow(
                    label = stringResource(R.string.tasks_calendar_recur_edit_mode_only_this),
                    selected = selected == RecurrenceEditMode.ONLY_THIS,
                    onClick = { selected = RecurrenceEditMode.ONLY_THIS },
                )
                ModeRow(
                    label = stringResource(R.string.tasks_calendar_recur_edit_mode_all),
                    selected = selected == RecurrenceEditMode.ALL,
                    onClick = { selected = RecurrenceEditMode.ALL },
                )
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = stringResource(R.string.common_next), onClick = { onConfirm(selected) })
        },
        dismissButton = {
            DialogSecondaryButton(text = stringResource(R.string.common_cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun ModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CustomRadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = label,
            color = appColors.textPrimary,
            fontSize = 14.sp,
        )
    }
}
