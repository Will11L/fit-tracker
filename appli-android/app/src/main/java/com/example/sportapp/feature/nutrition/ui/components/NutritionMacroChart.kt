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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.sportapp.core.stats.ChartType
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil

/**
 * Graphe d'une macro des Stats nutrition (A6) : 1 série « consommé » (barres OU
 * courbe selon [chartType], couleur de la macro) + 1 ligne de cible pointillée
 * optionnelle. Custom Compose/Canvas (pas de Vico) — une seule série + cible, ça
 * reste compact et auto-suffisant. Buckets X 'YYYY-MM-DD' → 'J/M' (DAILY) ou
 * 'YYYY-WW' → 'W##' (WEEKLY). Style aligné sur le bar chart custom des Stats sport
 * (fond bgRecessed, guidelines pointillées, axe Y lightGrayBlue).
 */
@Composable
fun NutritionMacroChart(
    buckets: List<String>,
    consumed: List<Float>,
    target: List<Float>,
    color: Color,
    chartType: ChartType,
    granularity: ChartGranularity,
    unit: String,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
) {
    val frameModifier = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(8.dp))
        .background(appColors.bgRecessed)

    // États vides (miroir web) : pas de bucket, ou aucun aliment saisi.
    val emptyMessage = when {
        buckets.isEmpty() -> stringResource(R.string.nutrition_stats_chart_empty)
        consumed.all { it <= 0f } -> stringResource(R.string.nutrition_stats_chart_empty_logged)
        else -> null
    }
    if (emptyMessage != null) {
        Box(
            modifier = frameModifier
                .border(1.5.dp, appColors.primaryAction, RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                text = emptyMessage,
                color = appColors.primaryAction,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val hasTarget = target.isNotEmpty() && target.any { it > 0f }
    val maxVal = (consumed.maxOrNull() ?: 0f)
        .let { c -> if (hasTarget) maxOf(c, target.maxOrNull() ?: 0f) else c }
        .coerceAtLeast(1f)
    val yTicks = listOf(0f, maxVal / 3f, maxVal * 2f / 3f, maxVal)

    val targetColor = appColors.textSecondary
    val n = buckets.size
    val labelStep = ceil(n / 8f).toInt().coerceAtLeast(1)

    Box(modifier = frameModifier.padding(start = 4.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Axe Y (labels alignés sur les ticks, max en haut).
            Column(
                modifier = Modifier
                    .width(30.dp)
                    .fillMaxHeight()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yTicks.reversed().forEach { tick ->
                    androidx.compose.material3.Text(
                        text = formatAxis(tick),
                        color = lightGrayBlue,
                        fontSize = 9.sp,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // ── Zone de tracé.
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        fun cx(i: Int) = (i + 0.5f) / n * w
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

                        when (chartType) {
                            ChartType.BAR -> {
                                val slot = w / n
                                val barW = slot * 0.55f
                                val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                consumed.forEachIndexed { i, v ->
                                    val top = cy(v)
                                    val barH = (h - top).coerceAtLeast(0f)
                                    if (barH <= 0f) return@forEachIndexed
                                    drawRoundRect(
                                        color = color,
                                        topLeft = Offset(cx(i) - barW / 2f, top),
                                        size = Size(barW, barH),
                                        cornerRadius = radius,
                                    )
                                }
                            }
                            ChartType.LINE -> {
                                val stroke = Path()
                                val area = Path()
                                consumed.forEachIndexed { i, v ->
                                    val x = cx(i)
                                    val y = cy(v)
                                    if (i == 0) {
                                        stroke.moveTo(x, y)
                                        area.moveTo(x, h); area.lineTo(x, y)
                                    } else {
                                        stroke.lineTo(x, y)
                                        area.lineTo(x, y)
                                    }
                                }
                                area.lineTo(cx(n - 1), h)
                                area.close()
                                drawPath(area, color.copy(alpha = 0.12f))
                                drawPath(stroke, color, style = Stroke(width = 2.5.dp.toPx()))
                                consumed.forEachIndexed { i, v ->
                                    drawCircle(color, 3.dp.toPx(), Offset(cx(i), cy(v)))
                                }
                            }
                        }

                        // Ligne de cible pointillée (par-dessus), si une cible existe.
                        if (hasTarget) {
                            val tp = Path()
                            target.forEachIndexed { i, v ->
                                val x = cx(i)
                                val y = cy(v)
                                if (i == 0) tp.moveTo(x, y) else tp.lineTo(x, y)
                            }
                            drawPath(
                                tp,
                                targetColor,
                                style = Stroke(
                                    width = 2f.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                                ),
                            )
                        }
                    }
                }

                // ── Labels X (un par bucket, n'affiche qu'un sur labelStep pour éviter le tassement).
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    buckets.forEachIndexed { i, bucket ->
                        androidx.compose.material3.Text(
                            text = if (i % labelStep == 0) formatBucketLabel(bucket, granularity) else "",
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
private fun formatBucketLabel(bucket: String, gran: ChartGranularity): String = when (gran) {
    ChartGranularity.DAILY -> {
        val parsed = runCatching { LocalDate.parse(bucket) }.getOrNull()
        if (parsed != null) "${parsed.dayOfMonth}/${parsed.monthValue}" else bucket
    }
    ChartGranularity.WEEKLY -> {
        val week = bucket.substringAfterLast('-').toIntOrNull()
        if (week != null) "W$week" else bucket
    }
}

/** Valeur d'axe Y compacte : entier, suffixe 'k' au-delà de 10 000 (kcal hebdo). */
private fun formatAxis(value: Float): String = when {
    value <= 0f -> "0"
    value >= 10_000f -> String.format(Locale.US, "%.0fk", value / 1000f)
    value >= 1_000f -> String.format(Locale.US, "%.1fk", value / 1000f)
    else -> value.toInt().toString()
}
