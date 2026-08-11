package com.example.sportapp.feature.settings

data class AppSettings(
    val vibrateOnInAppNotification: Boolean = true,     // Vibrate on notification
    val soundOnInAppNotification: Boolean = false,      // Sound on notification
    val showInAppNotificationOverlay: Boolean = true,   // Show notification overlay
    val showPhoneNotifications: Boolean = true,         // Show phone notifications

    // Activation des notifications par catégorie (2026-06-09). Défaut true =
    // toutes les catégories notifient. Couper une catégorie (ex. notifyTasks)
    // n'affecte pas les autres. Gating central dans NotificationCenter.post().
    val notifyTasks: Boolean = true,                    // TASK_REMINDER
    val notifyTimers: Boolean = true,                   // TIMER_DONE
    val notifyRoutines: Boolean = true,                 // ROUTINE_PERIOD_START / _END

    // Rappel par défaut (2026-06-08) : minutes avant l'échéance/le début, appliqué
    // au PRÉ-REMPLISSAGE du sélecteur à la création d'une tâche/période sans réglage
    // propre. null = "Aucun". Défaut usine 15 min. Local-only (non synchronisé).
    val defaultReminderMinutesBefore: Int? = 15,
)
