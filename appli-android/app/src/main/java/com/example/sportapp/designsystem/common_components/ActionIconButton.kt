package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun ActionIconButton(
    iconRes: Int,
    tint: Color = appColors.textPrimary,
    iconSize: Dp = 24.dp,
    boxSize: Dp = 40.dp,
    hasBackground: Boolean = true,
    customBackgroundColor : Color = appColors.bgButton,
    clickable: Boolean = true,
    onClick: () -> Unit = {},
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(boxSize) // 👈 attention : .size() ici va **écraser** un .weight() externe
            .clip(MaterialTheme.shapes.small)
            .background(
                if (hasBackground) customBackgroundColor else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(
                enabled = clickable,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
