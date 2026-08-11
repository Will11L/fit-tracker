package com.example.sportapp.feature.notifications.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.feature.notifications.utils.kind
import com.example.sportapp.feature.notifications.utils.notificationTypeIcon
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.redDark
import com.example.sportapp.core.utils.CustomDateUtils.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNotificationItem(
    notif: Notification,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    onDeleteNow: () -> Unit,
    onNavigate: (Notification) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { fullWidth -> fullWidth * 0.95f } // ✅ quasi 100%
    )

    val triggerThreshold = 0.95f
    var hasTriggered by remember { mutableStateOf(false) }

    BoxWithConstraints {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)

        LaunchedEffect(dismissState) {
            snapshotFlow { dismissState.requireOffset() }
                .collect { offset ->
                    val progress = (kotlin.math.abs(offset) / widthPx).coerceIn(0f, 1f)

                    // reset quand on revient proche du centre
                    if (progress < 0.05f) hasTriggered = false

                    if (!hasTriggered && progress >= triggerThreshold) {
                        hasTriggered = true

                        if (offset > 0f) {
                            onNavigate(notif)    // swipe droite
                        } else {
                            onDeleteNow()        // swipe gauche
                        }

                        dismissState.reset()
                    }
                }
        }

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = { SwipeBackground(state = dismissState) }
        ) {
            NotificationCard(
                notif = notif,
                onClick = onClick,
                onRequestDelete = onRequestDelete,
                onNavigate = { onNavigate(notif) }
            )
        }
    }

}


@Composable
private fun NotificationCard(
    notif: Notification,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    onNavigate: () -> Unit
) {
    val isUnread = notif.readAt == null

    val cardBg = appColors.bgRecessed
    val titleColor = if (isUnread) appColors.accentText else appColors.textTertiary
    val iconColor = if (isUnread) appColors.primaryAction else appColors.textTertiary
    val dateColor = if (isUnread) appColors.textSecondary else appColors.textTertiary
    val bodyColor = if (isUnread) appColors.textSecondary else appColors.textTertiary

    // ✅ Card: gauche rectangle, droite arrondie
    val cardShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 14.dp,
        bottomEnd = 14.dp
    )

    // ✅ Rail (trait): unread rectangle, read arrondi et même couleur que la card
    val railColor = if (isUnread) appColors.primaryAction else appColors.textTertiary
    val railShape = if (isUnread) {
        RoundedCornerShape(0.dp) // rectangle
    } else {
        RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(cardShape)
    ) {
        // Rail à gauche
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(railShape)
                .background(railColor)
        )

        // Card liée au rail
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .weight(1f)
                .clip(cardShape)
                .clickable { onClick() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // 🟦 Icône de type
                    notificationTypeIcon(notif.kind).let { iconRes ->
                        ActionIconButton(
                            iconRes = iconRes,
                            tint = iconColor,
                            hasBackground = false,
                            clickable = false,
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // 📝 Titre
                    Text(
                        text = notif.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = titleColor,
                        modifier = Modifier.weight(1f)
                    )

                    // ⏲️ Date
                    val ctx = LocalContext.current
                    Text(
                        text = formatRelativeTime(ctx, notif.createdAt),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = dateColor,
                        modifier = Modifier.padding(start = 6.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 🗑️ Delete
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_delete_sweep,
                        tint = appColors.textPrimary,
                        customBackgroundColor = redDark,
                        onClick = onRequestDelete
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // ⬅️ Navigate (placeholder)
                    ActionIconButton(
                        iconRes = R.drawable.ic_arrow_right_alt,
                        tint = MaterialTheme.colorScheme.onSurface,
                        clickable = true,
                        onClick = onNavigate
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    notif.body?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    state: SwipeToDismissBoxState,
) {
    val direction = state.dismissDirection

    val bgShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 14.dp,
        bottomEnd = 14.dp
    )

    val baseColor = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> appColors.primaryAction
        SwipeToDismissBoxValue.EndToStart -> redDark
        else -> Color.Transparent
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(bgShape)
            .background(Color.Transparent)
            .padding(horizontal = 14.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
        val offsetPx = runCatching { state.requireOffset() }.getOrElse { 0f }

        val progress = (kotlin.math.abs(offsetPx) / widthPx).coerceIn(0f, 1f)
        val alpha = (progress * progress).coerceIn(0f, 1f)

        val alignment = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            else -> Alignment.Center
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(bgShape)
                .background(baseColor.copy(alpha = alpha)),
            contentAlignment = alignment
        ) {
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    ActionIconWithTextButton(
                        iconRes = R.drawable.ic_arrow_right_alt,
                        text = stringResource(R.string.notifications_swipe_go),
                        tint = appColors.textPrimary,
                        textColor = appColors.textPrimary,
                        iconSize = 20.dp,
                        hasBackground = false,
                        clickable = false
                    )
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    ActionIconWithTextButton(
                        iconRes = R.drawable.ic_rounded_delete_sweep,
                        text = stringResource(R.string.notifications_swipe_delete),
                        tint = appColors.textPrimary,
                        textColor = appColors.textPrimary,
                        iconSize = 20.dp,
                        hasBackground = false,
                        clickable = false
                    )
                }
                else -> Unit
            }
        }
    }
}
