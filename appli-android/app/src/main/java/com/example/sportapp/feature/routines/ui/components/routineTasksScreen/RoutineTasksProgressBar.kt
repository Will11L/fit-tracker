package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ProgressBarPrimitive
import com.example.sportapp.designsystem.common_components.progressColor
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.yellowMedium

/**
 * 2026-05-13 v2 : ordre [progress bar -> X/Y -> ✓ -> % -> sync -> +] dans
 * un container appColors.bgRecessed. Inline le progress bar (plus de LabeledProgressBar)
 * pour controler precisement l'ordre. Couleur du % et du bar dependent du
 * progress.
 */
@Composable
fun RoutineTasksProgressBar(
    progress: Float,
    doneCount: Int,
    totalCount: Int,
    isSync: Boolean,
    onSyncClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val percent = (clamped * 100).toInt()
    val color = progressColor(clamped)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1) Progress bar (weight 1f) — R18 : delegue au primitif partage
        // troughColor = bgSurface (boxBlue) car le container parent ici est
        // bgRecessed (thirdBlue) -> sinon la trough est invisible.
        ProgressBarPrimitive(
            progress = clamped,
            color = color,
            modifier = Modifier.weight(1f),
            troughColor = appColors.bgSurface,
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 2) Percent
        Text(
            text = "$percent%",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.width(6.dp))

        // 3) Sync icon
        ActionIconButton(
            iconRes = if (isSync) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
            tint = if (isSync) appColors.primaryAction else yellowMedium,
            hasBackground = false,
            onClick = onSyncClick,
        )

        Spacer(modifier = Modifier.width(6.dp))

        // 4) X/Y en textSecondary (coherent avec les labels TitledDivider).
        Text(
            text = "$doneCount/$totalCount",
            color = appColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.width(2.dp))

        // 5) Check vert (ActionIconButton meme hauteur que sync : 40dp box)
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_check,
            tint = mediumGreen,
            hasBackground = false,
            clickable = false,
        )

        Spacer(modifier = Modifier.width(6.dp))

        // 6) Add
        ActionIconButton(
            iconRes = R.drawable.ic_add,
            tint = appColors.textPrimary,
            onClick = onAddClick,
        )
    }
}
