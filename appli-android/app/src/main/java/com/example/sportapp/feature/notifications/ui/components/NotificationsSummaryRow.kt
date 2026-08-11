package com.example.sportapp.feature.notifications.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.SummaryInlineDivider
import com.example.sportapp.feature.routines.ui.components.routineTasksScreen.SummaryInlineItem
import com.example.sportapp.designsystem.theme.*

@Composable
fun NotificationsSummaryRow(
    modifier: Modifier = Modifier,
    unreadCount: Int,
    totalCount: Int,
    unsyncedCount: Int = 0,
    onReadAll: () -> Unit
) {
    val readCount = (totalCount - unreadCount).coerceAtLeast(0)

    // 2026-05-12 : Row passe a SpaceAround pour repartir l'espace + items
    // marquant avec maxLines/ellipsis (cf SummaryInlineItem). En FR "8 Non lues"
    // est plus long que "8 Unread" -- avant le Row spacedBy(19dp) faisait
    // deborder le texte hors-ecran. SpaceAround + ellipsis evite le wrap.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        // Read
        SummaryInlineItem(
            icon = com.example.sportapp.R.drawable.ic_rounded_check,
            value = "$readCount",
            label = stringResource(R.string.notifications_summary_read),
            tint = mediumGreen
        )

        SummaryInlineDivider()

        // Unsynced (sans label texte -- juste l'icone + count)
        SummaryInlineItem(
            icon = com.example.sportapp.R.drawable.ic_cloud_off,
            value = "$unsyncedCount",
            tint = yellowMedium
        )

        SummaryInlineDivider()

        // Unread
        SummaryInlineItem(
            icon = com.example.sportapp.R.drawable.ic_rounded_mail,
            value = "$unreadCount",
            label = stringResource(R.string.notifications_summary_unread),
            tint = appColors.accentText,
            clickable = unreadCount > 0,
            onClick = onReadAll
        )
    }
}
