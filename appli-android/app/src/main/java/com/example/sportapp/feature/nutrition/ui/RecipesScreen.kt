package com.example.sportapp.feature.nutrition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardOptions
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.SheetAction
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.domain.JournalSection
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.RecipeKind
import com.example.sportapp.feature.nutrition.ui.components.RecipeEditorSheet
import kotlin.math.roundToInt

/** Recette en cours d'édition : null = création, sinon édition de cette recette. */
private sealed interface RecipeEditorTarget {
    data object Create : RecipeEditorTarget
    data class Edit(val recipe: Recipe) : RecipeEditorTarget
}

/**
 * Recettes & repas enregistrés (Nutrition A4). Deux sections : Plats (kind=RECIPE,
 * insérés au prorata du poids consommé) et Repas enregistrés (kind=SAVED_MEAL,
 * ingrédients insérés tels quels). Création/édition (nom, kind, poids cuit,
 * ingrédients réordonnables), ajout au journal du jour, suppression. Tout est
 * réactif (Room) → la liste + les macros se mettent à jour en live.
 */
@Composable
fun RecipesScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val foodsByUuid by viewModel.foodsByUuid.collectAsStateWithLifecycle()
    val ingredientsByRecipe by viewModel.ingredientsByRecipe.collectAsStateWithLifecycle()
    val todaySections by viewModel.todaySections.collectAsStateWithLifecycle()

    // Résolu en contexte composable (utilisé dans le lambda non-composable de l'éditeur).
    val deletedFoodLabel = stringResource(R.string.nutrition_recipe_deleted_food)

    var editorTarget by remember { mutableStateOf<RecipeEditorTarget?>(null) }
    var recipeForOptions by remember { mutableStateOf<Recipe?>(null) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }
    var recipeForJournal by remember { mutableStateOf<Recipe?>(null) }
    // RECIPE : (recette, section) en attente d'une quantité consommée.
    var pendingQty by remember { mutableStateOf<Pair<Recipe, JournalSection>?>(null) }

    val empty = rows.recipes.isEmpty() && rows.savedMeals.isEmpty()

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {
        ScreenTitleBar(title = stringResource(R.string.nutrition_recipes_title))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            ActionIconWithTextButton(
                iconRes = R.drawable.ic_add,
                text = stringResource(R.string.nutrition_recipes_add),
                iconSize = 20.dp,
                onClick = { editorTarget = RecipeEditorTarget.Create },
            )
        }

        if (empty) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                EmptyListRow(
                    text = stringResource(R.string.nutrition_recipes_empty),
                    iconRes = R.drawable.ic_rounded_book,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "header-recipes") { TitledDivider(title = stringResource(R.string.nutrition_recipes_section_dishes)) }
                if (rows.recipes.isEmpty()) {
                    item(key = "empty-recipes") { EmptyListRow(text = stringResource(R.string.nutrition_recipes_no_dish)) }
                }
                items(rows.recipes, key = { it.recipe.uuid }) { row ->
                    RecipeListRow(
                        row = row,
                        onAddToJournal = { recipeForJournal = row.recipe },
                        onOptions = { recipeForOptions = row.recipe },
                    )
                }

                item(key = "header-saved") { TitledDivider(title = stringResource(R.string.nutrition_recipes_section_saved)) }
                if (rows.savedMeals.isEmpty()) {
                    item(key = "empty-saved") { EmptyListRow(text = stringResource(R.string.nutrition_recipes_no_saved)) }
                }
                items(rows.savedMeals, key = { it.recipe.uuid }) { row ->
                    RecipeListRow(
                        row = row,
                        onAddToJournal = { recipeForJournal = row.recipe },
                        onOptions = { recipeForOptions = row.recipe },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // ─── Éditeur (création / édition) ─────────────────────────────────────────
    editorTarget?.let { target ->
        val recipe = (target as? RecipeEditorTarget.Edit)?.recipe
        key(recipe?.uuid ?: "create") {
            RecipeEditorSheet(
                recipe = recipe,
                initialIngredients = recipe?.let { ingredientsByRecipe[it.uuid] } ?: emptyList(),
                foods = foods,
                foodNameOf = { uuid -> foodsByUuid[uuid]?.name ?: deletedFoodLabel },
                onSave = { name, kind, totalWeightG, items ->
                    viewModel.saveRecipe(recipe?.uuid, name, kind, totalWeightG, items)
                    editorTarget = null
                },
                onDismiss = { editorTarget = null },
            )
        }
    }

    // ─── Options d'une recette ────────────────────────────────────────────────
    recipeForOptions?.let { recipe ->
        OptionsBottomSheet(
            title = recipe.name,
            onDismissRequest = { recipeForOptions = null },
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.nutrition_recipes_action_add_journal),
                    iconRes = R.drawable.ic_add,
                    color = appColors.primaryAction,
                    onClick = {
                        recipeForJournal = recipe
                        recipeForOptions = null
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.nutrition_catalog_action_edit),
                    iconRes = R.drawable.ic_rounded_edit,
                    color = blueMedium,
                    onClick = {
                        editorTarget = RecipeEditorTarget.Edit(recipe)
                        recipeForOptions = null
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    iconRes = R.drawable.ic_rounded_delete_forever,
                    color = redMedium,
                    onClick = {
                        recipeToDelete = recipe
                        recipeForOptions = null
                    },
                ),
            ),
        )
    }

    // ─── Ajout au journal : choix de la période du jour ───────────────────────
    recipeForJournal?.let { recipe ->
        AddToJournalSheet(
            recipeName = recipe.name,
            sections = todaySections,
            onPick = { section ->
                recipeForJournal = null
                if (recipe.kind == RecipeKind.SAVED_MEAL) {
                    viewModel.addSavedMealToJournal(recipe, section)
                } else {
                    pendingQty = recipe to section
                }
            },
            onDismiss = { recipeForJournal = null },
        )
    }

    // ─── kind=RECIPE : quantité consommée (g) avant insertion au prorata ──────
    pendingQty?.let { (recipe, section) ->
        RecipeQuantityDialog(
            recipeName = recipe.name,
            onConfirm = { quantityG ->
                viewModel.addRecipeToJournal(recipe, section, quantityG)
                pendingQty = null
            },
            onDismiss = { pendingQty = null },
        )
    }

    // ─── Confirmation de suppression ──────────────────────────────────────────
    recipeToDelete?.let { recipe ->
        ConfirmationDialog(
            title = stringResource(R.string.nutrition_recipes_delete_title),
            message = stringResource(R.string.nutrition_recipes_delete_message, recipe.name),
            onConfirm = {
                viewModel.deleteRecipe(recipe)
                recipeToDelete = null
            },
            onDismiss = { recipeToDelete = null },
        )
    }
}

@Composable
private fun RecipeListRow(
    row: RecipeRow,
    onAddToJournal: () -> Unit,
    onOptions: () -> Unit,
) {
    val kcal = row.macros.totals.kcal.roundToInt()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appColors.bgRecessed)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.recipe.name,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Méta : type + nb d'ingrédients (+ poids cuit pour kind=RECIPE).
            val meta = buildList {
                add(
                    stringResource(
                        if (row.recipe.kind == RecipeKind.SAVED_MEAL) R.string.nutrition_recipes_kind_saved_meal
                        else R.string.nutrition_recipes_kind_recipe
                    )
                )
                add(stringResource(R.string.nutrition_recipes_ingredient_count, row.ingredientCount))
                if (row.recipe.kind == RecipeKind.RECIPE && (row.recipe.totalWeightG ?: 0f) > 0f) {
                    add(stringResource(R.string.nutrition_recipes_cooked_grams, row.recipe.totalWeightG!!.roundToInt()))
                }
            }.joinToString(" · ")
            Text(text = meta, color = appColors.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Agrégats macros du plat (kcal + P/G/L/fibres).
            Spacer(Modifier.height(2.dp))
            MacroAggregateLine(row)
        }
        Spacer(Modifier.width(6.dp))
        ActionIconButton(
            iconRes = R.drawable.ic_add,
            customBackgroundColor = appColors.primaryAction,
            tint = appColors.textOnSelected,
            clickable = row.ingredientCount > 0,
            onClick = onAddToJournal,
        )
        Spacer(Modifier.width(6.dp))
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_more_vert,
            onClick = onOptions,
        )
    }
}

/** Ligne compacte d'agrégats : kcal coloré + P/G/L/fibres en g. */
@Composable
private fun MacroAggregateLine(row: RecipeRow) {
    val t = row.macros.totals
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.nutrition_recipes_kcal, t.kcal.roundToInt()),
            color = macroColor(MacroKey.KCAL),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(
                R.string.nutrition_recipes_macro_line,
                t.protein.roundToInt(), t.carbs.roundToInt(), t.fat.roundToInt(), t.fiber.roundToInt(),
            ),
            color = appColors.textTertiary,
            fontSize = 12.sp,
        )
    }
}

/** Sheet « Ajouter au journal » : liste des périodes du jour courant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToJournalSheet(
    recipeName: String,
    sections: List<JournalSection>,
    onPick: (JournalSection) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TitledDivider(title = stringResource(R.string.nutrition_recipes_add_to_journal, recipeName))
            Spacer(Modifier.height(8.dp))
            if (sections.isEmpty()) {
                EmptyListRow(text = stringResource(R.string.nutrition_no_meals))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sections, key = { it.key }) { section ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(appColors.bgRecessed)
                                .clickable { onPick(section) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = section.name,
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            section.defaultTime?.let {
                                Text(text = it, color = appColors.textTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Dialog de quantité consommée (g) pour insérer un plat (kind=RECIPE) au prorata. */
@Composable
private fun RecipeQuantityDialog(
    recipeName: String,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var qty by remember { mutableStateOf("100") }
    val parsed = qty.replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed > 0f

    FormDialog(
        title = stringResource(R.string.nutrition_recipes_qty_title, recipeName),
        confirmText = stringResource(R.string.nutrition_recipes_qty_confirm),
        onConfirm = { if (parsed != null && parsed > 0f) onConfirm(parsed) },
        onDismiss = onDismiss,
        confirmEnabled = valid,
        disabledReason = stringResource(R.string.nutrition_qty_invalid),
    ) {
        CustomTextField(
            value = qty,
            onValueChange = { qty = it },
            label = stringResource(R.string.nutrition_qty_grams),
            placeholder = "100",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
