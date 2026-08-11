package com.example.sportapp.feature.chrono.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.chrono.domain.TimerStateMachine
import com.example.sportapp.feature.chrono.ui.ChronoScreenViewModel
import com.example.sportapp.feature.chrono.utils.formatTimeWithCentiseconds
import com.example.sportapp.feature.chrono.utils.timerNameForDuration
import com.example.sportapp.feature.demo_tour.ui.components.demoHighlight
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun TimerPage(
    viewModel: ChronoScreenViewModel
) {
    val remainingMillis by viewModel.remainingMillis.collectAsState()
    val durationMillis by viewModel.timerDurationMillis.collectAsState()
    val state by viewModel.timerState.collectAsState()

    var showSetDialog by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            TimerPreset("30s", 30_000L),
            TimerPreset("45s", 45_000L),
            TimerPreset("1 min", 60_000L),
            TimerPreset("2 min", 2 * 60_000L),
            TimerPreset("5 min", 5 * 60_000L),
            TimerPreset("10 min", 10 * 60_000L),
            TimerPreset("15 min", 15 * 60_000L),
            TimerPreset("30 min", 30 * 60_000L),
            TimerPreset("1h", 60 * 60_000L),
        )
    }

    val leftText = when (state) {
        TimerStateMachine.State.IDLE -> stringResource(R.string.chrono_btn_set)
        else -> stringResource(R.string.chrono_btn_reset)
    }
    val leftIcon = when (state) {
        TimerStateMachine.State.IDLE -> R.drawable.ic_timer
        else -> R.drawable.ic_rounded_reset
    }
    val leftBg = blueMedium

    val rightText = when (state) {
        TimerStateMachine.State.IDLE -> stringResource(R.string.chrono_btn_start)
        TimerStateMachine.State.RUNNING -> stringResource(R.string.chrono_btn_pause)
        TimerStateMachine.State.PAUSED -> stringResource(R.string.chrono_btn_resume)
        TimerStateMachine.State.FINISHED -> stringResource(R.string.chrono_btn_restart)
    }
    val rightIcon = when (state) {
        TimerStateMachine.State.RUNNING -> R.drawable.ic_rounded_pause_circle
        else -> R.drawable.ic_rounded_play_circle
    }
    val rightBg = when (state) {
        TimerStateMachine.State.RUNNING -> redMedium
        else -> appColors.primaryAction
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            TitledDivider(stringResource(R.string.chrono_tab_timer))
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimerCircularDisplay(
                    remainingMillis = remainingMillis,
                    durationMillis = durationMillis,
                    state = state,
                    centerText = formatTimeWithCentiseconds(remainingMillis),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            TitledDivider(stringResource(R.string.chrono_presets_title))
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                items(presets) { preset ->
                    PresetTile(
                        label = preset.label,
                        selected = (preset.millis == durationMillis && state == TimerStateMachine.State.IDLE),
                        enabled = (state == TimerStateMachine.State.IDLE),
                        onClick = {
                            if (state == TimerStateMachine.State.IDLE) {
                                viewModel.setTimerDuration(preset.label, preset.millis)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val hint = when {
                state != TimerStateMachine.State.IDLE -> stringResource(R.string.chrono_hint_running)
                durationMillis <= 0L -> stringResource(R.string.chrono_hint_choose_preset)
                else -> stringResource(R.string.chrono_hint_ready)
            }

            Text(
                text = hint,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textPrimary.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
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
                tint = appColors.textPrimary,
                textColor = appColors.textPrimary,
                clickable = true,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                onClick = {
                    if (state == TimerStateMachine.State.IDLE) {
                        showSetDialog = true
                    } else {
                        viewModel.onTimerLeftButton()
                    }
                }
            )
            ActionIconWithTextButton(
                iconRes = rightIcon,
                text = rightText,
                backgroundColor = rightBg,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                onClick = {
                    if (state == TimerStateMachine.State.IDLE && durationMillis <= 0L) return@ActionIconWithTextButton
                    viewModel.onTimerRightButton()
                }
            )
        }
    }

    if (showSetDialog) {
        TimerDurationDialog(
            initialMillis = durationMillis,
            onConfirm = { ms ->
                // Custom dialog : pas de label preset → on génère un nom depuis la durée
                viewModel.setTimerDuration(timerNameForDuration(ms), ms)
                showSetDialog = false
            },
            onDismiss = { showSetDialog = false }
        )
    }
}

private data class TimerPreset(val label: String, val millis: Long)
