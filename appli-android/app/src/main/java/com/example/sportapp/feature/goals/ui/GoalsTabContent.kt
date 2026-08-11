package com.example.sportapp.feature.goals.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.R
import com.example.sportapp.core.data.Zones
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.data.paletteForZone
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.DialogValidationReason
import com.example.sportapp.designsystem.common_components.FilterDropdown
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.ChangeGoalStatusDialog
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.EmptyGoalsWeekState
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.GoalRow
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.GoalsAchievementChart
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.GoalsBottomSheet
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.GoalsHeader
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.GoalsProgressBar
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.GoalsSortToggle
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.GoalsViewModeToggle
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.MuscleOptionsBottomSheet
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.PriorityPicker
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.TableHeaderCell
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.TargetPickerDialog
import com.example.sportapp.feature.goals.ui.components.goalsTabContent.ZoneGoalsCard
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.core.utils.CustomDateUtils.getWeekISOFromOffset
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.calculateGoalProgress
import com.example.sportapp.core.utils.showSnackbar
import com.example.sportapp.feature.goals.viewmodel.GoalsSortMode
import com.example.sportapp.feature.goals.viewmodel.GoalsTabViewModel
import com.example.sportapp.feature.goals.viewmodel.GoalsViewMode
import com.example.sportapp.feature.goals.viewmodel.NormalizedZone
import java.util.UUID

fun getMuscleName(uuid: String, allMuscles: List<Muscle>): String {
    return allMuscles.find { it.uuid == uuid }?.name ?: "Unknown"
}

fun getMuscleUUID(name: String, allMuscles: List<Muscle>): String? {
    return allMuscles.find { it.name == name }?.uuid
}

@Composable
fun GoalsTabContent(
    navController: NavHostController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: GoalsTabViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val userId by viewModel.userId.collectAsState()

    LaunchedEffect(Unit) {
        SyncEvents.onReconnected.collect {
            //viewModel.refreshUserId() //to delete ?
        }
    }

    val context = LocalContext.current

    val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
    val weekISO = getWeekISOFromOffset(currentWeekOffset)

    val allMuscles by viewModel.muscles.collectAsState()
    val allMuscleNames = allMuscles.map { it.name }.sortedBy { it.lowercase() }

    val allMuscleGoals by viewModel.muscleGoals.collectAsState()
    val isEmptyWeek = allMuscleGoals.isEmpty()

    // Refonte 2026-05-09 : nouveaux flows pour l'affichage 3-niveaux + chart.
    val goalsViewMode by viewModel.goalsViewMode.collectAsState()
    val goalsSortMode by viewModel.goalsSortMode.collectAsState()
    val goalsListSorted by viewModel.goalsListSorted.collectAsState()
    val goalsByGroupSorted by viewModel.goalsByGroupSorted.collectAsState()
    val goalsByZoneSorted by viewModel.goalsByZoneSorted.collectAsState()
    val chartData by viewModel.chartData.collectAsState()

    val allGoalDone =
        allMuscleGoals.isNotEmpty() &&
                allMuscleGoals.all { it.status.trim().equals("DONE", ignoreCase = true) }

    var showGoalsBottomSheet by remember { mutableStateOf(false) }

    var showChangeTargetDialog by remember { mutableStateOf(false) }
    var selectedTargetGoal by remember { mutableStateOf<MuscleGoal?>(null) }

    var showAddMuscleDialog by remember { mutableStateOf(false) }
    var newMusclePriority by remember { mutableStateOf("LOW") }


    var selectedMuscleName by remember { mutableStateOf<String?>(null) }

    val targetOptions = listOf("12+", "6-12", "3-5")
    var selectedTarget by remember { mutableStateOf<String?>(null) }

    var selectedMuscleGoal by remember { mutableStateOf<MuscleGoal?>(null) }
    var showMuscleOptionsSheet by remember { mutableStateOf(false) }

    var showChangeGoalStatusDialog by remember { mutableStateOf(false) }

    var showSyncDialog by remember { mutableStateOf(false) }

    var showResetDialog by remember { mutableStateOf(false) }

    // ── ColorMaps : palette par zone alignee Stats (groupColors). Refonte
    // 2026-05-09 / refactor 3-niveaux 2026-05-08.
    //  - zoneColors : 6 zones canoniques + Other.
    //  - muscleGroupColors : nuance par muscle_group derivee de la couleur
    //    de sa zone via paletteForZone (jusqu'a 17 groups).
    //  - muscleColors : nuance par muscle precis (jusqu'a 35).
    // Pre-extract appColors.* en val locales : `remember { ... }` lambda est @DisallowComposableCalls.
    val primaryActionColor = appColors.primaryAction
    val accentTextColor = appColors.accentText
    val zoneColors: Map<String, Color> = remember(primaryActionColor, accentTextColor) {
        mapOf(
            "Chest" to primaryActionColor,
            "Back" to orangeMedium,
            "Shoulders" to accentTextColor,
            "Arms" to redMedium,
            "Legs" to mediumGreen,
            "Core" to yellowMedium,
            "Other" to mediumPurple,
        )
    }

    // Spread reduit a 0.4 pour Goals (vs Stats 1.0 par defaut) : 1 semaine =
    // peu d'elements par zone, on prefere des nuances clairement vertes/
    // rouges/bleues plutot que des derives gris-clair (user feedback runtime
    // 2026-05-09 : "quads devient presque vert gris clair, trop eloigne du
    // vert initial"). Cf. paletteForZone KDoc pour les ranges precis.
    val goalsPaletteSpread = 0.4f

    val muscleGroupColors: Map<String, Color> = remember(allMuscles, zoneColors) {
        val groupsByZone = allMuscles
            .mapNotNull { m ->
                val g = m.muscleGroup ?: return@mapNotNull null
                val z = m.zone ?: return@mapNotNull null
                g to z
            }
            .distinct()
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, names) -> names.distinct().sorted() }
        groupsByZone.flatMap { (zone, groupNames) ->
            val zoneColor = zoneColors[zone] ?: mediumPurple
            val shades = paletteForZone(zoneColor, groupNames.size, spread = goalsPaletteSpread)
            groupNames.zip(shades)
        }.toMap()
    }

    val muscleColors: Map<String, Color> = remember(allMuscles, zoneColors) {
        val musclesByZone = allMuscles
            .mapNotNull { m ->
                val z = m.zone ?: return@mapNotNull null
                z to m.name
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct().sorted() }
        musclesByZone.flatMap { (zone, names) ->
            val zoneColor = zoneColors[zone] ?: mediumPurple
            val shades = paletteForZone(zoneColor, names.size, spread = goalsPaletteSpread)
            names.zip(shades)
        }.toMap()
    }

    val currentColorMap: Map<String, Color> = when (goalsViewMode) {
        GoalsViewMode.MUSCLE -> muscleColors
        GoalsViewMode.GROUP -> muscleGroupColors
        GoalsViewMode.ZONE -> zoneColors
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(vertical = 18.dp, horizontal = 18.dp)
    ) {
        GoalsHeader(
            isSynced = allMuscleGoals.all { it.synced },
            allGoalDone = allGoalDone,
            onSyncClick = { showSyncDialog = true },
            currentWeekOffset = currentWeekOffset,
            onWeekChanged = { newOffset ->
                viewModel.changeWeekOffset(newOffset)
            },
            onRequestResetWeek = { showResetDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        GoalsProgressBar(
            progress = calculateGoalProgress(allMuscleGoals),
            onMoreOptionsClick = { showGoalsBottomSheet = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Toggles refonte 2026-05-09 : View mode (Muscle/Group/Zone) +
        // Sort mode (5 modes). Affectent simultanement la liste ET le chart.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoalsViewModeToggle(
                current = goalsViewMode,
                onSelect = viewModel::setGoalsViewMode,
            )
            GoalsSortToggle(
                current = goalsSortMode,
                onSelect = viewModel::setGoalsSortMode,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Header table une seule fois (user feedback runtime 2026-05-09 :
        // chaque card recopiait Muscle/Priority/Done/ToDo/Status, prenait
        // trop de place). Affiche pour les 3 view modes (cards GROUP/ZONE
        // passent showTableHeader=false a ZoneGoalsCard, mode MUSCLE n'a pas
        // de card mais directement les rows en dessous).
        // User feedback iter 2 : "Priority" coupe en "Priorit" -> raccourci
        // en "Prio." + Muscle weight 4f -> 5f pour donner plus de place aux
        // noms longs (ex. "Vastus Lateralis", "Semi Tendinosus").
        if (!isEmptyWeek) {
            Row(Modifier.fillMaxWidth()) {
                TableHeaderCell("Muscle", Modifier.weight(5f))
                TableHeaderCell("Prio.", Modifier.weight(2f))
                TableHeaderCell("Done", Modifier.weight(2f))
                Spacer(modifier = Modifier.weight(0.3f))
                TableHeaderCell("ToDo", Modifier.weight(2f))
                Spacer(modifier = Modifier.weight(0.3f))
                TableHeaderCell("Status", Modifier.weight(2f))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ── Zone scrollable (liste) : prend l'espace disponible entre
        // toggles et chart footer fixe en bas.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isEmptyWeek) {
                EmptyGoalsWeekState(
                    onCopyFromLastWeek = { viewModel.copyGoalsFromLastWeek() }
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    when (goalsViewMode) {
                        GoalsViewMode.MUSCLE -> {
                            // Liste flat 1 ligne par muscle goal (header en
                            // dehors de la LazyColumn ci-dessus).
                            items(
                                items = goalsListSorted,
                                key = { "muscle_${it.goal.uuid}" },
                            ) { gp ->
                                GoalRow(
                                    muscleGoal = gp.goal,
                                    muscleName = gp.muscleName,
                                    onMuscleClick = { selected ->
                                        selectedMuscleGoal = selected
                                        showMuscleOptionsSheet = true
                                    },
                                    onTargetClick = { selectedGoal ->
                                        selectedTargetGoal = selectedGoal
                                        showChangeTargetDialog = true
                                    },
                                    onPriorityChanged = { newPriority ->
                                        viewModel.updateMuscleGoalPriority(gp.goal.uuid, newPriority)
                                    },
                                )
                            }
                        }

                        GoalsViewMode.GROUP -> {
                            // Cards regroupees par muscle_group, palette derivee
                            // de la zone du group via paletteForZone.
                            goalsByGroupSorted.forEach { (group, goals) ->
                                item(key = "group_$group") {
                                    ZoneGoalsCard(
                                        zoneName = group,
                                        color = muscleGroupColors[group] ?: mediumPurple,
                                        goals = goals.map { it.goal },
                                        allMuscles = allMuscles,
                                        onMuscleClick = { selected ->
                                            selectedMuscleGoal = selected
                                            showMuscleOptionsSheet = true
                                        },
                                        onTargetClick = { selectedGoal ->
                                            selectedTargetGoal = selectedGoal
                                            showChangeTargetDialog = true
                                        },
                                        onPriorityChanged = { uuid, newPriority ->
                                            viewModel.updateMuscleGoalPriority(uuid, newPriority)
                                        },
                                        showTableHeader = false,
                                        showTitle = true,  // Label flottant en haut a gauche (refonte iter 5).
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }

                        GoalsViewMode.ZONE -> {
                            // Cards regroupees par zone (6 max), couleur primaire
                            // de la zone (cf. zoneColors).
                            goalsByZoneSorted.forEach { (zone, goals) ->
                                item(key = "zone_$zone") {
                                    ZoneGoalsCard(
                                        zoneName = zone,
                                        color = zoneColors[zone] ?: mediumPurple,
                                        goals = goals.map { it.goal },
                                        allMuscles = allMuscles,
                                        onMuscleClick = { selected ->
                                            selectedMuscleGoal = selected
                                            showMuscleOptionsSheet = true
                                        },
                                        onTargetClick = { selectedGoal ->
                                            selectedTargetGoal = selectedGoal
                                            showChangeTargetDialog = true
                                        },
                                        onPriorityChanged = { uuid, newPriority ->
                                            viewModel.updateMuscleGoalPriority(uuid, newPriority)
                                        },
                                        showTableHeader = false,
                                        showTitle = true,  // Label flottant en haut a gauche (refonte iter 5).
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Chart footer sticky (refonte 2026-05-09) : 250dp fixe, toujours
        // visible. Empty state interne si chartData vide.
        Spacer(modifier = Modifier.height(8.dp))
        GoalsAchievementChart(
            bars = chartData,
            colorMap = currentColorMap,
        )

        if (showGoalsBottomSheet) {
            GoalsBottomSheet(
                onDismissRequest = {
                    showGoalsBottomSheet = false
                },
                onAddMuscleToGoals = {
                    showGoalsBottomSheet = false
                    if (userId != null) {
                        showAddMuscleDialog = true
                    } else {
                        showSnackbar(
                            message = context.getString(R.string.vm_user_id_unavailable_reconnect),
                            type = SnackbarType.ERROR
                        )
                    }
                },
            )
        }

        if (showAddMuscleDialog) {
            val uid = userId
            if (uid == null) {
                showSnackbar(
                    message = context.getString(R.string.vm_user_id_unavailable_reconnect),
                    type = SnackbarType.ERROR
                )
                showAddMuscleDialog = false
                return
            }
            val canAddGoal = !selectedMuscleName.isNullOrBlank() && !selectedTarget.isNullOrBlank()
            val addGoalDisabledReason = when {
                selectedMuscleName.isNullOrBlank() -> stringResource(R.string.form_error_muscle_required)
                selectedTarget.isNullOrBlank() -> stringResource(R.string.form_error_target_required)
                else -> null
            }
            AlertDialog(
                onDismissRequest = { showAddMuscleDialog = false },
                confirmButton = {
                    DialogPrimaryButton(
                        text = stringResource(R.string.goals_add),
                        enabled = canAddGoal,
                        onClick = {
                        val name = selectedMuscleName?.trim()
                        val target = selectedTarget?.trim()
                        val uuid = name?.let { getMuscleUUID(it, allMuscles) }
                        if (!name.isNullOrEmpty() && !target.isNullOrEmpty() && uuid != null) {
                            val newGoal = MuscleGoal(
                                userId = uid,
                                muscleUUID = uuid,
                                target = target,
                                priority = newMusclePriority,
                                done = 0,
                                addedManually = true,
                                synced = false,
                                uuid = UUID.randomUUID().toString(),
                                weekISO = weekISO,
                            )

                            val alreadyExists = viewModel.muscleGoals.value.any {
                                it.muscleUUID == uuid && it.weekISO == weekISO
                            }
                            if (alreadyExists) {
                                showSnackbar(
                                    message = context.getString(R.string.vm_goal_already_exists),
                                    type = SnackbarType.WARNING,
                                    duration = SnackbarDuration.Long
                                )
                            }
                            else {
                                viewModel.addMuscleGoal(newGoal)
                            }

                            selectedMuscleName = null
                            selectedTarget = null
                            showAddMuscleDialog = false
                        }
                        })
                },
                dismissButton = {
                    DialogSecondaryButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { showAddMuscleDialog = false },
                    )
                },
                title = { Text(androidx.compose.ui.res.stringResource(R.string.goals_add_muscle_title), color = appColors.primaryAction) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterDropdown(
                            label = androidx.compose.ui.res.stringResource(R.string.goals_select_muscle),
                            options = allMuscleNames,
                            selected = selectedMuscleName,
                            onSelect = { selectedMuscleName = it }
                        )
                        FilterDropdown(
                            label = androidx.compose.ui.res.stringResource(R.string.goals_select_target),
                            options = targetOptions,
                            selected = selectedTarget,
                            onSelect = { selectedTarget = it }
                        )
                        PriorityPicker(
                            selected = newMusclePriority,
                            onSelect = { newMusclePriority = it }
                        )
                        DialogValidationReason(reason = addGoalDisabledReason)
                    }
                },
                containerColor = appColors.bgScreen
            )
        }

        if (showMuscleOptionsSheet && selectedMuscleGoal != null) {
            val goal = selectedMuscleGoal!!
            MuscleOptionsBottomSheet(
                muscleGoal = goal,
                muscleName = getMuscleName(goal.muscleUUID, allMuscles),
                onSeeMuscle = {
                    navController.navigate(Routes.muscle(goal.muscleUUID))
                    showMuscleOptionsSheet = false
                },
                onDismissRequest = {
                    showMuscleOptionsSheet = false
                    selectedMuscleGoal = null
                },
                onChangeStatus = {
                    showMuscleOptionsSheet = false
                    showChangeGoalStatusDialog = true
                },
                onToggleDone = {
                    selectedMuscleGoal?.let { goal ->
                        val newStatus = if (goal.status == "DONE") "IN_PROGRESS" else "DONE"
                        viewModel.updateMuscleGoalStatus(goal.uuid, newStatus)
                        showMuscleOptionsSheet = false
                        selectedMuscleGoal = null
                    }
                },
                onDelete = {
                    viewModel.markMuscleGoalForDeletion(selectedMuscleGoal!!)
                    selectedMuscleGoal = null
                    showMuscleOptionsSheet = false
                },
            )
        }

        if (showChangeTargetDialog && selectedTargetGoal != null) {
            TargetPickerDialog(
                currentTarget = selectedTargetGoal!!.target,
                onDismiss = { showChangeTargetDialog = false },
                onTargetSelected = { newTarget ->
                    viewModel.updateMuscleGoalTarget(selectedTargetGoal!!.uuid, newTarget)
                    showChangeTargetDialog = false
                }
            )
        }

        if (showChangeGoalStatusDialog && selectedMuscleGoal != null) {
            val goal = selectedMuscleGoal!!

            ChangeGoalStatusDialog(
                currentStatusRaw = goal.status,
                onDismiss = { showChangeGoalStatusDialog = false },
                onConfirm = { newStatus ->
                    viewModel.updateMuscleGoalStatus(goal.uuid, newStatus)
                    showChangeGoalStatusDialog = false
                }
            )
        }



        if (showSyncDialog) {
            ConfirmationDialog(
                title = stringResource(R.string.goals_sync_title),
                message = stringResource(R.string.goals_sync_message),
                confirmButtonText = stringResource(R.string.goals_sync_now),
                dismissButtonText = stringResource(R.string.common_cancel),
                confirmButtonColor = appColors.primaryAction,
                onConfirm = {
                    viewModel.syncAllMuscleGoals()
                    showSyncDialog = false
                },
                onDismiss = { showSyncDialog = false }
            )
        }

        if (showResetDialog) {
            ConfirmationDialog(
                title = stringResource(R.string.goals_return_current_title),
                message = stringResource(R.string.goals_return_current_message),
                confirmButtonText = stringResource(R.string.common_yes),
                dismissButtonText = stringResource(R.string.common_cancel),
                confirmButtonColor = appColors.primaryAction,
                onConfirm = {
                    viewModel.changeWeekOffset(0)
                    showResetDialog = false
                },
                onDismiss = { showResetDialog = false }
            )
        }

    }
}

fun NormalizedZone.displayLabel(): String = when (this) {
    NormalizedZone.FULL_BODY -> "Full Body"
    NormalizedZone.UPPER_BODY -> "Upper Body"
    NormalizedZone.LOWER_BODY -> "Lower Body"
    NormalizedZone.OTHER -> "Other"
    NormalizedZone.CHEST -> "Chest"
    NormalizedZone.BACK -> "Back"
    NormalizedZone.SHOULDERS -> "Shoulders"
    NormalizedZone.ARMS -> "Arms"
    NormalizedZone.LEGS -> "Legs"
    NormalizedZone.CORE -> "Core"
    NormalizedZone.UPPER_ARMS -> "Upper Arms"
    NormalizedZone.LOWER_ARMS -> "Lower Arms"
    NormalizedZone.UPPER_LEG -> "Upper Leg"
    NormalizedZone.LOWER_LEG -> "Lower Leg"
}
