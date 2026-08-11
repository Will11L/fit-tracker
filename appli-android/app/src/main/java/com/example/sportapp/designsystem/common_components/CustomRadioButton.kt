package com.example.sportapp.designsystem.common_components

import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium

/**
 * RadioButton stylé app : ring blueMedium quand non sélectionné,
 * fill appColors.primaryAction quand sélectionné.
 */
@Composable
fun CustomRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = RadioButtonDefaults.colors(
            selectedColor = appColors.primaryAction,
            unselectedColor = blueMedium,
            disabledSelectedColor = appColors.primaryAction.copy(alpha = 0.4f),
            disabledUnselectedColor = blueMedium.copy(alpha = 0.4f),
        ),
    )
}
