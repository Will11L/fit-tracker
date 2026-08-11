package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.goals.viewmodel.GoalsSortMode

/**
 * Segmented toggle 5 modes pour la page MuscleGoals (refonte 2026-05-09),
 * applique simultanement a la liste (cards/rows) et au chart footer :
 * ALPHA / PALETTE (tri par zone) / PERCENT_DESC / PERCENT_ASC / PRIORITY.
 */
@Composable
fun GoalsSortToggle(
    current: GoalsSortMode,
    onSelect: (GoalsSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedIconToggle(
        items = listOf(
            SegmentItem(GoalsSortMode.ALPHA, Icons.Filled.SortByAlpha, "Sort alphabetically"),
            SegmentItem(GoalsSortMode.PALETTE, Icons.Filled.Palette, "Sort by zone color"),
            SegmentItem(GoalsSortMode.PERCENT_DESC, Icons.AutoMirrored.Filled.TrendingDown, "Sort by % achievement (high first)"),
            SegmentItem(GoalsSortMode.PERCENT_ASC, Icons.AutoMirrored.Filled.TrendingUp, "Sort by % achievement (low first)"),
            SegmentItem(GoalsSortMode.PRIORITY, Icons.Filled.PriorityHigh, "Sort by priority"),
        ),
        selected = current,
        onSelect = onSelect,
        modifier = modifier,
        width = 36.dp,
        iconSize = 16.dp,
        unselectedBorderColor = appColors.divider.copy(alpha = 0.6f),
    )
}
