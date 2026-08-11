package com.example.sportapp.feature.muscles.ui.components.muscleListScreen

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.DetailRow
import com.example.sportapp.designsystem.common_components.GenericEntityCard
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium

/**
 * Wrapper R16 — délègue à `GenericEntityCard` (slot-based) le rendu canonique
 * Header + Details + Actions. Spécifique Muscle : fond par défaut, étoile favori
 * dans le header, 3 lignes de détails, 5 actions.
 */
@Composable
fun MuscleCard(
    modifier: Modifier = Modifier,
    muscle: Muscle,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onNavigate: () -> Unit,
) {
    val isPendingDeletion = muscle.pendingDeletion

    GenericEntityCard(
        modifier = modifier,
        title = muscle.name,
        iconRes = R.drawable.ic_rounded_neurology,
        isPendingDeletion = isPendingDeletion,
        headerTrailing = {
            Icon(
                painter = painterResource(
                    id = if (muscle.isFavorite) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star
                ),
                contentDescription = "Favorite",
                tint = when {
                    isPendingDeletion -> appColors.textTertiary
                    muscle.isFavorite -> orangeMedium
                    else -> Color.Transparent
                },
                modifier = Modifier.size(24.dp)
            )
        },
        detailsContent = {
            DetailRow(
                iconRes = R.drawable.ic_rounded_info,
                label = "UUID",
                value = muscle.uuid,
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_rounded_book,
                label = "Muscle zone",
                value = muscle.zone ?: "Unknown",
                valueColor = appColors.textTertiary
            )
            DetailRow(
                iconRes = R.drawable.ic_timer,
                label = "Updated at",
                value = muscle.updatedAt ?: "Unknown",
                valueColor = appColors.textTertiary
            )
        },
        actions = {
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_delete_forever,
                clickable = !isPendingDeletion,
                onClick = onDelete,
                customBackgroundColor = if (isPendingDeletion) appColors.textSecondary else redMedium,
                tint = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary
            )
            ActionIconButton(
                iconRes = if (muscle.isFavorite) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star,
                clickable = !isPendingDeletion,
                onClick = onToggleFavorite,
                customBackgroundColor = when {
                    isPendingDeletion -> appColors.textSecondary
                    muscle.isFavorite -> orangeMedium
                    else -> appColors.textTertiary.copy(alpha = 0.7f)
                },
                tint = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary
            )
            ActionIconButton(
                iconRes = if (muscle.synced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                clickable = !muscle.synced && !isPendingDeletion,
                onClick = onSync,
                iconSize = 30.dp,
                customBackgroundColor = Color.Transparent,
                tint = if (muscle.synced) appColors.primaryAction else yellowMedium
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
