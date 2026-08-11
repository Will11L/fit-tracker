package com.example.sportapp.core.stats

import com.example.sportapp.core.utils.CustomDateUtils
import java.time.LocalDate

/**
 * Range pre-defini ou custom pour les ecrans Stats. Les bornes sont calculees
 * via [computeBounds] qui retourne (startIso, endIso) au format "yyyy-MM-dd"
 * compatible avec les queries DAO `BETWEEN`.
 *
 * Pour `All`, on retourne ("0000-01-01", today) — tres permissif, l'index
 * SQLite sur `date` rend l'optimisation transparente.
 *
 * Type partage (core/) entre les features stats / muscles / exercises.
 */
sealed class StatsRange(val label: String) {
    object Last7Days : StatsRange("1 week")
    object Last30Days : StatsRange("30 days")
    object Last3Months : StatsRange("3 months")
    object Last6Months : StatsRange("6 months")
    object LastYear : StatsRange("1 year")
    object All : StatsRange("All")
    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : StatsRange("Custom")

    fun computeBounds(today: LocalDate): Pair<String, String> {
        val end = today
        val start = when (this) {
            Last7Days -> today.minusDays(7)
            Last30Days -> today.minusDays(30)
            Last3Months -> today.minusMonths(3)
            Last6Months -> today.minusMonths(6)
            LastYear -> today.minusYears(1)
            All -> LocalDate.of(2000, 1, 1)
            is Custom -> startDate
        }
        val effectiveEnd = if (this is Custom) endDate else end
        return CustomDateUtils.toIsoDay(start) to CustomDateUtils.toIsoDay(effectiveEnd)
    }
}

/** Granularite des buckets X axis du chart Volume by muscle group. */
enum class ChartGranularity { DAILY, WEEKLY }

/** Type de rendu du chart Volume by muscle group : ligne ou histogramme. */
enum class ChartType { LINE, BAR }

/** Metrique affichee dans le chart : volume cumule (kg), sets, ou exercices. */
enum class MetricType { TOTAL_WEIGHT, SETS, EXERCISES }

/**
 * Mode de tri des series dans les 4 sections du chart Stats.
 *  - ALPHA : ordre alphabetique des keys.
 *  - ZONE  : groupe par zone (Chest, Back, ..., Core selon Zones.ALL) puis
 *            par alpha dans chaque zone -> regroupe visuellement les nuances
 *            de couleur de la palette par zone.
 * User feedback 2026-05-09 : permet de toggler entre l'ordre alphabetique
 * (utile pour retrouver un muscle precis) et l'ordre par zone (utile pour
 * voir les tendances d'un groupe musculaire).
 *
 * Note : nom prefixe `Stats` pour eviter le conflit avec `SortMode` deja
 * defini dans `GoalsTabViewModel.kt` (BY_NAME / BY_PRIORITY).
 */
enum class StatsSortMode { ALPHA, ZONE }

/**
 * Stats agregees pour la card Training frequency. [totalValue] est dans
 * l'unite de la metrique selectionnee (kg en TOTAL_WEIGHT, sets ponderes en
 * SETS, exercises distincts en EXERCISES). Le ViewModel ne fait pas de
 * conversion d'unite ; c'est l'UI qui rend selon le contexte metric.
 */
data class FrequencyStats(
    val sessionsCount: Int,
    val avgSessionsPerWeek: Float,
    val totalDaysInRange: Int,
    val totalValue: Float,
    val topGroup: String?,
) {
    companion object {
        val EMPTY = FrequencyStats(0, 0f, 0, 0f, null)
    }
}
