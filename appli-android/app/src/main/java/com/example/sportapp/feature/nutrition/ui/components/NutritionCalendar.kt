package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CalendarMonthGrid
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.darkOrange
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.feature.nutrition.domain.DayRingTotals
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.RingMacroKey
import com.example.sportapp.feature.nutrition.ui.macroColor
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Couleur du macro correspondant à un anneau (réutilise macroColor via MacroKey). */
private fun ringColor(key: RingMacroKey): Color = macroColor(
    when (key) {
        RingMacroKey.KCAL -> MacroKey.KCAL
        RingMacroKey.CARBS -> MacroKey.CARBS
        RingMacroKey.FAT -> MacroKey.FAT
        RingMacroKey.PROTEIN -> MacroKey.PROTEIN
    }
)

/** Largeur (dp) de chaque anneau, du plus extérieur (kcal) au plus intérieur (protéines). */
private val ringWidths: Map<RingMacroKey, Float> = mapOf(
    RingMacroKey.KCAL to 4f,
    RingMacroKey.CARBS to 3f,
    RingMacroKey.FAT to 3f,
    RingMacroKey.PROTEIN to 3f,
)

/**
 * Calendrier mensuel du Journal nutrition : navigation mois (← mois →, label
 * cliquable = retour au mois courant), initiales de jours localisées (lundi-first)
 * et grille de cases à 4 anneaux concentriques (kcal / glucides / lipides /
 * protéines) par jour. Tap sur une case = sélectionne le jour.
 */
@Composable
fun NutritionCalendarSection(
    monthCursor: YearMonth,
    ringData: Map<String, DayRingTotals>,
    selectedDay: String,
    today: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (String) -> Unit,
    onGoCurrentMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDayOffset = (monthCursor.atDay(1).dayOfWeek.value - 1) // lundi-first
    val isCurrentMonth = monthCursor == YearMonth.now()
    // Fermé par défaut (le jour courant est déjà auto-sélectionné à l'arrivée) :
    // l'espace va au résumé et aux repas, le chevron déplie à la demande.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggleLabel = stringResource(R.string.nutrition_calendar_toggle)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.bgRecessed),
    ) {
        // Bandeau bord-à-bord secondBlue (style header du calendrier web) : chevrons
        // de mois firstBlue collés aux bords, pilule du mois firstBlue, et chevron de
        // repli à droite du mois (replie la grille pour laisser la place au reste).
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
                onClick = onPrevMonth,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            // Pilule du mois PARFAITEMENT centrée : un spacer de la largeur du chevron
            // compense celui-ci à gauche, le chevron (bleu clair, comme celui des
            // micros) se place juste à côté à droite.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Spacer(Modifier.width(36.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(firstBlue)
                        .clickable(onClick = onGoCurrentMonth)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = monthLabel(monthCursor),
                        color = appColors.textTertiary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (!isCurrentMonth) {
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar_today),
                            contentDescription = null,
                            tint = appColors.primaryAction,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                ActionIconButton(
                    iconRes = if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down,
                    boxSize = 34.dp,
                    iconSize = 20.dp,
                    tint = appColors.accentText,
                    hasBackground = false,
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics { contentDescription = toggleLabel },
                )
            }
            ActionIconButton(
                iconRes = R.drawable.ic_keyboard_arrow_right,
                boxSize = 34.dp,
                iconSize = 20.dp,
                customBackgroundColor = firstBlue,
                onClick = onNextMonth,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Grille repliable (dépli/repli animé — convention projet).
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdayInitials().forEach { label ->
                        Text(
                            text = label,
                            color = lightGrayBlue,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                CalendarMonthGrid(
                    month = monthCursor,
                    firstDayOffset = firstDayOffset,
                ) { date, cellSize ->
                    val iso = date.toString()
                    DayRingsCell(
                        cellSize = cellSize,
                        dayNum = date.dayOfMonth,
                        data = ringData[iso],
                        isToday = iso == today,
                        isSelected = iso == selectedDay,
                        onClick = { onSelectDay(iso) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRingsCell(
    cellSize: Dp,
    dayNum: Int,
    data: DayRingTotals?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        isSelected -> darkOrange
        isToday -> appColors.primaryAction
        else -> Color.Transparent
    }
    val trackColor = appColors.bgSurface

    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(cellSize - 6.dp)) {
            var radiusInsetPx = 0f
            RingMacroKey.entries.forEach { key ->
                val strokePx = (ringWidths[key] ?: 3f).dp.toPx()
                val inset = radiusInsetPx + strokePx / 2f
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                // Piste (track) faible.
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
                val progress = data?.progress?.get(key) ?: 0f
                if (progress > 0f) {
                    drawArc(
                        color = ringColor(key),
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx),
                    )
                }
                radiusInsetPx = inset + strokePx / 2f + 1.5f.dp.toPx()
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNum.toString(),
                color = appColors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            if (data?.hasData == true) {
                Text(
                    text = data.totals.kcal.roundToInt().toString(),
                    color = appColors.textTertiary,
                    fontSize = 8.sp,
                )
            }
        }
    }
}

/** Initiales des jours, lundi-first, localisées (NARROW) via le Locale courant. */
private fun weekdayInitials(): List<String> {
    val locale = Locale.getDefault()
    return (0..6).map { offset ->
        DayOfWeek.MONDAY.plus(offset.toLong())
            .getDisplayName(TextStyle.NARROW, locale)
            .uppercase(locale)
    }
}

/** Libellé « mois année » localisé (ex. « juin 2026 »). */
private fun monthLabel(month: YearMonth): String {
    val locale = Locale.getDefault()
    val name = month.month.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    return "$name ${month.year}"
}
