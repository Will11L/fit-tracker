package com.example.sportapp.designsystem.common_components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

/**
 * Grille mensuelle 7 colonnes x N rangees, slot-based.
 *
 * Extraite des grilles inline dupliquees de CalendarViewScreen et
 * TasksCalendarScreen (2026-05-30). La cellule concrete (CalendarDay,
 * CalendarTaskDay, ...) est injectee via le slot [dayCell], qui recoit la
 * date du jour ainsi que la taille de cellule calculee (largeur / 7).
 *
 * Le decalage du 1er jour ([firstDayOffset], = nombre de cellules vides en
 * tete de mois) est calcule par l'appelant pour respecter sa convention de
 * debut de semaine (politique onboarding weekStart). Les cellules vides en
 * tete sont rendues comme des Spacer de la meme taille que les cellules.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CalendarMonthGrid(
    month: YearMonth,
    firstDayOffset: Int,
    modifier: Modifier = Modifier,
    dayCell: @Composable (date: LocalDate, cellSize: androidx.compose.ui.unit.Dp) -> Unit,
) {
    val daysInMonth = month.lengthOfMonth()
    val days: List<LocalDate?> = buildList {
        repeat(firstDayOffset) { add(null) }
        for (day in 1..daysInMonth) add(LocalDate.of(month.year, month.month, day))
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellSize = remember(maxWidth) { maxWidth / 7 }

        // Grille NON-lazy : un mois tient en <= 6 rangees fixes. Un LazyVerticalGrid
        // exigerait une hauteur max bornee et crashait quand l'ecran appelant scrolle
        // verticalement (ex. Journal nutrition = Column.verticalScroll -> hauteur
        // infinie). Colonnes a poids egal = meme rendu que GridCells.Fixed(7), sans
        // contrainte de hauteur, donc valide dans tout parent (scrollable ou borne).
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    week.forEach { date ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (date == null) {
                                Spacer(modifier = Modifier.size(cellSize))
                            } else {
                                dayCell(date, cellSize)
                            }
                        }
                    }
                    // Complete la derniere semaine pour aligner les cellules sous les bons jours.
                    repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
