package com.example.sportapp.feature.session.ui.components.sessionTab

import androidx.compose.runtime.Composable
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.SummaryItemData
import com.example.sportapp.designsystem.common_components.SummaryRow
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium

@Composable
fun SessionSummaryRow(setsCompleted: Int, totalSets: Int, exercisesDone: Int, totalExercises: Int) {
    SummaryRow(
        items = listOf(
            SummaryItemData(
                icon = R.drawable.ic_rounded_check,
                value = "$setsCompleted/$totalSets",
                label = "Sets Done",
                iconTint = mediumGreen,
            ),
            SummaryItemData(
                icon = R.drawable.ic_arrow_progress,
                value = "$exercisesDone/$totalExercises",
                label = "Exercises",
                iconTint = orangeMedium,
            ),
        )
    )
}
