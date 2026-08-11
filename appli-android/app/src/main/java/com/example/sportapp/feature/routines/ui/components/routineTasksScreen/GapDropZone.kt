package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GapDropZone(
    key: String,
    isActive: Boolean,
    visualHeight: Dp,
    hitBoxHeight: Dp,
    onHover: () -> Unit,
    onExit: () -> Unit,
    onDropTaskUUID: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // ✅ La hauteur VISUELLE (petite) peut s'animer
    val animatedVisualHeight by animateDpAsState(
        targetValue = if (isActive) visualHeight else 2.dp,
        animationSpec = tween(120),
        label = "gapVisual"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ✅ IMPORTANT: le layout reste petit -> pas de gros espace
            .height(animatedVisualHeight),
        contentAlignment = Alignment.Center
    ) {
        // ✅ Hitbox PLUS GRANDE, sans impacter la hauteur du layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hitBoxHeight) // <- mord sur les rows
                .dropTargetForGap(
                    key = key,
                    onHover = onHover,
                    onExit = onExit,
                    onDropTaskUUID = onDropTaskUUID
                )
        )
    }
}
