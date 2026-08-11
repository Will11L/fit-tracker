package com.example.sportapp.feature.health.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.common_components.ChartTapTooltip
import com.example.sportapp.designsystem.common_components.ChartTooltipRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import kotlin.math.roundToInt

/**
 * Chart en barres générique (palette app). [values] = une barre par valeur (0 =
 * pas de barre, piste discrète) ; [axisLabels] = libellés d'axe. Deux modes,
 * détectés sur `axisLabels.size == values.size` : **1 label par barre** (ex. 7 jours,
 * lettre centrée sous chaque barre + tick sous chaque barre) ou **repères épars**
 * (ex. intraday 48 barres avec 5 labels 0/6/12/18/24 h → ticks aux fractions 0..1).
 * Réutilisé pour pas / FC / sommeil, vues « aujourd'hui » (30 min) et « 7 jours ».
 * Nouvel organism (cf. UI Showcase).
 *
 * Barres fines à coins arrondis : chaque valeur occupe un « slot » plein-largeur, la
 * barre est dessinée au quart du slot et centrée (espacement global constant). Un
 * **axe de base optionnel** (ligne + petits traits sortants sous les repères) ancre le
 * chart quand [axisColor] est non-null ; [axisColor] = null → aucun axe (ex. variante
 * 7 jours, seuls les chiffres du jour restent sous les barres). [background] /
 * [trackColor] / [contentPadding] sont surchargeables pour intégrer le chart « nu »
 * dans un cadre compact (ex. cadre thirdBlue du hub). [averageLine] non-null trace une
 * ligne horizontale pointillée (repère de moyenne) par-dessus les barres (ex. moyenne
 * 7 jours) ; null = pas de ligne (défaut, ne s'impose pas à l'intraday).
 */
@Composable
fun HealthBarChart(
    values: List<Float>,
    axisLabels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = appColors.primaryAction,
    height: Dp = 110.dp,
    background: Color = appColors.bgScreen,
    trackColor: Color = appColors.bgRecessed,
    axisColor: Color? = appColors.textTertiary,
    averageLine: Float? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    // Mode empilé optionnel : chaque barre = segments colorés du bas vers le haut
    // (ex. phases de sommeil profond/léger/paradoxal/éveillé). Non-null → prime sur
    // [values]/[barColor] ; [stackColors] par index de segment.
    stackedValues: List<List<Float>>? = null,
    stackColors: List<Color> = emptyList(),
    // Tooltip au TAP (pendant du survol web) : cadre en overlay (en-tête = label du
    // slot, ligne = [seriesName] · valeur+suffixe — mode empilé : UNE LIGNE PAR
    // SEGMENT nommé [stackLabels], couleurs [stackColors]) + guide vertical sur la
    // barre tapée (re-tap = masque). [tooltipLabel] = libellé du slot (défaut :
    // label d'axe en mode 1-label-par-barre, sinon vide).
    valueSuffix: String = "",
    tooltipLabel: ((Int) -> String)? = null,
    seriesName: String = "",
    stackLabels: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(contentPadding),
    ) {
        // Mode empilé : la hauteur de barre = somme des segments.
        val totals = stackedValues?.map { it.sum() } ?: values
        val max = (totals.maxOrNull() ?: 0f).coerceAtLeast(1f)
        // 1 label par barre (ex. 7 jours) vs repères épars (ex. intraday 0/6/.../24 h).
        val perBar = axisLabels.size == totals.size && totals.isNotEmpty()
        // Label de la ligne de moyenne : mesuré ici, dessiné dans le Canvas près de la ligne (même
        // teinte que la ligne). Juste la valeur brute arrondie + séparateur de milliers selon la locale.
        val avgTextMeasurer = rememberTextMeasurer()
        val avgStyle = MaterialTheme.typography.labelSmall.copy(color = lightGrayBlue)
        val locale = LocalConfiguration.current.locales[0]
        val avgLabel = String.format(locale, "%,d", (averageLine ?: 0f).roundToInt())
        val avgLayout = remember(avgLabel, avgStyle) { avgTextMeasurer.measure(avgLabel, avgStyle) }
        // Sélection au tap (tooltip) : index de barre, remise à zéro quand la série change.
        var selected by remember(totals) { mutableStateOf<Int?>(null) }
        Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(totals) {
                    detectTapGestures { offset ->
                        val count = totals.size
                        if (count == 0) return@detectTapGestures
                        val slot = (offset.x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)
                        selected = if (selected == slot || totals[slot] <= 0f) null else slot
                    }
                },
        ) {
            val count = totals.size
            if (count == 0) return@Canvas
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.25f).coerceAtLeast(1f) // 1/4 du slot (barres fines)
            // Coins arrondis (cf. ProgressBarPrimitive 2.dp), bornés à la ½ largeur fine.
            val radiusPx = minOf(2.dp.toPx(), barWidth / 2f)
            val radius = CornerRadius(radiusPx, radiusPx)
            val minBar = 1.dp.toPx()
            totals.forEachIndexed { index, total ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                if (total <= 0f) return@forEachIndexed
                val segments = stackedValues?.get(index)
                if (segments == null) {
                    val barHeight = ((total / max) * size.height).coerceAtLeast(minBar)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius,
                    )
                } else {
                    // Segments empilés du bas vers le haut ; seuls le bas du 1er segment
                    // et le haut du dernier sont arrondis (barre d'un seul tenant).
                    val drawn = segments.withIndex().filter { it.value > 0f }
                    var bottom = size.height
                    drawn.forEachIndexed { drawIdx, seg ->
                        val h = ((seg.value / max) * size.height).coerceAtLeast(minBar)
                        val topR = if (drawIdx == drawn.lastIndex) radius else CornerRadius.Zero
                        val bottomR = if (drawIdx == 0) radius else CornerRadius.Zero
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = x,
                                    top = bottom - h,
                                    right = x + barWidth,
                                    bottom = bottom,
                                    topLeftCornerRadius = topR,
                                    topRightCornerRadius = topR,
                                    bottomLeftCornerRadius = bottomR,
                                    bottomRightCornerRadius = bottomR,
                                ),
                            )
                        }
                        drawPath(path, stackColors.getOrElse(seg.index) { barColor })
                        bottom -= h
                    }
                }
            }
            // Repère de moyenne : ligne horizontale pointillée par-dessus les barres
            // (langage visuel des guidelines de GoalsAchievementChart).
            if (averageLine != null && averageLine > 0f) {
                val y = size.height - (averageLine / max).coerceIn(0f, 1f) * size.height
                drawLine(
                    color = lightGrayBlue.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
                )
                // Valeur juste au-dessus de la ligne, toujours calée à droite.
                val lx = (size.width - avgLayout.size.width).coerceAtLeast(0f)
                val ly = (y - avgLayout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                drawText(avgLayout, topLeft = Offset(lx, ly))
            }
            // Guide de sélection (tooltip) : trait pointillé vertical sur la barre tapée.
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
        // Tooltip en OVERLAY (cadre façon web : en-tête label + une ligne par série,
        // le mode empilé détaille chaque segment) — ne décale pas la mise en page.
        selected?.let { s ->
            val label = (tooltipLabel ?: { i: Int -> if (perBar) axisLabels.getOrElse(i) { "" } else "" })(s)
            fun fmt(v: Float) = String.format(locale, "%,d", v.roundToInt()) + valueSuffix
            val rows = stackedValues?.get(s)?.let { segments ->
                segments.mapIndexedNotNull { i, seg ->
                    if (seg <= 0f) null
                    else ChartTooltipRow(
                        name = stackLabels.getOrNull(i),
                        valueText = fmt(seg),
                        color = stackColors.getOrElse(i) { barColor },
                    )
                }
            } ?: listOf(ChartTooltipRow(seriesName.ifBlank { null }, fmt(totals[s]), barColor))
            ChartTapTooltip(
                label = label,
                rows = rows,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        }
        // Axe de base optionnel ([axisColor] non-null) collé au bas des barres : ligne
        // pleine largeur + petits traits sortants. En mode 1-label-par-barre → un tick
        // au centre de chaque barre ; en mode épars → ticks aux fractions équidistantes
        // 0..1 (0/6/12/18/24 h). [axisColor] null → aucun axe (ex. variante 7 jours).
        axisColor?.let { lineColor ->
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            ) {
                val count = totals.size
                if (count == 0) return@Canvas
                val stroke = 1.dp.toPx()
                val tickLen = 4.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = stroke,
                )
                val fractions = when {
                    perBar -> List(count) { (it + 0.5f) / count }
                    axisLabels.size <= 1 -> listOf(0.5f)
                    else -> List(axisLabels.size) { it.toFloat() / (axisLabels.size - 1) }
                }
                fractions.forEach { f ->
                    val x = (f * size.width).coerceIn(stroke / 2f, size.width - stroke / 2f)
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, tickLen),
                        strokeWidth = stroke,
                    )
                }
            }
        }
        // Sans axe (variante 7 jours), on aère un peu plus l'espace barres → labels ;
        // avec axe, la bande d'axe (5dp) fournit déjà la respiration.
        Spacer(Modifier.height(if (axisColor != null) 3.dp else 8.dp))
        if (perBar) {
            // 1 label par barre : centré sous chaque barre (slots réservés alignés).
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
        } else {
            // Repères d'axe épars (ex. 24 h aux quarts) : répartis SpaceBetween.
            Row(
                modifier = Modifier.fillMaxWidth(),
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
