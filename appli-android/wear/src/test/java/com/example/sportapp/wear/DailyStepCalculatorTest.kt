package com.example.sportapp.wear

import com.example.sportapp.wear.DailyStepCalculator.Baseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests JVM purs du fallback « pas du jour » (baseline / rollover / reboot). */
class DailyStepCalculatorTest {

    @Test
    fun `premier echantillon du jour pose la baseline et 0 pas`() {
        val r = DailyStepCalculator.compute(counter = 27_339, todayEpochDay = 20_000, saved = null)
        assertEquals(0L, r.daySteps)
        assertEquals(Baseline(20_000, 27_339), r.newBaseline)
    }

    @Test
    fun `meme jour compteur croissant donne le delta sans re-baseline`() {
        val saved = Baseline(20_000, 27_339)
        val r = DailyStepCalculator.compute(counter = 27_339 + 6_380, todayEpochDay = 20_000, saved = saved)
        assertEquals(6_380L, r.daySteps)
        assertNull(r.newBaseline) // baseline inchangée
    }

    @Test
    fun `changement de jour re-baseline a 0`() {
        val saved = Baseline(20_000, 27_339)
        val r = DailyStepCalculator.compute(counter = 40_000, todayEpochDay = 20_001, saved = saved)
        assertEquals(0L, r.daySteps)
        assertEquals(Baseline(20_001, 40_000), r.newBaseline)
    }

    @Test
    fun `reboot compteur inferieur re-baseline`() {
        val saved = Baseline(20_000, 27_339)
        // Après reboot, le compteur repart bas (< baseline) le même jour.
        val r = DailyStepCalculator.compute(counter = 120, todayEpochDay = 20_000, saved = saved)
        assertEquals(0L, r.daySteps)
        assertEquals(Baseline(20_000, 120), r.newBaseline)
    }
}
