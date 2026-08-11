package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.designsystem.theme.*

@Composable
fun RoutinePeriodOptionsBottomSheet(
    period: RoutinePeriod,
    onDismissRequest: () -> Unit,
    onEditPeriod: () -> Unit,
    onDeletePeriod: () -> Unit
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_period_edit),
            iconRes = R.drawable.ic_rounded_edit,
            color = blueMedium,
            onClick = onEditPeriod
        ),
        SheetAction(
            label = stringResource(R.string.sheet_period_delete),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redMedium,
            onClick = onDeletePeriod
        )
    )

    OptionsBottomSheet(
        title = period.name,
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
