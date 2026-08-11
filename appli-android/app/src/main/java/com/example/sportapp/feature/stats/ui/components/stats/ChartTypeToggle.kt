package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle
import com.example.sportapp.core.stats.ChartType

/**
 * Segmented toggle entre LINE chart et BAR chart pour 'Volume by muscle
 * group'. Compact (pas de fillMaxWidth) — destine a etre place dans une
 * Row a cote d'autres toggles. User feedback 2026-05-07 : a gauche, vs
 * MetricToggle a droite. Ordre : Bar a gauche, Line a droite.
 */
@Composable
fun ChartTypeToggle(
    current: ChartType,
    onSelect: (ChartType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedIconToggle(
        items = listOf(
            SegmentItem(ChartType.BAR, Icons.Filled.BarChart, stringResource(R.string.stats_a11y_bar_chart)),
            SegmentItem(ChartType.LINE, Icons.AutoMirrored.Filled.ShowChart, stringResource(R.string.stats_a11y_line_chart)),
        ),
        selected = current,
        onSelect = onSelect,
        modifier = modifier,
    )
}
