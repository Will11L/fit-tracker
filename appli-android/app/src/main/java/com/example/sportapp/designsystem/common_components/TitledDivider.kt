package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun TitledDivider(
    title: String,
    color: Color = appColors.divider,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            color = color,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 8.dp),
            color = color,
            fontWeight = FontWeight.SemiBold
        )

        HorizontalDivider(
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}
