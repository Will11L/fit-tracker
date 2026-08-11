package com.example.sportapp.feature.planning.ui.components.plannedWorkoutScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.EntityListRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium

@Composable
fun PlannedExerciseRow(
    exercise: Exercise,
    plannedWorkoutExercise: PlannedWorkoutExercise,
    backgroundColor: Color,
    nameBoxColor: Color = Color.Transparent,
    onClickOptions: (Exercise) -> Unit,
) {
    EntityListRow(
        isPendingDeletion = plannedWorkoutExercise.pendingDeletion,
        backgroundColor = backgroundColor,
        nameBoxColor = nameBoxColor,
        name = exercise.name,
        nameWeight = 2.6f,
        onNameClick = { onClickOptions(exercise) },
        verticalPadding = 4.dp,
        trailingContent = {
            // 👯‍♂️ Sync + Status icons group
            Box(
                modifier = Modifier
                    .weight(1.4f) // 2 icons (0.8 + 0.8)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // ☁️ Sync status
                    if (!plannedWorkoutExercise.synced) {
                        ActionIconButton(
                            iconRes = R.drawable.ic_cloud_off,
                            tint = yellowMedium,
                            iconSize = 20.dp,
                            hasBackground = false,
                            clickable = false
                        )
                    } else {
                        ActionIconButton(
                            iconRes = R.drawable.ic_cloud_done,
                            tint = appColors.primaryAction,
                            iconSize = 20.dp,
                            hasBackground = false,
                            clickable = false
                        )
                    }

                    // 🔍 Status icon
                    val (icon, iconTint) = when {
                        plannedWorkoutExercise.pendingDeletion -> {
                            R.drawable.ic_rounded_delete_sweep to appColors.textTertiary
                        }
                        plannedWorkoutExercise.ignored -> {
                            R.drawable.ic_rounded_close to orangeMedium
                        }
                        else -> {
                            when (plannedWorkoutExercise.status.replace(" ", "_").uppercase()) {
                                "DONE" -> R.drawable.ic_rounded_check to mediumGreen
                                "PLANNED" -> R.drawable.ic_arrow_progress to blueMedium
                                "SKIPPED" -> R.drawable.ic_rounded_cancel to redMedium
                                "NOT_STARTED" -> R.drawable.ic_rounded_not_started to orangeMedium
                                else -> R.drawable.ic_rounded_info to appColors.textTertiary
                            }
                        }
                    }

                    ActionIconButton(
                        iconRes = icon,
                        tint = iconTint,
                        iconSize = 20.dp,
                        hasBackground = false,
                        clickable = false,
                    )
                }
            }

            // 🔁 Sets × Reps
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.4f)
                    .padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "${plannedWorkoutExercise.sets} × ${plannedWorkoutExercise.reps}",
                    color = if (plannedWorkoutExercise.pendingDeletion) appColors.textTertiary else appColors.textPrimary,
                    fontSize = 14.sp
                )
            }
        }
    )
}
