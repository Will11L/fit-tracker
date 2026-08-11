package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.utils.localizedStatus
import com.example.sportapp.designsystem.common_components.StatusOption
import com.example.sportapp.designsystem.common_components.StatusPickerDialog
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun ChangeSetStatusDialog(
    showDialog: MutableState<Boolean>,
    currentSet: ActualWorkoutSet,
    onStatusSelected: (String) -> Unit
) {
    val options = listOf(
        StatusOption("NOT_STARTED", localizedStatus("NOT_STARTED"), R.drawable.ic_rounded_help, appColors.textTertiary),
        StatusOption("IN_PROGRESS", localizedStatus("IN_PROGRESS"), R.drawable.ic_arrow_progress, orangeMedium),
        StatusOption("DONE", localizedStatus("DONE"), R.drawable.ic_rounded_check_circle, mediumGreen),
        StatusOption("SKIPPED", localizedStatus("SKIPPED"), R.drawable.ic_rounded_cancel, redMedium),
    )

    StatusPickerDialog(
        title = stringResource(R.string.status_change_set_title),
        options = options,
        selected = currentSet.status,
        onConfirm = {
            onStatusSelected(it)
            showDialog.value = false
        },
        onDismiss = { showDialog.value = false },
    )
}
