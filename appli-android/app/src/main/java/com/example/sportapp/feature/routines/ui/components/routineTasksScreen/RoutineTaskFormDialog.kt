package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.SingleSelectDropdown

/**
 * Dialog création / édition d'une tâche de routine DAILY (période + titre).
 * [task] null = mode création, sinon édition (pré-rempli). Tâche routine
 * volontairement légère — pas de dueTime/notes/reminder ici (cf. revert
 * 2026-05-13). Bâti sur [FormDialog]. Canonique R15 — remplace
 * `AddRoutineTaskDialog` et `EditRoutineTaskDialog`. i18n via stringResource.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineTaskFormDialog(
    task: Task? = null,
    periods: List<RoutinePeriod>,
    onConfirm: (periodUUID: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isEdit = task != null
    val options = remember(periods) { periods.map { it.name } }

    var title by remember(task?.uuid) { mutableStateOf(task?.title ?: "") }

    val initialPeriodName = remember(task?.periodUUID, periods) {
        task?.periodUUID?.let { uuid -> periods.firstOrNull { it.uuid == uuid }?.name }
            ?: options.firstOrNull().orEmpty()
    }
    var selectedName by remember(task?.uuid, initialPeriodName) { mutableStateOf(initialPeriodName) }

    val selectedPeriodUUID = remember(selectedName, periods) {
        periods.firstOrNull { it.name == selectedName }?.uuid
    }

    val canConfirm = title.trim().isNotBlank() && !selectedPeriodUUID.isNullOrBlank()
    val disabledReason = when {
        title.trim().isBlank() -> stringResource(R.string.form_error_title_required)
        selectedPeriodUUID.isNullOrBlank() -> stringResource(R.string.routine_task_error_period_required)
        else -> null
    }

    FormDialog(
        title = stringResource(
            if (isEdit) R.string.tasks_calendar_edit_title else R.string.tasks_calendar_add_title
        ),
        confirmText = stringResource(if (isEdit) R.string.common_save else R.string.goals_add),
        confirmEnabled = canConfirm,
        disabledReason = disabledReason,
        onConfirm = { selectedPeriodUUID?.let { uuid -> onConfirm(uuid, title.trim()) } },
        onDismiss = onDismiss,
    ) {
        SingleSelectDropdown(
            label = stringResource(R.string.routine_edit_field_period),
            selected = selectedName,
            options = options,
            onSelect = { selectedName = it },
            modifier = Modifier.fillMaxWidth()
        )
        CustomTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = stringResource(R.string.tasks_calendar_field_title_placeholder),
            label = stringResource(R.string.tasks_calendar_field_title),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
