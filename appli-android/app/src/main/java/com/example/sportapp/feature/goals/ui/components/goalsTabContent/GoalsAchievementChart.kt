package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.feature.goals.viewmodel.GoalsChartBar
import java.util.Locale

/**
 * Chart footer de la page MuscleGoals (refonte 2026-05-09) : 1 bar par
 * muscle/muscle_group/zone (selon GoalsViewMode), hauteur = % achievement
 * cap-free (peut depasser 100). Style aligne sur le BarChartBox de Stats
 * mais simplifie (1 seul bucket = la semaine selectionnee, donc pas de
 * series temporelles, pas de scroll Vico, pas de toggle Line/Bar).
 *
 * Specifiques a Goals (vs Stats) :
 *  - Y axis en % (suffix "%" sur les ticks).
 *  - Ligne pointillee horizontale a y=100% (target line) en couleur primaire,
 *    visible meme quand toutes les bars depassent largement 100%.
 *  - Bars SKIPPED rendues en alpha 0.4 (semi-transparentes) avec ombrage
 *    pour signaler "etait prevu mais zappe".
 *  - Scroll horizontal au-dela de 15 bars (pattern identique BarChartBox).
 *
 * Ratios bar/spacing alignes sur Stats (BAR_RATIO=0.555, SPACING_RATIO=0.167)
 * pour coherence visuelle cross-screens.
 */
@Composable
fun GoalsAchievementChart(
    bars: List<GoalsChartBar>,
    colorMap: Map<String, Color>,
    modifier: Modifier = Modifier,
    height: Dp = 250.dp,
) {
    val borderColor = appColors.primaryAction

    if (bars.isEmpty()) {
        // Bordure en retrait du fond (padding interne) — même style que StatsChartCard vide.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.bgRecessed)
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, borderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No goals this week",
                    color = borderColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        return
    }

    // Echelle Y : max(100, max bar). 100 mini garantit que la ligne 100%
    // target est toujours visible meme si toutes les bars sont sous 100%.
    val rawMax = bars.maxOf { it.percent }.coerceAtLeast(100f)
    // Petit headroom 5% au-dessus du max pour que la pointe la plus haute
    // ne touche pas le bord superieur du cadre.
    val displayMax = rawMax * 1.05f
    // Ticks fixes 0/25/50/75/100 (user feedback runtime 2026-05-09 : valeurs
    // round attendues vs 0/34/69/104 calcules sur displayMax). Si rawMax >
    // 100, les bars peuvent depasser visuellement le tick 100% (continuent
    // vers le haut de la zone du chart jusqu'a displayMax). Pas de tick
    // au-dessus de 100% — ces labels rond restent les reperes lisibles.
    val yTicks = listOf(0f, 25f, 50f, 75f, 100f)

    // Mode scroll au-dela de 15 bars (idem Stats BarChartBox).
    val useScroll = bars.size > 15
    val barScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(appColors.bgRecessed)
            .padding(start = 4.dp, end = 12.dp, top = 28.dp, bottom = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Y axis labels (% suffix). Positionnement proportionnel
            // (vs equidistant Arrangement.SpaceBetween) : avec ticks fixes
            // 0/25/50/75/100 et displayMax variable (>= 100), un label "100%"
            // doit etre place a la position y de la guideline correspondante,
            // sinon decalage visuel (ex. displayMax=210 -> "100%" doit etre
            // a mi-hauteur, pas en haut).
            BoxWithConstraints(
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .padding(bottom = 22.dp),
            ) {
                val zoneHeight = maxHeight
                yTicks.forEach { tick ->
                    val frac = (tick / displayMax).coerceIn(0f, 1f)
                    // -5dp pour centrer verticalement le text (~9sp = ~12dp)
                    // sur la guideline horizontale du Canvas.
                    val yOffset: Dp = (zoneHeight.value * (1f - frac) - 5f)
                        .coerceIn(0f, zoneHeight.value - 11f)
                        .dp
                    Text(
                        text = formatPercent(tick),
                        color = lightGrayBlue,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = yOffset),
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
                    val n = bars.size.coerceAtLeast(1)
                    val (barWidthDp, spacingDp) = if (useScroll) {
                        Pair(10.dp, 3.dp)
                    } else {
                        val divisor = n.toFloat() + SPACING_RATIO * (n - 1).coerceAtLeast(0).toFloat()
                        val slotDp: Dp = maxWidth / divisor
                        Pair(slotDp * BAR_RATIO, slotDp * SPACING_RATIO)
                    }

                    // Guidelines horizontales + axe Y vertical + ligne 100% target.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Ticks guidelines (pointilles fins en gris).
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
                        // Ligne 100% target (specifique Goals) : couleur primaire,
                        // pointilles plus marques, alpha plus eleve. Toujours visible
                        // grace au coerceAtLeast(100f) sur rawMax.
                        val targetY = size.height * (1f - 100f / displayMax)
                        drawLine(
                            color = borderColor.copy(alpha = 0.85f),
                            start = Offset(0f, targetY),
                            end = Offset(size.width, targetY),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)),
                        )
                        // Axe Y vertical a x=0.
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
                        bars.forEach { bar ->
                            val rawColor = colorMap[bar.key] ?: appColors.textTertiary
                            // SKIPPED : alpha 0.4 (semi-transparent, "efface").
                            val color = if (bar.isSkipped) rawColor.copy(alpha = 0.4f) else rawColor
                            BarColumn(
                                color = color,
                                fraction = (bar.percent / displayMax).coerceIn(0f, 1f),
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
                // X labels : meme spacing dynamique que les bars.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val n = bars.size.coerceAtLeast(1)
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
                        bars.forEach { bar ->
                            val rawColor = colorMap[bar.key] ?: appColors.textTertiary
                            val color = if (bar.isSkipped) rawColor.copy(alpha = 0.5f) else rawColor
                            Text(
                                text = bar.key,
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

// Ratios alignes sur ui/components/stats/MuscleGroupVolumeChart.kt pour
// coherence visuelle cross-screens (refacto user feedback 2026-05-09).
private const val BAR_RATIO = 0.555f
private const val SPACING_RATIO = 0.167f

@Composable
private fun BarColumn(
    color: Color,
    fraction: Float,
    barWidth: Dp,
    modifier: Modifier = Modifier,
) {
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

private fun formatPercent(value: Float): String {
    if (value <= 0f) return "0%"
    return when {
        value >= 1000f -> String.format(Locale.US, "%.0fk%%", value / 1000f)
        else -> "${value.toInt()}%"
    }
}
