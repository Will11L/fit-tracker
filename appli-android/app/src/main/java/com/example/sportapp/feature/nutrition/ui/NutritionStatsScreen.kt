package com.example.sportapp.feature.nutrition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.sportapp.core.stats.ChartType
import com.example.sportapp.core.stats.StatsRange
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.TabRowCustom
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.TopFood
import com.example.sportapp.feature.nutrition.ui.components.AllMacroLine
import com.example.sportapp.feature.nutrition.ui.components.NutritionAllMacrosChart
import com.example.sportapp.feature.nutrition.ui.components.NutritionMacroChart
import com.example.sportapp.feature.stats.ui.components.stats.ChartTypeToggle
import com.example.sportapp.feature.stats.ui.components.stats.CustomRangePickerDialog
import com.example.sportapp.feature.stats.ui.components.stats.RangeChipsRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Index de la sous-section « Toutes les macros » (1re page du pager) ; les macros suivent. */
private const val ALL_TAB_INDEX = 0

/**
 * Stats nutrition (A6) — miroir Android de la page web `/nutrition/stats`, adapté au mobile : au lieu
 * de la grille tout-en-un du web (5 cartes simultanées), un **pager de sous-sections swipeables**
 * (Tout + une macro à la fois) piloté par une barre de **sous-tabs colorés** (couleurs macros) —
 * pattern du hub Santé (tab bar + HorizontalPager synchronisés : tap → anime, swipe → l'onglet suit).
 * Chaque sous-section vit dans un **cadre thirdBlue** unique (titre coloré + graphe + « Top aliments »
 * + rows à plat séparées par un divider interne, comme les listes du web). Couleurs via
 * `macroColor` ; tout texte via strings.xml (politique 18). Widgets période / type de graphe canoniques.
 */
@Composable
fun NutritionStatsScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: NutritionStatsViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val range by viewModel.range.collectAsStateWithLifecycle()
    val chartType by viewModel.chartType.collectAsStateWithLifecycle()
    val macroCards by viewModel.macroCards.collectAsStateWithLifecycle()
    val allChartData by viewModel.allChartData.collectAsStateWithLifecycle()
    val topFoodPerMacro by viewModel.topFoodPerMacro.collectAsStateWithLifecycle()

    var showCustomPicker by remember { mutableStateOf(false) }

    val macros = MacroKey.entries
    // 1 sous-section « Tout » + 1 par macro. tap → anime le pager ; swipe → l'onglet actif suit.
    val pagerState = rememberPagerState(pageCount = { macros.size + 1 })
    val scope = rememberCoroutineScope()
    val onAllPage = pagerState.currentPage == ALL_TAB_INDEX

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {
        ScreenTitleBar(title = stringResource(R.string.nutrition_stats_title))

        // Sous-tabs = vrai TabMenu canonique (TabRowCustom, comme Séance/Objectifs/Programme), mais
        // l'onglet actif prend la couleur de sa section (macro). Synchronisé au pager : tap → anime,
        // swipe → l'onglet suit. SCROLLABLE : onglets à leur largeur naturelle (6 = Tout + 5 macros,
        // débordent hors écran) + auto-scroll de la barre vers l'onglet actif.
        val tabLabels = listOf(stringResource(R.string.nutrition_stats_all_label)) +
            macros.map { stringResource(macroLabelRes(it)) }
        // Couleur active par onglet : Tout = primaire, puis la couleur de chaque macro (résolue ici,
        // en contexte @Composable, car appColors/macroColor ne se lisent pas dans TabRowCustom).
        val tabColors = listOf(appColors.primaryAction) + macros.map { macroColor(it) }
        TabRowCustom(
            items = tabLabels,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            selectedColors = tabColors,
            scrollable = true,
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (page == ALL_TAB_INDEX) {
                        AllSectionFrame(allChartData = allChartData, topFoodPerMacro = topFoodPerMacro)
                    } else {
                        macroCards.getOrNull(page - 1)?.let { card ->
                            MacroSectionFrame(card = card, chartType = chartType)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            // Sur la 1re sous-page : le pager consomme les gestes horizontaux → une fine zone de bord
            // gauche rend au drawer son ouverture au swipe (drag vers la droite). Taps/scroll passent.
            if (onAllPage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(28.dp)
                        .pointerInput(Unit) {
                            var triggered = false
                            detectHorizontalDragGestures(
                                onDragStart = { triggered = false },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (!triggered && dragAmount > 0f) {
                                        triggered = true
                                        scope.launch { drawerState.open() }
                                    }
                                },
                            )
                        },
                )
            }
        }

        // Contrôles partagés ÉPINGLÉS EN BAS (demande user 2026-07-14) : type de graphe (uniquement
        // pour une macro) + période, sous les graphes/listes. La Column vit dans la zone paddée du
        // Scaffold (innerPadding) → la row s'arrête au-dessus de la bottom nav, pas d'inset à gérer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!onAllPage) {
                ChartTypeToggle(current = chartType, onSelect = viewModel::setChartType)
            }
            RangeChipsRow(
                modifier = Modifier.weight(1f),
                range = range,
                onSelect = viewModel::setRange,
                onCustomClick = { showCustomPicker = true },
            )
        }
    }

    if (showCustomPicker) {
        CustomRangePickerDialog(
            initialRange = range,
            onConfirm = { start, end ->
                viewModel.setRange(StatsRange.Custom(start, end))
                showCustomPicker = false
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

// ─── Cadres de section (thirdBlue) ────────────────────────────────────────────────

/** Cadre thirdBlue unique d'une sous-section : titre + graphe + « Top » + liste, tout dedans. */
@Composable
private fun SectionFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgRecessed)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun MacroSectionFrame(card: MacroCardData, chartType: ChartType) {
    val color = macroColor(card.macro)
    val unit = macroUnit(card.macro)
    SectionFrame {
        TitledDivider(title = stringResource(macroLabelRes(card.macro)) + " ($unit)", color = color)
        NutritionMacroChart(
            buckets = card.chart.series.buckets,
            consumed = card.chart.series.consumed,
            target = card.chart.series.target,
            color = color,
            chartType = chartType,
            granularity = card.chart.granularity,
            unit = unit,
        )
        ChartLegend(macroColor = color, hasTarget = card.chart.series.target.any { it > 0f })

        // « Top aliments » (titre coloré) + rows à plat séparées par un divider interne (secondBlue).
        TitledDivider(title = stringResource(R.string.nutrition_stats_top_foods), color = color)
        if (card.topFoods.isEmpty()) {
            EmptyListRow(
                text = stringResource(R.string.nutrition_stats_top_empty),
                iconRes = R.drawable.ic_rounded_local_fire,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                card.topFoods.forEachIndexed { i, food ->
                    TopFoodRow(rank = i + 1, food = food, unit = unit, color = color)
                    if (i < card.topFoods.lastIndex) TopRowDivider()
                }
            }
        }
    }
}

@Composable
private fun AllSectionFrame(
    allChartData: AllMacrosChartData,
    topFoodPerMacro: List<MacroTopFood>,
) {
    // Vue « Tout » = toutes les macros → dividers neutres grayBlue (pas de couleur macro dédiée).
    val color = appColors.divider
    SectionFrame {
        TitledDivider(title = stringResource(R.string.nutrition_stats_all_title), color = color)
        NutritionAllMacrosChart(
            buckets = allChartData.buckets,
            lines = allChartData.lines.map { AllMacroLine(macroColor(it.macro), it.percents) },
            granularity = allChartData.granularity,
        )
        AllMacrosLegend()

        // Top aliment de chaque catégorie (Calories / Glucides / …), à plat + dividers internes.
        TitledDivider(title = stringResource(R.string.nutrition_stats_top_by_category), color = color)
        if (topFoodPerMacro.isEmpty()) {
            EmptyListRow(
                text = stringResource(R.string.nutrition_stats_top_empty),
                iconRes = R.drawable.ic_rounded_local_fire,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                topFoodPerMacro.forEachIndexed { i, item ->
                    CategoryTopFoodRow(
                        macro = item.macro,
                        food = item.food,
                        color = macroColor(item.macro),
                        unit = macroUnit(item.macro),
                    )
                    if (i < topFoodPerMacro.lastIndex) TopRowDivider()
                }
            }
        }
    }
}

/** Filet interne entre 2 rows d'une liste dans le cadre (miroir web `.top__row` border secondBlue). */
@Composable
private fun TopRowDivider() {
    HorizontalDivider(color = secondBlue, thickness = 1.dp)
}

// ─── Légendes ─────────────────────────────────────────────────────────────────────

/** Légende du graphe « Toutes les macros » : un point par macro + la cible (100 %). */
@Composable
private fun AllMacrosLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MacroKey.entries.forEach { key ->
            LegendDot(color = macroColor(key), label = stringResource(macroLabelRes(key)))
        }
        LegendDot(
            color = appColors.textSecondary,
            label = stringResource(R.string.nutrition_stats_legend_target),
        )
    }
}

@Composable
private fun ChartLegend(macroColor: Color, hasTarget: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(color = macroColor, label = stringResource(R.string.nutrition_stats_legend_consumed))
        if (hasTarget) {
            LegendDot(color = appColors.textSecondary, label = stringResource(R.string.nutrition_stats_legend_target))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(
            modifier = Modifier
                .width(12.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(text = label, color = appColors.textSecondary, fontSize = 12.sp)
    }
}

// ─── Rows Top aliments (à plat dans le cadre) ──────────────────────────────────────

@Composable
private fun TopFoodRow(rank: Int, food: TopFood, unit: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$rank",
            color = lightGrayBlue,
            fontSize = 12.sp,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text = food.displayName,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(food.share * 100f).roundToInt()} %",
            color = appColors.textTertiary,
            fontSize = 12.sp,
        )
        Text(
            text = "${formatMacroValue(food.value, unit)} $unit",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

/** Ligne « top aliment d'une catégorie » (page All) : macro coloré · aliment · valeur. */
@Composable
private fun CategoryTopFoodRow(macro: MacroKey, food: TopFood, color: Color, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(macroLabelRes(macro)),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = food.displayName,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatMacroValue(food.value, unit)} $unit",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────────

private fun macroLabelRes(key: MacroKey): Int = when (key) {
    MacroKey.KCAL -> R.string.nutrition_macro_calories
    MacroKey.CARBS -> R.string.nutrition_macro_carbs
    MacroKey.FAT -> R.string.nutrition_macro_fat
    MacroKey.PROTEIN -> R.string.nutrition_macro_protein
    MacroKey.FIBER -> R.string.nutrition_macro_fiber
}

/** Unité d'un macro — symbole universel (kcal / g), non traduit comme les unités micros. */
private fun macroUnit(key: MacroKey): String = if (key == MacroKey.KCAL) "kcal" else "g"

/** kcal en entier, grammes à la décimale (cohérent avec la page Objectifs / le web). */
private fun formatMacroValue(value: Float, unit: String): String =
    if (unit == "kcal") value.roundToInt().toString()
    else ((value * 10f).roundToInt() / 10f).toString()
