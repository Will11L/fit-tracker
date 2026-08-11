package com.example.sportapp.feature.muscles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.MultiLineChart
import com.example.sportapp.designsystem.common_components.StatsChartCard
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.WorkoutStatEntry
import com.example.sportapp.feature.stats.ui.components.stats.CustomRangePickerDialog
import com.example.sportapp.feature.stats.ui.components.stats.RangeChipsRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.feature.muscles.viewmodel.MuscleStatsViewModel
import com.example.sportapp.core.stats.StatsRange

@Composable
fun MuscleStatsScreen(
    muscleUUID: String,
    navController: NavHostController,
    viewModel: MuscleStatsViewModel = hiltViewModel(),
) {
    LaunchedEffect(muscleUUID) { viewModel.setMuscleUUID(muscleUUID) }
    BackHandler { navController.popBackStack() }

    val muscle by viewModel.muscle.collectAsState()
    val range by viewModel.range.collectAsState()
    val weeklyVolume by viewModel.weeklyVolume.collectAsState()
    val relatedExercises by viewModel.relatedExercises.collectAsState()
    val currentWeekGoal by viewModel.currentWeekGoal.collectAsState()

    var showCustomPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(iconRes = R.drawable.ic_arrow_left_alt, onClick = { navController.popBackStack() })
            Spacer(Modifier.width(8.dp))
            Text(
                text = muscle?.name ?: "—",
                color = appColors.primaryAction,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
        }

        Spacer(Modifier.height(8.dp))
        TitledDivider(stringResource(R.string.muscle_stats_weekly_goal))
        WeekGoalCard(goal = currentWeekGoal)

        TitledDivider(stringResource(R.string.muscle_stats_period))
        RangeChipsRow(
            range = range,
            onSelect = viewModel::setRange,
            onCustomClick = { showCustomPicker = true },
        )

        StatsChartCard(
            title = stringResource(R.string.muscle_stats_weekly_volume),
            isEmpty = weeklyVolume.isEmpty(),
            emptyText = stringResource(R.string.muscle_stats_no_data),
        ) {
            val chartData: List<WorkoutStatEntry> = weeklyVolume.map { row ->
                WorkoutStatEntry(
                    date = row.weekIso,
                    weight = 0f,
                    sets = row.setCount,
                    volume = row.volume,
                )
            }
            MultiLineChart(
                data = chartData,
                selectedMetrics = listOf("Volume"),
                colorMap = mapOf("Volume" to orangeMedium),
            )
        }

        TitledDivider(stringResource(R.string.muscle_stats_exercises))
        if (relatedExercises.isEmpty()) {
            Text(
                text = stringResource(R.string.muscle_stats_no_exercises),
                color = appColors.primaryAction,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            relatedExercises.forEach { exercise ->
                RelatedExerciseRow(
                    exercise = exercise,
                    onClick = { navController.navigate(Routes.exerciseStats(exercise.uuid)) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showCustomPicker) {
        CustomRangePickerDialog(
            initialRange = range,
            onConfirm = { start, end ->
                viewModel.setRange(StatsRange.Custom(start, end))
                showCustomPicker = false
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

@Composable
private fun WeekGoalCard(goal: MuscleGoal?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.bgRecessed),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (goal == null) {
                Text(stringResource(R.string.muscle_stats_no_goal), color = appColors.primaryAction)
            } else {
                Text(
                    text = stringResource(R.string.muscle_stats_goal_format, goal.done, goal.target),
                    color = appColors.primaryAction,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // priority/status codes UPPER_CASE wire (politique 11) -- display tel quel.
                    text = stringResource(R.string.muscle_stats_priority_status, goal.priority, goal.status),
                    color = appColors.primaryAction,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.bgRecessed),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = exercise.name,
                modifier = Modifier.weight(1f),
                color = appColors.primaryAction,
                fontWeight = FontWeight.Medium,
            )
            Text("›", color = appColors.primaryAction, fontSize = 18.sp)
        }
    }
}
