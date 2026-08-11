package com.example.sportapp.feature.muscles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.feature.muscles.ui.components.muscleListScreen.MuscleCard
import com.example.sportapp.designsystem.common_components.ListSearchHeader
import com.example.sportapp.feature.muscles.ui.components.muscleListScreen.MuscleListOptionsBottomSheet
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.DialogValidationReason
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.core.data.Zones
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.FilterDropdown
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import com.example.sportapp.feature.muscles.viewmodel.MuscleListScreenViewModel

@Composable
fun MuscleListScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: MuscleListScreenViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val userId by viewModel.userId.collectAsState()

    val allMuscles by viewModel.allMuscles.collectAsStateWithLifecycle()
    val allSynced = allMuscles.all { it.synced }

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var sortOption by remember { mutableStateOf("Name (A-Z)") }

    val showOptionsSheet = remember { mutableStateOf(false) }
    var muscleToDelete by remember { mutableStateOf<Muscle?>(null) }

    // =================== remember methods ===================== //

    val showSyncSheet = remember { mutableStateOf(false) }

    val showAddMuscleDialog = remember { mutableStateOf(false) }
    val newMuscleName = remember { mutableStateOf("") }
    val newMuscleZone = remember { mutableStateOf("") }

    var muscleToEdit by remember { mutableStateOf<Muscle?>(null) }
    val editedName = remember { mutableStateOf(muscleToEdit?.name ?: "") }
    val editedZone = remember { mutableStateOf(muscleToEdit?.zone ?: "") }

    // Filtre par zone (même pattern que ExerciseListScreen) : "All" + zones canoniques présentes.
    var selectedZone by remember { mutableStateOf("All") }
    val zoneOptions = remember(allMuscles) {
        val presentZones = allMuscles.mapNotNull { it.zone?.trim() }.toSet()
        listOf("All") + Zones.ALL.filter { it in presentZones }
    }

    val filteredMuscles = allMuscles
        .filter {
            it.name.contains(searchQuery.text, ignoreCase = true)
        }
        .filter { selectedZone == "All" || it.zone?.trim() == selectedZone }
        .sortedWith(when (sortOption) {
            "Name (A-Z)" -> compareBy { it.name.lowercase() }
            "Name (Z-A)" -> compareByDescending { it.name.lowercase() }
            else -> compareBy { it.name.lowercase() }
        })

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {

        ScreenTitleBar(title = stringResource(R.string.muscle_list_title))

        Column(modifier = Modifier.padding(horizontal = 18.dp)) {

            Spacer(modifier = Modifier.height(8.dp))
            TitledDivider("Actions")
            Spacer(modifier = Modifier.height(8.dp))

            ListSearchHeader(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                searchPlaceholder = stringResource(R.string.muscle_list_search_short),
                resultsCountText = stringResource(
                    R.string.muscle_list_results_count,
                    filteredMuscles.size,
                    sortOption
                ),
                allSynced = allSynced,
                onSyncClick = { showSyncSheet.value = true },
                onMoreClick = { showOptionsSheet.value = true },
                onSortChange = { sortOption = it },
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilterDropdown(
                label = stringResource(R.string.exercise_list_filter_zone),
                options = zoneOptions,
                selected = selectedZone,
                onSelect = { selectedZone = it }
            )

            Spacer(modifier = Modifier.height(8.dp))
            TitledDivider("Muscles")
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredMuscles, key = { it.uuid }) { muscle ->
                    MuscleCard(
                        muscle = muscle,
                        onDelete = {
                            muscleToDelete = muscle
                        },
                        onToggleFavorite = {
                            viewModel.toggleFavorite(muscle)
                        },
                        onSync = {
                            showSyncSheet.value = true
                        },
                        onEdit = {
                            muscleToEdit = muscle
                            editedName.value = muscle.name
                            editedZone.value = muscle.zone.orEmpty()
                        },
                        onNavigate = {
                            navController.navigate(Routes.muscle(muscle.uuid))
                        },
                    )
                }
            }
        }
    }

    if (showSyncSheet.value) {
        ConfirmationDialog(
            title = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_list_sync_title),
            message = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_list_sync_message),
            confirmButtonColor = appColors.primaryAction,
            confirmButtonText = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.common_sync),
            onConfirm = {
                viewModel.syncMuscles()
                showSyncSheet.value = false
            },
            onDismiss = {
                showSyncSheet.value = false
            }
        )
    }

    if (showOptionsSheet.value) {
        MuscleListOptionsBottomSheet(
            onDismissRequest = { showOptionsSheet.value = false },
            onAddSample = {
                showAddMuscleDialog.value = true
                showOptionsSheet.value = false
            },
            onClearAll = {
                //viewModel.clearAllMuscles()
                showOptionsSheet.value = false
            },
            onExport = {
                // TODO
                showOptionsSheet.value = false
            }
        )
    }

    if (showAddMuscleDialog.value) {
        val uid = userId
        val userIdUnavailableMsg = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.vm_user_id_unavailable)
        if (uid == null) {
            showSnackbar(
                message = userIdUnavailableMsg,
                type = SnackbarType.ERROR
            )
            showAddMuscleDialog.value = false
            return
        }
        val canAddMuscle = newMuscleName.value.isNotBlank()
        AlertDialog(
            onDismissRequest = {
                showAddMuscleDialog.value = false
                newMuscleName.value = ""
                newMuscleZone.value = ""
            },
            confirmButton = {
                DialogPrimaryButton(
                    text = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.goals_add),
                    enabled = canAddMuscle,
                    onClick = {
                    val name = newMuscleName.value.trim()
                    val zone = newMuscleZone.value.trim()

                    if (name.isNotEmpty()) {
                        val newMuscle = Muscle(
                            userId = uid,
                            uuid = java.util.UUID.randomUUID().toString(),
                            name = name,
                            zone = if (zone.isNotEmpty()) zone else null,
                            isFavorite = false,
                            synced = false,
                            updatedAt = getNowISO8601()
                        )
                        viewModel.addMuscle(newMuscle)

                        // Reset
                        newMuscleName.value = ""
                        newMuscleZone.value = ""
                        showAddMuscleDialog.value = false
                    }
                })
            },
            dismissButton = {
                DialogSecondaryButton(
                    text = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.common_cancel),
                    onClick = {
                        showAddMuscleDialog.value = false
                        newMuscleName.value = ""
                        newMuscleZone.value = ""
                    },
                )
            },
            title = {
                Text(androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_list_add_dialog_title), color = appColors.primaryAction)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CustomTextField(
                        value = newMuscleName.value,
                        onValueChange = { newMuscleName.value = it },
                        placeholder = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_field_name),
                        textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction)
                    )
                    CustomTextField(
                        value = newMuscleZone.value,
                        onValueChange = { newMuscleZone.value = it },
                        placeholder = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_field_zone),
                        textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction)
                    )
                    DialogValidationReason(
                        reason = if (!canAddMuscle) stringResource(R.string.form_error_name_required) else null,
                    )
                }
            },
            containerColor = appColors.bgScreen
        )
    }

    if (muscleToEdit != null) {
        val canEditMuscle = editedName.value.isNotBlank()
        AlertDialog(
            onDismissRequest = { muscleToEdit = null },
            confirmButton = {
                DialogPrimaryButton(
                    text = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.planned_update),
                    enabled = canEditMuscle,
                    onClick = {
                    val editedName = editedName.value.trim()
                    val editedZone = editedZone.value.trim()

                    if (editedName.isNotEmpty()) {
                        val updated = muscleToEdit!!.copy(
                            name = editedName,
                            zone = editedZone.ifEmpty { null },
                            synced = false
                        )
                        viewModel.updateMuscle(updated)
                        muscleToEdit = null
                    }
                })
            },
            dismissButton = {
                DialogSecondaryButton(
                    text = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.common_cancel),
                    onClick = { muscleToEdit = null },
                )
            },
            title = {
                Text(androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_list_edit_dialog_title), color = appColors.primaryAction)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CustomTextField(
                        label = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_field_name),
                        value = editedName.value,
                        onValueChange = { editedName.value = it },
                        placeholder = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_field_name),
                        textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction)
                    )
                    CustomTextField(
                        label = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_field_zone),
                        value = editedZone.value,
                        onValueChange = { editedZone.value = it },
                        placeholder = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_field_zone),
                        textStyle = LocalTextStyle.current.copy(color = appColors.primaryAction)
                    )
                    DialogValidationReason(
                        reason = if (!canEditMuscle) stringResource(R.string.form_error_name_required) else null,
                    )
                }
            },
            containerColor = appColors.bgScreen
        )
    }


    if (muscleToDelete != null) {
        ConfirmationDialog(
            title = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_list_delete_title),
            message = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.muscle_list_delete_message, muscleToDelete?.name ?: ""),
            onConfirm = {
                muscleToDelete?.let {
                    viewModel.deleteMuscle(it)
                }
                muscleToDelete = null
            },
            onDismiss = {
                muscleToDelete = null
            }
        )
    }
}
