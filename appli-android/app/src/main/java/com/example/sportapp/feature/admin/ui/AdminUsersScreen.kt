package com.example.sportapp.feature.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.feature.admin.ui.components.AdminUserRow
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Snackbar feedback : géré globalement via SnackbarController + showSnackbar()
    // depuis le VM. Le host visuel est dans MainActivity (SnackbarController),
    // pas besoin de SnackbarHost local ici.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        // Header : Box racine -> title aligné Center absolu (centré sur la page
        // entière indépendamment du bouton à droite), refresh button aligné
        // CenterEnd. Background "appColors.bgSurface" par défaut sur le bouton (hasBackground
        // = true) pour qu'il soit distinct du fond header SessionTabExerciseBackground.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(appColors.bgSurface),
        ) {
            Text(
                text = stringResource(R.string.admin_users_title),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_refresh,
                hasBackground = true,
                customBackgroundColor = blueMedium,  // exception : appColors.bgSurface ≈ fond header, blueMedium distinct
                tint = appColors.textPrimary,
                onClick = { viewModel.refresh() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            )
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
            when (val s = state) {
                is AdminUsersViewModel.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = appColors.primaryAction)
                    }
                }
                is AdminUsersViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.admin_users_error_title),
                            color = redMedium,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = s.message,
                            color = appColors.textPrimary.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.admin_users_retry), color = appColors.primaryAction)
                        }
                    }
                }
                is AdminUsersViewModel.UiState.Loaded -> {
                    val admins = s.users.filter { it.isAdmin }
                    val nonAdmins = s.users.filter { !it.isAdmin }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (admins.isNotEmpty()) {
                            item(key = "header_admins") {
                                SectionHeader(text = stringResource(R.string.admin_users_section_admins, admins.size))
                            }
                            items(admins, key = { it.id }) { user ->
                                AdminUserRow(
                                    user = user,
                                    isCurrentUser = (user.id == viewModel.currentUserId),
                                    onToggleClick = {
                                        viewModel.toggleAdmin(user.id, !user.isAdmin)
                                    }
                                )
                            }
                        }
                        if (nonAdmins.isNotEmpty()) {
                            item(key = "header_users") {
                                SectionHeader(text = stringResource(R.string.admin_users_section_users, nonAdmins.size))
                            }
                            items(nonAdmins, key = { it.id }) { user ->
                                AdminUserRow(
                                    user = user,
                                    isCurrentUser = (user.id == viewModel.currentUserId),
                                    onToggleClick = {
                                        viewModel.toggleAdmin(user.id, !user.isAdmin)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.5.dp,
            color = appColors.dividerStrong
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = appColors.primaryAction,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = appColors.primaryAction.copy(alpha = 0.3f)
        )
    }
}
