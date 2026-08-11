package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.feature.nutrition.ui.cleanServingLabel
import kotlin.math.roundToInt

/**
 * Dialog de saisie de quantité (grammes) pour ajouter/éditer une entry. En ajout,
 * un résumé visuel per-100 g ([FoodMacroSummary] : toggle Barres/Radar, macros +
 * micros — même rendu que l'écran Détail aliment) rappelle la valeur de l'aliment
 * ([food] non-null), puis les portions nommées en chips ; un tap pré-remplit la
 * quantité + fige le label. Une saisie manuelle efface le label.
 */
@Composable
fun EntryQuantityDialog(
    title: String,
    portions: List<FoodPortion>,
    initialQuantity: Float,
    onConfirm: (quantityG: Float, portionLabel: String?) -> Unit,
    onDismiss: () -> Unit,
    food: Food? = null,
) {
    var qtyText by remember { mutableStateOf(if (initialQuantity > 0f) trimNumber(initialQuantity) else "100") }
    var portionLabel by remember { mutableStateOf<String?>(null) }

    val parsed = qtyText.replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed > 0f

    FormDialog(
        title = title,
        confirmText = stringResource(R.string.common_save),
        onConfirm = { if (parsed != null && parsed > 0f) onConfirm(parsed, portionLabel) },
        onDismiss = onDismiss,
        confirmEnabled = valid,
        disabledReason = stringResource(R.string.nutrition_qty_invalid),
        scrollable = food != null, // le résumé macros/micros peut dépasser la hauteur du dialog
    ) {
        food?.let { FoodMacroSummary(it) }
        val chips = quantityChips(portions, food?.isWater == true)
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { chip ->
                    PortionChip(
                        label = chip.label,
                        onClick = {
                            qtyText = trimNumber(chip.grams)
                            portionLabel = chip.snapshot
                        },
                    )
                }
            }
        }
        CustomTextField(
            value = qtyText,
            onValueChange = {
                qtyText = it
                portionLabel = null
            },
            label = stringResource(R.string.nutrition_qty_grams),
            placeholder = "100",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun PortionChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = appColors.textOnSelected,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(blueMedium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun trimNumber(v: Float): String =
    if (v % 1f == 0f) v.roundToInt().toString() else v.toString()

/** Grammes → volume pur pour les boissons eau (1 g = 1 ml) : « 1 L » si litres entiers, sinon « N mL ». */
private fun formatVolume(grams: Float): String {
    val ml = grams.roundToInt()
    return if (ml % 1000 == 0) "${ml / 1000} L" else "$ml mL"
}

/** Un chip de quantité : quantité en g, libellé affiché, et libellé snapshot posé sur l'entry. */
private data class QtyChipSpec(val grams: Float, val label: String, val snapshot: String)

/** Volumes standards des chips eau (ml = g, 1 g = 1 ml). */
private val STANDARD_WATER_ML = listOf(250, 500, 1000)

/**
 * Chips de quantité du dialog. Pour un aliment eau : toujours les volumes standards
 * (250 mL · 500 mL · 1 L) en libellés volume purs, PLUS les portions propres de
 * l'aliment non déjà couvertes (dédup par grammage) — pas besoin de FoodPortions
 * persistées. Pour les autres aliments : les portions nommées avec « (X g) ».
 */
private fun quantityChips(portions: List<FoodPortion>, isWater: Boolean): List<QtyChipSpec> {
    if (!isWater) {
        return portions.map { p ->
            val cleanName = cleanServingLabel(p.label)
            val grams = "${p.grams.roundToInt()} g"
            QtyChipSpec(p.grams, if (cleanName != null) "$cleanName ($grams)" else grams, cleanName ?: p.label)
        }
    }
    val standard = STANDARD_WATER_ML.map { ml ->
        QtyChipSpec(ml.toFloat(), formatVolume(ml.toFloat()), formatVolume(ml.toFloat()))
    }
    val extras = portions
        .filter { it.grams.roundToInt() !in STANDARD_WATER_ML }
        .map { p ->
            val cleanName = cleanServingLabel(p.label)
            QtyChipSpec(p.grams, cleanName ?: formatVolume(p.grams), cleanName ?: p.label)
        }
    return standard + extras
}
