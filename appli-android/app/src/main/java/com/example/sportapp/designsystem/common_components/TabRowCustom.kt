package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.*

@Composable
fun TabRowCustom(
    items: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    height: Dp = 44.dp,
    isSubRow: Boolean = false,
    // Couleur du remplissage de l'onglet ACTIF, par index (additif, rétrocompatible). null (défaut) =
    // selectedFill (comportement historique) ; sinon un code couleur par section (macros) — la liste
    // est résolue par l'appelant en contexte @Composable (macroColor / appColors).
    selectedColors: List<Color>? = null,
    // Taille de police (additif) : Unspecified = défaut du thème.
    fontSize: TextUnit = TextUnit.Unspecified,
    // Scrollable (additif) : onglets à leur largeur NATURELLE dans une barre défilable horizontalement
    // (peuvent déborder hors écran) + auto-scroll vers l'actif. Défaut false = équi-répartis (weight),
    // comportement historique — les callsites Séance/Objectifs/Programme restent inchangés.
    scrollable: Boolean = false,
) {
    if (scrollable) {
        val listState = rememberLazyListState()
        // La barre suit le pager : ramène l'onglet actif à l'écran à chaque changement (tap ou swipe).
        LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.bgBottomNav),
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(items) { index, label ->
                TabCell(
                    modifier = Modifier,
                    height = height,
                    label = label,
                    isSelected = index == selectedIndex,
                    activeColor = selectedColors?.getOrNull(index) ?: appColors.selectedFill,
                    isSubRow = isSubRow,
                    fontSize = fontSize,
                    naturalWidth = true,
                    onClick = { onTabSelected(index) },
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.bgBottomNav),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, label ->
                TabCell(
                    modifier = Modifier.weight(1f),
                    height = height,
                    label = label,
                    isSelected = index == selectedIndex,
                    activeColor = selectedColors?.getOrNull(index) ?: appColors.selectedFill,
                    isSubRow = isSubRow,
                    fontSize = fontSize,
                    naturalWidth = false,
                    onClick = { onTabSelected(index) },
                )
            }
        }
    }
}

/** Une cellule d'onglet : fond selon état (actif à la couleur de section), texte, clic. [naturalWidth]
 *  = padding horizontal (barre scrollable) vs largeur imposée par le parent (weight, équi-réparti). */
@Composable
private fun TabCell(
    modifier: Modifier,
    height: Dp,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    isSubRow: Boolean,
    fontSize: TextUnit,
    naturalWidth: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(
                color = when {
                    isSelected && !isSubRow -> activeColor
                    isSelected && isSubRow -> activeColor.copy(alpha = 0.75f)
                    !isSelected && !isSubRow -> appColors.bgBottomNav
                    else -> appColors.bgBottomNav.copy(alpha = 0.5f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (naturalWidth) 20.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) appColors.textOnSelected else appColors.textTertiary.copy(alpha = if (isSubRow) 0.8f else 1f),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
