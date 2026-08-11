package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.feature.onboarding.data.kgToLbs
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.core.stats.ChartGranularity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer.Line
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer.LineFill
import com.patrykandpatrick.vico.core.common.Fill
import com.example.sportapp.core.stats.ChartType
import com.example.sportapp.core.stats.MetricType
import com.patrykandpatrick.vico.core.common.data.ExtraStore

/**
 * B3-2 Stats overview hero chart : multi-courbes par groupe musculaire (Chest,
 * Back, Shoulders, Arms, Legs, Core), volume hebdo aligne sur le range.
 *
 * Hauteur 300dp default (vs 200dp MultiLineChart legacy). Customisations :
 * - Y axis label 'Volume (kg)' au-dessus du chart
 * - X axis labels formates en 'W18' (numero de semaine ISO) au lieu de l'index
 * - Legende centree horizontalement
 */
@Composable
fun MuscleGroupVolumeChart(
    buckets: List<String>,
    seriesByGroup: Map<String, List<Float>>,
    orderedKeys: List<String>,
    colorMap: Map<String, Color>,
    granularity: ChartGranularity,
    chartType: ChartType,
    metric: MetricType,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    weightUnit: WeightUnit = WeightUnit.KG,
) {
    val borderColor = appColors.primaryAction

    // Convertit les valeurs poids en lbs si user en LBS. Les autres metriques
    // (SETS, EXERCISES) sont des counts -- pas de conversion.
    val displaySeries: Map<String, List<Float>> = remember(seriesByGroup, metric, weightUnit) {
        if (metric == MetricType.TOTAL_WEIGHT && weightUnit == WeightUnit.LBS) {
            seriesByGroup.mapValues { (_, values) -> values.map { kgToLbs(it) } }
        } else {
            seriesByGroup
        }
    }

    // Empty state : aucune donnee OU 1 seul bucket (Vico LineLayer ne peut
    // pas tracer une ligne avec 1 point).
    if (displaySeries.isEmpty() || buckets.size <= 1) {
        val message = if (buckets.size <= 1 && seriesByGroup.isNotEmpty()) {
            "Not enough data points to draw a trend.\nTry a wider range."
        } else {
            "No data for this period"
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.bgRecessed)
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                color = borderColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        return
    }

    // Bug fix 2026-05-09 : on utilise `orderedKeys` (List<String>) plutot
    // que `seriesByGroup.keys.toList()` parce que Map.equals() est set-based
    // (pas order-aware). Compose skipperait la recomposition de cette
    // fonction si seul l'ordre du Map changeait. Avec `orderedKeys` en
    // parametre, Compose detecte la difference et recompose, declenchant
    // alors le re-mount via key(dataSignature).
    val groupKeys = orderedKeys

    // KEY = signature stable de la donnee : invalide tout l'arbre Vico quand
    // buckets/seriesByGroup/granularity change. Fix crash observed apres
    // switch de range : Vico ModelProducer + LineProvider + valueFormatter
    // peuvent se desynchroniser quand on swap le data mid-recompose. Le
    // key(...) force un re-mount complet et propre du chart.
    val dataSignature = remember(buckets, groupKeys, granularity, chartType, metric, weightUnit) {
        listOf(
            buckets.size,
            groupKeys.joinToString("|") { "$it:${displaySeries[it]?.size ?: 0}" },
            granularity,
            chartType,
            metric,
            weightUnit,
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        key(dataSignature) {
            when (chartType) {
                ChartType.LINE -> LineChartBox(
                    buckets = buckets,
                    seriesByGroup = displaySeries,
                    groupKeys = groupKeys,
                    colorMap = colorMap,
                    granularity = granularity,
                    metric = metric,
                    height = height,
                )
                ChartType.BAR -> BarChartBox(
                    seriesByGroup = displaySeries,
                    groupKeys = groupKeys,
                    colorMap = colorMap,
                    metric = metric,
                    height = height,
                )
            }
        }
        // Legende externe retiree : les GroupFilterChips sous le chart font
        // deja office de legende (memes couleurs que les courbes / bars).
    }
}

/** Format des valeurs Y axis selon la metrique : kg (suffix k/M) ou entier. */
private fun formatMetricValue(value: Float, metric: MetricType): String = when (metric) {
    MetricType.TOTAL_WEIGHT -> formatVolumeKg(value)
    MetricType.SETS, MetricType.EXERCISES -> {
        if (value <= 0f) "0"
        else if (value < 100f) String.format(Locale.US, "%.0f", value)
        else String.format(Locale.US, "%.0f", value)
    }
}

/**
 * Bar chart custom (Compose, sans Vico). 1 colonne par groupe = cumul total
 * sur la periode. User feedback 2026-05-07 :
 *  - Bars plus fines (largeur fixe 18dp)
 *  - Plus d'espacement entre (spacedBy 16dp)
 *  - Axe Y custom a gauche pour mesurer (4 ticks 0/33%/66%/100% + guidelines
 *    horizontales pointillees fines)
 */
@Composable
private fun BarChartBox(
    seriesByGroup: Map<String, List<Float>>,
    groupKeys: List<String>,
    colorMap: Map<String, Color>,
    metric: MetricType,
    height: Dp,
) {
    val cumuls = groupKeys.map { group ->
        group to (seriesByGroup[group]?.sum() ?: 0f)
    }
    val rawMax = (cumuls.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)
    // Pas de headroom : la bar la plus haute touche le tick max (user
    // feedback 2026-05-09 : "trop d'espace en haut", revert).
    val displayMax = rawMax
    val yTicks = remember(rawMax) {
        listOf(0f, rawMax / 3f, rawMax * 2f / 3f, rawMax)
    }

    // Au-dela de 15 series, on bascule en mode scroll horizontal : slot largeur
    // fixe (18dp) + spacing 3dp -> ~15 bars visibles d'un coup. Sinon, on
    // calcule dynamiquement bar et spacing pour garder le meme ratio visuel
    // (bar/slot ~ 55%, spacing/slot ~ 17%) quel que soit le nombre de bars.
    // User feedback 2026-05-09 : "applique ce rapport aux graphes <15 bars".
    val useScroll = cumuls.size > 15
    val barScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(appColors.bgRecessed)
            // top 28dp pour matcher visuellement l'espace en bas (12dp padding
            // + 4dp spacer + ~14dp labels X = ~30dp). end 12dp pour respirer a
            // droite du dernier bar / label X. User feedback 2026-05-09.
            .padding(start = 4.dp, end = 12.dp, top = 28.dp, bottom = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Y axis labels (fixe a gauche). Chiffres centres horizontalement
            // dans la Column 24dp, soit ~12dp entre le bord gauche du cadre et
            // l'axe vertical Y a x=0 (user feedback 2026-05-09).
            Column(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                yTicks.reversed().forEach { tick ->
                    Text(
                        text = formatMetricValue(tick, metric),
                        color = lightGrayBlue,
                        fontSize = 9.sp,
                    )
                }
            }

            // ── Bars area + X labels
            Column(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val n = cumuls.size.coerceAtLeast(1)
                    // Mode scroll : slot 18dp / bar 10dp / spacing 3dp (ratios
                    // 0.555 et 0.167). Mode no-scroll : meme ratios appliques
                    // dynamiquement a la largeur dispo. n × slot + (n-1) ×
                    // spacingRatio × slot = totalWidth -> slot = totalWidth /
                    // (n + spacingRatio × (n-1)).
                    val (barWidthDp, spacingDp) = if (useScroll) {
                        Pair(10.dp, 3.dp)
                    } else {
                        val divisor = n.toFloat() + SPACING_RATIO * (n - 1).coerceAtLeast(0).toFloat()
                        val slotDp: Dp = maxWidth / divisor
                        Pair(slotDp * BAR_RATIO, slotDp * SPACING_RATIO)
                    }

                    // Guidelines + axe Y vertical (plein-ecran fixe)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        yTicks.forEach { tick ->
                            val frac = tick / displayMax
                            val y = size.height * (1f - frac)
                            drawLine(
                                color = lightGrayBlue.copy(alpha = 0.45f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)),
                            )
                        }
                        drawLine(
                            color = lightGrayBlue.copy(alpha = 0.55f),
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.2f,
                        )
                    }
                    Row(
                        modifier = if (useScroll) {
                            Modifier.fillMaxHeight().horizontalScroll(barScrollState)
                        } else {
                            Modifier.fillMaxSize()
                        },
                        horizontalArrangement = Arrangement.spacedBy(spacingDp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        cumuls.forEach { (group, volume) ->
                            val color = colorMap[group] ?: appColors.textTertiary
                            BarColumn(
                                color = color,
                                fraction = (volume / displayMax).coerceIn(0f, 1f),
                                barWidth = barWidthDp,
                                modifier = if (useScroll) {
                                    Modifier.width(18.dp)
                                } else {
                                    Modifier.weight(1f)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // X labels : meme spacing dynamique que les bars pour rester
                // aligned. En mode scroll, meme scrollState que les bars.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val n = cumuls.size.coerceAtLeast(1)
                    val spacingDp: Dp = if (useScroll) {
                        3.dp
                    } else {
                        val divisor = n.toFloat() + SPACING_RATIO * (n - 1).coerceAtLeast(0).toFloat()
                        val slotDp: Dp = maxWidth / divisor
                        slotDp * SPACING_RATIO
                    }
                    Row(
                        modifier = if (useScroll) {
                            Modifier.fillMaxWidth().horizontalScroll(barScrollState)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        horizontalArrangement = Arrangement.spacedBy(spacingDp),
                    ) {
                        cumuls.forEach { (group, _) ->
                            val color = colorMap[group] ?: appColors.textTertiary
                            Text(
                                text = shortGroupLabel(group),
                                color = color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = if (useScroll) {
                                    Modifier.width(18.dp)
                                } else {
                                    Modifier.weight(1f)
                                },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Constantes ratio globales pour le BarChart (refacto user feedback 2026-05-09).
// bar/slot = 0.555 et spacing/slot = 0.167 = ratios actuels du mode scroll
// (slot 18dp -> bar 10dp + spacing 3dp). Appliques uniformement au mode
// no-scroll pour que les bars d'un chart a 6 zones ressemblent visuellement
// a celles d'un chart a 35 muscles, juste plus larges.
private const val BAR_RATIO = 0.555f
private const val SPACING_RATIO = 0.167f

@Composable
private fun BarColumn(
    color: Color,
    fraction: Float,
    barWidth: Dp,
    modifier: Modifier = Modifier,
) {
    // Slot externe (weight(1f) en no-scroll, width 18dp en scroll) ; bar
    // centree dans le slot, largeur passee en parametre (calcul dynamique
    // ratio bar/slot = 0.555 cote BarChartBox).
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(fraction.coerceAtLeast(0.005f))
                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                .background(color),
        )
    }
}

/** Abreviations courtes pour les labels X axis du bar chart (slot etroit).
 *  Le shortcut "Shoulders" -> "Delts" datait d'avant le refactor 3-niveaux
 *  (avant 2026-05-08, "Shoulders" etait juste le nom de zone). Il est
 *  retire maintenant que "Delts" est le vrai muscle_group affiche dans la
 *  section Group, et "Shoulders" reste le nom de zone (user feedback
 *  2026-05-09). */
private fun shortGroupLabel(group: String): String = group

private fun formatVolumeKg(kg: Float): String {
    if (kg <= 0f) return "0"
    return when {
        kg >= 1_000_000f -> String.format(Locale.US, "%.1fM", kg / 1_000_000f)
        kg >= 10_000f -> String.format(Locale.US, "%.0fk", kg / 1_000f)
        kg >= 1_000f -> String.format(Locale.US, "%.1fk", kg / 1_000f)
        else -> "${kg.toInt()}"
    }
}

@Composable
private fun LineChartBox(
    buckets: List<String>,
    seriesByGroup: Map<String, List<Float>>,
    groupKeys: List<String>,
    colorMap: Map<String, Color>,
    granularity: ChartGranularity,
    metric: MetricType,
    height: Dp,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(Unit) {
        modelProducer.runTransaction {
            lineSeries {
                groupKeys.forEach { group ->
                    series(seriesByGroup[group] ?: emptyList())
                }
            }
        }
    }

    val lineSpecs = groupKeys.map { group ->
        val color = colorMap[group] ?: appColors.textTertiary
        Line(
            fill = LineFill.single(fill(color)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 2.5f),
            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0.5f),
        )
    }
    val lineProvider = LineCartesianLayer.LineProvider.series(lineSpecs)

    val dashedGuideline = rememberLineComponent(
        fill = Fill(appColors.textTertiary.toArgb()),
        thickness = 0.1.dp,
    )

    val tickSpacing = { _: ExtraStore -> maxOf(1, buckets.size / 8) }

    // Format X axis selon granularity :
    //  - DAILY  : 'YYYY-MM-DD' -> 'D/M' (ex. '5/5')
    //  - WEEKLY : 'YYYY-WW'    -> 'WNN' (ex. 'W19')
    val bottomFormatter = CartesianValueFormatter { _, value, _ ->
        val idx = value.toInt()
        val bucket = buckets.getOrNull(idx) ?: return@CartesianValueFormatter ""
        when (granularity) {
            ChartGranularity.DAILY -> {
                val parsed = try { LocalDate.parse(bucket) } catch (_: Exception) { null }
                if (parsed != null) "${parsed.dayOfMonth}/${parsed.monthValue}" else bucket
            }
            ChartGranularity.WEEKLY -> {
                val weekNumber = bucket.substringAfterLast('-').toIntOrNull()
                if (weekNumber != null) "W$weekNumber" else bucket
            }
        }
    }

    // Format Y axis : kg (k/M suffix) en TOTAL_WEIGHT ; entier en SETS/EXERCISES.
    val startFormatter = CartesianValueFormatter { _, value, _ ->
        formatMetricValue(value.toFloat(), metric)
    }

    // Detection des transitions de mois pour les guidelines verticales discretes.
    // (bucketIdx, monthShortLabel). Adapte au granularity (parse jour ou semaine ISO).
    val monthTransitions = remember(buckets, granularity) {
        computeMonthTransitions(buckets, granularity)
    }

    // Scroll horizontal externe partage entre le chart Vico et le Canvas overlay.
    // Largeur "logique" du chart = pixelPerWeek * buckets.size, mini = largeur
    // visible (range court tient sans scroll, range long deborde et scrolle).
    // Vico scroll/zoom internes desactives pour eviter conflit avec le scroll
    // externe. User feedback 2026-05-07 : guidelines doivent slider avec
    // les courbes.
    // Largeur par bucket : 56dp en weekly (longue periode), 80dp en daily
    // (les labels '5/5' et les transitions visuelles tiennent mieux).
    val pixelPerBucket = if (granularity == ChartGranularity.DAILY) 80.dp else 56.dp
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(appColors.bgRecessed),
    ) {
        val visibleWidth = maxWidth
        val computedWidth = pixelPerBucket * buckets.size.coerceAtLeast(2)
        val effectiveWidth = if (computedWidth < visibleWidth) visibleWidth else computedWidth

        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .width(effectiveWidth)
                .fillMaxHeight()
                .padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            // Axes en lightGrayBlue (coherence avec bar chart custom).
            val axisLabel = rememberAxisLabelComponent(color = lightGrayBlue)
            val axisLine = rememberAxisLineComponent(fill = fill(lightGrayBlue))
            val axisTick = rememberAxisTickComponent(fill = fill(lightGrayBlue))

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = lineProvider,
                        rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, minX = 0.0),
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        line = axisLine,
                        tick = axisTick,
                        guideline = dashedGuideline,
                        valueFormatter = startFormatter,
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = axisLabel,
                        line = axisLine,
                        tick = axisTick,
                        guideline = null,
                        valueFormatter = bottomFormatter,
                        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = tickSpacing),
                    ),
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(zoomEnabled = false),
                modifier = Modifier.fillMaxSize(),
            )

            if (monthTransitions.isNotEmpty() && buckets.size > 1) {
                val dashColor = lightGrayBlue.copy(alpha = 0.35f)
                val labelColor = lightGrayBlue.copy(alpha = 0.7f)

                // Padding qui matche la zone PLOT Vico (post-axes). Empirique :
                //  - top 14dp : place les labels mois sans depasser au-dessus
                //  - bottom 26dp : matche l'axe X Vico
                //  - start 36dp : matche l'axe Y Vico
                //  - end 4dp : marge minimale
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(start = 36.dp, top = 14.dp, end = 4.dp, bottom = 26.dp),
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        monthTransitions.forEach { (idx, _) ->
                            val frac = if (buckets.size > 1) {
                                idx.toFloat() / (buckets.size - 1).toFloat()
                            } else 0f
                            val x = frac * size.width
                            drawLine(
                                color = dashColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                            )
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val widthDp = maxWidth
                        monthTransitions.forEach { (idx, monthLabel) ->
                            val frac = if (buckets.size > 1) {
                                idx.toFloat() / (buckets.size - 1).toFloat()
                            } else 0f
                            val xDp = (widthDp.value * frac).dp
                            Text(
                                text = monthLabel,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(
                                        start = (xDp.value - 10f).coerceAtLeast(0f).dp,
                                        top = 4.dp,
                                    ),
                                color = labelColor,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pour chaque transition de mois dans la liste de buckets, retourne
 * (bucketIndex, monthShortLabel). Parse selon granularity :
 *  - DAILY  : 'YYYY-MM-DD' -> LocalDate.parse direct
 *  - WEEKLY : 'YYYY-WW' -> calcul du lundi de la semaine ISO
 */
private fun computeMonthTransitions(
    buckets: List<String>,
    granularity: ChartGranularity,
): List<Pair<Int, String>> {
    if (buckets.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<Int, String>>()
    var lastMonth: String? = null
    buckets.forEachIndexed { idx, bucket ->
        val date: LocalDate? = try {
            when (granularity) {
                ChartGranularity.DAILY -> LocalDate.parse(bucket)
                ChartGranularity.WEEKLY -> {
                    val parts = bucket.split("-")
                    if (parts.size != 2) return@forEachIndexed
                    val year = parts[0].toIntOrNull() ?: return@forEachIndexed
                    val weekNum = parts[1].toIntOrNull() ?: return@forEachIndexed
                    LocalDate.of(year, 1, 4)
                        .with(WeekFields.ISO.weekOfWeekBasedYear(), weekNum.toLong())
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                }
            }
        } catch (_: Exception) {
            null
        }
        if (date == null) return@forEachIndexed
        val monthShort = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        if (monthShort != lastMonth) {
            result.add(idx to monthShort)
            lastMonth = monthShort
        }
    }
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(items: List<Pair<String, Color>>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, shape = MaterialTheme.shapes.extraSmall),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, color = appColors.primaryAction)
            }
        }
    }
}
