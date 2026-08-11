package com.example.sportapp.feature.planning.ui.components.weekViewScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.common_components.ProgressBarPrimitive
import com.example.sportapp.designsystem.common_components.progressColor
import com.example.sportapp.designsystem.theme.*

@Composable
fun PlannedDayProgressBar(
    label: String,
    progress: Float,
    showProgressBar: Boolean = false,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true,
    barHeight: Dp = 7.dp,
    gap: Dp = 24.dp,
    labelColor: Color = appColors.primaryAction,
    labelFontSize: Int = 14,
    troughColor: Color = appColors.bgRecessed,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val percent = (safeProgress * 100).toInt()

    val color = progressColor(safeProgress)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            fontSize = labelFontSize.sp,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Bottom)
        )

        Spacer(Modifier.width(gap))

        if(showProgressBar) {
            // ✅ Zone barre + % qui prend le reste
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // R18 : delegue au primitif partage (barHeight reste parametrable)
                ProgressBarPrimitive(
                    progress = safeProgress,
                    color = color,
                    modifier = Modifier.weight(1f),
                    height = barHeight,
                    troughColor = troughColor,
                )

                if (showPercent) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$percent%",
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
