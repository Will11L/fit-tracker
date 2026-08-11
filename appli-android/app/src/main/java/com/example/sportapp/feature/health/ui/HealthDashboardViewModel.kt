package com.example.sportapp.feature.health.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.HealthGoalDao
import com.example.sportapp.core.data.local.HealthMetricDao
import com.example.sportapp.core.data.local.HealthStepCountDao
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.MeProfileUpdateRequest
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils.getTodayIsoDay
import com.example.sportapp.core.utils.CustomDateUtils.shiftIsoDay
import com.example.sportapp.feature.health.data.HealthConnectManager
import com.example.sportapp.feature.health.data.HealthImporter
import com.example.sportapp.feature.health.domain.CalorieBreakdown
import com.example.sportapp.feature.health.domain.CalorieMath
import com.example.sportapp.feature.health.domain.HealthConnectMapper
import com.example.sportapp.feature.health.domain.HealthUiAggregations
import com.example.sportapp.feature.health.domain.SleepSessionReading
import com.example.sportapp.feature.health.domain.HealthUuids
import com.example.sportapp.feature.health.wear.WearLiveState
import com.example.sportapp.feature.health.wear.WearRequester
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/** État du hub Santé (lecture Room + overlay live montre, affichage-only). */
data class HealthDashboardUiState(
    val today: String = "",
    val displaySteps: Int = 0,
    val liveSteps: Boolean = false,   // le compteur affiché vient-il de la montre live ?
    val goalTarget: Int? = null,
    val progress: Float = 0f,
    val hourlySteps: List<Float> = emptyList(),      // pas par heure (0..23) du jour
    val stepsWeek: List<Pair<String, Float>> = emptyList(),   // (date, total) 7 j
    val hrTodayBpm: Int? = null,
    val hrWeek: List<Pair<String, Float>> = emptyList(),      // (date, bpm) 7 j
    val sleepMinutes: Int? = null,
    val sleepDate: String? = null,
    val sleepWeek: List<Pair<String, Float>> = emptyList(),   // (date, minutes) 7 j
    val spo2Percent: Int? = null,
    val spo2Date: String? = null,
    val spo2Week: List<Pair<String, Float>> = emptyList(),   // (date, %) 7 j
    // Distance & calories (Option B) : distance + total mesuré ; BMR/actives dérivés (CalorieBreakdown).
    val distanceValue: Float? = null,
    val distanceUnit: String = "m",
    val calorieBreakdown: CalorieBreakdown? = null,
    val energySource: EnergySource = EnergySource.NONE,
    // Tendance 7 j énergie (chart combiné) : kcal actives (sinon total) + distance.
    val kcalWeek: List<Pair<String, Float>> = emptyList(),
    val distanceWeek: List<Pair<String, Float>> = emptyList(),
    // Suivi du poids (pesées manuelles WEIGHT_KG, sans HC) : dernière pesée +
    // tendances 7 j / 30 j (null = jour sans pesée, jamais interpolé).
    val weightKg: Float? = null,
    val weightDate: String? = null,
    val weightWeek: List<Pair<String, Float?>> = emptyList(),
    val weightMonth: List<Pair<String, Float?>> = emptyList(),
    // Toutes les pesées par date ISO (calendrier du dialog : points + pré-remplissage).
    val weightByDate: Map<String, Float> = emptyMap(),
    // Stress (saisie manuelle : SCORE 0..100 classé en 5 catégories, modèle Samsung —
    // jamais importé, Samsung ne l'expose pas dans HC).
    val stressScore: Int? = null,
    val stressDate: String? = null,
    val stressWeek: List<Pair<String, Float?>> = emptyList(),
    val stressMonth: List<Pair<String, Float?>> = emptyList(),
    val stressByDate: Map<String, Float> = emptyMap(),
)

/** Source de la ligne distance/calories affichée (décision #4 : afficher la provenance). */
enum class EnergySource { NONE, WATCH, HEALTH_CONNECT }

/** Instantané du profil /me utile au BMR (poids/taille/naissance/sexe), depuis CurrentUserManager. */
private data class ProfileSnapshot(
    val weightKg: Float?,
    val heightCm: Float?,
    val birthDate: String?,
    val sex: String?,
)

/**
 * Hub Santé : lit la couche Room persistée (buckets pas + métriques + objectif) en
 * réactif et l'agrège via [HealthUiAggregations] (pur). Le compteur de pas near-
 * real-time superpose la valeur live de la montre ([WearLiveState], affichage-only)
 * quand elle est plus fraîche. Le réglage d'objectif upsert un [HealthGoal] STEPS
 * (uuid déterministe du jour) + push sync existant. Aucun accès direct HC ici.
 */
@HiltViewModel
class HealthDashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    stepDao: HealthStepCountDao,
    private val metricDao: HealthMetricDao,
    private val goalDao: HealthGoalDao,
    private val syncEngine: SyncEngine,
    private val wearRequester: WearRequester,
    private val healthImporter: HealthImporter,
    private val manager: HealthConnectManager,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)
    private val today = getTodayIsoDay()

    // 7 jours calendaires [today-6 .. today] : slots réservés pour les jours vides.
    private val weekDays: List<String> = (6 downTo 0).map { shiftIsoDay(today, -it.toLong()) }

    // 30 jours calendaires [today-29 .. today] : tendance longue du poids.
    private val monthDays: List<String> = (29 downTo 0).map { shiftIsoDay(today, -it.toLong()) }

    // FC + sommeil intraday (tranches de 30 min) : calculés à l'affichage depuis le
    // manager (non persistés, décision de sourcing) → mis à jour à chaque refresh.
    // Les pas intraday viennent de Room (dans uiState).
    private val _hourlyHr = MutableStateFlow(List(HealthConnectMapper.SLOTS_PER_DAY) { 0f })
    val hourlyHr: StateFlow<List<Float>> = _hourlyHr.asStateFlow()
    private val _hourlySleep = MutableStateFlow(List(HealthConnectMapper.SLOTS_PER_DAY) { 0f })
    val hourlySleep: StateFlow<List<Float>> = _hourlySleep.asStateFlow()

    // Sessions de sommeil des dernières 24 h (heures au lit / endormi par session),
    // même fenêtre que l'aperçu Données santé — affichage-only.
    private val _sleepSessions = MutableStateFlow<List<SleepSessionReading>>(emptyList())
    val sleepSessions: StateFlow<List<SleepSessionReading>> = _sleepSessions.asStateFlow()

    // Phases de sommeil par jour (7 j) [profond, léger, paradoxal, éveillé] et SpO2
    // intraday — affichage-only, comme les autres séries calculées depuis le manager.
    private val _sleepWeekStages = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val sleepWeekStages: StateFlow<Map<String, List<Float>>> = _sleepWeekStages.asStateFlow()
    private val _hourlySpo2 = MutableStateFlow(List(HealthConnectMapper.SLOTS_PER_DAY) { 0f })
    val hourlySpo2: StateFlow<List<Float>> = _hourlySpo2.asStateFlow()
    // Hypnogramme « Cette nuit » : slices de phases des sessions finissant aujourd'hui
    // (chronologie relative à minuit, veille au soir en négatif). Vide → fallback barres.
    private val _sleepPhases = MutableStateFlow<List<HealthUiAggregations.SleepPhasePoint>>(emptyList())
    val sleepPhases: StateFlow<List<HealthUiAggregations.SleepPhasePoint>> = _sleepPhases.asStateFlow()

    private val buckets = stepDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val metrics = metricDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val goals = goalDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    // Profil du user courant depuis /me (CurrentUserManager) : la table `users` Room n'est pas
    // peuplée → c'est la source du poids/taille/naissance/sexe pour estimer le BMR (Option B).
    private val profile = combine(
        CurrentUserManager.weightKgFlow,
        CurrentUserManager.heightCmFlow,
        CurrentUserManager.birthDateFlow,
        CurrentUserManager.sexFlow,
    ) { w, h, bd, sex -> ProfileSnapshot(w, h, bd, sex) }

    val uiState: StateFlow<HealthDashboardUiState> =
        combine(buckets, metrics, goals, WearLiveState.live, profile) { b, m, g, live, p ->
            // Tranche courante : borne l'affichage intraday à « maintenant » (pas de
            // barres futures — artefact de proration Samsung). Recalculé à chaque
            // recomposition du state (refresh / changement Room).
            val currentSlot = HealthConnectMapper.slotOfDay(Instant.now(), ZoneId.systemDefault())
            val roomSteps = HealthUiAggregations.stepsForDay(b, today)
            val liveSteps = live?.steps?.toInt()
            val useLive = liveSteps != null && liveSteps >= roomSteps
            val displaySteps = if (useLive) liveSteps!! else roomSteps
            val goal = HealthUiAggregations.activeStepGoal(g, today)
            val hrToday = HealthUiAggregations.latestMetric(m.filter { it.date == today }, "HEART_RATE")
            val sleep = HealthUiAggregations.latestMetric(m, "SLEEP")
            val spo2 = HealthUiAggregations.latestMetric(m, "SPO2")
            val weight = HealthUiAggregations.latestMetric(m, HealthUiAggregations.METRIC_TYPE_WEIGHT)
            val stress = HealthUiAggregations.latestMetric(m, HealthUiAggregations.METRIC_TYPE_STRESS)

            // Distance & calories. La sémantique des calories vient du TYPE stocké : ACTIVE_CALORIES
            // (montre — Health Services CALORIES_DAILY = actives sur la Watch4) → total dérivé = actives
            // + BMR ; TOTAL_CALORIES (HC = vrai total) → actives dérivées = max(0, total − BMR). Les
            // actives (montre) priment quand présentes. BMR estimé du profil.
            val distanceMetric = HealthUiAggregations.latestMetric(m, "DISTANCE")
            val activeCal = HealthUiAggregations.latestMetric(m, "ACTIVE_CALORIES")?.value?.roundToInt()
            val totalCal = HealthUiAggregations.latestMetric(m, "TOTAL_CALORIES")?.value?.roundToInt()
            val breakdown = when {
                activeCal != null -> CalorieMath.fromActive(activeCal, p.weightKg, p.heightCm, p.birthDate, p.sex, LocalDate.now())
                totalCal != null -> CalorieMath.fromTotal(totalCal, p.weightKg, p.heightCm, p.birthDate, p.sex, LocalDate.now())
                else -> null
            }
            // Source : « Montre » si le live montre porte l'énergie du jour OU si une donnée ACTIVE
            // (origine montre) est persistée ; sinon HC si total/distance existent ; sinon rien.
            val watchEnergyToday = live != null &&
                (live.distanceM != null || live.caloriesKcal != null) &&
                Instant.ofEpochMilli(live.timestampMillis).atZone(ZoneId.systemDefault())
                    .toLocalDate().toString() == today
            val energySource = when {
                watchEnergyToday || activeCal != null -> EnergySource.WATCH
                totalCal != null || distanceMetric != null -> EnergySource.HEALTH_CONNECT
                else -> EnergySource.NONE
            }

            HealthDashboardUiState(
                today = today,
                displaySteps = displaySteps,
                liveSteps = useLive,
                goalTarget = goal?.target?.roundToInt(),
                progress = HealthUiAggregations.stepProgress(displaySteps, goal?.target),
                hourlySteps = HealthUiAggregations.clipFutureSlots(
                    HealthUiAggregations.stepsBySlot(b, today),
                    currentSlot,
                ),
                stepsWeek = HealthUiAggregations.stepsByDayCalendar(b, weekDays),
                hrTodayBpm = hrToday?.value?.roundToInt(),
                hrWeek = HealthUiAggregations.metricByDayCalendar(m, "HEART_RATE", weekDays),
                sleepMinutes = sleep?.value?.roundToInt(),
                sleepDate = sleep?.date,
                sleepWeek = HealthUiAggregations.metricByDayCalendar(m, "SLEEP", weekDays),
                spo2Percent = spo2?.value?.roundToInt(),
                spo2Date = spo2?.date,
                spo2Week = HealthUiAggregations.metricByDayCalendar(m, "SPO2", weekDays),
                distanceValue = distanceMetric?.value,
                distanceUnit = distanceMetric?.unit ?: "m",
                calorieBreakdown = breakdown,
                energySource = energySource,
                // Tendance 7 j : actives (montre) prioritaires, sinon total (HC) — même règle que le breakdown.
                kcalWeek = HealthUiAggregations.metricByDayCalendar(
                    m,
                    if (activeCal != null || totalCal == null) "ACTIVE_CALORIES" else "TOTAL_CALORIES",
                    weekDays,
                ),
                distanceWeek = HealthUiAggregations.metricByDayCalendar(m, "DISTANCE", weekDays),
                weightKg = weight?.value,
                weightDate = weight?.date,
                weightWeek = HealthUiAggregations.weightByDayCalendar(m, weekDays),
                weightMonth = HealthUiAggregations.weightByDayCalendar(m, monthDays),
                weightByDate = m.filter { it.type == HealthUiAggregations.METRIC_TYPE_WEIGHT }
                    .associate { it.date to it.value },
                stressScore = stress?.value?.roundToInt(),
                stressDate = stress?.date,
                stressWeek = HealthUiAggregations.nullableMetricByDayCalendar(
                    m, HealthUiAggregations.METRIC_TYPE_STRESS, weekDays,
                ),
                stressMonth = HealthUiAggregations.nullableMetricByDayCalendar(
                    m, HealthUiAggregations.METRIC_TYPE_STRESS, monthDays,
                ),
                stressByDate = m.filter { it.type == HealthUiAggregations.METRIC_TYPE_STRESS }
                    .associate { it.date to it.value },
            )
        }.stateIn(viewModelScope, started, HealthDashboardUiState(today = today))

    init {
        refresh()
    }

    /**
     * Rafraîchit la source à l'arrivée sur le hub (open + ON_RESUME) et au tap ↻ :
     * réimporte HC → Room best-effort (les Flows du dashboard se mettent à jour
     * seuls) + pull montre (fraîcheur live). Pas de push sync complet ici (léger).
     */
    fun refresh() {
        viewModelScope.launch {
            runCatching { healthImporter.importRecentToRoom() }
            // L'import d'ouverture d'écran écrit en Room synced=false (buckets de pas +
            // métriques HC + énergie montre). On pousse ces entités en best-effort
            // silencieux pour ne pas laisser de compteurs "unsync" après une simple visite
            // du hub. pushEntityClass court-circuite si rien n'est à pousser
            // (getUnsyncedLocals/getPendingDeletions vides) → pas de requête ni de boucle.
            runCatching {
                syncEngine.pushEntityClass(HealthStepCount::class)
                syncEngine.pushEntityClass(HealthMetric::class)
            }
            runCatching { wearRequester.requestLive() }
            loadHourly()
        }
    }

    /** FC + sommeil horaires du jour depuis le manager (affichage-only). */
    private suspend fun loadHourly() {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        runCatching { _hourlyHr.value = manager.readHourlyHeartRate(start, end, zone) }
        runCatching { _hourlySleep.value = manager.readHourlySleepMinutes(start, end, zone) }
        // Sessions sur 24 h glissantes : la nuit commence souvent la veille.
        val now = Instant.now()
        runCatching { _sleepSessions.value = manager.readSleepSessions(now.minus(Duration.ofHours(24)), now, zone) }
        runCatching {
            _sleepPhases.value = HealthUiAggregations.sleepPhaseTimeline(
                manager.readSleepPhaseSlices(now.minus(Duration.ofHours(24)), now, zone)
                    .filter { it.endDate == today },
            )
        }
        runCatching { _sleepWeekStages.value = manager.readSleepStagesByDay(weekDays, zone) }
        runCatching { _hourlySpo2.value = manager.readHourlySpo2(start, end, zone) }
    }

    /** Règle l'objectif de pas du jour : upsert HealthGoal STEPS + push sync. */
    fun setStepGoal(target: Int) {
        viewModelScope.launch {
            val userId = CurrentUserManager.userId ?: 0
            val uuid = HealthUiAggregations.stepGoalUuid(userId, today)
            val goal = HealthGoal(
                uuid = uuid,
                userId = userId,
                type = HealthUiAggregations.GOAL_TYPE_STEPS,
                target = target.toFloat(),
                effectiveFrom = today,
            )
            if (goalDao.getByUUID(uuid) == null) goalDao.insert(goal) else goalDao.update(goal)
            syncEngine.pushEntityClass(HealthGoal::class)
        }
    }

    /**
     * Enregistre une pesée (saisie manuelle, sans HC) : upsert `health_metrics`
     * type WEIGHT_KG (uuid déterministe user+type+date → 1 row/jour, la re-saisie
     * d'un jour écrase) + push sync. [date] permet de combler un jour oublié
     * (défaut aujourd'hui). **Synergie profil** : la pesée met aussi à jour
     * `User.weightKg` via PATCH /me/profile (le BMR des calories se recale seul) —
     * SEULEMENT si elle est la plus récente (une pesée rétro-datée ne doit pas
     * écraser le poids courant) ; best-effort : hors-ligne, la métrique locale
     * part quand même au prochain sync, le profil se recalera à la prochaine
     * pesée en ligne.
     */
    fun logWeight(kg: Float, date: String = today) {
        viewModelScope.launch {
            val userId = CurrentUserManager.userId ?: return@launch
            val uuid = HealthUuids.metric(userId, HealthUiAggregations.METRIC_TYPE_WEIGHT, date, null)
            val metric = HealthMetric(
                uuid = uuid,
                userId = userId,
                type = HealthUiAggregations.METRIC_TYPE_WEIGHT,
                value = kg,
                unit = "kg",
                date = date,
                startTime = null,
            )
            if (metricDao.getByUUID(uuid) == null) metricDao.insert(metric) else metricDao.update(metric)
            syncEngine.pushEntityClass(HealthMetric::class)
            // Comparaison ISO lexicographique : profil mis à jour ssi cette pesée
            // devient (ou reste) la plus récente connue.
            val latestDate = uiState.value.weightDate
            if (latestDate == null || date >= latestDate) {
                runCatching {
                    val updated = RetrofitInstance.userService.updateMeProfile(MeProfileUpdateRequest(weightKg = kg))
                    CurrentUserManager.setProfile(appContext, updated)
                }
            }
        }
    }

    /**
     * Enregistre un SCORE de stress 0..100 (saisie manuelle, classé en 5 catégories
     * par tranches de 20 — modèle Samsung ; jamais importé, Samsung n'expose pas le
     * stress dans HC) : upsert `health_metrics` type STRESS (uuid déterministe
     * user+type+date → 1 valeur/jour, la re-saisie écrase) + push sync. [date]
     * permet de combler un jour oublié (défaut aujourd'hui). Aucune synergie profil.
     */
    fun logStress(score: Int, date: String = today) {
        viewModelScope.launch {
            val userId = CurrentUserManager.userId ?: return@launch
            val uuid = HealthUuids.metric(userId, HealthUiAggregations.METRIC_TYPE_STRESS, date, null)
            val metric = HealthMetric(
                uuid = uuid,
                userId = userId,
                type = HealthUiAggregations.METRIC_TYPE_STRESS,
                value = score.toFloat(),
                unit = "score",
                date = date,
                startTime = null,
            )
            if (metricDao.getByUUID(uuid) == null) metricDao.insert(metric) else metricDao.update(metric)
            syncEngine.pushEntityClass(HealthMetric::class)
        }
    }
}
