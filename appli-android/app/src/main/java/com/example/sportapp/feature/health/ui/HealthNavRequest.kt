package com.example.sportapp.feature.health.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pont de navigation du hub Santé (miroir du `HealthNavService` web) : le drawer
 * demande l'ouverture d'une SECTION précise (index de page du pager), le hub
 * consomme la demande à l'arrivée (ou immédiatement s'il est déjà monté) et anime
 * son pager dessus. One-shot : consommée puis remise à null.
 */
object HealthNavRequest {
    private val _page = MutableStateFlow<Int?>(null)
    val page: StateFlow<Int?> = _page

    /** Demande d'ouvrir la section [index] du hub (clic item drawer). */
    fun request(index: Int) {
        _page.value = index
    }

    /** Consomme la demande courante (le hub l'a traitée). */
    fun consume() {
        _page.value = null
    }
}
