package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*

/**
 * Checkbox custom de l'app (atome partage, au meme niveau que [CustomSwitch] /
 * [CustomRadioButton]). Box 44dp (clip shapes.small) + Image de l'icone Material
 * check_box, tintee. 3 etats : unchecked (transparent + outline divider),
 * checked (fond mediumGreen + check text/primary), pendingDeletion (close
 * text/tertiary). Couleurs / icones overridables par le caller.
 *
 * 2026-06-02 : promu depuis feature/routines/RoutineCheckboxButton vers
 * designsystem/common_components (atome reutilisable, appele par RoutineTaskRow,
 * DayTasksBottomSheet, etc.).
 */
@Composable
fun CustomCheckbox(
    checked: Boolean,
    enabled: Boolean,
    pendingDeletion: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,

    // 🎨 customisation
    backgroundColor: Color? = null,          // null => auto
    iconTint: Color? = null,                 // null => auto
    uncheckedIconRes: Int = R.drawable.ic_rounded_check_box_outline_blank,
    checkedIconRes: Int = R.drawable.ic_rounded_check_box,
    pendingDeletionIconRes: Int = R.drawable.ic_rounded_close,

    onClick: () -> Unit
) {
    val resolvedBackground = when {
        pendingDeletion -> Color.Transparent
        backgroundColor != null -> backgroundColor
        !enabled -> redMedium
        checked -> mediumGreen
        else -> Color.Transparent // ✅ checkbox “vide” par défaut sans fond
    }

    val iconRes = when {
        pendingDeletion -> pendingDeletionIconRes
        checked -> checkedIconRes
        else -> uncheckedIconRes
    }

    val resolvedTint = when {
        pendingDeletion -> appColors.textTertiary
        iconTint != null -> iconTint
        !enabled -> appColors.textPrimary
        checked -> appColors.textPrimary
        else -> appColors.divider // ✅ unchecked: gris/soft par défaut
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(resolvedBackground)
            .clickable(enabled = !pendingDeletion && enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(resolvedTint)
        )
    }
}
