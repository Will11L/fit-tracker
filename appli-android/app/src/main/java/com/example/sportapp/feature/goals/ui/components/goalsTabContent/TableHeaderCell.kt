package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun TableHeaderCell(title: String, modifier: Modifier) {
    Text(
        text = title,
        //fontSize = MaterialTheme.typography.bodySmall.fontSize, // 👈 plus petit
        modifier = modifier.height(IntrinsicSize.Min),
        color = appColors.textSecondary,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1 // 👈 s'assure qu'il reste sur une ligne
    )
}
