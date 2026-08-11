package com.example.sportapp.feature.health.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État live reçu de la montre via le Data Layer (singleton process-wide, alimenté
 * par [PhoneWearListenerService]). **Affichage-only** : aucune écriture Room/serveur
 * (Health Connect reste la source persistée → anti double-comptage). `null` tant
 * qu'aucun message n'a été reçu (= pas de montre / montre en veille).
 */
object WearLiveState {

    private val _live = MutableStateFlow<HealthLivePayload?>(null)
    val live: StateFlow<HealthLivePayload?> = _live.asStateFlow()

    fun update(payload: HealthLivePayload) {
        _live.value = payload
    }
}
