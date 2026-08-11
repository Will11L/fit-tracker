package com.example.sportapp.feature.session.ui.components.sessionTab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.LabeledProgressBar
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.yellowMedium

@Composable
fun SessionTabProgressBar(
    progress: Float,
    isSync: Boolean,
    isDone: Boolean,
    onSyncClick: () -> Unit,
    onAddClick: () -> Unit,
    onToggleDoneClick: () -> Unit,
) {
    LabeledProgressBar(
        progress = progress,
        showPercent = true,
        rightContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                // ☁️ Sync
                ActionIconButton(
                    iconRes = if (isSync) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                    tint = if (isSync) appColors.primaryAction else yellowMedium,
                    hasBackground = false,
                    onClick = onSyncClick,
                )

                // ✅ Done / In progress
                ActionIconButton(
                    iconRes = if (isDone) R.drawable.ic_rounded_check else R.drawable.ic_arrow_progress,
                    tint = if (isDone) mediumGreen else blueMedium,
                    hasBackground = false,
                    onClick = onToggleDoneClick
                )

                // ➕ Add
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    onClick = onAddClick
                )
            }
        }
    )
}
