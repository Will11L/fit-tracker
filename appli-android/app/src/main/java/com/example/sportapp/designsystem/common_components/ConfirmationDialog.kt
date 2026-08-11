package com.example.sportapp.designsystem.common_components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmButtonText: String = stringResource(R.string.common_delete),
    dismissButtonText: String = stringResource(R.string.common_cancel),
    confirmButtonColor: Color = redMedium,
    dismissButtonColor: Color = appColors.textTertiary,
    titleColor: Color = appColors.primaryAction,
    messageColor: Color = appColors.textPrimary,
    containerColor: Color = appColors.bgScreen,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = titleColor) },
        text = { Text(message, color = messageColor) },
        confirmButton = {
            DialogPrimaryButton(text = confirmButtonText, onClick = onConfirm, color = confirmButtonColor)
        },
        dismissButton = {
            DialogSecondaryButton(text = dismissButtonText, onClick = onDismiss)
        },
        containerColor = containerColor
    )
}
