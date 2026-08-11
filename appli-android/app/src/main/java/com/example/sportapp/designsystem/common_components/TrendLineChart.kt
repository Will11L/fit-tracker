package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Courbe de tendance canonique (palette app), pendant « ligne » du [HealthBarChart] et
 * transposition Android du multi-line chart web (Stats). [values] = 1 slot par point daté,
 * `null` = slot sans valeur : le point est simplement ABSENT (aucune interpolation — décision
 * produit) ; une courbe fine lissée (Catmull-Rom) relie les valeurs mesurées dans l'ordre
 * chronologique. Échelle Y resserrée autour des valeurs (les faibles variations restent
 * lisibles : 74,5 vs 75,0 ne s'écrasent pas comme sur des barres partant de 0). Deux modes de
 * labels X, détectés sur `axisLabels.size == values.size` comme [HealthBarChart] : 1 label par
 * slot (ex. 7 jours) ou repères épars (ex. 30 jours). [valueFormat] formate les graduations Y
 * (défaut : 1 décimale, séparateur selon la locale in-app). [showArea] (défaut true) remplit
 * l'aire sous la courbe d'un dégradé [lineColor] → transparent en descendant (echarts areaStyle),
 * subtil : la courbe reste la vedette. [showAxis] (défaut true) affiche des **graduations Y** façon
 * multi-line chart web (sans ligne d'axe verticale) : à chaque niveau « rond » (pas nice-number), un
 * label rattaché à gauche (min, max et médiane en clair textTertiary pour dominer la lecture, autres
 * niveaux intermédiaires en GrayBlue plus foncé/discret) + une gridline horizontale pointillée légère qui
 * traverse le chart → l'œil raccroche la courbe aux valeurs. Par défaut la hauteur du chart suit la
 * largeur mesurée ([widthToHeightRatio] = 1/0,46 → hauteur = 0,46 × largeur : compact, bonne amplitude
 * sans dominer l'écran) ; [height] non-null force une hauteur fixe. [averageLine] non-null (> 0) trace
 * une ligne horizontale pointillée de moyenne (lightGrayBlue, mêmes codes que [HealthBarChart]) avec sa
 * valeur (formatée via [valueFormat]) calée en haut à droite — null = pas de ligne (ex. page Poids).
 * [emptySlotColor] non-null matérialise chaque slot vide par un petit point PLEIN (taille des points
 * de mesure) posé SUR la courbe lissée (y résolu sur le segment de Bézier au x du slot ; bords = à
 * plat sur la valeur la plus proche) — ex. jours sans pesée. [onEmptySlotClick] reçoit l'index du
 * slot vide tapé (hit-test par colonne, marqueur le plus proche, tolérance ≥ 16 dp) — ex. ouvrir la
 * saisie du jour manquant.
 * Nouvel organism (cf. UI Showcase).
 */
@Composable
fun TrendLineChart(
    values: List<Float?>,
    axisLabels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = appColors.primaryAction,
    height: Dp? = null,
    widthToHeightRatio: Float = 1f / 0.46f, // hauteur = 0,46 × largeur
    valueFormat: ((Float) -> String)? = null,
    showArea: Boolean = true,
    showAxis: Boolean = true,
    averageLine: Float? = null,
    emptySlotColor: Color? = null,
    onEmptySlotClick: ((Int) -> Unit)? = null,
    // Couleur d'un point MESURÉ selon sa valeur (null = lineColor) — ex. catégories
    // de stress (vert → rouge) ; la courbe garde lineColor.
    pointColor: ((Float) -> Color)? = null,
    // Tooltip au TAP (pendant du survol web) : cadre en overlay (en-tête = label du
    // slot, ligne = [seriesName] · valeur+suffixe) + halo sur le point tapé (re-tap =
    // masque). [tooltipLabel] = libellé du slot (défaut : label d'axe en mode
    // 1-label-par-slot, sinon vide).
    valueSuffix: String = "",
    tooltipLabel: ((Int) -> String)? = null,
    seriesName: String = "",
) {
    // Points mesurés (index de slot → valeur) : la courbe saute les slots vides.
    val points = values.mapIndexedNotNull { index, v -> v?.let { index to it } }
    if (points.isEmpty()) return
    val measured = points.map { it.second }
    val rawMin = measured.min()
    val rawMax = measured.max()
    // Fenêtre Y : marge d'au moins 0,5 autour des extrêmes (un point unique ou une série
    // plate reste centré au lieu de coller aux bords).
    val pad = ((rawMax - rawMin) * 0.15f).coerceAtLeast(0.5f)
    val lo = rawMin - pad
    val hi = rawMax + pad

    val locale = LocalConfiguration.current.locales[0]
    val format = valueFormat ?: { v: Float -> String.format(locale, "%.1f", v) }

    // Graduations « rondes » de l'axe Y (pas de step nice-number, ~4 intervalles) dans la fenêtre
    // [lo, hi] ; mesurées une fois pour réserver la gouttière gauche et dessiner les labels rattachés.
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = appColors.textTertiary)
    // Extrêmes (min/max graduations) en clair pour dominer la lecture (capturé hors DrawScope).
    val extremeLabelColor = appColors.textTertiary
    val textMeasurer = rememberTextMeasurer()
    val yTicks = remember(lo, hi) { niceTicks(lo, hi) }
    val tickStrings = yTicks.map(format)
    val tickLayouts = remember(tickStrings, labelStyle) {
        tickStrings.map { textMeasurer.measure(it, labelStyle) }
    }
    val density = LocalDensity.current
    val gutter: Dp = if (showAxis && tickLayouts.isNotEmpty()) {
        with(density) { tickLayouts.maxOf { it.size.width }.toDp() } + 8.dp
    } else {
        0.dp
    }

    // Label de la ligne de moyenne (chiffre seul, teinte de la ligne) : mesuré ici, dessiné en haut
    // à droite dans le Canvas. Absent si pas de moyenne.
    val avgStyle = MaterialTheme.typography.labelSmall.copy(color = lightGrayBlue)
    val avgLabelText = averageLine?.takeIf { it > 0f }?.let(format)
    val avgLayout = remember(avgLabelText, avgStyle) {
        avgLabelText?.let { textMeasurer.measure(it, avgStyle) }
    }

    // Sélection au tap (tooltip) : index de slot MESURÉ, remise à zéro quand la série change.
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (height != null) Modifier.height(height)
                    else Modifier.aspectRatio(widthToHeightRatio),
                )
                .pointerInput(values, gutter) {
                    // Tap : le SLOT le plus proche gagne (mêmes maths d'x que le dessin,
                    // tolérance ≥ 16 dp pour les fenêtres denses type 30 jours) — mesuré →
                    // tooltip ; vide → saisie du jour manquant ([onEmptySlotClick]).
                    detectTapGestures { offset ->
                        val plotLeft = gutter.toPx()
                        val plotWidth = size.width - plotLeft
                        val count = values.size
                        if (count == 0) return@detectTapGestures
                        fun xOf(slot: Int) = plotLeft + (slot + 0.5f) / count * plotWidth
                        val tolerance = maxOf(plotWidth / count / 2f, 16.dp.toPx())
                        val nearest = values.indices.minByOrNull { abs(offset.x - xOf(it)) }
                            ?: return@detectTapGestures
                        if (abs(offset.x - xOf(nearest)) > tolerance) {
                            selected = null
                            return@detectTapGestures
                        }
                        if (values[nearest] != null) {
                            selected = if (selected == nearest) null else nearest
                        } else {
                            selected = null
                            if (emptySlotColor != null) onEmptySlotClick?.invoke(nearest)
                        }
                    }
                },
        ) {
            val count = values.size
            if (count == 0) return@Canvas
            val plotLeft = gutter.toPx()
            val plotWidth = size.width - plotLeft
            // Padding vertical = ½ hauteur de label : la gridline extrême (min/max) n'est plus collée
            // au bord du canvas → son label reste EXACTEMENT centré dessus sans être rogné (donc sans
            // clamp qui le décalerait). Les points/courbe respirent aussi des bords.
            val vPad = if (showAxis && tickLayouts.isNotEmpty()) tickLayouts.first().size.height / 2f else 0f
            val plotBottom = size.height - vPad
            val plotHeight = plotBottom - vPad
            fun xOf(slot: Int) = plotLeft + (slot + 0.5f) / count * plotWidth
            fun yOf(v: Float) = plotBottom - (v - lo) / (hi - lo) * plotHeight

            // Graduations Y (façon multi-line web), SANS ligne d'axe verticale : à chaque niveau
            // « rond », une gridline horizontale pointillée légère qui traverse le chart + son label
            // clair rattaché à gauche. Les valeurs (min, max et intermédiaires) situent la courbe.
            if (showAxis) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                val grid = GrayBlue.copy(alpha = 0.20f)
                // Graduations « fortes » en clair : min, max ET médiane (la plus proche du centre si
                // le nombre de graduations est pair) ; les autres intermédiaires en GrayBlue plus foncé.
                val medianIndex = yTicks.size / 2
                yTicks.forEachIndexed { i, tick ->
                    val y = yOf(tick)
                    drawLine(
                        color = grid,
                        start = Offset(plotLeft, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                    val layout = tickLayouts[i]
                    val top = (y - layout.size.height / 2f)
                        .coerceIn(0f, size.height - layout.size.height)
                    // Min, max et médiane en clair (dominent la lecture) ; autres intermédiaires en
                    // GrayBlue (plus foncé, discret).
                    val labelColor = if (i == 0 || i == yTicks.lastIndex || i == medianIndex) {
                        extremeLabelColor
                    } else {
                        GrayBlue
                    }
                    drawText(
                        layout,
                        color = labelColor,
                        topLeft = Offset(plotLeft - 4.dp.toPx() - layout.size.width, top),
                    )
                }
            }

            // Courbe LISSÉE (Catmull-Rom → Bézier cubique) reliant les valeurs successives :
            // une vraie courbe fine (1,5 dp), jamais des barres. Les slots vides sont sautés.
            if (points.size > 1) {
                val pts = points.map { (slot, v) -> Offset(xOf(slot), yOf(v)) }
                val linePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[(i - 1).coerceAtLeast(0)]
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        val p3 = pts[(i + 2).coerceAtMost(pts.size - 1)]
                        // Tangentes Catmull-Rom (tension 0,5) → points de contrôle Bézier.
                        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
                        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
                    }
                }
                // Aire sous la courbe : même tracé lissé refermé jusqu'à la base, rempli d'un
                // dégradé vertical lineColor (au sommet de la courbe) → transparent (base).
                if (showArea) {
                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(pts.last().x, size.height)
                        lineTo(pts.first().x, size.height)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                            startY = pts.minOf { it.y },
                            endY = size.height,
                        ),
                    )
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            // Point discret par valeur (petit, visible même isolé) — couleur par
            // valeur via [pointColor] (ex. catégorie de stress), sinon lineColor.
            points.forEach { (slot, v) ->
                drawCircle(
                    color = pointColor?.invoke(v) ?: lineColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(xOf(slot), yOf(v)),
                )
            }
            // Points des slots vides (jours manquants) : petits points PLEINS (taille des
            // points de mesure) posés SUR la courbe lissée — le y est résolu sur le segment
            // de Bézier (mêmes points de contrôle Catmull-Rom que le tracé) au x du slot ;
            // hors de la plage mesurée, à plat sur la valeur la plus proche.
            if (emptySlotColor != null) {
                val pts = points.map { (slot, v) -> Offset(xOf(slot), yOf(v)) }
                fun cubic(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
                    val u = 1f - t
                    return u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d
                }
                fun curveY(x: Float): Float {
                    if (x <= pts.first().x) return pts.first().y
                    if (x >= pts.last().x) return pts.last().y
                    val i = pts.indices.last { pts[it].x <= x }.coerceAtMost(pts.size - 2)
                    val p0 = pts[(i - 1).coerceAtLeast(0)]
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val p3 = pts[(i + 2).coerceAtMost(pts.size - 1)]
                    val c1x = p1.x + (p2.x - p0.x) / 6f
                    val c2x = p2.x - (p3.x - p1.x) / 6f
                    val c1y = p1.y + (p2.y - p0.y) / 6f
                    val c2y = p2.y - (p3.y - p1.y) / 6f
                    // x(t) est monotone sur le segment (x mesurés croissants) → bisection sur t.
                    var lo = 0f
                    var hi = 1f
                    repeat(20) {
                        val mid = (lo + hi) / 2f
                        if (cubic(p1.x, c1x, c2x, p2.x, mid) < x) lo = mid else hi = mid
                    }
                    val t = (lo + hi) / 2f
                    return cubic(p1.y, c1y, c2y, p2.y, t)
                }
                values.indices.filter { values[it] == null }.forEach { slot ->
                    val x = xOf(slot)
                    drawCircle(
                        color = emptySlotColor,
                        radius = 3.dp.toPx(), // un poil plus gros que les points de mesure (2,5 dp)
                        center = Offset(x, curveY(x)),
                    )
                }
            }
            // Ligne de moyenne optionnelle (par-dessus la courbe) : pointillé lightGrayBlue + valeur
            // calée en haut à droite (mêmes codes que HealthBarChart).
            if (averageLine != null && averageLine > 0f) {
                val avgY = yOf(averageLine).coerceIn(0f, size.height)
                drawLine(
                    color = lightGrayBlue.copy(alpha = 0.7f),
                    start = Offset(plotLeft, avgY),
                    end = Offset(size.width, avgY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
                )
                avgLayout?.let { layout ->
                    val lx = (size.width - layout.size.width).coerceAtLeast(plotLeft)
                    val ly = (avgY - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                    drawText(layout, topLeft = Offset(lx, ly))
                }
            }
            // Halo de sélection (tooltip) : anneau autour du point tapé + guide vertical.
            selected?.let { s ->
                values[s]?.let { v ->
                    val cx = xOf(s)
                    drawLine(
                        color = lightGrayBlue.copy(alpha = 0.6f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                    drawCircle(
                        color = pointColor?.invoke(v) ?: lineColor,
                        radius = 4.5.dp.toPx(),
                        center = Offset(cx, yOf(v)),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
        // Tooltip en OVERLAY (cadre façon web : en-tête label + valeur colorée) —
        // ne décale pas la mise en page.
        selected?.let { s ->
            values[s]?.let { v ->
                val label = (tooltipLabel ?: { i: Int ->
                    if (axisLabels.size == values.size) axisLabels.getOrElse(i) { "" } else ""
                })(s)
                ChartTapTooltip(
                    label = label,
                    rows = listOf(
                        ChartTooltipRow(
                            name = seriesName.ifBlank { null },
                            valueText = format(v) + valueSuffix,
                            color = pointColor?.invoke(v) ?: lineColor,
                        ),
                    ),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        }
        Spacer(Modifier.height(6.dp))
        // Libellés d'axe X : décalés de la gouttière pour rester alignés sous les slots du chart.
        if (axisLabels.size == values.size && values.isNotEmpty()) {
            // 1 label par slot (ex. 7 jours) : centré sous chaque position.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = gutter),
            ) {
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
        } else {
            // Repères épars (ex. 30 jours) : répartis SpaceBetween.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = gutter),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                axisLabels.forEach { label ->
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

/**
 * Graduations « rondes » couvrant [lo, hi] : ~4 intervalles avec un pas nice-number (1/2/5 × 10ⁿ),
 * comme l'axe de valeur auto d'ECharts. Retourne les niveaux (croissants) tombant dans la fenêtre.
 */
private fun niceTicks(lo: Float, hi: Float): List<Float> {
    val range = hi - lo
    if (range <= 0f) return listOf(lo)
    val step = niceStep(range / 4f)
    val ticks = mutableListOf<Float>()
    var t = ceil(lo / step) * step
    var guard = 0
    while (t <= hi + step * 0.001f && guard < 32) {
        ticks.add(t)
        t += step
        guard++
    }
    return if (ticks.isEmpty()) listOf(lo, hi) else ticks
}

/** Pas « rond » (1, 2, 5 × puissance de 10) le plus proche au-dessus de [rough]. */
private fun niceStep(rough: Float): Float {
    if (rough <= 0f) return 1f
    val exp = floor(log10(rough.toDouble())).toInt()
    val base = 10.0.pow(exp).toFloat()
    val frac = rough / base
    val niceFrac = when {
        frac < 1.5f -> 1f
        frac < 3f -> 2f
        frac < 7f -> 5f
        else -> 10f
    }
    return niceFrac * base
}
