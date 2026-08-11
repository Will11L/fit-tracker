package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Radar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle

/** Vue du résumé du jour Nutrition : barres, anneaux (donut) ou radar. UPPER_CASE (politique 11). */
enum class SummaryView { BARS, RINGS, RADAR }

/**
 * Toggle segmenté de la vue du résumé du jour (barres / anneaux / radar), partagé
 * par les sections macros et micros. Mirror de `ChartTypeToggle` des Stats — un
 * `SegmentedIconToggle` icône-seule (les descriptions servent de libellé a11y,
 * politique 18). Compact (34 dp) pour tenir à droite d'un en-tête de section.
 */
@Composable
fun SummaryViewToggle(
    current: SummaryView,
    onSelect: (SummaryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedIconToggle(
        // Ordre anneaux / barres / radar (parité web viewSegments, demande user 2026-07-14).
        items = listOf(
            SegmentItem(SummaryView.RINGS, Icons.Filled.DonutLarge, stringResource(R.string.nutrition_summary_view_rings)),
            SegmentItem(SummaryView.BARS, Icons.Filled.BarChart, stringResource(R.string.nutrition_summary_view_bars)),
            SegmentItem(SummaryView.RADAR, Icons.Filled.Radar, stringResource(R.string.nutrition_summary_view_radar)),
        ),
        selected = current,
        onSelect = onSelect,
        modifier = modifier,
        width = 34.dp,
        iconSize = 16.dp,
    )
}
