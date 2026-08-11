package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium

/**
 * D4 (2026-05-13) : helpers partages pour styler les tasks par type
 * (DAILY / NONE / WEEKLY+MONTHLY+YEARLY). Utilises par Quotidien
 * (RoutineTasksScreen) ET Agenda BottomSheet (DayTasksBottomSheet).
 *
 * - NONE (ponctuelle one-off) -> teinte orange (taskRowOrange*,
 *   icone ic_calendar_today orangeMedium)
 * - WEEKLY/MONTHLY/YEARLY (ponctuelle reguliere) -> teinte verte
 *   (taskRowGreen*, icone ic_rounded_repeat mediumGreen)
 * - DAILY -> null (couleurs/icone par defaut, drag handle)
 *
 * Les surfaces teintees sont des tokens AppColors (declines light/dark).
 */

/** Retourne Pair(backgroundColor, nameBoxColor) ou null si DAILY. */
@Composable
fun rowColorsForTask(task: Task): Pair<Color, Color>? = when (task.recurrenceKind) {
    "NONE" -> appColors.taskRowOrangeBg to appColors.taskRowOrangeNameBox
    "WEEKLY", "MONTHLY", "YEARLY" -> appColors.taskRowGreenBg to appColors.taskRowGreenNameBox
    else -> null
}

/** Retourne Pair(iconRes, tint) ou null si DAILY. */
fun iconForNonDailyTask(task: Task): Pair<Int, Color>? = when (task.recurrenceKind) {
    "NONE" -> R.drawable.ic_calendar_today to orangeMedium
    "WEEKLY", "MONTHLY", "YEARLY" -> R.drawable.ic_rounded_repeat to mediumGreen
    else -> null
}
