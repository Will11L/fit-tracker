package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.sportapp.core.data.model.NutritionGoal
import com.example.sportapp.designsystem.common_components.AnimatedModeContent
import com.example.sportapp.designsystem.common_components.ProgressBarPrimitive
import com.example.sportapp.designsystem.common_components.ProgressRing
import com.example.sportapp.designsystem.common_components.swipeToSwitch
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.MacroTotals
import com.example.sportapp.feature.nutrition.domain.MicroKey
import com.example.sportapp.feature.nutrition.domain.MicroRow
import com.example.sportapp.feature.nutrition.domain.fiberTargetG
import com.example.sportapp.feature.nutrition.domain.sugarLimitsG
import com.example.sportapp.feature.nutrition.ui.macroColor
import com.example.sportapp.feature.nutrition.ui.microColor
import com.example.sportapp.feature.nutrition.ui.sugarColor
import kotlin.math.roundToInt

/** Ordre VISUEL des modes (= ordre du toggle), partagé swipe + transition animée. */
private val SUMMARY_VIEW_ORDER = listOf(SummaryView.RINGS, SummaryView.BARS, SummaryView.RADAR)

private data class BannerRow(
    val label: String,
    val color: Color,
    val progress: Float,
    val valueText: String,
    val targetText: String,
    /** Repère vertical optionnel sur la barre (0..1) — seuil « idéal » des sucres. */
    val markerAt: Float? = null,
)

/**
 * Bandeau « cumuls du jour vs cibles actives » : macros (Calories / Glucides /
 * Lipides / Protéines / Fibres) + ligne Sucres (PLAFOND OMS < 10 % de l'AET,
 * repère « idéal » 5 %, alerte si dépassé — pattern plafond Sodium) + section
 * micros repliable (10 vitamines/minéraux vs VNR UE, Sodium en plafond). Chaque
 * section a son propre sélecteur de vue (barres / anneaux / radar,
 * [SummaryViewToggle]) — la vue radar donne un aperçu de l'équilibre
 * macros/micros d'un coup d'œil (miroir du radar nutrition web ; sucres exclus
 * du radar, comme au web).
 */
@Composable
fun DaySummaryBanner(
    totals: MacroTotals,
    goal: NutritionGoal?,
    sugarG: Float,
    micros: List<MicroRow>,
    showMicros: Boolean,
    onToggleMicros: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sélecteur UNIQUE (parité web) : le mode choisi s'applique aux macros ET aux micros.
    // Défaut = anneaux (parité web readSectionView, demande user 2026-07-14).
    var view by remember { mutableStateOf(SummaryView.RINGS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // Swipe gauche/droite = mode suivant/précédent (ordre visuel du toggle).
            .swipeToSwitch(values = SUMMARY_VIEW_ORDER, current = view) { view = it },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.nutrition_macros_header),
            view = view,
            onSelectView = { view = it },
        )

        // Macros + ligne Sucres (plafond OMS) — les sucres restent hors du radar.
        // Transition animée entre modes (swipe ou tap) : glisse dans le sens de navigation.
        val rows = macroRows(totals, goal) + sugarRow(sugarG, goal)
        AnimatedModeContent(current = view, values = SUMMARY_VIEW_ORDER) { v ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (v) {
            SummaryView.BARS -> rows.forEach { row -> BannerBarRow(row) }
            // 6 anneaux (5 macros + sucres), valeur au centre, cible en sous-titre,
            // libellé coloré dessous. 2 lignes de 3 (demande user 2026-07-14 : 6 sur
            // une ligne trop serré sur tel), anneaux agrandis 76dp + inter-lignes 16dp
            // pour un espacement visuel proche entre voisins horizontaux et verticaux.
            SummaryView.RINGS -> Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rows.chunked(3).forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        line.forEach { row ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                ProgressRing(
                                    progress = row.progress,
                                    color = row.color,
                                    label = row.valueText,
                                    sublabel = row.targetText,
                                    size = 76.dp,
                                    strokeWidth = 7.dp,
                                )
                                Text(
                                    text = row.label,
                                    color = row.color,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            SummaryView.RADAR -> if (goal != null) {
                MacroRadarChart(
                    axes = macroRadarAxes(totals, goal),
                    actualLabel = stringResource(R.string.nutrition_summary_radar_today),
                    targetLabel = stringResource(R.string.nutrition_summary_radar_target),
                )
            }
            }
            }
        }

        if (goal == null) {
            Text(
                text = stringResource(R.string.nutrition_no_goal_hint),
                color = appColors.divider,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleMicros)
                .padding(vertical = 2.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (showMicros) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
                ),
                contentDescription = null,
                tint = appColors.accentText,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (showMicros) R.string.nutrition_hide_micros else R.string.nutrition_show_micros
                ),
                color = appColors.accentText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (showMicros) {
            // Le sélecteur des macros pilote aussi cette section (pas de toggle ici).
            SectionHeader(title = stringResource(R.string.nutrition_micros_header))
            AnimatedModeContent(current = view, values = SUMMARY_VIEW_ORDER) { v ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (v) {
                SummaryView.BARS -> micros.forEach { mr -> MicroBarRow(mr) }
                // Un anneau par micronutriment (2 lignes de 5), même langage que les macros.
                SummaryView.RINGS -> micros.chunked(5).forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        line.forEach { mr -> MicroRingCell(mr, Modifier.weight(1f)) }
                        repeat(5 - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                SummaryView.RADAR -> MacroRadarChart(
                    axes = microRadarAxes(micros),
                    actualLabel = stringResource(R.string.nutrition_summary_radar_today),
                    targetLabel = stringResource(R.string.nutrition_summary_radar_nrv),
                )
                }
                }
            }
            Text(
                text = stringResource(R.string.nutrition_micros_note),
                color = appColors.divider,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Note plafond sucres : sous la note VNR, même style (parité web, demande user 2026-07-14).
            Text(
                text = stringResource(R.string.nutrition_sugar_note),
                color = appColors.divider,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

/** En-tête d'une section du bandeau : titre à gauche, sélecteur de vue optionnel à
 *  droite (le sélecteur des macros pilote toutes les sections — parité web). */
@Composable
private fun SectionHeader(
    title: String,
    view: SummaryView? = null,
    onSelectView: (SummaryView) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        view?.let { SummaryViewToggle(current = it, onSelect = onSelectView) }
    }
}

/** Cellule anneau d'un micronutriment : anneau (valeur + cible) + libellé coloré. */
@Composable
private fun MicroRingCell(mr: MicroRow, modifier: Modifier = Modifier) {
    val color = if (mr.exceeded) appColors.snackbarWarning else microColor(mr.key.family)
    val sep = if (mr.key.isLimit) "≤" else "/"
    val valueText = ((mr.value * 10).roundToInt() / 10f).toString()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        ProgressRing(
            progress = mr.progress,
            color = color,
            label = valueText,
            sublabel = "$sep ${mr.key.target.roundToInt()} ${mr.key.unit.symbol}",
        )
        Text(
            text = microLabel(mr.key),
            color = color,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BannerBarRow(row: BannerRow) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.label,
            color = row.color,
            fontSize = 13.sp,
            modifier = Modifier.width(76.dp),
        )
        ProgressBarPrimitive(
            progress = row.progress,
            color = row.color,
            troughColor = appColors.bgSurface,
            markerAt = row.markerAt,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${row.valueText} ${row.targetText}",
            color = appColors.textPrimary,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(120.dp).padding(start = 4.dp),
        )
    }
}

@Composable
internal fun MicroBarRow(mr: MicroRow) {
    val color = if (mr.exceeded) appColors.snackbarWarning else microColor(mr.key.family)
    val sep = if (mr.key.isLimit) "≤" else "/"
    val valueText = (mr.value * 10).roundToInt() / 10f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = microLabel(mr.key),
            color = color,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.width(88.dp),
        )
        ProgressBarPrimitive(
            progress = mr.progress,
            color = color,
            troughColor = appColors.bgSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$valueText $sep ${mr.key.target.roundToInt()} ${mr.key.unit.symbol}",
            color = appColors.textPrimary,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(120.dp).padding(start = 4.dp),
        )
    }
}

@Composable
private fun macroRows(totals: MacroTotals, goal: NutritionGoal?): List<BannerRow> {
    val fiberTarget = fiberTargetG(goal?.kcal)
    val specs = listOf(
        Triple(MacroKey.KCAL, totals.kcal, goal?.kcal),
        Triple(MacroKey.CARBS, totals.carbs, goal?.carbsG),
        Triple(MacroKey.FAT, totals.fat, goal?.fatG),
        Triple(MacroKey.PROTEIN, totals.protein, goal?.proteinG),
        Triple(MacroKey.FIBER, totals.fiber, fiberTarget),
    )
    return specs.map { (key, value, target) ->
        val unit = if (key == MacroKey.KCAL) "kcal" else "g"
        BannerRow(
            label = macroLabel(key),
            color = macroColor(key),
            progress = if (target != null && target > 0f) (value / target).coerceIn(0f, 1f) else 0f,
            valueText = value.roundToInt().toString(),
            targetText = if (target != null) "/ ${target.roundToInt()} $unit" else unit,
        )
    }
}

/**
 * Ligne Sucres du bandeau : un PLAFOND (5 % de la cible kcal en g, max 100 g —
 * cf. [sugarLimitsG], fallback 2000 → 100 g), pas une cible à remplir — affiché
 * « ≤ limite », repère « idéal » à la moitié sur la barre, couleur d'alerte si
 * dépassé (pattern plafond Sodium des micros). Miroir de la ligne Sucres des
 * `bannerRows` web.
 */
@Composable
private fun sugarRow(sugarG: Float, goal: NutritionGoal?): BannerRow {
    val (limitG, idealG) = sugarLimitsG(goal?.kcal)
    val exceeded = sugarG > limitG
    return BannerRow(
        label = stringResource(R.string.nutrition_macro_sugar),
        // Alerte orange en dépassement (comme le plafond Sodium), framboise sinon.
        color = if (exceeded) appColors.snackbarWarning else sugarColor,
        progress = (sugarG / limitG).coerceIn(0f, 1f),
        valueText = sugarG.roundToInt().toString(),
        targetText = "≤ ${limitG.roundToInt()} g",
        markerAt = idealG / limitG,
    )
}

/**
 * Axes du radar macros : un axe par macro (Protéines / Glucides / Lipides / Fibres),
 * valeur = % de la cible active. La cible fibres est dérivée du kcal de l'objectif.
 */
@Composable
private fun macroRadarAxes(totals: MacroTotals, goal: NutritionGoal): List<RadarAxis> {
    val fiberTarget = fiberTargetG(goal.kcal) ?: 0f
    val specs = listOf(
        Triple(MacroKey.PROTEIN, totals.protein, goal.proteinG),
        Triple(MacroKey.CARBS, totals.carbs, goal.carbsG),
        Triple(MacroKey.FAT, totals.fat, goal.fatG),
        Triple(MacroKey.FIBER, totals.fiber, fiberTarget),
    )
    return specs.map { (key, value, target) ->
        RadarAxis(
            label = macroLabel(key),
            color = macroColor(key),
            percent = if (target > 0f) value / target * 100f else 0f,
        )
    }
}

/**
 * Axes du radar micros : un axe par micronutriment suivi, valeur = % de la VNR
 * (ou du plafond pour le Sodium). Teinte par famille, alerte si dépassement.
 */
@Composable
internal fun microRadarAxes(micros: List<MicroRow>): List<RadarAxis> = micros.map { mr ->
    RadarAxis(
        label = microLabel(mr.key),
        color = if (mr.exceeded) appColors.snackbarWarning else microColor(mr.key.family),
        percent = if (mr.key.target > 0f) mr.value / mr.key.target * 100f else 0f,
    )
}

@Composable
private fun macroLabel(key: MacroKey): String = stringResource(
    when (key) {
        MacroKey.KCAL -> R.string.nutrition_macro_calories
        MacroKey.CARBS -> R.string.nutrition_macro_carbs
        MacroKey.FAT -> R.string.nutrition_macro_fat
        MacroKey.PROTEIN -> R.string.nutrition_macro_protein
        MacroKey.FIBER -> R.string.nutrition_macro_fiber
    }
)

@Composable
private fun microLabel(key: MicroKey): String = stringResource(
    when (key) {
        MicroKey.IRON -> R.string.nutrition_micro_iron
        MicroKey.CALCIUM -> R.string.nutrition_micro_calcium
        MicroKey.MAGNESIUM -> R.string.nutrition_micro_magnesium
        MicroKey.ZINC -> R.string.nutrition_micro_zinc
        MicroKey.POTASSIUM -> R.string.nutrition_micro_potassium
        MicroKey.SODIUM -> R.string.nutrition_micro_sodium
        MicroKey.VITAMIN_C -> R.string.nutrition_micro_vitamin_c
        MicroKey.VITAMIN_D -> R.string.nutrition_micro_vitamin_d
        MicroKey.VITAMIN_B12 -> R.string.nutrition_micro_vitamin_b12
        MicroKey.VITAMIN_A -> R.string.nutrition_micro_vitamin_a
    }
)
