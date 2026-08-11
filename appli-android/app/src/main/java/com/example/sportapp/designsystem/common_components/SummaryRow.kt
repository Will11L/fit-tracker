package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Données d'une cellule de [SummaryRow]. */
data class SummaryItemData(
    val icon: Int,
    val value: String,
    val label: String,
    val iconTint: Color,
)

/**
 * Rangée de cellules de résumé réparties à poids égal (pleine largeur, gap 8dp).
 * Canonique partagé — remplace la structure `Row` dupliquée de `SessionSummaryRow`,
 * `PlannedWorkoutSummaryRow` et `CalendarSummaryRow` (R10, ↔ organism O4 SummaryStatsRow).
 * [compact] passe toutes les cellules de la rangée en variante compacte.
 */
@Composable
fun SummaryRow(
    items: List<SummaryItemData>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            SummaryItem(
                icon = item.icon,
                value = item.value,
                label = item.label,
                iconTint = item.iconTint,
                compact = compact,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
