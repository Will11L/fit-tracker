package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.utils.localizedStatus
import com.example.sportapp.designsystem.common_components.StatusOption
import com.example.sportapp.designsystem.common_components.StatusPickerDialog
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun ChangeGoalStatusDialog(
    currentStatusRaw: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Statuts normalisés en base (UPPER_CASE, politique 11).
    fun normalize(s: String) = s.trim().uppercase().replace(" ", "_")

    val options = listOf(
        StatusOption("IN_PROGRESS", localizedStatus("IN_PROGRESS"), R.drawable.ic_arrow_progress, orangeMedium),
        StatusOption("DONE", localizedStatus("DONE"), R.drawable.ic_rounded_check, mediumGreen),
        StatusOption("SKIPPED", localizedStatus("SKIPPED"), R.drawable.ic_rounded_close, redMedium),
    )

    StatusPickerDialog(
        title = stringResource(R.string.status_change_goal_title),
        options = options,
        selected = normalize(currentStatusRaw),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
