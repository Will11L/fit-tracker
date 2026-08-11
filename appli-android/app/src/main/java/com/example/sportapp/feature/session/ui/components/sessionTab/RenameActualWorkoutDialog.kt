package com.example.sportapp.feature.session.ui.components.sessionTab

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog

@Composable
fun RenameActualWorkoutDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val canConfirm = name.isNotBlank()

    FormDialog(
        title = stringResource(R.string.session_rename_title),
        confirmText = stringResource(R.string.common_save),
        confirmEnabled = canConfirm,
        disabledReason = stringResource(R.string.form_error_name_required),
        onConfirm = {
            onConfirm(name.trim()) // le parent ferme le dialog
        },
        onDismiss = onDismiss,
    ) {
        CustomTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.session_rename_placeholder),
            label = stringResource(R.string.session_rename_label),
            singleLine = true
        )
    }
}
