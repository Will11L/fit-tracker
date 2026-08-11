package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.core.stats.ZoneVolumeDatum
import com.example.sportapp.core.utils.localizedZone
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.feature.onboarding.data.formatVolume
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar « Équilibre par zone (volume) » — port Compose du radar web (composant
 * DS `radar-chart`, page Stats). Un axe par zone (6 zones canoniques dans
 * l'ordre [Zones.ALL]), nom d'axe colore par zone, une unique serie remplie en
 * accent ([appColors.primaryAction]). Donne une lecture rapide de la symetrie
 * d'entrainement (volume Σ poids·reps·coef agrege sur la periode).
 *
 * Echelle partagee : tous les axes utilisent le meme max (= max des volumes,
 * marge +5 %) → les axes restent comparables. Les valeurs sont en KG canonique ;
 * l'affichage applique la conversion via [formatVolume].
 *
 * @param data volumes par zone (KG). Vide → placeholder borde (pas de radar plat).
 * @param colorMap couleur par nom de zone EN (memes tokens que les sections Stats).
 */
@Composable
fun ZoneVolumeRadarChart(
    data: List<ZoneVolumeDatum>,
    colorMap: Map<String, Color>,
    weightUnit: WeightUnit,
    emptyText: String,
    modifier: Modifier = Modifier,
    height: Dp = 280.dp,
) {
    if (data.size < 3) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, appColors.primaryAction, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emptyText, color = appColors.primaryAction, fontSize = 13.sp)
        }
        return
    }

    // `localizedZone` est @Composable (stringResource) → resolu ici, hors du
    // DrawScope du Canvas (qui n'est pas un contexte @Composable).
    val zoneLabels = data.map { localizedZone(it.zone) }

    val measurer = rememberTextMeasurer()
    val seriesColor = appColors.primaryAction
    val gridColor = appColors.textTertiary.copy(alpha = 0.30f)
    val fallbackAxisColor = appColors.textTertiary
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val valueStyle = TextStyle(fontSize = 11.sp)

    // Echelle partagee : max des volumes (+5 % de marge), minimum 1 pour eviter
    // une division par zero quand toutes les valeurs sont egales/faibles.
    val maxVolume = max(1f, data.maxOf { it.volume } * 1.05f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height).padding(30.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f
            val n = data.size
            // Angle de l'axe i : depart en haut (-90°), sens horaire.
            fun angle(i: Int): Float = (-90f + i * 360f / n) * (Math.PI / 180f).toFloat()
            fun point(i: Int, fraction: Float): Offset {
                val a = angle(i)
                val r = radius * fraction.coerceIn(0f, 1f)
                return Offset(center.x + r * cos(a), center.y + r * sin(a))
            }

            // Grille : anneaux concentriques (polygones) a 25/50/75/100 % du rayon.
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

            // Serie unique (rempli + contour) en accent.
            val seriesPath = Path()
            for (i in 0 until n) {
                val p = point(i, data[i].volume / maxVolume)
                if (i == 0) seriesPath.moveTo(p.x, p.y) else seriesPath.lineTo(p.x, p.y)
            }
            seriesPath.close()
            drawPath(seriesPath, seriesColor.copy(alpha = 0.22f))
            drawPath(seriesPath, seriesColor, style = Stroke(width = 2.5f))

            // Libelles d'axe (au-dela du sommet) : nom de zone localise + volume.
            data.forEachIndexed { i, datum ->
                val axisColor = colorMap[datum.zone] ?: fallbackAxisColor
                val tip = point(i, 1.04f)
                val nameLayout = measurer.measure(
                    zoneLabels[i],
                    labelStyle.copy(color = axisColor),
                )
                val valLayout = measurer.measure(
                    formatVolume(datum.volume, weightUnit),
                    valueStyle.copy(color = axisColor),
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
}
