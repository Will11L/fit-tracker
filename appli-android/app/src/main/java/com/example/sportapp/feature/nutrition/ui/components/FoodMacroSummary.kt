package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Radar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.designsystem.common_components.AnimatedModeContent
import com.example.sportapp.designsystem.common_components.ProgressBarPrimitive
import com.example.sportapp.designsystem.common_components.SegmentItem
import com.example.sportapp.designsystem.common_components.SegmentedIconToggle
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.swipeToSwitch
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.nutrition.domain.KCAL_MACRO_KEYS
import com.example.sportapp.feature.nutrition.domain.MacroKcalShare
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.MicroKey
import com.example.sportapp.feature.nutrition.domain.MicroRow
import com.example.sportapp.feature.nutrition.domain.effectiveFoodKcal
import com.example.sportapp.feature.nutrition.domain.microPer100g
import com.example.sportapp.feature.nutrition.ui.macroColor
import com.example.sportapp.feature.nutrition.ui.sugarColor
import kotlin.math.roundToInt

/** Mode du résumé visuel macros + micros (per-100 g) : barres ou radar. */
enum class FoodSummaryView { BARS, RADAR }

/** Ordre VISUEL des modes (= ordre du toggle), partagé swipe + transition animée. */
private val FOOD_SUMMARY_VIEW_ORDER = listOf(FoodSummaryView.BARS, FoodSummaryView.RADAR)

/**
 * Résumé visuel per-100 g d'un aliment — toggle Barres/Radar + macros (4 barres
 * relatives au plus grand macro, ou radar du profil calorique) + micros présents
 * (barres vs VNR, ou radar % VNR par famille). Composant partagé par l'écran
 * Détail aliment et le dialog d'ajout de quantité du journal (parité).
 * Swipe horizontal sur le cadre = mode suivant/précédent (transition animée).
 */
@Composable
fun FoodMacroSummary(food: Food, modifier: Modifier = Modifier) {
    var summaryView by remember { mutableStateOf(FoodSummaryView.BARS) }
    val microRowsList = foodMicroRows(food)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Titre « Pour 100 g » + toggle Barres/Radar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TitledDivider(title = stringResource(R.string.nutrition_food_per_100g))
            }
            Spacer(Modifier.width(8.dp))
            SegmentedIconToggle(
                items = listOf(
                    SegmentItem(FoodSummaryView.BARS, Icons.Filled.BarChart, stringResource(R.string.nutrition_summary_view_bars)),
                    SegmentItem(FoodSummaryView.RADAR, Icons.Filled.Radar, stringResource(R.string.nutrition_summary_view_radar)),
                ),
                selected = summaryView,
                onSelect = { summaryView = it },
                width = 34.dp,
                iconSize = 16.dp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(appColors.bgRecessed)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .swipeToSwitch(values = FOOD_SUMMARY_VIEW_ORDER, current = summaryView) { summaryView = it },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // En-tête macros : sous-titre « Macros » à gauche, kcal /100 g à droite.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nutrition_macros_header),
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rounded_local_fire),
                        contentDescription = null,
                        tint = macroColor(MacroKey.KCAL),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.nutrition_food_kcal_value, effectiveFoodKcal(food).roundToInt()),
                        color = macroColor(MacroKey.KCAL),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.nutrition_food_per_100g_unit),
                        color = appColors.textTertiary,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
            // Macros (4 barres relatives au plus grand macro, ou radar du profil).
            // Transition animée entre modes (swipe ou tap).
            AnimatedModeContent(current = summaryView, values = FOOD_SUMMARY_VIEW_ORDER) { v ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (v) {
                        FoodSummaryView.BARS -> MacroBars(food)
                        FoodSummaryView.RADAR -> {
                            val shares = foodMacroShares(food)
                            val maxShare = shares.maxOfOrNull { it.percent }?.coerceAtLeast(1f) ?: 100f
                            MacroRadarChart(
                                axes = shares.map {
                                    RadarAxis(stringResource(macroLabelRes(it.key)), macroColor(it.key), it.percent)
                                },
                                showLegend = false,
                                referencePercent = maxShare,
                                maxPercent = maxShare * 1.25f,
                            )
                        }
                    }
                }
            }
            // Micros présents : barres vs VNR, ou radar (% VNR par famille).
            if (microRowsList.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.nutrition_micros_header),
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AnimatedModeContent(current = summaryView, values = FOOD_SUMMARY_VIEW_ORDER) { v ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (v) {
                            FoodSummaryView.BARS -> microRowsList.forEach { MicroBarRow(it) }
                            FoodSummaryView.RADAR -> MacroRadarChart(
                                axes = microRadarAxes(microRowsList),
                                showLegend = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Parts caloriques par macro d'un aliment (per-100 g), facteurs Atwater 4·G + 9·L + 4·P + 2·fibres. */
private fun foodMacroShares(food: Food): List<MacroKcalShare> {
    val grams = mapOf(
        MacroKey.CARBS to food.carbsPer100g,
        MacroKey.FAT to food.fatPer100g,
        MacroKey.PROTEIN to food.proteinPer100g,
        MacroKey.FIBER to (food.fiberPer100g ?: 0f),
    )
    val factor = mapOf(MacroKey.CARBS to 4f, MacroKey.FAT to 9f, MacroKey.PROTEIN to 4f, MacroKey.FIBER to 2f)
    val kcalByKey = KCAL_MACRO_KEYS.associateWith { (grams[it] ?: 0f) * (factor[it] ?: 0f) }
    val total = kcalByKey.values.sum().coerceAtLeast(1f)
    return KCAL_MACRO_KEYS.map { MacroKcalShare(it, kcalByKey[it] ?: 0f, (kcalByKey[it] ?: 0f) / total * 100f) }
}

private fun macroLabelRes(key: MacroKey): Int = when (key) {
    MacroKey.KCAL -> R.string.nutrition_macro_calories
    MacroKey.CARBS -> R.string.nutrition_macro_carbs
    MacroKey.FAT -> R.string.nutrition_macro_fat
    MacroKey.PROTEIN -> R.string.nutrition_macro_protein
    MacroKey.FIBER -> R.string.nutrition_macro_fiber
}

/** 4 barres macros (G/L/P/Fibres) per-100 g, longueur relative au plus grand macro (pas d'objectif). */
@Composable
private fun MacroBars(food: Food) {
    val rows = listOf(
        MacroKey.CARBS to food.carbsPer100g,
        MacroKey.FAT to food.fatPer100g,
        MacroKey.PROTEIN to food.proteinPer100g,
        MacroKey.FIBER to (food.fiberPer100g ?: 0f),
    )
    val maxValue = rows.maxOf { it.second }.coerceAtLeast(1f)
    rows.forEach { (key, value) ->
        MacroBarRow(
            label = stringResource(macroLabelRes(key)),
            color = macroColor(key),
            progress = (value / maxValue).coerceIn(0f, 1f),
            value = value,
        )
    }
    // Sucres (information per-100 g, teinte dédiée) : sous les fibres, mode barres SEULEMENT —
    // jamais dans le radar (plafond ≠ cible, cohérent bandeau Journal). Masqué si non renseignés.
    food.sugarPer100g?.let { sugar ->
        MacroBarRow(
            label = stringResource(R.string.nutrition_macro_sugar),
            color = sugarColor,
            progress = (sugar / maxValue).coerceIn(0f, 1f),
            value = sugar,
        )
    }
}

/** Une ligne barre du résumé (libellé coloré · barre · valeur en g), partagée macros + sucres. */
@Composable
private fun MacroBarRow(label: String, color: Color, progress: Float, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
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
            text = "${value.roundToInt()} g",
            color = appColors.textPrimary,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(56.dp).padding(start = 4.dp),
        )
    }
}

/** Lignes micros présents (per-100 g) vs VNR / plafond, pour les barres / le radar du résumé. */
private fun foodMicroRows(food: Food): List<MicroRow> =
    MicroKey.entries.mapNotNull { key ->
        val v = food.microPer100g(key) ?: return@mapNotNull null
        val ratio = if (key.target > 0f) v / key.target else 0f
        MicroRow(key = key, value = v, progress = ratio.coerceIn(0f, 1f), exceeded = key.isLimit && v > key.target)
    }
