package com.example.sportapp.feature.calendar.ui.components.calendarScreen

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.SummaryItemData
import com.example.sportapp.designsystem.common_components.SummaryRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CalendarSummaryRow(perfectWeeksTotal: Int, completedDays: Int, nextWorkoutDate: LocalDate?) {
    val locale = LocalConfiguration.current.locales[0]
    SummaryRow(
        modifier = Modifier.padding(vertical = 12.dp),
        compact = true,
        items = listOf(
            SummaryItemData(
                icon = R.drawable.ic_rounded_local_fire,
                value = stringResource(R.string.calendar_summary_weeks, perfectWeeksTotal),
                label = stringResource(R.string.calendar_summary_streak),
                iconTint = orangeMedium,
            ),
            SummaryItemData(
                icon = R.drawable.ic_rounded_check_circle,
                value = stringResource(R.string.calendar_summary_days, completedDays),
                label = stringResource(R.string.calendar_summary_done),
                iconTint = mediumGreen,
            ),
            SummaryItemData(
                icon = R.drawable.ic_calendar_month,
                value = nextWorkoutDate?.format(DateTimeFormatter.ofPattern("d MMM", locale))
                    ?: stringResource(R.string.calendar_summary_next_workout_none),
                label = stringResource(R.string.calendar_summary_next_workout),
                iconTint = appColors.accentText,
            ),
        )
    )
}
