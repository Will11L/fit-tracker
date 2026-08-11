package com.example.sportapp.feature.session.ui.components.sessionTab

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog

@Composable
fun CreateActualWorkoutDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val isEmpty = trimmed.isEmpty()
    val isRestDay = trimmed.equals("Rest Day", ignoreCase = true)
    val isInvalid = isEmpty || isRestDay
    val disabledReason = when {
        isEmpty -> stringResource(R.string.home_create_workout_error_empty)
        isRestDay -> stringResource(R.string.home_create_workout_error_rest_day)
        else -> null
    }

    FormDialog(
        title = stringResource(R.string.home_create_workout_title),
        confirmText = stringResource(R.string.home_create_workout_start),
        confirmEnabled = !isInvalid,
        disabledReason = disabledReason,
        onConfirm = {
            if (!isInvalid) {
                onCreate(trimmed)
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    ) {
        CustomTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.home_create_workout_placeholder)
        )
    }
}
