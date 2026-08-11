package com.example.sportapp.feature.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.feature.health.data.HealthConnectManager
import com.example.sportapp.feature.health.data.StepSamplingScheduler
import com.example.sportapp.feature.health.data.StepSamplingStore
import com.example.sportapp.feature.health.domain.HealthConnectAvailability
import com.example.sportapp.feature.health.domain.HealthDataType
import com.example.sportapp.feature.health.domain.HealthSnapshot
import com.example.sportapp.feature.health.domain.PointMeasurement
import com.example.sportapp.feature.health.wear.WearLiveState
import com.example.sportapp.feature.health.wear.WearLiveStatus
import com.example.sportapp.feature.health.wear.WearRequester
import com.example.sportapp.feature.health.wear.wearLiveStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** État de connexion aux données santé (uniquement pertinent si HC installé). */
enum class HealthConnectionState { CONNECTED, PARTIAL, DISCONNECTED }

data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.NOT_SUPPORTED,
    val connectionState: HealthConnectionState = HealthConnectionState.DISCONNECTED,
    val snapshot: HealthSnapshot? = null,
    val loading: Boolean = false,
)

/** Live montre (Data Layer) prêt à afficher : valeurs + âge de la dernière mesure. */
data class WearLiveDisplay(
    val steps: Long,
    val hr: Int?,
    val ageSeconds: Long,
)

/** Section « Montre — live » : valeurs (si présentes) + statut d'affichage. */
data class WearLiveUi(
    val display: WearLiveDisplay?,
    val status: WearLiveStatus,
)

/**
 * Pilote l'écran Settings « Connecter les données santé » : disponibilité HC,
 * état des permissions par type, et aperçu de lecture (preuve que les 5 types
 * remontent). Aucune écriture vers HC.
 */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val manager: HealthConnectManager,
    private val wearRequester: WearRequester,
    private val samplingStore: StepSamplingStore,
    private val samplingScheduler: StepSamplingScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    /** Input du launcher de permission (les 5 permissions de lecture). */
    val permissions: Set<String> = manager.permissions

    /** Input du launcher de permission de lecture en arrière-plan (échantillonnage). */
    val backgroundPermissions: Set<String> = manager.backgroundPermissions

    fun permissionRequestContract() = manager.permissionRequestContract()
    fun healthConnectSettingsIntent() = manager.healthConnectSettingsIntent()
    fun healthConnectStoreUri() = manager.healthConnectStoreUri()

    /** Toggle Settings « vraies tranches de pas » : activé = worker d'échantillonnage. */
    val samplingEnabled: StateFlow<Boolean> =
        samplingStore.enabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Tap sur le toggle d'échantillonnage. Activation : requiert la permission de
     * lecture en arrière-plan — si déjà accordée, on active ; sinon on lance sa demande
     * ([requestBackground]) et l'activation se fera au retour ([onBackgroundPermissionResult]).
     * Désactivation : annule le worker et réinitialise l'état.
     */
    fun onSamplingToggle(enabled: Boolean, requestBackground: () -> Unit) {
        if (!enabled) {
            setSampling(false)
            return
        }
        viewModelScope.launch {
            if (manager.hasBackgroundPermission()) setSampling(true) else requestBackground()
        }
    }

    /** Retour du launcher de permission background : active si la permission est là. */
    fun onBackgroundPermissionResult(@Suppress("UNUSED_PARAMETER") granted: Set<String>) {
        viewModelScope.launch {
            if (manager.hasBackgroundPermission()) setSampling(true)
        }
    }

    private fun setSampling(enabled: Boolean) {
        viewModelScope.launch {
            samplingStore.setEnabled(enabled)
            if (enabled) {
                samplingScheduler.enable()
            } else {
                samplingScheduler.disable()
                samplingStore.clearState() // repart en rattrapage à la prochaine activation
            }
        }
    }

    private val _querying = MutableStateFlow(false)
    private var queryJob: Job? = null
    private var pullStartMillis = 0L

    /**
     * Section « Montre — live » : valeurs (Data Layer) recombinées à un ticker 1 s
     * (âge) + état d'interrogation → statut (LIVE / STALE / QUERYING / DISCONNECTED).
     */
    val wearLiveUi: StateFlow<WearLiveUi> =
        combine(WearLiveState.live, freshnessTicker(), _querying) { payload, now, querying ->
            val display = payload?.let {
                WearLiveDisplay(
                    steps = it.steps,
                    hr = it.hr,
                    ageSeconds = ((now - it.timestampMillis) / 1000).coerceAtLeast(0),
                )
            }
            WearLiveUi(
                display = display,
                status = wearLiveStatus(
                    hasData = display != null,
                    ageSeconds = display?.ageSeconds ?: 0L,
                    querying = querying,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearLiveUi(null, WearLiveStatus.DISCONNECTED))

    private fun freshnessTicker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    /**
     * Déclenche un pull de la montre (open écran + refresh). État `querying` levé
     * jusqu'à réception d'une réponse fraîche (timestamp ≥ début du pull) ou timeout.
     */
    fun pullWatch() {
        queryJob?.cancel()
        pullStartMillis = System.currentTimeMillis()
        queryJob = viewModelScope.launch {
            _querying.value = true
            wearRequester.requestLive()
            delay(QUERY_TIMEOUT_MS)
            _querying.value = false
        }
    }

    init {
        refresh()
        // Coupe l'état "interrogation" dès qu'une réponse fraîche de la montre arrive.
        viewModelScope.launch {
            WearLiveState.live.collect { payload ->
                if (payload != null && payload.timestampMillis >= pullStartMillis && _querying.value) {
                    queryJob?.cancel()
                    _querying.value = false
                }
            }
        }
    }

    /**
     * Recharge disponibilité + permissions + aperçu. Appelé à l'ouverture, au
     * retour de l'écran système (ON_RESUME) et après le flow de permission.
     */
    fun refresh() {
        val availability = manager.availability
        if (availability != HealthConnectAvailability.INSTALLED) {
            _uiState.value = HealthConnectUiState(
                availability = availability,
                connectionState = HealthConnectionState.DISCONNECTED,
            )
            return
        }
        viewModelScope.launch {
            val granted = manager.grantedPermissions()
            val connection = when {
                granted.containsAll(manager.permissions) -> HealthConnectionState.CONNECTED
                granted.any { it in manager.permissions } -> HealthConnectionState.PARTIAL
                else -> HealthConnectionState.DISCONNECTED
            }
            _uiState.update { it.copy(availability = availability, connectionState = connection) }
            if (connection == HealthConnectionState.DISCONNECTED) {
                _uiState.update { it.copy(snapshot = null) }
            } else {
                loadSnapshot()
            }
        }
    }

    /** Callback du launcher de permission : on rafraîchit l'état. */
    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") granted: Set<String>) = refresh()

    private suspend fun loadSnapshot() {
        _uiState.update { it.copy(loading = true) }
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant()
        val last24h = now.minus(Duration.ofHours(24))

        // Pas / distance / calories : journée locale COMPLÈTE [minuit, minuit+1j).
        // Samsung Health écrit un unique record couvrant toute la journée ; Health
        // Connect prorate linéairement un record au temps couvert par la fenêtre,
        // donc lire [minuit, now] renverrait la fraction de journée écoulée (bug
        // 3756 pas → 2233 à 14h16 = 59 %), pas le total courant. La fenêtre jour
        // entier couvre ce record à 100 % : la SOMME est exacte (contrat total=SUM).
        val stepsToday = manager.readStepBuckets(startOfDay, endOfDay, zone).sumOf { it.steps }
        val todayMetrics = manager.readMetricReadings(startOfDay, endOfDay, zone).associateBy { it.type }
        // FC moyenne (agrégat, = valeur poussée au serveur) : fenêtre 24 h.
        val nightMetrics = manager.readMetricReadings(last24h, now, zone).associateBy { it.type }
        // FC dernière mesure (sample le plus récent) : fenêtre 24 h.
        val lastHr = manager.lastHeartRate(last24h, now, zone)
        // Sommeil : par session sur 24 h (temps dormi + temps au lit, décision produit).
        val sleepSessions = manager.readSleepSessions(last24h, now, zone)

        _uiState.update {
            it.copy(
                loading = false,
                snapshot = HealthSnapshot(
                    stepsToday = stepsToday,
                    distanceMeters = todayMetrics[HealthDataType.DISTANCE]?.value,
                    activeKcal = todayMetrics[HealthDataType.ACTIVE_CALORIES]?.value,
                    avgHeartRateBpm = nightMetrics[HealthDataType.HEART_RATE]?.value,
                    lastHeartRate = lastHr,
                    spo2 = nightMetrics[HealthDataType.SPO2]?.let { r ->
                        r.startTime?.let { t -> PointMeasurement(r.value, t) }
                    },
                    sleepSessions = sleepSessions,
                ),
            )
        }
    }

    private companion object {
        const val QUERY_TIMEOUT_MS = 10_000L // délai max d'attente d'une réponse montre
    }
}
