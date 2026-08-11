package com.example.sportapp.feature.health.wear

/** État d'affichage de la section « Montre — live ». */
enum class WearLiveStatus {
    /** Aucune donnée et aucune interrogation en cours → montre non connectée. */
    DISCONNECTED,

    /** Requête pull envoyée, en attente de la réponse de la montre. */
    QUERYING,

    /** Donnée fraîche (âge ≤ seuil). */
    LIVE,

    /** Donnée reçue mais ancienne (montre en veille / plus de heartbeat). */
    STALE,
}

/** Au-delà de ce délai (s) sans nouvelle mesure, le live est considéré « en veille ». */
const val WEAR_STALE_SECONDS = 10L

/**
 * Statut pur de la section montre. Si une donnée existe, on l'affiche (LIVE/STALE
 * selon l'âge) même si une interrogation est en cours ; sinon QUERYING pendant
 * l'attente, DISCONNECTED autrement. Logique pure → testable en JVM.
 */
fun wearLiveStatus(
    hasData: Boolean,
    ageSeconds: Long,
    querying: Boolean,
    staleThreshold: Long = WEAR_STALE_SECONDS,
): WearLiveStatus = when {
    hasData && ageSeconds <= staleThreshold -> WearLiveStatus.LIVE
    hasData -> WearLiveStatus.STALE
    querying -> WearLiveStatus.QUERYING
    else -> WearLiveStatus.DISCONNECTED
}
