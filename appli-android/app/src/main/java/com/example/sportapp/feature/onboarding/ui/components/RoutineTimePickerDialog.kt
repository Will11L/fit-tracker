package com.example.sportapp.feature.onboarding.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.appColors

/**
 * TimePicker M3 wrappé dans un AlertDialog stylé app.
 * Utilisé dans OnboardingPreferencesScreen "Default routine time".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.bgScreen,
        title = { Text(text = stringResource(R.string.onboarding_preferences_card_default_time), color = appColors.primaryAction) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = appColors.bgRecessed,
                        clockDialSelectedContentColor = appColors.textPrimary,
                        clockDialUnselectedContentColor = appColors.textTertiary,
                        selectorColor = appColors.primaryAction,
                        containerColor = appColors.bgRecessed,
                        periodSelectorBorderColor = appColors.primaryAction,
                        periodSelectorSelectedContainerColor = appColors.primaryAction,
                        periodSelectorUnselectedContainerColor = appColors.bgRecessed,
                        periodSelectorSelectedContentColor = appColors.textPrimary,
                        periodSelectorUnselectedContentColor = appColors.textTertiary,
                        timeSelectorSelectedContainerColor = appColors.primaryAction,
                        timeSelectorUnselectedContainerColor = appColors.bgRecessed,
                        timeSelectorSelectedContentColor = appColors.textPrimary,
                        timeSelectorUnselectedContentColor = appColors.textTertiary,
                    ),
                )
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = stringResource(R.string.common_save), onClick = { onConfirm(timeState.hour, timeState.minute) })
        },
        dismissButton = {
            DialogSecondaryButton(text = stringResource(R.string.common_cancel), onClick = onDismiss)
        },
    )
}
