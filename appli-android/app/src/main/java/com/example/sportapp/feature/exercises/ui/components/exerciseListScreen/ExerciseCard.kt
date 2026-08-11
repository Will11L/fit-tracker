package com.example.sportapp.feature.exercises.ui.components.exerciseListScreen

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.DetailRow
import com.example.sportapp.designsystem.common_components.DetailRowWithIndentation
import com.example.sportapp.designsystem.common_components.GenericEntityCard
import com.example.sportapp.designsystem.theme.WeekViewProgramCardBackground
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium

/**
 * Wrapper R16 — délègue à `GenericEntityCard` (slot-based) le rendu canonique
 * Header + Details + Actions. Spécifique Exercise : fond `WeekViewProgramCardBackground`,
 * étoile favori dans le header, 9 lignes de détails, 5 actions.
 */
@Composable
fun ExerciseCard(
    modifier: Modifier = Modifier,
    exercise: Exercise,
    equipments: List<Equipment> = emptyList(),
    muscles: List<Muscle> = emptyList(),
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onNavigate: () -> Unit,
) {
    val isPendingDeletion = exercise.pendingDeletion

    GenericEntityCard(
        modifier = modifier,
        title = exercise.name,
        iconRes = R.drawable.ic_exercise,
        isPendingDeletion = isPendingDeletion,
        cardBackground = WeekViewProgramCardBackground,
        headerTrailing = {
            Icon(
                painter = painterResource(
                    id = if (exercise.isFavorite) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star
                ),
                contentDescription = "Favorite",
                tint = when {
                    isPendingDeletion -> appColors.textTertiary
                    exercise.isFavorite -> orangeMedium
                    else -> Color.Transparent
                },
                modifier = Modifier.size(24.dp)
            )
        },
        detailsContent = {
            DetailRow(
                iconRes = R.drawable.ic_rounded_repeat,
                label = "Reps",
                value = exercise.recommendedReps ?: "N/A",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_rounded_format_list_numbered,
                label = "Sets",
                value = exercise.recommendedSets?.toString() ?: "N/A",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_rounded_bedtime,
                label = "Rest Time",
                value = exercise.restTimeSeconds?.let { "$it sec" } ?: "N/A",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_timer,
                label = "Duration",
                value = exercise.durationInSeconds?.let { "$it sec" } ?: "N/A",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_rounded_neurology,
                label = "Muscles",
                value = if (muscles.isNotEmpty()) muscles.joinToString(", ") { it.name } else "None",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_exercise,
                label = "Equipment",
                value = if (equipments.isNotEmpty()) equipments.joinToString(", ") { it.name } else "None",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_calendar_today,
                label = "Last done",
                value = exercise.lastDone ?: "Never",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_rounded_info,
                label = "Updated at",
                value = exercise.updatedAt ?: "Unknown",
                valueColor = appColors.textTertiary
            )
            DetailRowWithIndentation(
                iconRes = R.drawable.ic_rounded_list_alt,
                label = "Description",
                value = exercise.description?.takeIf { it.isNotBlank() } ?: "No description available",
                valueColor = appColors.textTertiary
            )
        },
        actions = {
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_delete_sweep,
                clickable = !isPendingDeletion,
                onClick = onDelete,
                customBackgroundColor = if (isPendingDeletion) appColors.textSecondary else redMedium,
                tint = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary
            )
            ActionIconButton(
                iconRes = if (exercise.isFavorite) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star,
                clickable = !isPendingDeletion,
                onClick = onToggleFavorite,
                customBackgroundColor = when {
                    isPendingDeletion -> appColors.textSecondary
                    exercise.isFavorite -> orangeMedium
                    else -> appColors.textTertiary.copy(alpha = 0.7f)
                },
                tint = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary
            )
            ActionIconButton(
                iconRes = if (exercise.synced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                clickable = !exercise.synced && !isPendingDeletion,
                onClick = onSync,
                iconSize = 30.dp,
                customBackgroundColor = Color.Transparent,
                tint = if (exercise.synced) appColors.primaryAction else yellowMedium
            )
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_edit,
                clickable = !isPendingDeletion,
                onClick = onEdit,
                customBackgroundColor = if (isPendingDeletion) appColors.textSecondary else mediumGreen,
                tint = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary
            )
            ActionIconButton(
                iconRes = R.drawable.ic_arrow_right_alt,
                clickable = !isPendingDeletion,
                onClick = onNavigate,
                customBackgroundColor = if (isPendingDeletion) appColors.textSecondary else blueMedium,
                tint = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary
            )
        }
    )
}
