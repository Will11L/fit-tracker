package com.example.sportapp.feature.exercises.ui.components.exerciseScreen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.core.stats.StatsRange
import com.example.sportapp.designsystem.common_components.MultiLineChart
import com.example.sportapp.designsystem.common_components.StatsChartCard
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.feature.exercises.viewmodel.ExerciseStatsViewModel
import com.example.sportapp.feature.onboarding.data.weightLabel
import com.example.sportapp.feature.stats.ui.components.stats.CustomRangePickerDialog
import com.example.sportapp.feature.stats.ui.components.stats.RangeChipsRow

/**
 * Section Stats de ExerciseScreen — refonte 2026-06-11 : remplace le graphe mock
 * (generateFakeStats + CustomSelect "7 days…") par les données réelles et le style
 * de la page Stats / ExerciseStatsScreen : RangeChipsRow (1 sem → Tout + Custom,
 * range partagé via StatsRangeState) + StatsChartCard "Progression" + MultiLineChart
 * branché sur ExerciseStatsViewModel.dailyStats (DAO agrégé, sets DONE). Les
 * 3 StatFilterButton (légende cliquable) togglent la visibilité des métriques —
 * mêmes couleurs que ExerciseStatsScreen (Poids max vert, Volume orange, Séries bleu).
 */
@Composable
fun StatsSection(
    exerciseUUID: String,
    modifier: Modifier = Modifier,
    viewModel: ExerciseStatsViewModel = hiltViewModel(),
) {
    LaunchedEffect(exerciseUUID) { viewModel.setExerciseUUID(exerciseUUID) }

    val range by viewModel.range.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    var showCustomPicker by remember { mutableStateOf(false) }
    val selectedMetrics = remember { mutableStateMapOf("Weight" to true, "Sets" to true, "Volume" to true) }

    val unit = weightLabel(weightUnit)
    val metricLabels = mapOf(
        "Weight" to stringResource(R.string.exercise_stats_legend_max_weight, unit),
        "Sets" to stringResource(R.string.exercise_stats_legend_sets),
        "Volume" to stringResource(R.string.exercise_stats_legend_volume, unit),
    )
    val colorMap = mapOf(
        "Weight" to mediumGreen,
        "Volume" to orangeMedium,
        "Sets" to appColors.primaryAction,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        RangeChipsRow(
            range = range,
            onSelect = viewModel::setRange,
            onCustomClick = { showCustomPicker = true },
        )

        Spacer(Modifier.height(8.dp))

        StatsChartCard(
            title = "",
            isEmpty = dailyStats.isEmpty(),
            emptyText = stringResource(R.string.muscle_stats_no_data),
        ) {
            val chartData = dailyStats.map { row ->
                WorkoutStatEntry(
                    date = row.dayIso,
                    weight = row.maxWeight,
                    sets = row.setCount,
                    volume = row.volume,
                )
            }
            MultiLineChart(
                data = chartData,
                selectedMetrics = selectedMetrics.filter { it.value }.keys.toList(),
                colorMap = colorMap,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Légende cliquable = filtre de visibilité des métriques.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Weight", "Sets", "Volume").forEach { key ->
                StatFilterButton(
                    label = metricLabels.getValue(key),
                    isSelected = selectedMetrics[key] == true,
                    color = colorMap.getValue(key),
                ) {
                    selectedMetrics[key] = !(selectedMetrics[key] ?: false)
                }
            }
        }
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

/** Point du graphe de progression (consommé aussi par ExerciseStatsScreen / MultiLineChart). */
data class WorkoutStatEntry(
    val date: String,  // ISO 8601 UTC, ex: "2025-12-26T13:01:28.075727Z"
    val weight: Float,
    val sets: Int,
    val volume: Float
)
