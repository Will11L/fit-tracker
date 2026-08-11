package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.*

@Composable
fun ExerciseActionBar(
    exercise: Exercise,
    onBack: () -> Unit,
    onFavoriteClick: () -> Unit,
    onSyncClick: () -> Unit,
    onDelavierMethodClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 🔙 Back button
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
            onClick = onBack,
            tint = appColors.textPrimary
        )

        // ⭐ Favoris — mêmes couleurs que ExerciseCard (l'ancien tint textTertiary sur fond
        // textTertiary rendait l'étoile invisible quand pas favori).
        ActionIconButton(
            iconRes = if (exercise.isFavorite) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star,
            onClick = onFavoriteClick,
            tint = appColors.textPrimary,
            hasBackground = true,
            customBackgroundColor = if (exercise.isFavorite) orangeMedium else appColors.textTertiary.copy(alpha = 0.7f)
        )

        // 🔄 Sync
        ActionIconButton(
            iconRes = if (exercise.synced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
            clickable = !exercise.synced,
            onClick = onSyncClick,
            tint = if (exercise.synced) appColors.primaryAction else yellowMedium,
            hasBackground = true,
            customBackgroundColor = appColors.bgRecessed
        )

        // 📖 Delavier
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_book,
            onClick = onDelavierMethodClick,
            tint = appColors.textPrimary,
            hasBackground = true,
            customBackgroundColor = appColors.selectedFill
        )

        // ⋮ Menu
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_more_vert,
            onClick = onMoreClick,
            tint = appColors.textPrimary
        )
    }
}
