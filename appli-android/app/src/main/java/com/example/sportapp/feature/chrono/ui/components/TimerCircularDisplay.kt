package com.example.sportapp.feature.chrono.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.feature.chrono.domain.TimerStateMachine
import com.example.sportapp.designsystem.theme.appColors

/**
 * Affichage circulaire du timer en style cadran d'horloge :
 * `segmentCount` barres radiales partant du bord vers l'intérieur, qui
 * s'éteignent dans le sens horaire à mesure que le temps restant diminue.
 *
 * - IDLE / duration > 0 : tous segments allumés (pleine charge).
 * - RUNNING / PAUSED : nombre de segments allumés ∝ remaining / duration.
 * - FINISHED : tous éteints + pulse infini de tout l'anneau (effet "ding").
 * - duration <= 0 : tous éteints (état "no preset selected").
 *
 * Variant mini : diminuer diameter/barLength/barWidth + réduire segmentCount
 * pour rester lisible (60 segments à 40dp diamètre = bruit visuel).
 * `showText = false` cache le texte central (utile pour mini overlay).
 */
@Composable
fun TimerCircularDisplay(
    remainingMillis: Long,
    durationMillis: Long,
    state: TimerStateMachine.State,
    diameter: Dp = 260.dp,
    barLength: Dp = 18.dp,
    barWidth: Dp = 3.dp,
    segmentCount: Int = 60,
    centerText: String? = null,
    centerTextStyle: TextStyle = MaterialTheme.typography.displaySmall,
    centerTextColor: Color = appColors.primaryAction,
    modifier: Modifier = Modifier,
) {
    val progress: Float = when {
        state == TimerStateMachine.State.FINISHED -> 0f
        state == TimerStateMachine.State.IDLE && durationMillis > 0L -> 1f
        durationMillis <= 0L -> 0f
        else -> (remainingMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }
    val litCount: Int = (progress * segmentCount).toInt().coerceIn(0, segmentCount)

    // Pulse infini quand FINISHED : alpha cycle 0f → 1f → 0f (~800ms aller-retour).
    val infinite = rememberInfiniteTransition(label = "timer-finished-pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )

    val onColor = appColors.primaryAction
    val offColor = appColors.primaryAction.copy(alpha = 0.15f)

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val barWidthPx = with(density) { barWidth.toPx() }
        val barLengthPx = with(density) { barLength.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerRadius = size.minDimension / 2f
            val innerRadius = outerRadius - barLengthPx
            val angleStep = 360f / segmentCount

            for (i in 0 until segmentCount) {
                // i=0 dessiné au-dessus du centre (12h), rotation +angleStep° clockwise par i.
                val angleDeg = i * angleStep
                rotate(angleDeg, pivot = Offset(cx, cy)) {
                    // Décompte sens horaire : segments à 12h s'éteignent en premier
                    // → lit si l'index est dans la queue, soit i >= (segmentCount - litCount).
                    val color = when (state) {
                        TimerStateMachine.State.FINISHED -> onColor.copy(alpha = pulseAlpha)
                        else -> if (i >= segmentCount - litCount) onColor else offColor
                    }
                    drawLine(
                        color = color,
                        start = Offset(cx, cy - outerRadius),
                        end = Offset(cx, cy - innerRadius),
                        strokeWidth = barWidthPx,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        if (centerText != null) {
            // À FINISHED, le texte central pulse avec le même alpha que les segments
            // (effet "ding" homogène entre l'anneau et le chiffre).
            val displayedColor = if (state == TimerStateMachine.State.FINISHED) {
                centerTextColor.copy(alpha = pulseAlpha)
            } else {
                centerTextColor
            }
            Text(
                text = centerText,
                style = centerTextStyle,
                color = displayedColor
            )
        }
    }
}
