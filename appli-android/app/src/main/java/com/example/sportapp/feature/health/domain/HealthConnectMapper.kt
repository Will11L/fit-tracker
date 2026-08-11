package com.example.sportapp.feature.health.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Conversion pure des lectures Health Connect vers les types wire du projet
 * (convention dates "YYYY-MM-DD" / heures "HH:MM", unités self-describing).
 *
 * Isolé du SDK (n'accepte que des [Instant] + primitives) pour être testable
 * en JVM sans device ni Health Connect. Source unique du façonnage wire,
 * réutilisée par l'aperçu (cette tâche) et la couche sync (tâche 3).
 *
 * java.time est natif dès l'API 26 (minSdk 29) — pas besoin de desugaring.
 */
object HealthConnectMapper {

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** "YYYY-MM-DD" de l'instant dans le fuseau local. */
    fun date(instant: Instant, zone: ZoneId): String =
        DATE_FMT.format(instant.atZone(zone))

    /** "HH:MM" de l'instant dans le fuseau local. */
    fun hhmm(instant: Instant, zone: ZoneId): String =
        TIME_FMT.format(instant.atZone(zone))

    /**
     * Bucket de pas → [StepBucketReading] wire. [bucketStart] est l'instant de
     * début de la tranche ; sa date et son heure locales stampent la row.
     */
    fun stepBucket(bucketStart: Instant, steps: Long, zone: ZoneId): StepBucketReading =
        StepBucketReading(
            date = date(bucketStart, zone),
            bucketStart = hhmm(bucketStart, zone),
            steps = steps,
        )

    /**
     * Métrique agrégée → [HealthMetricReading] wire. L'unité est dérivée du type
     * ([HealthDataType.wireUnit]) : un seul endroit décide de l'unité par type.
     * [startTime] optionnel pour les mesures intraday.
     */
    fun metric(
        type: HealthDataType,
        value: Double,
        date: String,
        startTime: String? = null,
    ): HealthMetricReading =
        HealthMetricReading(
            type = type,
            value = value,
            unit = type.wireUnit,
            date = date,
            startTime = startTime,
        )

    /**
     * Mesure SpO2 instantanée → [HealthMetricReading] wire (type SPO2, unité %).
     * [startTime] = heure de la mesure ("HH:MM") : ancre la mesure intraday et rend
     * l'uuid d'import unique par mesure (user + SPO2 + date + startTime).
     */
    fun spo2(percentage: Double, time: Instant, zone: ZoneId): HealthMetricReading =
        metric(
            type = HealthDataType.SPO2,
            value = percentage,
            date = date(time, zone),
            startTime = hhmm(time, zone),
        )

    /**
     * Dernière mesure de FC parmi [samples] (samples aplatis de tous les records
     * de la fenêtre, ordre non garanti) → [PointMeasurement] (bpm + heure "HH:MM").
     * Null si aucun sample. Affichage-only : la valeur poussée au serveur reste la
     * moyenne 24 h (agrégat BPM_AVG, cf. `readMetricReadings`).
     */
    fun latestHeartRate(samples: List<HeartRateSample>, zone: ZoneId): PointMeasurement? {
        val latest = samples.maxByOrNull { it.time } ?: return null
        return PointMeasurement(value = latest.bpm.toDouble(), time = hhmm(latest.time, zone))
    }

    // Codes de stades exclus du temps dormi (miroir de SleepSessionRecord.STAGE_TYPE_*) :
    // AWAKE=1, OUT_OF_BED=3, AWAKE_IN_BED=7. Tout le reste (SLEEPING/LIGHT/DEEP/REM/
    // UNKNOWN) compte comme dormi.
    const val SLEEP_STAGE_AWAKE = 1
    const val SLEEP_STAGE_OUT_OF_BED = 3
    const val SLEEP_STAGE_AWAKE_IN_BED = 7
    private val AWAKE_STAGE_TYPES = setOf(SLEEP_STAGE_AWAKE, SLEEP_STAGE_OUT_OF_BED, SLEEP_STAGE_AWAKE_IN_BED)

    /**
     * Session de sommeil → [SleepSessionReading] (affichage). [inBedMinutes] =
     * durée de session ([start]..[end]). [asleepMinutes] = somme des stades hors
     * éveil ([AWAKE_STAGE_TYPES]) ; **fallback** = durée de session si aucun stade
     * n'est fourni (certaines sources n'écrivent pas de stades).
     */
    fun sleepSession(
        start: Instant,
        end: Instant,
        stages: List<SleepStageSlice>,
        zone: ZoneId,
    ): SleepSessionReading {
        val inBed = Duration.between(start, end).toMinutes()
        val asleep = if (stages.isEmpty()) {
            inBed
        } else {
            stages.filter { it.stageType !in AWAKE_STAGE_TYPES }.sumOf { it.minutes }
        }
        // Endormissement = début du 1er stade hors éveil (fallback = mise au lit si
        // aucun stade ou stades sans horodatage).
        val asleepStart = stages
            .filter { it.stageType !in AWAKE_STAGE_TYPES }
            .mapNotNull { it.start }
            .minOrNull() ?: start
        return SleepSessionReading(
            startTime = hhmm(start, zone),
            endTime = hhmm(end, zone),
            asleepMinutes = asleep,
            inBedMinutes = inBed,
            asleepStartTime = hhmm(asleepStart, zone),
            endDate = end.atZone(zone).toLocalDate().toString(),
        )
    }

    /** True si le stade compte comme « dormi » (hors éveil) — même règle que [sleepSession]. */
    fun isAsleepStage(stageType: Int): Boolean = stageType !in AWAKE_STAGE_TYPES

    // Ventilation des stades en 4 familles pour l'empilement du chart 7 jours :
    // profond / léger / paradoxal (REM) / éveillé. Les stades génériques sans détail
    // (SLEEPING=2, UNKNOWN=0) tombent en « léger » (famille la plus neutre).
    const val STAGE_BUCKET_DEEP = 0
    const val STAGE_BUCKET_LIGHT = 1
    const val STAGE_BUCKET_REM = 2
    const val STAGE_BUCKET_AWAKE = 3
    const val STAGE_BUCKETS = 4
    const val SLEEP_STAGE_GENERIC = 2 // SLEEPING sans détail de stade
    private const val SLEEP_STAGE_DEEP = 5
    private const val SLEEP_STAGE_REM = 6

    /** Famille d'empilement (0..3) d'un type de stade Health Connect. */
    fun sleepStageBucket(stageType: Int): Int = when {
        stageType == SLEEP_STAGE_DEEP -> STAGE_BUCKET_DEEP
        stageType == SLEEP_STAGE_REM -> STAGE_BUCKET_REM
        stageType in AWAKE_STAGE_TYPES -> STAGE_BUCKET_AWAKE
        else -> STAGE_BUCKET_LIGHT
    }

    // Granularité intraday des charts « aujourd'hui » : tranches de 30 min → 48 slots.
    const val SLOT_MINUTES = 30
    const val SLOTS_PER_DAY = 24 * 60 / SLOT_MINUTES // 48

    /** Index de tranche (0..47) de l'instant dans le fuseau local (tranches de 30 min). */
    fun slotOfDay(instant: Instant, zone: ZoneId): Int {
        val z = instant.atZone(zone)
        return z.hour * (60 / SLOT_MINUTES) + z.minute / SLOT_MINUTES
    }

    /** "HH:MM" du DÉBUT de la tranche de 30 min de l'instant (ex. 08:47 → "08:30"). */
    fun slotStartHhmm(instant: Instant, zone: ZoneId): String {
        val z = instant.atZone(zone)
        val minute = if (z.minute < SLOT_MINUTES) 0 else SLOT_MINUTES
        return TIME_FMT.format(z.withMinute(minute).withSecond(0).withNano(0))
    }

    /** "HH:MM" du DÉBUT de la tranche d'index [slot] (0..47) : slot 20 → "10:00", 21 → "10:30". */
    fun slotIndexHhmm(slot: Int): String {
        val perHour = 60 / SLOT_MINUTES
        return TIME_FMT.format(java.time.LocalTime.of(slot / perHour, (slot % perHour) * SLOT_MINUTES))
    }

    /**
     * Ventile des valeurs (index de tranche 0..47 → valeur) sur 48 tranches de
     * 30 min ; slots absents = 0. Utilisé pour la FC intraday (bpm moyen par tranche).
     */
    fun valuesBySlot(entries: List<Pair<Int, Float>>): List<Float> {
        val bySlot = FloatArray(SLOTS_PER_DAY)
        entries.forEach { (slot, value) -> if (slot in 0 until SLOTS_PER_DAY) bySlot[slot] = value }
        return bySlot.toList()
    }

    /**
     * Ventile des intervalles [start, end) sur 48 tranches de 30 min (minutes par
     * tranche locale), en découpant chaque intervalle à la frontière de tranche et en
     * le clippant à [dayStart, dayEnd). Utilisé pour le sommeil intraday (minutes
     * dormies par tranche, dérivées des stades). Logique pure → testable JVM.
     */
    fun minutesBySlot(
        intervals: List<Pair<Instant, Instant>>,
        dayStart: Instant,
        dayEnd: Instant,
        zone: ZoneId,
    ): List<Float> {
        val bySlot = DoubleArray(SLOTS_PER_DAY)
        for ((rawStart, rawEnd) in intervals) {
            val start = maxOf(rawStart, dayStart)
            val end = minOf(rawEnd, dayEnd)
            if (!start.isBefore(end)) continue
            var cursor = start
            while (cursor.isBefore(end)) {
                val zoned = cursor.atZone(zone)
                val slot = slotOfDay(cursor, zone)
                val segmentEnd = minOf(end, nextSlotBoundary(zoned))
                if (slot in 0 until SLOTS_PER_DAY) {
                    bySlot[slot] += Duration.between(cursor, segmentEnd).seconds / 60.0
                }
                cursor = segmentEnd
            }
        }
        return bySlot.map { it.toFloat() }
    }

    /** Début de la tranche de 30 min suivant [zoned] (frontière :30 ou heure pleine). */
    private fun nextSlotBoundary(zoned: ZonedDateTime): Instant {
        val topOfHour = zoned.truncatedTo(ChronoUnit.HOURS)
        return if (zoned.minute < SLOT_MINUTES) {
            topOfHour.plusMinutes(SLOT_MINUTES.toLong()).toInstant()
        } else {
            topOfHour.plusHours(1).toInstant()
        }
    }
}
