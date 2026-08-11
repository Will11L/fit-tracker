package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGrayBlue
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * DatePicker custom thémé app (style CalendarViewScreen) — calendrier maison qui
 * remplace le `DatePicker` M3 (thème rose/violet par défaut). Réutilisable :
 * onboarding (date de naissance), TaskFormDialog (échéance / début / fin), etc.
 *
 * - Header : flèche prev mois + Text Month/Year (cliquable -> year picker) + flèche next mois.
 * - Mode month : grille 7 colonnes (M T W T F S S + jours cliquables).
 * - Mode year : grille 4 colonnes d'années ([minYear]..[maxYear]).
 *
 * [title] : titre du dialog (le caller passe son `stringResource`).
 * [defaultDate] : date affichée si [initialIso] est null/invalide.
 * Format wire ISO "YYYY-MM-DD".
 */
@Composable
fun CustomDatePickerDialog(
    initialIso: String?,
    title: String,
    onConfirm: (iso: String) -> Unit,
    onDismiss: () -> Unit,
    minYear: Int = 1900,
    maxYear: Int = LocalDate.now().year,
    defaultDate: LocalDate = LocalDate.now(),
) {
    val initialDate = initialIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: defaultDate
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var yearPickerMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.bgScreen,
        title = { Text(text = title, color = appColors.primaryAction) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
            ) {
                // Header : flèche prev / Month Year clickable / flèche next
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionIconButton(iconRes = R.drawable.ic_arrow_left_alt, onClick = {
                        if (yearPickerMode) yearPickerMode = false
                        else currentMonth = currentMonth.minusMonths(1)
                    })
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { yearPickerMode = !yearPickerMode }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                            fontSize = 16.sp,
                            color = appColors.textTertiary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ActionIconButton(iconRes = R.drawable.ic_arrow_right_alt, onClick = {
                        if (yearPickerMode) yearPickerMode = false
                        else currentMonth = currentMonth.plusMonths(1)
                    })
                }

                if (yearPickerMode) {
                    // Year grid (4 cols), scrollable, [minYear]..[maxYear].
                    val years = (minYear..maxYear).toList().reversed()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(280.dp),
                    ) {
                        items(years) { year ->
                            val isSelected = year == currentMonth.year
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) appColors.primaryAction else appColors.bgRecessed)
                                    .clickable {
                                        currentMonth = YearMonth.of(year, currentMonth.month)
                                        yearPickerMode = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = year.toString(),
                                    color = if (isSelected) appColors.textPrimary else appColors.textTertiary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                } else {
                    // Weekday labels -- mutualises avec CalendarViewScreen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
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
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = lightGrayBlue,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Days grid : 7 cols. firstDayOfMonth offset Monday=1..Sunday=7.
                    val firstOfMonth = currentMonth.atDay(1)
                    val firstWeekday = firstOfMonth.dayOfWeek.value  // Mon=1..Sun=7
                    val daysInMonth = currentMonth.lengthOfMonth()
                    val cells: List<LocalDate?> = buildList {
                        repeat(firstWeekday - 1) { add(null) }
                        for (d in 1..daysInMonth) add(currentMonth.atDay(d))
                    }
                    val today = LocalDate.now()
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val cellSize = maxWidth / 7
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.height(cellSize * 6),
                        ) {
                            items(cells) { date ->
                                if (date == null) {
                                    Spacer(modifier = Modifier.size(cellSize))
                                } else {
                                    val isSelected = date == selectedDate
                                    val isToday = date == today
                                    val cellShape = if (isToday && !isSelected) MaterialTheme.shapes.small else CircleShape
                                    Box(
                                        modifier = Modifier
                                            .size(cellSize)
                                            .clip(cellShape)
                                            .then(
                                                when {
                                                    isSelected -> Modifier.background(appColors.primaryAction)
                                                    isToday -> Modifier.border(
                                                        width = 1.5.dp,
                                                        color = appColors.primaryAction,
                                                        shape = MaterialTheme.shapes.small,
                                                    )
                                                    else -> Modifier
                                                }
                                            )
                                            .clickable { selectedDate = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            fontSize = 14.sp,
                                            color = if (isSelected) appColors.textPrimary else appColors.textTertiary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = stringResource(R.string.common_save), onClick = { onConfirm(selectedDate.toString()) })
        },
        dismissButton = {
            DialogSecondaryButton(text = stringResource(R.string.common_cancel), onClick = onDismiss)
        },
    )
}
