package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun ActionIconWithTextButton(
    iconRes: Int,
    text: String,
    tint: Color = appColors.textPrimary,
    textColor: Color = appColors.textPrimary,
    iconSize: Dp = 24.dp,
    backgroundColor: Color = appColors.bgButton,
    hasBackground: Boolean = true,
    /** Bord optionnel (1 dp) — look « bouton secondaire » (ex. toggle Archivés OFF). */
    borderColor: Color? = null,
    clickable: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (hasBackground) backgroundColor else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, MaterialTheme.shapes.small)
                else Modifier
            )
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 14.sp
            )
        }
    }
}
