// MuscleScreen.kt
package com.example.sportapp.feature.muscles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.data.MuscleGroups
import com.example.sportapp.core.data.Zones
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.stats.StatsRange
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.MultiLineChart
import com.example.sportapp.designsystem.common_components.SingleSelectDropdown
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.StatsChartCard
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.StatFilterButton
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.WorkoutStatEntry
import com.example.sportapp.feature.muscles.viewmodel.MuscleScreenViewModel
import com.example.sportapp.feature.muscles.viewmodel.MuscleStatsViewModel
import com.example.sportapp.feature.stats.ui.components.stats.CustomRangePickerDialog
import com.example.sportapp.feature.stats.ui.components.stats.RangeChipsRow

/**
 * Page détail d'un muscle — refonte 2026-06-11, miroir de ExerciseScreen :
 * action bar (back / favori / sync / supprimer), cadre Détails (zone + groupe + image),
 * section Stats réelle (RangeChipsRow partagé + chart volume hebdo via MuscleStatsViewModel,
 * légende cliquable Séries/Volume) et exercices liés au muscle (flèche → page exercice).
 */
@Composable
fun MuscleScreen(
    muscleUuid: String,
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: MuscleScreenViewModel = hiltViewModel(),
    statsViewModel: MuscleStatsViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    LaunchedEffect(muscleUuid) {
        viewModel.setMuscleUuid(muscleUuid)
        statsViewModel.setMuscleUUID(muscleUuid)
    }

    val muscle by viewModel.muscle.collectAsState()
    val relatedExercises by statsViewModel.relatedExercises.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editZone by remember { mutableStateOf("") }
    var editGroup by remember { mutableStateOf("") }

    if (muscle == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appColors.bgScreen),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.muscle_not_found), color = redMedium)
        }
        return
    }

    if (showEditDialog) {
        FormDialog(
            title = stringResource(R.string.muscle_edit_zone_group_title),
            confirmText = stringResource(R.string.common_save),
            onConfirm = {
                viewModel.updateZoneAndGroup(zone = editZone, muscleGroup = editGroup)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        ) {
            SingleSelectDropdown(
                label = stringResource(R.string.muscle_field_zone),
                selected = editZone,
                options = Zones.ALL,
                onSelect = { editZone = it },
            )
            SingleSelectDropdown(
                label = stringResource(R.string.muscle_screen_group),
                selected = editGroup,
                options = MuscleGroups.ALL,
                onSelect = { editGroup = it },
            )
        }
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.muscle_list_delete_title),
            message = stringResource(R.string.muscle_screen_delete_message),
            onConfirm = {
                showDeleteDialog = false
                viewModel.markMuscleForDeletion(muscle!!)
                navController.popBackStack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        ScreenTitleBar(title = muscle!!.name)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { TitledDivider(stringResource(R.string.muscle_screen_actions)) }

            // Action bar façon ExerciseActionBar : back / favori / sync / supprimer.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
                        onClick = { navController.popBackStack() },
                        tint = appColors.textPrimary
                    )

                    ActionIconButton(
                        iconRes = if (muscle!!.isFavorite) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star,
                        onClick = { viewModel.toggleFavorite() },
                        tint = appColors.textPrimary,
                        hasBackground = true,
                        customBackgroundColor = if (muscle!!.isFavorite) orangeMedium else appColors.textTertiary.copy(alpha = 0.7f)
                    )

                    ActionIconButton(
                        iconRes = if (muscle!!.synced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                        clickable = !muscle!!.synced,
                        onClick = { viewModel.syncMuscle() },
                        tint = if (muscle!!.synced) appColors.primaryAction else yellowMedium,
                        hasBackground = true,
                        customBackgroundColor = appColors.bgRecessed
                    )

                    // 📖 Delavier (les visuels anatomiques du muscle, comme la page exercice)
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_book,
                        onClick = { navController.navigate(Routes.DELAVIER_METHOD) },
                        tint = appColors.textPrimary,
                        hasBackground = true,
                        customBackgroundColor = appColors.selectedFill
                    )

                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_delete_forever,
                        onClick = { showDeleteDialog = true },
                        tint = appColors.textPrimary,
                        hasBackground = true,
                        customBackgroundColor = redMedium
                    )
                }
            }

            item { TitledDivider(stringResource(R.string.muscle_screen_details)) }

            item {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailText(
                            label = stringResource(R.string.muscle_field_zone),
                            value = muscle!!.zone?.takeIf { it.isNotBlank() } ?: "—"
                        )
                        DetailText(
                            label = stringResource(R.string.muscle_screen_group),
                            value = muscle!!.muscleGroup?.takeIf { it.isNotBlank() } ?: "—"
                        )
                    }

                    // ✏️ édition zone + groupe (même pattern que les cadres de ExerciseScreenDetails)
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_edit,
                        tint = appColors.textTertiary,
                        hasBackground = false,
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = {
                            editZone = muscle!!.zone.orEmpty()
                            editGroup = muscle!!.muscleGroup.orEmpty()
                            showEditDialog = true
                        }
                    )
                }
            }

            item { TitledDivider("Stats") }

            item { MuscleStatsSection(statsViewModel = statsViewModel) }

            item { TitledDivider(stringResource(R.string.muscle_screen_exercises)) }

            if (relatedExercises.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.muscle_stats_no_exercises),
                        color = appColors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(relatedExercises.size) { index ->
                    RelatedExerciseRow(
                        exercise = relatedExercises[index],
                        onOpenClick = { navController.navigate(Routes.exercise(relatedExercises[index].uuid)) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

/**
 * Section Stats du muscle — même pattern que StatsSection de ExerciseScreen :
 * RangeChipsRow (range partagé StatsRangeState) + chart volume hebdo (buckets weekIso)
 * + légende cliquable (Séries bleu / Volume orange).
 */
@Composable
private fun MuscleStatsSection(statsViewModel: MuscleStatsViewModel) {
    val range by statsViewModel.range.collectAsState()
    val weeklyVolume by statsViewModel.weeklyVolume.collectAsState()

    var showCustomPicker by remember { mutableStateOf(false) }
    val selectedMetrics = remember { mutableStateMapOf("Sets" to true, "Volume" to true) }

    val metricLabels = mapOf(
        "Sets" to stringResource(R.string.exercise_stats_legend_sets),
        "Volume" to stringResource(R.string.muscle_stats_weekly_volume),
    )
    val colorMap = mapOf(
        "Volume" to orangeMedium,
        "Sets" to appColors.primaryAction,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        RangeChipsRow(
            range = range,
            onSelect = statsViewModel::setRange,
            onCustomClick = { showCustomPicker = true },
        )

        Spacer(Modifier.height(8.dp))

        StatsChartCard(
            title = "",
            isEmpty = weeklyVolume.isEmpty(),
            emptyText = stringResource(R.string.muscle_stats_no_data),
        ) {
            val chartData = weeklyVolume.map { row ->
                WorkoutStatEntry(
                    date = row.weekIso,
                    weight = 0f,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Sets", "Volume").forEach { key ->
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
                statsViewModel.setRange(StatsRange.Custom(start, end))
                showCustomPicker = false
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

/** Row d'exercice lié au muscle : nom + flèche blueMedium → page détail exercice. */
@Composable
private fun RelatedExerciseRow(exercise: Exercise, onOpenClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = exercise.name,
            modifier = Modifier.weight(1f),
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        ActionIconButton(
            iconRes = R.drawable.ic_arrow_right_alt,
            tint = appColors.textPrimary,
            hasBackground = true,
            customBackgroundColor = blueMedium,
            onClick = onOpenClick,
        )
    }
}

@Composable
private fun DetailText(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            color = appColors.primaryAction,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
