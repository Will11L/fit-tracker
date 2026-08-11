package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.yellowMedium
import com.example.sportapp.core.utils.CustomDateUtils
import java.time.ZoneOffset


@Composable
fun GoalsHeader(
    isSynced: Boolean,
    allGoalDone: Boolean,
    onSyncClick: () -> Unit,
    currentWeekOffset: Int,
    onWeekChanged: (Int) -> Unit,
    onRequestResetWeek: () -> Unit
) {
    val rowHeight = 44.dp
    val iconSize = rowHeight - 16.dp

    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM")

    // 👇 Aujourd’hui en LocalDate UTC
    val today = CustomDateUtils.getNowInstant().atZone(ZoneOffset.UTC).toLocalDate()

    val startOfWeek = today
        .minusDays(today.dayOfWeek.value.toLong() - 1)
        .plusWeeks(currentWeekOffset.toLong())
    val endOfWeek = startOfWeek.plusDays(6)

    val displayedRange = "${startOfWeek.format(formatter)} to ${endOfWeek.format(formatter)}"

    Row(
        modifier = Modifier
            .height(rowHeight)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ← Back
        ActionIconButton(
            iconRes = R.drawable.ic_arrow_left_alt,
            tint = appColors.textPrimary,
            iconSize = iconSize,
            hasBackground = true,
            onClick = { onWeekChanged(currentWeekOffset - 1) },
            modifier = Modifier.size(rowHeight)
        )

        // ☁️ Synced
        ActionIconButton(
            iconRes = if (isSynced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
            tint = if (isSynced) appColors.primaryAction else yellowMedium,
            iconSize = rowHeight,
            hasBackground = false,
            onClick = onSyncClick,
            modifier = Modifier.size(iconSize)
        )

        // 🗓 Date Range dynamique
        Box(
            modifier = Modifier
                .height(rowHeight)
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    if (currentWeekOffset != 0) {
                        onRequestResetWeek()
                    }
                }
                .background(appColors.bgButton)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayedRange,
                color = appColors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }

        // → Check goals
        val iconRes = if (allGoalDone) R.drawable.ic_rounded_check else R.drawable.ic_arrow_progress
        val tint = if (allGoalDone) appColors.textPrimary else blueMedium
        val bgColor = if (allGoalDone) appColors.primaryAction else appColors.bgRecessed
        val borderColor = if (allGoalDone) appColors.primaryAction else blueMedium
        val borderWidth = if (allGoalDone) 0.dp else 1.5.dp
        val iconSizeFinal = if (allGoalDone) 24.dp else iconSize - 2.dp

        ActionIconButton(
            iconRes = iconRes,
            tint = tint,
            iconSize = iconSizeFinal,
            hasBackground = true,
            customBackgroundColor = bgColor,
            clickable = false,
            modifier = Modifier
                .size(rowHeight)
                .border(borderWidth, borderColor, shape = MaterialTheme.shapes.small)
        )

        // → Forward
        ActionIconButton(
            iconRes = R.drawable.ic_arrow_right_alt,
            tint = appColors.textPrimary,
            iconSize = iconSize,
            hasBackground = true,
            onClick = { onWeekChanged(currentWeekOffset + 1) },
            modifier = Modifier.size(rowHeight)
        )
    }
}