package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.MicroLineItem
import com.example.sportapp.feature.nutrition.ui.macroColor
import com.example.sportapp.feature.nutrition.ui.microColor
import com.example.sportapp.feature.nutrition.ui.sugarColor
import kotlin.math.roundToInt

/**
 * Données d'une ligne aliment / ingrédient : nom + macros colorées + micros
 * présents. Forme découplée du modèle (MealEntry, ingrédient de recette…) — le
 * parent construit la vue, le composant ne fait que l'afficher (miroir du
 * `MacroEntryRow` web, réutilisable cards repas / recettes).
 */
data class MacroEntryRowData(
    val name: String,
    val kcal: Float,
    val carbs: Float,
    val fat: Float,
    val protein: Float,
    /** null = fibres inconnues → la part « F » est masquée. */
    val fiber: Float?,
    /** Micros présents (déjà mis à l'échelle) ; vide → pas de chevron ni de dépli. */
    val micros: List<MicroLineItem> = emptyList(),
    /** Sucres consommés (g, à l'échelle de la quantité) → 1ʳᵉ valeur du dépli micros. null = masqué. */
    val sugarG: Float? = null,
)

/**
 * Ligne aliment / ingrédient réutilisable (miroir du composant web) : nom +
 * macros colorées (kcal · G · L · P · F), slot [trailing] (grammes, menu ⋮…) et
 * chevron qui déroule les micros consommés (dépli interne, animé). [divider] =
 * filet secondBlue sous la ligne (à désactiver sur la dernière).
 */
@Composable
fun MacroEntryRow(
    data: MacroEntryRowData,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    /** Calé à droite du NOM (1ʳᵉ ligne) — ex. les grammes (écran étroit). */
    nameTrailing: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    // Chevron + dépli présents si la ligne a des micros OU des sucres consommés à montrer.
    val hasDetails = data.micros.isNotEmpty() || data.sugarG != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = data.name,
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    nameTrailing()
                }
                // Chevron des micros collé aux macros (compact), comme la row du Catalogue.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MacroLine(data, modifier = Modifier.weight(1f, fill = false))
                    if (hasDetails) {
                        ActionIconButton(
                            iconRes = if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down,
                            boxSize = 26.dp,
                            iconSize = 20.dp,
                            tint = appColors.primaryAction,
                            hasBackground = false,
                            onClick = { expanded = !expanded },
                        )
                    }
                }
            }
            trailing()
        }
        if (hasDetails) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                MicroLine(
                    sugarG = data.sugarG,
                    micros = data.micros,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        if (divider) {
            HorizontalDivider(color = secondBlue, thickness = 1.dp)
        }
    }
}

/** Macros colorées « kcal · G x · L x · P x [· F x] » — séparateurs en GrayBlue. */
@Composable
private fun MacroLine(data: MacroEntryRowData, modifier: Modifier = Modifier) {
    val cShort = stringResource(R.string.nutrition_short_carbs)
    val fShort = stringResource(R.string.nutrition_short_fat)
    val pShort = stringResource(R.string.nutrition_short_protein)
    val fiShort = stringResource(R.string.nutrition_short_fiber)
    Text(
        text = buildAnnotatedString {
            appendMacro("${data.kcal.roundToInt()} kcal", MacroKey.KCAL)
            append(" · ")
            appendMacro("$cShort ${round1(data.carbs)}", MacroKey.CARBS)
            append(" · ")
            appendMacro("$fShort ${round1(data.fat)}", MacroKey.FAT)
            append(" · ")
            appendMacro("$pShort ${round1(data.protein)}", MacroKey.PROTEIN)
            data.fiber?.let {
                append(" · ")
                appendMacro("$fiShort ${round1(it)}", MacroKey.FIBER)
            }
        },
        color = GrayBlue,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}

/** Micros consommés colorés par famille — précédés des sucres consommés (teinte dédiée) si connus :
 *  « Sucres 12,4 g · Fe 2,1 mg · Vit C 12 mg… ». */
@Composable
private fun MicroLine(sugarG: Float?, micros: List<MicroLineItem>, modifier: Modifier = Modifier) {
    val sugarLabel = stringResource(R.string.nutrition_macro_sugar)
    Text(
        text = buildAnnotatedString {
            sugarG?.let {
                withStyle(SpanStyle(color = sugarColor)) {
                    append("$sugarLabel ${round1(it)} g")
                }
                if (micros.isNotEmpty()) append(" · ")
            }
            micros.forEachIndexed { index, mi ->
                withStyle(SpanStyle(color = microColor(mi.family))) {
                    append("${mi.short} ${mi.value} ${mi.unit}")
                }
                if (index != micros.lastIndex) append(" · ")
            }
        },
        color = GrayBlue,
        fontSize = 11.sp,
        modifier = modifier,
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMacro(text: String, key: MacroKey) {
    withStyle(SpanStyle(color = macroColor(key))) { append(text) }
}

private fun round1(v: Float): String = ((v * 10).roundToInt() / 10f).toString()
