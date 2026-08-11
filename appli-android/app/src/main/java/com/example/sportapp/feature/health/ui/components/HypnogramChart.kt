package com.example.sportapp.feature.health.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.feature.health.domain.HealthUiAggregations.SleepPhasePoint

/**
 * Hypnogramme « Cette nuit » : phases de sommeil en ligne en marches — x = temps
 * (du début de la 1re slice à la fin de la dernière, minuit relatif : la veille au
 * soir en négatif), y = 4 niveaux, ÉVEILLÉ EN HAUT → PROFOND EN BAS (lecture
 * classique : le profond domine en début de nuit). Aspect CRÉNEAU : paliers et
 * montées de même épaisseur, bouts carrés ; chaque palier est coloré par famille
 * ([phaseColors], mêmes teintes que la légende des phases 7 j) et chaque montée
 * est un dégradé vertical entre les couleurs des deux paliers. Libellés d'heure
 * début / milieu / fin sous le chart. Chart « nu » (fond = cadre appelant).
 */
@Composable
fun HypnogramChart(
    points: List<SleepPhasePoint>,
    phaseColors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
) {
    if (points.isEmpty()) return
    val minX = points.minOf { it.startMin }
    val maxX = points.maxOf { it.startMin + it.minutes }
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val span = (maxX - minX).coerceAtLeast(1)
            fun xOf(min: Int) = (min - minX).toFloat() / span * size.width
            // bucket 0 (profond) en bas … 3 (éveillé) en haut : 4 pistes réparties
            // avec une ½ piste de marge haut/bas.
            fun yOf(bucket: Int) = size.height * (1f - (bucket + 0.5f) / 4f)
            val fallback = lightGrayBlue
            // MÊME épaisseur pour paliers et montées + bouts CARRÉS (Butt) : aspect
            // créneau — un éveil bref reste un créneau fin, jamais un gros point rond.
            val stroke = 2.5.dp.toPx()
            points.forEachIndexed { index, p ->
                val y = yOf(p.bucket)
                val color = phaseColors.getOrElse(p.bucket) { fallback }
                if (index > 0) {
                    val prev = points[index - 1]
                    // Montée seulement si les slices sont contiguës (écart ≤ pont) : un
                    // vrai trou (> 5 min) reste une rupture sans montée (données absentes).
                    val gap = p.startMin - (prev.startMin + prev.minutes)
                    if (prev.bucket != p.bucket && gap <= BRIDGE_MIN) {
                        // Montée/descente : trait FIN (l'épaisseur reste celle de l'ancien
                        // connecteur) en dégradé vertical ADOUCI — couleurs des deux paliers
                        // tirées à mi-chemin vers le gris discret d'avant (entre-deux).
                        val prevY = yOf(prev.bucket)
                        val prevColor = phaseColors.getOrElse(prev.bucket) { fallback }
                        val topY = minOf(prevY, y)
                        val bottomY = maxOf(prevY, y)
                        val topColor = lerp(if (prevY < y) prevColor else color, fallback, 0.5f)
                        val bottomColor = lerp(if (prevY < y) color else prevColor, fallback, 0.5f)
                        val x = xOf(p.startMin)
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(topColor, bottomColor),
                                startY = topY,
                                endY = bottomY,
                            ),
                            start = Offset(x, topY),
                            end = Offset(x, bottomY),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                // Fin de palier PONTÉE jusqu'au début de la slice suivante quand l'écart
                // est petit (arrondis à la minute de l'import + micro-trous HC) : le
                // palier rejoint exactement la montée, plus de coins décrochés ni de
                // petits trous horizontaux. Un vrai trou (> pont) reste une rupture.
                val end = p.startMin + p.minutes
                val next = points.getOrNull(index + 1)
                val bridgedEnd = if (next != null && next.startMin - end in 0..BRIDGE_MIN) next.startMin else end
                drawLine(
                    color = color,
                    start = Offset(xOf(p.startMin), y),
                    end = Offset(xOf(bridgedEnd), y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Butt,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Repères d'heure : début / milieu / fin de la fenêtre.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(minX, (minX + maxX) / 2, maxX).forEach { min ->
                Text(
                    text = hhmmOfRelative(min),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Écart max (min) ponté entre deux slices : au-delà = vraie rupture (données absentes). */
private const val BRIDGE_MIN = 5

/** "HH:MM" d'une minute relative à minuit du jour de réveil (négatif = la veille). */
private fun hhmmOfRelative(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}
