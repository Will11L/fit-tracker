package com.example.sportapp.feature.stats.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.core.stats.StatsRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * DateRangePicker custom (Compose pur, sans Material3 DatePicker), style
 * CalendarViewScreen : background appColors.bgScreen/appColors.bgRecessed, header avec
 * fleches navigation + label mois centre entre 2 traits, grille des jours
 * Monday-first, range visuel start/end en appColors.primaryAction + middle range
 * en appColors.selectedFill. User feedback 2026-05-07.
 */
@Composable
fun CustomRangePickerDialog(
    initialRange: StatsRange,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val (initialStart, initialEnd) = when (initialRange) {
        is StatsRange.Custom -> initialRange.startDate to initialRange.endDate
        else -> today.minusMonths(3) to today
    }

    var startDate by remember { mutableStateOf<LocalDate?>(initialStart) }
    var endDate by remember { mutableStateOf<LocalDate?>(initialEnd) }
    var displayMonth by remember { mutableStateOf(YearMonth.from(initialStart)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(appColors.bgScreen)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // ── Header : title + range
            Text(
                text = stringResource(R.string.stats_range_select_title),
                color = lightGrayBlue,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatRange(
                    startDate,
                    endDate,
                    startPlaceholder = stringResource(R.string.stats_range_start_placeholder),
                    endPlaceholder = stringResource(R.string.stats_range_end_placeholder),
                ),
                color = appColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = appColors.accentText.copy(alpha = 0.4f), thickness = 1.dp)
            Spacer(Modifier.height(12.dp))

            // ── Month nav header (centered with 2 traits autour du label)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconButton(iconRes = R.drawable.ic_arrow_left_alt, onClick = {
                    displayMonth = displayMonth.minusMonths(1)
                })
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = appColors.accentText.copy(alpha = 0.5f),
                        thickness = 1.dp,
                    )
                    Text(
                        text = "${displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayMonth.year}",
                        color = appColors.accentText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = appColors.accentText.copy(alpha = 0.5f),
                        thickness = 1.dp,
                    )
                }
                ActionIconButton(iconRes = R.drawable.ic_arrow_right_alt, onClick = {
                    displayMonth = displayMonth.plusMonths(1)
                })
            }
            Spacer(Modifier.height(12.dp))

            // ── Day labels (Monday-first) -- mutualises strings.xml
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    stringResource(R.string.weekday_short_mon),
                    stringResource(R.string.weekday_short_tue),
                    stringResource(R.string.weekday_short_wed),
                    stringResource(R.string.weekday_short_thu),
                    stringResource(R.string.weekday_short_fri),
                    stringResource(R.string.weekday_short_sat),
                    stringResource(R.string.weekday_short_sun),
                ).forEach {
                    Text(
                        text = it,
                        color = lightGrayBlue,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // ── Days grid
            DaysGrid(
                month = displayMonth,
                today = today,
                startDate = startDate,
                endDate = endDate,
                onDayClick = { date ->
                    val s = startDate
                    val e = endDate
                    when {
                        s == null || e != null -> {
                            // reset selection
                            startDate = date
                            endDate = null
                        }
                        date.isBefore(s) -> {
                            // before start -> swap
                            endDate = s
                            startDate = date
                        }
                        else -> {
                            endDate = date
                        }
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            // ── Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = lightGrayBlue),
                ) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = {
                        val s = startDate
                        val e = endDate
                        if (s != null && e != null) onConfirm(s, e)
                    },
                    enabled = startDate != null && endDate != null,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = appColors.primaryAction,
                        disabledContentColor = appColors.primaryAction.copy(alpha = 0.4f),
                    ),
                ) { Text(stringResource(R.string.common_ok)) }
            }
        }
    }
}

@Composable
private fun DaysGrid(
    month: YearMonth,
    today: LocalDate,
    startDate: LocalDate?,
    endDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
) {
    val daysInMonth = month.lengthOfMonth()
    val firstDayOfWeek = (month.atDay(1).dayOfWeek.value + 6) % 7  // Monday-first
    val cells = buildList {
        repeat(firstDayOfWeek) { add(null) }
        for (day in 1..daysInMonth) add(month.atDay(day))
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
    ) {
        items(cells) { date ->
            if (date == null) {
                Box(modifier = Modifier.aspectRatio(1f))
            } else {
                DayCell(
                    date = date,
                    isToday = date == today,
                    startDate = startDate,
                    endDate = endDate,
                    onClick = { onDayClick(date) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    startDate: LocalDate?,
    endDate: LocalDate?,
    onClick: () -> Unit,
) {
    val isStart = startDate != null && date == startDate
    val isEnd = endDate != null && date == endDate
    val isInRange = startDate != null && endDate != null
        && date.isAfter(startDate) && date.isBefore(endDate)

    val bg = when {
        isStart || isEnd -> appColors.primaryAction
        isInRange -> appColors.selectedFill
        else -> Color.Transparent
    }
    val fg = when {
        isStart || isEnd -> appColors.textPrimary
        isInRange -> appColors.textOnSelected
        isToday -> appColors.accentText
        else -> appColors.textPrimary
    }

    val baseModifier = Modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(6.dp))
        .background(bg)
        .clickable(onClick = onClick)

    val finalModifier = if (isToday && !isStart && !isEnd && !isInRange) {
        baseModifier.border(1.dp, appColors.accentText, RoundedCornerShape(6.dp))
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = fg,
            fontSize = 14.sp,
            fontWeight = if (isStart || isEnd) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// Formatter recompute par appel pour suivre Locale.getDefault() courante
// (locale switching live -- on ne peut pas cacher dans un val au top-level).
private fun formatRange(
    start: LocalDate?,
    end: LocalDate?,
    startPlaceholder: String,
    endPlaceholder: String,
): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val s = start?.format(formatter) ?: startPlaceholder
    val e = end?.format(formatter) ?: endPlaceholder
    return "$s  →  $e"
}

private val DayOfWeek.mondayBasedIndex: Int
    get() = (value + 6) % 7
