package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.*

@Composable
fun TimeRangePickerBar(
    minMinutes: Int,          // ex: 0
    maxMinutes: Int,          // ex: 1439
    stepMinutes: Int,         // ex: 5
    startMinutes: Int,
    endMinutes: Int,
    onChange: (start: Int, end: Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Time",
    thumbColor: Color = appColors.primaryAction,
    activeTrackColor: Color = appColors.primaryAction,
    activeTickColor: Color = appColors.dividerStrong,
    inactiveTickColor: Color = appColors.dividerStrong,
    inactiveTrackColor: Color = appColors.bgRecessed
) {
    val safeStart = startMinutes.coerceIn(minMinutes, maxMinutes)
    val safeEnd = endMinutes
        .coerceIn(minMinutes, maxMinutes)
        .coerceAtLeast(safeStart)

    fun snap(x: Int): Int {
        val clamped = x.coerceIn(minMinutes, maxMinutes)
        val k = ((clamped - minMinutes).toFloat() / stepMinutes.toFloat()).roundToInt()
        return (minMinutes + k * stepMinutes).coerceIn(minMinutes, maxMinutes)
    }

    fun fmt(m: Int): String {
        val hh = (m / 60).toString().padStart(2, '0')
        val mm = (m % 60).toString().padStart(2, '0')
        return "$hh:$mm"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = appColors.primaryAction)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ⬅️ Decrease start
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
                hasBackground = true,
                customBackgroundColor = appColors.bgRecessed,
                onClick = {
                    val newStart = snap(safeStart - stepMinutes)
                    val newEnd = safeEnd.coerceAtLeast(newStart)
                    onChange(newStart, newEnd)
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Slider + labels ABOVE the track
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp) // 👈 un peu plus haut pour placer les labels au-dessus
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val widthPx = with(density) { maxWidth.toPx() }

                    // padding “optique” pour coller les labels aux thumbs sans dépasser
                    val edgePaddingPx = with(density) { 14.dp.toPx() }

                    val startFrac =
                        (safeStart - minMinutes).toFloat() / (maxMinutes - minMinutes).toFloat()
                    val endFrac =
                        (safeEnd - minMinutes).toFloat() / (maxMinutes - minMinutes).toFloat()

                    fun xFor(frac: Float): Float {
                        val raw = frac * widthPx
                        return raw.coerceIn(edgePaddingPx, widthPx - edgePaddingPx)
                    }

                    // 👆 Labels au-dessus du slider (y négatif léger)
                    val labelYOffsetPx = with(density) { (-2).dp.toPx() } // petit “lift”

                    Text(
                        text = fmt(safeStart),
                        color = thumbColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.offset {
                            IntOffset(
                                x = (xFor(startFrac) - edgePaddingPx).roundToInt(),
                                y = labelYOffsetPx.roundToInt()
                            )
                        }
                    )

                    Text(
                        text = fmt(safeEnd),
                        color = thumbColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.offset {
                            IntOffset(
                                x = (xFor(endFrac) - edgePaddingPx).roundToInt(),
                                y = labelYOffsetPx.roundToInt()
                            )
                        }
                    )

                    // Slider aligné en bas + padding top pour laisser l’espace des labels
                    RangeSlider(
                        value = safeStart.toFloat()..safeEnd.toFloat(),
                        onValueChange = { range ->
                            val newStart = snap(range.start.toInt())
                            val newEnd = snap(range.endInclusive.toInt()).coerceAtLeast(newStart)
                            onChange(newStart, newEnd)
                        },
                        valueRange = minMinutes.toFloat()..maxMinutes.toFloat(),
                        steps = ((maxMinutes - minMinutes) / stepMinutes).coerceAtLeast(1) - 1,
                        colors = SliderDefaults.colors(
                            activeTrackColor = activeTrackColor,      // ✅ milieu sélectionné (foncé)
                            inactiveTrackColor = inactiveTrackColor,  // ✅ extérieur (clair)
                            thumbColor = thumbColor,
                            activeTickColor = activeTickColor,
                            inactiveTickColor = inactiveTickColor
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(top = 18.dp) // ✅ pousse la track sous les labels
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ➡️ Increase end
            ActionIconButton(
                iconRes = R.drawable.ic_keyboard_arrow_right,
                hasBackground = true,
                customBackgroundColor = appColors.bgRecessed,
                onClick = {
                    val newEnd = snap(safeEnd + stepMinutes)
                    onChange(safeStart.coerceAtMost(newEnd), newEnd)
                }
            )
        }
    }
}
