package com.example.sportapp.feature.equipment.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.EntityListRow
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.feature.equipment.viewmodel.EquipmentDetailViewModel

/**
 * Détail d'un matériel — miroir de la colonne droite de la page web `/materiel` :
 * toggle « Mon matériel » (crée/retire l'AvailableEquipment homonyme, persiste +
 * sync) + liste des exercices qui l'utilisent (flèche → page exercice). La clé
 * est le nom du matériel (la possession est insensible à la casse).
 */
@Composable
fun EquipmentDetailScreen(
    equipmentName: String,
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: EquipmentDetailViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    LaunchedEffect(equipmentName) {
        viewModel.setEquipmentName(equipmentName)
    }

    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val exercises by viewModel.exercisesUsing.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        ScreenTitleBar(title = detail?.name ?: equipmentName)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TitledDivider(stringResource(R.string.material_detail_actions)) }

            // Action bar : back + toggle « mon matériel » pleine largeur.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionIconButton(
                        iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
                        onClick = { navController.popBackStack() },
                        tint = appColors.textPrimary,
                    )

                    val owned = detail?.owned == true
                    ActionIconWithTextButton(
                        iconRes = if (owned) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star,
                        text = if (owned)
                            stringResource(R.string.material_detail_remove_mine)
                        else
                            stringResource(R.string.material_detail_add_mine),
                        tint = if (owned) orangeMedium else appColors.textPrimary,
                        textColor = if (owned) orangeMedium else appColors.textPrimary,
                        backgroundColor = if (owned) appColors.selectedFill else appColors.primaryAction,
                        onClick = { detail?.let { viewModel.toggleOwned(it.name) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                TitledDivider(
                    if (detail?.inCatalog == false)
                        stringResource(R.string.material_detail_exercises_simple)
                    else
                        stringResource(R.string.material_detail_exercises_title, exercises.size)
                )
            }

            when {
                detail?.inCatalog == false -> item {
                    EmptyListRow(text = stringResource(R.string.material_detail_no_catalog))
                }
                exercises.isEmpty() -> item {
                    EmptyListRow(text = stringResource(R.string.material_detail_no_exercises))
                }
                else -> items(exercises, key = { it.uuid }) { exercise ->
                    EntityListRow(
                        backgroundColor = appColors.bgRecessed,
                        name = exercise.name,
                        nameMaxLines = 1,
                        onNameClick = { navController.navigate(Routes.exercise(exercise.uuid)) },
                        contentEndPadding = 6.dp,
                    ) {
                        ActionIconButton(
                            iconRes = R.drawable.ic_arrow_right_alt,
                            onClick = { navController.navigate(Routes.exercise(exercise.uuid)) },
                            tint = appColors.textPrimary,
                            hasBackground = true,
                            customBackgroundColor = blueMedium,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
