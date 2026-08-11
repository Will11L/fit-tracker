package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.SingleSelectDropdown
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.domain.NutrientKey
import com.example.sportapp.feature.nutrition.domain.NutrientThreshold
import com.example.sportapp.feature.nutrition.domain.ThresholdOp
import kotlin.math.roundToInt

/** Label localisé d'un nutriment filtrable (réutilise les strings macro/micro du Journal). */
@Composable
fun nutrientLabel(key: NutrientKey): String = stringResource(
    when (key) {
        NutrientKey.KCAL -> R.string.nutrition_macro_calories
        NutrientKey.PROTEIN -> R.string.nutrition_macro_protein
        NutrientKey.CARBS -> R.string.nutrition_macro_carbs
        NutrientKey.FAT -> R.string.nutrition_macro_fat
        NutrientKey.FIBER -> R.string.nutrition_macro_fiber
        NutrientKey.IRON -> R.string.nutrition_micro_iron
        NutrientKey.CALCIUM -> R.string.nutrition_micro_calcium
        NutrientKey.MAGNESIUM -> R.string.nutrition_micro_magnesium
        NutrientKey.ZINC -> R.string.nutrition_micro_zinc
        NutrientKey.POTASSIUM -> R.string.nutrition_micro_potassium
        NutrientKey.SODIUM -> R.string.nutrition_micro_sodium
        NutrientKey.VITAMIN_C -> R.string.nutrition_micro_vitamin_c
        NutrientKey.VITAMIN_D -> R.string.nutrition_micro_vitamin_d
        NutrientKey.VITAMIN_B12 -> R.string.nutrition_micro_vitamin_b12
        NutrientKey.VITAMIN_A -> R.string.nutrition_micro_vitamin_a
    }
)

/** Texte court d'un seuil pour un chip : « Protein ≥ 20 g ». */
@Composable
fun thresholdSummary(t: NutrientThreshold): String {
    val op = if (t.op == ThresholdOp.GTE) "≥" else "≤"
    val v = if (t.value % 1f == 0f) t.value.roundToInt().toString() else t.value.toString()
    return "${nutrientLabel(t.key)} $op $v ${t.key.unit}"
}

/**
 * Panneau de filtres multi-critères du catalogue (A3) : seuils ≥/≤ sur kcal +
 * macros + micros, combinables (ET). Affiche les seuils actifs (chips
 * supprimables) puis un formulaire d'ajout (nutriment + opérateur + valeur). Tout
 * texte vient de strings.xml (politique 18).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodFilterSheet(
    active: List<NutrientThreshold>,
    onAdd: (NutrientThreshold) -> Unit,
    onRemove: (NutrientThreshold) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = remember { NutrientKey.entries }
    var selectedKey by remember { mutableStateOf(NutrientKey.PROTEIN) }
    var op by remember { mutableStateOf(ThresholdOp.GTE) }
    var valueText by remember { mutableStateOf("") }

    val parsed = valueText.replace(',', '.').toFloatOrNull()
    val canAdd = parsed != null && parsed >= 0f

    // Labels -> clé (pas de collision entre labels macro/micro). Boucle `for`
    // (contexte composable) car `nutrientLabel` est @Composable.
    val labelByKey = LinkedHashMap<NutrientKey, String>()
    for (k in labels) labelByKey[k] = nutrientLabel(k)
    val keyByLabel = labelByKey.entries.associate { (k, v) -> v to k }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TitledDivider(title = stringResource(R.string.nutrition_catalog_filters_title))
            Spacer(Modifier.height(8.dp))

            // Seuils actifs.
            if (active.isEmpty()) {
                EmptyListRow(text = stringResource(R.string.nutrition_catalog_filters_none))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    active.forEach { t ->
                        ThresholdChip(text = thresholdSummary(t), onRemove = { onRemove(t) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TitledDivider(title = stringResource(R.string.nutrition_catalog_filter_add))
            Spacer(Modifier.height(8.dp))

            SingleSelectDropdown(
                label = stringResource(R.string.nutrition_catalog_filter_nutrient),
                selected = labelByKey[selectedKey] ?: "",
                options = labels.map { labelByKey[it] ?: "" },
                onSelect = { label -> keyByLabel[label]?.let { selectedKey = it } },
            )

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OpPill(
                    text = "≥",
                    selected = op == ThresholdOp.GTE,
                    onClick = { op = ThresholdOp.GTE },
                )
                OpPill(
                    text = "≤",
                    selected = op == ThresholdOp.LTE,
                    onClick = { op = ThresholdOp.LTE },
                )
                Box(modifier = Modifier.weight(1f)) {
                    CustomTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        placeholder = stringResource(R.string.nutrition_catalog_filter_value, selectedKey.unit),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTextButton(
                    text = stringResource(R.string.nutrition_catalog_filter_add_button),
                    hasBackground = true,
                    clickable = canAdd,
                    onClick = {
                        if (parsed != null) {
                            onAdd(NutrientThreshold(selectedKey, op, parsed))
                            valueText = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (active.isNotEmpty()) {
                    ActionTextButton(
                        text = stringResource(R.string.nutrition_catalog_filter_clear),
                        hasBackground = true,
                        backgroundColor = redMedium,
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OpPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(46.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) blueMedium else appColors.bgRecessed)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            color = if (selected) appColors.textOnSelected else appColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ThresholdChip(text: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(50))
            .background(blueMedium)
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(text = text, color = appColors.textOnSelected, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(R.drawable.ic_rounded_close_small),
            contentDescription = stringResource(R.string.common_delete),
            tint = appColors.textOnSelected,
            modifier = Modifier.height(16.dp),
        )
    }
}
