package com.example.sportapp.feature.notifications.domain

import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.feature.notifications.utils.NotificationType
import com.example.sportapp.feature.notifications.utils.kind

data class NotificationNavTarget(
    val route: String,
    val markAsReadBeforeNavigate: Boolean = true
)

object NotificationNavigationMapper {

    fun resolve(notif: Notification): NotificationNavTarget? {
        val data = notif.data
        val exerciseUuid = data?.get("exerciseUuid")
        val conversationId = data?.get("conversationId")

        return when (notif.kind) {
            NotificationType.TIMER_DONE ->
                NotificationNavTarget(route = Routes.CHRONO)

            // NOTE : route "planned_workout" sans UUID = potentiel bug pre-existant
            // (la vraie route est PLANNED_WORKOUT_PATTERN avec UUID). Comportement
            // conserve a l'identique en attendant decision sur le mapping notif -> ecran.
            NotificationType.WORKOUT_REMINDER ->
                NotificationNavTarget(route = "planned_workout")

            NotificationType.TASK_REMINDER ->
                NotificationNavTarget(route = Routes.TASKS)  // Phase 3 (2026-05-12)

            NotificationType.ROUTINE_PERIOD_START ->
                NotificationNavTarget(route = Routes.TASKS)  // 2026-06-08 : ecran routines/quotidien

            NotificationType.ROUTINE_PERIOD_END ->
                NotificationNavTarget(route = Routes.TASKS)  // 2026-06-08 : ecran routines/quotidien

            NotificationType.SYNC_DONE ->
                NotificationNavTarget(route = Routes.HOME)

            NotificationType.SYNC_ERROR ->
                NotificationNavTarget(route = Routes.SYNC_SETTINGS)

            NotificationType.CHAT ->
                if (!conversationId.isNullOrBlank())
                    NotificationNavTarget(route = "${Routes.CONVERSATIONS}/$conversationId")
                else
                    NotificationNavTarget(route = Routes.CONVERSATIONS)

            NotificationType.EXERCISE ->
                if (!exerciseUuid.isNullOrBlank())
                    NotificationNavTarget(route = Routes.exercise(exerciseUuid))
                else null

            NotificationType.UNKNOWN ->
                NotificationNavTarget(route = Routes.HOME) // ✅ fallback robuste
        }
    }
}
