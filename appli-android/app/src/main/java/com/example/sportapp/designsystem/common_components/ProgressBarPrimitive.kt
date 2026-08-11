package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors

/**
 * Primitif R18 — rend uniquement la barre de progression : trough (couleur de
 * fond, default `appColors.bgRecessed` = thirdBlue) + remplissage couleur,
 * hauteur par défaut 7dp, coins `RoundedCornerShape(2.dp)`. Source unique de
 * vérité du look de la barre, partagée par `LabeledProgressBar`,
 * `RoutineTasksProgressBar`, `PlannedDayProgressBar`.
 *
 * Le `modifier` est appliqué à la trough (extérieur) — c'est là que le caller
 * passe `Modifier.weight(1f)` ou similaire. La `progress` est coerçée 0..1.
 *
 * `troughColor` peut être overridé en `appColors.bgSurface` (boxBlue) quand la
 * bar est rendue sur un container `bgRecessed` (thirdBlue) — sinon trough
 * invisible (même couleur que le container). Cas typique : la bar dans
 * `RoutineTasksProgressBar` qui se posent dans son propre container bgRecessed.
 *
 * `markerAt` : repère vertical optionnel (0..1, null = aucun), trait fin 2dp
 * `textTertiary` par-dessus trough ET remplissage — ex. seuil « idéal » d'une
 * barre de limite (sucres OMS : marque à 5 % sur la barre bornée au plafond
 * 10 %). Miroir du `markerAt` du primitif web.
 */
@Composable
fun ProgressBarPrimitive(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp,
    troughColor: Color = appColors.bgRecessed,
    markerAt: Float? = null,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(troughColor, shape = RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color, shape = RoundedCornerShape(2.dp))
        )
        markerAt?.let { at ->
            Box(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(at.coerceIn(0f, 1f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(appColors.textTertiary)
                )
            }
        }
    }
}
