package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

/**
 * Preset de rappel : un offset en minutes + son libellé. `minutes == null` =
 * "Aucun" (rappel désactivé pour cet élément -> on/off par élément).
 */
data class ReminderPreset(val minutes: Int?, val labelRes: Int)

/**
 * Presets standard partagés (Aucun / 5 / 15 / 30 / 1 h) pour les sélecteurs
 * "avant le début / avant la fin" des périodes et le rappel par défaut global.
 * Les tâches (échéance) gardent leur propre liste (incluant "1 jour").
 */
val DEFAULT_REMINDER_PRESETS: List<ReminderPreset> = listOf(
    ReminderPreset(null, R.string.reminder_preset_none),
    ReminderPreset(5, R.string.reminder_preset_5min),
    ReminderPreset(15, R.string.reminder_preset_15min),
    ReminderPreset(30, R.string.reminder_preset_30min),
    ReminderPreset(60, R.string.reminder_preset_1h),
)

/**
 * Sélecteur de rappel réutilisable (chips presets, style app). Extrait de
 * `TaskFormDialog` (R-karpathy 2026-06-08) pour être partagé par les dialogs
 * tâche/période et l'écran Notifications (rappel par défaut).
 */
@Composable
fun ReminderSelector(
    label: String,
    selectedMinutes: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<ReminderPreset> = DEFAULT_REMINDER_PRESETS,
) {
    Column(modifier) {
        Text(text = label, color = appColors.textTertiary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { preset ->
                ReminderChip(
                    labelRes = preset.labelRes,
                    selected = selectedMinutes == preset.minutes,
                    onClick = { onSelect(preset.minutes) },
                )
            }
        }
    }
}

@Composable
private fun ReminderChip(
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) appColors.primaryAction else appColors.bgRecessed)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            color = if (selected) appColors.textPrimary else appColors.textTertiary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
