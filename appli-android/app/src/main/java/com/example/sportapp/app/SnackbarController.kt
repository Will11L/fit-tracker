package com.example.sportapp.app

import androidx.compose.material3.SnackbarDuration
import com.example.sportapp.core.utils.SnackbarType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Donnee minimale d'un snackbar : message + type semantique + actions optionnelles.
 * Les COULEURS sont resolues au rendu par le SnackbarHost (@Composable) via appColors.*
 * → un meme event s'adapte automatiquement au theme dark/light en cours.
 */
data class SnackbarEvent(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val type: SnackbarType = SnackbarType.INFO,
    val action: SnackbarAction? = null,
    val secondaryAction: SnackbarAction? = null,
    val duration: SnackbarDuration = SnackbarDuration.Long,
)

data class SnackbarAction(
    val name: String,
    val action: suspend () -> Unit
)

object SnackbarController {
    private val _snackbars = MutableStateFlow<List<SnackbarEvent>>(emptyList())
    val snackbars = _snackbars.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    suspend fun show(event: SnackbarEvent) {
        _snackbars.value = _snackbars.value + event

        // Gestion auto-dismiss si pas "Indefinite"
        if (event.duration != SnackbarDuration.Indefinite) {
            scope.launch {
                delay(event.duration.toMillis())
                dismissSnackbarById(event.id)
            }
        }
    }

    suspend fun dismissSnackbarById(id: String) {
        _snackbars.value = _snackbars.value.filterNot { it.id == id }
    }

    suspend fun dismissAll() {
        _snackbars.value = emptyList()
    }
}

// Extension pour convertir SnackbarDuration → millisecondes
fun SnackbarDuration.toMillis(): Long {
    return when (this) {
        SnackbarDuration.Short -> 2000L
        SnackbarDuration.Long -> 4000L
        SnackbarDuration.Indefinite -> Long.MAX_VALUE
    }
}
