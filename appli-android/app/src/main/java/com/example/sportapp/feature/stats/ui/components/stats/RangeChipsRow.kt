package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.core.stats.StatsRange

/**
 * Composant commun aux 3 ecrans Stats : ligne de chips raccourcis + bouton
 * Custom qui ouvre [CustomRangePickerDialog]. Style aligne CalendarViewScreen.
 */
@Composable
fun RangeChipsRow(
    range: StatsRange,
    onSelect: (StatsRange) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hauteur 32dp explicite : confine le tap target M3 (default 48dp pour
    // accessibility) au visuel reel du chip -> ecart vertical entre 2 rows
    // de chips suit fidelement le Spacer parent (sinon +~16dp invisibles).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RangeChip(stringResource(R.string.stats_range_1_week), range == StatsRange.Last7Days) { onSelect(StatsRange.Last7Days) }
        RangeChip(stringResource(R.string.stats_range_30_days), range == StatsRange.Last30Days) { onSelect(StatsRange.Last30Days) }
        RangeChip(stringResource(R.string.stats_range_3_months), range == StatsRange.Last3Months) { onSelect(StatsRange.Last3Months) }
        RangeChip(stringResource(R.string.stats_range_6_months), range == StatsRange.Last6Months) { onSelect(StatsRange.Last6Months) }
        RangeChip(stringResource(R.string.stats_range_1_year), range == StatsRange.LastYear) { onSelect(StatsRange.LastYear) }
        RangeChip(stringResource(R.string.stats_range_all), range == StatsRange.All) { onSelect(StatsRange.All) }
        RangeChip(stringResource(R.string.stats_range_custom), range is StatsRange.Custom, onCustomClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = lightGrayBlue,
            selectedContainerColor = appColors.primaryAction,
            selectedLabelColor = appColors.textPrimary,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) appColors.primaryAction else appColors.textSecondary.copy(alpha = 0.6f),
        ),
    )
}
