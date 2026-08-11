package com.example.sportapp.feature.health

import com.example.sportapp.feature.health.domain.HealthConnectMapper
import com.example.sportapp.feature.health.domain.HealthDataType
import com.example.sportapp.feature.health.domain.HeartRateSample
import com.example.sportapp.feature.health.domain.SleepStageSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Tests JVM purs du façonnage HC -> contrat serveur (health_step_counts /
 * health_metrics). Le vrai flow de permission + les lectures HC se testent sur
 * device (Functional review) ; ici on verrouille les conversions déterministes.
 */
class HealthConnectMapperTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `date formate en YYYY-MM-DD dans le fuseau local`() {
        // 2026-06-17T08:30:00Z == 10:30 heure de Paris (été, +02:00) -> même jour.
        val instant = Instant.parse("2026-06-17T08:30:00Z")
        assertEquals("2026-06-17", HealthConnectMapper.date(instant, paris))
    }

    @Test
    fun `date bascule de jour selon le fuseau`() {
        // 23:30 UTC = 01:30 le lendemain à Paris (+02:00).
        val instant = Instant.parse("2026-06-17T23:30:00Z")
        assertEquals("2026-06-18", HealthConnectMapper.date(instant, paris))
    }

    @Test
    fun `hhmm formate l'heure locale sur 2 chiffres`() {
        val instant = Instant.parse("2026-06-17T06:05:00Z") // 08:05 Paris
        assertEquals("08:05", HealthConnectMapper.hhmm(instant, paris))
    }

    @Test
    fun `stepBucket stampe date + bucketStart + steps`() {
        val start = Instant.parse("2026-06-17T07:00:00Z") // 09:00 Paris
        val bucket = HealthConnectMapper.stepBucket(start, steps = 1234L, zone = paris)
        assertEquals("2026-06-17", bucket.date)
        assertEquals("09:00", bucket.bucketStart)
        assertEquals(1234L, bucket.steps)
    }

    @Test
    fun `metric derive l'unite du type et respecte le vocabulaire UPPER_CASE`() {
        val distance = HealthConnectMapper.metric(HealthDataType.DISTANCE, value = 5432.0, date = "2026-06-17")
        assertEquals("DISTANCE", distance.type.name)
        assertEquals("m", distance.unit)
        assertEquals(5432.0, distance.value, 0.0001)
        assertNull(distance.startTime)

        val hr = HealthConnectMapper.metric(HealthDataType.HEART_RATE, value = 72.0, date = "2026-06-17", startTime = "06:30")
        assertEquals("HEART_RATE", hr.type.name)
        assertEquals("bpm", hr.unit)
        assertEquals("06:30", hr.startTime)
    }

    @Test
    fun `les 4 types metrique matchent le vocabulaire serveur health_metrics`() {
        // Le serveur attend type ∈ {HEART_RATE, SLEEP, DISTANCE, ACTIVE_CALORIES}.
        assertEquals("HEART_RATE", HealthDataType.HEART_RATE.name)
        assertEquals("SLEEP", HealthDataType.SLEEP.name)
        assertEquals("DISTANCE", HealthDataType.DISTANCE.name)
        assertEquals("ACTIVE_CALORIES", HealthDataType.ACTIVE_CALORIES.name)
        // Unités self-describing attendues côté health_metrics.unit.
        assertEquals("bpm", HealthDataType.HEART_RATE.wireUnit)
        assertEquals("min", HealthDataType.SLEEP.wireUnit)
        assertEquals("kcal", HealthDataType.ACTIVE_CALORIES.wireUnit)
    }

    @Test
    fun `date en UTC pur reste stable`() {
        val instant = Instant.parse("2026-01-15T12:00:00Z")
        assertEquals("2026-01-15", HealthConnectMapper.date(instant, ZoneOffset.UTC))
        assertEquals("12:00", HealthConnectMapper.hhmm(instant, ZoneOffset.UTC))
    }

    // -------------------- SpO2 --------------------

    @Test
    fun `spo2 map en metrique SPO2 pourcent avec ancrage horaire`() {
        val time = Instant.parse("2026-06-18T03:20:00Z") // 05:20 à Paris (été)
        val r = HealthConnectMapper.spo2(percentage = 95.0, time = time, zone = paris)
        assertEquals("SPO2", r.type.name)
        assertEquals("%", r.unit)
        assertEquals(95.0, r.value, 0.0001)
        assertEquals("2026-06-18", r.date)
        // startTime non-null : rend l'uuid d'import unique par mesure
        // (user + SPO2 + date + startTime) et ancre la mesure intraday.
        assertEquals("05:20", r.startTime)
    }

    @Test
    fun `SPO2 porte le vocabulaire wire attendu`() {
        assertEquals("SPO2", HealthDataType.SPO2.name)
        assertEquals("%", HealthDataType.SPO2.wireUnit)
    }

    // ------------- Ventilation intraday par tranche de 30 min (charts 0-24h) -------------

    @Test
    fun `isAsleepStage exclut les stades d'eveil`() {
        assertEquals(false, HealthConnectMapper.isAsleepStage(HealthConnectMapper.SLEEP_STAGE_AWAKE))
        assertEquals(false, HealthConnectMapper.isAsleepStage(HealthConnectMapper.SLEEP_STAGE_OUT_OF_BED))
        assertEquals(false, HealthConnectMapper.isAsleepStage(HealthConnectMapper.SLEEP_STAGE_AWAKE_IN_BED))
        assertEquals(true, HealthConnectMapper.isAsleepStage(4)) // LIGHT
        assertEquals(true, HealthConnectMapper.isAsleepStage(2)) // SLEEPING
    }

    @Test
    fun `slotOfDay indexe par tranche de 30 min en local`() {
        assertEquals(48, HealthConnectMapper.SLOTS_PER_DAY)
        // 06:20Z = 08:20 Paris → tranche 16 (08:00–08:30). 06:40Z = 08:40 → tranche 17.
        assertEquals(16, HealthConnectMapper.slotOfDay(Instant.parse("2026-06-17T06:20:00Z"), paris))
        assertEquals(17, HealthConnectMapper.slotOfDay(Instant.parse("2026-06-17T06:40:00Z"), paris))
        // Minuit local → tranche 0 ; 23:30 local → tranche 47.
        assertEquals(0, HealthConnectMapper.slotOfDay(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC))
        assertEquals(47, HealthConnectMapper.slotOfDay(Instant.parse("2026-01-15T23:30:00Z"), ZoneOffset.UTC))
    }

    @Test
    fun `slotIndexHhmm rend l'heure de debut de chaque tranche`() {
        assertEquals("00:00", HealthConnectMapper.slotIndexHhmm(0))
        assertEquals("00:30", HealthConnectMapper.slotIndexHhmm(1))
        assertEquals("10:00", HealthConnectMapper.slotIndexHhmm(20))
        assertEquals("10:30", HealthConnectMapper.slotIndexHhmm(21))
        assertEquals("23:30", HealthConnectMapper.slotIndexHhmm(47))
    }

    @Test
    fun `slotIndexHhmm est l'inverse de slotOfDay (tranche FC intraday)`() {
        // Une mesure à 10:40 Paris tombe dans la tranche 21 ; son heure de tranche = "10:30".
        val instant = Instant.parse("2026-06-17T08:40:00Z") // 10:40 Paris (été)
        val slot = HealthConnectMapper.slotOfDay(instant, paris)
        assertEquals(21, slot)
        assertEquals("10:30", HealthConnectMapper.slotIndexHhmm(slot))
    }

    @Test
    fun `valuesBySlot place les valeurs aux bonnes tranches`() {
        val out = HealthConnectMapper.valuesBySlot(listOf(12 to 55f, 33 to 75f))
        assertEquals(48, out.size)
        assertEquals(55f, out[12]) // 06:00
        assertEquals(75f, out[33]) // 16:30
        assertEquals(0f, out[0])
    }

    @Test
    fun `minutesBySlot decoupe un intervalle a la frontiere de tranche`() {
        val dayStart = Instant.parse("2026-06-17T22:00:00Z") // 00:00 Paris (été)
        val dayEnd = Instant.parse("2026-06-18T22:00:00Z")
        // 00:40Z → 01:20Z = 02:40 → 03:20 Paris : 20 min tranche 5 (02:30–03:00),
        // 20 min tranche 6 (03:00–03:30).
        val start = Instant.parse("2026-06-18T00:40:00Z")
        val end = Instant.parse("2026-06-18T01:20:00Z")
        val out = HealthConnectMapper.minutesBySlot(listOf(start to end), dayStart, dayEnd, paris)
        assertEquals(48, out.size)
        assertEquals(20f, out[5], 0.01f)
        assertEquals(20f, out[6], 0.01f)
        assertEquals(0f, out[4], 0.01f)
    }

    @Test
    fun `minutesBySlot clippe hors de la fenetre du jour`() {
        val dayStart = Instant.parse("2026-06-18T00:00:00Z")
        val dayEnd = Instant.parse("2026-06-19T00:00:00Z")
        // 23:00 veille → 01:00 aujourd'hui (UTC) : seule 00:00→01:00 est dans la fenêtre,
        // répartie sur les tranches 0 (00:00–00:30) et 1 (00:30–01:00).
        val start = Instant.parse("2026-06-17T23:00:00Z")
        val end = Instant.parse("2026-06-18T01:00:00Z")
        val out = HealthConnectMapper.minutesBySlot(listOf(start to end), dayStart, dayEnd, ZoneOffset.UTC)
        assertEquals(30f, out[0], 0.01f)
        assertEquals(30f, out[1], 0.01f)
        assertEquals(0f, out[47], 0.01f)
    }

    // -------------------- FC : dernière mesure --------------------

    @Test
    fun `latestHeartRate prend le sample le plus recent quel que soit l'ordre`() {
        // Samples aplatis de plusieurs records, ordre non garanti.
        val samples = listOf(
            HeartRateSample(Instant.parse("2026-06-18T10:00:00Z"), 60),
            HeartRateSample(Instant.parse("2026-06-18T14:10:00Z"), 75), // le plus récent
            HeartRateSample(Instant.parse("2026-06-18T08:30:00Z"), 55),
            HeartRateSample(Instant.parse("2026-06-18T12:00:00Z"), 68),
        )
        val r = HealthConnectMapper.latestHeartRate(samples, paris)!!
        assertEquals(75.0, r.value, 0.0001)
        assertEquals("16:10", r.time) // 14:10 UTC = 16:10 Paris (été)
    }

    @Test
    fun `latestHeartRate sur fenetre vide renvoie null`() {
        assertNull(HealthConnectMapper.latestHeartRate(emptyList(), paris))
    }

    // -------------------- Sommeil : temps dormi vs temps au lit --------------------

    @Test
    fun `sleepSession exclut les stades d'eveil du temps dormi`() {
        val start = Instant.parse("2026-06-17T23:00:00Z")
        val end = Instant.parse("2026-06-18T07:00:00Z") // 8 h au lit = 480 min
        val stages = listOf(
            SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE, 20),        // exclu
            SleepStageSlice(4, 200),                                           // LIGHT -> dormi
            SleepStageSlice(5, 120),                                           // DEEP -> dormi
            SleepStageSlice(6, 90),                                            // REM -> dormi
            SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE_IN_BED, 30), // exclu
            SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_OUT_OF_BED, 10),   // exclu
        )
        val r = HealthConnectMapper.sleepSession(start, end, stages, ZoneOffset.UTC)
        assertEquals(480L, r.inBedMinutes)   // end - start
        assertEquals(410L, r.asleepMinutes)  // 200 + 120 + 90
        assertEquals("23:00", r.startTime)
        assertEquals("07:00", r.endTime)
    }

    @Test
    fun `importer et apercu partagent le meme temps dormi (source unique mapper)`() {
        // Nuit à stades mixtes : dormi = LIGHT + DEEP + REM (hors AWAKE / AWAKE_IN_BED).
        val start = Instant.parse("2026-06-17T23:00:00Z")
        val end = Instant.parse("2026-06-18T07:00:00Z") // au lit 480 min
        val stages = listOf(
            SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE, 20),
            SleepStageSlice(4, 200),                                            // LIGHT
            SleepStageSlice(5, 120),                                            // DEEP
            SleepStageSlice(6, 61),                                             // REM
            SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE_IN_BED, 14),
        )
        val session = HealthConnectMapper.sleepSession(start, end, stages, ZoneOffset.UTC)
        // Aperçu = asleepMinutes de la session ; Importer = somme des sessions.
        val apercu = session.asleepMinutes
        val importer = listOf(session).sumOf { it.asleepMinutes }
        assertEquals(381L, apercu)     // 200 + 120 + 61 (= 6 h 21)
        assertEquals(apercu, importer) // hub (Room import) == aperçu : source unique
    }

    @Test
    fun `sleepSession sans stades retombe sur la duree de session`() {
        val start = Instant.parse("2026-06-18T01:00:00Z")
        val end = Instant.parse("2026-06-18T06:30:00Z") // 5 h 30 = 330 min
        val r = HealthConnectMapper.sleepSession(start, end, emptyList(), ZoneOffset.UTC)
        assertEquals(330L, r.inBedMinutes)
        assertEquals(330L, r.asleepMinutes) // fallback = temps au lit
        assertEquals(r.startTime, r.asleepStartTime) // sans stades : endormi = mise au lit
    }

    @Test
    fun `sleepSession endormissement = debut du premier stade hors eveil`() {
        val start = Instant.parse("2026-06-17T23:00:00Z") // mise au lit 23:00
        val end = Instant.parse("2026-06-18T07:00:00Z")
        val stages = listOf(
            // Éveil initial 23:00 → 23:20 : ne compte pas comme endormissement.
            SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE, 20, Instant.parse("2026-06-17T23:00:00Z")),
            SleepStageSlice(4, 200, Instant.parse("2026-06-17T23:20:00Z")), // LIGHT → endormi à 23:20
            SleepStageSlice(5, 120, Instant.parse("2026-06-18T02:40:00Z")), // DEEP (plus tardif, ignoré)
        )
        val r = HealthConnectMapper.sleepSession(start, end, stages, ZoneOffset.UTC)
        assertEquals("23:00", r.startTime)
        assertEquals("23:20", r.asleepStartTime)
    }

    @Test
    fun `sleepSession stades sans horodatage retombent sur la mise au lit`() {
        val start = Instant.parse("2026-06-18T00:30:00Z")
        val end = Instant.parse("2026-06-18T06:00:00Z")
        val stages = listOf(SleepStageSlice(2, 300)) // SLEEPING, sans start
        val r = HealthConnectMapper.sleepSession(start, end, stages, ZoneOffset.UTC)
        assertEquals("00:30", r.asleepStartTime) // fallback = start de session
    }

    @Test
    fun `sleepSession multi-sessions produit des valeurs distinctes`() {
        val night = HealthConnectMapper.sleepSession(
            Instant.parse("2026-06-18T00:00:00Z"),
            Instant.parse("2026-06-18T07:00:00Z"), // 420 min au lit
            listOf(
                SleepStageSlice(2, 380),                                   // SLEEPING -> dormi
                SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE, 40),
            ),
            ZoneOffset.UTC,
        )
        val nap = HealthConnectMapper.sleepSession(
            Instant.parse("2026-06-18T14:00:00Z"),
            Instant.parse("2026-06-18T14:45:00Z"), // 45 min au lit
            listOf(
                SleepStageSlice(2, 40),
                SleepStageSlice(HealthConnectMapper.SLEEP_STAGE_AWAKE, 5),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(420L, night.inBedMinutes)
        assertEquals(380L, night.asleepMinutes)
        assertEquals(45L, nap.inBedMinutes)
        assertEquals(40L, nap.asleepMinutes)
    }
}
