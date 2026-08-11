package com.example.sportapp.feature.health

import com.example.sportapp.feature.health.domain.StepSamplingLogic
import com.example.sportapp.feature.health.domain.StepSamplingLogic.SamplingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM purs de la logique d'échantillonnage des pas (deltas / rattrapage /
 * rollover minuit / invariant de somme). Le worker + la lecture HC se testent sur
 * device (Functional review) ; ici on verrouille la mécanique déterministe.
 */
class StepSamplingLogicTest {

    private val never = SamplingState(date = "", lastTotal = 0, openSlot = "", openSlotBase = 0)

    @Test
    fun `1er releve du jour est un rattrapage (tout le total dans la tranche courante)`() {
        val step = StepSamplingLogic.next(never, "2026-07-04", "08:00", 3000)
        assertTrue(step.resetDay)
        assertEquals("08:00", step.slot)
        assertEquals(3000, step.value)
        assertEquals(SamplingState("2026-07-04", 3000, "08:00", 0), step.newState)
    }

    @Test
    fun `meme tranche SET la valeur (total - base)`() {
        val prev = SamplingState("2026-07-04", 3000, "08:00", 0)
        val step = StepSamplingLogic.next(prev, "2026-07-04", "08:00", 3200)
        assertFalse(step.resetDay)
        assertEquals("08:00", step.slot)
        assertEquals(3200, step.value) // 3200 - base(0)
        assertEquals(3200, step.newState.lastTotal)
    }

    @Test
    fun `changement de tranche telescope (base = total du releve precedent)`() {
        val prev = SamplingState("2026-07-04", 3200, "08:00", 0)
        val step = StepSamplingLogic.next(prev, "2026-07-04", "08:30", 3500)
        assertFalse(step.resetDay)
        assertEquals("08:30", step.slot)
        assertEquals(300, step.value) // 3500 - 3200
        assertEquals(SamplingState("2026-07-04", 3500, "08:30", 3200), step.newState)
    }

    @Test
    fun `rollover minuit repart en rattrapage`() {
        val prev = SamplingState("2026-07-03", 9000, "23:30", 8800)
        val step = StepSamplingLogic.next(prev, "2026-07-04", "00:00", 120)
        assertTrue(step.resetDay)
        assertEquals("00:00", step.slot)
        assertEquals(120, step.value)
        assertEquals(SamplingState("2026-07-04", 120, "00:00", 0), step.newState)
    }

    @Test
    fun `delta negatif (re-agregation HC a la baisse) borne a zero`() {
        val prev = SamplingState("2026-07-04", 3500, "08:30", 3200)
        val step = StepSamplingLogic.next(prev, "2026-07-04", "08:30", 3100)
        assertEquals(0, step.value) // (3100 - 3200) borné à 0
        assertEquals(3100, step.newState.lastTotal)
    }

    @Test
    fun `tranche sautee (worker differe) attribue le gap a la tranche courante`() {
        val prev = SamplingState("2026-07-04", 4000, "10:00", 3800)
        // Passe directement à 11:30 (tranches 10:30 / 11:00 sautées).
        val step = StepSamplingLogic.next(prev, "2026-07-04", "11:30", 4600)
        assertEquals("11:30", step.slot)
        assertEquals(600, step.value) // 4600 - 4000, tout le gap dans 11:30
    }

    @Test
    fun `sequence de releves preserve la SOMME = total courant`() {
        var state = never
        val buckets = mutableMapOf<String, Int>()
        fun apply(today: String, slot: String, total: Int) {
            val step = StepSamplingLogic.next(state, today, slot, total)
            if (step.resetDay) buckets.clear() // rattrapage = efface le jour (tombstone côté worker)
            buckets[step.slot] = step.value    // SET (pas d'accumulation)
            state = step.newState
        }
        apply("2026-07-04", "08:00", 3000) // rattrapage
        apply("2026-07-04", "08:00", 3200) // même tranche
        apply("2026-07-04", "08:30", 3500) // nouvelle tranche
        apply("2026-07-04", "09:00", 3600) // nouvelle tranche
        apply("2026-07-04", "09:00", 4000) // même tranche (SET, pas +)
        // SOMME des tranches = dernier total lu (invariant Samsung total = SUM).
        assertEquals(4000, buckets.values.sum())
        assertEquals(mapOf("08:00" to 3200, "08:30" to 300, "09:00" to 500), buckets)
    }
}
