package com.example.sportapp.feature.notifications.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.feature.notifications.utils.kind
import com.example.sportapp.feature.notifications.utils.notificationTypeIcon
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.core.utils.CustomDateUtils.formatRelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun NotificationOverlayHost(
    events: SharedFlow<Notification>,
    modifier: Modifier = Modifier,
    onClick: (Notification) -> Unit = {}
) {
    var current by remember { mutableStateOf<Notification?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        events.collectLatest { notif ->
            current = notif
            visible = true

            delay(3500)

            if (current?.uuid == notif.uuid) {
                visible = false
                delay(200)
                if (current?.uuid == notif.uuid) current = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            label = "NotificationOverlayVisibility"
        ) {
            current?.let { notif ->
                NotificationOverlayCard(
                    notification = notif,
                    onClick = { onClick(notif) }
                )
            }
        }
    }
}

@Composable
private fun NotificationOverlayCard(
    notification: Notification,
    onClick: () -> Unit
) {
    val isUnread = notification.readAt == null

    // mêmes codes couleur que ta page
    val cardBg = appColors.bgRecessed
    val titleColor = if (isUnread) appColors.accentText else appColors.textTertiary
    val iconColor = if (isUnread) appColors.primaryAction else appColors.textTertiary
    val dateColor = if (isUnread) appColors.textSecondary else appColors.textTertiary
    val bodyColor = if (isUnread) appColors.textSecondary else appColors.textTertiary

    // rail comme les cards
    val railColor = when (notification.level.lowercase()) {
        "success" -> mediumGreen
        "info" -> appColors.primaryAction
        "warning" -> yellowMedium
        "error" -> redDark
        else -> if (isUnread) appColors.primaryAction else appColors.textTertiary
    }
    val railShape = if (isUnread) RoundedCornerShape(0.dp)
    else RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)

    // shape card (gauche rectangle, droite arrondie)
    val cardShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 14.dp,
        bottomEnd = 14.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(cardShape)
    ) {
        // Rail à gauche (comme ta liste)
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(railShape)
                .background(railColor)
        )

        Card(
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 0.5.dp,
                    color = railColor,
                    shape = cardShape
                )
                .clickable { onClick() }
                .clip(cardShape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 10.dp,
                        top = 8.dp,
                        bottom = 10.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icône de type (comme ta page)
                    notificationTypeIcon(notification.kind).let { iconRes ->
                        ActionIconButton(
                            iconRes = iconRes,
                            tint = iconColor,
                            hasBackground = false,
                            clickable = false,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = notification.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = titleColor,
                        modifier = Modifier.weight(1f)
                    )

                    val ctx = LocalContext.current
                    Text(
                        text = formatRelativeTime(ctx, notification.createdAt),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = dateColor,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                notification.body?.takeIf { it.isNotBlank() }?.let { body ->
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = bodyColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
