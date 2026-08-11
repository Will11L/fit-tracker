package com.example.sportapp.feature.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.sportapp.R
import java.time.LocalDate
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.LabeledProgressBar
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.TrendLineChart
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.ButtonPrimaryColor
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.lightBlue
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.designsystem.theme.lightGreen
import com.example.sportapp.designsystem.theme.brightPurple
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.designsystem.theme.turquoise
import com.example.sportapp.designsystem.theme.yellowMedium
import com.example.sportapp.feature.health.domain.HealthConnectMapper
import com.example.sportapp.feature.health.domain.HealthUiAggregations
import com.example.sportapp.feature.health.domain.SleepSessionReading
import com.example.sportapp.feature.health.ui.components.EnergyWeekChart
import com.example.sportapp.feature.health.ui.components.HealthBarChart
import com.example.sportapp.feature.health.ui.components.HypnogramChart
import com.example.sportapp.feature.health.ui.components.StepGoalDialog
import com.example.sportapp.feature.health.ui.components.StressEntryDialog
import com.example.sportapp.feature.health.ui.components.WeightEntryDialog
import com.example.sportapp.feature.health.ui.components.stressCategoryColor
import com.example.sportapp.feature.health.ui.components.stressCategoryLabelRes
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Nombre d'onglets/pages du hub (source unique [HEALTH_SECTIONS]). */
private val HEALTH_TABS = HEALTH_SECTIONS.size

/**
 * Hub Santé (accès provisoire via le drawer, section General). Affiche la couche
 * Room persistée : pas & objectif (anneau + réglage), FC, sommeil, SpO2, et états
 * vides soignés pour distance/calories (indisponibles via HC). Style app, i18n.
 */
@Composable
fun HealthDashboardScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: HealthDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val hourlyHr by viewModel.hourlyHr.collectAsState()
    val hourlySleep by viewModel.hourlySleep.collectAsState()
    val sleepSessions by viewModel.sleepSessions.collectAsState()
    val sleepPhases by viewModel.sleepPhases.collectAsState()
    val sleepWeekStages by viewModel.sleepWeekStages.collectAsState()
    val hourlySpo2 by viewModel.hourlySpo2.collectAsState()
    var editingGoal by remember { mutableStateOf(false) }
    var editingWeight by remember { mutableStateOf(false) }
    // Jour ciblé à l'ouverture du dialog de pesée (null = aujourd'hui) : posé par
    // le tap sur l'anneau d'un jour manquant dans un chart.
    var weightDialogDate by remember { mutableStateOf<String?>(null) }
    var editingStress by remember { mutableStateOf(false) }
    var stressDialogDate by remember { mutableStateOf<String?>(null) }
    val refreshLabel = stringResource(R.string.health_refresh)

    // Un onglet = une sous-page (pager). L'icône active suit la page courante ; tap
    // icône → anime vers la page ; swipe → l'icône suit.
    val pagerState = rememberPagerState(pageCount = { HEALTH_TABS })
    val scope = rememberCoroutineScope()

    // Refresh à l'arrivée sur le hub (ouverture + retour ON_RESUME) : réimport
    // HC → Room best-effort + pull montre, comme l'écran Données santé.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // Demande de section du drawer ([HealthNavRequest], miroir HealthNavService web) :
    // anime le pager vers la page demandée — que le hub soit fraîchement monté ou déjà là.
    LaunchedEffect(Unit) {
        HealthNavRequest.page.collect { page ->
            if (page != null) {
                pagerState.animateScrollToPage(page.coerceIn(0, HEALTH_TABS - 1))
                HealthNavRequest.consume()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen),
    ) {
        // Header canonique ScreenTitleBar (même style que Nutrition/Stats, demande user
        // 2026-07-14 — Santé est une destination bottom-nav, pas de flèche retour) ;
        // ↻ global overlay CenterEnd, fond par défaut du bouton.
        Box(modifier = Modifier.fillMaxWidth()) {
            ScreenTitleBar(title = stringResource(R.string.health_dash_title))
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_refresh,
                onClick = { viewModel.refresh() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .semantics { contentDescription = refreshLabel },
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (page) {
                        0 -> StepsSection(state = state, onEditGoal = { editingGoal = true })
                        1 -> CardioSection(state = state, hourly = hourlyHr)
                        2 -> SleepSection(
                            state = state,
                            hourly = hourlySleep,
                            sessions = sleepSessions,
                            phases = sleepPhases,
                            weekStages = sleepWeekStages,
                        )
                        3 -> Spo2Section(state = state, hourly = hourlySpo2)
                        4 -> EnergySection(state = state)
                        5 -> WeightSection(
                            state = state,
                            onLogWeight = { day ->
                                weightDialogDate = day
                                editingWeight = true
                            },
                        )
                        else -> StressSection(
                            state = state,
                            onLogStress = { day ->
                                stressDialogDate = day
                                editingStress = true
                            },
                        )
                    }
                }
            }
            // Sur la 1re sous-page uniquement : le pager consomme tout geste horizontal,
            // donc une fine zone de bord gauche rend au drawer son ouverture au swipe
            // (drag vers la droite). Les taps et le scroll vertical passent au travers.
            if (pagerState.currentPage == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(28.dp)
                        .pointerInput(Unit) {
                            var triggered = false
                            detectHorizontalDragGestures(
                                onDragStart = { triggered = false },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (!triggered && dragAmount > 0f) {
                                        triggered = true
                                        onOpenDrawer()
                                    }
                                },
                            )
                        },
                )
            }
        }

        // Barre d'icônes des sections ÉPINGLÉE EN BAS (demande user 2026-07-14), juste
        // au-dessus de la bottom nav (la Column vit dans la zone paddée du Scaffold).
        HealthIconBar(activeIndex = pagerState.currentPage) { index ->
            scope.launch { pagerState.animateScrollToPage(index) }
        }
    }

    if (editingGoal) {
        StepGoalDialog(
            current = state.goalTarget,
            onConfirm = {
                viewModel.setStepGoal(it)
                editingGoal = false
            },
            onDismiss = { editingGoal = false },
        )
    }

    if (editingWeight) {
        WeightEntryDialog(
            current = state.weightKg,
            existingByDate = state.weightByDate,
            initialDate = weightDialogDate,
            onConfirm = { kg, date ->
                viewModel.logWeight(kg, date)
                editingWeight = false
            },
            onDismiss = { editingWeight = false },
        )
    }

    if (editingStress) {
        StressEntryDialog(
            current = state.stressScore,
            existingByDate = state.stressByDate,
            initialDate = stressDialogDate,
            onConfirm = { score, date ->
                viewModel.logStress(score, date)
                editingStress = false
            },
            onDismiss = { editingStress = false },
        )
    }
}

/* ------------------------------ Icon bar ------------------------------- */

/** Barre d'icônes sous le header : tap → scroll vers la section. L'état actif prend
 *  la couleur d'identité de la sous-section (vert Pas, orange FC, etc.). */
@Composable
private fun HealthIconBar(activeIndex: Int, onTap: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(secondBlue) // fond barre d'onglets (= bgBottomNav du design system)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        HEALTH_SECTIONS.forEachIndexed { index, (iconRes, labelRes, activeColor) ->
            val active = index == activeIndex
            val label = stringResource(labelRes)
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (active) activeColor else Color.Transparent)
                    .clickable { onTap(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (active) appColors.textOnSelected else appColors.textTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/* ------------------------------- Sections ------------------------------- */

@Composable
private fun StepsSection(
    state: HealthDashboardUiState,
    onEditGoal: () -> Unit,
) {
    val editGoalLabel = stringResource(R.string.health_dash_edit_goal)
    SectionCard(title = stringResource(R.string.health_dash_steps_title), titleColor = lightGreen) {
        // Ligne compteur : « X » (gros/gras) + « sur Y » (secondaire) + pastille live,
        // et bouton « Régler l'objectif » (✎ blanc sur pastille firstBlue) à droite.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Groupe compteur serré : « X » (gros/gras) + « sur Y » (secondaire).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = state.displaySteps.toString(),
                    color = appColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.goalTarget?.let { stringResource(R.string.health_dash_steps_of_goal, it) }
                        ?: stringResource(R.string.health_dash_no_goal),
                    color = appColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Respiration avant la pastille live, puis espace flexible avant le bouton.
            if (state.liveSteps) {
                Spacer(Modifier.width(12.dp))
                LivePill()
            }
            Spacer(Modifier.weight(1f))
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_edit,
                tint = appColors.textPrimary,          // icône blanche
                hasBackground = true,
                customBackgroundColor = firstBlue,     // pastille firstBlue
                onClick = onEditGoal,
                modifier = Modifier.semantics { contentDescription = editGoalLabel },
            )
        }
        // Piste en secondBlue (visible sur la card bgRecessed, cohérent ex-anneau).
        LabeledProgressBar(progress = state.progress, troughColor = secondBlue)
    }
    // Charts sortis de la card : cadres compacts indépendants (fond thirdBlue).
    // Intraday en barres vertes (volumes par tranche) ; 7 jours en courbe verte (tendance).
    val stepsName = stringResource(R.string.health_dash_steps_title)
    if (state.hourlySteps.any { it > 0f }) IntradayChartFrame(state.hourlySteps, barColor = lightGreen, valueSuffix = " " + stringResource(R.string.health_unit_steps), seriesName = stepsName)
    if (state.stepsWeek.any { it.second > 0f }) WeekLineFrame(state.stepsWeek, lineColor = lightGreen, valueSuffix = " " + stringResource(R.string.health_unit_steps), seriesName = stepsName)
}

@Composable
private fun CardioSection(state: HealthDashboardUiState, hourly: List<Float>) {
    val hasWeek = state.hrWeek.any { it.second > 0f }
    SectionCard(title = stringResource(R.string.health_dash_cardio_title), titleColor = orangeMedium) {
        // Même traitement que la ligne compteur des Pas : dernière mesure (gros/gras) +
        // moyenne 24 h (secondaire, comme « sur Y ») + pastille de l'heure de la mesure
        // (comme la pastille live). L'intraday est la source (dernière tranche non nulle).
        val lastSlot = hourly.indexOfLast { it > 0f }
        val hrLatest = state.hrTodayBpm
        when {
            lastSlot >= 0 -> {
                val bpm = hourly[lastSlot].roundToInt()
                // Moyenne du jour : la métrique Room (vraie moyenne quotidienne) prime
                // sur la moyenne des tranches affichées (approximation).
                val avg = state.hrTodayBpm
                    ?: (HealthUiAggregations.averageOfFilledDays(hourly) ?: 0f).roundToInt()
                val minutes = lastSlot * HealthConnectMapper.SLOT_MINUTES
                // Les 3 éléments « X bpm · moy. Y bpm · heure » répartis en SpaceEvenly.
                // La row déborde du padding horizontal de la card (bleed) pour que les
                // espaces soient visuellement égaux, bords du cadre compris.
                Row(
                    modifier = Modifier.bleedCardPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text(
                        text = stringResource(R.string.health_dash_cardio_bpm, bpm),
                        color = appColors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.health_dash_cardio_avg, avg),
                        color = appColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MeasureTimePill(stringResource(R.string.health_dash_cardio_at, minutes / 60, minutes % 60))
                }
            }
            hrLatest != null -> BigValue(stringResource(R.string.health_dash_cardio_today, hrLatest))
            else -> EmptyLine()
        }
    }
    // Intraday en barres orange (volumes par tranche) ; 7 jours en courbe orange (tendance).
    // Intraday TOUJOURS rendu (barres plates si rien encore aujourd'hui, comme les Pas) :
    // le cadre sert d'aperçu plutôt que de laisser un vide.
    val cardioName = stringResource(R.string.health_dash_cardio_title)
    IntradayChartFrame(hourly, barColor = orangeMedium, valueSuffix = " bpm", seriesName = cardioName)
    if (hasWeek) WeekLineFrame(state.hrWeek, lineColor = orangeMedium, valueSuffix = " bpm", seriesName = cardioName)
}

@Composable
private fun SleepSection(
    state: HealthDashboardUiState,
    hourly: List<Float>,
    sessions: List<SleepSessionReading>,
    phases: List<HealthUiAggregations.SleepPhasePoint>,
    weekStages: Map<String, List<Float>>,
) {
    val hasHourly = hourly.any { it > 0f }
    val hasWeek = state.sleepWeek.any { it.second > 0f }
    SectionCard(title = stringResource(R.string.health_dash_sleep_title), titleColor = ButtonPrimaryColor) {
        if (state.sleepMinutes == null && !hasHourly && !hasWeek) {
            EmptyLine()
        } else {
            // Tout sur UNE ligne (façon ligne FC) : durée dormie + heures de la nuit par
            // session (mise au lit / endormissement). Bleed du padding de card →
            // espaces visuellement égaux, bords du cadre compris.
            Row(
                modifier = Modifier.bleedCardPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                state.sleepMinutes?.let { min ->
                    BigValue(stringResource(R.string.health_dash_sleep_value, min / 60, min % 60))
                }
                sessions.forEach { session ->
                    SleepTimeLabel(
                        label = stringResource(R.string.health_dash_sleep_bed_at),
                        time = session.startTime,
                    )
                    SleepTimeLabel(
                        label = stringResource(R.string.health_dash_sleep_asleep_at),
                        time = session.asleepStartTime,
                    )
                }
            }
        }
    }
    // Hypnogramme « Cette nuit » (slices de phases exactes) quand des stades
    // existent ; sinon fallback barres intraday 30 min (bleues, identité sommeil) —
    // TOUJOURS rendu (barres plates = aperçu, comme les Pas, plutôt qu'un vide).
    if (phases.isNotEmpty()) {
        ChartFrame(title = stringResource(R.string.health_dash_tonight)) {
            HypnogramChart(points = phases, phaseColors = sleepStageColors)
            SleepStageLegend()
        }
    } else {
        IntradayChartFrame(hourly, barColor = ButtonPrimaryColor, valueSuffix = " min", seriesName = stringResource(R.string.health_dash_sleep_title))
    }
    // 7 jours : barres empilées par phase (profond/léger/paradoxal/éveillé) quand le
    // détail des stades existe ; sinon barres simples (totaux).
    if (hasWeek || weekStages.isNotEmpty()) {
        if (weekStages.isEmpty()) {
            WeekChartFrame(state.sleepWeek, barColor = ButtonPrimaryColor, valueSuffix = " min", seriesName = stringResource(R.string.health_dash_sleep_title))
        } else {
            SleepStagesWeekFrame(state.sleepWeek, weekStages)
        }
    }
}

// Couleurs des 4 phases de sommeil (empilement + légende) : profond / léger /
// paradoxal (REM) / éveillé — vals de palette existantes, lisibles sur thirdBlue.
private val sleepStageColors = listOf(ButtonPrimaryColor, lightGrayBlue, lightGreen, orangeMedium)

/**
 * Cadre « 7 jours » du sommeil en barres EMPILÉES par phase. Jours avec un total
 * Room mais sans détail de stades (sessions hors rétention HC) : tout en « léger ».
 */
@Composable
private fun SleepStagesWeekFrame(
    pairs: List<Pair<String, Float>>,
    stages: Map<String, List<Float>>,
) {
    val stacked = pairs.map { (date, total) ->
        stages[date] ?: listOf(0f, total, 0f, 0f)
    }
    val totals = stacked.map { it.sum() }
    ChartFrame(title = stringResource(R.string.health_dash_week)) {
        HealthBarChart(
            values = totals,
            axisLabels = dayAxisLabels(pairs.map { it.first }),
            background = Color.Transparent,
            trackColor = Color.Transparent,
            axisColor = null,
            averageLine = HealthUiAggregations.averageOfFilledDays(totals),
            contentPadding = PaddingValues(0.dp),
            stackedValues = stacked,
            stackColors = sleepStageColors,
            valueSuffix = " min",
            // Tooltip au tap : une ligne par phase (mêmes intitulés que la légende).
            stackLabels = listOf(
                stringResource(R.string.health_dash_stage_deep),
                stringResource(R.string.health_dash_stage_light),
                stringResource(R.string.health_dash_stage_rem),
                stringResource(R.string.health_dash_stage_awake),
            ),
        )
        SleepStageLegend()
    }
}

/** Légende des 4 phases (points colorés), répartie en SpaceEvenly sur la largeur. */
@Composable
private fun SleepStageLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendDot(sleepStageColors[0], stringResource(R.string.health_dash_stage_deep))
        LegendDot(sleepStageColors[1], stringResource(R.string.health_dash_stage_light))
        LegendDot(sleepStageColors[2], stringResource(R.string.health_dash_stage_rem))
        LegendDot(sleepStageColors[3], stringResource(R.string.health_dash_stage_awake))
    }
}

/** Pastille de légende : point coloré + libellé secondaire. */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            color = appColors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Libellés d'axe horaire (5 repères aux quarts : 0/6/12/18/24 h). */
@Composable
private fun hourlyAxisLabels(): List<String> =
    listOf(0, 6, 12, 18, 24).map { stringResource(R.string.health_dash_hour_axis, it) }

/** Libellés d'axe « 7 jours » : le quantième du jour (ex. 28 29 30 1 2 3 4). */
private fun dayAxisLabels(dates: List<String>): List<String> =
    dates.map { LocalDate.parse(it).dayOfMonth.toString() }

/** Section SpO2 (page dédiée, séparée du Sommeil) : dernière saturation en O2 +
 *  mesures de la journée (souvent 1 seule — Samsung n'écrit qu'une mesure nocturne,
 *  sauf mesure continue activée) + tendance 7 jours en barres. */
@Composable
private fun Spo2Section(state: HealthDashboardUiState, hourly: List<Float>) {
    SectionCard(title = stringResource(R.string.health_dash_spo2_title), titleColor = lightBlue) {
        val spo2 = state.spo2Percent
        if (spo2 == null) {
            EmptyLine()
        } else {
            BigValue(stringResource(R.string.health_dash_spo2_value, spo2))
        }
    }
    val spo2Name = stringResource(R.string.health_dash_spo2_title)
    // Intraday TOUJOURS rendu (barres plates = aperçu, comme les Pas, plutôt qu'un vide).
    IntradayChartFrame(hourly, barColor = lightBlue, valueSuffix = " %", seriesName = spo2Name)
    if (state.spo2Week.any { it.second > 0f }) WeekChartFrame(state.spo2Week, barColor = lightBlue, valueSuffix = " %", seriesName = spo2Name)
}

/**
 * Distance & calories. Calories : chaque ligne dont la valeur est connue (Métabolisme estimé ·
 * Activité · Total). Selon la source, le champ MESURÉ diffère (montre = actives → total dérivé ;
 * HC = total → actives dérivées) et, profil incomplet, seul le mesuré s'affiche. Ligne de source
 * (« Montre » / « Health Connect »). État vide soigné si aucune donnée.
 */
@Composable
private fun EnergySection(state: HealthDashboardUiState) {
    SectionCard(title = stringResource(R.string.health_dash_energy_title), titleColor = turquoise) {
        val hasDistance = (state.distanceValue ?: 0f) > 0f
        val bd = state.calorieBreakdown
        if (!hasDistance && bd == null) {
            EmptyLine()
        } else {
            if (hasDistance) {
                EnergyLine(
                    label = stringResource(R.string.health_dash_distance_label),
                    value = formatDistance(state.distanceValue!!, state.distanceUnit),
                    valueColor = turquoise,
                )
            }
            bd?.let { b ->
                // Métabolisme (estimé, teinte discrète) · Activité · Total — chaque ligne si connue.
                b.bmrKcal?.let {
                    EnergyLine(
                        label = stringResource(R.string.health_dash_cal_bmr_label),
                        value = stringResource(R.string.health_dash_cal_kcal, it),
                        valueColor = appColors.textSecondary,
                    )
                }
                b.activeKcal?.let {
                    EnergyLine(
                        label = stringResource(R.string.health_dash_cal_active_label),
                        value = stringResource(R.string.health_dash_cal_kcal, it),
                        valueColor = orangeMedium,
                    )
                }
                b.totalKcal?.let {
                    EnergyLine(
                        label = stringResource(R.string.health_dash_cal_total_label),
                        value = stringResource(R.string.health_dash_cal_kcal, it),
                        valueColor = turquoise,
                    )
                }
            }
            SourceLine(state.energySource)
        }
    }
    // Tendance 7 jours COMBINÉE (miroir web) : barres = activité kcal (turquoise,
    // identité de section, moyenne pointillée) + courbe = distance (trait clair,
    // échelle indépendante), avec sa légende à points.
    if (state.kcalWeek.any { it.second > 0f } || state.distanceWeek.any { it.second > 0f }) {
        ChartFrame(title = stringResource(R.string.health_dash_week)) {
            EnergyWeekChart(
                kcal = state.kcalWeek.map { it.second },
                distance = state.distanceWeek.map { it.second.takeIf { v -> v > 0f } },
                axisLabels = dayAxisLabels(state.kcalWeek.map { it.first }),
                barColor = turquoise,
                averageKcal = HealthUiAggregations.averageOfFilledDays(state.kcalWeek.map { it.second }),
                distanceUnit = state.distanceUnit,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LegendDot(turquoise, stringResource(R.string.health_dash_cal_active_label))
                LegendDot(appColors.textPrimary, stringResource(R.string.health_dash_distance_label))
            }
        }
    }
}

/** Ligne « label … valeur » d'une mesure d'énergie (label secondaire à gauche, valeur colorée à droite). */
@Composable
private fun EnergyLine(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = appColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Ligne de provenance de la donnée (masquée si aucune donnée). */
@Composable
private fun SourceLine(source: EnergySource) {
    if (source == EnergySource.NONE) return
    Text(
        text = if (source == EnergySource.WATCH) stringResource(R.string.health_dash_source_watch)
        else stringResource(R.string.health_dash_source_hc),
        color = appColors.textTertiary,
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * Suivi du poids : pesées MANUELLES (`health_metrics` WEIGHT_KG, jamais lues de
 * Health Connect — indépendance voulue) + tendance 7 j / 30 j en courbe (jours sans
 * pesée absents, aucune interpolation ; anneau ROUGE cliquable par jour manquant →
 * dialog pré-positionné sur ce jour). Le « + » ouvre le dialog de pesée (calendrier
 * pour combler un jour oublié, 1 valeur/jour, re-saisie = écrase). [onLogWeight]
 * reçoit le jour ciblé (ISO) ou null (aujourd'hui). Identité couleur : yellowMedium
 * (teinte libre de la palette, les autres sections tiennent vert/orange/bleu/turquoise).
 */
@Composable
private fun WeightSection(
    state: HealthDashboardUiState,
    onLogWeight: (String?) -> Unit,
) {
    val logLabel = stringResource(R.string.health_dash_weight_log)
    SectionCard(title = stringResource(R.string.health_dash_weight_title), titleColor = yellowMedium) {
        if (state.weightKg == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmptyLine()
                Spacer(Modifier.weight(1f))
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    tint = appColors.textPrimary,
                    hasBackground = true,
                    customBackgroundColor = firstBlue,
                    onClick = { onLogWeight(null) },
                    modifier = Modifier.semantics { contentDescription = logLabel },
                )
            }
        } else {
            // Ligne façon FC (bleed + SpaceEvenly, bords du cadre compris) : dernière
            // pesée (hero) · moyenne des pesées 7 j · date en chip (toujours, parité
            // web) · bouton « + ».
            val avg = HealthUiAggregations.averageOfFilledDays(state.weightWeek.map { it.second ?: 0f })
            Row(
                modifier = Modifier.bleedCardPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = stringResource(R.string.health_dash_weight_value, formatKg(state.weightKg)),
                    color = appColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                avg?.let {
                    Text(
                        text = stringResource(R.string.health_dash_weight_avg, formatKg(it)),
                        color = appColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.weightDate?.let { MeasureTimePill(formatDayMonth(it)) }
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    tint = appColors.textPrimary,          // icône blanche
                    hasBackground = true,
                    customBackgroundColor = firstBlue,     // pastille firstBlue (cf. réglage objectif Pas)
                    onClick = { onLogWeight(null) },
                    modifier = Modifier.semantics { contentDescription = logLabel },
                )
            }
        }
    }
    // Tendances en cadres compacts indépendants (fond thirdBlue, comme les charts
    // des autres sections) : 7 jours (1 quantième par slot) puis 30 jours (repères épars).
    val weightName = stringResource(R.string.health_dash_weight_title)
    if (state.weightWeek.any { it.second != null }) {
        DailyEntryChartFrame(
            title = stringResource(R.string.health_dash_week),
            pairs = state.weightWeek,
            sparseLabels = false,
            lineColor = yellowMedium,
            onEmptySlotClick = onLogWeight,
            valueSuffix = " kg",
            seriesName = weightName,
        )
    }
    if (state.weightMonth.any { it.second != null }) {
        DailyEntryChartFrame(
            title = stringResource(R.string.health_dash_month),
            pairs = state.weightMonth,
            sparseLabels = true,
            lineColor = yellowMedium,
            onEmptySlotClick = onLogWeight,
            valueSuffix = " kg",
            seriesName = weightName,
        )
    }
}

/**
 * Section Stress : SCORE 0..100 saisi manuellement (Samsung n'expose pas le stress
 * dans Health Connect), classé en 5 catégories par tranches de 20 (modèle Samsung) —
 * code couleur par catégorie (vert → rouge) sur le score, le libellé et les points
 * des courbes. Ligne façon FC : « X/100 » (hero coloré) · libellé de catégorie ·
 * date en chip · bouton « + » (dialog slider + calendrier rétro-datable) + tendances
 * 7 j / 30 j (points rouges des jours manquants cliquables). Identité : brightPurple.
 */
@Composable
private fun StressSection(
    state: HealthDashboardUiState,
    onLogStress: (String?) -> Unit,
) {
    val logLabel = stringResource(R.string.health_dash_stress_log)
    SectionCard(title = stringResource(R.string.health_dash_stress_title), titleColor = brightPurple) {
        if (state.stressScore == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmptyLine()
                Spacer(Modifier.weight(1f))
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    tint = appColors.textPrimary,
                    hasBackground = true,
                    customBackgroundColor = firstBlue,
                    onClick = { onLogStress(null) },
                    modifier = Modifier.semantics { contentDescription = logLabel },
                )
            }
        } else {
            Row(
                modifier = Modifier.bleedCardPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    // Score à la couleur de catégorie, « /100 » neutre (blanc).
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = stressCategoryColor(state.stressScore))) {
                            append(state.stressScore.toString())
                        }
                        append("/100")
                    },
                    color = appColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(stressCategoryLabelRes(state.stressScore)),
                    color = stressCategoryColor(state.stressScore),
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.stressDate?.let { MeasureTimePill(formatDayMonth(it)) }
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    tint = appColors.textPrimary,
                    hasBackground = true,
                    customBackgroundColor = firstBlue,
                    onClick = { onLogStress(null) },
                    modifier = Modifier.semantics { contentDescription = logLabel },
                )
            }
        }
    }
    val scoreFormat: (Float) -> String = { it.roundToInt().toString() }
    // Points des mesures colorés par CATÉGORIE (vert → rouge) — la courbe reste violette.
    val categoryPointColor: (Float) -> Color = { stressCategoryColor(it.roundToInt()) }
    val stressName = stringResource(R.string.health_dash_stress_title)
    if (state.stressWeek.any { it.second != null }) {
        DailyEntryChartFrame(
            title = stringResource(R.string.health_dash_week),
            pairs = state.stressWeek,
            sparseLabels = false,
            lineColor = brightPurple,
            onEmptySlotClick = onLogStress,
            valueFormat = scoreFormat,
            pointColor = categoryPointColor,
            valueSuffix = "/100",
            seriesName = stressName,
        )
    }
    if (state.stressMonth.any { it.second != null }) {
        DailyEntryChartFrame(
            title = stringResource(R.string.health_dash_month),
            pairs = state.stressMonth,
            sparseLabels = true,
            lineColor = brightPurple,
            onEmptySlotClick = onLogStress,
            valueFormat = scoreFormat,
            pointColor = categoryPointColor,
            valueSuffix = "/100",
            seriesName = stressName,
        )
    }
}

/** Cadre de tendance d'une saisie quotidienne (poids, stress) : chart ligne nu dans
 *  un [ChartFrame] thirdBlue, courbe à la couleur d'identité [lineColor], point
 *  rouge par jour manquant → [onEmptySlotClick] avec la date ISO du slot. */
@Composable
private fun DailyEntryChartFrame(
    title: String,
    pairs: List<Pair<String, Float?>>,
    sparseLabels: Boolean,
    lineColor: Color,
    onEmptySlotClick: (String) -> Unit,
    valueFormat: ((Float) -> String)? = null,
    pointColor: ((Float) -> Color)? = null,
    valueSuffix: String = "",
    seriesName: String = "",
) {
    ChartFrame(title = title) {
        TrendLineChart(
            values = pairs.map { it.second },
            axisLabels = if (sparseLabels) {
                sparseDayAxisLabels(pairs.map { it.first })
            } else {
                dayAxisLabels(pairs.map { it.first })
            },
            lineColor = lineColor,
            valueFormat = valueFormat,
            emptySlotColor = redMedium,
            onEmptySlotClick = { slot -> onEmptySlotClick(pairs[slot].first) },
            pointColor = pointColor,
            valueSuffix = valueSuffix,
            seriesName = seriesName,
            // Tooltip : quantième/mois du jour tapé (les labels 30 j sont épars).
            tooltipLabel = { i ->
                val d = LocalDate.parse(pairs[i].first)
                "${d.dayOfMonth}/${d.monthValue}"
            },
        )
    }
}

/** Repères épars « j/m » pour la vue 30 jours (5 dates réparties sur la fenêtre). */
private fun sparseDayAxisLabels(dates: List<String>): List<String> {
    if (dates.isEmpty()) return emptyList()
    val indexes = listOf(0, dates.size / 4, dates.size / 2, dates.size * 3 / 4, dates.size - 1).distinct()
    return indexes.map { i ->
        val d = LocalDate.parse(dates[i])
        "${d.dayOfMonth}/${d.monthValue}"
    }
}

/** Poids en kg : entier si rond, sinon 1 décimale (séparateur selon la locale in-app). */
@Composable
private fun formatKg(value: Float): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (value % 1f == 0f) String.format(locale, "%.0f", value) else String.format(locale, "%.1f", value)
}

/** Date courte localisée « 5 juil. » pour la pastille de dernière pesée. */
@Composable
private fun formatDayMonth(isoDate: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMM", locale))
    }.getOrDefault(isoDate)
}

/** Distance en m, basculée en km au-delà de 1000 m (1 décimale). Unités non traduites (universelles). */
@Composable
private fun formatDistance(value: Float, unit: String): String =
    if (unit == "m" && value >= 1000f) {
        stringResource(R.string.health_dash_distance_km, ((value / 100f).roundToInt() / 10f).toString())
    } else {
        stringResource(R.string.health_dash_distance_m, value.roundToInt())
    }

/* ------------------------------ Primitives ------------------------------ */

/**
 * Étend l'élément sur toute la largeur de la card (annule le padding horizontal
 * 16 dp de [SectionCard]) : un SpaceEvenly interne rend alors des espaces
 * visuellement égaux, bords du cadre compris.
 */
private fun Modifier.bleedCardPadding(): Modifier = layout { measurable, constraints ->
    val extra = 32.dp.roundToPx() // 2 × 16 dp de padding horizontal de la card
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun SectionCard(
    title: String,
    titleColor: Color = appColors.divider,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgRecessed)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Titre de card : TitledDivider C1 à la couleur d'identité de la section (miroir web),
        // défaut grayBlue (uniforme avec les cadres charts) si non précisé.
        TitledDivider(title = title, color = titleColor)
        content()
    }
}

/**
 * Cadre indépendant (fond thirdBlue, coins arrondis style app) : titre de bloc +
 * chart « nu ». Le chart sort de la card de section et vit dans son propre cadre,
 * avec un padding de card standard (16/12) pour rester aéré.
 */
@Composable
private fun ChartFrame(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgRecessed) // thirdBlue (palette design system)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), // respiration titre → chart
    ) {
        // Titre de bloc (niveau « default ») : TitledDivider C1 en couleur par défaut.
        TitledDivider(title = title)
        content()
    }
}

/** Cadre « aujourd'hui » (intraday 30 min) : chart nu sur le fond thirdBlue du cadre,
 *  axe de base en grayBlue. */
@Composable
private fun IntradayChartFrame(values: List<Float>, barColor: Color, valueSuffix: String = "", seriesName: String = "") {
    ChartFrame(title = stringResource(R.string.health_dash_intraday)) {
        HealthBarChart(
            values = values,
            axisLabels = hourlyAxisLabels(),
            barColor = barColor,
            background = Color.Transparent,
            trackColor = Color.Transparent, // piste transparente : seules les barres pleines se voient (fond = cadre thirdBlue)
            axisColor = GrayBlue,
            contentPadding = PaddingValues(0.dp),
            valueSuffix = valueSuffix,
            tooltipLabel = { HealthConnectMapper.slotIndexHhmm(it) },
            seriesName = seriesName,
        )
    }
}

/**
 * Cadre « 7 jours » en COURBE (pas / FC) : [TrendLineChart] à la couleur du domaine — courbe lissée +
 * aire dégradée + axe Y gradué + ligne de moyenne (jours renseignés) avec sa valeur en haut à droite.
 * Jours vides = point absent (0 → null, aucune interpolation, comme la page Poids).
 */
@Composable
private fun WeekLineFrame(pairs: List<Pair<String, Float>>, lineColor: Color, valueSuffix: String = "", seriesName: String = "") {
    val locale = LocalConfiguration.current.locales[0]
    val values = pairs.map { it.second.takeIf { v -> v > 0f } }
    val average = HealthUiAggregations.averageOfFilledDays(pairs.map { it.second })
    ChartFrame(title = stringResource(R.string.health_dash_week)) {
        TrendLineChart(
            values = values,
            axisLabels = dayAxisLabels(pairs.map { it.first }),
            lineColor = lineColor,
            averageLine = average,
            valueFormat = { String.format(locale, "%,d", it.roundToInt()) },
            valueSuffix = valueSuffix,
            seriesName = seriesName,
        )
    }
}

/**
 * Cadre « 7 jours » en BARRES (sommeil) : chart nu, sans axe (quantième du jour seul sous chaque barre) +
 * ligne pointillée de moyenne des jours renseignés par-dessus les barres.
 */
@Composable
private fun WeekChartFrame(pairs: List<Pair<String, Float>>, barColor: Color, valueSuffix: String = "", seriesName: String = "") {
    val values = pairs.map { it.second }
    ChartFrame(title = stringResource(R.string.health_dash_week)) {
        HealthBarChart(
            values = values,
            axisLabels = dayAxisLabels(pairs.map { it.first }),
            barColor = barColor,
            background = Color.Transparent,
            trackColor = Color.Transparent, // piste transparente : seules les barres pleines se voient (fond = cadre thirdBlue)
            axisColor = null, // pas d'axe sur la variante 7 jours
            averageLine = HealthUiAggregations.averageOfFilledDays(values),
            contentPadding = PaddingValues(0.dp),
            valueSuffix = valueSuffix,
            seriesName = seriesName,
        )
    }
}

@Composable
private fun BigValue(text: String) {
    Text(
        text = text,
        color = appColors.textPrimary,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Pastille « live » discrète : fond vert atténué + point + libellé (snackbarSuccess). */
@Composable
private fun LivePill() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(appColors.snackbarSuccess.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(appColors.snackbarSuccess),
        )
        Text(
            text = stringResource(R.string.health_dash_live_badge),
            color = appColors.snackbarSuccess,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Paire « label secondaire + heure en avant » pour les heures de sommeil. */
@Composable
private fun SleepTimeLabel(label: String, time: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = appColors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = time,
            color = appColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Pastille « heure de la dernière mesure » : chip neutre (mesure ponctuelle, ≠ statut live). */
@Composable
private fun MeasureTimePill(text: String) {
    Text(
        text = text,
        color = appColors.textSecondary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(CircleShape)
            .background(appColors.bgSurface)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyLine() {
    Text(
        text = stringResource(R.string.health_dash_empty),
        color = appColors.textTertiary,
        style = MaterialTheme.typography.bodyMedium,
    )
}
