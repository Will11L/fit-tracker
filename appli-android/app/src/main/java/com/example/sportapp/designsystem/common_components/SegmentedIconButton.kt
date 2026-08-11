package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue

/**
 * Bouton-segment d'un toggle segmenté : icône dans un Box bordé, sélectionné =
 * fond [primaryAction]. Canonique partagé — remplace les ex-doublons internes
 * `SegmentIconButton` (stats) et `GoalsSegmentIconButton` (goals) (R5).
 * Les 2 variantes ne diffèrent que par [width], [iconSize] et
 * [unselectedBorderColor] (défauts = variante stats).
 */
@Composable
fun SegmentedIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    iconSize: Dp = 18.dp,
    unselectedBorderColor: Color = appColors.textSecondary.copy(alpha = 0.6f),
) {
    val shape = RoundedCornerShape(6.dp)
    val bg = if (selected) appColors.primaryAction else Color.Transparent
    val borderColor = if (selected) appColors.primaryAction else unselectedBorderColor
    val fg = if (selected) appColors.textPrimary else lightGrayBlue
    Box(
        modifier = modifier
            .size(width = width, height = 30.dp)
            .clip(shape)
            .background(bg)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = fg,
            modifier = Modifier.size(iconSize),
        )
    }
}
