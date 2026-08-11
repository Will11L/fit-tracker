package com.example.sportapp.designsystem.common_components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium

/**
 * Switch stylé app : checked = appColors.primaryAction, unchecked = gris foncé,
 * thumb blanc, bordure track invisible mais le checkedBorder est posé pour
 * un trait blueMedium léger en mode unchecked.
 *
 * À utiliser partout où on veut un Switch dans le style sport-app
 * (settings, onboarding, admin, etc.) au lieu du M3 raw.
 */
@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = appColors.textPrimary,
            checkedTrackColor = appColors.primaryAction,
            checkedBorderColor = appColors.primaryAction,
            uncheckedThumbColor = appColors.textPrimary,
            uncheckedTrackColor = appColors.bgRecessed,
            uncheckedBorderColor = blueMedium,
            disabledCheckedThumbColor = appColors.textPrimary.copy(alpha = 0.6f),
            disabledCheckedTrackColor = appColors.primaryAction.copy(alpha = 0.4f),
            disabledCheckedBorderColor = appColors.primaryAction.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = appColors.textPrimary.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = appColors.bgRecessed.copy(alpha = 0.4f),
            disabledUncheckedBorderColor = blueMedium.copy(alpha = 0.4f),
        ),
    )
}
