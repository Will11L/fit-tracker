package com.example.sportapp.wear

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.edit
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Lit les capteurs de la montre en direct et pousse un heartbeat live (≤ 1/s) au
 * téléphone via le Data Layer. Actif uniquement en premier plan ([start]/[stop]
 * pilotés par le cycle de vie de l'écran) → pas de conso capteur/réseau en fond.
 *
 * - **FC** : Health Services `MeasureClient` (HEART_RATE_BPM, ~1 Hz).
 * - **Pas du jour** : Health Services `PassiveMonitoringClient` + `STEPS_DAILY`
 *   (total du jour géré par la plateforme, reset minuit) si disponible ; sinon
 *   **fallback** compteur cumulatif + baseline ([DailyStepCalculator]).
 */
class WearHealthViewModel(app: Application) : AndroidViewModel(app) {

    private val sender = HealthLiveSender(app)
    private val healthClient = HealthServices.getClient(app)
    private val measureClient = healthClient.measureClient
    private val passiveClient = healthClient.passiveMonitoringClient
    private val sensorManager = app.getSystemService(SensorManager::class.java)

    private val _steps = MutableStateFlow<Long?>(null)
    val steps: StateFlow<Long?> = _steps.asStateFlow()

    private val _hr = MutableStateFlow<Int?>(null)
    val hr: StateFlow<Int?> = _hr.asStateFlow()

    // Agrégats quotidiens montre (m / kcal totales) → persistés côté téléphone (Option B).
    private val _distanceM = MutableStateFlow<Int?>(null)
    val distanceM: StateFlow<Int?> = _distanceM.asStateFlow()

    private val _caloriesKcal = MutableStateFlow<Int?>(null)
    val caloriesKcal: StateFlow<Int?> = _caloriesKcal.asStateFlow()

    private val _sending = MutableStateFlow(false)
    /** True si le dernier heartbeat a atteint au moins un téléphone connecté. */
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var hrCallback: MeasureCallback? = null
    private var passiveCallback: PassiveListenerCallback? = null
    private var stepListener: SensorEventListener? = null
    private var pushJob: Job? = null

    /** Démarre capteurs + heartbeat. Idempotent (no-op si déjà démarré). */
    fun start() {
        if (pushJob != null) return
        registerHeartRate()
        registerDaily()
        pushJob = viewModelScope.launch {
            while (isActive) {
                _sending.value = sender.send(
                    WearLivePayload.encode(
                        steps = _steps.value ?: 0L,
                        hr = _hr.value,
                        distanceM = _distanceM.value,
                        caloriesKcal = _caloriesKcal.value,
                        timestampMillis = System.currentTimeMillis(),
                    ),
                )
                delay(PUSH_INTERVAL_MS)
            }
        }
    }

    /** Arrête capteurs + heartbeat (fond / écran quitté). */
    fun stop() {
        pushJob?.cancel()
        pushJob = null
        hrCallback?.let { cb ->
            runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, cb) }
        }
        hrCallback = null
        passiveCallback?.let {
            runCatching { passiveClient.clearPassiveListenerCallbackAsync() }
        }
        passiveCallback = null
        stepListener?.let { sensorManager?.unregisterListener(it) }
        stepListener = null
        _sending.value = false
    }

    override fun onCleared() {
        stop()
    }

    private fun registerHeartRate() {
        val cb = object : MeasureCallback {
            override fun onRegistered() {}
            override fun onRegistrationFailed(throwable: Throwable) {}
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}
            override fun onDataReceived(data: DataPointContainer) {
                data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { sample ->
                    _hr.value = sample.value.toInt()
                }
            }
        }
        hrCallback = cb
        runCatching { measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, cb) }
    }

    /**
     * Agrégats quotidiens de la plateforme (reset minuit géré système) : pas + distance + calories
     * TOTALES, selon ce que la montre supporte. Un SEUL PassiveListenerCallback pour les 3 types.
     * Les pas retombent sur le compteur cumulatif si STEPS_DAILY n'est pas supporté ; distance/calories
     * n'ont pas de fallback simple (restent null si non supportées).
     */
    private fun registerDaily() {
        viewModelScope.launch {
            val supported = runCatching {
                passiveClient.getCapabilitiesAsync().await().supportedDataTypesPassiveMonitoring
            }.getOrDefault(emptySet())

            Log.d(
                TAG_WEAR,
                "capabilities daily: steps=${DataType.STEPS_DAILY in supported} " +
                    "dist=${DataType.DISTANCE_DAILY in supported} cal=${DataType.CALORIES_DAILY in supported}",
            )
            val dailyTypes = mutableSetOf<DataType<*, *>>()
            if (DataType.STEPS_DAILY in supported) dailyTypes += DataType.STEPS_DAILY
            if (DataType.DISTANCE_DAILY in supported) dailyTypes += DataType.DISTANCE_DAILY
            if (DataType.CALORIES_DAILY in supported) dailyTypes += DataType.CALORIES_DAILY

            if (dailyTypes.isNotEmpty()) registerPassiveDaily(dailyTypes)
            if (DataType.STEPS_DAILY !in supported) registerSensorStepsFallback()
        }
    }

    private fun registerPassiveDaily(types: Set<DataType<*, *>>) {
        val callback = object : PassiveListenerCallback {
            override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
                // getData renvoie une liste vide pour un type non enregistré → pas d'écrasement.
                dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { _steps.value = it.value }
                dataPoints.getData(DataType.DISTANCE_DAILY).lastOrNull()?.let { _distanceM.value = it.value.roundToInt() }
                dataPoints.getData(DataType.CALORIES_DAILY).lastOrNull()?.let { _caloriesKcal.value = it.value.roundToInt() }
                Log.d(TAG_WEAR, "live daily: steps=${_steps.value} dist=${_distanceM.value} cal=${_caloriesKcal.value}")
            }
        }
        passiveCallback = callback
        val config = PassiveListenerConfig.builder()
            .setDataTypes(types)
            .build()
        runCatching { passiveClient.setPassiveListenerCallback(config, callback) }
    }

    /** Fallback : compteur cumulatif normalisé « du jour » via baseline persistée. */
    private fun registerSensorStepsFallback() {
        val manager = sensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val prefs = getApplication<Application>()
            .getSharedPreferences(PREFS_STEPS, Context.MODE_PRIVATE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val counter = event.values.firstOrNull()?.toLong() ?: return
                val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
                val result = DailyStepCalculator.compute(counter, today, loadBaseline(prefs))
                result.newBaseline?.let { saveBaseline(prefs, it) }
                _steps.value = result.daySteps
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        stepListener = listener
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun loadBaseline(prefs: SharedPreferences): DailyStepCalculator.Baseline? {
        if (!prefs.contains(KEY_DAY)) return null
        return DailyStepCalculator.Baseline(
            epochDay = prefs.getLong(KEY_DAY, 0L),
            counterAtDayStart = prefs.getLong(KEY_COUNTER, 0L),
        )
    }

    private fun saveBaseline(prefs: SharedPreferences, baseline: DailyStepCalculator.Baseline) {
        prefs.edit {
            putLong(KEY_DAY, baseline.epochDay)
            putLong(KEY_COUNTER, baseline.counterAtDayStart)
        }
    }

    private companion object {
        const val TAG_WEAR = "WearPull" // instrumentation canal montre (capabilities + valeurs live)
        const val PUSH_INTERVAL_MS = 1000L // throttle : ≤ 1 message/s en écran actif
        const val PREFS_STEPS = "wear_steps"
        const val KEY_DAY = "baseline_day"
        const val KEY_COUNTER = "baseline_counter"
    }
}
