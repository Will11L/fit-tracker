package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue

/**
 * Boutons d'action des dialogs (style ActionTextButton + bordure).
 * - Secondaire (Cancel/Annuler) : transparent + bordure, texte textPrimary.
 * - Primaire (action) : fond = couleur de l'action + bordure, texte blanc.
 * Coins MaterialTheme.shapes.small, texte 14sp (cf. ActionTextButton).
 */
@Composable
fun DialogSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = if (enabled) lightGrayBlue else appColors.textTertiary
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, c), MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = c, fontSize = 14.sp)
    }
}

@Composable
fun DialogPrimaryButton(
    text: String,
    onClick: () -> Unit,
    color: Color = appColors.primaryAction,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Désactivé = fond bleu-gris (lightGrayBlue, comme le bouton Cancel) à 50%
    // d'opacité : l'alpha est appliqué sur TOUT le Box (fond + bordure + texte
    // d'un bloc) → fondu uniforme, pas de liseré (la bordure suit le fond), et
    // l'affordance "c'est LE bouton, juste pas encore prêt" reste lisible
    // (functional review 2026-06-09).
    val bg = if (enabled) color else lightGrayBlue
    Box(
        modifier = modifier
            .padding(start = 6.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(MaterialTheme.shapes.small)
            .background(bg, MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, bg), MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp)
    }
}
