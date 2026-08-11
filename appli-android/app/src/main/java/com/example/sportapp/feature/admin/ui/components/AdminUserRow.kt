package com.example.sportapp.feature.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.admin.data.AdminUserDto
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun AdminUserRow(
    user: AdminUserDto,
    isCurrentUser: Boolean,
    onToggleClick: () -> Unit,
) {
    val displayName = listOfNotNull(user.firstName, user.lastName)
        .joinToString(" ").ifBlank { user.username }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(appColors.bgRecessed)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = appColors.textPrimary
                )
                if (isCurrentUser) {
                    Text(
                        text = stringResource(R.string.admin_users_you_chip),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors.primaryAction
                    )
                }
            }
            Text(
                text = "@${user.username}  •  id ${user.id}",
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textPrimary.copy(alpha = 0.6f)
            )
        }

        Switch(
            checked = user.isAdmin,
            // Self-protect UI : on ne peut pas toggle son propre admin (cohérent
            // avec la 400 serveur self-demote, et bloque aussi self-promote = nop
            // car déjà admin de toute façon).
            enabled = !isCurrentUser,
            onCheckedChange = { onToggleClick() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = appColors.textPrimary,
                checkedTrackColor = appColors.primaryAction,
                uncheckedThumbColor = appColors.textPrimary,
                uncheckedTrackColor = appColors.bgRecessed,
                disabledCheckedTrackColor = appColors.primaryAction.copy(alpha = 0.4f),
                disabledUncheckedTrackColor = appColors.bgRecessed.copy(alpha = 0.4f),
            )
        )
    }
}
