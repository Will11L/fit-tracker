// com.example.sportapp.core.sync.SyncEvents.kt

package com.example.sportapp.core.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object SyncEvents {
    val onReconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val isNetworkAvailable = MutableStateFlow(false)

    /**
     * Émis par l'`Authenticator` OkHttp (cf. [com.example.sportapp.core.network.RetrofitInstance])
     * quand un appel REST retourne 401 → token serveur invalide/expiré.
     *
     * Consommé par `MainActivity` pour naviguer vers l'écran login.
     */
    val onTokenExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
