package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.PastMeal
import com.example.sportapp.feature.nutrition.ui.macroColor
import kotlin.math.roundToInt

/** Sheet « Dupliquer un repas passé » : liste des repas non vides des jours antérieurs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateMealSheet(
    pastMeals: List<PastMeal>,
    onDuplicate: (Meal) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TitledDivider(title = stringResource(R.string.nutrition_duplicate_title))
            Spacer(Modifier.height(8.dp))
            if (pastMeals.isEmpty()) {
                EmptyListRow(text = stringResource(R.string.nutrition_duplicate_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(pastMeals, key = { it.meal.uuid }) { pm ->
                        DuplicateRow(pm = pm, onClick = { onDuplicate(pm.meal) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Row d'un repas passé (retours user 2026-07-14) : nom + date (GrayBlue) à gauche ·
 * au centre macros colorées sur DEUX lignes (kcal · glucides / lipides · protéines ·
 * fibres) · bouton dupliquer sur fond firstBlue à droite — SEUL élément cliquable.
 */
@Composable
private fun DuplicateRow(pm: PastMeal, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appColors.bgRecessed)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = pm.meal.name,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatShortDate(pm.meal.date),
                color = GrayBlue,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sep = SpanStyle(color = appColors.textTertiary)
            // Shorts résolus HORS du builder (stringResource est @Composable).
            val cShort = stringResource(R.string.nutrition_short_carbs)
            val fShort = stringResource(R.string.nutrition_short_fat)
            val pShort = stringResource(R.string.nutrition_short_protein)
            val fbShort = stringResource(R.string.nutrition_short_fiber)
            // Haut : les 4 macros grammes colorées ; bas : kcal + « x aliments » (GrayBlue).
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = macroColor(MacroKey.CARBS))) { append("$cShort ${round1(pm.totals.carbs)}") }
                    withStyle(sep) { append(" · ") }
                    withStyle(SpanStyle(color = macroColor(MacroKey.FAT))) { append("$fShort ${round1(pm.totals.fat)}") }
                    withStyle(sep) { append(" · ") }
                    withStyle(SpanStyle(color = macroColor(MacroKey.PROTEIN))) { append("$pShort ${round1(pm.totals.protein)}") }
                    withStyle(sep) { append(" · ") }
                    withStyle(SpanStyle(color = macroColor(MacroKey.FIBER))) { append("$fbShort ${round1(pm.totals.fiber)}") }
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val countText = stringResource(R.string.nutrition_duplicate_count, pm.entryCount)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = macroColor(MacroKey.KCAL))) { append("${pm.totals.kcal.roundToInt()} kcal") }
                    withStyle(sep) { append(" · ") }
                    withStyle(SpanStyle(color = GrayBlue)) { append(countText) }
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_content_copy,
            iconSize = 20.dp,
            boxSize = 34.dp,
            customBackgroundColor = firstBlue,
            onClick = onClick,
            contentDescription = stringResource(R.string.nutrition_duplicate_short),
        )
    }
}

/** Grammes à 1 décimale (miroir round1 web). */
private fun round1(v: Float): String = ((v * 10).roundToInt() / 10f).toString()

/** "YYYY-MM-DD" -> "DD/MM/YYYY". */
private fun formatShortDate(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}
