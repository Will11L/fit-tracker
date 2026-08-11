package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*

@Composable
fun ExerciseMoreOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    onEditClick: () -> Unit,
    onDelavierMethodClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_exercise_more_edit),
            iconRes = R.drawable.ic_rounded_edit,
            color = blueMedium,
            onClick = onEditClick
        ),
        SheetAction(
            label = stringResource(R.string.sheet_exercise_more_delavier),
            iconRes = R.drawable.ic_rounded_book,
            color = appColors.selectedFill,
            onClick = onDelavierMethodClick
        ),
        SheetAction(
            label = stringResource(R.string.sheet_exercise_more_delete),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redMedium,
            onClick = onDeleteClick
        ),
    )

    OptionsBottomSheet(
        title = stringResource(R.string.sheet_exercise_list_title),
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
