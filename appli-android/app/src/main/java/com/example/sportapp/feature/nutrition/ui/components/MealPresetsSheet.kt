package com.example.sportapp.feature.nutrition.ui.components

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.redMedium

/**
 * Gestion des REPAS récurrents (`meal_presets`) — miroir allégé de la sheet web :
 * renommer, réordonner (↑/↓), ajouter, supprimer (les repas déjà journalisés ne
 * sont pas touchés). Les collations, elles, se créent directement depuis le
 * journal (« Ajouter une collation »).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPresetsSheet(
    presets: List<MealPreset>,
    canDuplicate: Boolean,
    onAdd: (String) -> Unit,
    onRename: (MealPreset, String) -> Unit,
    onDelete: (MealPreset) -> Unit,
    onMove: (MealPreset, Int) -> Unit,
    onDuplicateRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MealPreset?>(null) }
    var deleteTarget by remember { mutableStateOf<MealPreset?>(null) }
    var nameDraft by remember { mutableStateOf("") }

    AppBottomSheet(onDismissRequest = onDismiss) {
        // Le padding horizontal ne s'applique qu'au contenu : la row d'actions en
        // sort (pleine largeur) pour que SpaceEvenly donne des espaces de bord
        // égaux à l'espace central (sinon le padding de la sheet s'y ajoute).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            TitledDivider(title = stringResource(R.string.nutrition_presets_header))
            if (presets.isEmpty()) {
                EmptyListRow(text = stringResource(R.string.nutrition_no_presets))
            }
            presets.forEachIndexed { index, preset ->
                // Row au style OptionRow (sheets d'options du Programme) : fond
                // bgRecessed, padding 12/10, boutons aux défauts 40/24.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors.bgRecessed)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = preset.name,
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    preset.defaultTime?.let {
                        Text(text = it, color = appColors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(2.dp))
                    }
                    ActionIconButton(
                        iconRes = R.drawable.ic_arrow_upward_alt,
                        clickable = index > 0,
                        onClick = { onMove(preset, -1) },
                    )
                    ActionIconButton(
                        iconRes = R.drawable.ic_arrow_downward_alt,
                        clickable = index < presets.lastIndex,
                        onClick = { onMove(preset, +1) },
                    )
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_edit,
                        customBackgroundColor = blueMedium,
                        onClick = {
                            nameDraft = preset.name
                            renameTarget = preset
                        },
                    )
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_delete_forever,
                        customBackgroundColor = redMedium,
                        onClick = { deleteTarget = preset },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Actions de la sheet : dupliquer un repas passé (ferme et ouvre la
            // sheet de duplication, comme le web) + ajouter un repas récurrent.
            TitledDivider(title = stringResource(R.string.nutrition_section_actions))
            } // fin du contenu paddé — la row d'actions est pleine largeur.
            // Espace égal aux bords et entre les deux boutons (miroir web).
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_rounded_content_copy,
                    text = stringResource(R.string.nutrition_duplicate_meal),
                    iconSize = 20.dp,
                    backgroundColor = firstBlue,
                    clickable = canDuplicate,
                    onClick = onDuplicateRequest,
                )
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_add,
                    text = stringResource(R.string.nutrition_add_meal),
                    iconSize = 20.dp,
                    backgroundColor = appColors.primaryAction,
                    onClick = {
                        nameDraft = ""
                        showAdd = true
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAdd) {
        FormDialog(
            title = stringResource(R.string.nutrition_add_meal),
            confirmText = stringResource(R.string.common_add),
            onConfirm = {
                if (nameDraft.isNotBlank()) {
                    onAdd(nameDraft)
                    showAdd = false
                }
            },
            onDismiss = { showAdd = false },
            confirmEnabled = nameDraft.isNotBlank(),
            disabledReason = stringResource(R.string.nutrition_meal_name_required),
        ) {
            CustomTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = stringResource(R.string.nutrition_meal_name),
                placeholder = stringResource(R.string.nutrition_meal_name_hint),
            )
        }
    }

    renameTarget?.let { preset ->
        FormDialog(
            title = stringResource(R.string.nutrition_rename_meal),
            confirmText = stringResource(R.string.common_save),
            onConfirm = {
                if (nameDraft.isNotBlank()) {
                    onRename(preset, nameDraft)
                    renameTarget = null
                }
            },
            onDismiss = { renameTarget = null },
            confirmEnabled = nameDraft.isNotBlank(),
            disabledReason = stringResource(R.string.nutrition_meal_name_required),
        ) {
            CustomTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = stringResource(R.string.nutrition_meal_name),
                placeholder = stringResource(R.string.nutrition_meal_name_hint),
            )
        }
    }

    deleteTarget?.let { preset ->
        ConfirmationDialog(
            title = stringResource(R.string.nutrition_delete_preset_title),
            message = stringResource(R.string.nutrition_delete_preset_message, preset.name),
            onConfirm = {
                onDelete(preset)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
