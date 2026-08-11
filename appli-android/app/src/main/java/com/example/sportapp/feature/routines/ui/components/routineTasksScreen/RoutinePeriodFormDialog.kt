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
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.ReminderSelector

private fun hhmmToMinutes(hhmm: String): Int? {
    val parts = hhmm.split(":")
    if (parts.size != 2) return null
    val hh = parts[0].toIntOrNull() ?: return null
    val mm = parts[1].toIntOrNull() ?: return null
    if (hh !in 0..23 || mm !in 0..59) return null
    return hh * 60 + mm
}

private fun minutesToHHmm(m: Int): String {
    val hh = (m / 60).toString().padStart(2, '0')
    val mm = (m % 60).toString().padStart(2, '0')
    return "$hh:$mm"
}

/**
 * Dialog création / édition d'une période de routine (nom + plage horaire + 2
 * rappels indépendants "avant le début" / "avant la fin").
 * [period] null = mode création (champs vides), sinon édition (pré-rempli).
 *
 * Pré-remplissage des rappels (2026-06-08) :
 *   - édition  -> valeurs stockées de la période ;
 *   - création -> rappel de début = [defaultReminderMinutes] (défaut global),
 *                 rappel de fin = "Aucun" (opt-in).
 *
 * Bâti sur [FormDialog]. Canonique R15.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinePeriodFormDialog(
    period: RoutinePeriod? = null,
    defaultReminderMinutes: Int? = null,
    onConfirm: (
        name: String,
        startTime: String,
        endTime: String,
        reminderBeforeStart: Int?,
        reminderBeforeEnd: Int?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val isEdit = period != null
    var name by remember(period?.uuid) { mutableStateOf(period?.name ?: "") }
    var start by remember(period?.uuid) {
        mutableStateOf(period?.startTime?.let { hhmmToMinutes(it) } ?: 6 * 60)
    }
    var end by remember(period?.uuid) {
        mutableStateOf(period?.endTime?.let { hhmmToMinutes(it) } ?: 12 * 60)
    }
    var reminderStart by remember(period?.uuid) {
        mutableStateOf(if (period != null) period.reminderBeforeStartMinutes else defaultReminderMinutes)
    }
    var reminderEnd by remember(period?.uuid) {
        mutableStateOf(if (period != null) period.reminderBeforeEndMinutes else null)
    }

    val canConfirm = name.trim().isNotBlank() && start < end
    val disabledReason = when {
        name.trim().isBlank() -> stringResource(R.string.form_error_name_required)
        start >= end -> stringResource(R.string.routine_period_error_end_after_start)
        else -> null
    }

    FormDialog(
        title = if (isEdit) stringResource(R.string.routine_period_dialog_edit_title)
                else stringResource(R.string.routine_period_dialog_add_title),
        confirmText = if (isEdit) stringResource(R.string.common_save) else stringResource(R.string.common_add),
        confirmEnabled = canConfirm,
        disabledReason = disabledReason,
        onConfirm = {
            onConfirm(name.trim(), minutesToHHmm(start), minutesToHHmm(end), reminderStart, reminderEnd)
        },
        onDismiss = onDismiss,
        scrollable = true,
    ) {
        CustomTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.routine_period_field_name_placeholder),
            label = stringResource(R.string.routine_period_field_name),
            modifier = Modifier.fillMaxWidth()
        )
        TimeRangePickerBar(
            minMinutes = 0,
            maxMinutes = 23 * 60 + 59,
            stepMinutes = 5,
            startMinutes = start,
            endMinutes = end,
            onChange = { s, e ->
                start = s
                end = e
            },
            label = stringResource(R.string.routine_period_field_time)
        )
        ReminderSelector(
            label = stringResource(R.string.routine_period_reminder_before_start),
            selectedMinutes = reminderStart,
            onSelect = { reminderStart = it },
            modifier = Modifier.fillMaxWidth(),
        )
        ReminderSelector(
            label = stringResource(R.string.routine_period_reminder_before_end),
            selectedMinutes = reminderEnd,
            onSelect = { reminderEnd = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
