package com.example.sportapp.wear

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
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Lecture **spot** (one-shot) des données santé montre pour répondre à une requête
 * pull du téléphone, **même app fermée** (déclenché par [WearRequestListenerService]).
 * Répond en deux temps sur `/health/live` : d'abord les pas du jour (réponse quasi
 * immédiate), puis la FC dès qu'un échantillon est verrouillé (timeout dur). Aucun
 * polling ni service persistant — tout est libéré à la fin de [respondTo].
 */
class SpotHealthReader(private val context: Context) {

    private val healthClient = HealthServices.getClient(context)
    private val messageClient = Wearable.getMessageClient(context)

    /** Agrégats quotidiens montre d'une réponse pull : pas + distance (m) + calories totales (kcal). */
    private data class DailyAggregates(val steps: Long, val distanceM: Int?, val caloriesKcal: Int?)

    suspend fun respondTo(nodeId: String) {
        val agg = readDailyAggregates()
        Log.d(TAG, "steps=${agg.steps} dist=${agg.distanceM} cal=${agg.caloriesKcal} → réponse immédiate vers $nodeId")
        send(nodeId, WearLivePayload.encode(agg.steps, null, agg.distanceM, agg.caloriesKcal, System.currentTimeMillis()))

        val hr = measureSpotHeartRate()
        Log.d(TAG, "spot hr=$hr")
        if (hr != null) {
            send(nodeId, WearLivePayload.encode(agg.steps, hr, agg.distanceM, agg.caloriesKcal, System.currentTimeMillis()))
        }
    }

    private suspend fun send(nodeId: String, payload: String) {
        runCatching {
            messageClient.sendMessage(nodeId, WearLivePayload.PATH, payload.toByteArray(Charsets.UTF_8)).await()
        }.onSuccess { Log.d(TAG, "réponse envoyée sur ${WearLivePayload.PATH}") }
            .onFailure { Log.w(TAG, "échec envoi réponse", it) }
    }

    /**
     * Agrégats du jour (pas + distance + calories TOTALES) instantanés, sinon fallback pas seuls
     * (compteur + baseline). N'enregistre QUE les types supportés (une config avec un type non
     * supporté ferait échouer toute l'écoute, y compris les pas).
     */
    private suspend fun readDailyAggregates(): DailyAggregates {
        val supported = runCatching {
            healthClient.passiveMonitoringClient.getCapabilitiesAsync().await().supportedDataTypesPassiveMonitoring
        }.getOrDefault(emptySet())
        Log.d(
            TAG,
            "capabilities daily: steps=${DataType.STEPS_DAILY in supported} " +
                "dist=${DataType.DISTANCE_DAILY in supported} cal=${DataType.CALORIES_DAILY in supported}",
        )
        if (DataType.STEPS_DAILY !in supported) return DailyAggregates(readSensorDaySteps(), null, null)

        val types = mutableSetOf<DataType<*, *>>(DataType.STEPS_DAILY)
        if (DataType.DISTANCE_DAILY in supported) types += DataType.DISTANCE_DAILY
        if (DataType.CALORIES_DAILY in supported) types += DataType.CALORIES_DAILY

        return withTimeoutOrNull(STEPS_TIMEOUT_MS) { awaitDaily(types) }
            ?: DailyAggregates(readSensorDaySteps(), null, null)
    }

    private suspend fun awaitDaily(types: Set<DataType<*, *>>): DailyAggregates = suspendCancellableCoroutine { cont ->
        val passiveClient = healthClient.passiveMonitoringClient
        var distance: Int? = null
        var calories: Int? = null
        val callback = object : PassiveListenerCallback {
            override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
                dataPoints.getData(DataType.DISTANCE_DAILY).lastOrNull()?.let { distance = it.value.roundToInt() }
                dataPoints.getData(DataType.CALORIES_DAILY).lastOrNull()?.let { calories = it.value.roundToInt() }
                // Les pas déclenchent la réponse (distance/calories déjà capturées dans le même lot).
                dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { dp ->
                    if (cont.isActive) {
                        runCatching { passiveClient.clearPassiveListenerCallbackAsync() }
                        cont.resume(DailyAggregates(dp.value, distance, calories))
                    }
                }
            }
        }
        val config = PassiveListenerConfig.builder()
            .setDataTypes(types)
            .build()
        val ok = runCatching { passiveClient.setPassiveListenerCallback(config, callback) }.isSuccess
        if (!ok && cont.isActive) cont.cancel() // non supporté → fallback
        cont.invokeOnCancellation { runCatching { passiveClient.clearPassiveListenerCallbackAsync() } }
    }

    private suspend fun readSensorDaySteps(): Long {
        val counter = readStepCounterOnce() ?: return 0L
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val prefs = context.getSharedPreferences(PREFS_STEPS, Context.MODE_PRIVATE)
        val result = DailyStepCalculator.compute(counter, today, loadBaseline(prefs))
        result.newBaseline?.let { saveBaseline(prefs, it) }
        return result.daySteps
    }

    private suspend fun readStepCounterOnce(): Long? = withTimeoutOrNull(SENSOR_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(SensorManager::class.java)
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (manager == null || sensor == null) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    event.values.firstOrNull()?.toLong()?.let {
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(it)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            cont.invokeOnCancellation { manager.unregisterListener(listener) }
        }
    }

    private suspend fun measureSpotHeartRate(): Int? {
        val measureClient = healthClient.measureClient
        var callback: MeasureCallback? = null
        return try {
            withTimeoutOrNull(HR_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val cb = object : MeasureCallback {
                        override fun onRegistered() {}
                        override fun onRegistrationFailed(throwable: Throwable) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}
                        override fun onDataReceived(data: DataPointContainer) {
                            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt()?.let { bpm ->
                                if (bpm > 0 && cont.isActive) cont.resume(bpm)
                            }
                        }
                    }
                    callback = cb
                    measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, cb)
                }
            }
        } finally {
            callback?.let {
                runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, it) }
            }
        }
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
        const val TAG = "WearPull"
        const val STEPS_TIMEOUT_MS = 5_000L
        const val SENSOR_TIMEOUT_MS = 2_000L
        const val HR_TIMEOUT_MS = 8_000L
        const val PREFS_STEPS = "wear_steps"
        const val KEY_DAY = "baseline_day"
        const val KEY_COUNTER = "baseline_counter"
    }
}
