package com.example.sportapp.feature.session.ui.components.sessionTab

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun ExerciseOptionsBottomSheet(
    exercise: Exercise,
    onDismissRequest: () -> Unit,
    onViewDetails: () -> Unit,
    onRemoveFromSession: () -> Unit,
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_exercise_see_details),
            iconRes = R.drawable.ic_rounded_eye_tracking,
            color = blueMedium,
            onClick = onViewDetails
        ),
        SheetAction(
            label = stringResource(R.string.sheet_exercise_delete_from_session),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redMedium,
            onClick = onRemoveFromSession
        )
    )

    OptionsBottomSheet(
        title = exercise.name,
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
