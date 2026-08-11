package com.example.sportapp.feature.planning.ui.components.weekViewScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*

@Composable
fun WeekCompletionBottomSheet(
    onDismissRequest: () -> Unit,
    onSyncAll: () -> Unit,
    onMarkAllDone: () -> Unit,
    onMarkAllUndone: () -> Unit
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_week_sync_sessions),
            iconRes = R.drawable.ic_rounded_cloud_upload,
            color = appColors.primaryAction,
            onClick = onSyncAll
        ),
        SheetAction(
            label = stringResource(R.string.sheet_week_mark_done),
            iconRes = R.drawable.ic_rounded_check,
            color = mediumGreen,
            onClick = onMarkAllDone
        ),
        SheetAction(
            label = stringResource(R.string.sheet_week_mark_undone),
            iconRes = R.drawable.ic_rounded_close,
            color = orangeMedium,
            onClick = onMarkAllUndone
        )
    )

    OptionsBottomSheet(
        title = stringResource(R.string.sheet_week_completion_title),
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
