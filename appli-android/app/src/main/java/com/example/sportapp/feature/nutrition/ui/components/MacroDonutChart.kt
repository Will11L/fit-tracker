package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.ButtonPrimaryColor
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Une part du donut : libellé déjà localisé + couleur macro + valeur (kcal) + % du total. */
data class DonutSlice(
    val label: String,
    val color: Color,
    val kcal: Float,
    val percent: Float,
)

/**
 * Donut de répartition calorique par macro (A5) — port Compose du donut web
 * (`donut-chart` DS, mode étiquettes externes) : anneau d'arcs proportionnels aux
 * kcal, total kcal au centre, et pour chaque part une **ligne de rappel** (trait
 * radial 14dp + horizontal 10dp, miroir labelLine ECharts) vers l'étiquette
 * « Nom X % » dans la couleur de la part (demande user 2026-07-14 — remplace
 * l'ex-légende latérale). Couleurs macro fournies par l'appelant.
 *
 * @param centerLabel libellé sous le grand nombre central (défaut "kcal"). Permet
 *   de réutiliser le donut pour d'autres jeux que les macros (ex. couverture micros).
 */
@Composable
fun MacroDonutChart(
    slices: List<DonutSlice>,
    centerKcal: Int,
    modifier: Modifier = Modifier,
    centerLabel: String = "kcal",
) {
    val total = slices.sumOf { it.kcal.toDouble() }.toFloat()
    val trough = appColors.bgSurface
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // Rayon extérieur : proportion du web (outer ≈ 21 % de la largeur), borné par la
            // hauteur pour laisser la place verticale des traits + étiquettes.
            val outerR = minOf(size.height * 0.38f, size.width * 0.21f)
            // Bande = même rapport rayon que le web ([0.1725, 0.21] → épaisseur 0.1786 × outer).
            val stroke = outerR * 0.1786f
            val arcR = outerR - stroke / 2f
            val topLeft = Offset(center.x - arcR, center.y - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)

            // Anneau de fond (creux) TOUJOURS dessiné, même vide (0 kcal).
            drawArc(
                color = trough,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            val leaderOut = 14.dp.toPx()
            val leaderH = 10.dp.toPx()
            val textGap = 4.dp.toPx()
            var start = -90f
            slices.forEach { slice ->
                val sweep = if (total > 0f) slice.kcal / total * 360f else 0f
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    // Ligne de rappel au milieu de la part : radiale puis horizontale
                    // vers l'extérieur, étiquette au bout (côté selon l'hémisphère).
                    val midRad = Math.toRadians((start + sweep / 2f).toDouble())
                    val dir = Offset(cos(midRad).toFloat(), sin(midRad).toFloat())
                    val p1 = center + dir * outerR
                    val p2 = center + dir * (outerR + leaderOut)
                    val right = dir.x >= 0f
                    val p3 = Offset(p2.x + (if (right) leaderH else -leaderH), p2.y)
                    drawLine(color = slice.color, start = p1, end = p2, strokeWidth = 1.dp.toPx())
                    drawLine(color = slice.color, start = p2, end = p3, strokeWidth = 1.dp.toPx())
                    val layout = textMeasurer.measure(
                        AnnotatedString("${slice.label} ${slice.percent.roundToInt()} %"),
                        style = TextStyle(color = slice.color, fontSize = 11.sp),
                    )
                    val tx = if (right) p3.x + textGap else p3.x - textGap - layout.size.width
                    drawText(layout, topLeft = Offset(tx, p3.y - layout.size.height / 2f))
                    start += sweep
                }
            }
        }
        // Centre : nombre en bleu primaire (= centerColor kcal du web), libellé en GrayBlue.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerKcal.toString(),
                color = ButtonPrimaryColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = centerLabel,
                color = GrayBlue,
                fontSize = 11.sp,
            )
        }
    }
}
