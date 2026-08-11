package com.example.sportapp.feature.health.domain

import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

/**
 * Répartition calorique du jour. Les 3 champs sont nullable : seul le champ **mesuré** est garanti
 * (les 2 autres sont estimés/dérivés et deviennent null si le profil est incomplet). L'UI affiche
 * chaque ligne dont la valeur est non-null (métabolisme · activité · total).
 */
data class CalorieBreakdown(
    val bmrKcal: Int?,      // métabolisme de base ESTIMÉ (Mifflin-St Jeor) ; null si profil incomplet
    val activeKcal: Int?,   // calories actives (MESURÉES montre, ou dérivées total − BMR)
    val totalKcal: Int?,    // total, BMR inclus (MESURÉ HC, ou dérivé actives + BMR)
)

/**
 * Dérivation calorique (Santé, Option B + inversion 2026-07-06). La sémantique vient du **type
 * stocké** (`health_metrics.type`), pas d'un flag :
 * - **`ACTIVE_CALORIES`** (montre — Health Services `CALORIES_DAILY` s'avère être des actives sur la
 *   Watch4) → mesuré = actives → **total = actives + BMR** ([fromActive]).
 * - **`TOTAL_CALORIES`** (Health Connect — `TotalCaloriesBurnedRecord` est un vrai total BMR-inclus)
 *   → mesuré = total → **actives = max(0, total − BMR)** ([fromTotal]).
 * Le BMR est estimé depuis le profil (Mifflin-St Jeor). Pur → testable JVM. Profil incomplet (poids/
 * taille/âge manquants ou sexe ≠ MALE/FEMALE) → seul le champ mesuré est renseigné.
 */
object CalorieMath {

    /** Âge en années à [today] depuis une date "YYYY-MM-DD" ; null si absente / non parsable / future. */
    fun ageYears(birthDate: String?, today: LocalDate): Int? {
        val raw = birthDate?.takeIf { it.isNotBlank() } ?: return null
        val birth = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return null
        if (birth.isAfter(today)) return null
        return Period.between(birth, today).years
    }

    /**
     * BMR (kcal/jour) via Mifflin-St Jeor. `null` si un champ manque/est invalide ou si le sexe n'est
     * ni MALE ni FEMALE (la formule n'est pas définie autrement) :
     *   MALE   = 10·kg + 6,25·cm − 5·âge + 5
     *   FEMALE = 10·kg + 6,25·cm − 5·âge − 161
     */
    fun bmr(weightKg: Float?, heightCm: Float?, ageYears: Int?, sex: String?): Double? {
        val w = weightKg?.takeIf { it > 0f } ?: return null
        val h = heightCm?.takeIf { it > 0f } ?: return null
        val a = ageYears?.takeIf { it in 0..130 } ?: return null
        val base = 10.0 * w + 6.25 * h - 5.0 * a
        return when (sex?.uppercase()) {
            "MALE" -> base + 5.0
            "FEMALE" -> base - 161.0
            else -> null
        }
    }

    /** Depuis un TOTAL mesuré (HC) : `actives = max(0, total − BMR)`. Profil incomplet → total seul. */
    fun fromTotal(
        totalKcal: Int,
        weightKg: Float?,
        heightCm: Float?,
        birthDate: String?,
        sex: String?,
        today: LocalDate,
    ): CalorieBreakdown {
        val bmrInt = bmr(weightKg, heightCm, ageYears(birthDate, today), sex)?.roundToInt()
            ?: return CalorieBreakdown(bmrKcal = null, activeKcal = null, totalKcal = totalKcal)
        return CalorieBreakdown(
            bmrKcal = bmrInt,
            activeKcal = (totalKcal - bmrInt).coerceAtLeast(0),
            totalKcal = totalKcal,
        )
    }

    /** Depuis des ACTIVES mesurées (montre) : `total = actives + BMR`. Profil incomplet → actives seules. */
    fun fromActive(
        activeKcal: Int,
        weightKg: Float?,
        heightCm: Float?,
        birthDate: String?,
        sex: String?,
        today: LocalDate,
    ): CalorieBreakdown {
        val bmrInt = bmr(weightKg, heightCm, ageYears(birthDate, today), sex)?.roundToInt()
            ?: return CalorieBreakdown(bmrKcal = null, activeKcal = activeKcal, totalKcal = null)
        return CalorieBreakdown(
            bmrKcal = bmrInt,
            activeKcal = activeKcal,
            totalKcal = activeKcal + bmrInt,
        )
    }
}
