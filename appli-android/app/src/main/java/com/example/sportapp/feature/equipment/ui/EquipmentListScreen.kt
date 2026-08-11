package com.example.sportapp.feature.equipment.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.DialogValidationReason
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.EntityListRow
import com.example.sportapp.designsystem.common_components.FilterDropdown
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.ListSearchHeader
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.yellowMedium
import com.example.sportapp.feature.equipment.viewmodel.EquipmentItem
import com.example.sportapp.feature.equipment.viewmodel.EquipmentListViewModel
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar

/**
 * Écran Matériel — liste autonome (pendant Android de la page web `/materiel`,
 * cf. docs/ANDROID_PARITY.md §B). Filtre possession (Tout · Mon matériel · Hors
 * mon matériel), recherche + tri, rows « catalogue global ∪ matériel perso hors
 * catalogue » avec toggle « mon matériel » (étoile). La flèche / le nom ouvrent
 * le détail. Le bouton « + » (more) ajoute un matériel perso. Aucune écriture sur
 * le catalogue global `Equipment` (Type C admin, politique 8) — l'ajout crée
 * uniquement de l'AvailableEquipment user-scoped, comme le web.
 */
@Composable
fun EquipmentListScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: EquipmentListViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val allSynced = items.all { it.synced }

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    val sortAsc = stringResource(R.string.exercise_list_sort_asc)
    val sortDesc = stringResource(R.string.exercise_list_sort_desc)
    var sortOption by remember(sortAsc) { mutableStateOf(sortAsc) }

    // Filtre possession : Tout / Mon matériel / Hors mon matériel.
    val filterAll = stringResource(R.string.material_filter_all)
    val filterMine = stringResource(R.string.material_filter_mine)
    val filterNotMine = stringResource(R.string.material_filter_not_mine)
    var selectedFilter by remember(filterAll) { mutableStateOf(filterAll) }
    val filterOptions = listOf(filterAll, filterMine, filterNotMine)

    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val filtered = items
        .filter { it.name.contains(searchQuery.text, ignoreCase = true) }
        .filter {
            when (selectedFilter) {
                filterMine -> it.owned
                filterNotMine -> !it.owned
                else -> true
            }
        }
        .sortedWith(
            if (sortOption == sortDesc) compareByDescending { it.name.lowercase() }
            else compareBy { it.name.lowercase() }
        )

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {

        ScreenTitleBar(title = stringResource(R.string.material_title))

        Column(modifier = Modifier.padding(horizontal = 18.dp)) {

            Spacer(modifier = Modifier.height(8.dp))
            TitledDivider(stringResource(R.string.material_divider_actions))
            Spacer(modifier = Modifier.height(8.dp))

            ListSearchHeader(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                searchPlaceholder = stringResource(R.string.material_search_placeholder),
                resultsCountText = stringResource(
                    R.string.material_results_count,
                    filtered.size,
                    sortOption,
                ),
                allSynced = allSynced,
                onSyncClick = { viewModel.sync() },
                onMoreClick = {
                    newName = ""
                    showAddDialog = true
                },
                onSortChange = { sortOption = it },
            )

            FilterDropdown(
                label = stringResource(R.string.material_filter_possession),
                options = filterOptions,
                selected = selectedFilter,
                onSelect = { selectedFilter = it },
            )

            Spacer(modifier = Modifier.height(8.dp))
            TitledDivider(stringResource(R.string.material_divider_list))
            Spacer(modifier = Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                val emptyText =
                    if (searchQuery.text.isNotBlank() || selectedFilter != filterAll)
                        stringResource(R.string.material_empty)
                    else
                        stringResource(R.string.material_empty_default)
                EmptyListRow(text = emptyText, iconRes = R.drawable.ic_exercise)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 10.dp),
                ) {
                    items(filtered, key = { it.name }) { item ->
                        EquipmentRow(
                            item = item,
                            onToggleOwned = { viewModel.toggleOwned(item.name) },
                            onOpen = { navController.navigate(Routes.materialDetail(item.name)) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val uid = userId
        val userIdUnavailableMsg = stringResource(R.string.vm_user_id_unavailable)
        if (uid == null) {
            showSnackbar(message = userIdUnavailableMsg, type = SnackbarType.ERROR)
            showAddDialog = false
            return
        }
        val canAdd = newName.isNotBlank()
        FormDialog(
            title = stringResource(R.string.material_add_title),
            confirmText = stringResource(R.string.common_add),
            confirmEnabled = canAdd,
            disabledReason = stringResource(R.string.form_error_name_required),
            onConfirm = {
                if (canAdd) {
                    viewModel.addPersonalEquipment(newName.trim())
                    showAddDialog = false
                    newName = ""
                }
            },
            onDismiss = {
                showAddDialog = false
                newName = ""
            },
        ) {
            CustomTextField(
                label = stringResource(R.string.material_add_field_name),
                value = newName,
                onValueChange = { newName = it },
                placeholder = stringResource(R.string.material_add_placeholder),
            )
        }
    }
}

/** Row d'un matériel : nom (ouvre le détail) + statut sync + étoile possession + flèche. */
@Composable
private fun EquipmentRow(
    item: EquipmentItem,
    onToggleOwned: () -> Unit,
    onOpen: () -> Unit,
) {
    EntityListRow(
        backgroundColor = appColors.bgRecessed,
        name = item.name,
        nameMaxLines = 1,
        onNameClick = onOpen,
        contentEndPadding = 6.dp,
    ) {
        Icon(
            painter = painterResource(
                if (item.synced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off
            ),
            contentDescription = null,
            tint = if (item.synced) appColors.primaryAction else yellowMedium,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        ActionIconButton(
            iconRes = if (item.owned) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star,
            onClick = onToggleOwned,
            tint = if (item.owned) Color.White else appColors.textPrimary,
            hasBackground = true,
            customBackgroundColor = if (item.owned) orangeMedium else appColors.bgButton,
        )
        Spacer(modifier = Modifier.width(6.dp))
        ActionIconButton(
            iconRes = R.drawable.ic_arrow_right_alt,
            onClick = onOpen,
            tint = appColors.textPrimary,
            hasBackground = true,
            customBackgroundColor = blueMedium,
        )
    }
}
