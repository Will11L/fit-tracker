package com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.common_components.CustomDatePickerDialog
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.ReminderPreset
import com.example.sportapp.designsystem.common_components.ReminderSelector
import com.example.sportapp.designsystem.theme.appColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Phase 2 (2026-05-12) : dialog create/edit pour Task avec recurrence (option C
 * "All occurrences" pour le edit -- pas de "this only / future" en MVP).
 *
 * recurrenceKind:
 *   - NONE : dueDate REQUIRED, dueTime optional
 *   - WEEKLY : weekdays REQUIRED (multi-select Mon..Sun), startDate, optional endDate
 *   - MONTHLY/YEARLY : startDate REQUIRED (jour-du-mois ou jour+mois), optional endDate
 */
data class TaskFormData(
    val title: String,
    val recurrenceKind: String,
    val dueDate: LocalDate?,
    val dueTime: String?,
    val recurrenceWeekdays: List<Int>?,
    val recurrenceStartDate: LocalDate?,
    val recurrenceEndDate: LocalDate?,
    val reminderMinutesBefore: Int?,
)

/** Presets reminder spécifiques aux tâches (échéance) : incluent "1 jour".
 *  S'appuient sur le type partagé [ReminderPreset] (ReminderSelector). */
private val TASK_REMINDER_PRESETS = listOf(
    ReminderPreset(null, R.string.tasks_calendar_reminder_none),
    ReminderPreset(15, R.string.tasks_calendar_reminder_15min),
    ReminderPreset(60, R.string.tasks_calendar_reminder_1h),
    ReminderPreset(24 * 60, R.string.tasks_calendar_reminder_1day),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(
    existing: Task? = null,
    defaultDate: LocalDate,
    defaultReminderMinutes: Int? = null,
    onConfirm: (TaskFormData) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(existing?.uuid) { mutableStateOf(existing?.title ?: "") }
    var recurrenceKind by remember(existing?.uuid) {
        mutableStateOf(existing?.recurrenceKind ?: "NONE")
    }
    var dueDate by remember(existing?.uuid) {
        mutableStateOf(
            existing?.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: defaultDate
        )
    }
    var dueTime by remember(existing?.uuid) { mutableStateOf(existing?.dueTime ?: "") }

    var weekdays by remember(existing?.uuid) {
        mutableStateOf(existing?.recurrenceWeekdays?.toSet() ?: emptySet())
    }
    var startDate by remember(existing?.uuid) {
        mutableStateOf(
            existing?.recurrenceStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: defaultDate
        )
    }
    var endDate by remember(existing?.uuid) {
        mutableStateOf(
            existing?.recurrenceEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        )
    }
    var reminderMinutes by remember(existing?.uuid) {
        // Pré-remplissage : édition -> valeur stockée ; création -> défaut global.
        mutableStateOf(if (existing != null) existing.reminderMinutesBefore else defaultReminderMinutes)
    }

    var datePickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }

    val isEdit = existing != null
    val titleRes = if (isEdit) R.string.tasks_calendar_edit_title else R.string.tasks_calendar_add_title

    val canConfirm = title.trim().isNotBlank() && when (recurrenceKind) {
        "NONE" -> true  // dueDate has default
        "WEEKLY" -> weekdays.isNotEmpty()
        "MONTHLY", "YEARLY" -> true
        else -> false
    }
    val disabledReason = when {
        title.trim().isBlank() -> stringResource(R.string.form_error_title_required)
        recurrenceKind == "WEEKLY" && weekdays.isEmpty() ->
            stringResource(R.string.tasks_calendar_error_weekday_required)
        else -> null
    }

    FormDialog(
        title = stringResource(titleRes),
        confirmText = stringResource(if (isEdit) R.string.common_save else R.string.goals_add),
        confirmEnabled = canConfirm,
        disabledReason = disabledReason,
        onConfirm = {
            val timeNormalized = dueTime.trim()
                .takeIf { it.matches(Regex("""^\d{2}:\d{2}$""")) }
            onConfirm(
                TaskFormData(
                    title = title.trim(),
                    recurrenceKind = recurrenceKind,
                    dueDate = if (recurrenceKind == "NONE") dueDate else null,
                    dueTime = timeNormalized,
                    recurrenceWeekdays = if (recurrenceKind == "WEEKLY") weekdays.sorted() else null,
                    recurrenceStartDate = if (recurrenceKind != "NONE") startDate else null,
                    recurrenceEndDate = if (recurrenceKind != "NONE") endDate else null,
                    reminderMinutesBefore = reminderMinutes,
                )
            )
        },
        onDismiss = onDismiss,
        scrollable = true,
    ) {
                CustomTextField(
                    label = stringResource(R.string.tasks_calendar_field_title),
                    value = title,
                    onValueChange = { title = it },
                    placeholder = stringResource(R.string.tasks_calendar_field_title_placeholder),
                )

                // Recurrence kind selector (4 chips scrollable horizontalement
                // pour eviter que "Annuel" sorte du dialog si la police FR est large)
                Column {
                    Text(
                        text = stringResource(R.string.tasks_calendar_field_repeat),
                        color = appColors.textTertiary,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RecurrenceChip("NONE", recurrenceKind, R.string.tasks_calendar_recur_none) { recurrenceKind = "NONE" }
                        RecurrenceChip("WEEKLY", recurrenceKind, R.string.tasks_calendar_recur_weekly) { recurrenceKind = "WEEKLY" }
                        RecurrenceChip("MONTHLY", recurrenceKind, R.string.tasks_calendar_recur_monthly) { recurrenceKind = "MONTHLY" }
                        RecurrenceChip("YEARLY", recurrenceKind, R.string.tasks_calendar_recur_yearly) { recurrenceKind = "YEARLY" }
                    }
                }

                // Champ date selon kind
                when (recurrenceKind) {
                    "NONE" -> DateField(
                        label = stringResource(R.string.tasks_calendar_field_due_date),
                        date = dueDate,
                        onClick = { datePickerTarget = DatePickerTarget.DUE_DATE },
                    )
                    else -> DateField(
                        label = stringResource(R.string.tasks_calendar_field_start_date),
                        date = startDate,
                        onClick = { datePickerTarget = DatePickerTarget.START_DATE },
                    )
                }

                if (recurrenceKind == "WEEKLY") {
                    // Multi-select weekdays
                    Column {
                        Text(
                            text = stringResource(R.string.tasks_calendar_field_weekdays),
                            color = appColors.textTertiary,
                            fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val dayLabels = listOf(
                            stringResource(R.string.weekday_short_mon),
                            stringResource(R.string.weekday_short_tue),
                            stringResource(R.string.weekday_short_wed),
                            stringResource(R.string.weekday_short_thu),
                            stringResource(R.string.weekday_short_fri),
                            stringResource(R.string.weekday_short_sat),
                            stringResource(R.string.weekday_short_sun),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            dayLabels.forEachIndexed { idx, label ->
                                WeekdayChip(
                                    label = label,
                                    selected = idx in weekdays,
                                    onClick = {
                                        weekdays = if (idx in weekdays) weekdays - idx else weekdays + idx
                                    },
                                )
                            }
                        }
                    }
                }

                if (recurrenceKind != "NONE") {
                    DateField(
                        label = stringResource(R.string.tasks_calendar_field_end_date_optional),
                        date = endDate,
                        placeholder = stringResource(R.string.tasks_calendar_field_end_date_none),
                        onClick = { datePickerTarget = DatePickerTarget.END_DATE },
                        onClear = if (endDate != null) ({ endDate = null }) else null,
                    )
                }

                // dueTime : restreint aux chiffres + ":" (eviter lettres typees)
                CustomTextField(
                    label = stringResource(R.string.tasks_calendar_field_due_time),
                    value = dueTime,
                    onValueChange = { input ->
                        dueTime = input.filter { it.isDigit() || it == ':' }.take(5)
                    },
                    placeholder = stringResource(R.string.tasks_calendar_field_due_time_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                // Reminder section (Phase 3) -- ReminderSelector partagé
                ReminderSelector(
                    label = stringResource(R.string.tasks_calendar_field_reminder),
                    selectedMinutes = reminderMinutes,
                    onSelect = { reminderMinutes = it },
                    presets = TASK_REMINDER_PRESETS,
                )
            }

    datePickerTarget?.let { target ->
        val initial = when (target) {
            DatePickerTarget.DUE_DATE -> dueDate
            DatePickerTarget.START_DATE -> startDate
            DatePickerTarget.END_DATE -> endDate ?: startDate
        }
        val pickerTitleRes = when (target) {
            DatePickerTarget.DUE_DATE -> R.string.tasks_calendar_field_due_date
            DatePickerTarget.START_DATE -> R.string.tasks_calendar_field_start_date
            DatePickerTarget.END_DATE -> R.string.tasks_calendar_field_end_date_optional
        }
        CustomDatePickerDialog(
            initialIso = initial.toString(),
            title = stringResource(pickerTitleRes),
            minYear = LocalDate.now().year - 1,
            maxYear = LocalDate.now().year + 5,
            onConfirm = { iso ->
                val newDate = LocalDate.parse(iso)
                when (target) {
                    DatePickerTarget.DUE_DATE -> dueDate = newDate
                    DatePickerTarget.START_DATE -> startDate = newDate
                    DatePickerTarget.END_DATE -> endDate = newDate
                }
                datePickerTarget = null
            },
            onDismiss = { datePickerTarget = null },
        )
    }
}

private enum class DatePickerTarget { DUE_DATE, START_DATE, END_DATE }

@Composable
private fun RecurrenceChip(
    kind: String,
    selectedKind: String,
    labelRes: Int,
    onClick: () -> Unit,
) {
    val selected = kind == selectedKind
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) appColors.primaryAction else appColors.bgRecessed)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            color = if (selected) appColors.textPrimary else appColors.textTertiary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun WeekdayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 36.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) appColors.primaryAction else appColors.bgRecessed)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) appColors.textPrimary else appColors.textTertiary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    placeholder: String = "—",
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = appColors.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (onClear != null) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.common_delete), color = appColors.textTertiary, fontSize = 10.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            val txt = date?.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
                ?: placeholder
            Text(
                text = txt,
                color = if (date != null) appColors.textPrimary else appColors.textTertiary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
