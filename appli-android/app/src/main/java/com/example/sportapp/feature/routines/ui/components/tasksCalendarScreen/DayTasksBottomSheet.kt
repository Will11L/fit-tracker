package com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.common_components.OptionRow
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.CustomCheckbox
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.routines.viewmodel.TaskRowDayUi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Phase 1 (2026-05-12) : BottomSheet affichant les tasks d'un jour selectionne
 * dans TasksCalendarScreen + bouton "Add task".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTasksBottomSheet(
    date: LocalDate,
    rows: List<TaskRowDayUi>,
    onDismissRequest: () -> Unit,
    onAddTask: () -> Unit,
    onToggleDone: (Task, Boolean) -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        // Fix nav bar systeme : matche le color du sheet (dark) au lieu de blanc.
        forceDarkSystemBars = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            TitledDivider(
                title = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rounded_check_circle),
                        contentDescription = null,
                        tint = blueMedium.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tasks_calendar_day_empty),
                        color = appColors.textTertiary,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(rows, key = { it.task.uuid }) { row ->
                        val badge = computeRelativeBadge(row.task, date, row.isChecked)
                        DayTaskItem(
                            row = row,
                            relativeBadge = badge,
                            onToggleDone = onToggleDone,
                            onEdit = { onEditTask(row.task) },
                            onDelete = { onDeleteTask(row.task) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TitledDivider(title = stringResource(R.string.tasks_calendar_add_section))
            Spacer(modifier = Modifier.height(8.dp))

            // Row "Ajouter une tache" style cohere avec OptionRow autres BottomSheets
            // (cadre appColors.bgRecessed, texte gauche, icone droite clickable seule).
            OptionRow(
                label = stringResource(R.string.tasks_calendar_add_task),
                iconRes = R.drawable.ic_add,
                onClick = onAddTask,
                hasBackground = true,
                customColor = appColors.primaryAction,
            )
        }
    }
}

private data class RelativeBadge(val text: String, val color: Color)

/**
 * A.5 (2026-05-12) : badge "in 30m" / "overdue 2h" / "in 3d" calcule a la
 * volee selon la position relative de la task vs maintenant.
 *
 * Regles :
 * - row checkee : pas de badge (deja faite, peu importe le timing)
 * - date > today (jour futur) : "in Xd" / "in Xh" (appColors.primaryAction)
 * - date < today (jour passe) : "overdue Xd" (redMedium)
 * - date == today :
 *     * dueTime null : pas de badge (ambigu, juste "today")
 *     * diff > 60min : "in Xh" (appColors.primaryAction)
 *     * diff 1..60min : "in Xm" (orangeMedium si <30min sinon appColors.primaryAction)
 *     * diff < 0 : "overdue X" (redMedium)
 *     * diff == 0 : pas de badge ("now", trop transient)
 */
@Composable
private fun computeRelativeBadge(task: Task, date: LocalDate, isChecked: Boolean): RelativeBadge? {
    if (isChecked) return null
    val today = LocalDate.now()

    if (date > today) {
        val days = ChronoUnit.DAYS.between(today, date).toInt()
        return RelativeBadge(
            text = stringResource(R.string.tasks_calendar_due_in_format, "${days}d"),
            color = appColors.primaryAction,
        )
    }
    if (date < today) {
        val days = ChronoUnit.DAYS.between(date, today).toInt()
        return RelativeBadge(
            text = stringResource(R.string.tasks_calendar_overdue_format, "${days}d"),
            color = redMedium,
        )
    }

    // date == today : need dueTime
    val dueTime = task.dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val dueDateTime = date.atTime(dueTime)
    val diffMinutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), dueDateTime).toInt()

    return when {
        diffMinutes > 60 -> RelativeBadge(
            text = stringResource(R.string.tasks_calendar_due_in_format, "${diffMinutes / 60}h"),
            color = appColors.primaryAction,
        )
        diffMinutes in 1..60 -> RelativeBadge(
            text = stringResource(R.string.tasks_calendar_due_in_format, "${diffMinutes}m"),
            color = if (diffMinutes < 30) orangeMedium else appColors.primaryAction,
        )
        diffMinutes == 0 -> null
        // diffMinutes < 0 : overdue
        diffMinutes > -60 -> RelativeBadge(
            text = stringResource(R.string.tasks_calendar_overdue_format, "${-diffMinutes}m"),
            color = redMedium,
        )
        else -> RelativeBadge(
            text = stringResource(R.string.tasks_calendar_overdue_format, "${-diffMinutes / 60}h"),
            color = redMedium,
        )
    }
}

@Composable
private fun DayTaskItem(
    row: TaskRowDayUi,
    relativeBadge: RelativeBadge?,
    onToggleDone: (Task, Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // 2026-05-13 : meme style que Quotidien -- tinte la row par type (orange
    // NONE / vert W/M/Y). Pas de nameBox separe dans Agenda BottomSheet,
    // donc on prend juste le bg (.first).
    val rowBg = com.example.sportapp.feature.routines.ui.components.routineTasksScreen
        .rowColorsForTask(row.task)?.first ?: appColors.bgRecessed
    val typeIcon = com.example.sportapp.feature.routines.ui.components.routineTasksScreen
        .iconForNonDailyTask(row.task)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(rowBg, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox style cohere avec le tab Daily (CustomCheckbox)
        CustomCheckbox(
            checked = row.isChecked,
            enabled = !row.task.pendingDeletion,
            pendingDeletion = row.task.pendingDeletion,
            size = 36.dp,
            iconSize = 22.dp,
            backgroundColor = Color.Transparent,
            iconTint = if (row.isChecked) mediumGreen else appColors.divider,
            onClick = { onToggleDone(row.task, !row.isChecked) },
        )

        // Titre + icone type + heure + badge sur une SEULE ligne
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.task.title,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                style = if (row.isChecked) TextStyle(textDecoration = TextDecoration.LineThrough) else TextStyle.Default,
                modifier = Modifier.weight(1f, fill = false),
            )
            // 2026-05-13 : icone type a DROITE du titre (pas a gauche). Meme
            // drawable + tint que Quotidien (cf. iconForNonDailyTask).
            typeIcon?.let { (iconRes, tint) ->
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            row.task.dueTime?.let { time ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = time,
                    color = blueMedium,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            relativeBadge?.let { badge ->
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = badge.text,
                    color = badge.color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Bouton delete avec fond rouge (style aligne avec autres boutons delete app)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(redMedium)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rounded_delete_forever),
                contentDescription = stringResource(R.string.common_delete),
                tint = appColors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        // B.1 (2026-05-12) : bouton edit (a droite du delete, fond bleu primaire)
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(appColors.primaryAction)
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rounded_edit),
                contentDescription = stringResource(R.string.common_edit),
                tint = appColors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
