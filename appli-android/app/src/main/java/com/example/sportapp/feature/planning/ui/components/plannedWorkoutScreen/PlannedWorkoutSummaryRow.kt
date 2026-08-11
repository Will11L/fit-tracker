package com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen

import androidx.compose.runtime.Composable
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.SummaryItemData
import com.example.sportapp.designsystem.common_components.SummaryRow
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium

@Composable
fun PlannedWorkoutSummaryRow(
    totalSets: Int,
    totalReps: Int
) {
    SummaryRow(
        items = listOf(
            SummaryItemData(
                icon = R.drawable.ic_rounded_check,
                value = totalSets.toString(),
                label = "Total Sets",
                iconTint = mediumGreen,
            ),
            SummaryItemData(
                icon = R.drawable.ic_rounded_refresh,
                value = totalReps.toString(),
                label = "Total Reps",
                iconTint = orangeMedium,
            ),
        )
    )
}
