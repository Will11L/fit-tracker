package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun PriorityIcon(
    priority: String,
    isDisabled: Boolean = false,
    clickable: Boolean = false,
    showBorder: Boolean = true,
    onClick: () -> Unit = {}
) {
    val priorityUpper = priority.uppercase()

    val iconId = when (priorityUpper) {
        "HIGH" -> R.drawable.ic_north
        "MEDIUM" -> R.drawable.ic_north_east
        "LOW" -> R.drawable.ic_south
        else -> R.drawable.ic_check_indeterminate_small
    }

    val defaultTint = when (priorityUpper) {
        "HIGH" -> appColors.priorityHigh
        "MEDIUM" -> appColors.priorityMedium
        "LOW" -> appColors.priorityLow
        else -> appColors.textTertiary
    }

    val tintColor = if (isDisabled) appColors.textTertiary else defaultTint

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .let {
                if (showBorder) it.border(width = 1.dp, color = tintColor, shape = MaterialTheme.shapes.extraSmall) else it
            }
            .let {
                if (clickable) it.clickable(onClick = onClick) else it
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(20.dp)
        )
    }
}