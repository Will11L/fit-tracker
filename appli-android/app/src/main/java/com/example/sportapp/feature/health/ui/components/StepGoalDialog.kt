package com.example.sportapp.feature.health.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FormDialog

/** Dialog d'édition de l'objectif de pas du jour (saisie numérique, style app). */
@Composable
fun StepGoalDialog(
    current: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current?.toString() ?: "") }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed > 0

    FormDialog(
        title = stringResource(R.string.health_dash_goal_dialog_title),
        confirmText = stringResource(R.string.health_dash_goal_save),
        onConfirm = { parsed?.let(onConfirm) },
        onDismiss = onDismiss,
        confirmEnabled = valid,
        disabledReason = if (!valid) stringResource(R.string.health_dash_goal_invalid) else null,
    ) {
        CustomTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
            placeholder = stringResource(R.string.health_dash_goal_placeholder),
            label = stringResource(R.string.health_dash_goal_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
