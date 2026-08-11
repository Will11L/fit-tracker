package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors

/**
 * Filtre multi-select des groupes muscle pour le chart 'Volume by muscle group'.
 * Une seule ligne horizontalement scrollable (demande user 2026-05-07 : tient
 * en 1 ligne sans manger de hauteur). Couleur du fond = couleur de la courbe
 * quand selectionne ; transparent quand decoche (juste la bordure dans la
 * couleur du groupe). FilterChip M3.
 *
 * Ordre des chips = ordre de [groups] (le ViewModel passe les groupes
 * disponibles dans la donnee, dans l'ordre canonique MuscleGroups.ALL).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFilterChips(
    groups: List<String>,
    selectedGroups: Set<String>,
    colorMap: Map<String, Color>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hauteur 32dp explicite : confine le tap target M3 (default 48dp pour
    // accessibility) au visuel reel du chip — sinon +~16dp invisibles
    // entre les 2 rows.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEach { group ->
            val color = colorMap[group] ?: appColors.textTertiary
            val selected = group in selectedGroups
            FilterChip(
                selected = selected,
                onClick = { onToggle(group) },
                label = { Text(group) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = color,
                    selectedContainerColor = color,
                    selectedLabelColor = appColors.textPrimary,
                ),
                border = BorderStroke(width = 1.dp, color = color),
            )
        }
    }
}
