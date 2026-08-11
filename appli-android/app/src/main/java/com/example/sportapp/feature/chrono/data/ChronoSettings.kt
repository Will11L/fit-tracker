package com.example.sportapp.feature.chrono.data

/**
 * Settings du module chrono persistés via DataStore (cf. ChronoSettingsDataStore).
 *
 * Note : sound + vibration ne sont PAS dupliqués ici — ils sont déjà gérés
 * globalement par `settings/AppSettings` (`soundOnInAppNotification`,
 * `vibrateOnInAppNotification`) et appliqués automatiquement par
 * `NotificationCenter.post()` à toutes les notifs in-app dont la fin de timer.
 */
data class ChronoSettings(
    val lastTimerName: String = "",
    val lastTimerDurationMillis: Long = 60_000L,
    val lastActiveTab: ChronoTab = ChronoTab.STOPWATCH
)

/** Onglets du ChronoScreen. UPPER_CASE conforme à la politique 11. */
enum class ChronoTab { STOPWATCH, TIMER }
