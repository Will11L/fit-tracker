package com.example.sportapp.feature.nutrition

import com.example.sportapp.feature.nutrition.ui.cleanServingLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Nettoyage du `serving_size` OFF en nom de portion (le chip de quantité
 * réaffiche déjà « (X g) »). Verrouille le fix du doublon « 1 portion (30 g)
 * (30 g) » (Functional review 2026-07-05).
 */
class CleanServingLabelTest {

    @Test fun `strips a trailing gram parenthetical`() {
        assertEquals("1 portion", cleanServingLabel("1 portion (30 g)"))
        assertEquals("2 biscuits", cleanServingLabel("2 biscuits (25g)"))
        assertEquals("1 verre", cleanServingLabel("1 verre (200 ml)"))
    }

    @Test fun `keeps a real name without amount`() {
        assertEquals("1 scoop", cleanServingLabel("1 scoop"))
    }

    @Test fun `drops a bare amount (chip grams suffice)`() {
        assertNull(cleanServingLabel("30 g"))
        assertNull(cleanServingLabel("30g"))
        assertNull(cleanServingLabel("330 ml"))
    }

    @Test fun `null or blank yields null`() {
        assertNull(cleanServingLabel(null))
        assertNull(cleanServingLabel("   "))
    }
}
