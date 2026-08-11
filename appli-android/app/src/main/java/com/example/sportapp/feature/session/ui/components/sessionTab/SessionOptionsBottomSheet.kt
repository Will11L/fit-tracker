package com.example.sportapp.feature.session.ui.components.sessionTab

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*

@Composable
fun SessionOptionsBottomSheet(
    title: String,
    isDone: Boolean,
    onDismissRequest: () -> Unit,
    onRenameActualWorkout: () -> Unit,
    onSeeTodayPlannedWorkout: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    val toggleLabel = if (isDone) stringResource(R.string.sheet_session_mark_undone)
                      else stringResource(R.string.sheet_session_mark_done)
    val toggleIcon =
        if (isDone) R.drawable.ic_arrow_progress else R.drawable.ic_rounded_check
    val toggleColor =
        if (isDone) blueMedium else mediumGreen

    val actions = listOf(
        SheetAction(
            label = toggleLabel,
            iconRes = toggleIcon,
            color = toggleColor,
            onClick = onToggleDone
        ),
        SheetAction(
            label = stringResource(R.string.sheet_session_rename),
            iconRes = R.drawable.ic_rounded_edit,
            color = appColors.selectedFill,
            onClick = onRenameActualWorkout
        ),
        SheetAction(
            label = stringResource(R.string.sheet_session_see_planned),
            iconRes = R.drawable.ic_calendar_month,
            color = appColors.selectedFill,
            onClick = onSeeTodayPlannedWorkout
        ),
        SheetAction(
            label = stringResource(R.string.sheet_session_delete),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redDark,
            onClick = onDelete
        )
    )

    OptionsBottomSheet(
        title = title,
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
