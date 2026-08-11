package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.common_components.OptionRow
import com.example.sportapp.designsystem.theme.SessionExerciseScreenBackground
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.core.utils.parseTargetMinimum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleOptionsBottomSheet(
    muscleGoal: MuscleGoal,
    muscleName: String,
    onDismissRequest: () -> Unit,
    onSeeMuscle: () -> Unit,
    onChangeStatus: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDone = muscleGoal.status == "DONE"
    // User feedback runtime 2026-05-09 iter 4 : si DONE et done >= targetMin,
    // l'auto-completion re-declencherait immediatement au moindre passage en
    // IN_PROGRESS (cf. GoalsTabViewModel.shouldAutoComplete). On masque le
    // bouton pour eviter la fausse promesse — l'auto-completion reste
    // l'unique source de verite dans ce cas. Le bouton "Mark as Done"
    // reste lui toujours dispo (utile si done < targetMin et user veut
    // valider manuellement).
    val targetMin = parseTargetMinimum(muscleGoal.target)
    val canManuallyToggleProgress = !(isDone && muscleGoal.done >= targetMin)

    val toggleIcon = if (isDone) R.drawable.ic_arrow_progress else R.drawable.ic_rounded_check
    val toggleLabel = if (isDone) stringResource(R.string.sheet_muscle_options_mark_in_progress)
                      else stringResource(R.string.sheet_muscle_options_mark_done)
    val toggleColor = if (isDone) appColors.selectedFill.copy(alpha = 0.7f) else mediumGreen

    val actions = listOfNotNull(
        SheetAction(
            label = stringResource(R.string.sheet_muscle_options_see),
            iconRes = R.drawable.ic_rounded_eye_tracking,
            color = blueMedium,
            onClick = onSeeMuscle
        ),
        SheetAction(
            label = stringResource(R.string.sheet_muscle_options_change_status),
            iconRes = R.drawable.ic_rounded_edit,
            color = appColors.selectedFill,
            onClick = onChangeStatus
        ),
        if (canManuallyToggleProgress) {
            SheetAction(
                label = toggleLabel,
                iconRes = toggleIcon,
                color = toggleColor,
                onClick = onToggleDone
            )
        } else null,
        SheetAction(
            label = stringResource(R.string.sheet_muscle_options_delete),
            iconRes = R.drawable.ic_rounded_delete_forever,
            color = redMedium,
            onClick = onDelete
        ),
    )

    OptionsBottomSheet(
        title = muscleName,
        actions = actions,
        onDismissRequest = onDismissRequest
    )
}
