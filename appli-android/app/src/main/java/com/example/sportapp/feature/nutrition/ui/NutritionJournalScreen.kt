package com.example.sportapp.feature.nutrition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomHourPicker
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.SheetAction
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.domain.JournalSection
import com.example.sportapp.feature.nutrition.ui.components.DaySummaryBanner
import com.example.sportapp.feature.nutrition.ui.components.DuplicateMealSheet
import com.example.sportapp.feature.nutrition.ui.components.EntryQuantityDialog
import com.example.sportapp.feature.nutrition.ui.components.FoodPickerSheet
import com.example.sportapp.feature.nutrition.ui.components.HydrationCard
import com.example.sportapp.feature.nutrition.ui.components.MealPresetsSheet
import com.example.sportapp.feature.nutrition.ui.components.MealSectionCard
import com.example.sportapp.feature.nutrition.ui.components.NutritionCalendarSection

/** Cible du dialog de quantité : ajout (section + aliment) ou édition d'une entry. */
private sealed interface QtyTarget {
    data class Add(val section: JournalSection, val food: Food) : QtyTarget
    data class Edit(val entry: MealEntry) : QtyTarget
}

/**
 * Journal du jour (Nutrition A2). Navigation ← jour → (+ retour aujourd'hui),
 * calendrier mensuel à anneaux, bandeau cumuls vs cibles (macros + micros
 * repliables), sections repas (presets + ad hoc) avec CRUD d'entrées, duplication
 * d'un repas passé et portions nommées. Tout est réactif (Room) → cumuls live.
 */
@Composable
fun NutritionJournalScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: NutritionJournalViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val monthCursor by viewModel.monthCursor.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val dayTotals by viewModel.dayTotals.collectAsStateWithLifecycle()
    val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()
    val daySugarG by viewModel.daySugarG.collectAsStateWithLifecycle()
    val microRows by viewModel.microRows.collectAsStateWithLifecycle()
    val ringData by viewModel.monthRingData.collectAsStateWithLifecycle()
    val pastMeals by viewModel.pastMeals.collectAsStateWithLifecycle()
    val presetsList by viewModel.presetsList.collectAsStateWithLifecycle()
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val portions by viewModel.portions.collectAsStateWithLifecycle()
    val hydration by viewModel.hydration.collectAsStateWithLifecycle()
    val canUndoWater by viewModel.canUndoWater.collectAsStateWithLifecycle()

    var showMicros by remember { mutableStateOf(false) }
    var showWaterDialog by remember { mutableStateOf(false) }
    var waterMlInput by remember { mutableStateOf("") }
    var showWaterGoalDialog by remember { mutableStateOf(false) }
    var waterGoalInput by remember { mutableStateOf("") }
    var pickerSection by remember { mutableStateOf<JournalSection?>(null) }
    var qtyTarget by remember { mutableStateOf<QtyTarget?>(null) }
    var showAddMeal by remember { mutableStateOf(false) }     // collation (ad hoc du jour)
    var showPresets by remember { mutableStateOf(false) }     // sheet « Gérer les repas » (presets)
    var newMealName by remember { mutableStateOf("") }
    var newMealTime by remember { mutableStateOf("") }         // "HH:MM", vide = aucune heure
    var showDuplicate by remember { mutableStateOf(false) }
    var entryForOptions by remember { mutableStateOf<MealEntry?>(null) }
    var mealForOptions by remember { mutableStateOf<JournalSection?>(null) }
    var mealToDelete by remember { mutableStateOf<Meal?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {
        ScreenTitleBar(title = stringResource(R.string.nutrition_title))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NutritionCalendarSection(
                monthCursor = monthCursor,
                ringData = ringData,
                selectedDay = selectedDay,
                today = viewModel.today,
                onPrevMonth = viewModel::prevMonth,
                onNextMonth = viewModel::nextMonth,
                onSelectDay = viewModel::selectDay,
                onGoCurrentMonth = viewModel::goToday,
            )

            DaySummaryBanner(
                totals = dayTotals,
                goal = activeGoal,
                sugarG = daySugarG,
                micros = microRows,
                showMicros = showMicros,
                onToggleMicros = { showMicros = !showMicros },
            )

            // Hydratation — card au-dessus des repas, suit le jour sélectionné.
            HydrationCard(
                consumedMl = hydration.consumedMl,
                goalMl = hydration.goalMl,
                canUndo = canUndoWater,
                onAdd = viewModel::addWater,
                onCustom = {
                    waterMlInput = ""
                    showWaterDialog = true
                },
                onUndo = viewModel::undoLastWater,
                onEditGoal = {
                    waterGoalInput = hydration.goalMl?.toString() ?: ""
                    showWaterGoalDialog = true
                },
            )

            if (sections.isEmpty()) {
                EmptyListRow(
                    text = stringResource(R.string.nutrition_no_meals),
                    iconRes = R.drawable.ic_rounded_local_fire,
                )
            } else {
                sections.forEach { section ->
                    MealSectionCard(
                        section = section,
                        onAddFood = { pickerSection = section },
                        onMealOptions = { mealForOptions = section },
                        onEntryOptions = { entryForOptions = it },
                    )
                }
            }

            // Actions du jour, SOUS les repas (parité web) : gérer les repas
            // (récurrents, meal_presets) · dupliquer un repas passé · ajouter une
            // collation (ponctuelle, ad hoc). Repas ≠ collations (décision produit).
            // Couleurs : gérer + dupliquer en firstBlue, collation en primaire.
            TitledDivider(title = stringResource(R.string.nutrition_section_actions))
            // Libellés courts pour tenir sur UNE ligne (écran étroit) : Gérer (tune,
            // firstBlue) à gauche ; Dupliquer (firstBlue) + Ajouter collation (bleu
            // primaire) groupés à droite — même répartition que le web.
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_rounded_tune,
                    text = stringResource(R.string.nutrition_manage_short),
                    iconSize = 20.dp,
                    backgroundColor = firstBlue,
                    onClick = { showPresets = true },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionIconWithTextButton(
                        iconRes = R.drawable.ic_rounded_content_copy,
                        text = stringResource(R.string.nutrition_duplicate_short),
                        iconSize = 20.dp,
                        backgroundColor = firstBlue,
                        clickable = pastMeals.isNotEmpty(),
                        onClick = { showDuplicate = true },
                    )
                    ActionIconWithTextButton(
                        iconRes = R.drawable.ic_add,
                        text = stringResource(R.string.nutrition_add_snack_short),
                        iconSize = 20.dp,
                        backgroundColor = appColors.primaryAction,
                        onClick = {
                            newMealName = ""
                            newMealTime = ""
                            showAddMeal = true
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ─── Sheet : choix d'un aliment ──────────────────────────────────────────
    pickerSection?.let { section ->
        FoodPickerSheet(
            foods = foods,
            onPick = { food ->
                pickerSection = null
                qtyTarget = QtyTarget.Add(section, food)
            },
            onDismiss = { pickerSection = null },
        )
    }

    // ─── Dialog : quantité (ajout ou édition) ────────────────────────────────
    qtyTarget?.let { target ->
        when (target) {
            is QtyTarget.Add -> EntryQuantityDialog(
                title = target.food.name,
                portions = portions.filter { it.foodUUID == target.food.uuid },
                initialQuantity = 100f,
                food = target.food,
                onConfirm = { qty, label ->
                    viewModel.addEntryFromFood(target.section, target.food, qty, label)
                    qtyTarget = null
                },
                onDismiss = { qtyTarget = null },
            )
            is QtyTarget.Edit -> EntryQuantityDialog(
                title = stringResource(R.string.nutrition_edit_qty_title, target.entry.displayName),
                portions = emptyList(),
                initialQuantity = target.entry.quantityG,
                onConfirm = { qty, _ ->
                    viewModel.updateEntryQuantity(target.entry, qty)
                    qtyTarget = null
                },
                onDismiss = { qtyTarget = null },
            )
        }
    }

    // ─── Dialog : ajouter un repas ad hoc ────────────────────────────────────
    // ─── Dialog : ajouter une collation (repas ponctuel ad hoc du jour) ──────
    if (showAddMeal) {
        FormDialog(
            title = stringResource(R.string.nutrition_add_snack),
            confirmText = stringResource(R.string.common_add),
            onConfirm = {
                if (newMealName.isNotBlank()) {
                    viewModel.addAdHocMeal(newMealName, newMealTime)
                    showAddMeal = false
                }
            },
            onDismiss = { showAddMeal = false },
            confirmEnabled = newMealName.isNotBlank(),
            disabledReason = stringResource(R.string.nutrition_meal_name_required),
        ) {
            CustomTextField(
                value = newMealName,
                onValueChange = { newMealName = it },
                label = stringResource(R.string.nutrition_meal_name),
                placeholder = stringResource(R.string.nutrition_snack_name_hint),
            )
            // Heure facultative (parité dialog web : nom + custom-hour-picker).
            CustomHourPicker(
                label = stringResource(R.string.nutrition_meal_time_label),
                value = newMealTime,
                onValueChange = { newMealTime = it },
            )
        }
    }

    // ─── Dialog : ajout d'eau perso (ml) ─────────────────────────────────────
    if (showWaterDialog) {
        val ml = waterMlInput.trim().toIntOrNull()
        FormDialog(
            title = stringResource(R.string.nutrition_hydration_custom_title),
            confirmText = stringResource(R.string.common_add),
            onConfirm = {
                if (ml != null && ml > 0) {
                    viewModel.addWater(ml)
                    showWaterDialog = false
                }
            },
            onDismiss = { showWaterDialog = false },
            confirmEnabled = ml != null && ml > 0,
            disabledReason = stringResource(R.string.nutrition_hydration_custom_hint),
        ) {
            CustomTextField(
                value = waterMlInput,
                onValueChange = { waterMlInput = it.filter { c -> c.isDigit() } },
                label = stringResource(R.string.nutrition_hydration_custom_label),
                placeholder = "500",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
            )
        }
    }

    // ─── Dialog : objectif d'hydratation (ml/jour) — édité depuis la card ─────
    if (showWaterGoalDialog) {
        val ml = waterGoalInput.trim().toIntOrNull()
        FormDialog(
            title = stringResource(R.string.nutrition_goals_hydration_dialog_title),
            confirmText = stringResource(R.string.common_save),
            onConfirm = {
                if (ml != null && ml > 0) {
                    viewModel.setWaterGoal(ml)
                    showWaterGoalDialog = false
                }
            },
            onDismiss = { showWaterGoalDialog = false },
            confirmEnabled = ml != null && ml > 0,
            disabledReason = stringResource(R.string.nutrition_goals_hydration_dialog_hint),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CustomTextField(
                    value = waterGoalInput,
                    onValueChange = { waterGoalInput = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.nutrition_goals_hydration_dialog_label),
                    placeholder = "2000",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                )
                Text(
                    text = stringResource(R.string.nutrition_goals_hydration_reco),
                    color = com.example.sportapp.designsystem.theme.GrayBlue,
                    fontSize = 11.sp,
                )
            }
        }
    }

    // ─── Sheet : gérer les repas récurrents (meal_presets) ───────────────────
    if (showPresets) {
        MealPresetsSheet(
            presets = presetsList,
            canDuplicate = pastMeals.isNotEmpty(),
            onAdd = viewModel::addMealPreset,
            onRename = viewModel::renameMealPreset,
            onDelete = viewModel::deleteMealPreset,
            onMove = viewModel::moveMealPreset,
            onDuplicateRequest = {
                showPresets = false
                showDuplicate = true
            },
            onDismiss = { showPresets = false },
        )
    }

    // ─── Sheet : dupliquer un repas passé ────────────────────────────────────
    if (showDuplicate) {
        DuplicateMealSheet(
            pastMeals = pastMeals,
            onDuplicate = { meal ->
                viewModel.duplicateMeal(meal)
                showDuplicate = false
            },
            onDismiss = { showDuplicate = false },
        )
    }

    // ─── Sheet : options d'une entry ─────────────────────────────────────────
    entryForOptions?.let { entry ->
        OptionsBottomSheet(
            title = entry.displayName,
            onDismissRequest = { entryForOptions = null },
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.nutrition_edit_quantity),
                    iconRes = R.drawable.ic_rounded_edit,
                    color = blueMedium,
                    onClick = {
                        entryForOptions = null
                        qtyTarget = QtyTarget.Edit(entry)
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    iconRes = R.drawable.ic_rounded_delete_forever,
                    color = redMedium,
                    onClick = {
                        entryForOptions = null
                        viewModel.deleteEntry(entry)
                    },
                ),
            ),
        )
    }

    // ─── Sheet : options d'un repas ──────────────────────────────────────────
    mealForOptions?.let { section ->
        OptionsBottomSheet(
            title = section.name,
            onDismissRequest = { mealForOptions = null },
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.nutrition_delete_meal),
                    iconRes = R.drawable.ic_rounded_delete_forever,
                    color = redMedium,
                    onClick = {
                        val meal = section.meal
                        mealForOptions = null
                        if (meal != null) mealToDelete = meal
                    },
                ),
            ),
        )
    }

    // ─── Confirmation : supprimer le repas ───────────────────────────────────
    mealToDelete?.let { meal ->
        ConfirmationDialog(
            title = stringResource(R.string.nutrition_delete_meal),
            message = stringResource(R.string.nutrition_delete_meal_message, meal.name),
            onConfirm = {
                viewModel.deleteMeal(meal)
                mealToDelete = null
            },
            onDismiss = { mealToDelete = null },
        )
    }
}

