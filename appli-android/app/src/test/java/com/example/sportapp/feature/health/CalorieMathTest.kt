package com.example.sportapp.feature.health

import com.example.sportapp.feature.health.domain.CalorieMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Dérivation calorique (Option B + inversion 2026-07-06). Deux chemins selon la sémantique du type
 * mesuré : [CalorieMath.fromTotal] (HC = total → actives = max(0, total − BMR)) et
 * [CalorieMath.fromActive] (montre = actives → total = actives + BMR). BMR estimé Mifflin-St Jeor,
 * fallback profil incomplet. Logique pure (aucune dépendance Android).
 */
class CalorieMathTest {

    private val today = LocalDate.of(2026, 7, 6)

    @Test
    fun `age from birth date, null cases`() {
        assertEquals(30, CalorieMath.ageYears("1996-03-01", today))
        assertEquals(29, CalorieMath.ageYears("1996-12-31", today)) // anniversaire pas encore passé
        assertNull(CalorieMath.ageYears(null, today))
        assertNull(CalorieMath.ageYears("", today))
        assertNull(CalorieMath.ageYears("pas-une-date", today))
        assertNull(CalorieMath.ageYears("2030-01-01", today)) // future
    }

    // ─── fromTotal (source HC : total mesuré → actives dérivées) ────────────────────

    @Test
    fun `fromTotal male = Mifflin-St Jeor + 5, active = total - bmr`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        val b = CalorieMath.fromTotal(2500, weightKg = 80f, heightCm = 180f, birthDate = "1996-01-01", sex = "MALE", today = today)
        assertEquals(2500, b.totalKcal)
        assertEquals(1780, b.bmrKcal)
        assertEquals(720, b.activeKcal)
    }

    @Test
    fun `fromTotal female = Mifflin-St Jeor - 161`() {
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25 -> 1345
        val b = CalorieMath.fromTotal(1800, weightKg = 60f, heightCm = 165f, birthDate = "2001-02-01", sex = "female", today = today)
        assertEquals(1345, b.bmrKcal)
        assertEquals(455, b.activeKcal)
    }

    @Test
    fun `fromTotal below BMR clamps active to zero (never negative)`() {
        val b = CalorieMath.fromTotal(1000, weightKg = 80f, heightCm = 180f, birthDate = "1996-01-01", sex = "MALE", today = today)
        assertEquals(1780, b.bmrKcal)
        assertEquals(0, b.activeKcal)
    }

    @Test
    fun `fromTotal incomplete profile keeps total only (bmr and active null)`() {
        val noWeight = CalorieMath.fromTotal(2000, weightKg = null, heightCm = 180f, birthDate = "1996-01-01", sex = "MALE", today = today)
        assertEquals(2000, noWeight.totalKcal)
        assertNull(noWeight.bmrKcal)
        assertNull(noWeight.activeKcal)

        // Sexe non exploitable par la formule.
        val otherSex = CalorieMath.fromTotal(2000, weightKg = 80f, heightCm = 180f, birthDate = "1996-01-01", sex = "OTHER", today = today)
        assertNull(otherSex.bmrKcal)
        assertNull(otherSex.activeKcal)

        // Date de naissance absente → âge inconnu → fallback.
        val noAge = CalorieMath.fromTotal(2000, weightKg = 80f, heightCm = 180f, birthDate = null, sex = "MALE", today = today)
        assertNull(noAge.bmrKcal)
    }

    // ─── fromActive (source montre : actives mesurées → total dérivé) ───────────────

    @Test
    fun `fromActive male = active mesuree, total = active + bmr`() {
        // Cas réel Watch4 : actives=706, BMR=1780 → total=2486 (au lieu de l'incohérent actives=0).
        val b = CalorieMath.fromActive(706, weightKg = 80f, heightCm = 180f, birthDate = "1996-01-01", sex = "MALE", today = today)
        assertEquals(706, b.activeKcal)
        assertEquals(1780, b.bmrKcal)
        assertEquals(2486, b.totalKcal)
    }

    @Test
    fun `fromActive incomplete profile keeps active only (bmr and total null)`() {
        val b = CalorieMath.fromActive(706, weightKg = null, heightCm = 180f, birthDate = "1996-01-01", sex = "MALE", today = today)
        assertEquals(706, b.activeKcal)
        assertNull(b.bmrKcal)
        assertNull(b.totalKcal)
    }
}
