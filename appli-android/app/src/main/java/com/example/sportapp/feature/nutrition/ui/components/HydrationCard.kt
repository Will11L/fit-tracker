package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionTextButton
import com.example.sportapp.designsystem.common_components.LabeledProgressBar
import com.example.sportapp.designsystem.theme.ButtonPrimaryColor
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.designsystem.theme.thirdBlue

/** Presets d'ajout rapide (ml). 1 L = chip dédiée. */
private val QUICK_ADD_ML = listOf(250, 500)
private const val ONE_LITER_ML = 1000

/** Texte des chips légèrement réduit (un cran sous le 14sp par défaut) pour tenir à 4 côte à côte. */
private val CHIP_FONT_SIZE = 13.sp

/**
 * Card Hydratation du Journal nutrition (2026-07-05), au-dessus des repas, suit le
 * jour sélectionné. Barre de progression au thème app (LabeledProgressBar) `X / Y L`,
 * chips d'ajout rapide 250 / 500 ml + 1 L + saisie perso, et annulation de la
 * dernière prise manuelle (icône undo discrète). Tout le texte vient de strings.xml
 * (politique 18). Palette : card thirdBlue, trough secondBlue, chips firstBlue,
 * icône/accent en bleu primaire.
 */
@Composable
fun HydrationCard(
    consumedMl: Int,
    goalMl: Int?,
    canUndo: Boolean,
    onAdd: (Int) -> Unit,
    onCustom: () -> Unit,
    onUndo: () -> Unit,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (goalMl != null && goalMl > 0) consumedMl.toFloat() / goalMl else 0f
    val consumedL = formatLiters(consumedMl)
    val amountText = if (goalMl != null && goalMl > 0)
        stringResource(R.string.nutrition_hydration_amount, consumedL, formatLiters(goalMl))
    else
        stringResource(R.string.nutrition_hydration_amount_no_goal, consumedL)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(thirdBlue)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_rounded_water_drop),
                contentDescription = null,
                tint = appColors.primaryAction,
                modifier = Modifier.height(20.dp),
            )
            // weight(1f) + maxLines=1 : le titre garde toute la largeur restante et
            // ne se compresse jamais en vertical quand l'icône undo apparaît.
            Text(
                text = stringResource(R.string.nutrition_hydration_title),
                color = appColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            // Bouton ✎ : règle l'objectif quotidien (dialog géré par l'écran). Taille fixe.
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_edit,
                tint = appColors.primaryAction,
                iconSize = 18.dp,
                boxSize = 32.dp,
                hasBackground = false,
                onClick = onEditGoal,
                contentDescription = stringResource(R.string.nutrition_goals_hydration_dialog_title),
            )
            if (canUndo) {
                ActionIconButton(
                    iconRes = R.drawable.ic_rounded_undo,
                    tint = appColors.primaryAction,
                    iconSize = 18.dp,
                    boxSize = 32.dp,
                    hasBackground = false,
                    onClick = onUndo,
                    contentDescription = stringResource(R.string.nutrition_hydration_undo),
                )
            }
        }

        LabeledProgressBar(
            progress = progress,
            showPercent = false,
            troughColor = secondBlue,
            fillColor = hydrationFillColor(progress),
            rightContent = {
                Text(
                    text = amountText,
                    color = appColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            },
        )

        if (goalMl == null) {
            Text(
                text = stringResource(R.string.nutrition_hydration_no_goal),
                color = appColors.textTertiary,
                fontSize = 11.sp,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QUICK_ADD_ML.forEach { ml ->
                ActionTextButton(
                    text = stringResource(R.string.nutrition_hydration_add_ml, ml),
                    hasBackground = true,
                    backgroundColor = firstBlue,
                    onClick = { onAdd(ml) },
                    fontSize = CHIP_FONT_SIZE,
                    modifier = Modifier.weight(1f),
                )
            }
            ActionTextButton(
                text = stringResource(R.string.nutrition_hydration_add_liter),
                hasBackground = true,
                backgroundColor = firstBlue,
                onClick = { onAdd(ONE_LITER_ML) },
                fontSize = CHIP_FONT_SIZE,
                modifier = Modifier.weight(1f),
            )
            ActionTextButton(
                text = stringResource(R.string.nutrition_hydration_custom),
                hasBackground = true,
                backgroundColor = appColors.primaryAction,
                onClick = onCustom,
                fontSize = CHIP_FONT_SIZE,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Couleur de remplissage : dégradé de bleus du foncé (faible %) vers le clair
 * (proche/atteint 100 %), lisible sur le trough secondBlue. Au-delà de 100 %, la
 * fraction est bornée → reste sur le bleu clair final (pas de nouvelle teinte).
 */
private fun hydrationFillColor(progress: Float): Color =
    lerp(firstBlue, ButtonPrimaryColor, progress.coerceIn(0f, 1f))

/** ml → litres compacts (« 1,25 » sans zéros superflus, « 2 » si entier). */
private fun formatLiters(ml: Int): String {
    val liters = ml / 1000f
    if (liters % 1f == 0f) return liters.toInt().toString()
    val rounded = Math.round(liters * 100f) / 100f
    return rounded.toString()
}
