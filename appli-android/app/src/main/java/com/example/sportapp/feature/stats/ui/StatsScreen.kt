package com.example.sportapp.feature.stats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.Zones
import com.example.sportapp.core.utils.localizedZone
import com.example.sportapp.core.data.paletteForZone
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.stats.ui.components.stats.ChartTypeToggle
import com.example.sportapp.feature.stats.ui.components.stats.CustomRangePickerDialog
import com.example.sportapp.feature.stats.ui.components.stats.GroupFilterChips
import com.example.sportapp.feature.stats.ui.components.stats.MetricToggle
import com.example.sportapp.feature.stats.ui.components.stats.MuscleGroupVolumeChart
import com.example.sportapp.feature.stats.ui.components.stats.RangeChipsRow
import com.example.sportapp.feature.stats.ui.components.stats.SortToggle
import com.example.sportapp.feature.stats.ui.components.stats.ZoneVolumeRadarChart
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.darkOrange
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.designsystem.theme.lightGreen
import com.example.sportapp.designsystem.theme.lightPurple
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.mediumPurple
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redDark
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium
import com.example.sportapp.core.stats.FrequencyStats
import com.example.sportapp.core.stats.MetricType
import com.example.sportapp.core.stats.StatsSortMode
import com.example.sportapp.core.stats.StatsRange
import com.example.sportapp.feature.stats.viewmodel.StatsViewModel
import com.example.sportapp.feature.demo_tour.ui.components.demoHighlight

@Composable
fun StatsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val range by viewModel.range.collectAsState()
    val muscles by viewModel.muscles.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val groupChart by viewModel.muscleGroupWeeklyVolume.collectAsState()
    val muscleGroupChart by viewModel.groupByGroupData.collectAsState()
    val muscleChart by viewModel.muscleByMuscleData.collectAsState()
    val exerciseChart by viewModel.exerciseByExerciseData.collectAsState()
    val visibleGroups by viewModel.visibleGroups.collectAsState()
    val visibleMuscleGroups by viewModel.visibleMuscleGroups.collectAsState()
    val visibleMuscles by viewModel.visibleMuscles.collectAsState()
    val visibleExercises by viewModel.visibleExercises.collectAsState()
    val chartType by viewModel.chartType.collectAsState()
    val metric by viewModel.metric.collectAsState()
    val chartTypeGroup by viewModel.chartTypeGroup.collectAsState()
    val metricGroup by viewModel.metricGroup.collectAsState()
    val chartTypeMuscle by viewModel.chartTypeMuscle.collectAsState()
    val metricMuscle by viewModel.metricMuscle.collectAsState()
    val chartTypeExercise by viewModel.chartTypeExercise.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val metricExercise by viewModel.metricExercise.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val exerciseNameToZone by viewModel.exerciseNameToZone.collectAsState()
    val freq by viewModel.frequencyStats.collectAsState()
    val zoneRadar by viewModel.zoneRadarVolume.collectAsState()

    // Mappings name -> zone construits a partir de la liste muscles (Room).
    // Permet de trier les series par zone (= par couleur) en groupant les
    // muscles/groups d'une meme zone consecutifs dans le chart.
    val muscleNameToZone = remember(muscles) {
        muscles.mapNotNull { m -> m.zone?.let { z -> m.name to z } }.toMap()
    }
    val muscleGroupToZone = remember(muscles) {
        muscles.mapNotNull { m ->
            val g = m.muscleGroup ?: return@mapNotNull null
            val z = m.zone ?: return@mapNotNull null
            g to z
        }.distinct().toMap()
    }
    val zoneIdentity = remember { Zones.ALL.associateWith { it } }

    // Tokens hoistes : `appColors` ne se lit que dans un corps @Composable, pas
    // dans une lambda `remember { }` (non-@Composable). On capture les valeurs ici.
    val primaryActionColor = appColors.primaryAction
    val accentTextColor = appColors.accentText

    val groupColors = remember(primaryActionColor, accentTextColor) {
        mapOf(
            "Chest" to primaryActionColor,
            "Back" to orangeMedium,
            "Shoulders" to accentTextColor,
            "Arms" to redMedium,
            "Legs" to mediumGreen,
            "Core" to yellowMedium,
            "Other" to mediumPurple,
        )
    }

    var showCustomPicker by remember { mutableStateOf(false) }

    // Tri global applique aux 4 sections : alpha (toSortedMap) ou par zone
    // (groupe par zone Zones.ALL puis alpha dans chaque zone).
    val sortedZoneSeries = remember(groupChart.seriesByGroup, sortMode) {
        sortSeriesByMode(groupChart.seriesByGroup, sortMode, zoneIdentity)
    }
    val sortedMuscleGroupSeries = remember(muscleGroupChart.seriesByGroup, sortMode, muscleGroupToZone) {
        sortSeriesByMode(muscleGroupChart.seriesByGroup, sortMode, muscleGroupToZone)
    }
    val sortedMuscleSeries = remember(muscleChart.seriesByGroup, sortMode, muscleNameToZone) {
        sortSeriesByMode(muscleChart.seriesByGroup, sortMode, muscleNameToZone)
    }
    val sortedExerciseSeries = remember(exerciseChart.seriesByGroup, sortMode, exerciseNameToZone) {
        sortSeriesByMode(exerciseChart.seriesByGroup, sortMode, exerciseNameToZone)
    }

    // Pas de `remember` ici car Map.equals est set-based -> il cacherait
    // sortedZoneSeries entre deux ordres differents et filteredSeries
    // resterait stale au tap SortToggle. L'op filterKeys est O(n=6..35),
    // recalcul a chaque recomposition est negligeable.
    val filteredSeries = sortedZoneSeries.filterKeys { it in visibleGroups }
    // Chips Zone : suivent sortMode (ALPHA = alpha, ZONE = ordre Zones.ALL).
    val filterableZones = remember(sortMode) {
        when (sortMode) {
            StatsSortMode.ALPHA -> Zones.ALL.sorted()
            StatsSortMode.ZONE -> Zones.ALL
        }
    }

    // ── Refactor 3-niveaux (2026-05-08) : palettes par zone generees programmatiquement.
    // Chaque muscle_group + chaque muscle precis recoit une nuance de la couleur
    // de sa zone (Pecs/Mid Chest = tons bleus, Lats/Lat Pulldown referrer = tons orange,
    // etc). Permet une legende rapide a parser visuellement ('vert = jambe').

    val musclePalette = remember(primaryActionColor, accentTextColor) {
        listOf(
            primaryActionColor, orangeMedium, accentTextColor, redMedium, mediumGreen,
            yellowMedium, mediumPurple, lightGreen, blueMedium, darkOrange,
            redDark, lightPurple,
        )
    }

    // ── Section muscle_group : palette par zone (17 groups derivee depuis 6 zones)
    val muscleGroupColors = remember(muscles, groupColors) {
        val groupsByZone = muscles
            .mapNotNull { m ->
                val g = m.muscleGroup ?: return@mapNotNull null
                val z = m.zone ?: return@mapNotNull null
                g to z
            }
            .distinct()
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, groupNames) -> groupNames.distinct().sorted() }
        groupsByZone.flatMap { (zone, groupNames) ->
            val zoneColor = groupColors[zone] ?: musclePalette.first()
            val shades = paletteForZone(zoneColor, groupNames.size)
            groupNames.zip(shades)
        }.toMap()
    }
    // Drop remember : Map.equals set-based -> sortedMuscleGroupSeries reste
    // "egal" entre 2 ordres differents -> chips figees au tap A↓Z. L'op est
    // O(17), recalcul a chaque composition sans cout.
    val allMuscleGroupNames = sortedMuscleGroupSeries.keys.toList()
    val effectiveVisibleMuscleGroups = remember(visibleMuscleGroups, allMuscleGroupNames) {
        if (visibleMuscleGroups.isEmpty()) allMuscleGroupNames.toSet() else visibleMuscleGroups
    }
    val filteredMuscleGroupSeries =
        sortedMuscleGroupSeries.filterKeys { it in effectiveVisibleMuscleGroups }

    // ── Section Muscle : palette par zone (35 muscles derivee depuis 6 zones)
    val allMuscleNames = remember(muscles, sortMode, muscleNameToZone) {
        val names = muscles.map { it.name }
        when (sortMode) {
            StatsSortMode.ALPHA -> names.sorted()
            StatsSortMode.ZONE -> names.sortedWith(compareBy(
                { name ->
                    val idx = Zones.ALL.indexOf(muscleNameToZone[name] ?: "Other")
                    if (idx < 0) Int.MAX_VALUE else idx
                },
                { it },
            ))
        }
    }
    val muscleColors = remember(muscles, groupColors, musclePalette) {
        val musclesByZone = muscles
            .mapNotNull { m -> (m.zone ?: return@mapNotNull null).let { it to m.name } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct().sorted() }
        val perZoneColors = musclesByZone.flatMap { (zone, names) ->
            val zoneColor = groupColors[zone] ?: musclePalette.first()
            val shades = paletteForZone(zoneColor, names.size)
            names.zip(shades)
        }.toMap()
        // Fallback pour les muscles sans zone (NULL en DB) : cycle musclePalette.
        allMuscleNames.mapIndexed { idx, name ->
            name to (perZoneColors[name] ?: musclePalette[idx % musclePalette.size])
        }.toMap()
    }
    // Si _visibleMuscles est vide -> tous selectionnes par default
    val effectiveVisibleMuscles = remember(visibleMuscles, allMuscleNames) {
        if (visibleMuscles.isEmpty()) allMuscleNames.toSet() else visibleMuscles
    }
    val filteredMuscleSeries =
        sortedMuscleSeries.filterKeys { it in effectiveVisibleMuscles }

    // ── Section Exercise : palette par zone primaire (chaque exercise recoit
    // une nuance de la couleur de sa zone primaire, comme les muscles).
    // Chips derives de sortedExerciseSeries.keys (= ordre EXACT du graph).
    val allExerciseNames = sortedExerciseSeries.keys.toList()
    val exerciseColors = remember(exercises, exerciseNameToZone, groupColors, musclePalette) {
        val exercisesByZone = exercises
            .mapNotNull { ex ->
                val zone = exerciseNameToZone[ex.name] ?: return@mapNotNull null
                ex.name to zone
            }
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, names) -> names.distinct().sorted() }
        val perZoneColors = exercisesByZone.flatMap { (zone, names) ->
            val zoneColor = groupColors[zone] ?: musclePalette.first()
            val shades = paletteForZone(zoneColor, names.size)
            names.zip(shades)
        }.toMap()
        // Fallback : exercises sans zone connue -> cycle musclePalette.
        allExerciseNames.mapIndexed { idx, name ->
            name to (perZoneColors[name] ?: musclePalette[idx % musclePalette.size])
        }.toMap()
    }
    val effectiveVisibleExercises = remember(visibleExercises, allExerciseNames) {
        if (visibleExercises.isEmpty()) allExerciseNames.toSet() else visibleExercises
    }
    val filteredExerciseSeries =
        sortedExerciseSeries.filterKeys { it in effectiveVisibleExercises }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(horizontal = 18.dp),
    ) {
        // ── HEADER FIXE (sticky) : Training frequency + Sort + Period.
        // Reste visible meme quand on scrolle les charts en dessous (user
        // feedback 2026-05-09).
        Spacer(Modifier.height(12.dp))
        TitledDivider(stringResource(R.string.stats_training_frequency))
        FrequencyCard(stats = freq, metric = metric, weightUnit = weightUnit)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .demoHighlight("stats.range_picker"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortToggle(
                current = sortMode,
                onSelect = viewModel::setSortMode,
            )
            RangeChipsRow(
                modifier = Modifier.weight(1f),
                range = range,
                onSelect = viewModel::setRange,
                onCustomClick = { showCustomPicker = true },
            )
        }
        Spacer(Modifier.height(16.dp))

        // ── ZONE SCROLLABLE : les 4 sections charts. weight(1f) prend
        // l'espace vertical restant sous le header.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {

        // ── Radar 'Equilibre par zone (volume)' : place en tete de la zone
        // scrollable, comme sur le web (radar avant les 4 sections). Volume
        // agrege par zone sur la periode, lecture rapide de la symetrie.
        TitledDivider(stringResource(R.string.stats_zone_balance_title))
        Spacer(Modifier.height(4.dp))
        ZoneVolumeRadarChart(
            data = zoneRadar,
            colorMap = groupColors,
            weightUnit = weightUnit,
            emptyText = stringResource(R.string.stats_zone_balance_empty),
        )
        Spacer(Modifier.height(16.dp))

        // ── Hero chart : titre dynamique selon la metrique
        val weightUnitLabel = com.example.sportapp.feature.onboarding.data.weightLabel(weightUnit)
        val chartTitle = when (metric) {
            MetricType.TOTAL_WEIGHT -> stringResource(R.string.stats_chart_zone_volume, weightUnitLabel)
            MetricType.SETS -> stringResource(R.string.stats_chart_zone_sets)
            MetricType.EXERCISES -> stringResource(R.string.stats_chart_zone_exercises)
        }
        TitledDivider(chartTitle)
        // Row : ChartTypeToggle a GAUCHE (Line / Bar) + MetricToggle a DROITE
        // (Total weight / Sets / Exercises). User feedback 2026-05-07.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartTypeToggle(
                current = chartType,
                onSelect = viewModel::setChartType,
            )
            MetricToggle(
                current = metric,
                onSelect = viewModel::setMetric,
            )
        }
        Box(modifier = Modifier
            .fillMaxWidth()
            .demoHighlight("stats.chart"),
        ) {
            MuscleGroupVolumeChart(
                buckets = groupChart.buckets,
                seriesByGroup = filteredSeries,
                orderedKeys = filteredSeries.keys.toList(),
                colorMap = groupColors,
                granularity = groupChart.granularity,
                chartType = chartType,
                metric = metric,
            )
        }

        // ── Zone chips uniquement (le RangeChipsRow est globalement en haut).
        Spacer(Modifier.height(8.dp))
        GroupFilterChips(
            groups = filterableZones,
            selectedGroups = visibleGroups,
            colorMap = groupColors,
            onToggle = viewModel::toggleGroup,
        )

        // ── Section 'X / Group' : agrege par muscle_group intermediaire (Pecs,
        // Lats, Triceps, ...). 17 groups au total. Toggles INDEPENDANTS de Zone
        // et Muscle. Refactor 3-niveaux 2026-05-08.
        Spacer(Modifier.height(16.dp))
        val muscleGroupChartTitle = when (metricGroup) {
            MetricType.TOTAL_WEIGHT -> stringResource(R.string.stats_chart_group_volume, weightUnitLabel)
            MetricType.SETS -> stringResource(R.string.stats_chart_group_sets)
            MetricType.EXERCISES -> stringResource(R.string.stats_chart_group_exercises)
        }
        TitledDivider(muscleGroupChartTitle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartTypeToggle(
                current = chartTypeGroup,
                onSelect = viewModel::setChartTypeGroup,
            )
            MetricToggle(
                current = metricGroup,
                onSelect = viewModel::setMetricGroup,
            )
        }
        MuscleGroupVolumeChart(
            buckets = muscleGroupChart.buckets,
            seriesByGroup = filteredMuscleGroupSeries,
            orderedKeys = filteredMuscleGroupSeries.keys.toList(),
            colorMap = muscleGroupColors,
            granularity = muscleGroupChart.granularity,
            chartType = chartTypeGroup,
            metric = metricGroup,
            weightUnit = weightUnit,
        )
        Spacer(Modifier.height(8.dp))
        GroupFilterChips(
            groups = allMuscleGroupNames,
            selectedGroups = effectiveVisibleMuscleGroups,
            colorMap = muscleGroupColors,
            onToggle = viewModel::toggleMuscleGroup,
        )

        // ── Section 'Volume / Muscle' : agrege par muscle precis individuel
        // (Mid Chest, Triceps Long head, ...). 35 muscles au total. Toggles
        // INDEPENDANTS de Zone/Group (user peut avoir Zone en BAR/Sets et
        // Muscle en LINE/Volume).
        Spacer(Modifier.height(16.dp))
        val muscleChartTitle = when (metricMuscle) {
            MetricType.TOTAL_WEIGHT -> stringResource(R.string.stats_chart_muscle_volume, weightUnitLabel)
            MetricType.SETS -> stringResource(R.string.stats_chart_muscle_sets)
            MetricType.EXERCISES -> stringResource(R.string.stats_chart_muscle_exercises)
        }
        TitledDivider(muscleChartTitle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartTypeToggle(
                current = chartTypeMuscle,
                onSelect = viewModel::setChartTypeMuscle,
            )
            MetricToggle(
                current = metricMuscle,
                onSelect = viewModel::setMetricMuscle,
            )
        }
        MuscleGroupVolumeChart(
            buckets = muscleChart.buckets,
            seriesByGroup = filteredMuscleSeries,
            orderedKeys = filteredMuscleSeries.keys.toList(),
            colorMap = muscleColors,
            granularity = muscleChart.granularity,
            chartType = chartTypeMuscle,
            metric = metricMuscle,
            weightUnit = weightUnit,
        )
        Spacer(Modifier.height(8.dp))
        GroupFilterChips(
            groups = allMuscleNames,
            selectedGroups = effectiveVisibleMuscles,
            colorMap = muscleColors,
            onToggle = { name -> viewModel.toggleMuscle(name, allMuscleNames) },
        )

        // ── Section 'X / Exercise' : agrege par exercise individuel.
        // Toggles INDEPENDANTS de Zone/Muscle. Range partage (commun aux 3
        // graphes). Pour metric EXERCISES, label = 'Sessions' (count distinct
        // sessions ou cet exercise apparait — plus parlant que count distinct
        // exercise_uuid qui vaut trivialement 1).
        Spacer(Modifier.height(16.dp))
        val exerciseChartTitle = when (metricExercise) {
            MetricType.TOTAL_WEIGHT -> stringResource(R.string.stats_chart_exercise_volume, weightUnitLabel)
            MetricType.SETS -> stringResource(R.string.stats_chart_exercise_sets)
            MetricType.EXERCISES -> stringResource(R.string.stats_chart_exercise_sessions)
        }
        TitledDivider(exerciseChartTitle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartTypeToggle(
                current = chartTypeExercise,
                onSelect = viewModel::setChartTypeExercise,
            )
            MetricToggle(
                current = metricExercise,
                onSelect = viewModel::setMetricExercise,
            )
        }
        MuscleGroupVolumeChart(
            buckets = exerciseChart.buckets,
            seriesByGroup = filteredExerciseSeries,
            orderedKeys = filteredExerciseSeries.keys.toList(),
            colorMap = exerciseColors,
            granularity = exerciseChart.granularity,
            chartType = chartTypeExercise,
            metric = metricExercise,
            weightUnit = weightUnit,
        )
        Spacer(Modifier.height(8.dp))
        GroupFilterChips(
            groups = allExerciseNames,
            selectedGroups = effectiveVisibleExercises,
            colorMap = exerciseColors,
            onToggle = { name -> viewModel.toggleExercise(name, allExerciseNames) },
        )

        Spacer(Modifier.height(24.dp))
        }  // fin de la zone scrollable
    }

    if (showCustomPicker) {
        CustomRangePickerDialog(
            initialRange = range,
            onConfirm = { start, end ->
                viewModel.setRange(StatsRange.Custom(start, end))
                showCustomPicker = false
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

@Composable
private fun FrequencyCard(
    stats: FrequencyStats,
    metric: MetricType,
    weightUnit: com.example.sportapp.feature.onboarding.data.WeightUnit,
) {
    val perWeekStr = String.format(java.util.Locale.getDefault(), "%.1f", stats.avgSessionsPerWeek)
    val (totalLabel, totalValueStr, totalSuffix) = when (metric) {
        MetricType.TOTAL_WEIGHT -> Triple(
            stringResource(R.string.stats_freq_total_volume),
            com.example.sportapp.feature.onboarding.data.formatVolume(stats.totalValue, weightUnit),
            " ${com.example.sportapp.feature.onboarding.data.weightLabel(weightUnit)}",
        )
        MetricType.SETS -> Triple(stringResource(R.string.stats_freq_total_sets), "${stats.totalValue.toInt()}", "")
        MetricType.EXERCISES -> Triple(stringResource(R.string.stats_freq_total_exos), "${stats.totalValue.toInt()}", "")
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.bgRecessed),
    ) {
        // Padding equilibre horizontal/vertical (avant : 14.dp partout
        // donnait l'impression d'un cadre trop epais en haut/bas car le
        // contenu = 1 Row courte). User feedback 2026-05-07.
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FrequencyStat(label = stringResource(R.string.stats_freq_sessions), value = "${stats.sessionsCount}")
                FrequencyStat(label = stringResource(R.string.stats_freq_per_week), value = perWeekStr)
                FrequencyStat(
                    label = stringResource(R.string.stats_freq_top_group),
                    // Zone storage = EN canonique. Display = localise.
                    value = stats.topGroup?.let { localizedZone(it) } ?: "—",
                    valueColor = stats.topGroup?.let { topGroupColor(it) } ?: lightGrayBlue,
                )
                FrequencyStat(label = totalLabel, value = totalValueStr, suffix = totalSuffix)
            }
        }
    }
}

@Composable
private fun FrequencyStat(
    label: String,
    value: String,
    suffix: String = "",
    valueColor: Color = appColors.primaryAction,
) {
    // User feedback 2026-05-07 : label en haut, value en bas (et pas l'inverse).
    // Taille values reduite a 15sp (vs 18sp) pour gagner de la place verticale
    // sur le header sticky (user feedback 2026-05-09).
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = lightGrayBlue,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(1.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    color = valueColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 1.dp, bottom = 1.dp),
                )
            }
        }
    }
}

// `formatVolume(kg)` local supprimé -- remplacé par
// `com.example.sportapp.feature.onboarding.data.formatVolume(kg, unit)` qui gère
// la conversion KG/LBS au boundary UI.

@Composable
private fun topGroupColor(group: String): Color = when (group) {
    "Chest" -> appColors.primaryAction
    "Back" -> orangeMedium
    "Shoulders" -> appColors.accentText
    "Arms" -> redMedium
    "Legs" -> mediumGreen
    "Core" -> yellowMedium
    else -> mediumPurple
}

/**
 * Trie un Map<key, series> selon le StatsSortMode. ALPHA = ordre alphabetique
 * des keys ; ZONE = ordre par zone (selon Zones.ALL) puis alpha dans la
 * zone. La key sans zone connue (keyToZone[k] == null) est repoussee en
 * fin de liste. Retourne un LinkedHashMap pour preserver l'ordre.
 */
private fun sortSeriesByMode(
    series: Map<String, List<Float>>,
    sortMode: StatsSortMode,
    keyToZone: Map<String, String>,
): Map<String, List<Float>> {
    return when (sortMode) {
        StatsSortMode.ALPHA -> series.toSortedMap()
        StatsSortMode.ZONE -> series.entries
            .sortedWith(
                compareBy(
                    { entry ->
                        val zone = keyToZone[entry.key] ?: return@compareBy Int.MAX_VALUE
                        val idx = Zones.ALL.indexOf(zone)
                        if (idx < 0) Int.MAX_VALUE else idx
                    },
                    { it.key },
                )
            )
            .associateTo(LinkedHashMap()) { it.toPair() }
    }
}

