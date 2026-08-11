package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.LabeledProgressBar
import com.example.sportapp.designsystem.theme.appColors


@Composable
fun GoalsProgressBar(
    progress: Float,
    onMoreOptionsClick: () -> Unit
) {
    LabeledProgressBar(
        progress = progress,
        showPercent = true,
        rightContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(appColors.bgButton)
                    .clickable { onMoreOptionsClick() },
                contentAlignment = Alignment.Center
            ) {
                ActionIconButton(
                    iconRes = R.drawable.ic_rounded_more_vert,
                    tint = appColors.textPrimary,
                    hasBackground = false, // 👈 car le Box parent a déjà un fond
                    onClick = onMoreOptionsClick
                )
            }
        }
    )
}