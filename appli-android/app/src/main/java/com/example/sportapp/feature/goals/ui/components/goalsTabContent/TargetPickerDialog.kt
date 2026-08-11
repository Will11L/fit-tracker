package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.HorizontalNumberPicker
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.appColors


@Composable
fun TargetPickerDialog(
    currentTarget: String,
    onDismiss: () -> Unit,
    onTargetSelected: (String) -> Unit
) {
    val options = listOf("12+", "6-12", "3-5")
    val selected = remember { mutableStateOf(currentTarget) }

    val customRange = 1..20
    val customInt = remember { mutableStateOf(currentTarget.toIntOrNull() ?: 12) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goals_target_title), color = appColors.primaryAction) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 🎯 Preset options
                options.forEach { option ->
                    val isSelected = selected.value == option
                    val color = if (isSelected) appColors.primaryAction else appColors.textPrimary
                    val iconRes = R.drawable.ic_rounded_check

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                selected.value = option
                                onTargetSelected(option)
                            }
                            .background(
                                color = if (isSelected) color.copy(alpha = 0.1f) else appColors.bgRecessed,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option, color = color)
                        if (isSelected) {
                            ActionIconButton(
                                iconRes = iconRes,
                                tint = color,
                                hasBackground = false,
                                clickable = false
                            )
                        }
                    }
                }

                // ➕ Custom number picker
                Text(stringResource(R.string.goals_target_custom_label), color = appColors.textSecondary)
                HorizontalNumberPicker(
                    range = customRange,
                    selected = customInt.value,
                    onValueChange = {
                        customInt.value = it
                        selected.value = it.toString()
                        onTargetSelected(it.toString())
                    },
                    targetRange = null,
                    scrollOnSelect = true
                )
            }
        },
        confirmButton = {
            DialogSecondaryButton(text = stringResource(R.string.common_close), onClick = onDismiss)
        },
        containerColor = appColors.bgScreen
    )
}
