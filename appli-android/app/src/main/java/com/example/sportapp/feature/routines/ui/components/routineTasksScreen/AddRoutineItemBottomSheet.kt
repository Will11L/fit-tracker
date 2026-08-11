package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*

@Composable
fun AddRoutineItemBottomSheet(
    onDismissRequest: () -> Unit,
    onAddTask: () -> Unit,
    onAddPeriod: () -> Unit
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_add_routine_task),
            iconRes = R.drawable.ic_rounded_list_alt,
            color = blueMedium,
            onClick = onAddTask
        ),
        SheetAction(
            label = stringResource(R.string.sheet_add_routine_period),
            iconRes = R.drawable.ic_rounded_schedule,
            color = appColors.selectedFill,
            onClick = onAddPeriod
        )
    )

    OptionsBottomSheet(
        title = stringResource(R.string.sheet_add_routine_title),
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
