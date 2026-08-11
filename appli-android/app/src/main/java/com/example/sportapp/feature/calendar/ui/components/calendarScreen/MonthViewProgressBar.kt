package com.example.sportapp.feature.calendar.ui.components.calendarScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.LabeledProgressBar

@Composable
fun MonthViewProgressBar(progress: Float) {
    var showLegend by remember { mutableStateOf(false) }

    if (showLegend) {
        CalendarLegendBottomSheet(onDismiss = { showLegend = false })
    }

    LabeledProgressBar(
        progress = progress,
        showPercent = true,
        rightContent = {
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_info,
                onClick = { showLegend = true }
            )
        }
    )
}
