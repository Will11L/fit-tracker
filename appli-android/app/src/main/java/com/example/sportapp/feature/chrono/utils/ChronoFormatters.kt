package com.example.sportapp.feature.chrono.utils

/**
 * Formatters de temps consolidés pour le module chrono.
 *
 * Avant ce refactor, 3 formats co-existaient en privé dans 4 fichiers :
 *  - StopwatchPage.kt + TimerPage.kt   → "HH:MM:SS:CC" (avec centisecondes)
 *  - MiniChronoOverlay.kt              → "HH:MM:SS" (sans centisecondes)
 *  - MiniTimerOverlay.kt               → "MM:SS" ou "HH:MM:SS" selon heures
 */

/** "HH:MM:SS:CC" — main display (pages stopwatch + timer). */
fun formatTimeWithCentiseconds(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val hours = (totalSeconds / 3600L).toInt()
    val minutes = ((totalSeconds % 3600L) / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    val centiseconds = ((clamped % 1000L) / 10L).toInt()
    return "%02d:%02d:%02d:%02d".format(hours, minutes, seconds, centiseconds)
}

/** "HH:MM:SS" — mini overlay stopwatch (toujours avec heures). */
fun formatTimeFull(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val hours = (totalSeconds / 3600L).toInt()
    val minutes = ((totalSeconds % 3600L) / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** "MM:SS" ou "HH:MM:SS" si heures > 0 — mini overlay timer (compact). */
fun formatTimeCompact(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val hours = (totalSeconds / 3600L).toInt()
    val minutes = ((totalSeconds % 3600L) / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Compteur compact pour le mini overlay : 1 ou 2 chiffres maximum.
 * - >= 1 min : minutes restantes (floor) -> "1" pour 1m30s, "2" pour 2m45s.
 * - < 1 min : secondes restantes (floor) -> "30" pour 30s, "5" pour 5s.
 *
 * Transition naturelle minutes -> secondes au passage du seuil 60s
 * (ex. "1" jusqu'à 59,999s puis "59" puis countdown jusqu'à "0").
 */
fun compactRemainingText(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    return if (clamped < 60_000L) {
        "${clamped / 1000L}"
    } else {
        "${clamped / 60_000L}"
    }
}

/**
 * Génère un nom de timer "humain" depuis une durée en ms.
 * Utilisé pour le custom dialog (où l'user n'a pas de label preset).
 * Ex. 90_000L → "1 min 30 s" ; 60_000L → "1 min" ; 30_000L → "30 s" ;
 *     3_660_000L → "1 h 1 min" ; 7_200_000L → "2 h"
 */
fun timerNameForDuration(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val hours = (totalSeconds / 3600L).toInt()
    val minutes = ((totalSeconds % 3600L) / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    return buildString {
        if (hours > 0) append("$hours h")
        if (minutes > 0) {
            if (isNotEmpty()) append(' ')
            append("$minutes min")
        }
        if (seconds > 0) {
            if (isNotEmpty()) append(' ')
            append("$seconds s")
        }
        if (isEmpty()) append("0 s")
    }
}
