package com.example.sportapp.feature.notifications.data

import com.example.sportapp.core.data.local.NotificationDao
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.feature.notifications.utils.NotificationLevel
import com.example.sportapp.feature.notifications.utils.NotificationType
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class NotificationRepository @Inject constructor(
    private val dao: NotificationDao
) {
    fun observeAll(): Flow<List<Notification>> = dao.observeAll()
    fun observeUnread(): Flow<List<Notification>> = dao.observeUnread()
    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()
    fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    suspend fun insertLocal(notification: Notification) {
        dao.insert(notification)
    }

    suspend fun markAsRead(uuid: String) {
        dao.markAsRead(uuid)
    }

    suspend fun markAllAsRead() {
        dao.markAllAsRead()
    }

    suspend fun markAsPendingDeletion(uuid: String){
        dao.markAsPendingDeletion(uuid)
    }

    suspend fun deleteLocal(notification: Notification) = dao.delete(notification)

    @Deprecated(
        "Utiliser l'overload prenant NotificationType (enum) + NotificationLevel (enum). " +
                "Strings = risque de typo + level non types. Cf. NotificationType.wire si " +
                "vraiment besoin d'une string brute.",
        ReplaceWith("build(userId, NotificationType.valueOf(type), NotificationLevel.fromWire(level), title, body, data, dedupeKey)")
    )
    fun build(
        userId: Int,
        type: String,
        level: String = "info",
        title: String,
        body: String? = null,
        data: Map<String, String>? = null,
        dedupeKey: String? = null
    ): Notification {
        return Notification(
            uuid = UUID.randomUUID().toString(),
            userId = userId,
            type = type,
            level = level,
            title = title,
            body = body,
            data = data,
            dedupeKey = dedupeKey,
            createdAt = getNowISO8601(),
            readAt = null,
            archivedAt = null,
            synced = false,
            pendingDeletion = false,
            updatedAt = getNowISO8601()
        )
    }

    fun build(
        userId: Int,
        type: NotificationType,
        level: NotificationLevel = type.defaultLevel,
        title: String,
        body: String? = null,
        data: Map<String, String>? = null,
        dedupeKey: String? = null
    ): Notification {
        @Suppress("DEPRECATION")
        return build(
            userId = userId,
            type = type.wire,
            level = level.wire,
            title = title,
            body = body,
            data = data,
            dedupeKey = dedupeKey
        )
    }

    /**
     * Exemple d’utilisation – comment déclencher une notification applicative complète
     * (persistée + notification système + vibration + deep link)
     *
     * ---
     * Cas d’usage typique : fin de synchronisation
     *
     * val notif = Notification(
     *     uuid = UUID.randomUUID().toString(),
     *     userId = userId,
     *     type = NotificationType.SYNC_DONE.wire,   // ⚠️ toujours via enum
     *     level = NotificationLevel.SUCCESS.wire,   // ⚠️ level = vibration / channel
     *     title = "Synchronisation",
     *     body = "Synchro automatique terminée",
     *     data = mapOf(
     *         "screen" to "home"                     // utilisé par NotificationNavigationMapper
     *     ),
     *     dedupeKey = "sync_status",                 // évite le spam
     *     createdAt = getNowISO8601(),
     *     readAt = null,
     *     archivedAt = null,
     *     synced = false,
     *     pendingDeletion = false,
     *     updatedAt = getNowISO8601()
     * )
     *
     * // Envoi centralisé :
     * // - insertion en base (Room)
     * // - notification téléphone (même app en veille)
     * // - vibration selon NotificationLevel
     * // - deep link au clic
     *
     * notificationCenter.post(
     *     notification = notif,
     *     showOverlay = false   // true = bannière in-app si app au premier plan
     * )
     *
     * ---
     * IMPORTANT :
     * - Le clic téléphone est résolu via NotificationNavigationMapper
     * - Les vibrations dépendent du channel lié au NotificationLevel
     * - Ne JAMAIS utiliser de strings en dur pour type / level
     */


}
