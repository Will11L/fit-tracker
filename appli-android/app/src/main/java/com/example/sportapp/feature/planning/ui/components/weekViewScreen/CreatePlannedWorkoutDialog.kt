package com.example.sportapp.feature.planning.ui.components.weekViewScreen

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.utils.localizedDayOfWeek
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog

@Composable
fun CreatePlannedWorkoutDialog(
    dayOfWeek: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val canConfirm = name.isNotBlank()

    FormDialog(
        title = stringResource(R.string.week_create_title, localizedDayOfWeek(dayOfWeek)),
        confirmText = stringResource(R.string.week_create_confirm),
        confirmEnabled = canConfirm,
        disabledReason = stringResource(R.string.form_error_name_required),
        onConfirm = {
            onCreate(name.trim())
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        CustomTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.week_create_placeholder)
        )
    }
}
