package com.example.sportapp.feature.health.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CalendarMonthGrid
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.designsystem.theme.secondBlue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendrier de saisie santé (partagé pesée / stress) : conteneur thirdBlue arrondi
 * + header bord-à-bord secondBlue ‹ pilule mois › (pilule cliquable = retour au mois
 * courant, navigation future bloquée — miroir NutritionCalendar) + grille
 * [CalendarMonthGrid]. Point [accentColor] sous les jours de [existingByDate],
 * sélection = bord [accentColor], aujourd'hui = bord primaire, futurs grisés non
 * sélectionnables. Le curseur de mois est interne (initialisé sur [selected]).
 */
@Composable
fun HealthEntryCalendar(
    today: LocalDate,
    selected: LocalDate,
    existingByDate: Map<String, Float>,
    accentColor: Color,
    onSelect: (LocalDate) -> Unit,
) {
    var monthCursor by remember { mutableStateOf(YearMonth.from(selected)) }
    val locale = LocalConfiguration.current.locales[0]
    val canNextMonth = monthCursor < YearMonth.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(secondBlue),
        ) {
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
                boxSize = 34.dp,
                iconSize = 20.dp,
                customBackgroundColor = firstBlue,
                onClick = { monthCursor = monthCursor.minusMonths(1) },
                contentDescription = stringResource(R.string.health_dash_weight_prev_month),
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = monthLabel(monthCursor, locale),
                color = appColors.textTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(firstBlue)
                    .clickable { monthCursor = YearMonth.now() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            ActionIconButton(
                iconRes = R.drawable.ic_keyboard_arrow_right,
                boxSize = 34.dp,
                iconSize = 20.dp,
                tint = if (canNextMonth) appColors.textPrimary else GrayBlue,
                customBackgroundColor = firstBlue,
                clickable = canNextMonth,
                onClick = { monthCursor = monthCursor.plusMonths(1) },
                contentDescription = stringResource(R.string.health_dash_weight_next_month),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayInitials(locale).forEach { label ->
                    Text(
                        text = label,
                        color = lightGrayBlue,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            CalendarMonthGrid(
                month = monthCursor,
                firstDayOffset = monthCursor.atDay(1).dayOfWeek.value - 1, // lundi-first
            ) { day, cellSize ->
                EntryDayCell(
                    cellSize = cellSize,
                    dayNum = day.dayOfMonth,
                    hasData = existingByDate.containsKey(day.toString()),
                    isToday = day == today,
                    isSelected = day == selected,
                    selectable = !day.isAfter(today),
                    accentColor = accentColor,
                    onClick = { onSelect(day) },
                )
            }
        }
    }
}

/** Case jour : numéro + point [accentColor] (saisie existante). Sélection = bord
 *  accent, aujourd'hui = bord primaire, futur = grisé non cliquable. */
@Composable
private fun EntryDayCell(
    cellSize: Dp,
    dayNum: Int,
    hasData: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    selectable: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val borderColor = when {
        isSelected -> accentColor
        isToday -> appColors.primaryAction
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .clickable(enabled = selectable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = dayNum.toString(),
                color = if (selectable) appColors.textPrimary else GrayBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            // Place du point toujours réservée (numéros alignés entre cases).
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (hasData) accentColor else Color.Transparent),
            )
        }
    }
}

/** Initiales des jours, lundi-first, localisées (NARROW). */
private fun weekdayInitials(locale: Locale): List<String> =
    (0..6).map { offset ->
        DayOfWeek.MONDAY.plus(offset.toLong())
            .getDisplayName(TextStyle.NARROW, locale)
            .uppercase(locale)
    }

/** Libellé « Mois année » localisé (ex. « Juillet 2026 »). */
private fun monthLabel(month: YearMonth, locale: Locale): String {
    val name = month.month.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    return "$name ${month.year}"
}
