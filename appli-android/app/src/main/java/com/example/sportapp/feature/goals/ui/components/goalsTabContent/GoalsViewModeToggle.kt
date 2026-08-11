package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.goals.viewmodel.GoalsViewMode

/**
 * Segmented toggle entre les 3 niveaux d'affichage de la page Goals
 * (refonte 2026-05-09) : MUSCLE (liste flat) / GROUP (cards par
 * muscle_group) / ZONE (cards par zone). Affecte simultanement la liste
 * et le chart footer.
 */
@Composable
fun GoalsViewModeToggle(
    current: GoalsViewMode,
    onSelect: (GoalsViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedIconToggle(
        items = listOf(
            SegmentItem(GoalsViewMode.MUSCLE, Icons.AutoMirrored.Filled.ViewList, "View by muscle (flat list)"),
            SegmentItem(GoalsViewMode.GROUP, Icons.Filled.Layers, "View by muscle group"),
            SegmentItem(GoalsViewMode.ZONE, Icons.Filled.GridView, "View by zone"),
        ),
        selected = current,
        onSelect = onSelect,
        modifier = modifier,
        width = 36.dp,
        iconSize = 16.dp,
        unselectedBorderColor = appColors.divider.copy(alpha = 0.6f),
    )
}
