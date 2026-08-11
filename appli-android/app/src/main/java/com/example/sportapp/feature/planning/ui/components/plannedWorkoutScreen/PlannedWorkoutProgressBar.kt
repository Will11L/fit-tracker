package com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.LabeledProgressBar
import androidx.compose.ui.graphics.Color
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.yellowMedium

@Composable
fun PlannedWorkoutProgressBar(
    progress: Float = 0f,
    isSynced: Boolean = true,
    onSyncClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    LabeledProgressBar(
        progress = progress,
        showPercent = true,
        rightContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionIconButton(
                    iconRes = if (isSynced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                    tint = if (isSynced) appColors.primaryAction else yellowMedium,
                    hasBackground = false,
                    onClick = onSyncClick,
                )
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    onClick = onAddClick
                )
            }
        }
    )
}
