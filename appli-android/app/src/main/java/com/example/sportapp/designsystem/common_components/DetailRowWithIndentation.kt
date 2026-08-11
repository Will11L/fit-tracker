package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun DetailRowWithIndentation(
    iconRes: Int,
    iconColor: Color = appColors.textTertiary,
    label: String,
    labelColor: Color = appColors.textTertiary,
    value: String,
    valueColor: Color = appColors.textTertiary
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )

        // Texte avec label coloré + valeur colorée, avec indentation
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = labelColor)) {
                    append("$label: ")
                }
                withStyle(style = SpanStyle(color = valueColor, fontWeight = FontWeight.Medium)) {
                    append(value)
                }
            },
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
