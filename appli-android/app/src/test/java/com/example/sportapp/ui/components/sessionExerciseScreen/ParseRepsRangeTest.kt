package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JVM de la fonction pure [parseRepsRange] extraite avec le composable
 * ExerciseBlock (refactor 2026-05-30, commit eae820a). Couvre les 3 formats de
 * reps cible (range "a-b", min ouvert "n+", valeur unique "n") + les cas
 * d'entrée invalide / null. Fonction pure, pas de Robolectric requis.
 */
class ParseRepsRangeTest {

    @Test
    fun `range a-b parses to a until b`() {
        assertEquals(8..12, parseRepsRange("8-12"))
    }

    @Test
    fun `range tolerates surrounding and inner whitespace`() {
        assertEquals(8..12, parseRepsRange("  8 - 12 "))
    }

    @Test
    fun `open minimum n plus parses to n until 100`() {
        assertEquals(10..100, parseRepsRange("10+"))
    }

    @Test
    fun `single value n parses to n until n`() {
        assertEquals(5..5, parseRepsRange("5"))
    }

    @Test
    fun `null input returns null`() {
        assertNull(parseRepsRange(null))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(parseRepsRange("   "))
    }

    @Test
    fun `non numeric single value returns null`() {
        assertNull(parseRepsRange("abc"))
    }

    @Test
    fun `range with non numeric bound returns null`() {
        assertNull(parseRepsRange("8-x"))
    }

    @Test
    fun `range missing upper bound returns null`() {
        assertNull(parseRepsRange("8-"))
    }

    @Test
    fun `open minimum with non numeric base returns null`() {
        assertNull(parseRepsRange("x+"))
    }
}
