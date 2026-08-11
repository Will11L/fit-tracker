package com.example.sportapp.feature.nutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.ui.components.FoodEditorSheet
import com.example.sportapp.feature.nutrition.ui.components.FoodMacroSummary
import com.example.sportapp.feature.nutrition.ui.components.PortionsEditor

/**
 * Détail d'un aliment du catalogue (pendant Android du `food-detail-panel` web) : résumé
 * visuel macros (anneau / radar, per-100 g) + micros colorés + portions nommées (CRUD via
 * l'éditeur réutilisé) + actions (modifier les CUSTOM / archiver-restaurer / supprimer).
 * Réutilise le `FoodCatalogueViewModel` (mêmes sources Room) ; l'aliment est observé par uuid
 * indépendamment des filtres de la liste, donc reste affichable même archivé.
 */
@Composable
fun FoodDetailScreen(
    foodUuid: String,
    navController: NavController,
    viewModel: FoodCatalogueViewModel = hiltViewModel(),
) {
    // remember(foodUuid) : sans ça, foodFlow() recrée un StateFlow à chaque recomposition (initial
    // null → "aucun aliment" → ré-émet l'aliment → recompose) = clignotement en boucle.
    val food by remember(foodUuid) { viewModel.foodFlow(foodUuid) }.collectAsStateWithLifecycle()
    val portionsByFood by viewModel.portionsByFood.collectAsStateWithLifecycle()

    var editorOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {
        // En-tête : flèche retour + nom de l'aliment centré.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(44.dp).background(appColors.bgSurface),
        ) {
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
                hasBackground = false,
                onClick = { navController.popBackStack() },
            )
            Text(
                text = food?.name ?: "",
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(40.dp)) // équilibre la flèche → titre vraiment centré
        }

        // null = aliment pas encore chargé (ou supprimé → popBackStack ci-dessous) : on n'affiche
        // que l'en-tête, jamais de message « introuvable » qui flasherait au chargement.
        val current = food
        if (current != null) {
            val portions = portionsByFood[current.uuid] ?: emptyList()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── Badge catégorie + marque/source + actions ──────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.foodGroup?.takeIf { it.isNotBlank() }?.let { FoodGroupBadge(it) }
                    val sub = listOfNotNull(current.brand?.takeIf { it.isNotBlank() }, current.source).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = appColors.textTertiary, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Modifier : seulement pour les aliments perso (un OFF/CIQUAL garde ses valeurs source).
                    if (current.source == "CUSTOM") {
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_edit,
                            text = stringResource(R.string.nutrition_catalog_action_edit),
                            iconSize = 18.dp,
                            backgroundColor = blueMedium,
                            onClick = { editorOpen = true },
                        )
                    }
                    ActionIconWithTextButton(
                        iconRes = R.drawable.ic_rounded_folder_eye,
                        text = stringResource(
                            if (current.archived) R.string.nutrition_catalog_action_unarchive
                            else R.string.nutrition_catalog_action_archive
                        ),
                        iconSize = 18.dp,
                        onClick = { viewModel.setArchived(current, !current.archived) },
                    )
                    ActionIconWithTextButton(
                        iconRes = R.drawable.ic_rounded_delete_forever,
                        text = stringResource(R.string.common_delete),
                        iconSize = 18.dp,
                        backgroundColor = redMedium,
                        onClick = { confirmDelete = true },
                    )
                }

                // ── Résumé macros + micros (per-100 g) : barres ou radar ───────────
                // Composant partagé avec le dialog d'ajout du journal (parité).
                FoodMacroSummary(current)

                // ── Portions nommées (réutilise l'éditeur de portions) ─────────────
                TitledDivider(title = stringResource(R.string.nutrition_food_portions))
                PortionsEditor(
                    portions = portions,
                    onAdd = { label, grams -> viewModel.addPortion(current.uuid, label, grams) },
                    onDelete = { viewModel.deletePortion(it) },
                )

                Spacer(Modifier.height(8.dp))
            }

            if (editorOpen) {
                FoodEditorSheet(
                    food = current,
                    portions = portions,
                    onSave = { r ->
                        viewModel.updateFood(current, r.name, r.brand, r.kcalPer100g, r.proteinPer100g, r.carbsPer100g, r.fatPer100g, r.fiberPer100g, r.isWater)
                        editorOpen = false
                    },
                    onAddPortion = { label, grams -> viewModel.addPortion(current.uuid, label, grams) },
                    onDeletePortion = { viewModel.deletePortion(it) },
                    onDismiss = { editorOpen = false },
                )
            }

            if (confirmDelete) {
                ConfirmationDialog(
                    title = stringResource(R.string.nutrition_catalog_delete_title),
                    message = stringResource(R.string.nutrition_catalog_delete_message, current.name),
                    onConfirm = {
                        viewModel.deleteFood(current)
                        confirmDelete = false
                        navController.popBackStack()
                    },
                    onDismiss = { confirmDelete = false },
                )
            }
        }
    }
}

