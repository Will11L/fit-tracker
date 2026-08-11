package com.example.sportapp.feature.notifications.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.feature.notifications.data.NotificationRepository
import com.example.sportapp.feature.notifications.utils.NotificationType
import com.example.sportapp.feature.notifications.utils.kind
import com.example.sportapp.feature.settings.AppSettingsRepository
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central in-app notification manager.
 * - Persists notifications (Room)
 * - Emits overlay events (SharedFlow)
 * - Applies app settings (vibration, overlay enable/disable)
 */
@Singleton
class NotificationCenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NotificationRepository,
    private val phoneNotif: PhoneNotificationManager,
    private val appSettingsRepo: AppSettingsRepository,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) {
    private val _overlayEvents = MutableSharedFlow<Notification>(extraBufferCapacity = 64)
    val overlayEvents: SharedFlow<Notification> = _overlayEvents

    @SuppressLint("MissingPermission")
    suspend fun post(notification: Notification, showOverlay: Boolean = true) {
        repo.insertLocal(notification)

        val settings = appSettingsRepo.settings.value

        // Gate par catégorie (2026-06-09) : couper une catégorie (tâches / timers /
        // routines) sans toucher aux autres. Point de décision UNIQUE — on inspecte
        // notification.type ici plutôt que dans chaque émetteur. La notif est toujours
        // persistée (historique in-app) ; seuls l'overlay + le push système sont gatés.
        // Catégories non mappées (sync, workout, chat...) ne sont jamais coupées.
        val categoryEnabled = when (notification.kind) {
            NotificationType.TASK_REMINDER -> settings.notifyTasks
            NotificationType.TIMER_DONE -> settings.notifyTimers
            NotificationType.ROUTINE_PERIOD_START,
            NotificationType.ROUTINE_PERIOD_END -> settings.notifyRoutines
            else -> true
        }

        if (categoryEnabled && showOverlay && settings.showInAppNotificationOverlay) {
            _overlayEvents.tryEmit(notification)
        }

        if (categoryEnabled && settings.showPhoneNotifications) {
            phoneNotif.show(
                notification = notification,
                soundEnabled = settings.soundOnInAppNotification,
                vibrationEnabled = settings.vibrateOnInAppNotification
            )
        }
        // V6.4-D4 : vibration via channel système uniquement (passee dans
        // phoneNotif.show ci-dessus). On retire l'appel direct a
        // VibrationUtils.vibrateForNotification qui causait une
        // double-vibration en arriere-plan.

        syncEngine.pushEntityClass(Notification::class)
    }

    // Example helper
    // TimerPage
    suspend fun notifyTimerDone(userId: Int, timerName: String, durationSeconds: Int) {
        val notif = repo.build(
            userId = userId,
            type = NotificationType.TIMER_DONE,
            title = context.getString(R.string.notif_timer_done_title, timerName),
            body = context.getString(R.string.notif_timer_done_body, timerName, durationSeconds),
            data = mapOf(
                "timerName" to timerName,
                "durationSeconds" to durationSeconds.toString(),
                "screen" to "timer"
            )
        )
        post(notif, showOverlay = true)
    }

}
