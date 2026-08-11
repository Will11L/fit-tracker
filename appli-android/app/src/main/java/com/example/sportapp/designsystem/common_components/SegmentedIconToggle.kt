package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors

/** Un segment de [SegmentedIconToggle] : la valeur enum, son icône, sa description a11y. */
data class SegmentItem<T>(
    val value: T,
    val icon: ImageVector,
    val description: String,
)

/**
 * Toggle segmenté générique : une rangée de [SegmentedIconButton], un seul
 * segment sélectionné. Canonique partagé — remplace la structure `Row`
 * dupliquée des 5 toggles `ChartTypeToggle`, `MetricToggle`, `SortToggle`,
 * `GoalsViewModeToggle`, `GoalsSortToggle` (R11). Défauts = variante stats ;
 * les toggles Goals passent leur [width]/[iconSize]/[unselectedBorderColor].
 */
@Composable
fun <T> SegmentedIconToggle(
    items: List<SegmentItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    iconSize: Dp = 18.dp,
    unselectedBorderColor: Color = appColors.textSecondary.copy(alpha = 0.6f),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            SegmentedIconButton(
                selected = item.value == selected,
                onClick = { onSelect(item.value) },
                icon = item.icon,
                description = item.description,
                width = width,
                iconSize = iconSize,
                unselectedBorderColor = unselectedBorderColor,
            )
        }
    }
}
