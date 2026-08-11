package com.example.sportapp.feature.health.domain

import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.data.model.HealthStepCount
import java.util.UUID

/**
 * Agrégations pures pour l'UI Santé (lecture depuis Room, aucune dépendance
 * Android → testable JVM). Les entrées sont les listes brutes des DAOs
 * (`observeAll`), déjà filtrées de `pendingDeletion` par l'appelant.
 */
object HealthUiAggregations {

    const val GOAL_TYPE_STEPS = "STEPS"

    /** Type `health_metrics` d'une pesée manuelle (kg) — UPPER_CASE (politique 11).
     *  Jamais importé de Health Connect (saisie utilisateur uniquement, v2 balance
     *  connectée = même type, autre source). */
    const val METRIC_TYPE_WEIGHT = "WEIGHT_KG"

    /** Type `health_metrics` d'une saisie manuelle de stress (niveau 1..5) — UPPER_CASE
     *  (politique 11). Jamais importé (Samsung n'expose pas le stress dans HC) ;
     *  v2 HRV = même type, autre source. */
    const val METRIC_TYPE_STRESS = "STRESS"

    /**
     * Objectif de pas actif un jour [day] = le [HealthGoal] STEPS au plus grand
     * `effectiveFrom` ≤ [day] (même sémantique que NutritionGoal / muscle goals).
     * `null` si aucun objectif défini pour ce jour.
     */
    fun activeStepGoal(goals: List<HealthGoal>, day: String): HealthGoal? =
        goals.filter { it.type == GOAL_TYPE_STEPS && it.effectiveFrom <= day }
            .maxByOrNull { it.effectiveFrom }

    /** Total de pas du jour [day] = somme des buckets de cette date. */
    fun stepsForDay(buckets: List<HealthStepCount>, day: String): Int =
        buckets.filter { it.date == day }.sumOf { it.steps }

    /** Buckets intraday d'un jour, triés par heure de début. */
    fun bucketsForDay(buckets: List<HealthStepCount>, day: String): List<HealthStepCount> =
        buckets.filter { it.date == day }.sortedBy { it.bucketStart }

    /**
     * Pas **cumulés** du jour, bucket par bucket (courbe montante). La distribution
     * intraday Samsung étant uniformément proratée, la courbe par tranche serait
     * plate ; la cumulée est le rendu standard et lisible. Vide si aucun bucket.
     */
    fun cumulativeStepsForDay(buckets: List<HealthStepCount>, day: String): List<Float> {
        var acc = 0
        return bucketsForDay(buckets, day).map { acc += it.steps; acc.toFloat() }
    }

    /**
     * Pas du jour ventilés sur 48 tranches de 30 min (index 0 = 00:00 … 47 = 23:30), à
     * partir des buckets Room (déjà en tranches de 30 min pour le jour importé). Slots
     * sans bucket = 0. Alimente le chart barres (`HealthBarChart`). Un éventuel bucket
     * horaire résiduel ("HH:00") tombe simplement dans sa tranche.
     */
    fun stepsBySlot(buckets: List<HealthStepCount>, day: String): List<Float> {
        val bySlot = FloatArray(HealthConnectMapper.SLOTS_PER_DAY)
        bucketsForDay(buckets, day).forEach { b ->
            val slot = slotOfBucketStart(b.bucketStart) ?: return@forEach
            if (slot in 0 until HealthConnectMapper.SLOTS_PER_DAY) bySlot[slot] += b.steps.toFloat()
        }
        return bySlot.toList()
    }

    /** Tranche de 30 min (0..47) d'un `bucket_start` "HH:MM", ou null si non parsable. */
    private fun slotOfBucketStart(hhmm: String): Int? {
        val h = hhmm.substringBefore(':').toIntOrNull() ?: return null
        val m = hhmm.substringAfter(':', "").toIntOrNull() ?: 0
        return h * (60 / HealthConnectMapper.SLOT_MINUTES) + m / HealthConnectMapper.SLOT_MINUTES
    }

    /**
     * Met à zéro les tranches **postérieures** à [currentSlot] (index de la tranche
     * courante 0..47) d'une série intraday : la vue « aujourd'hui » n'affiche aucune
     * barre future. Garde-fou contre l'artefact de proration Samsung (le record
     * jour-entier étale des pas sur les 48 slots, y compris à venir). Affichage-only —
     * ne touche pas aux buckets Room (contrat total=SUM de la journée complète intact).
     */
    fun clipFutureSlots(series: List<Float>, currentSlot: Int): List<Float> =
        series.mapIndexed { index, value -> if (index > currentSlot) 0f else value }

    /**
     * Totaux de pas par jour sur les [days] dernières dates présentes, ordre
     * chronologique croissant (plus ancien → plus récent). Ne fabrique pas de
     * jours vides : ne renvoie que les dates réellement observées.
     */
    fun stepsByDay(buckets: List<HealthStepCount>, days: Int): List<Pair<String, Int>> =
        buckets.groupBy { it.date }
            .map { (date, rows) -> date to rows.sumOf { it.steps } }
            .sortedBy { it.first }
            .takeLast(days)

    /**
     * Totaux de pas pour un ensemble de jours calendaires [days] (dans l'ordre
     * fourni), 0 pour les jours sans bucket → **slots réservés** (le chart 7 j garde
     * toutes les positions alignées, jamais compressées).
     */
    fun stepsByDayCalendar(buckets: List<HealthStepCount>, days: List<String>): List<Pair<String, Float>> {
        val byDate = buckets.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.steps }.toFloat() }
        return days.map { it to (byDate[it] ?: 0f) }
    }

    /**
     * Valeur d'un type de métrique pour des jours calendaires [days] (ordre fourni),
     * 0 si absent. Une date à plusieurs rows → la plus tardive (startTime max).
     */
    fun metricByDayCalendar(metrics: List<HealthMetric>, type: String, days: List<String>): List<Pair<String, Float>> {
        val byDate = metrics.filter { it.type == type }
            .groupBy { it.date }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.startTime ?: "" }?.value ?: rows.first().value }
        return days.map { it to (byDate[it] ?: 0f) }
    }

    /**
     * Pesée (kg) par jour calendaire [days] (ordre fourni), `null` pour les jours
     * sans pesée — ≠ 0 : un jour non pesé reste ABSENT de la courbe (aucune
     * interpolation, décision produit). Une date à plusieurs rows (théorique,
     * l'uuid déterministe garantit 1 row/jour) → la plus tardive, comme
     * [metricByDayCalendar].
     */
    fun weightByDayCalendar(metrics: List<HealthMetric>, days: List<String>): List<Pair<String, Float?>> =
        nullableMetricByDayCalendar(metrics, METRIC_TYPE_WEIGHT, days)

    /** Valeur NULLABLE d'un type par jour calendaire (`null` = jour sans row, jamais
     *  interpolé) — généralisation de [weightByDayCalendar] (pesées, stress…). */
    fun nullableMetricByDayCalendar(
        metrics: List<HealthMetric>,
        type: String,
        days: List<String>,
    ): List<Pair<String, Float?>> {
        val byDate = metrics.filter { it.type == type }
            .groupBy { it.date }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.startTime ?: "" }?.value }
        return days.map { it to byDate[it] }
    }

    /** Dernière valeur d'un type de métrique (date max, puis startTime max). */
    fun latestMetric(metrics: List<HealthMetric>, type: String): HealthMetric? =
        metrics.filter { it.type == type }
            .maxWithOrNull(compareBy({ it.date }, { it.startTime ?: "" }))

    /**
     * Valeur d'un type de métrique par jour sur les [days] dernières dates, ordre
     * chronologique croissant. Une date peut porter plusieurs rows (intraday) : on
     * retient la plus tardive (startTime max) comme représentante du jour.
     */
    fun metricByDay(metrics: List<HealthMetric>, type: String, days: Int): List<Pair<String, Float>> =
        metrics.filter { it.type == type }
            .groupBy { it.date }
            .map { (date, rows) ->
                date to (rows.maxByOrNull { it.startTime ?: "" }?.value ?: rows.first().value)
            }
            .sortedBy { it.first }
            .takeLast(days)

    /**
     * Moyenne des **jours renseignés** (valeurs > 0) d'une série ; `null` si aucun jour
     * renseigné. Exclut les slots vides/0 pour qu'un court historique n'écrase pas la
     * moyenne (2 jours à 8000 → 8000, et non 8000·2/7). Alimente la ligne de repère
     * (moyenne) du chart 7 jours.
     */
    fun averageOfFilledDays(values: List<Float>): Float? {
        val filled = values.filter { it > 0f }
        return if (filled.isEmpty()) null else filled.sum() / filled.size
    }

    /** Point d'hypnogramme : début en minutes RELATIVES à minuit du jour de réveil
     *  (négatif = la veille au soir), durée (min), famille STAGE_BUCKET_* 0..3. */
    data class SleepPhasePoint(val startMin: Int, val minutes: Int, val bucket: Int)

    /**
     * Chronologie d'hypnogramme depuis des slices "HH:MM" rattachées au jour de
     * réveil : une heure ≥ 15:00 est interprétée comme LA VEILLE AU SOIR (la nuit
     * appartient au matin du réveil ; une sieste d'après-midi appartient, elle, à
     * son propre jour de fin — même convention des deux côtés). Triée par début.
     * Pur → testable JVM, miroir de `sleepPhaseTimeline` web.
     */
    fun sleepPhaseTimeline(slices: List<SleepPhaseSliceReading>): List<SleepPhasePoint> =
        slices.mapNotNull { s ->
            val parts = s.startTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            var start = h * 60 + m
            if (start >= 15 * 60) start -= 24 * 60 // la veille au soir
            SleepPhasePoint(start, s.minutes.toInt(), s.bucket)
        }.sortedBy { it.startMin }

    /**
     * Minutes par famille de stade `[profond, léger, paradoxal, éveillé]` et par jour
     * calendaire de [days] (clé = date de FIN de session : la nuit appartient au matin
     * du réveil). Sessions hors [days] ignorées ; jours sans session absents de la map
     * (le chart réserve les slots via [days]). Pur → testable JVM.
     */
    fun sleepStageMinutesByDay(
        sessions: List<Pair<String, List<SleepStageSlice>>>,
        days: List<String>,
    ): Map<String, List<Float>> {
        val daySet = days.toSet()
        val out = mutableMapOf<String, FloatArray>()
        sessions.forEach { (endDate, stages) ->
            if (endDate !in daySet) return@forEach
            val acc = out.getOrPut(endDate) { FloatArray(HealthConnectMapper.STAGE_BUCKETS) }
            stages.forEach { s ->
                acc[HealthConnectMapper.sleepStageBucket(s.stageType)] += s.minutes.toFloat()
            }
        }
        return out.mapValues { it.value.toList() }
    }

    /** Progression [0f, 1f] du total de pas vers l'objectif (0 si objectif ≤ 0). */
    fun stepProgress(steps: Int, goalTarget: Float?): Float {
        val target = goalTarget ?: return 0f
        if (target <= 0f) return 0f
        return (steps / target).coerceIn(0f, 1f)
    }

    /** UUID déterministe d'un objectif de pas (user + type + jour) : re-régler le
     *  même jour upsert la même row au lieu de créer un doublon. */
    fun stepGoalUuid(userId: Int, effectiveFrom: String): String =
        UUID.nameUUIDFromBytes("health_goal:$userId:$GOAL_TYPE_STEPS:$effectiveFrom".toByteArray(Charsets.UTF_8))
            .toString()
}
