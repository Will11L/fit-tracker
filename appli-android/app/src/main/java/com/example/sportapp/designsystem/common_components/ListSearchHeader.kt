package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.yellowMedium

/**
 * En-tête d'écran-liste : champ de recherche + sync + tri (dropdown) + more,
 * puis une ligne « N résultats · tri ». Canonique partagé — remplace les
 * ex-doublons `ExerciseListHeader` et `MuscleListHeader` (R7). Les écrans
 * passent leur propre [searchPlaceholder] et [resultsCountText] (null = ligne masquée).
 */
@Composable
fun ListSearchHeader(
    searchQuery: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    searchPlaceholder: String,
    resultsCountText: String? = null,
    allSynced: Boolean,
    onSyncClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSortChange: (String) -> Unit,
) {
    val sortOptions = listOf(
        stringResource(R.string.exercise_list_sort_asc),
        stringResource(R.string.exercise_list_sort_desc),
    )
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 🔍 Search bar
            StyledSearchField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholderText = searchPlaceholder,
                modifier = Modifier.weight(1f)
            )

            // Sync Button
            ActionIconButton(
                iconSize = 28.dp,
                iconRes = if (allSynced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                clickable = !allSynced,
                onClick = onSyncClick,
                hasBackground = false,
                tint = if (allSynced) appColors.primaryAction else yellowMedium
            )

            // Sort dropdown
            Box {
                ActionIconButton(
                    iconRes = R.drawable.ic_rounded_sort,
                    onClick = { showSortMenu = true },
                    tint = appColors.textPrimary
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier
                        .background(appColors.bgRecessed)
                        .clip(MaterialTheme.shapes.small)
                ) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = appColors.textPrimary
                                )
                            },
                            onClick = {
                                onSortChange(option)
                                showSortMenu = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(appColors.bgRecessed)
                        )
                    }
                }
            }

            // More button
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_more_vert,
                onClick = onMoreClick,
                tint = appColors.textPrimary
            )
        }

        // 🔢 Result count + current sort (masqué si null — gain de place)
        if (resultsCountText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = resultsCountText,
                color = appColors.textTertiary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
