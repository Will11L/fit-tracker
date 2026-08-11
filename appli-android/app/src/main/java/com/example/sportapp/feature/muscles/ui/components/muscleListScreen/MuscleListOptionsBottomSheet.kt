package com.example.sportapp.feature.muscles.ui.components.muscleListScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*

@Composable
fun MuscleListOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    onAddSample: () -> Unit,
    onClearAll: () -> Unit,
    onExport: () -> Unit,
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_muscle_list_add_sample),
            iconRes = R.drawable.ic_add,
            color = blueMedium,
            onClick = onAddSample
        ),
        SheetAction(
            label = stringResource(R.string.sheet_exercise_list_export),
            iconRes = R.drawable.ic_rounded_share,
            color = appColors.selectedFill,
            onClick = onExport
        ),
        SheetAction(
            label = stringResource(R.string.sheet_exercise_list_clear),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redMedium,
            onClick = onClearAll
        )
    )

    OptionsBottomSheet(
        title = stringResource(R.string.sheet_muscle_list_title),
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
