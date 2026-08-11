package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun SetOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    onSupersetClick: () -> Unit,
    onDropSetClick: () -> Unit,
    onBonusSetClick: () -> Unit,
    onChangeStatusClick: () -> Unit
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_set_add_bonus),
            iconRes = R.drawable.ic_add,
            color = appColors.primaryAction,
            onClick = onBonusSetClick
        ),
        SheetAction(
            label = stringResource(R.string.sheet_set_add_drop),
            iconRes = R.drawable.ic_rounded_add_link,
            color = appColors.primaryAction.copy(alpha = 0.75f),
            onClick = onDropSetClick
        ),
        SheetAction(
            label = stringResource(R.string.sheet_set_change_status),
            iconRes = R.drawable.ic_rounded_info,
            color = appColors.selectedFill,
            onClick = onChangeStatusClick
        ),
        SheetAction(
            label = stringResource(R.string.sheet_set_create_superset),
            iconRes = R.drawable.ic_rounded_join,
            color = appColors.selectedFill.copy(alpha = 0.75f),
            onClick = onSupersetClick
        )
    )

    OptionsBottomSheet(
        title = stringResource(R.string.sheet_set_options_title),
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
