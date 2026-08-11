package com.example.sportapp.feature.health

import com.example.sportapp.feature.health.domain.HealthUuids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Verrouille l'idempotence des tranches FC intraday : l'uuid `health_metrics` est
 * DÉTERMINISTE (user + type + date + startTime) → ré-importer le même jour upsert la
 * même row (pas de doublon, pas de dérive des compteurs unsync) et deux devices du
 * même user convergent. Les tranches se distinguent par leur `startTime`, et la
 * moyenne quotidienne (`HEART_RATE`, startTime null) ne collisionne jamais avec une
 * tranche (`HEART_RATE_INTRADAY`, startTime posé).
 */
class HealthUuidsTest {

    private val hrIntraday = "HEART_RATE_INTRADAY"

    @Test
    fun `metric uuid est deterministe pour la meme cle`() {
        val a = HealthUuids.metric(1, hrIntraday, "2026-07-07", "10:30")
        val b = HealthUuids.metric(1, hrIntraday, "2026-07-07", "10:30")
        assertEquals(a, b)
    }

    @Test
    fun `metric uuid differe par tranche (startTime)`() {
        val slot10h00 = HealthUuids.metric(1, hrIntraday, "2026-07-07", "10:00")
        val slot10h30 = HealthUuids.metric(1, hrIntraday, "2026-07-07", "10:30")
        assertNotEquals(slot10h00, slot10h30)
    }

    @Test
    fun `la moyenne quotidienne ne collisionne pas avec une tranche`() {
        // Moyenne 24 h : type HEART_RATE, startTime null. Tranche : type HEART_RATE_INTRADAY,
        // startTime posé. Les deux coexistent sur la même journée sans écraser l'une l'autre.
        val dailyAvg = HealthUuids.metric(1, "HEART_RATE", "2026-07-07", null)
        val slice = HealthUuids.metric(1, hrIntraday, "2026-07-07", "10:30")
        assertNotEquals(dailyAvg, slice)
    }

    @Test
    fun `metric uuid differe par utilisateur et par date`() {
        val base = HealthUuids.metric(1, hrIntraday, "2026-07-07", "10:30")
        assertNotEquals(base, HealthUuids.metric(2, hrIntraday, "2026-07-07", "10:30"))
        assertNotEquals(base, HealthUuids.metric(1, hrIntraday, "2026-07-08", "10:30"))
    }
}
