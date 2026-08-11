package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun ActionTextButton(
    text: String,
    textColor: Color = appColors.textPrimary,
    hasBackground: Boolean,
    backgroundColor: Color = appColors.bgButton,
    clickable: Boolean = true,  // aligné avec ActionIconButton + ActionIconWithTextButton (c'est un bouton)
    onClick: () -> Unit = {},
    fontSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (hasBackground) backgroundColor else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(enabled = clickable, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize
        )
    }
}
