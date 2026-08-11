package com.example.sportapp.designsystem.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGreen
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun DrawerMiniProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    barWidth: Dp = 60.dp,       // tweak si besoin
    barHeight: Dp = 6.dp,       // tweak si besoin
    showPercent: Boolean = true,
    troughColor: Color = appColors.bgSurface, // boxBlue : visible sur container bgRecessed du DrawerSection (cf. pattern ProgressBarPrimitive)
) {
    val p = progress.coerceIn(0f, 1f)
    val pct = (p * 100).toInt()
    val primaryAction = appColors.primaryAction

    fun progressColor(value: Float): Color = when {
        value >= 1f -> primaryAction
        value >= 0.75f -> mediumGreen
        value >= 0.5f -> lightGreen
        value >= 0.2f -> orangeMedium
        else -> redMedium
    }

    val color = progressColor(p)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // mini bar (même style que LabeledProgressBar)
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .background(troughColor, shape = RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(p)
                    .background(color, shape = RoundedCornerShape(2.dp))
            )
        }

        if (showPercent) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$pct%",
                color = color,                  // ✅ comme LabeledProgressBar (pas gris)
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
