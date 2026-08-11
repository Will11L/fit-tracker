package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors


@Composable
fun HeaderDetailColumn(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    hasBackground: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = appColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (hasBackground) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(appColors.bgSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    color = appColors.textPrimary,
                    fontSize = 14.sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}