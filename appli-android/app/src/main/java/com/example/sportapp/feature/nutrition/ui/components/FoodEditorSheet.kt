package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.designsystem.common_components.ActionTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.CustomSwitch
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.domain.FoodSource
import com.example.sportapp.feature.nutrition.domain.effectiveFoodKcal
import kotlin.math.roundToInt

/** Champs sauvegardés par l'éditeur (kcal/fiber optionnels). */
data class FoodEditorResult(
    val name: String,
    val brand: String?,
    val kcalPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val fiberPer100g: Float?,
    /** Hydratation : boisson eau → auto-comptage (1 g = 1 ml). Éditable sur tout aliment. */
    val isWater: Boolean,
)

/**
 * Éditeur d'aliment du catalogue (A3) : création d'un aliment CUSTOM ou édition
 * d'un existant. Les valeurs nutritionnelles ne sont éditables que pour les
 * aliments CUSTOM (un OFF/CIQUAL garde ses valeurs source) ; les portions nommées
 * sont gérables sur tout aliment déjà créé. Tout texte vient de strings.xml
 * (politique 18).
 *
 * @param food null = création ; sinon édition de cet aliment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorSheet(
    food: Food?,
    portions: List<FoodPortion>,
    onSave: (FoodEditorResult) -> Unit,
    onAddPortion: (label: String, grams: Float) -> Unit,
    onDeletePortion: (FoodPortion) -> Unit,
    onDismiss: () -> Unit,
) {
    val editable = food == null || food.source == FoodSource.CUSTOM

    var name by remember { mutableStateOf(food?.name ?: "") }
    var brand by remember { mutableStateOf(food?.brand ?: "") }
    var kcal by remember { mutableStateOf(food?.kcalPer100g?.let { trimNum(it) } ?: "") }
    var protein by remember { mutableStateOf(food?.proteinPer100g?.let { trimNum(it) } ?: "") }
    var carbs by remember { mutableStateOf(food?.carbsPer100g?.let { trimNum(it) } ?: "") }
    var fat by remember { mutableStateOf(food?.fatPer100g?.let { trimNum(it) } ?: "") }
    var fiber by remember { mutableStateOf(food?.fiberPer100g?.let { trimNum(it) } ?: "") }
    var isWater by remember { mutableStateOf(food?.isWater ?: false) }

    val pName = name.trim()
    val pProtein = protein.parseNum()
    val pCarbs = carbs.parseNum()
    val pFat = fat.parseNum()
    val pKcal = kcal.parseNumOrNull()
    val pFiber = fiber.parseNumOrNull()
    // kcal/fiber optionnels (blanc OK) ; macros requises >= 0.
    val macrosValid = pProtein != null && pCarbs != null && pFat != null
    val kcalValid = kcal.isBlank() || pKcal != null
    val fiberValid = fiber.isBlank() || pFiber != null
    val canSave = pName.isNotEmpty() && macrosValid && kcalValid && fiberValid

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TitledDivider(
                title = stringResource(
                    if (food == null) R.string.nutrition_catalog_create_title
                    else R.string.nutrition_catalog_edit_title
                )
            )

            CustomTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.nutrition_food_name),
                placeholder = stringResource(R.string.nutrition_food_name_hint),
            )
            CustomTextField(
                value = brand,
                onValueChange = { brand = it },
                label = stringResource(R.string.nutrition_food_brand),
                placeholder = stringResource(R.string.nutrition_food_brand_hint),
            )

            Text(
                text = stringResource(R.string.nutrition_food_per_100g),
                color = appColors.textTertiary,
                fontSize = 12.sp,
            )

            if (editable) {
                NumField(kcal, { kcal = it }, R.string.nutrition_macro_calories, "kcal", optional = true) {
                    // Indice kcal dérivée des macros (D12) quand la valeur est laissée vide.
                    val derived = 4f * (pProtein ?: 0f) + 4f * (pCarbs ?: 0f) +
                        9f * (pFat ?: 0f) + 2f * (pFiber ?: 0f)
                    stringResource(R.string.nutrition_food_kcal_derived_hint, derived.roundToInt())
                }
                NumField(protein, { protein = it }, R.string.nutrition_macro_protein, "g")
                NumField(carbs, { carbs = it }, R.string.nutrition_macro_carbs, "g")
                NumField(fat, { fat = it }, R.string.nutrition_macro_fat, "g")
                NumField(fiber, { fiber = it }, R.string.nutrition_macro_fiber, "g", optional = true)
            } else {
                Text(
                    text = stringResource(R.string.nutrition_food_readonly_note, food!!.source),
                    color = appColors.textTertiary,
                    fontSize = 12.sp,
                )
                ReadOnlyMacro(R.string.nutrition_macro_calories, "${effectiveFoodKcal(food).roundToInt()} kcal")
                ReadOnlyMacro(R.string.nutrition_macro_protein, "${trimNum(food.proteinPer100g)} g")
                ReadOnlyMacro(R.string.nutrition_macro_carbs, "${trimNum(food.carbsPer100g)} g")
                ReadOnlyMacro(R.string.nutrition_macro_fat, "${trimNum(food.fatPer100g)} g")
                food.fiberPer100g?.let { ReadOnlyMacro(R.string.nutrition_macro_fiber, "${trimNum(it)} g") }
            }

            // Hydratation (2026-07-05) : marquer l'aliment comme boisson eau
            // (auto-comptage 1 g = 1 ml). Éditable sur tout aliment (backfill inclus).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nutrition_food_is_water),
                        color = appColors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = stringResource(R.string.nutrition_food_is_water_hint),
                        color = appColors.textTertiary,
                        fontSize = 11.sp,
                    )
                }
                CustomSwitch(checked = isWater, onCheckedChange = { isWater = it })
            }

            // Portions nommées (uniquement sur un aliment déjà créé).
            if (food != null) {
                Spacer(Modifier.height(2.dp))
                TitledDivider(title = stringResource(R.string.nutrition_food_portions))
                PortionsEditor(
                    portions = portions,
                    onAdd = onAddPortion,
                    onDelete = onDeletePortion,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTextButton(
                    text = stringResource(R.string.common_cancel),
                    hasBackground = true,
                    backgroundColor = appColors.bgRecessed,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                ActionTextButton(
                    text = stringResource(R.string.common_save),
                    hasBackground = true,
                    clickable = canSave,
                    onClick = {
                        onSave(
                            FoodEditorResult(
                                name = pName,
                                brand = brand.trim().ifBlank { null },
                                kcalPer100g = pKcal ?: 0f,
                                proteinPer100g = pProtein ?: 0f,
                                carbsPer100g = pCarbs ?: 0f,
                                fatPer100g = pFat ?: 0f,
                                fiberPer100g = pFiber,
                                isWater = isWater,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NumField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    unit: String,
    optional: Boolean = false,
    hint: @Composable () -> String? = { null },
) {
    val label = stringResource(labelRes) +
        if (optional) " (${stringResource(R.string.nutrition_food_optional)})" else ""
    CustomTextField(
        value = value,
        onValueChange = onValueChange,
        label = "$label · $unit",
        placeholder = "0",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    if (optional && value.isBlank()) {
        hint()?.let { Text(text = it, color = appColors.textTertiary, fontSize = 11.sp) }
    }
}

@Composable
private fun ReadOnlyMacro(labelRes: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(labelRes), color = appColors.textPrimary, fontSize = 13.sp)
        Text(text = value, color = appColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PortionsEditor(
    portions: List<FoodPortion>,
    onAdd: (label: String, grams: Float) -> Unit,
    onDelete: (FoodPortion) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }
    val pGrams = grams.parseNumOrNull()
    val canAdd = label.trim().isNotEmpty() && pGrams != null && pGrams > 0f

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        portions.forEach { p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(appColors.bgRecessed)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${p.label} · ${p.grams.roundToInt()} g",
                    color = appColors.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDelete(p) }
                        .padding(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rounded_delete_forever),
                        contentDescription = stringResource(R.string.common_delete),
                        tint = redMedium,
                        modifier = Modifier.height(20.dp),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.weight(1.4f)) {
                CustomTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = stringResource(R.string.nutrition_food_portion_label_hint),
                )
            }
            Box(modifier = Modifier.width(96.dp)) {
                CustomTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    placeholder = stringResource(R.string.nutrition_food_portion_grams),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            ActionTextButton(
                text = stringResource(R.string.nutrition_food_portion_add),
                hasBackground = true,
                clickable = canAdd,
                onClick = {
                    if (pGrams != null) {
                        onAdd(label.trim(), pGrams)
                        label = ""
                        grams = ""
                    }
                },
            )
        }
    }
}

private fun trimNum(v: Float): String = if (v % 1f == 0f) v.roundToInt().toString() else v.toString()
private fun String.parseNum(): Float? = trim().replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }
private fun String.parseNumOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }
