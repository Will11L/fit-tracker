package com.example.sportapp.feature.chrono.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.chrono.domain.StopwatchStateMachine
import com.example.sportapp.feature.chrono.ui.ChronoScreenViewModel
import com.example.sportapp.feature.chrono.utils.formatTimeWithCentiseconds
import com.example.sportapp.feature.demo_tour.ui.components.demoHighlight
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun StopwatchPage(
    viewModel: ChronoScreenViewModel
) {
    val elapsedMillis by viewModel.elapsedMillis.collectAsState()
    val state by viewModel.stopwatchState.collectAsState()

    val timeText = formatTimeWithCentiseconds(elapsedMillis)

    val rightText = when (state) {
        StopwatchStateMachine.State.IDLE -> stringResource(R.string.chrono_btn_start)
        StopwatchStateMachine.State.PAUSED -> stringResource(R.string.chrono_btn_resume)
        StopwatchStateMachine.State.RUNNING -> stringResource(R.string.chrono_btn_stop)
    }
    val rightIcon = when (state) {
        StopwatchStateMachine.State.RUNNING -> R.drawable.ic_rounded_pause_circle
        StopwatchStateMachine.State.PAUSED -> R.drawable.ic_rounded_play_circle
        StopwatchStateMachine.State.IDLE -> R.drawable.ic_rounded_play_circle
    }
    val rightBg = when (state) {
        StopwatchStateMachine.State.RUNNING -> redMedium
        else -> appColors.primaryAction
    }

    val leftEnabled = state != StopwatchStateMachine.State.IDLE
    val leftText = when (state) {
        StopwatchStateMachine.State.IDLE -> ""
        StopwatchStateMachine.State.RUNNING -> stringResource(R.string.chrono_btn_lap)
        StopwatchStateMachine.State.PAUSED -> stringResource(R.string.chrono_btn_reset)
    }
    val leftIcon = when (state) {
        StopwatchStateMachine.State.RUNNING -> R.drawable.ic_rounded_flag
        StopwatchStateMachine.State.PAUSED -> R.drawable.ic_rounded_reset
        StopwatchStateMachine.State.IDLE -> R.drawable.ic_rounded_reset
    }
    val leftBg = if (leftEnabled) blueMedium else appColors.bgRecessed
    val leftTint = if (leftEnabled) appColors.textPrimary else appColors.textTertiary
    val leftTextColor = if (leftEnabled) appColors.textPrimary else appColors.textTertiary

    val laps by viewModel.laps.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(laps.size) {
        if (laps.isNotEmpty()) {
            listState.animateScrollToItem(laps.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            TitledDivider(stringResource(R.string.chrono_tab_stopwatch))
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = appColors.bgRecessed, shape = MaterialTheme.shapes.small)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayMedium,
                    color = appColors.primaryAction
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            TitledDivider(stringResource(R.string.chrono_laps_title))
            Spacer(modifier = Modifier.height(8.dp))

            LapsHeader()

            Spacer(modifier = Modifier.height(8.dp))

            if (laps.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(color = appColors.bgRecessed)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.chrono_no_laps),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        color = blueMedium
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(laps) { lap ->
                        LapRow(
                            index = lap.index,
                            lapMillis = lap.lapMillis,
                            totalMillis = lap.totalMillis
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp)
                .demoHighlight("chrono.buttons", expand = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ActionIconWithTextButton(
                iconRes = leftIcon,
                text = leftText,
                backgroundColor = leftBg,
                tint = leftTint,
                textColor = leftTextColor,
                clickable = leftEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                onClick = { viewModel.onLeftButton() }
            )
            ActionIconWithTextButton(
                iconRes = rightIcon,
                text = rightText,
                backgroundColor = rightBg,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                onClick = { viewModel.onRightButton() }
            )
        }
    }
}
