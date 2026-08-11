package com.example.sportapp.feature.health

import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.feature.health.domain.HealthConnectMapper
import com.example.sportapp.feature.health.domain.HealthUiAggregations
import com.example.sportapp.feature.health.domain.SleepPhaseSliceReading
import com.example.sportapp.feature.health.domain.HealthUuids
import com.example.sportapp.feature.health.domain.SleepStageSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests JVM purs des agrégations d'affichage de l'UI Santé. */
class HealthUiAggregationsTest {

    private fun goal(uuid: String, from: String, target: Float, type: String = "STEPS") =
        HealthGoal(uuid = uuid, userId = 1, type = type, target = target, effectiveFrom = from)

    private fun bucket(date: String, hhmm: String, steps: Int) =
        HealthStepCount(uuid = "$date-$hhmm", userId = 1, date = date, bucketStart = hhmm, steps = steps)

    private fun metric(type: String, value: Float, date: String, start: String? = null) =
        HealthMetric(uuid = "$type-$date-$start", userId = 1, type = type, value = value, unit = "u", date = date, startTime = start)

    @Test
    fun `activeStepGoal prend le plus grand effectiveFrom sous ou egal au jour`() {
        val goals = listOf(
            goal("a", "2026-06-01", 8000f),
            goal("b", "2026-06-15", 10000f),
            goal("c", "2026-07-01", 12000f), // futur par rapport au jour testé
        )
        val active = HealthUiAggregations.activeStepGoal(goals, "2026-06-20")
        assertEquals("b", active?.uuid)
        assertEquals(10000f, active?.target)
    }

    @Test
    fun `activeStepGoal ignore les autres types et renvoie null si aucun applicable`() {
        val goals = listOf(goal("x", "2026-06-01", 5f, type = "OTHER"))
        assertNull(HealthUiAggregations.activeStepGoal(goals, "2026-06-20"))
        assertNull(HealthUiAggregations.activeStepGoal(listOf(goal("y", "2026-07-01", 9000f)), "2026-06-20"))
    }

    @Test
    fun `stepsForDay somme les buckets de la date`() {
        val buckets = listOf(
            bucket("2026-06-20", "08:00", 1200),
            bucket("2026-06-20", "09:00", 800),
            bucket("2026-06-19", "10:00", 5000), // autre jour
        )
        assertEquals(2000, HealthUiAggregations.stepsForDay(buckets, "2026-06-20"))
    }

    @Test
    fun `stepsByDay agrege par date, tri croissant, derniers N`() {
        val buckets = listOf(
            bucket("2026-06-18", "08:00", 100),
            bucket("2026-06-19", "08:00", 200),
            bucket("2026-06-19", "09:00", 50),
            bucket("2026-06-20", "08:00", 300),
        )
        val last2 = HealthUiAggregations.stepsByDay(buckets, 2)
        assertEquals(listOf("2026-06-19" to 250, "2026-06-20" to 300), last2)
    }

    @Test
    fun `cumulativeStepsForDay produit des sommes cumulees croissantes`() {
        val buckets = listOf(
            bucket("2026-06-20", "10:00", 300),
            bucket("2026-06-20", "08:00", 1200),
            bucket("2026-06-20", "09:00", 800),
            bucket("2026-06-19", "08:00", 5000), // autre jour, ignoré
        )
        // Trié par bucketStart : 1200, 800, 300 → cumulé 1200, 2000, 2300.
        assertEquals(listOf(1200f, 2000f, 2300f), HealthUiAggregations.cumulativeStepsForDay(buckets, "2026-06-20"))
    }

    @Test
    fun `cumulativeStepsForDay vide sans bucket`() {
        assertEquals(emptyList<Float>(), HealthUiAggregations.cumulativeStepsForDay(emptyList(), "2026-06-20"))
    }

    @Test
    fun `stepsBySlot ventile sur 48 tranches de 30 min`() {
        val buckets = listOf(
            bucket("2026-06-20", "08:00", 1200),
            bucket("2026-06-20", "08:30", 300), // même heure, tranche suivante
            bucket("2026-06-20", "09:00", 800),
            bucket("2026-06-19", "10:00", 5000), // autre jour, ignoré
        )
        val s = HealthUiAggregations.stepsBySlot(buckets, "2026-06-20")
        assertEquals(48, s.size)
        assertEquals(1200f, s[16]) // 08:00
        assertEquals(300f, s[17])  // 08:30
        assertEquals(800f, s[18])  // 09:00
        assertEquals(0f, s[0])
        assertEquals(0f, s[19])    // 09:30
    }

    @Test
    fun `stepsByDayCalendar garde tous les jours, 0 pour les vides`() {
        val buckets = listOf(
            bucket("2026-06-20", "08:00", 1000),
            bucket("2026-06-20", "09:00", 500),
            bucket("2026-06-22", "08:00", 3000),
        )
        val days = listOf("2026-06-20", "2026-06-21", "2026-06-22") // le 21 est vide
        assertEquals(
            listOf("2026-06-20" to 1500f, "2026-06-21" to 0f, "2026-06-22" to 3000f),
            HealthUiAggregations.stepsByDayCalendar(buckets, days),
        )
    }

    @Test
    fun `clipFutureSlots masque les tranches posterieures a la tranche courante`() {
        val series = List(48) { 100f } // toutes les tranches non nulles (proraté)
        val clipped = HealthUiAggregations.clipFutureSlots(series, currentSlot = 16) // 08:00
        assertEquals(48, clipped.size)
        assertEquals(100f, clipped[15]) // tranche passée conservée
        assertEquals(100f, clipped[16]) // tranche COURANTE incluse
        assertEquals(0f, clipped[17])   // tranche suivante (future) masquée
        assertEquals(0f, clipped[47])   // dernière tranche masquée
    }

    @Test
    fun `clipFutureSlots minuit (tranche 0) ne garde que la 1re, 23h30 garde tout`() {
        val series = List(48) { 5f }
        val atMidnight = HealthUiAggregations.clipFutureSlots(series, currentSlot = 0)
        assertEquals(5f, atMidnight[0])
        assertEquals(0f, atMidnight[1])
        assertEquals(0f, atMidnight[47])
        val atEndOfDay = HealthUiAggregations.clipFutureSlots(series, currentSlot = 47)
        assertEquals(48, atEndOfDay.count { it == 5f }) // aucune tranche masquée
    }

    @Test
    fun `metricByDayCalendar 0 pour les jours sans mesure`() {
        val metrics = listOf(
            metric("HEART_RATE", 66f, "2026-06-20", "08:00"),
            metric("HEART_RATE", 60f, "2026-06-20", "20:00"), // startTime max → représentant
            metric("HEART_RATE", 70f, "2026-06-22", "09:00"),
        )
        val days = listOf("2026-06-20", "2026-06-21", "2026-06-22")
        assertEquals(
            listOf("2026-06-20" to 60f, "2026-06-21" to 0f, "2026-06-22" to 70f),
            HealthUiAggregations.metricByDayCalendar(metrics, "HEART_RATE", days),
        )
    }

    @Test
    fun `latestMetric prend la date puis le startTime max`() {
        val metrics = listOf(
            metric("HEART_RATE", 60f, "2026-06-19", "23:00"),
            metric("HEART_RATE", 72f, "2026-06-20", "06:00"),
            metric("HEART_RATE", 75f, "2026-06-20", "16:10"),
        )
        val latest = HealthUiAggregations.latestMetric(metrics, "HEART_RATE")
        assertEquals(75f, latest?.value)
        assertEquals("16:10", latest?.startTime)
    }

    @Test
    fun `metricByDay retient la row la plus tardive par jour`() {
        val metrics = listOf(
            metric("SLEEP", 400f, "2026-06-19", null),
            metric("HEART_RATE", 60f, "2026-06-19", "07:00"),
            metric("HEART_RATE", 66f, "2026-06-19", "20:00"),
            metric("HEART_RATE", 70f, "2026-06-20", "08:00"),
        )
        val hr = HealthUiAggregations.metricByDay(metrics, "HEART_RATE", 7)
        assertEquals(listOf("2026-06-19" to 66f, "2026-06-20" to 70f), hr)
    }

    @Test
    fun `averageOfFilledDays moyenne uniquement les jours renseignes`() {
        // 2 jours renseignés (8000, 6000) + 5 vides → moyenne 7000, pas 14000/7.
        val week = listOf(0f, 8000f, 0f, 0f, 6000f, 0f, 0f)
        assertEquals(7000f, HealthUiAggregations.averageOfFilledDays(week)!!, 0.001f)
        // Aucun jour renseigné → null (pas de ligne de moyenne).
        assertNull(HealthUiAggregations.averageOfFilledDays(listOf(0f, 0f, 0f)))
        assertNull(HealthUiAggregations.averageOfFilledDays(emptyList()))
    }

    @Test
    fun `stepProgress borne entre 0 et 1 et gere objectif nul`() {
        assertEquals(0.5f, HealthUiAggregations.stepProgress(5000, 10000f), 0.0001f)
        assertEquals(1f, HealthUiAggregations.stepProgress(12000, 10000f), 0.0001f)
        assertEquals(0f, HealthUiAggregations.stepProgress(5000, null), 0.0001f)
        assertEquals(0f, HealthUiAggregations.stepProgress(5000, 0f), 0.0001f)
    }

    // ------------------------- Suivi du poids (WEIGHT_KG) -------------------------

    @Test
    fun `latestMetric WEIGHT_KG renvoie la derniere pesee (date max)`() {
        val metrics = listOf(
            metric(HealthUiAggregations.METRIC_TYPE_WEIGHT, 75.2f, "2026-07-01"),
            metric(HealthUiAggregations.METRIC_TYPE_WEIGHT, 74.5f, "2026-07-05"),
            metric("HEART_RATE", 60f, "2026-07-07", "08:00"), // autre type, ignoré
        )
        val latest = HealthUiAggregations.latestMetric(metrics, HealthUiAggregations.METRIC_TYPE_WEIGHT)
        assertEquals(74.5f, latest?.value)
        assertEquals("2026-07-05", latest?.date)
    }

    @Test
    fun `weightByDayCalendar laisse null les jours sans pesee (pas d'interpolation)`() {
        val metrics = listOf(
            metric(HealthUiAggregations.METRIC_TYPE_WEIGHT, 75.2f, "2026-07-01"),
            metric(HealthUiAggregations.METRIC_TYPE_WEIGHT, 74.6f, "2026-07-03"),
            metric("SLEEP", 400f, "2026-07-02"), // autre type, ne comble pas le trou
        )
        val days = listOf("2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04")
        assertEquals(
            listOf(
                "2026-07-01" to 75.2f,
                "2026-07-02" to null,     // jour sans pesée → absent (null), pas 0
                "2026-07-03" to 74.6f,
                "2026-07-04" to null,
            ),
            HealthUiAggregations.weightByDayCalendar(metrics, days),
        )
    }

    @Test
    fun `pesee du jour — uuid deterministe user+type+date, la re-saisie ecrase`() {
        val type = HealthUiAggregations.METRIC_TYPE_WEIGHT
        val first = HealthUuids.metric(1, type, "2026-07-07", null)
        val resaisie = HealthUuids.metric(1, type, "2026-07-07", null)
        assertEquals(first, resaisie) // même row Room → REPLACE = écrasement du jour
        assertNotEquals(first, HealthUuids.metric(1, type, "2026-07-08", null)) // autre jour
        assertNotEquals(first, HealthUuids.metric(2, type, "2026-07-07", null)) // autre user
    }

    @Test
    fun `stepGoalUuid est deterministe par user, type et jour`() {
        val a = HealthUiAggregations.stepGoalUuid(1, "2026-06-20")
        val b = HealthUiAggregations.stepGoalUuid(1, "2026-06-20")
        val c = HealthUiAggregations.stepGoalUuid(1, "2026-06-21")
        assertEquals(a, b)
        assertEquals(false, a == c)
    }

    @Test
    fun `sleepStageMinutesByDay ventile les stades en 4 familles par jour de reveil`() {
        val sessions = listOf(
            // Nuit finissant le 03 : profond 90, léger 200 (+ SLEEPING générique 30
            // → léger), paradoxal 70, éveillé 20 + 10.
            "2026-07-03" to listOf(
                SleepStageSlice(5, 90),   // DEEP
                SleepStageSlice(4, 200),  // LIGHT
                SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_GENERIC, 30), // SLEEPING → léger
                SleepStageSlice(6, 70),   // REM
                SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE, 20),
                SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE_IN_BED, 10),
            ),
            // Session hors fenêtre : ignorée.
            "2026-06-20" to listOf(SleepStageSlice(5, 500)),
        )
        val days = listOf("2026-07-01", "2026-07-02", "2026-07-03")
        val out = HealthUiAggregations.sleepStageMinutesByDay(sessions, days)
        assertEquals(setOf("2026-07-03"), out.keys)
        assertEquals(listOf(90f, 230f, 70f, 30f), out["2026-07-03"]) // [profond, léger, REM, éveillé]
    }

    @Test
    fun `sleepStageMinutesByDay cumule plusieurs sessions du meme jour`() {
        // Nuit + sieste finissant le même jour → sommes par famille.
        val sessions = listOf(
            "2026-07-03" to listOf(SleepStageSlice(5, 60), SleepStageSlice(4, 100)),
            "2026-07-03" to listOf(SleepStageSlice(4, 40)),
        )
        val out = HealthUiAggregations.sleepStageMinutesByDay(sessions, listOf("2026-07-03"))
        assertEquals(listOf(60f, 140f, 0f, 0f), out["2026-07-03"])
    }

    @Test
    fun `sleepPhaseTimeline interprete 15h et plus comme la veille et trie par debut`() {
        val slices = listOf(
            // 00:15 (après minuit) → +15 ; 23:30 (veille au soir) → -30.
            SleepPhaseSliceReading(startTime = "00:15", minutes = 45, bucket = 1, endDate = "2026-07-10"),
            SleepPhaseSliceReading(startTime = "23:30", minutes = 45, bucket = 0, endDate = "2026-07-10"),
            // Sieste 14:00 → même jour (+840), après la nuit dans la chronologie.
            SleepPhaseSliceReading(startTime = "14:00", minutes = 30, bucket = 2, endDate = "2026-07-10"),
        )
        val out = HealthUiAggregations.sleepPhaseTimeline(slices)
        assertEquals(listOf(-30, 15, 840), out.map { it.startMin })
        assertEquals(listOf(0, 1, 2), out.map { it.bucket })
        assertEquals(listOf(45, 45, 30), out.map { it.minutes })
    }
}
