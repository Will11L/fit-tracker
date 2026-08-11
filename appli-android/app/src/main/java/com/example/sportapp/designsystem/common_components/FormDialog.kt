package com.example.sportapp.designsystem.common_components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

/**
 * Dialog de formulaire canonique : titre + champs (slot [content]) + boutons
 * [dismissText] / [confirmText]. Encapsule le squelette `AlertDialog` partagé
 * par les form dialogs de l'app (R12, ↔ organism O12 FormDialog).
 *
 * [content] est un `ColumnScope` (les champs) avec un espacement vertical de
 * 12dp. [confirmEnabled] grise et désactive le bouton de confirmation.
 * [disabledReason] est un message d'aide affiché sous le contenu quand le
 * bouton est désactivé (`!confirmEnabled`), pour expliquer pourquoi le
 * formulaire est invalide (sinon l'utilisateur croit à un bug). Le callsite
 * calcule la raison contextuelle (FormDialog ne peut pas la deviner).
 * [scrollable] rend la zone de contenu défilable verticalement (formulaires
 * longs qui dépasseraient la hauteur du dialog).
 */
@Composable
fun FormDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    disabledReason: String? = null,
    dismissText: String = stringResource(R.string.common_cancel),
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.bgScreen,
        title = { Text(title, color = appColors.primaryAction) },
        text = {
            Column(
                modifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
                // Message de validation : composant partagé (zone réservée + glissement
                // + fondu), même rendu que les dialogs inline. Cf. DialogValidationReason.
                DialogValidationReason(
                    reason = if (!confirmEnabled) disabledReason else null,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = confirmText, onClick = onConfirm, enabled = confirmEnabled)
        },
        dismissButton = {
            DialogSecondaryButton(text = dismissText, onClick = onDismiss)
        },
    )
}

/**
 * Message de validation d'un form dialog (raison de désactivation du bouton).
 * Zone à hauteur fixe (toujours 1 ligne ; placeholder espace quand masqué) +
 * animation de MOUVEMENT : le texte glisse verticalement en place (translationY)
 * + fondu via graphicsLayer (au niveau dessin) → mouvement visible SANS changer la
 * hauteur, donc sans resize/saut du dialog. [reason] null/vide = masqué (place
 * réservée). Source unique partagée par FormDialog et les dialogs inline.
 */
@Composable
fun DialogValidationReason(reason: String?, modifier: Modifier = Modifier) {
    var lastReason by remember { mutableStateOf("") }
    val show = !reason.isNullOrEmpty()
    if (show) lastReason = reason!!
    val progress by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(200, easing = LinearEasing),
        label = "reasonProgress",
    )
    Text(
        text = lastReason.ifEmpty { " " },
        color = appColors.snackbarError,
        fontSize = 12.sp,
        minLines = 1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 10.dp.toPx()
            },
    )
}
