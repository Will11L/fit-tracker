package com.example.sportapp.feature.notifications.utils

import com.example.sportapp.R
import com.example.sportapp.core.data.model.Notification

// --- Extensions type-safe sur le modèle DB (string -> enum) ---

val Notification.kind: NotificationType
    get() = NotificationType.fromWire(this.type)

val Notification.levelKind: NotificationLevel
    get() = NotificationLevel.fromWire(this.level)

// --- Enums ---

enum class NotificationLevel(val wire: String) {
    SUCCESS("success"),
    INFO("info"),
    WARNING("warning"),
    ERROR("error");

    companion object {
        fun fromWire(raw: String?): NotificationLevel {
            if (raw.isNullOrBlank()) return INFO
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.wire == normalized } ?: INFO
        }
    }
}

enum class NotificationType(
    val wire: String,
    val defaultLevel: NotificationLevel
) {
    TIMER_DONE("TIMER_DONE", NotificationLevel.INFO),
    WORKOUT_REMINDER("WORKOUT_REMINDER", NotificationLevel.INFO),
    TASK_REMINDER("TASK_REMINDER", NotificationLevel.INFO),   // Phase 3 (2026-05-12)
    ROUTINE_PERIOD_START("ROUTINE_PERIOD_START", NotificationLevel.INFO),   // 2026-06-08
    ROUTINE_PERIOD_END("ROUTINE_PERIOD_END", NotificationLevel.INFO),       // 2026-06-08
    SYNC_DONE("SYNC_DONE", NotificationLevel.SUCCESS),
    SYNC_ERROR("SYNC_ERROR", NotificationLevel.ERROR),
    CHAT("CHAT", NotificationLevel.INFO),
    EXERCISE("EXERCISE", NotificationLevel.INFO),
    UNKNOWN("UNKNOWN", NotificationLevel.INFO);

    companion object {
        fun fromWire(raw: String?): NotificationType {
            if (raw.isNullOrBlank()) return UNKNOWN
            val normalized = raw.trim().uppercase()
            return entries.firstOrNull { it.wire == normalized } ?: UNKNOWN
        }
    }
}

// --- UI helpers ---

fun notificationTypeIcon(type: NotificationType): Int {
    return when (type) {
        NotificationType.TIMER_DONE -> R.drawable.ic_timer
        NotificationType.WORKOUT_REMINDER -> R.drawable.ic_exercise
        NotificationType.TASK_REMINDER -> R.drawable.ic_rounded_list_alt
        NotificationType.ROUTINE_PERIOD_START -> R.drawable.ic_rounded_av_timer
        NotificationType.ROUTINE_PERIOD_END -> R.drawable.ic_rounded_av_timer
        NotificationType.SYNC_DONE -> R.drawable.ic_cloud_done
        NotificationType.SYNC_ERROR -> R.drawable.ic_cloud_off
        NotificationType.CHAT -> R.drawable.ic_chat
        NotificationType.EXERCISE -> R.drawable.ic_exercise
        NotificationType.UNKNOWN -> R.drawable.ic_rounded_question_mark
    }
}
