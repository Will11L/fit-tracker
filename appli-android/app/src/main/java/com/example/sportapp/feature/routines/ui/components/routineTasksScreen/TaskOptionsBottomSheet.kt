package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun TaskOptionsBottomSheet(
    task: Task,
    onDismissRequest: () -> Unit,
    onEditTask: () -> Unit,
    onDeleteTask: () -> Unit
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_task_edit),
            iconRes = R.drawable.ic_rounded_edit,
            color = blueMedium,
            onClick = onEditTask
        ),
        SheetAction(
            label = stringResource(R.string.sheet_task_delete),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redMedium,
            onClick = onDeleteTask
        )
    )

    OptionsBottomSheet(
        title = task.title,
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
