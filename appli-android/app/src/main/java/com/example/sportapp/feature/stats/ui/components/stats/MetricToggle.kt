package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle
import com.example.sportapp.core.stats.MetricType

/**
 * Segmented toggle entre les 3 metriques du chart 'Volume by muscle group' :
 * SETS (Repeat) / EXERCISES (FormatListNumbered) / TOTAL_WEIGHT (FitnessCenter).
 * Place a droite du ChartTypeToggle. Ordre user 2026-05-07 : Sets, Exercises, Volume.
 */
@Composable
fun MetricToggle(
    current: MetricType,
    onSelect: (MetricType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedIconToggle(
        items = listOf(
            SegmentItem(MetricType.SETS, Icons.Filled.Repeat, stringResource(R.string.stats_a11y_sets_count)),
            SegmentItem(MetricType.EXERCISES, Icons.Filled.FormatListNumbered, stringResource(R.string.stats_a11y_exercises_count)),
            SegmentItem(MetricType.TOTAL_WEIGHT, Icons.Filled.FitnessCenter, stringResource(R.string.stats_a11y_total_weight)),
        ),
        selected = current,
        onSelect = onSelect,
        modifier = modifier,
    )
}
