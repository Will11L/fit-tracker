package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

/**
 * Anneau de progression circulaire (miroir du `ProgressRing` du design system web) :
 * piste + arc de progression à bouts arrondis, valeur au centre ([label]) et
 * sous-titre optionnel ([sublabel], ex. la cible « / 220 g »). Progression bornée
 * 0..1. Utilisé par le bandeau nutrition (mode anneaux : un anneau par macro).
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    size: Dp = 58.dp,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = appColors.bgSurface,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // lineHeight serrées : valeur et sous-titre directement l'un sous l'autre
        // (parité avec le ProgressRing web, sans interligne parasite).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = appColors.textPrimary,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            sublabel?.let {
                Text(
                    text = it,
                    color = appColors.textSecondary,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
