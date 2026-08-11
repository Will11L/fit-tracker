package com.example.sportapp.feature.routines.ui.components.tasksCalendarScreen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.StatusIcon
import com.example.sportapp.designsystem.theme.*

/**
 * B.3 (2026-05-12) : cellule TasksCalendarScreen, style cohera avec CalendarDay
 * (CalendarViewScreen). Top row 18dp avec icone d'etat + day number en dessous.
 *
 * Etat (priorite descendante) :
 *   - hasOverdue           : ic_rounded_close (redMedium)        -- passe non fait
 *   - isCompleted          : ic_rounded_check (mediumGreen)      -- tout fait
 *   - isInProgress         : ic_arrow_progress (orangeMedium)    -- partiel
 *   - isAllPending         : ic_arrow_progress (blueMedium)      -- rien fait, non-overdue
 *   - totalCount == 0      : (rien)
 */
@Composable
fun CalendarTaskDay(
    dayNumber: Int,
    cellSize: Dp,
    isToday: Boolean = false,
    totalCount: Int = 0,
    doneCount: Int = 0,
    hasOverdue: Boolean = false,
    onClick: () -> Unit = {},
) {
    val isCompleted = totalCount > 0 && doneCount == totalCount
    val isInProgress = totalCount > 0 && doneCount > 0 && doneCount < totalCount
    val isAllPending = totalCount > 0 && doneCount == 0 && !hasOverdue

    Column(
        modifier = Modifier
            .size(cellSize)
            .clip(MaterialTheme.shapes.small)
            .border(
                width = if (isToday) 1.5.dp else 0.dp,
                color = if (isToday) appColors.primaryAction else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                hasOverdue -> StatusIcon(R.drawable.ic_rounded_close, redMedium)
                isCompleted -> StatusIcon(R.drawable.ic_rounded_check, mediumGreen)
                isInProgress -> StatusIcon(R.drawable.ic_arrow_progress, orangeMedium)
                isAllPending -> StatusIcon(R.drawable.ic_arrow_progress, blueMedium)
                // totalCount == 0 : rien (pas d'icone)
            }
        }

        Text(
            text = dayNumber.toString(),
            fontSize = 16.sp,
            color = appColors.textTertiary,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
        )
    }
}
