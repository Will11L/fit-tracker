package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.stats.ChartGranularity
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import java.time.LocalDate
import kotlin.math.ceil

/** Une ligne du graphe « Toutes les macros » : sa couleur + ses valeurs en % par bucket. */
data class AllMacroLine(val color: Color, val percents: List<Float>)

/**
 * Graphe « Toutes les macros » des Stats nutrition : une ligne par macro,
 * valeur = % du consommé vs l'objectif (comparable entre macros), + une ligne de
 * référence pointillée à 100 %. Custom Compose/Canvas, même langage visuel que
 * [NutritionMacroChart] (fond bgRecessed, guidelines pointillées, axe Y lightGrayBlue).
 * Miroir du graphe « % des macros vs objectif » du web.
 */
@Composable
fun NutritionAllMacrosChart(
    buckets: List<String>,
    lines: List<AllMacroLine>,
    granularity: ChartGranularity,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
) {
    val frameModifier = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(8.dp))
        .background(appColors.bgRecessed)

    if (buckets.isEmpty() || lines.all { line -> line.percents.all { it <= 0f } }) {
        Box(
            modifier = frameModifier
                .border(1.5.dp, appColors.primaryAction, RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                text = stringResource(R.string.nutrition_stats_chart_empty_logged),
                color = appColors.primaryAction,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    // Échelle Y en % : au moins 100 (la référence d'objectif) ou le max atteint.
    val maxPct = lines.flatMap { it.percents }.maxOrNull() ?: 100f
    val maxVal = maxOf(100f, maxPct).coerceAtLeast(1f)
    val yTicks = listOf(0f, maxVal / 3f, maxVal * 2f / 3f, maxVal)

    val n = buckets.size
    val labelStep = ceil(n / 8f).toInt().coerceAtLeast(1)
    // Hoisté hors du Canvas (accesseur @Composable interdit dans un DrawScope).
    val refColor = appColors.textSecondary

    Box(modifier = frameModifier.padding(start = 4.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Axe Y (% alignés sur les ticks, max en haut).
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yTicks.reversed().forEach { tick ->
                    androidx.compose.material3.Text(
                        text = "${tick.toInt()}%",
                        color = lightGrayBlue,
                        fontSize = 9.sp,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        fun cx(i: Int) = if (n <= 1) w / 2f else (i + 0.5f) / n * w
                        fun cy(v: Float) = h * (1f - (v / maxVal).coerceIn(0f, 1f))

                        // Guidelines horizontales + axe Y.
                        yTicks.forEach { tick ->
                            val y = cy(tick)
                            drawLine(
                                color = lightGrayBlue.copy(alpha = 0.40f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)),
                            )
                        }
                        drawLine(
                            color = lightGrayBlue.copy(alpha = 0.55f),
                            start = Offset(0f, 0f),
                            end = Offset(0f, h),
                            strokeWidth = 1.2f,
                        )

                        // Ligne de référence à 100 % (objectif atteint).
                        val refY = cy(100f)
                        drawLine(
                            color = refColor,
                            start = Offset(0f, refY),
                            end = Offset(w, refY),
                            strokeWidth = 2f.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                        )

                        // Une polyligne par macro, dans sa couleur.
                        lines.forEach { line ->
                            val path = Path()
                            line.percents.forEachIndexed { i, v ->
                                val x = cx(i)
                                val y = cy(v)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, line.color, style = Stroke(width = 2.5.dp.toPx()))
                            line.percents.forEachIndexed { i, v ->
                                drawCircle(line.color, 2.5.dp.toPx(), Offset(cx(i), cy(v)))
                            }
                        }
                    }
                }

                // ── Labels X (un sur labelStep pour éviter le tassement).
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    buckets.forEachIndexed { i, bucket ->
                        androidx.compose.material3.Text(
                            text = if (i % labelStep == 0) formatAllBucketLabel(bucket, granularity) else "",
                            color = lightGrayBlue,
                            fontSize = 9.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Étiquette X : 'YYYY-MM-DD' → 'd/M' (jour) ou 'YYYY-WW' → 'W##' (semaine). */
private fun formatAllBucketLabel(bucket: String, gran: ChartGranularity): String = when (gran) {
    ChartGranularity.DAILY -> {
        val parsed = runCatching { LocalDate.parse(bucket) }.getOrNull()
        if (parsed != null) "${parsed.dayOfMonth}/${parsed.monthValue}" else bucket
    }
    ChartGranularity.WEEKLY -> {
        val week = bucket.substringAfterLast('-').toIntOrNull()
        if (week != null) "W$week" else bucket
    }
}
