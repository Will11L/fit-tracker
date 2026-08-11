package com.example.sportapp.feature.health.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ChartTapTooltip
import com.example.sportapp.designsystem.common_components.ChartTooltipRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import kotlin.math.roundToInt

/**
 * Tendance 7 jours combinée « Distance & calories » (miroir de `energy-week-chart`
 * web) : barres = calories d'activité (couleur de section, ¼ du slot, coins arrondis,
 * ligne pointillée de moyenne) + courbe lissée superposée = distance ([lineColor],
 * échelle indépendante, jours vides = point absent, jamais interpolé). Quantième du
 * jour sous chaque slot. Chart « nu » (fond = cadre appelant).
 */
@Composable
fun EnergyWeekChart(
    kcal: List<Float>,
    distance: List<Float?>,
    axisLabels: List<String>,
    barColor: Color,
    lineColor: Color = appColors.textPrimary,
    averageKcal: Float? = null,
    height: Dp = 110.dp,
    // Unité de la distance ('m' / 'km') pour le tooltip au tap.
    distanceUnit: String = "m",
) {
    val locale = LocalConfiguration.current.locales[0]
    // Sélection au tap (tooltip façon web) : index de slot, remise à zéro au changement de série.
    var selected by remember(kcal, distance) { mutableStateOf<Int?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(kcal, distance) {
                    detectTapGestures { offset ->
                        val count = kcal.size
                        if (count == 0) return@detectTapGestures
                        val slot = (offset.x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)
                        val hasData = kcal.getOrElse(slot) { 0f } > 0f || distance.getOrNull(slot) != null
                        selected = if (selected == slot || !hasData) null else slot
                    }
                },
        ) {
            val count = kcal.size
            if (count == 0) return@Canvas
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.25f).coerceAtLeast(1f) // ¼ du slot (rapport commun)
            val radiusPx = minOf(2.dp.toPx(), barWidth / 2f)
            val radius = CornerRadius(radiusPx, radiusPx)
            val maxKcal = (kcal.maxOrNull() ?: 0f).coerceAtLeast(1f)
            // Barres kcal (pas de piste : seules les barres pleines se voient sur le cadre).
            kcal.forEachIndexed { index, value ->
                if (value <= 0f) return@forEachIndexed
                val h = ((value / maxKcal) * size.height).coerceAtLeast(1.dp.toPx())
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }
            // Ligne pointillée de moyenne kcal (mêmes codes que HealthBarChart).
            if (averageKcal != null && averageKcal > 0f) {
                val y = size.height - (averageKcal / maxKcal).coerceIn(0f, 1f) * size.height
                drawLine(
                    color = lightGrayBlue.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
                )
            }
            // Courbe distance à ÉCHELLE INDÉPENDANTE : fenêtre resserrée autour des
            // valeurs (comme TrendLineChart), points aux centres de slots, slots vides sautés.
            val points = distance.mapIndexedNotNull { index, v -> v?.let { index to it } }
            if (points.isNotEmpty()) {
                val values = points.map { it.second }
                val rawMin = values.min()
                val rawMax = values.max()
                val pad = ((rawMax - rawMin) * 0.15f).coerceAtLeast(0.5f)
                val lo = rawMin - pad
                val hi = rawMax + pad
                fun xOf(slot: Int) = (slot + 0.5f) / count * size.width
                fun yOf(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
                if (points.size > 1) {
                    val pts = points.map { (slot, v) -> Offset(xOf(slot), yOf(v)) }
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 0 until pts.size - 1) {
                            val p0 = pts[(i - 1).coerceAtLeast(0)]
                            val p1 = pts[i]
                            val p2 = pts[i + 1]
                            val p3 = pts[(i + 2).coerceAtMost(pts.size - 1)]
                            val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
                            val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
                            cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                points.forEach { (slot, v) ->
                    drawCircle(color = lineColor, radius = 2.5.dp.toPx(), center = Offset(xOf(slot), yOf(v)))
                }
            }
            // Guide de sélection (tooltip) : trait pointillé vertical sur le slot tapé.
            selected?.let { s ->
                val x = s * slotWidth + slotWidth / 2f
                drawLine(
                    color = lightGrayBlue.copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
        }
        // Tooltip en OVERLAY (cadre façon web) : une ligne par mesure présente
        // (Activité kcal + Distance), en-tête = quantième du jour.
        selected?.let { s ->
            val rows = buildList {
                kcal.getOrNull(s)?.takeIf { it > 0f }?.let {
                    add(
                        ChartTooltipRow(
                            name = stringResource(R.string.health_dash_cal_active_label),
                            valueText = String.format(locale, "%,d", it.roundToInt()) + " kcal",
                            color = barColor,
                        ),
                    )
                }
                distance.getOrNull(s)?.let {
                    add(
                        ChartTooltipRow(
                            name = stringResource(R.string.health_dash_distance_label),
                            valueText = String.format(locale, "%,d", it.roundToInt()) + " " + distanceUnit,
                            color = lineColor,
                        ),
                    )
                }
            }
            if (rows.isNotEmpty()) {
                ChartTapTooltip(
                    label = axisLabels.getOrElse(s) { "" },
                    rows = rows,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        }
        Spacer(Modifier.height(8.dp))
        // Quantième du jour centré sous chaque slot (mêmes codes que la variante 7 jours).
        Row(modifier = Modifier.fillMaxWidth()) {
            axisLabels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        color = appColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
