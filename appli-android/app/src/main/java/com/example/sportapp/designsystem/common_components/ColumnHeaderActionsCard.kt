package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.sync.base.SortDir
import com.example.sportapp.designsystem.theme.appColors

/**
 * Molecule : popup d'actions sur le header d'une colonne data grid.
 *
 * Card avec :
 *   - Titre du nom de colonne (sqlColumn)
 *   - Row de 3 `ActionIconButton` : ↑ ASC, ↓ DESC, ↺ Clear
 *   - `StyledSearchField` pour filtrer par cette colonne
 *
 * Utilisé par `SyncTableDetailScreen` dans le DropdownMenu sur tap header.
 * Cf. T-sync-grid (2026-05-26) + sync Figma page `3 · Molecules`.
 */
@Composable
fun ColumnHeaderActionsCard(
    columnName: String,
    sortDir: SortDir,
    filterValue: String,
    onSetSort: (SortDir) -> Unit,
    onFilterChange: (String) -> Unit,
    filterPlaceholder: String,
    modifier: Modifier = Modifier,
) {
    // ⚠️ Pas de `remember(filterValue)` : sinon chaque frappe reconstruit le TextFieldValue
    // → cursor reset à 0 → texte tapé à l'envers (bug "hsup" pour "push"). On synchronise
    // depuis l'extérieur seulement quand filterValue diffère vraiment du texte tapé.
    var filterFieldValue by remember { mutableStateOf(TextFieldValue(filterValue)) }
    LaunchedEffect(filterValue) {
        if (filterValue != filterFieldValue.text) {
            filterFieldValue = TextFieldValue(filterValue, TextRange(filterValue.length))
        }
    }

    Column(
        modifier = modifier
            .width(260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(appColors.bgScreen)
            .padding(12.dp),
    ) {
        TitledDivider(title = columnName)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconButton(
                iconRes = R.drawable.ic_arrow_upward_alt,
                onClick = { onSetSort(SortDir.ASC) },
                customBackgroundColor = if (sortDir == SortDir.ASC) appColors.primaryAction else appColors.bgRecessed,
                tint = if (sortDir == SortDir.ASC) appColors.textPrimary else appColors.textSecondary,
            )
            ActionIconButton(
                iconRes = R.drawable.ic_arrow_downward_alt,
                onClick = { onSetSort(SortDir.DESC) },
                customBackgroundColor = if (sortDir == SortDir.DESC) appColors.primaryAction else appColors.bgRecessed,
                tint = if (sortDir == SortDir.DESC) appColors.textPrimary else appColors.textSecondary,
            )
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_refresh,
                onClick = { onSetSort(SortDir.NONE) },
                clickable = sortDir != SortDir.NONE,
                customBackgroundColor = appColors.bgRecessed,
                tint = if (sortDir != SortDir.NONE) appColors.textPrimary else appColors.textTertiary,
            )
        }

        Spacer(Modifier.height(10.dp))

        StyledSearchField(
            value = filterFieldValue,
            onValueChange = {
                filterFieldValue = it
                onFilterChange(it.text)
            },
            placeholderText = filterPlaceholder,
        )
    }
}
