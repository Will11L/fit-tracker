package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle
import com.example.sportapp.core.stats.StatsSortMode

/**
 * Segmented toggle entre tri alphabetique et tri par zone (par couleur),
 * applique aux 4 sections du chart Stats (Zone / Group / Muscle / Exercise).
 * User feedback 2026-05-09 : switch entre vue 'pure alpha' et 'groupee par couleur'.
 */
@Composable
fun SortToggle(
    current: StatsSortMode,
    onSelect: (StatsSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedIconToggle(
        items = listOf(
            SegmentItem(StatsSortMode.ALPHA, Icons.Filled.SortByAlpha, stringResource(R.string.stats_a11y_sort_alpha)),
            SegmentItem(StatsSortMode.ZONE, Icons.Filled.Palette, stringResource(R.string.stats_a11y_sort_zone)),
        ),
        selected = current,
        onSelect = onSelect,
        modifier = modifier,
    )
}
