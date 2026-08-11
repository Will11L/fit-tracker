package com.example.sportapp.designsystem.common_components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.app.SnackbarEvent
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.core.utils.SnackbarType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Host custom des snackbars de l'app (O14 · SnackbarHost dans le Design System).
 *
 * Reçoit la liste des [SnackbarEvent] actifs (poussés via le SnackbarController) et
 * les positionne / anime en colonne en haut de l'écran. Chaque snackbar :
 *  - barre `bg/recessed` arrondie 12dp + bordure 1.5dp accent (couleur par type),
 *  - icône tintée accent + message + actions optionnelles (action / action secondaire),
 *  - apparition décalée (slide in horizontal + fade) + réajustement vertical fluide.
 *
 * Les couleurs/icônes sont résolues AU RENDU à partir de [SnackbarEvent.type] via
 * `appColors.*` → theme-aware (s'adapte au dark/light en cours).
 *
 * NOTE : volontairement nommé `AppSnackbarHost` et non `SnackbarHost` pour éviter la
 * collision avec le `SnackbarHost` de Material3.
 */
@Composable
fun AppSnackbarHost(
    snackbars: List<SnackbarEvent>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(snackbars, key = { _, it -> it.id }) { index, event ->
            var isVisible by remember { mutableStateOf(false) }

            LaunchedEffect(event.id) {
                delay(index * 100L) // ⏳ Décalage d'apparition
                isVisible = true
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                label = "SnackbarAnimation"
            ) {
                AppSnackbar(
                    event = event,
                    onActionClick = { action -> scope.launch { action() } },
                )
            }
        }
    }
}

/**
 * Rendu visuel d'un snackbar custom (la "notif" qui se glisse à l'écran).
 *
 * Accent/icône résolus depuis [SnackbarEvent.type] au moment du rendu (theme-aware).
 * [onActionClick] exécute la lambda suspend d'une action (dismiss, navigation, ...).
 */
@Composable
private fun LazyItemScope.AppSnackbar(
    event: SnackbarEvent,
    onActionClick: (suspend () -> Unit) -> Unit,
) {
    // Resolution couleurs/icone depuis event.type au moment du rendu
    // (theme-aware : refleche le dark/light en cours).
    val accent = when (event.type) {
        SnackbarType.SUCCESS -> appColors.snackbarSuccess
        SnackbarType.WARNING -> appColors.snackbarWarning
        SnackbarType.ERROR -> appColors.snackbarError
        SnackbarType.INFO -> appColors.primaryAction
    }
    val iconRes = when (event.type) {
        SnackbarType.SUCCESS -> R.drawable.ic_rounded_check_circle
        SnackbarType.WARNING -> R.drawable.ic_rounded_warning
        SnackbarType.ERROR -> R.drawable.ic_rounded_error
        SnackbarType.INFO -> R.drawable.ic_rounded_info
    }

    Snackbar(
        shape = RoundedCornerShape(12.dp),
        containerColor = appColors.bgRecessed,
        contentColor = appColors.textPrimary,
        actionOnNewLine = true,
        modifier = Modifier
            .border(1.5.dp, accent, RoundedCornerShape(12.dp))
            .fillMaxWidth()
            .animateItem( // ✅ Animation de réajustement vertical
                fadeInSpec = null,
                fadeOutSpec = null,
                placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(event.message, color = appColors.textPrimary)
            }

            if (event.action != null || event.secondaryAction != null) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    event.secondaryAction?.let { secondary ->
                        TextButton(onClick = { onActionClick(secondary.action) }) {
                            Text(secondary.name, color = appColors.textTertiary)
                        }
                    }
                    event.action?.let { primary ->
                        TextButton(onClick = { onActionClick(primary.action) }) {
                            Text(primary.name, color = accent)
                        }
                    }
                }
            }
        }
    }
}
