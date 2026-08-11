package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Un axe du radar : libellé déjà localisé + couleur macro + valeur réelle en % de la cible. */
data class RadarAxis(
    val label: String,
    val color: Color,
    val percent: Float,
)

/**
 * Radar « profil macro : cible vs réel » (A5) — port Compose du radar web. Deux
 * tracés superposés : le réel moyen /jour sur 7 jours tracé en % de la cible
 * (rempli, couleur kcal) + la cible en repère à 100 % (trait). Axes plafonnés à
 * 120 % (le réel peut dépasser, le rayon sature). Libellés colorés par macro.
 *
 * @param axes 3-N axes (G/L/P/Fibres) ; chacun porte sa valeur réelle en % de la cible.
 * @param actualLabel / targetLabel légende des 2 tracés (localisés par l'appelant).
 */
@Composable
fun MacroRadarChart(
    axes: List<RadarAxis>,
    actualLabel: String = "",
    targetLabel: String = "",
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    showReference: Boolean = true,
    showLegend: Boolean = true,
    referencePercent: Float = 100f,
    maxPercent: Float = 120f,
) {
    if (axes.size < 3) return

    val measurer = rememberTextMeasurer()
    val maxAxis = maxPercent
    val actualColor = appColors.primaryAction
    val targetColor = appColors.textSecondary
    val gridColor = appColors.textTertiary.copy(alpha = 0.30f)
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val valueStyle = TextStyle(fontSize = 11.sp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(height).padding(28.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f
                val n = axes.size
                // Angle de l'axe i : départ en haut (-90°), sens horaire.
                fun angle(i: Int): Float = (-90f + i * 360f / n) * (Math.PI / 180f).toFloat()
                fun point(i: Int, fraction: Float): Offset {
                    val a = angle(i)
                    val r = radius * fraction.coerceIn(0f, 1f)
                    return Offset(center.x + r * cos(a), center.y + r * sin(a))
                }

                // Grille : anneaux concentriques (polygones) à 25/50/75/100 % du rayon.
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { ring ->
                    val path = Path()
                    for (i in 0 until n) {
                        val p = point(i, ring)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path, gridColor, style = Stroke(width = 1f))
                }
                // Rayons.
                for (i in 0 until n) {
                    drawLine(gridColor, center, point(i, 1f), strokeWidth = 1f)
                }

                // Anneau de repère (réel vs cible : la cible à 100 % ; répartition : la limite
                // visuelle au niveau de la macro la plus grosse), en pointillés.
                if (showReference) {
                    val targetFrac = referencePercent / maxAxis
                    val targetPath = Path()
                    for (i in 0 until n) {
                        val p = point(i, targetFrac)
                        if (i == 0) targetPath.moveTo(p.x, p.y) else targetPath.lineTo(p.x, p.y)
                    }
                    targetPath.close()
                    drawPath(
                        targetPath,
                        targetColor,
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                }

                // Tracé réel (rempli + contour).
                val actualPath = Path()
                for (i in 0 until n) {
                    val p = point(i, axes[i].percent / maxAxis)
                    if (i == 0) actualPath.moveTo(p.x, p.y) else actualPath.lineTo(p.x, p.y)
                }
                actualPath.close()
                drawPath(actualPath, actualColor.copy(alpha = 0.22f))
                drawPath(actualPath, actualColor, style = Stroke(width = 2.5f))

                // Libellés d'axe (au-delà du sommet) : nom coloré + % réel.
                axes.forEachIndexed { i, axis ->
                    val tip = point(i, 1.04f)
                    val nameLayout = measurer.measure(axis.label, labelStyle.copy(color = axis.color))
                    val valLayout = measurer.measure(
                        "${axis.percent.roundToInt()} %",
                        valueStyle.copy(color = axis.color),
                    )
                    drawText(
                        nameLayout,
                        topLeft = Offset(
                            tip.x - nameLayout.size.width / 2f,
                            tip.y - nameLayout.size.height - 1f,
                        ),
                    )
                    drawText(
                        valLayout,
                        topLeft = Offset(
                            tip.x - valLayout.size.width / 2f,
                            tip.y + 1f,
                        ),
                    )
                }
            }
        }

        if (showLegend) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                LegendDot(actualColor, actualLabel)
                LegendDot(targetColor, targetLabel)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = appColors.textSecondary, fontSize = 12.sp)
    }
}
