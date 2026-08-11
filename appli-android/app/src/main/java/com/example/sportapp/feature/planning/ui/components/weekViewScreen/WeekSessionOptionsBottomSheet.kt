package com.example.sportapp.feature.planning.ui.components.weekViewScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun WeekSessionOptionsBottomSheet(
    plannedWorkout: PlannedWorkout,
    isFiller: Boolean = false,
    isDoneThisWeek: Boolean = false,
    onDismissRequest: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleDone: () -> Unit,
    onCreatePlannedWorkout: () -> Unit,
    onDelete: () -> Unit,
) {
    val actions = if (isFiller) {
        listOf(
            SheetAction(
                label = stringResource(R.string.sheet_week_session_plan),
                iconRes = R.drawable.ic_add,
                color = appColors.selectedFill,
                onClick = onCreatePlannedWorkout
            )
        )
    } else {
        val toggleLabel = if (isDoneThisWeek) stringResource(R.string.sheet_session_mark_undone)
                          else stringResource(R.string.sheet_session_mark_done)
        val toggleIcon =
            if (isDoneThisWeek) R.drawable.ic_check_indeterminate_small else R.drawable.ic_rounded_check
        val toggleColor = if (isDoneThisWeek) orangeMedium else mediumGreen

        listOf(
            SheetAction(
                label = stringResource(R.string.sheet_week_session_rename),
                iconRes = R.drawable.ic_rounded_edit,
                color = blueMedium,
                onClick = onRename
            ),
            SheetAction(
                label = toggleLabel,
                iconRes = toggleIcon,
                color = toggleColor,
                onClick = onToggleDone
            ),
            SheetAction(
                label = stringResource(R.string.sheet_week_session_duplicate),
                iconRes = R.drawable.ic_rounded_content_copy,
                color = appColors.selectedFill,
                onClick = onDuplicate
            ),
            SheetAction(
                label = stringResource(R.string.sheet_week_session_delete),
                iconRes = R.drawable.ic_rounded_delete_forever,
                color = redMedium,
                onClick = onDelete
            )
        )
    }

    OptionsBottomSheet(
        title = plannedWorkout.name,
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
