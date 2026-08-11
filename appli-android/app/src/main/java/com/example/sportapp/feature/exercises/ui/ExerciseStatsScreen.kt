package com.example.sportapp.feature.exercises.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.feature.onboarding.data.formatVolume
import com.example.sportapp.feature.onboarding.data.formatWeightValue
import com.example.sportapp.feature.onboarding.data.weightLabel
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.MultiLineChart
import com.example.sportapp.designsystem.common_components.StatsChartCard
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.WorkoutStatEntry
import com.example.sportapp.feature.stats.ui.components.stats.CustomRangePickerDialog
import com.example.sportapp.feature.stats.ui.components.stats.RangeChipsRow
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.feature.exercises.viewmodel.ExerciseStatsViewModel
import com.example.sportapp.core.stats.StatsRange

@Composable
fun ExerciseStatsScreen(
    exerciseUUID: String,
    navController: NavHostController,
    viewModel: ExerciseStatsViewModel = hiltViewModel(),
) {
    LaunchedEffect(exerciseUUID) { viewModel.setExerciseUUID(exerciseUUID) }
    BackHandler { navController.popBackStack() }

    val exercise by viewModel.exercise.collectAsState()
    val range by viewModel.range.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()
    val allTimeStats by viewModel.allTimeStats.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

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
                text = exercise?.name ?: "—",
                color = appColors.primaryAction,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
        }

        Spacer(Modifier.height(8.dp))
        TitledDivider(stringResource(R.string.exercise_stats_all_time))
        AllTimeStatsCard(
            maxWeight = allTimeStats?.maxWeight ?: 0f,
            totalSets = allTimeStats?.totalSets ?: 0,
            totalVolume = allTimeStats?.totalVolume ?: 0f,
            weightUnit = weightUnit,
        )

        TitledDivider(stringResource(R.string.muscle_stats_period))
        RangeChipsRow(
            range = range,
            onSelect = viewModel::setRange,
            onCustomClick = { showCustomPicker = true },
        )

        StatsChartCard(
            title = stringResource(R.string.exercise_stats_progression),
            isEmpty = dailyStats.isEmpty(),
            emptyText = stringResource(R.string.muscle_stats_no_data),
            legend = {
                ChartLegend(
                    items = listOf(
                        stringResource(R.string.exercise_stats_legend_max_weight, weightLabel(weightUnit)) to mediumGreen,
                        stringResource(R.string.exercise_stats_legend_volume, weightLabel(weightUnit)) to orangeMedium,
                    ),
                )
            },
        ) {
            val chartData: List<WorkoutStatEntry> = dailyStats.map { row ->
                WorkoutStatEntry(
                    date = row.dayIso,
                    weight = row.maxWeight,
                    sets = row.setCount,
                    volume = row.volume,
                )
            }
            MultiLineChart(
                data = chartData,
                selectedMetrics = listOf("Weight", "Volume"),
                colorMap = mapOf(
                    "Weight" to mediumGreen,
                    "Volume" to orangeMedium,
                    "Sets" to appColors.primaryAction,
                ),
            )
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
private fun AllTimeStatsCard(
    maxWeight: Float,
    totalSets: Int,
    totalVolume: Float,
    weightUnit: WeightUnit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.bgRecessed),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatColumn(label = stringResource(R.string.exercise_stats_top_set), value = "${formatWeightValue(maxWeight, weightUnit)} ${weightLabel(weightUnit)}")
            StatColumn(label = stringResource(R.string.exercise_stats_total_sets), value = "$totalSets")
            StatColumn(label = stringResource(R.string.exercise_stats_total_volume), value = "${formatVolume(totalVolume, weightUnit)} ${weightLabel(weightUnit)}")
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = appColors.primaryAction, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = appColors.primaryAction, fontSize = 12.sp)
    }
}

@Composable
private fun ChartLegend(items: List<Pair<String, Color>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, shape = MaterialTheme.shapes.extraSmall),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, color = appColors.primaryAction)
            }
        }
    }
}
