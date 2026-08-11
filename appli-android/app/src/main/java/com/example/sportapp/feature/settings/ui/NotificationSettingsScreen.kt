package com.example.sportapp.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ReminderSelector
import com.example.sportapp.feature.settings.SettingsViewModel
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        SettingsSubScreenHeader(
            title = stringResource(R.string.drawer_item_notifications),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard(title = stringResource(R.string.drawer_item_notifications)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toggle_vibrate_title),
                    description = stringResource(R.string.settings_toggle_vibrate_desc),
                    checked = settings.vibrateOnInAppNotification,
                    onCheckedChange = viewModel::setVibrate,
                )
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toggle_sound_title),
                    description = stringResource(R.string.settings_toggle_sound_desc),
                    checked = settings.soundOnInAppNotification,
                    onCheckedChange = viewModel::setSound,
                )
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toggle_overlay_title),
                    description = stringResource(R.string.settings_toggle_overlay_desc),
                    checked = settings.showInAppNotificationOverlay,
                    onCheckedChange = viewModel::setOverlay,
                )
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toggle_phone_title),
                    description = stringResource(R.string.settings_toggle_phone_desc),
                    checked = settings.showPhoneNotifications,
                    onCheckedChange = viewModel::setPhone,
                )
            }

            SettingsSectionCard(title = stringResource(R.string.settings_section_categories)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toggle_notify_tasks_title),
                    description = stringResource(R.string.settings_toggle_notify_tasks_desc),
                    checked = settings.notifyTasks,
                    onCheckedChange = viewModel::setNotifyTasks,
                )
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toggle_notify_timers_title),
                    description = stringResource(R.string.settings_toggle_notify_timers_desc),
                    checked = settings.notifyTimers,
                    onCheckedChange = viewModel::setNotifyTimers,
                )
                SettingsToggleRow(
                    title = stringResource(R.string.settings_toggle_notify_routines_title),
                    description = stringResource(R.string.settings_toggle_notify_routines_desc),
                    checked = settings.notifyRoutines,
                    onCheckedChange = viewModel::setNotifyRoutines,
                )
            }

            SettingsSectionCard(title = stringResource(R.string.settings_default_reminder_title)) {
                Text(
                    text = stringResource(R.string.settings_default_reminder_desc),
                    color = appColors.textTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ReminderSelector(
                    label = stringResource(R.string.settings_default_reminder_label),
                    selectedMinutes = settings.defaultReminderMinutesBefore,
                    onSelect = viewModel::setDefaultReminder,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
