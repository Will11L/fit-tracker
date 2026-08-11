package com.example.sportapp.feature.health.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.sportapp.feature.health.domain.HealthConnectAvailability
import com.example.sportapp.feature.health.domain.HealthConnectMapper
import com.example.sportapp.feature.health.domain.HealthDataType
import com.example.sportapp.feature.health.domain.HealthMetricReading
import com.example.sportapp.feature.health.domain.HealthUiAggregations
import com.example.sportapp.feature.health.domain.HeartRateSample
import com.example.sportapp.feature.health.domain.PointMeasurement
import com.example.sportapp.feature.health.domain.SleepPhaseSliceReading
import com.example.sportapp.feature.health.domain.SleepSessionReading
import com.example.sportapp.feature.health.domain.SleepStageSlice
import com.example.sportapp.feature.health.domain.StepBucketReading
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encapsule le SDK Health Connect : disponibilité, permissions par type, et
 * lectures **read-only** (aucune écriture, jamais). Toutes les lectures et
 * requêtes de permission sont défensives (fallback silencieux → liste vide /
 * `false`) pour ne jamais crasher si HC est absent, non installé, ou si une
 * permission par type manque (grant partiel toléré).
 *
 * Vendor-agnostic : lit ce que HC agrège, quelle que soit la source (Samsung
 * Health, Google Fit, montre, etc.). Constructor-injectable → pas de module Hilt.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Les permissions de lecture, une par type de donnée (demande groupée). */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    )

    /** Disponibilité du provider HC sur ce device (drive le fallback UI). */
    val availability: HealthConnectAvailability
        get() = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.INSTALLED
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }

    /** Client HC, uniquement si le provider est prêt ; null sinon (fallback). */
    private val client: HealthConnectClient?
        get() = if (availability == HealthConnectAvailability.INSTALLED) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    /** Contract à brancher dans un `rememberLauncherForActivityResult` : ouvre
     *  l'écran système HC de consentement (input = [permissions]). */
    fun permissionRequestContract() =
        PermissionController.createRequestPermissionResultContract()

    /** Permissions actuellement accordées (sous-ensemble de [permissions]). */
    suspend fun grantedPermissions(): Set<String> =
        runCatching { client?.permissionController?.getGrantedPermissions() }
            .getOrNull() ?: emptySet()

    /** True si les 5 permissions de lecture sont accordées. */
    suspend fun hasAllPermissions(): Boolean =
        grantedPermissions().containsAll(permissions)

    /**
     * Permission de lecture HC en arrière-plan (Android 14+) — requise pour que le
     * worker d'échantillonnage des pas lise HC hors premier plan. Flow séparé (demandé
     * après les permissions de premier plan, comme BODY_SENSORS_BACKGROUND sur la montre).
     */
    val backgroundPermissions: Set<String> = setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

    /** True si la permission de lecture en arrière-plan est accordée. */
    suspend fun hasBackgroundPermission(): Boolean =
        grantedPermissions().containsAll(backgroundPermissions)

    /**
     * Pas en buckets de 30 min sur [from, to] → liste façonnée `health_step_counts`
     * (une row par tranche non vide, `bucket_start` "HH:MM" p.ex. "08:30"). Le total
     * du jour = somme des `steps`.
     */
    suspend fun readStepBuckets(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<StepBucketReading> {
        val hc = client ?: return emptyList()
        return runCatching {
            hc.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    timeRangeSlicer = Duration.ofMinutes(HealthConnectMapper.SLOT_MINUTES.toLong()),
                ),
            ).mapNotNull { bucket ->
                val count = bucket.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                if (count <= 0L) return@mapNotNull null
                HealthConnectMapper.stepBucket(bucket.startTime, count, zone)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Total de pas du jour sur [from, to] (fenêtre pleine-journée = total courant exact,
     * la proration Samsung ne mord que sur les fenêtres partielles). Utilisé par
     * l'échantillonneur ([StepSamplingWorker]) pour dériver les deltas par tranche.
     * `null` = HC indisponible / lecture impossible (ex. permission background absente
     * en arrière-plan) → le worker saute le relevé ; `0` = aucun pas enregistré.
     */
    suspend fun readDayStepTotal(from: Instant, to: Instant): Long? {
        val hc = client ?: return null
        return runCatching {
            hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, to)))
                .get(StepsRecord.COUNT_TOTAL) ?: 0L
        }.getOrNull()
    }

    /**
     * Agrégats des 4 métriques (distance, calories actives, FC moyenne, sommeil)
     * sur [from, to] → liste façonnée `health_metrics`. Chaque type est agrégé
     * isolément : une permission manquante n'affecte que son type.
     */
    suspend fun readMetricReadings(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HealthMetricReading> {
        val hc = client ?: return emptyList()
        val date = HealthConnectMapper.date(from, zone)
        val out = mutableListOf<HealthMetricReading>()

        aggregateOne(hc, DistanceRecord.DISTANCE_TOTAL, from, to)
            ?.get(DistanceRecord.DISTANCE_TOTAL)?.let { length ->
                out += HealthConnectMapper.metric(HealthDataType.DISTANCE, length.inMeters, date)
            }
        aggregateOne(hc, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, from, to)
            ?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.let { energy ->
                out += HealthConnectMapper.metric(HealthDataType.ACTIVE_CALORIES, energy.inKilocalories, date)
            }
        // TOTAL calories (BMR inclus) — source réelle côté Samsung/HC (Option B) ; le hub affiche le
        // total et dérive BMR/actives du profil. Agrégé isolément (permission propre).
        aggregateOne(hc, TotalCaloriesBurnedRecord.ENERGY_TOTAL, from, to)
            ?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.let { energy ->
                out += HealthConnectMapper.metric(HealthDataType.TOTAL_CALORIES, energy.inKilocalories, date)
            }
        aggregateOne(hc, HeartRateRecord.BPM_AVG, from, to)
            ?.get(HeartRateRecord.BPM_AVG)?.let { bpm ->
                out += HealthConnectMapper.metric(HealthDataType.HEART_RATE, bpm.toDouble(), date)
            }
        // SLEEP volontairement absent de readMetricReadings : le « temps dormi » a
        // UNE seule source de vérité = le mapper par stades (readSleepSessions +
        // HealthConnectMapper.sleepSession). L'agrégat HC SLEEP_DURATION_TOTAL
        // classait différemment les stades limites (écart ~14 min) → incohérence
        // hub/aperçu. HealthImporter importe désormais SLEEP via readSleepSessions.
        // SpO2 : mesure nocturne instantanée → dernière mesure de la fenêtre (comme
        // la FC, mesure de nuit). Lue en records (pas d'agrégat), datée par la mesure.
        lastOxygenSaturation(hc, from, to)?.let { record ->
            out += HealthConnectMapper.spo2(record.percentage.value, record.time, zone)
        }
        return out
    }

    /** Dernière mesure SpO2 (record instantané le plus récent) sur [from, to]. */
    private suspend fun lastOxygenSaturation(
        hc: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): OxygenSaturationRecord? = runCatching {
        hc.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to),
            ),
        ).records.maxByOrNull { it.time }
    }.getOrNull()

    /**
     * Dernière mesure de FC sur [from, to] : le sample le plus récent de tous les
     * `HeartRateRecord` (ce sont des séries de samples ; on aplatit et on prend le
     * plus récent). Affichage-only ; la valeur poussée au serveur reste la moyenne
     * 24 h (agrégat BPM_AVG). `null` si HC indisponible / aucune mesure.
     */
    suspend fun lastHeartRate(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PointMeasurement? {
        val hc = client ?: return null
        return runCatching {
            val samples = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records.flatMap { record ->
                record.samples.map { HeartRateSample(it.time, it.beatsPerMinute) }
            }
            HealthConnectMapper.latestHeartRate(samples, zone)
        }.getOrNull()
    }

    /**
     * Sessions de sommeil sur [from, to] → [SleepSessionReading] par session
     * (temps dormi + temps au lit), triées par heure de début. Affichage UI-only :
     * la métrique SLEEP poussée au serveur reste le temps dormi (cf. `HealthImporter`).
     */
    suspend fun readSleepSessions(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SleepSessionReading> {
        val hc = client ?: return emptyList()
        return runCatching {
            hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records.map { session ->
                val stages = session.stages.map { stage ->
                    SleepStageSlice(
                        stageType = stage.stage,
                        minutes = Duration.between(stage.startTime, stage.endTime).toMinutes(),
                        start = stage.startTime,
                    )
                }
                HealthConnectMapper.sleepSession(session.startTime, session.endTime, stages, zone)
            }.sortedBy { it.startTime }
        }.getOrDefault(emptyList())
    }

    /**
     * Slices de phases des sessions de sommeil sur [from, to] (hypnogramme) : un
     * élément par stade HC (début "HH:MM" local, durée, famille STAGE_BUCKET_*,
     * jour de réveil de la session porteuse). Sessions sans stades → aucune slice
     * (le chart retombe sur les barres intraday). Lu à l'affichage par le hub ET
     * persisté par l'importer (types SLEEP_SLICE_*) pour le web.
     */
    suspend fun readSleepPhaseSlices(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SleepPhaseSliceReading> {
        val hc = client ?: return emptyList()
        return runCatching {
            hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records.flatMap { session ->
                val endDate = HealthConnectMapper.date(session.endTime, zone)
                session.stages.mapNotNull { stage ->
                    val minutes = Duration.between(stage.startTime, stage.endTime).toMinutes()
                    if (minutes <= 0L) return@mapNotNull null
                    SleepPhaseSliceReading(
                        startTime = HealthConnectMapper.hhmm(stage.startTime, zone),
                        minutes = minutes,
                        bucket = HealthConnectMapper.sleepStageBucket(stage.stage),
                        endDate = endDate,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * FC moyenne par tranche de 30 min (0..47) sur [from, to] → 48 valeurs bpm (0 =
     * pas de mesure). Vue « aujourd'hui » intraday, **calculée à l'affichage** (non
     * persistée) : la métrique quotidienne HEART_RATE de Room/serveur est inchangée.
     */
    suspend fun readHourlyHeartRate(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Float> {
        val hc = client ?: return List(HealthConnectMapper.SLOTS_PER_DAY) { 0f }
        return runCatching {
            val entries = hc.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(HeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    timeRangeSlicer = Duration.ofMinutes(HealthConnectMapper.SLOT_MINUTES.toLong()),
                ),
            ).mapNotNull { bucket ->
                val bpm = bucket.result[HeartRateRecord.BPM_AVG] ?: return@mapNotNull null
                HealthConnectMapper.slotOfDay(bucket.startTime, zone) to bpm.toFloat()
            }
            HealthConnectMapper.valuesBySlot(entries)
        }.getOrDefault(List(HealthConnectMapper.SLOTS_PER_DAY) { 0f })
    }

    /**
     * Minutes dormies par tranche de 30 min (0..47) sur [from, to] → 48 valeurs,
     * dérivées des stades des sessions (découpage par tranche). **Calculé à
     * l'affichage** (non persisté) ; cohérent avec le « dormi » total (même règle de
     * stades que `HealthConnectMapper.sleepSession`), sans stocker de rows/jour.
     */
    suspend fun readHourlySleepMinutes(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Float> {
        val hc = client ?: return List(HealthConnectMapper.SLOTS_PER_DAY) { 0f }
        return runCatching {
            val sessions = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
            val intervals = sessions.flatMap { session ->
                if (session.stages.isEmpty()) {
                    listOf(session.startTime to session.endTime)
                } else {
                    session.stages
                        .filter { HealthConnectMapper.isAsleepStage(it.stage) }
                        .map { it.startTime to it.endTime }
                }
            }
            HealthConnectMapper.minutesBySlot(intervals, from, to, zone)
        }.getOrDefault(List(HealthConnectMapper.SLOTS_PER_DAY) { 0f })
    }

    /**
     * Minutes par famille de stade `[profond, léger, paradoxal, éveillé]` et par jour
     * calendaire de [days] (clé = date de FIN de session — la nuit appartient au matin).
     * **Calculé à l'affichage** (non persisté) : alimente l'empilement du chart sommeil
     * 7 jours. Sessions sans stades → tout en « léger » (pas de détail exploitable).
     */
    suspend fun readSleepStagesByDay(
        days: List<String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, List<Float>> {
        val hc = client ?: return emptyMap()
        if (days.isEmpty()) return emptyMap()
        return runCatching {
            val from = LocalDate.parse(days.first()).minusDays(1).atStartOfDay(zone).toInstant()
            val to = LocalDate.parse(days.last()).plusDays(1).atStartOfDay(zone).toInstant()
            val sessions = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records.map { session ->
                val stages = if (session.stages.isEmpty()) {
                    listOf(
                        SleepStageSlice(
                            stageType = HealthConnectMapper.SLEEP_STAGE_GENERIC,
                            minutes = Duration.between(session.startTime, session.endTime).toMinutes(),
                        ),
                    )
                } else {
                    session.stages.map { stage ->
                        SleepStageSlice(
                            stageType = stage.stage,
                            minutes = Duration.between(stage.startTime, stage.endTime).toMinutes(),
                            start = stage.startTime,
                        )
                    }
                }
                HealthConnectMapper.date(session.endTime, zone) to stages
            }
            HealthUiAggregations.sleepStageMinutesByDay(sessions, days)
        }.getOrDefault(emptyMap())
    }

    /**
     * SpO2 (%) par tranche de 30 min (0..47) sur [from, to] → 48 valeurs (0 = pas de
     * mesure). Samsung n'écrit typiquement qu'une mesure nocturne par jour ; le chart
     * se remplit si la mesure continue de nuit est activée dans Samsung Health.
     * **Calculé à l'affichage** (non persisté).
     */
    suspend fun readHourlySpo2(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Float> {
        val hc = client ?: return List(HealthConnectMapper.SLOTS_PER_DAY) { 0f }
        return runCatching {
            val entries = hc.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records.map {
                HealthConnectMapper.slotOfDay(it.time, zone) to it.percentage.value.toFloat()
            }
            HealthConnectMapper.valuesBySlot(entries)
        }.getOrDefault(List(HealthConnectMapper.SLOTS_PER_DAY) { 0f })
    }

    /** Agrège une seule métrique (isolation grant partiel + fallback null). */
    private suspend fun aggregateOne(
        hc: HealthConnectClient,
        metric: AggregateMetric<*>,
        from: Instant,
        to: Instant,
    ): AggregationResult? = runCatching {
        hc.aggregate(AggregateRequest(setOf(metric), TimeRangeFilter.between(from, to)))
    }.getOrNull()

    /** Intent vers l'écran système Health Connect (gérer / révoquer). */
    fun healthConnectSettingsIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /** Deep link store pour installer / mettre à jour Health Connect (fallback). */
    fun healthConnectStoreUri(): Uri =
        "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri()

    private companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
