package com.example.sportapp.feature.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.local.NutritionGoalDao
import com.example.sportapp.core.stats.ChartGranularity
import com.example.sportapp.core.stats.ChartType
import com.example.sportapp.core.stats.StatsRange
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.MacroSeries
import com.example.sportapp.feature.nutrition.domain.TopFood
import com.example.sportapp.feature.nutrition.domain.aggregateMacroSeries
import com.example.sportapp.feature.nutrition.domain.earliestMealDate
import com.example.sportapp.feature.nutrition.domain.granularityFor
import com.example.sportapp.feature.nutrition.domain.topFoodsByMacro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel des Stats nutrition (A6). Lecture seule : source de vérité = Room
 * (réactif) → le graphe et le top aliments se recalculent à chaque écriture du
 * journal. Un seul macro affiché à la fois (sélecteur, mémoire
 * `nutrition-android-nav-mode`) — pas la grille tout-en-un du web. Période + type
 * de graphe (barres/courbe) propres à cet écran (pas le range partagé des Stats
 * sport, pour ne pas coupler les 2 surfaces).
 */
@HiltViewModel
class NutritionStatsViewModel @Inject constructor(
    mealDao: MealDao,
    mealEntryDao: MealEntryDao,
    nutritionGoalDao: NutritionGoalDao,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    private val meals = mealDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val entries = mealEntryDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val goals = nutritionGoalDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }

    // ─── Sélections UI (partagées entre toutes les sous-sections / pages du pager) ──
    private val _range = MutableStateFlow<StatsRange>(StatsRange.Last30Days)
    val range: StateFlow<StatsRange> = _range

    private val _chartType = MutableStateFlow(ChartType.BAR)
    val chartType: StateFlow<ChartType> = _chartType

    /**
     * Données par macro pour TOUTES les sous-sections à la fois (une carte par MacroKey, dans l'ordre
     * de l'enum) : graphe (buckets + consommé + cible + granularité) + top aliments. Précalculé pour
     * que chaque page du pager lise sa propre donnée → swipe fluide, sans glitch inter-page.
     */
    val macroCards: StateFlow<List<MacroCardData>> =
        combine(meals, entries, goals, _range) { m, e, g, range ->
            val (start, end) = bounds(range, m)
            val gran = granularityFor(start, end)
            MacroKey.entries.map { key ->
                MacroCardData(
                    macro = key,
                    chart = MacroChartData(
                        series = aggregateMacroSeries(e, m, g, start, end, gran, key),
                        granularity = gran,
                    ),
                    // Top 5 aliments max par macro (retour review) — pas plus de 5 rows par section.
                    topFoods = topFoodsByMacro(e, m, start, end, key).take(5),
                )
            }
        }.stateIn(viewModelScope, started, emptyList())

    /**
     * Graphe « Toutes les macros » : pour chaque macro, % du consommé vs l'objectif
     * par bucket (comparable entre macros). Buckets partagés (mêmes bornes/granularité).
     */
    val allChartData: StateFlow<AllMacrosChartData> =
        combine(meals, entries, goals, _range) { m, e, g, range ->
            val (start, end) = bounds(range, m)
            val gran = granularityFor(start, end)
            val series = MacroKey.entries.map { key ->
                key to aggregateMacroSeries(e, m, g, start, end, gran, key)
            }
            val buckets = series.firstOrNull()?.second?.buckets ?: emptyList()
            val lines = series.map { (key, s) ->
                val pct = s.consumed.indices.map { i ->
                    val t = s.target.getOrElse(i) { 0f }
                    if (t > 0f) s.consumed[i] / t * 100f else 0f
                }
                MacroLine(key, pct)
            }
            AllMacrosChartData(buckets, gran, lines)
        }.stateIn(viewModelScope, started, AllMacrosChartData.EMPTY)

    /** Mode « All » : le top aliment (n°1) de CHAQUE macro — une ligne par catégorie. */
    val topFoodPerMacro: StateFlow<List<MacroTopFood>> =
        combine(meals, entries, _range) { m, e, range ->
            val (start, end) = bounds(range, m)
            MacroKey.entries.mapNotNull { key ->
                topFoodsByMacro(e, m, start, end, key).firstOrNull()?.let { MacroTopFood(key, it) }
            }
        }.stateIn(viewModelScope, started, emptyList())

    fun setRange(range: StatsRange) { _range.value = range }
    fun setChartType(type: ChartType) { _chartType.value = type }

    /**
     * Bornes de la période. « Tout » est clampé à la date du repas le plus ancien
     * (évite des centaines de buckets vides depuis l'an 2000) ; sinon les bornes du
     * range tel quel.
     */
    private fun bounds(range: StatsRange, meals: List<com.example.sportapp.core.data.model.Meal>): Pair<String, String> {
        val (rawStart, end) = range.computeBounds(LocalDate.now())
        val start = if (range is StatsRange.All) (earliestMealDate(meals) ?: end) else rawStart
        return start to end
    }
}

/** Données du graphe d'un macro pour l'UI (série + granularité dérivée des bornes). */
data class MacroChartData(
    val series: MacroSeries,
    val granularity: ChartGranularity,
) {
    companion object {
        val EMPTY = MacroChartData(
            series = MacroSeries(emptyList(), emptyList(), emptyList()),
            granularity = ChartGranularity.WEEKLY,
        )
    }
}

/** Données d'une sous-section macro (graphe + top aliments), une par MacroKey (ordre de l'enum). */
data class MacroCardData(
    val macro: MacroKey,
    val chart: MacroChartData,
    val topFoods: List<TopFood>,
)

/** Une macro du graphe « Toutes les macros » : sa clé + ses valeurs en % par bucket. */
data class MacroLine(val macro: MacroKey, val percents: List<Float>)

/** Top aliment (n°1) d'une macro — une ligne du récap « par catégorie » du mode All. */
data class MacroTopFood(val macro: MacroKey, val food: TopFood)

/** Données du graphe « Toutes les macros » (buckets partagés + une ligne % par macro). */
data class AllMacrosChartData(
    val buckets: List<String>,
    val granularity: ChartGranularity,
    val lines: List<MacroLine>,
) {
    companion object {
        val EMPTY = AllMacrosChartData(emptyList(), ChartGranularity.WEEKLY, emptyList())
    }
}
