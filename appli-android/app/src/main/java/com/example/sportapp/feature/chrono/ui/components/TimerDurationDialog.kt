package com.example.sportapp.feature.chrono.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.HmsWheelPicker
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun TimerDurationDialog(
    initialMillis: Long,
    onConfirm: (millis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val init = remember(initialMillis) { clampMillisToHms(initialMillis) }
    var h by remember(init) { mutableIntStateOf(init.hours) }
    var m by remember(init) { mutableIntStateOf(init.minutes) }
    var s by remember(init) { mutableIntStateOf(init.seconds) }

    val canConfirm = (h + m + s) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.bgScreen,
        title = { Text(text = stringResource(R.string.chrono_dialog_set_timer), color = appColors.primaryAction) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HmsWheelPicker(
                    hours = h,
                    minutes = m,
                    seconds = s,
                    onHoursChange = { h = it },
                    onMinutesChange = { m = it },
                    onSecondsChange = { s = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            DialogPrimaryButton(
                text = stringResource(R.string.common_save),
                enabled = canConfirm,
                onClick = {
                    val millis = ((h * 3600L) + (m * 60L) + s) * 1000L
                    onConfirm(millis)
                },
            )
        },
        dismissButton = {
            DialogSecondaryButton(text = stringResource(R.string.common_cancel), onClick = onDismiss)
        }
    )
}

private data class Hms(val hours: Int, val minutes: Int, val seconds: Int)

private fun clampMillisToHms(ms: Long): Hms {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = (totalSeconds / 3600).coerceIn(0, 23)
    val minutes = ((totalSeconds % 3600) / 60).coerceIn(0, 59)
    val seconds = (totalSeconds % 60).coerceIn(0, 59)
    return Hms(hours, minutes, seconds)
}
