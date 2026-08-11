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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.TabRowCustom
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.domain.RecipeKind
import com.example.sportapp.feature.nutrition.ui.DraftItem
import kotlin.math.roundToInt

/** Ligne d'ingrédient du brouillon d'édition (quantité en saisie libre, parsée à la sauvegarde). */
private data class DraftIngredient(val foodUUID: String, val name: String, val quantity: String)

private fun parseGrams(text: String): Float? = text.replace(',', '.').toFloatOrNull()

/**
 * Éditeur de recette (création ou modification) en bottom-sheet : nom + kind
 * (Plat / Repas enregistré) + poids cuit (kind=RECIPE) + liste d'ingrédients
 * réordonnable. L'état du brouillon vit localement (réinitialisé à chaque
 * ouverture via la `key` du callsite). « Ajouter un ingrédient » ouvre le
 * [FoodPickerSheet] par-dessus.
 *
 * [recipe] null = création. [initialIngredients] = ingrédients existants triés
 * par orderIndex (édition). [onSave] reçoit le nom, le kind, le poids cuit (null
 * pour SAVED_MEAL) et la liste ordonnée des ingrédients.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorSheet(
    recipe: Recipe?,
    initialIngredients: List<RecipeIngredient>,
    foods: List<Food>,
    foodNameOf: (String) -> String,
    onSave: (name: String, kind: String, totalWeightG: Float?, items: List<DraftItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(recipe?.name ?: "") }
    // 0 = Plat (RECIPE), 1 = Repas enregistré (SAVED_MEAL).
    var kindIndex by remember { mutableStateOf(if (recipe?.kind == RecipeKind.SAVED_MEAL) 1 else 0) }
    var weight by remember {
        mutableStateOf(recipe?.totalWeightG?.let { if (it % 1f == 0f) it.roundToInt().toString() else it.toString() } ?: "")
    }
    val ingredients: SnapshotStateList<DraftIngredient> = remember {
        initialIngredients.map {
            DraftIngredient(it.foodUUID, foodNameOf(it.foodUUID), trimGrams(it.quantityG))
        }.toMutableStateList()
    }
    var showPicker by remember { mutableStateOf(false) }

    val valid = remember(name, kindIndex, weight, ingredients.toList()) {
        editorValid(name, kindIndex, weight, ingredients)
    }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TitledDivider(
                title = stringResource(
                    if (recipe != null) R.string.nutrition_recipe_edit_title
                    else R.string.nutrition_recipe_create_title
                )
            )

            CustomTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.nutrition_recipe_name),
                placeholder = stringResource(R.string.nutrition_recipe_name_hint),
            )

            TabRowCustom(
                items = listOf(
                    stringResource(R.string.nutrition_recipe_kind_recipe),
                    stringResource(R.string.nutrition_recipe_kind_saved_meal),
                ),
                selectedIndex = kindIndex,
                onTabSelected = { kindIndex = it },
                height = 42.dp,
            )
            Text(
                text = stringResource(
                    if (kindIndex == 0) R.string.nutrition_recipe_hint_recipe
                    else R.string.nutrition_recipe_hint_saved_meal
                ),
                color = appColors.textTertiary,
                fontSize = 12.sp,
            )

            if (kindIndex == 0) {
                CustomTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = stringResource(R.string.nutrition_recipe_cooked_weight),
                    placeholder = "450",
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            }

            TitledDivider(title = stringResource(R.string.nutrition_recipe_ingredients))
            if (ingredients.isEmpty()) {
                EmptyListRow(text = stringResource(R.string.nutrition_recipe_no_ingredient))
            } else {
                ingredients.forEachIndexed { index, ing ->
                    IngredientEditRow(
                        ingredient = ing,
                        isFirst = index == 0,
                        isLast = index == ingredients.lastIndex,
                        onQuantityChange = { ingredients[index] = ing.copy(quantity = it) },
                        onMoveUp = { if (index > 0) ingredients.add(index - 1, ingredients.removeAt(index)) },
                        onMoveDown = { if (index < ingredients.lastIndex) ingredients.add(index + 1, ingredients.removeAt(index)) },
                        onRemove = { ingredients.removeAt(index) },
                    )
                }
            }
            ActionIconWithTextButton(
                iconRes = R.drawable.ic_add,
                text = stringResource(R.string.nutrition_recipe_add_ingredient),
                iconSize = 20.dp,
                backgroundColor = blueMedium,
                onClick = { showPicker = true },
            )

            if (!valid) {
                Text(
                    text = stringResource(R.string.nutrition_recipe_invalid),
                    color = appColors.snackbarError,
                    fontSize = 12.sp,
                )
            }
            ActionIconWithTextButton(
                iconRes = R.drawable.ic_rounded_check,
                text = stringResource(R.string.common_save),
                iconSize = 20.dp,
                clickable = valid,
                backgroundColor = if (valid) appColors.primaryAction else appColors.bgButton,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!valid) return@ActionIconWithTextButton
                    val kind = if (kindIndex == 0) RecipeKind.RECIPE else RecipeKind.SAVED_MEAL
                    val totalWeightG = if (kindIndex == 0 && weight.isNotBlank()) parseGrams(weight) else null
                    val items = ingredients.map { DraftItem(it.foodUUID, parseGrams(it.quantity)!!) }
                    onSave(name.trim(), kind, totalWeightG, items)
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showPicker) {
        FoodPickerSheet(
            foods = foods,
            onPick = { food ->
                showPicker = false
                // Pas de doublon d'aliment dans une même recette (miroir web onIngredientPicked).
                if (ingredients.none { it.foodUUID == food.uuid }) {
                    ingredients.add(DraftIngredient(food.uuid, food.name, "100"))
                }
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun IngredientEditRow(
    ingredient: DraftIngredient,
    isFirst: Boolean,
    isLast: Boolean,
    onQuantityChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appColors.bgRecessed)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = ingredient.name,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CustomTextField(
            value = ingredient.quantity,
            onValueChange = onQuantityChange,
            placeholder = stringResource(R.string.nutrition_food_portion_grams),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.width(72.dp),
        )
        ActionIconButton(
            iconRes = R.drawable.ic_arrow_upward_alt,
            iconSize = 18.dp,
            boxSize = 32.dp,
            clickable = !isFirst,
            tint = if (isFirst) appColors.textTertiary else appColors.textPrimary,
            onClick = onMoveUp,
        )
        ActionIconButton(
            iconRes = R.drawable.ic_arrow_downward_alt,
            iconSize = 18.dp,
            boxSize = 32.dp,
            clickable = !isLast,
            tint = if (isLast) appColors.textTertiary else appColors.textPrimary,
            onClick = onMoveDown,
        )
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_delete_forever,
            iconSize = 18.dp,
            boxSize = 32.dp,
            customBackgroundColor = redMedium,
            tint = appColors.textOnSelected,
            onClick = onRemove,
        )
    }
}

/** Validation : nom non vide, ≥ 1 ingrédient, toutes les quantités > 0, poids cuit > 0 si saisi. */
private fun editorValid(
    name: String,
    kindIndex: Int,
    weight: String,
    ingredients: List<DraftIngredient>,
): Boolean {
    if (name.trim().isEmpty()) return false
    if (ingredients.isEmpty()) return false
    if (!ingredients.all { (parseGrams(it.quantity) ?: 0f) > 0f }) return false
    if (kindIndex == 0 && weight.isNotBlank() && (parseGrams(weight) ?: 0f) <= 0f) return false
    return true
}

private fun trimGrams(v: Float): String = if (v % 1f == 0f) v.roundToInt().toString() else v.toString()
