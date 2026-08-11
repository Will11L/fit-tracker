package com.example.sportapp.feature.health.domain

/**
 * Lecture d'un bucket de pas intraday, façonnée sur `health_step_counts` :
 * `date` "YYYY-MM-DD" + `bucketStart` "HH:MM" + `steps`. Le total du jour se
 * dérive par somme sur une même date. La future couche sync (tâche 3) mappe ce
 * type 1:1 vers `HealthStepCountCreate`.
 */
data class StepBucketReading(
    val date: String,
    val bucketStart: String,
    val steps: Long,
)

/**
 * Lecture d'une métrique santé, façonnée sur `health_metrics` :
 * `type` (UPPER_CASE via [HealthDataType.name]) + `value` + `unit` + `date` +
 * `startTime` "HH:MM" optionnel. [type] est toujours l'un des 4 types métrique
 * (jamais [HealthDataType.STEPS], qui a son propre canal). Mappé 1:1 vers
 * `HealthMetricCreate` par la couche sync (tâche 3).
 */
data class HealthMetricReading(
    val type: HealthDataType,
    val value: Double,
    val unit: String,
    val date: String,
    val startTime: String? = null,
)

/**
 * Tranche de stade de sommeil (entrée pure du mapper) : type de stade Health
 * Connect (`SleepSessionRecord.STAGE_TYPE_*`) + durée en minutes. Le Manager la
 * construit depuis les stades du SDK ; le mapper décide lesquels comptent comme
 * « dormi » — logique gardée pure/testable en JVM.
 */
data class SleepStageSlice(
    val stageType: Int,
    val minutes: Long,
    val start: java.time.Instant? = null,
)

/**
 * Lecture d'une session de sommeil pour l'affichage (UI-only v1) : distingue le
 * temps réellement dormi ([asleepMinutes], stades hors éveil) du temps au lit
 * ([inBedMinutes], durée de session). La métrique SLEEP poussée au serveur reste
 * le temps dormi (cf. `HealthImporter`) — ce type n'étend pas le contrat wire.
 */
/**
 * Slice de phase d'une session de sommeil (hypnogramme) : début local "HH:MM",
 * durée, famille d'empilement 0..3 (STAGE_BUCKET_*) et jour de réveil de la
 * session porteuse (la nuit appartient au matin du réveil).
 */
data class SleepPhaseSliceReading(
    val startTime: String, // "HH:MM"
    val minutes: Long,
    val bucket: Int,       // famille STAGE_BUCKET_* (0=profond, 1=léger, 2=REM, 3=éveillé)
    val endDate: String,   // "YYYY-MM-DD" — jour de réveil de la session
)

data class SleepSessionReading(
    val startTime: String,       // "HH:MM" — mise au lit (début de session)
    val endTime: String,         // "HH:MM"
    val asleepMinutes: Long,
    val inBedMinutes: Long,
    val asleepStartTime: String, // "HH:MM" — endormissement (1er stade hors éveil ; fallback = startTime)
    val endDate: String,         // "YYYY-MM-DD" — jour du réveil (fin de session, fuseau local)
)

/**
 * Échantillon de fréquence cardiaque (entrée pure du mapper) : instant + bpm.
 * Un `HeartRateRecord` est une série de samples ; le Manager les aplatit et le
 * mapper en extrait le plus récent (logique gardée pure/testable en JVM).
 */
data class HeartRateSample(
    val time: java.time.Instant,
    val bpm: Long,
)

/** Mesure ponctuelle horodatée pour l'aperçu : valeur + heure "HH:MM". */
data class PointMeasurement(
    val value: Double,
    val time: String,
)

/**
 * Aperçu agrégé affiché sur l'écran de statut Santé pour prouver que les
 * lectures fonctionnent (critère d'acceptation). Purement diagnostic : la vraie
 * persistance/agrégation est traitée par les écrans Santé et la couche sync.
 * Chaque champ est null si la donnée est absente ou la permission non accordée ;
 * le sommeil est détaillé par session ([sleepSessions], vide si aucune). La FC
 * expose la moyenne 24 h ([avgHeartRateBpm], = valeur poussée au serveur) ET la
 * dernière mesure ([lastHeartRate]) ; SpO2 = dernière mesure ([spo2]).
 */
data class HealthSnapshot(
    val stepsToday: Long? = null,
    val distanceMeters: Double? = null,
    val activeKcal: Double? = null,
    val avgHeartRateBpm: Double? = null,
    val lastHeartRate: PointMeasurement? = null,
    val spo2: PointMeasurement? = null,
    val sleepSessions: List<SleepSessionReading> = emptyList(),
)
