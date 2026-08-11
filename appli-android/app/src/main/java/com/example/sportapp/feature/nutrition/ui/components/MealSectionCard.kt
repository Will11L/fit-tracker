package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.firstBlue
import androidx.compose.foundation.clickable
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.feature.nutrition.domain.JournalSection
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.consumedMicroLineItems
import com.example.sportapp.feature.nutrition.domain.consumedSugarG
import com.example.sportapp.feature.nutrition.domain.entryTotals
import com.example.sportapp.feature.nutrition.ui.macroColor
import kotlin.math.roundToInt

/**
 * Card d'une section repas du journal — miroir de l'`ExpandableCard` repas du web :
 * header bord-à-bord firstBlue cliquable (nom · heure · totaux colorés + boutons + chevron
 * de repli), corps repliable animé listant les aliments en [MacroEntryRow]
 * (composant réutilisable) avec filets entre lignes.
 */
@Composable
fun MealSectionCard(
    section: JournalSection,
    onAddFood: () -> Unit,
    onMealOptions: () -> Unit,
    onEntryOptions: (MealEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(section.presetUuid ?: section.name) { mutableStateOf(true) }
    val toggleLabel = stringResource(R.string.nutrition_meal_toggle)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed),
    ) {
        // Header bord-à-bord sur DEUX lignes (écran tel plus étroit que le web) :
        // à gauche nom + heure puis totaux colorés ; à droite le groupe de boutons
        // ([+] primaire · [⋮] firstBlue · chevron), espacés et centrés verticalement.
        // TOUT le header est cliquable pour dérouler/enrouler le repas.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .background(secondBlue)
                // 8 dp vertical : le nom ne colle pas au haut du header (les rows
                // d'aliments, elles, centrent leur contenu — pas ce bloc 2 lignes).
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = section.name,
                        color = appColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // L'heure réelle du repas (meal.time) surclasse le defaultTime du preset (parité web).
                    (section.meal?.time ?: section.defaultTime)?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(text = it, color = appColors.textTertiary, fontSize = 12.sp)
                    }
                }
                if (section.entries.isNotEmpty()) {
                    MealTotalsLine(section, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    boxSize = 34.dp,
                    iconSize = 20.dp,
                    customBackgroundColor = appColors.primaryAction,
                    onClick = onAddFood,
                )
                if (section.meal != null) {
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_more_vert,
                        boxSize = 34.dp,
                        iconSize = 20.dp,
                        customBackgroundColor = firstBlue,
                        onClick = onMealOptions,
                    )
                }
                ActionIconButton(
                    iconRes = if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down,
                    boxSize = 34.dp,
                    iconSize = 20.dp,
                    tint = appColors.primaryAction,
                    hasBackground = false,
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics { contentDescription = toggleLabel },
                )
            }
        }

        // Corps repliable : aliments (MacroEntryRow réutilisable) ou état vide.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                if (section.entries.isEmpty()) {
                    EmptyListRow(text = stringResource(R.string.nutrition_meal_empty), verticalPadding = 0.dp)
                } else {
                    section.entries.forEachIndexed { index, entry ->
                        val t = entryTotals(entry)
                        MacroEntryRow(
                            data = MacroEntryRowData(
                                name = entry.displayName,
                                kcal = t.kcal,
                                carbs = t.carbs,
                                fat = t.fat,
                                protein = t.protein,
                                fiber = entry.fiberPer100g?.let { t.fiber },
                                micros = entry.consumedMicroLineItems(),
                                // Sucres consommés (échelle quantité) en tête du dépli micros.
                                sugarG = entry.consumedSugarG(),
                            ),
                            divider = index != section.entries.lastIndex,
                            trailing = {
                                // Grammes effectifs seuls, centrés verticalement avant le ⋮
                                // (le libellé de portion serait redondant, cf. Functional
                                // review 2026-07-05).
                                Text(
                                    text = "${entry.quantityG.roundToInt()} g",
                                    color = appColors.textTertiary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                                ActionIconButton(
                                    iconRes = R.drawable.ic_rounded_more_vert,
                                    boxSize = 34.dp,
                                    iconSize = 20.dp,
                                    customBackgroundColor = firstBlue,
                                    onClick = { onEntryOptions(entry) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Totaux du repas colorés par macro (header) : « 540 kcal · G 45 · L 12 · P 30 · F 4 ». */
@Composable
private fun MealTotalsLine(section: JournalSection, modifier: Modifier = Modifier) {
    val t = section.totals
    val cShort = stringResource(R.string.nutrition_short_carbs)
    val fShort = stringResource(R.string.nutrition_short_fat)
    val pShort = stringResource(R.string.nutrition_short_protein)
    val fiShort = stringResource(R.string.nutrition_short_fiber)
    Text(
        text = buildAnnotatedString {
            appendMacro("${t.kcal.roundToInt()} kcal", MacroKey.KCAL)
            append(" · ")
            appendMacro("$cShort ${round1(t.carbs)}", MacroKey.CARBS)
            append(" · ")
            appendMacro("$fShort ${round1(t.fat)}", MacroKey.FAT)
            append(" · ")
            appendMacro("$pShort ${round1(t.protein)}", MacroKey.PROTEIN)
            append(" · ")
            appendMacro("$fiShort ${round1(t.fiber)}", MacroKey.FIBER)
        },
        color = GrayBlue,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMacro(text: String, key: MacroKey) {
    withStyle(SpanStyle(color = macroColor(key))) { append(text) }
}

private fun round1(v: Float): String = ((v * 10).roundToInt() / 10f).toString()
