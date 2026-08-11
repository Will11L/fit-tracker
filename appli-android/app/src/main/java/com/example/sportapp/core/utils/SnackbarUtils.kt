package com.example.sportapp.core.utils

import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.app.SnackbarAction
import com.example.sportapp.app.SnackbarController
import com.example.sportapp.app.SnackbarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Cree un SnackbarEvent et le pousse dans le SnackbarController.
 * Les couleurs/icones sont resolues au rendu par le SnackbarHost (@Composable)
 * a partir de [type] -- voir MainActivity.snackbarHost.
 */
fun showSnackbar(
    message: String,
    type: SnackbarType = SnackbarType.INFO,
    action: SnackbarAction? = null,
    secondaryAction: SnackbarAction? = null,
    duration: SnackbarDuration = SnackbarDuration.Indefinite
): String {
    val snackbarId = UUID.randomUUID().toString()

    // Si pas de secondaryAction fourni, on cree un "Close" par defaut qui dismiss la snackbar
    val resolvedSecondaryAction = when {
        secondaryAction != null -> secondaryAction
        duration == SnackbarDuration.Indefinite -> SnackbarAction("Close") {
            SnackbarController.dismissSnackbarById(snackbarId)
        }
        else -> null
    }

    CoroutineScope(Dispatchers.Main).launch {
        SnackbarController.show(
            SnackbarEvent(
                id = snackbarId,
                message = message,
                type = type,
                action = action,
                secondaryAction = resolvedSecondaryAction,
                duration = duration,
            )
        )
    }

    return snackbarId
}

enum class SnackbarType {
    SUCCESS,
    WARNING,
    ERROR,
    INFO
}
