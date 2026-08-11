package com.example.sportapp.feature.stats.viewmodel
import com.example.sportapp.core.stats.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.MuscleGroups
import com.example.sportapp.core.data.Zones
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.data.model.projections.ExerciseNameBucketValueRow
import com.example.sportapp.core.data.model.projections.MuscleGroupBucketValueRow
import com.example.sportapp.core.data.model.projections.MuscleNameBucketValueRow
import com.example.sportapp.core.data.model.projections.MuscleNameDailyVolumeRow
import com.example.sportapp.core.data.model.projections.MuscleNameWeeklyVolumeRow
import com.example.sportapp.core.data.model.projections.ZoneBucketValueRow
import com.example.sportapp.core.utils.CustomDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject


/**
 * ViewModel Overview des Stats : delivre les agregats globaux + listes
 * d'entites navigables (muscles, exercises). Les sous-ecrans (Muscle/Exercise)
 * auront leurs propres ViewModels paramétrés par UUID.
 *
 * Range partagé entre les 3 ecrans via [StatsRangeState] singleton.
 *
 * Cf. CLAUDE.md historique 2026-05-07 (B3-2 Etape 3).
 */

/**
 * Donnees pour le multi-courbes 'Volume by muscle group' :
 * - [buckets] : axe X (jours en mode DAILY 'YYYY-MM-DD' ou semaines ISO en
 *   mode WEEKLY 'YYYY-WW', uniques et triees).
 * - [seriesByGroup] : map groupe -> liste de volumes alignee sur [buckets]
 *   (0f pour les buckets sans data). Les groupes sans aucun volume sur la
 *   periode sont exclus pour ne pas polluer la legende.
 * - [granularity] : daily si range court (<= 14 jours), sinon weekly.
 *   Permet au chart de formatter X axis en consequence ('5/5' vs 'W19').
 */
data class MuscleGroupChartData(
    val buckets: List<String>,
    val seriesByGroup: Map<String, List<Float>>,
    val granularity: ChartGranularity = ChartGranularity.WEEKLY,
) {
    companion object { val EMPTY = MuscleGroupChartData(emptyList(), emptyMap(), ChartGranularity.WEEKLY) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val actualWorkoutDao: ActualWorkoutDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    muscleDao: MuscleDao,
    exerciseDao: ExerciseDao,
    exerciseMuscleDao: com.example.sportapp.core.data.local.ExerciseMuscleDao,
    val rangeState: StatsRangeState,
    private val onboardingRepo: com.example.sportapp.feature.onboarding.data.OnboardingRepository,
) : ViewModel() {

    /** Unité poids choisie par l'user (KG par défaut). Affecte tous les
     *  affichages weight/volume Stats. Stockage interne reste KG canonique. */
    val weightUnit: StateFlow<com.example.sportapp.feature.onboarding.data.WeightUnit> =
        onboardingRepo.preferences
            .map { it.weightUnit }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.example.sportapp.feature.onboarding.data.WeightUnit.KG
            )

    private val zone: ZoneId = ZoneId.systemDefault()

    val range: StateFlow<StatsRange> = rangeState.range

    private val rangeBounds: StateFlow<Pair<String, String>> =
        range
            .map { it.computeBounds(CustomDateUtils.getTodayLocalDate(zone)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                StatsRange.Last7Days.computeBounds(CustomDateUtils.getTodayLocalDate(zone))
            )

    val activeDaysCount: StateFlow<Int> =
        rangeBounds
            .flatMapLatest { (start, end) -> actualWorkoutDao.observeActiveDaysCount(start, end) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalDaysInRange: StateFlow<Int> =
        rangeBounds
            .map { (start, end) ->
                val s = LocalDate.parse(start)
                val e = LocalDate.parse(end)
                (ChronoUnit.DAYS.between(s, e).toInt() + 1).coerceAtLeast(1)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val muscles: StateFlow<List<Muscle>> =
        muscleDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exercises: StateFlow<List<Exercise>> =
        exerciseDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Mapping `exerciseName -> primaryZoneName`, dérivé de exercise_muscles JOIN
     * muscles. Pour chaque exercise, on prend la zone du muscle ciblé avec le
     * coefficient le plus élevé. Permet de trier la section Exercise par zone
     * (le toggle SortToggle ZONE regroupe alors les exercises selon la zone
     * qu'ils ciblent en priorité). User feedback 2026-05-09.
     */
    val exerciseNameToZone: StateFlow<Map<String, String>> =
        kotlinx.coroutines.flow.combine(
            exerciseDao.observeAll(),
            muscleDao.observeAll(),
            exerciseMuscleDao.observeAll(),
        ) { exs, mus, links ->
            val muscleByUuid = mus.associateBy { it.uuid }
            val exerciseByUuid = exs.associateBy { it.uuid }
            // Group by exercise_uuid, pick the link with max coefficient,
            // resolve its muscle.zone.
            links.groupBy { it.exerciseUUID }
                .mapNotNull { (exUuid, list) ->
                    val ex = exerciseByUuid[exUuid] ?: return@mapNotNull null
                    val topLink = list.maxByOrNull { it.coefficient } ?: return@mapNotNull null
                    val muscle = muscleByUuid[topLink.muscleUUID] ?: return@mapNotNull null
                    val zone = muscle.zone ?: return@mapNotNull null
                    ex.name to zone
                }
                .toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _metric = MutableStateFlow(MetricType.SETS)
    val metric: StateFlow<MetricType> = _metric.asStateFlow()

    fun setMetric(metric: MetricType) {
        _metric.value = metric
    }

    // ─── Tri global des 4 sections (alpha vs zone color) ───
    // Default = ZONE (cohérent avec la palette par zone : visuel groupe).
    private val _sortMode = MutableStateFlow(StatsSortMode.ZONE)
    val sortMode: StateFlow<StatsSortMode> = _sortMode.asStateFlow()

    fun setSortMode(mode: StatsSortMode) {
        _sortMode.value = mode
    }

    // ─── Toggles independants pour la section 'Volume / Muscle' ───
    // Permet d'avoir Zone en BAR/Sets et Muscle en LINE/Volume simultanement.

    private val _chartTypeMuscle = MutableStateFlow(ChartType.BAR)
    val chartTypeMuscle: StateFlow<ChartType> = _chartTypeMuscle.asStateFlow()

    fun setChartTypeMuscle(type: ChartType) {
        _chartTypeMuscle.value = type
    }

    private val _metricMuscle = MutableStateFlow(MetricType.SETS)
    val metricMuscle: StateFlow<MetricType> = _metricMuscle.asStateFlow()

    fun setMetricMuscle(metric: MetricType) {
        _metricMuscle.value = metric
    }

    // ─── Toggles independants section 'X / Exercise' ───

    private val _chartTypeExercise = MutableStateFlow(ChartType.BAR)
    val chartTypeExercise: StateFlow<ChartType> = _chartTypeExercise.asStateFlow()

    fun setChartTypeExercise(type: ChartType) {
        _chartTypeExercise.value = type
    }

    private val _metricExercise = MutableStateFlow(MetricType.SETS)
    val metricExercise: StateFlow<MetricType> = _metricExercise.asStateFlow()

    fun setMetricExercise(metric: MetricType) {
        _metricExercise.value = metric
    }

    /** Set des exercises individuels visibles dans le chart 'X / Exercise'. */
    private val _visibleExercises = MutableStateFlow<Set<String>>(emptySet())
    val visibleExercises: StateFlow<Set<String>> = _visibleExercises.asStateFlow()

    fun toggleExercise(name: String, allExercises: List<String>) {
        val current = _visibleExercises.value
        val seedSet = if (current.isEmpty()) allExercises.toSet() else current
        _visibleExercises.value =
            if (name in seedSet) seedSet - name else seedSet + name
    }

    /**
     * Set des muscles individuels visibles dans le chart 'Volume / Muscle'.
     * Default = vide (UI interprete comme 'tous'). toggleMuscle remplit le set
     * a partir du clic — quand un muscle est dans le set, il est filtre selon
     * la logique 'in set = visible'.
     */
    private val _visibleMuscles = MutableStateFlow<Set<String>>(emptySet())
    val visibleMuscles: StateFlow<Set<String>> = _visibleMuscles.asStateFlow()

    fun toggleMuscle(name: String, allMuscles: List<String>) {
        val current = _visibleMuscles.value
        // Init lazy : 1er toggle -> on part de tous selectionnes (sauf celui clique).
        val seedSet = if (current.isEmpty()) allMuscles.toSet() else current
        _visibleMuscles.value =
            if (name in seedSet) seedSet - name else seedSet + name
    }

    /**
     * Section 'Volume / Zone' (niveau haut de la hierarchie 3-niveaux).
     * Lit la colonne `muscles.zone` directement via JOIN SQL (pas de mapping
     * client comme c'etait le cas avant le refactor 2026-05-08).
     */
    val muscleGroupWeeklyVolume: StateFlow<MuscleGroupChartData> =
        combine(rangeBounds, _metric) { bounds, metric -> bounds to metric }
            .flatMapLatest { (bounds, metric) ->
                val (start, end) = bounds
                val days = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1
                val gran = if (days <= 14) ChartGranularity.DAILY else ChartGranularity.WEEKLY
                when (metric) {
                    MetricType.TOTAL_WEIGHT -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllZonesDailyVolume(start, end)
                    } else {
                        actualWorkoutSetDao.observeAllZonesWeeklyVolume(start, end)
                    }
                    MetricType.SETS -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllZonesDailySetCount(start, end)
                    } else {
                        actualWorkoutSetDao.observeAllZonesWeeklySetCount(start, end)
                    }
                    MetricType.EXERCISES -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllZonesDailyExerciseCount(start, end)
                    } else {
                        actualWorkoutSetDao.observeAllZonesWeeklyExerciseCount(start, end)
                    }
                }.map { rows -> aggregateByZone(rows, gran) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleGroupChartData.EMPTY)

    /**
     * Radar 'Equilibre par zone (volume)' : volume KG agrege par zone sur la
     * periode (toujours en TOTAL_WEIGHT, independant du toggle metric des
     * sections). Les 6 zones canoniques sont toujours presentes (axe a 0 si
     * pas de volume) → hexagone stable. Vide si aucune seance (placeholder UI).
     * Port du `zoneRadar` web (cf. stats-page.ts).
     */
    val zoneRadarVolume: StateFlow<List<ZoneVolumeDatum>> =
        rangeBounds
            .flatMapLatest { (start, end) ->
                actualWorkoutSetDao.observeAllZonesWeeklyVolume(start, end)
            }
            .map { rows ->
                val volumeByZone = rows
                    .groupBy { it.zoneName }
                    .mapValues { (_, list) -> list.sumOf { it.value.toDouble() }.toFloat() }
                val zones = Zones.ALL.map { z -> ZoneVolumeDatum(z, volumeByZone[z] ?: 0f) }
                buildZoneVolumeRadar(zones)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Set des zones visibles dans le chart 'Volume / Zone'. Default = tous (Zones.ALL).
     * Le chart filtre `seriesByGroup` selon ce set pour permettre a l'user de
     * toggler chaque zone via filter chips.
     */
    private val _visibleGroups = MutableStateFlow<Set<String>>(Zones.ALL.toSet())
    val visibleGroups: StateFlow<Set<String>> = _visibleGroups.asStateFlow()

    fun toggleGroup(group: String) {
        val current = _visibleGroups.value
        _visibleGroups.value =
            if (group in current) current - group
            else current + group
    }

    // ─── Toggles independants pour la section 'Volume / Group' (refactor 2026-05-08) ───
    // Niveau intermediaire de la hierarchie : 17 muscle_groups (Pecs, Lats, Triceps, ...)
    // entre Zone (6 zones) et Muscle (35 muscles precis).

    private val _chartTypeGroup = MutableStateFlow(ChartType.BAR)
    val chartTypeGroup: StateFlow<ChartType> = _chartTypeGroup.asStateFlow()

    fun setChartTypeGroup(type: ChartType) {
        _chartTypeGroup.value = type
    }

    private val _metricGroup = MutableStateFlow(MetricType.SETS)
    val metricGroup: StateFlow<MetricType> = _metricGroup.asStateFlow()

    fun setMetricGroup(metric: MetricType) {
        _metricGroup.value = metric
    }

    private val _visibleMuscleGroups = MutableStateFlow<Set<String>>(MuscleGroups.ALL.toSet())
    val visibleMuscleGroups: StateFlow<Set<String>> = _visibleMuscleGroups.asStateFlow()

    fun toggleMuscleGroup(group: String) {
        val current = _visibleMuscleGroups.value
        _visibleMuscleGroups.value =
            if (group in current) current - group
            else current + group
    }

    val groupByGroupData: StateFlow<MuscleGroupChartData> =
        combine(rangeBounds, _metricGroup) { bounds, metric -> bounds to metric }
            .flatMapLatest { (bounds, metric) ->
                val (start, end) = bounds
                val days = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1
                val gran = if (days <= 14) ChartGranularity.DAILY else ChartGranularity.WEEKLY
                when (metric) {
                    MetricType.TOTAL_WEIGHT -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllGroupsDailyVolume(start, end)
                    } else {
                        actualWorkoutSetDao.observeAllGroupsWeeklyVolume(start, end)
                    }
                    MetricType.SETS -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllGroupsDailySetCount(start, end)
                    } else {
                        actualWorkoutSetDao.observeAllGroupsWeeklySetCount(start, end)
                    }
                    MetricType.EXERCISES -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllGroupsDailyExerciseCount(start, end)
                    } else {
                        actualWorkoutSetDao.observeAllGroupsWeeklyExerciseCount(start, end)
                    }
                }.map { rows -> aggregateByMuscleGroup(rows, gran) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleGroupChartData.EMPTY)

    private val _chartType = MutableStateFlow(ChartType.BAR)
    val chartType: StateFlow<ChartType> = _chartType.asStateFlow()

    fun setChartType(type: ChartType) {
        _chartType.value = type
    }

    /**
     * Stats agregees pour la card 'Training frequency' enrichie : sessions
     * count, total volume, top group (groupe avec le plus gros volume cumule
     * sur la periode), avg sessions/week. Tout derive de [muscleGroupWeeklyVolume]
     * + [activeDaysCount] + [totalDaysInRange] sans nouvelle query DAO.
     */
    val frequencyStats: StateFlow<FrequencyStats> =
        combine(
            muscleGroupWeeklyVolume,
            activeDaysCount,
            totalDaysInRange,
        ) { chart, active, total ->
            val totalVolume = chart.seriesByGroup.values
                .sumOf { series -> series.sumOf { it.toDouble() } }
                .toFloat()
            val topGroup = chart.seriesByGroup
                .mapValues { (_, series) -> series.sumOf { it.toDouble() }.toFloat() }
                .maxByOrNull { it.value }
                ?.key
            val avgPerWeek = if (total <= 0) 0f else active.toFloat() * 7f / total.toFloat()
            FrequencyStats(
                sessionsCount = active,
                avgSessionsPerWeek = avgPerWeek,
                totalDaysInRange = total,
                totalValue = totalVolume,
                topGroup = topGroup,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FrequencyStats.EMPTY)

    fun setRange(range: StatsRange) {
        rangeState.setRange(range)
    }

    /**
     * Aggregation au niveau Zone. La projection `ZoneBucketValueRow` contient
     * deja la zone (pas de mapping client necessaire — refactor 2026-05-08).
     * `Zones.ALL` donne l'ordre canonique (Chest, Back, Shoulders, ...).
     */
    private fun aggregateByZone(
        rows: List<ZoneBucketValueRow>,
        granularity: ChartGranularity,
    ): MuscleGroupChartData {
        if (rows.isEmpty()) return MuscleGroupChartData.EMPTY
        val buckets = rows.map { it.bucket }.distinct().sorted()
        val sumByZoneBucket: Map<Pair<String, String>, Float> = rows
            .groupBy { it.zoneName to it.bucket }
            .mapValues { (_, list) -> list.sumOf { it.value.toDouble() }.toFloat() }
        val seriesByZone = Zones.ALL.associateWith { zone ->
            buckets.map { b -> sumByZoneBucket[zone to b] ?: 0f }
        }.filterValues { series -> series.any { it > 0f } }
        return MuscleGroupChartData(buckets, seriesByZone, granularity)
    }

    /**
     * Aggregation au niveau muscle_group (intermediaire). La projection
     * `MuscleGroupBucketValueRow` contient deja le group (pas de mapping client).
     * `MuscleGroups.ALL` donne l'ordre canonique (Pecs, Lats, Rhomboids, ...).
     */
    private fun aggregateByMuscleGroup(
        rows: List<MuscleGroupBucketValueRow>,
        granularity: ChartGranularity,
    ): MuscleGroupChartData {
        if (rows.isEmpty()) return MuscleGroupChartData.EMPTY
        val buckets = rows.map { it.bucket }.distinct().sorted()
        val sumByGroupBucket: Map<Pair<String, String>, Float> = rows
            .groupBy { it.muscleGroup to it.bucket }
            .mapValues { (_, list) -> list.sumOf { it.value.toDouble() }.toFloat() }
        val seriesByGroup = MuscleGroups.ALL.associateWith { group ->
            buckets.map { b -> sumByGroupBucket[group to b] ?: 0f }
        }.filterValues { series -> series.any { it > 0f } }
        return MuscleGroupChartData(buckets, seriesByGroup, granularity)
    }

    // ─── Section 'Volume / Muscle' : meme data que muscleGroupWeeklyVolume
    // mais agrege par muscleName individuel au lieu de groupe musculaire.

    val muscleByMuscleData: StateFlow<MuscleGroupChartData> =
        combine(rangeBounds, _metricMuscle) { bounds, metric -> bounds to metric }
            .flatMapLatest { (bounds, metric) ->
                val (start, end) = bounds
                val days = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1
                val gran = if (days <= 14) ChartGranularity.DAILY else ChartGranularity.WEEKLY
                when (metric) {
                    MetricType.TOTAL_WEIGHT -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllMusclesDailyVolume(start, end)
                            .map { rows -> aggregateByMuscleDaily(rows) }
                    } else {
                        actualWorkoutSetDao.observeAllMusclesWeeklyVolume(start, end)
                            .map { rows -> aggregateByMuscleWeekly(rows) }
                    }
                    MetricType.SETS -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllMusclesDailySetCount(start, end)
                            .map { rows -> aggregateByMuscleBucket(rows, gran) }
                    } else {
                        actualWorkoutSetDao.observeAllMusclesWeeklySetCount(start, end)
                            .map { rows -> aggregateByMuscleBucket(rows, gran) }
                    }
                    MetricType.EXERCISES -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllMusclesDailyExerciseCount(start, end)
                            .map { rows -> aggregateByMuscleBucket(rows, gran) }
                    } else {
                        actualWorkoutSetDao.observeAllMusclesWeeklyExerciseCount(start, end)
                            .map { rows -> aggregateByMuscleBucket(rows, gran) }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleGroupChartData.EMPTY)

    private fun aggregateByMuscleWeekly(rows: List<MuscleNameWeeklyVolumeRow>): MuscleGroupChartData {
        if (rows.isEmpty()) return MuscleGroupChartData.EMPTY
        val buckets = rows.map { it.weekIso }.distinct().sorted()
        val sumByMuscleBucket = rows
            .groupBy { it.muscleName to it.weekIso }
            .mapValues { (_, list) -> list.sumOf { it.volume.toDouble() }.toFloat() }
        val muscleNames = rows.map { it.muscleName }.distinct().sorted()
        val series = muscleNames.associateWith { name ->
            buckets.map { b -> sumByMuscleBucket[name to b] ?: 0f }
        }.filterValues { s -> s.any { it > 0f } }
        return MuscleGroupChartData(buckets, series, ChartGranularity.WEEKLY)
    }

    private fun aggregateByMuscleDaily(rows: List<MuscleNameDailyVolumeRow>): MuscleGroupChartData {
        if (rows.isEmpty()) return MuscleGroupChartData.EMPTY
        val buckets = rows.map { it.dayIso }.distinct().sorted()
        val sumByMuscleBucket = rows
            .groupBy { it.muscleName to it.dayIso }
            .mapValues { (_, list) -> list.sumOf { it.volume.toDouble() }.toFloat() }
        val muscleNames = rows.map { it.muscleName }.distinct().sorted()
        val series = muscleNames.associateWith { name ->
            buckets.map { b -> sumByMuscleBucket[name to b] ?: 0f }
        }.filterValues { s -> s.any { it > 0f } }
        return MuscleGroupChartData(buckets, series, ChartGranularity.DAILY)
    }

    private fun aggregateByMuscleBucket(
        rows: List<MuscleNameBucketValueRow>,
        granularity: ChartGranularity,
    ): MuscleGroupChartData {
        if (rows.isEmpty()) return MuscleGroupChartData.EMPTY
        val buckets = rows.map { it.bucket }.distinct().sorted()
        val sumByMuscleBucket = rows
            .groupBy { it.muscleName to it.bucket }
            .mapValues { (_, list) -> list.sumOf { it.value.toDouble() }.toFloat() }
        val muscleNames = rows.map { it.muscleName }.distinct().sorted()
        val series = muscleNames.associateWith { name ->
            buckets.map { b -> sumByMuscleBucket[name to b] ?: 0f }
        }.filterValues { s -> s.any { it > 0f } }
        return MuscleGroupChartData(buckets, series, granularity)
    }

    // ─── Section 'X / Exercise' : agrege par exercise individuel ───

    val exerciseByExerciseData: StateFlow<MuscleGroupChartData> =
        combine(rangeBounds, _metricExercise) { bounds, metric -> bounds to metric }
            .flatMapLatest { (bounds, metric) ->
                val (start, end) = bounds
                val days = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1
                val gran = if (days <= 14) ChartGranularity.DAILY else ChartGranularity.WEEKLY
                when (metric) {
                    MetricType.TOTAL_WEIGHT -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllExercisesDailyVolume(start, end)
                            .map { rows -> aggregateByExerciseBucket(rows, gran) }
                    } else {
                        actualWorkoutSetDao.observeAllExercisesWeeklyVolume(start, end)
                            .map { rows -> aggregateByExerciseBucket(rows, gran) }
                    }
                    MetricType.SETS -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllExercisesDailySetCount(start, end)
                            .map { rows -> aggregateByExerciseBucket(rows, gran) }
                    } else {
                        actualWorkoutSetDao.observeAllExercisesWeeklySetCount(start, end)
                            .map { rows -> aggregateByExerciseBucket(rows, gran) }
                    }
                    MetricType.EXERCISES -> if (gran == ChartGranularity.DAILY) {
                        actualWorkoutSetDao.observeAllExercisesDailySessionCount(start, end)
                            .map { rows -> aggregateByExerciseBucket(rows, gran) }
                    } else {
                        actualWorkoutSetDao.observeAllExercisesWeeklySessionCount(start, end)
                            .map { rows -> aggregateByExerciseBucket(rows, gran) }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleGroupChartData.EMPTY)

    private fun aggregateByExerciseBucket(
        rows: List<ExerciseNameBucketValueRow>,
        granularity: ChartGranularity,
    ): MuscleGroupChartData {
        if (rows.isEmpty()) return MuscleGroupChartData.EMPTY
        val buckets = rows.map { it.bucket }.distinct().sorted()
        val sumByExerciseBucket = rows
            .groupBy { it.exerciseName to it.bucket }
            .mapValues { (_, list) -> list.sumOf { it.value.toDouble() }.toFloat() }
        val exerciseNames = rows.map { it.exerciseName }.distinct().sorted()
        val series = exerciseNames.associateWith { name ->
            buckets.map { b -> sumByExerciseBucket[name to b] ?: 0f }
        }.filterValues { s -> s.any { it > 0f } }
        return MuscleGroupChartData(buckets, series, granularity)
    }
}

