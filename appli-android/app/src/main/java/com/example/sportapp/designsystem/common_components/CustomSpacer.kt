package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacer vertical de largeur paramétrable (default 6dp), qui remplit la hauteur du parent.
 * Créé pour ajouter un gap horizontal entre cellules dans une Row.
 *
 * Renommé depuis l'ancien `CustomVerticalDivider` (qui était mal nommé : ce n'est pas un
 * divider mais un spacer transparent — le param `color` était d'ailleurs ignoré).
 */
@Composable
fun CustomSpacer(width: Dp = 6.dp) {
    Spacer(modifier = Modifier.width(width).fillMaxHeight())
}
