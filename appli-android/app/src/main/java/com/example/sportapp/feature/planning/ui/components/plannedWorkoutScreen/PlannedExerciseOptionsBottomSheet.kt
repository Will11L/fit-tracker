package com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.designsystem.theme.*

@Composable
fun PlannedExerciseOptionsBottomSheet(
    exercise: Exercise,
    onDismissRequest: () -> Unit,
    onViewDetails: () -> Unit,
    onChangeStatus: () -> Unit,
    onRemoveFromPlannedWorkout: () -> Unit,
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_exercise_see_details),
            iconRes = R.drawable.ic_rounded_eye_tracking,
            color = blueMedium,
            onClick = onViewDetails
        ),
        SheetAction(
            label = stringResource(R.string.sheet_planned_exercise_change_status),
            iconRes = R.drawable.ic_rounded_edit,
            color = appColors.selectedFill,
            onClick = onChangeStatus
        ),
        SheetAction(
            label = stringResource(R.string.sheet_planned_exercise_remove),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redMedium,
            onClick = onRemoveFromPlannedWorkout
        )
    )

    OptionsBottomSheet(
        title = exercise.name,
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
