package com.example.sportapp.designsystem.drawer

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.TextUnit

import com.example.sportapp.designsystem.theme.appColors


@Composable
fun DrawerIconCountIndicator(
    iconRes: Int,
    count: Int,
    modifier: Modifier = Modifier,
    showWhenZero: Boolean = false,
    tint: Color = appColors.primaryAction,
    textColor: Color = appColors.primaryAction,
    iconSize: Dp = 16.dp,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    spacing: Dp = 6.dp
) {
    if (!showWhenZero && count <= 0) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )

        Spacer(modifier = Modifier.width(spacing))

        Text(
            text = count.toString(),
            color = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
    }
}
