package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.example.sportapp.designsystem.theme.*

@Composable
fun PriorityPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val priorities = listOf("HIGH", "MEDIUM", "LOW")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(appColors.bgRecessed)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Priority",
            color = appColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            priorities.forEach { priority ->
                val isSelected = priority.equals(selected, ignoreCase = true)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) appColors.primaryAction else appColors.divider,
                                shape = MaterialTheme.shapes.extraSmall
                            )
                            .clickable { onSelect(priority) },
                        contentAlignment = Alignment.Center
                    ) {
                        PriorityIcon(priority = priority, showBorder = false)
                    }

                    Text(
                        text = priority.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        color = if (isSelected) appColors.primaryAction else appColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
