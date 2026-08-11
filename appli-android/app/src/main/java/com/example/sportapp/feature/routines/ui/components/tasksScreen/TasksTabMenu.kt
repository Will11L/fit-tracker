package com.example.sportapp.feature.routines.ui.components.tasksScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.SessionTabExerciseBackground
import com.example.sportapp.designsystem.theme.appColors

/**
 * Phase 1 (2026-05-12) : header de l'ecran Tasks unifie. Affiche les 2
 * onglets cote a cote (Daily | Agenda) ; tap sur celui non-actif switche
 * le tab. Hauteur 44dp identique a RoutineHeader pour conserver le meme
 * visuel entre les 2 vues.
 */
@Composable
fun TasksTabMenu(
    selectedTab: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = stringResource(R.string.tasks_tab_daily)
    val agenda = stringResource(R.string.tasks_tab_agenda)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        TabLabel(
            text = daily,
            selected = selectedTab == 0,
            onClick = { onChange(0) },
            modifier = Modifier.weight(1f),
        )
        TabLabel(
            text = agenda,
            selected = selectedTab == 1,
            onClick = { onChange(1) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Memes couleurs que DualTabMenu (Chrono / Stopwatch / Timer) :
 * tokens appColors.selectedFill / appColors.bgBottomNav (declines light/dark).
 */
@Composable
private fun TabLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                color = if (selected) appColors.selectedFill else appColors.bgBottomNav,
            )
            .clickable(enabled = !selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) appColors.textOnSelected else appColors.textTertiary,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
