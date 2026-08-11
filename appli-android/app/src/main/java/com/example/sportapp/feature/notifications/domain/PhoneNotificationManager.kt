package com.example.sportapp.feature.notifications.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.feature.notifications.utils.NotificationLevel
import com.example.sportapp.feature.notifications.utils.levelKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // ⚠️ Si tu avais déjà "in_app_events" et que tu changes les patterns,
        // envisage de versionner: "in_app_info_v2" etc.
        private const val CHANNEL_INFO = "in_app_info"
        private const val CHANNEL_SUCCESS = "in_app_success"
        private const val CHANNEL_WARNING = "in_app_warning"
        private const val CHANNEL_ERROR = "in_app_error"
    }

    private fun channelIdFor(level: NotificationLevel): String = when (level) {
        NotificationLevel.INFO -> CHANNEL_INFO
        NotificationLevel.SUCCESS -> CHANNEL_SUCCESS
        NotificationLevel.WARNING -> CHANNEL_WARNING
        NotificationLevel.ERROR -> CHANNEL_ERROR
    }

    private fun channelNameFor(level: NotificationLevel): String = when (level) {
        NotificationLevel.INFO -> "SportApp - Info"
        NotificationLevel.SUCCESS -> "SportApp - Success"
        NotificationLevel.WARNING -> "SportApp - Warning"
        NotificationLevel.ERROR -> "SportApp - Error"
    }

    private fun vibrationPatternFor(level: NotificationLevel): LongArray = when (level) {
        NotificationLevel.SUCCESS -> longArrayOf(0, 40)
        NotificationLevel.INFO -> longArrayOf(0, 60)
        NotificationLevel.WARNING -> longArrayOf(0, 30, 40, 30)
        NotificationLevel.ERROR -> longArrayOf(0, 80, 40, 80)
    }

    private fun importanceFor(level: NotificationLevel, soundEnabled: Boolean, vibrationEnabled: Boolean): Int {
        // Si ni son ni vibration, LOW (silencieux)
        if (!soundEnabled && !vibrationEnabled) return NotificationManager.IMPORTANCE_LOW

        // Tu peux ajuster selon ton goût :
        return when (level) {
            NotificationLevel.ERROR, NotificationLevel.WARNING -> NotificationManager.IMPORTANCE_DEFAULT
            NotificationLevel.INFO, NotificationLevel.SUCCESS -> NotificationManager.IMPORTANCE_DEFAULT
        }
    }

    private fun ensureChannels(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(NotificationManager::class.java)

        fun upsert(level: NotificationLevel) {
            val channelId = channelIdFor(level)
            val channel = NotificationChannel(
                channelId,
                channelNameFor(level),
                importanceFor(level, soundEnabled, vibrationEnabled)
            ).apply {
                description = "SportApp in-app notifications (${level.wire})"
                setShowBadge(true)

                enableVibration(vibrationEnabled)
                if (vibrationEnabled) vibrationPattern = vibrationPatternFor(level)

                if (!soundEnabled) setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }

        upsert(NotificationLevel.INFO)
        upsert(NotificationLevel.SUCCESS)
        upsert(NotificationLevel.WARNING)
        upsert(NotificationLevel.ERROR)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun show(
        notification: Notification,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        ensureChannels(soundEnabled, vibrationEnabled)

        val level = notification.levelKind
        val channelId = channelIdFor(level)

        val navTarget = NotificationNavigationMapper.resolve(notification)
        val pendingIntent = navTarget?.let {
            buildDeepLinkPendingIntent(route = it.route, notificationUuid = notification.uuid)
        }

        val smallIcon = when (level) {
            NotificationLevel.SUCCESS -> R.drawable.ic_rounded_check_circle
            NotificationLevel.WARNING -> R.drawable.ic_rounded_warning
            NotificationLevel.ERROR -> R.drawable.ic_rounded_warning
            NotificationLevel.INFO -> R.drawable.ic_notifications
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(notification.title)
            .setContentText(notification.body ?: "")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (soundEnabled || vibrationEnabled) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_LOW
            )

        // BigText si body long
        notification.body?.takeIf { it.length > 40 }?.let { body ->
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        // Image optionnelle (BigPicture) si data["imageUri"]
        val imageUri = notification.data?.get("imageUri")
        if (!imageUri.isNullOrBlank()) {
            runCatching {
                val bmp = context.contentResolver.openInputStream(imageUri.toUri())
                    ?.use { BitmapFactory.decodeStream(it) }

                if (bmp != null) {
                    builder.setLargeIcon(bmp)
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bmp)
                            .bigLargeIcon(null as Bitmap?)
                    )
                }
            }
        }

        val id = (notification.dedupeKey ?: notification.uuid).hashCode()
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun buildDeepLinkPendingIntent(route: String, notificationUuid: String?): PendingIntent {
        val base = "sportapp://notif/$route"
        val uri: Uri = if (!notificationUuid.isNullOrBlank()) {
            "$base?uuid=$notificationUuid".toUri()
        } else {
            base.toUri()
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (PendingIntent.FLAG_IMMUTABLE)

        return PendingIntent.getActivity(
            context,
            (route + (notificationUuid ?: "")).hashCode(),
            intent,
            flags
        )
    }
}
