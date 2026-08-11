package com.example.sportapp.feature.nutrition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.secondBlue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomDatePickerDialog
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.WheelPicker
import com.example.sportapp.designsystem.common_components.ProgressBarPrimitive
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle
import com.example.sportapp.designsystem.common_components.AnimatedModeContent
import com.example.sportapp.designsystem.common_components.SheetAction
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.swipeToSwitch
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.domain.MacroKcalShare
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.MacroTotals
import com.example.sportapp.feature.nutrition.domain.fiberDensity
import com.example.sportapp.feature.nutrition.domain.fiberTargetG
import com.example.sportapp.feature.nutrition.domain.macroKcalBreakdown
import com.example.sportapp.feature.nutrition.domain.macroPerKg
import com.example.sportapp.feature.nutrition.ui.components.DonutSlice
import com.example.sportapp.feature.nutrition.ui.components.MacroDonutChart
import com.example.sportapp.feature.nutrition.ui.components.MacroRadarChart
import com.example.sportapp.feature.nutrition.ui.components.RadarAxis
import java.time.LocalDate
import kotlin.math.roundToInt

/** Mode du formulaire de cible : création (nouvelle entrée) ou édition d'une entrée existante. */
private sealed interface GoalFormMode {
    data object Create : GoalFormMode
    data class Edit(val goal: NutritionGoal) : GoalFormMode
}

/** Vue du « Profil macros » : radar (défaut), multi-anneaux ou barres comparatives. UPPER_CASE (politique 11). */
private enum class MacroProfileView { RADAR, RINGS, BARS }

/**
 * Vue du cadre « Répartition des calories » : un seul visuel à la fois. Donut par
 * défaut, puis % par macro, g/kg de poids de corps, et radar cible vs réel.
 * UPPER_CASE (politique 11).
 */
private enum class BreakdownView { DONUT, RADAR, PER_KG }

/** Ordres VISUELS des modes (= ordre des toggles), partagés swipe + transition animée. */
private val BREAKDOWN_VIEW_ORDER = listOf(BreakdownView.DONUT, BreakdownView.RADAR, BreakdownView.PER_KG)
private val MACRO_PROFILE_VIEW_ORDER = listOf(MacroProfileView.RADAR, MacroProfileView.RINGS, MacroProfileView.BARS)

/**
 * Objectifs nutrition (A5) — miroir Android de la page web `/nutrition/goals` :
 * cible active du jour (kcal + P/G/L + fibres dérivées), analyse (donut de
 * répartition calorique + radar cible vs réel + comparatif barres 7 jours) et
 * historique des cibles par `effectiveFrom`. « Nouvelle cible » crée une nouvelle
 * entrée d'historique ; le ⋮ d'une entrée permet de la modifier/supprimer. Saisie
 * macro-first (D12) : kcal + fibres dérivés en direct. Tout texte via strings.xml
 * (politique 18), couleurs par macro via NutritionColors.macroColor.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NutritionGoalsScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: NutritionGoalsViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val weekAvg by viewModel.weekAvg.collectAsStateWithLifecycle()
    val weightKg by viewModel.weightKg.collectAsStateWithLifecycle()

    var formMode by remember { mutableStateOf<GoalFormMode?>(null) }
    var fFrom by remember { mutableStateOf(viewModel.today) }
    var fProtein by remember { mutableStateOf(0) }
    var fCarbs by remember { mutableStateOf(0) }
    var fFat by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var goalForOptions by remember { mutableStateOf<NutritionGoal?>(null) }
    var goalToDelete by remember { mutableStateOf<NutritionGoal?>(null) }

    fun openCreate() {
        val g = activeGoal
        formMode = GoalFormMode.Create
        fFrom = viewModel.today
        fProtein = g?.proteinG?.roundToInt() ?: 0
        fCarbs = g?.carbsG?.roundToInt() ?: 0
        fFat = g?.fatG?.roundToInt() ?: 0
    }

    fun openEdit(goal: NutritionGoal) {
        formMode = GoalFormMode.Edit(goal)
        fFrom = goal.effectiveFrom
        fProtein = goal.proteinG.roundToInt()
        fCarbs = goal.carbsG.roundToInt()
        fFat = goal.fatG.roundToInt()
    }

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {
        ScreenTitleBar(title = stringResource(R.string.nutrition_goals_title))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            activeGoal?.let { goal ->
                BreakdownBlock(goal = goal, weightKg = weightKg)

                MacroProfileSection(goal = goal, weekAvg = weekAvg)
            }

            // Cible active SOUS le Profil macros (demande user 2026-07-14).
            ActiveTargetCard(goal = activeGoal, onNewTarget = { openCreate() })

            TitledDivider(title = stringResource(R.string.nutrition_goals_section_history))
            if (history.isEmpty()) {
                EmptyListRow(
                    text = stringResource(R.string.nutrition_goals_history_empty),
                    iconRes = R.drawable.ic_rounded_flag,
                )
            } else {
                // Cadre de liste UNIQUE (miroir ListFrame/ListRow web, demande user 2026-07-14) :
                // rows à plat séparées par un filet secondBlue inset (sauf la dernière et sauf
                // sous la row active — son liseré primaryAction fait office de séparateur).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(appColors.bgRecessed),
                ) {
                    history.forEachIndexed { i, goal ->
                        val isActive = goal.uuid == activeGoal?.uuid
                        GoalHistoryRow(
                            goal = goal,
                            isActive = isActive,
                            onOptions = { goalForOptions = goal },
                        )
                        if (i < history.lastIndex && !isActive) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = secondBlue,
                                modifier = Modifier.padding(start = 14.dp, end = 6.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ─── Formulaire création / modification (bottom sheet + wheel pickers) ────
    formMode?.let { mode ->
        val derived = remember(fProtein, fCarbs, fFat) {
            com.example.sportapp.feature.nutrition.domain.deriveGoalFromMacros(
                fProtein.toFloat(), fCarbs.toFloat(), fFat.toFloat(),
            )
        }
        val valid = derived.kcal > 0f

        AppBottomSheet(onDismissRequest = { formMode = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (mode is GoalFormMode.Edit) R.string.nutrition_goals_form_edit_title
                        else R.string.nutrition_goals_form_new_title
                    ),
                    color = appColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                // Date d'effet (cliquable -> date picker).
                Column {
                    Text(
                        text = stringResource(R.string.nutrition_goals_form_date),
                        color = appColors.primaryAction,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(appColors.bgRecessed)
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = formatDate(fFrom),
                            color = appColors.textPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 3 wheel pickers côte à côte (P / G / L) — la bottom sheet offre la largeur.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MacroWheel(MacroKey.PROTEIN, fProtein, 0..400) { fProtein = it }
                    MacroWheel(MacroKey.CARBS, fCarbs, 0..800) { fCarbs = it }
                    MacroWheel(MacroKey.FAT, fFat, 0..300) { fFat = it }
                }

                // Total kcal + fibres dérivés en direct.
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(appColors.bgRecessed)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = derived.kcal.roundToInt().toString(),
                        color = appColors.primaryAction,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.nutrition_goals_form_derived_label,
                            derived.fiberG.roundToInt(),
                        ),
                        color = appColors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }

                // Actions.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    DialogSecondaryButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { formMode = null },
                    )
                    DialogPrimaryButton(
                        text = stringResource(R.string.common_save),
                        enabled = valid,
                        onClick = {
                            if (valid) {
                                val p = fProtein.toFloat()
                                val c = fCarbs.toFloat()
                                val f = fFat.toFloat()
                                when (mode) {
                                    is GoalFormMode.Create -> viewModel.createGoal(fFrom, p, c, f)
                                    is GoalFormMode.Edit -> viewModel.updateGoal(mode.goal, fFrom, p, c, f)
                                }
                                formMode = null
                            }
                        },
                    )
                }
            }
        }
    }

    // ─── Date picker (date d'effet) ──────────────────────────────────────────
    if (showDatePicker) {
        CustomDatePickerDialog(
            initialIso = fFrom,
            title = stringResource(R.string.nutrition_goals_form_date),
            maxYear = LocalDate.now().year + 1,
            onConfirm = { iso ->
                fFrom = iso
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    // ─── Options d'une cible (modifier / supprimer) ──────────────────────────
    goalForOptions?.let { goal ->
        OptionsBottomSheet(
            title = stringResource(R.string.nutrition_goals_from, formatDate(goal.effectiveFrom)),
            onDismissRequest = { goalForOptions = null },
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.nutrition_catalog_action_edit),
                    iconRes = R.drawable.ic_rounded_edit,
                    color = blueMedium,
                    onClick = {
                        goalForOptions = null
                        openEdit(goal)
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    iconRes = R.drawable.ic_rounded_delete_forever,
                    color = redMedium,
                    onClick = {
                        goalForOptions = null
                        goalToDelete = goal
                    },
                ),
            ),
        )
    }

    // ─── Confirmation de suppression ─────────────────────────────────────────
    goalToDelete?.let { goal ->
        ConfirmationDialog(
            title = stringResource(R.string.nutrition_goals_delete_title),
            message = stringResource(R.string.nutrition_goals_delete_message, formatDate(goal.effectiveFrom)),
            onConfirm = {
                viewModel.deleteGoal(goal)
                goalToDelete = null
            },
            onDismiss = { goalToDelete = null },
        )
    }

}

// ─── Cible active ────────────────────────────────────────────────────────────

@Composable
private fun ActiveTargetCard(goal: NutritionGoal?, onNewTarget: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (goal == null) {
            // Pas de cible active : le texte d'invite + le bouton « + Nouvelle cible » à droite.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nutrition_goals_active_empty),
                    color = appColors.textTertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_add,
                    text = stringResource(R.string.nutrition_goals_new),
                    iconSize = 20.dp,
                    backgroundColor = firstBlue,
                    onClick = onNewTarget,
                )
            }
        } else {
            // Valeurs de la cible active EN HAUT (demande user 2026-07-14 : lignes échangées).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActiveValue(MacroKey.KCAL, goal.kcal.roundToInt(), R.string.nutrition_macro_calories, "")
                ActiveValue(MacroKey.PROTEIN, goal.proteinG.roundToInt(), R.string.nutrition_macro_protein, "g")
                ActiveValue(MacroKey.CARBS, goal.carbsG.roundToInt(), R.string.nutrition_macro_carbs, "g")
                ActiveValue(MacroKey.FAT, goal.fatG.roundToInt(), R.string.nutrition_macro_fat, "g")
                ActiveValue(
                    MacroKey.FIBER,
                    (fiberTargetG(goal.kcal) ?: 0f).roundToInt(),
                    R.string.nutrition_macro_fiber,
                    "g",
                )
            }
            // Ligne du bas : « + Nouvelle cible » à droite, « depuis le [date] » à gauche.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    // La DATE en blanc (texte primaire), le libellé « Depuis le » reste
                    // secondaire — miroir .date-value web (demande user 2026-07-14).
                    val dateStr = formatDate(goal.effectiveFrom)
                    val sinceText = stringResource(R.string.nutrition_goals_since, dateStr)
                    val dateStart = sinceText.indexOf(dateStr)
                    Text(
                        text = buildAnnotatedString {
                            append(sinceText)
                            if (dateStart >= 0) {
                                addStyle(
                                    SpanStyle(color = appColors.textPrimary),
                                    dateStart,
                                    dateStart + dateStr.length,
                                )
                            }
                        },
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_add,
                    text = stringResource(R.string.nutrition_goals_new),
                    iconSize = 20.dp,
                    backgroundColor = firstBlue,
                    onClick = onNewTarget,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActiveValue(
    key: MacroKey,
    value: Int,
    labelRes: Int,
    unit: String,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (unit.isEmpty()) value.toString() else "$value $unit",
            color = macroColor(key),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(labelRes),
            color = appColors.textTertiary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Analyse : donut + métriques dérivées ────────────────────────────────────

/**
 * Cadre « Répartition des calories » : un seul visuel à la fois, piloté par un
 * [SegmentedIconToggle] 4 modes (donut / % par macro / g·kg / radar) placé à
 * droite du titre de section — même pattern que [MacroProfileSection]. Le donut,
 * le radar et le [SegmentedIconToggle] sont réutilisés tels quels (pas de nouveau
 * graphe). La densité fibres reste un repère santé toujours visible en pied
 * (miroir du `analysis__footer` web).
 */
@Composable
private fun BreakdownBlock(goal: NutritionGoal, weightKg: Float?) {
    var view by remember { mutableStateOf(BreakdownView.DONUT) }
    val breakdown = macroKcalBreakdown(goal)

    // Cadre thirdBlue UNIQUE : titre + bascule + visuel dedans (demande user 2026-07-14,
    // pattern SectionFrame des Stats nutrition). Swipe horizontal = mode suivant/précédent.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed)
            .padding(16.dp)
            .swipeToSwitch(values = BREAKDOWN_VIEW_ORDER, current = view) { view = it },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TitledDivider(title = stringResource(R.string.nutrition_goals_section_breakdown))
            }
            Spacer(Modifier.width(8.dp))
            SegmentedIconToggle(
                items = listOf(
                    SegmentItem(
                        BreakdownView.DONUT,
                        Icons.Filled.DonutLarge,
                        stringResource(R.string.nutrition_goals_breakdown_view_donut),
                    ),
                    SegmentItem(
                        BreakdownView.RADAR,
                        Icons.Filled.Radar,
                        stringResource(R.string.nutrition_goals_breakdown_view_radar),
                    ),
                    SegmentItem(
                        BreakdownView.PER_KG,
                        Icons.Filled.MonitorWeight,
                        stringResource(R.string.nutrition_goals_breakdown_view_per_kg),
                    ),
                ),
                selected = view,
                onSelect = { view = it },
                width = 34.dp,
                iconSize = 16.dp,
            )
        }

        // Transition animée entre modes (swipe ou tap) : glisse dans le sens de navigation.
        AnimatedModeContent(current = view, values = BREAKDOWN_VIEW_ORDER) { v ->
        when (v) {
            BreakdownView.DONUT -> {
                val slices = breakdown.map { share ->
                    DonutSlice(
                        label = stringResource(macroLabelRes(share.key)),
                        color = macroColor(share.key),
                        kcal = share.kcal,
                        percent = share.percent,
                    )
                }
                MacroDonutChart(slices = slices, centerKcal = goal.kcal.roundToInt())
            }
            BreakdownView.PER_KG -> PerKgList(goal = goal, weightKg = weightKg)
            // Radar de RÉPARTITION : part en % des calories par macro (= donnée du donut),
            // pas le comparatif 7 jours réel/cible (showReference=false, échelle 0-100 %).
            BreakdownView.RADAR -> {
                // Échelle relative + headroom : la macro la plus grosse atteint le repère
                // (limite visuelle, ~80 % du rayon, pas une vraie cible) ; les autres suivent
                // ce rapport et la pointe ne déborde plus sous les libellés.
                val maxShare = breakdown.maxOfOrNull { it.percent }?.coerceAtLeast(1f) ?: 100f
                MacroRadarChart(
                    axes = breakdownRadarAxes(breakdown),
                    showLegend = false,
                    referencePercent = maxShare,
                    maxPercent = maxShare * 1.25f,
                )
            }
        }
        }
    }
}

/**
 * Apports rapportés au poids de corps — tuiles 2×2 (miroir `.bwtiles` web, demande user
 * 2026-07-14) : P/G/L en g/kg + densité fibres (g/1000 kcal). Grand chiffre coloré + unité
 * GrayBlue, icône + libellé dessous. Fond bgSurface (le cadre de section est déjà recessed).
 * Repli « — » + hint si le poids manque.
 */
@Composable
private fun PerKgList(goal: NutritionGoal, weightKg: Float?) {
    data class Tile(val key: MacroKey, val num: String, val unit: String, val icon: ImageVector)
    val tiles = listOf(
        Tile(MacroKey.PROTEIN, macroPerKg(goal.proteinG, weightKg)?.let { round1(it).toString() } ?: "—", "g/kg", Icons.Filled.Egg),
        Tile(MacroKey.CARBS, macroPerKg(goal.carbsG, weightKg)?.let { round1(it).toString() } ?: "—", "g/kg", Icons.Filled.BakeryDining),
        Tile(MacroKey.FAT, macroPerKg(goal.fatG, weightKg)?.let { round1(it).toString() } ?: "—", "g/kg", Icons.Filled.WaterDrop),
        Tile(MacroKey.FIBER, fiberDensity(goal.kcal).roundToInt().toString(), "g / 1000 kcal", Icons.Filled.Grass),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.chunked(2).forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                line.forEach { tile ->
                    val color = macroColor(tile.key)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            // secondBlue (demande user 2026-07-14) — ressort dans le cadre recessed.
                            .background(secondBlue)
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = tile.num,
                                color = color,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = tile.unit,
                                color = GrayBlue,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = tile.icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(macroLabelRes(tile.key)),
                                color = appColors.textSecondary,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
        if (weightKg == null || weightKg <= 0f) {
            Text(
                text = stringResource(R.string.nutrition_goals_per_kg_hint),
                color = appColors.divider,
                fontSize = 11.sp,
            )
        }
    }
}

// ─── Profil macros (radar / barres) ──────────────────────────────────────────

/**
 * Section « Profil macros » : un seul visuel à la fois (radar par défaut, ou
 * barres comparatives 7 jours), piloté par un [SegmentedIconToggle] Radar/Barres
 * placé à droite du titre de section — même pattern que le résumé du jour du
 * Journal. Les deux vues lisent la même donnée déjà collectée (cible vs moyenne
 * 7 j) : basculer ne recalcule ni ne perd rien.
 */
@Composable
private fun MacroProfileSection(goal: NutritionGoal, weekAvg: MacroTotals) {
    // Barres par défaut (demande user 2026-07-14, ex-radar).
    var view by remember { mutableStateOf(MacroProfileView.BARS) }

    // Cadre thirdBlue UNIQUE : titre + bascule + visuel dedans (demande user 2026-07-14).
    // Swipe horizontal = mode suivant/précédent (ordre visuel du toggle).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed)
            .padding(16.dp)
            .swipeToSwitch(values = MACRO_PROFILE_VIEW_ORDER, current = view) { view = it },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TitledDivider(title = stringResource(R.string.nutrition_goals_section_radar))
            }
            Spacer(Modifier.width(8.dp))
            SegmentedIconToggle(
                items = listOf(
                    SegmentItem(
                        MacroProfileView.RADAR,
                        Icons.Filled.Radar,
                        stringResource(R.string.nutrition_summary_view_radar),
                    ),
                    SegmentItem(
                        MacroProfileView.RINGS,
                        Icons.Filled.TrackChanges,
                        stringResource(R.string.nutrition_summary_view_rings),
                    ),
                    SegmentItem(
                        MacroProfileView.BARS,
                        Icons.Filled.BarChart,
                        stringResource(R.string.nutrition_summary_view_bars),
                    ),
                ),
                selected = view,
                onSelect = { view = it },
                width = 34.dp,
                iconSize = 16.dp,
            )
        }

        // Transition animée entre modes (swipe ou tap) : glisse dans le sens de navigation.
        AnimatedModeContent(current = view, values = MACRO_PROFILE_VIEW_ORDER) { v ->
            when (v) {
                MacroProfileView.RADAR -> MacroRadarChart(
                    axes = radarAxes(goal, weekAvg),
                    actualLabel = stringResource(R.string.nutrition_goals_radar_actual),
                    targetLabel = stringResource(R.string.nutrition_goals_radar_target),
                )
                MacroProfileView.RINGS -> MacroTargetRings(goal = goal, weekAvg = weekAvg)
                MacroProfileView.BARS -> WeekComparisonBars(weekAvg = weekAvg, goal = goal)
            }
        }
    }
}

/**
 * Multi-anneaux concentriques du Profil macros (miroir `macroRingViews` + concentric-rings-chart
 * web) : un anneau par macro (kcal extérieur → fibres au centre), avancement = moyenne 7 jours /
 * cible active (borné 0..1), kcal moyens 7 j au centre. Légende à droite (pastille + libellé +
 * % de l'objectif, non plafonné) — layout miroir du donut de répartition.
 */
@Composable
private fun MacroTargetRings(goal: NutritionGoal, weekAvg: MacroTotals) {
    // Extérieur → intérieur, ordre du web (RING_ORDER : kcal, glucides, lipides, protéines, fibres).
    val specs = listOf(
        Triple(MacroKey.KCAL, weekAvg.kcal, goal.kcal),
        Triple(MacroKey.CARBS, weekAvg.carbs, goal.carbsG),
        Triple(MacroKey.FAT, weekAvg.fat, goal.fatG),
        Triple(MacroKey.PROTEIN, weekAvg.protein, goal.proteinG),
        Triple(MacroKey.FIBER, weekAvg.fiber, fiberTargetG(goal.kcal) ?: 0f),
    )
    val colors = specs.map { macroColor(it.first) }
    val trough = appColors.bgSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var insetPx = 0f
                    specs.forEachIndexed { i, (_, value, target) ->
                        // kcal (extérieur) un peu plus épais, comme les cases du calendrier.
                        val strokePx = (if (i == 0) 8f else 6f).dp.toPx()
                        val arcInset = insetPx + strokePx / 2f
                        val topLeft = Offset(arcInset, arcInset)
                        val arcSize = Size(size.width - arcInset * 2f, size.height - arcInset * 2f)
                        drawArc(
                            color = trough,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx),
                        )
                        val progress = if (target > 0f) (value / target).coerceIn(0f, 1f) else 0f
                        if (progress > 0f) {
                            drawArc(
                                color = colors[i],
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx),
                            )
                        }
                        insetPx = arcInset + strokePx / 2f + 2.5f.dp.toPx()
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = weekAvg.kcal.roundToInt().toString(),
                        color = macroColor(MacroKey.KCAL),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = "kcal", color = appColors.textTertiary, fontSize = 10.sp)
                }
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                specs.forEachIndexed { i, (key, value, target) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors[i]),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(macroLabelRes(key)),
                            color = appColors.textSecondary,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (target > 0f) "${(value / target * 100f).roundToInt()} %" else "—",
                            color = colors[i],
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        }
        // Rappel : les anneaux comparent la moyenne sur 7 jours à la cible (demande user 2026-07-14).
        Text(
            text = stringResource(R.string.nutrition_goals_week_avg_caption),
            color = appColors.divider,
            fontSize = 11.sp,
        )
    }
}

// ─── Comparatif 7 jours (barres) ─────────────────────────────────────────────

@Composable
private fun WeekComparisonBars(weekAvg: MacroTotals, goal: NutritionGoal) {
    val specs = listOf(
        Triple(MacroKey.KCAL, weekAvg.kcal, goal.kcal),
        Triple(MacroKey.CARBS, weekAvg.carbs, goal.carbsG),
        Triple(MacroKey.FAT, weekAvg.fat, goal.fatG),
        Triple(MacroKey.PROTEIN, weekAvg.protein, goal.proteinG),
        Triple(MacroKey.FIBER, weekAvg.fiber, fiberTargetG(goal.kcal) ?: 0f),
    )
    // À plat : le fond recessed est porté par le cadre de section (MacroProfileSection).
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        specs.forEach { (key, value, target) ->
            val unit = if (key == MacroKey.KCAL) "kcal" else "g"
            val progress = if (target > 0f) (value / target).coerceIn(0f, 1f) else 0f
            val color = macroColor(key)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(macroLabelRes(key)),
                    color = color,
                    fontSize = 13.sp,
                    modifier = Modifier.width(76.dp),
                )
                ProgressBarPrimitive(
                    progress = progress,
                    color = color,
                    troughColor = appColors.bgSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${value.roundToInt()} / ${target.roundToInt()} $unit",
                    color = appColors.textPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(120.dp).padding(start = 4.dp),
                )
            }
        }
        // Rappel : ces barres comparent la moyenne sur 7 jours à la cible.
        Text(
            text = stringResource(R.string.nutrition_goals_week_avg_caption),
            color = appColors.divider,
            fontSize = 11.sp,
        )
    }
}

// ─── Historique ──────────────────────────────────────────────────────────────

@Composable
private fun GoalHistoryRow(goal: NutritionGoal, isActive: Boolean, onOptions: () -> Unit) {
    // Row À PLAT dans le cadre de liste (le fond recessed est porté par le cadre) ;
    // l'active garde son liseré primaryAction (miroir .list-row--selected web).
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (isActive) Modifier.border(1.0.dp, appColors.primaryAction, RoundedCornerShape(12.dp))
            else Modifier
        )
        .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nutrition_goals_from, formatDate(goal.effectiveFrom)),
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(appColors.primaryAction)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.nutrition_goals_badge_active),
                            color = appColors.textOnSelected,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            // Macros colorées par macro (code couleur partagé avec les autres pages).
            val sepStyle = SpanStyle(color = appColors.textTertiary)
            val macrosLine = buildAnnotatedString {
                withStyle(SpanStyle(color = macroColor(MacroKey.KCAL))) { append("${goal.kcal.roundToInt()} kcal") }
                withStyle(sepStyle) { append(" · ") }
                withStyle(SpanStyle(color = macroColor(MacroKey.PROTEIN))) { append("P ${goal.proteinG.roundToInt()} g") }
                withStyle(sepStyle) { append(" · ") }
                withStyle(SpanStyle(color = macroColor(MacroKey.CARBS))) { append("C ${goal.carbsG.roundToInt()} g") }
                withStyle(sepStyle) { append(" · ") }
                withStyle(SpanStyle(color = macroColor(MacroKey.FAT))) { append("F ${goal.fatG.roundToInt()} g") }
                withStyle(sepStyle) { append(" · ") }
                withStyle(SpanStyle(color = macroColor(MacroKey.FIBER))) {
                    append("Fib ${(fiberTargetG(goal.kcal) ?: 0f).roundToInt()} g")
                }
            }
            Text(text = macrosLine, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_more_vert,
            boxSize = 34.dp,
            iconSize = 20.dp,
            hasBackground = true,
            onClick = onOptions,
        )
    }
}

/** Une colonne du form : label macro coloré + WheelPicker (g), dans une Row de 3. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MacroWheel(
    key: MacroKey,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(macroLabelRes(key)),
            color = macroColor(key),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        WheelPicker(
            range = range,
            selected = value,
            onSelected = onValue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun radarAxes(goal: NutritionGoal, weekAvg: MacroTotals): List<RadarAxis> {
    fun pct(value: Float, target: Float): Float = if (target > 0f) value / target * 100f else 0f
    return listOf(
        Triple(MacroKey.CARBS, weekAvg.carbs, goal.carbsG),
        Triple(MacroKey.FAT, weekAvg.fat, goal.fatG),
        Triple(MacroKey.PROTEIN, weekAvg.protein, goal.proteinG),
        Triple(MacroKey.FIBER, weekAvg.fiber, fiberTargetG(goal.kcal) ?: 0f),
    ).map { (key, value, target) ->
        RadarAxis(
            label = stringResource(macroLabelRes(key)),
            color = macroColor(key),
            percent = pct(value, target),
        )
    }
}

/** Axes du radar de RÉPARTITION : part en % des calories de chaque macro (= donut). */
@Composable
private fun breakdownRadarAxes(breakdown: List<MacroKcalShare>): List<RadarAxis> =
    breakdown.map { share ->
        RadarAxis(
            label = stringResource(macroLabelRes(share.key)),
            color = macroColor(share.key),
            percent = share.percent,
        )
    }

private fun macroLabelRes(key: MacroKey): Int = when (key) {
    MacroKey.KCAL -> R.string.nutrition_macro_calories
    MacroKey.CARBS -> R.string.nutrition_macro_carbs
    MacroKey.FAT -> R.string.nutrition_macro_fat
    MacroKey.PROTEIN -> R.string.nutrition_macro_protein
    MacroKey.FIBER -> R.string.nutrition_macro_fiber
}

/** "YYYY-MM-DD" -> "DD/MM/YYYY" (miroir formatDate web). */
private fun formatDate(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}

private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f
