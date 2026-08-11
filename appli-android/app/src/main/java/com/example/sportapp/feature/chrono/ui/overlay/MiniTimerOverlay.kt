package com.example.sportapp.feature.chrono.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.chrono.domain.TimerStateMachine
import com.example.sportapp.feature.chrono.ui.ChronoScreenViewModel
import com.example.sportapp.feature.chrono.ui.components.TimerCircularDisplay
import com.example.sportapp.feature.chrono.utils.compactRemainingText
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium
import kotlin.math.roundToInt

@Composable
fun MiniTimerOverlay(
    viewModel: ChronoScreenViewModel,
    onOpenChrono: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingMillis by viewModel.remainingMillis.collectAsState()
    val durationMillis by viewModel.timerDurationMillis.collectAsState()
    val state by viewModel.timerState.collectAsState()

    if (state == TimerStateMachine.State.IDLE) return

    val leftIcon = R.drawable.ic_rounded_reset
    val leftBg = blueMedium

    val rightIcon = when (state) {
        TimerStateMachine.State.RUNNING -> R.drawable.ic_rounded_pause_circle
        else -> R.drawable.ic_rounded_play_circle
    }
    val rightBg = when (state) {
        TimerStateMachine.State.RUNNING -> redMedium
        else -> appColors.primaryAction
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        var widgetSize by remember { mutableStateOf(IntSize(0, 0)) }
        var offset by remember { mutableStateOf(Offset(0f, 0f)) }

        LaunchedEffect(widgetSize, screenW, screenH) {
            if (widgetSize.width > 0 && widgetSize.height > 0 && offset == Offset(0f, 0f)) {
                val bottomOffset = with(density) { 205.dp.toPx() }
                val startX = ((screenW - widgetSize.width) / 2f).coerceAtLeast(0f)
                val startY = (screenH - widgetSize.height - bottomOffset).coerceAtLeast(0f)
                offset = Offset(startX, startY)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .onSizeChanged { widgetSize = it }
                .pointerInput(screenW, screenH, widgetSize) {
                    detectDragGestures { _, dragAmount ->
                        val maxX = (screenW - widgetSize.width).coerceAtLeast(0f)
                        val maxY = (screenH - widgetSize.height).coerceAtLeast(0f)
                        val newX = (offset.x + dragAmount.x).coerceIn(0f, maxX)
                        val newY = (offset.y + dragAmount.y).coerceIn(0f, maxY)
                        offset = Offset(newX, newY)
                    }
                }
                .border(1.5.dp, blueMedium, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(appColors.bgRecessed)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onOpenChrono() }
            )
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionIconButton(
                    iconRes = leftIcon,
                    customBackgroundColor = leftBg,
                    onClick = { viewModel.onTimerLeftButton() }
                )
                TimerCircularDisplay(
                    remainingMillis = remainingMillis,
                    durationMillis = durationMillis,
                    state = state,
                    diameter = 40.dp,
                    barLength = 8.dp,
                    barWidth = 1.5.dp,
                    segmentCount = 30,
                    centerText = compactRemainingText(remainingMillis),
                    centerTextStyle = MaterialTheme.typography.labelMedium,
                    centerTextColor = appColors.primaryAction,
                )
                ActionIconButton(
                    iconRes = rightIcon,
                    customBackgroundColor = rightBg,
                    onClick = { viewModel.onTimerRightButton() }
                )
            }
        }
    }
}
