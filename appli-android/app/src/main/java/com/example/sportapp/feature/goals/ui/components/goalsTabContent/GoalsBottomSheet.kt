package com.example.sportapp.feature.goals.ui.components.goalsTabContent

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*

/**
 * BottomSheet d'options de la page MuscleGoals (refonte 2026-05-09).
 *
 * Avant : 4 actions (Add muscle, Sort by name, Sort by priority, Group by zone).
 * Apres : 1 seule action (Add muscle) — les 3 actions Sort/Group sont
 * remplacees par les toggles GoalsViewModeToggle + GoalsSortToggle visibles
 * en permanence dans le header de la page.
 */
@Composable
fun GoalsBottomSheet(
    onDismissRequest: () -> Unit,
    onAddMuscleToGoals: () -> Unit,
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.sheet_goals_add_muscle),
            iconRes = R.drawable.ic_add,
            color = appColors.selectedFill,
            onClick = onAddMuscleToGoals
        ),
    )

    OptionsBottomSheet(
        title = stringResource(R.string.sheet_goals_title),
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
