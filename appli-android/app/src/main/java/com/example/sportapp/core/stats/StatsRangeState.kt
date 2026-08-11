package com.example.sportapp.core.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Etat partage du range de date entre les 3 ecrans Stats (overview,
 * muscle detail, exercise detail). Un seul range applique a tout — l'user le
 * choisit une fois et la valeur suit la navigation.
 *
 * Singleton Hilt : memoire volatile (perd la valeur au kill process —
 * acceptable pour MVP, default `Last3Months` au prochain run).
 */
@Singleton
class StatsRangeState @Inject constructor() {
    private val _range = MutableStateFlow<StatsRange>(StatsRange.Last7Days)
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    fun setRange(range: StatsRange) {
        _range.value = range
    }
}
