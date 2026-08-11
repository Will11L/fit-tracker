package com.example.sportapp.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.sync.base.EntityStats
import kotlinx.coroutines.flow.Flow
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.feature.settings.viewmodel.SyncSettingsViewModel
import com.example.sportapp.R
import com.example.sportapp.core.network.TokenManager
import com.example.sportapp.designsystem.common_components.TitledDivider
import androidx.compose.ui.res.painterResource
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar

@Composable
fun SyncSettingsScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: SyncSettingsViewModel = hiltViewModel(),
    onRequireLogin: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val isWsConnected by viewModel.isWsConnected.collectAsState()
    val isTokenValid by viewModel.isTokenValid.collectAsState()

    val tables by viewModel.tables.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadLocalTables()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(16.dp)
    ) {
        // NOTE i18n : ce screen est un outil dev/admin (boutons Get All / Upsert /
        // Log DB / Clear DB / Verify Token / etc.). Les labels boutons + messages
        // snackbar restent EN canonique car internes. Le titre principal est traduit.
        TitledDivider(title = androidx.compose.ui.res.stringResource(com.example.sportapp.R.string.sync_settings_title))

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 Boutons (section) — non-scrollable, FIXÉ
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Ligne 1 : Get All + Upsert + Sync All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_cloud_download,
                            text = "Get All",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.getAllTablesFromServer { success ->
                                    showSnackbar(
                                        message = if (success) "Tables fetched" else "Fetch failed",
                                        type = if (success) SnackbarType.INFO else SnackbarType.ERROR,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_cloud_upload,
                            text = "Upsert",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.upsertAllTablesToServer { success ->
                                    showSnackbar(
                                        message = if (success) "Upserted" else "Upsert failed",
                                        type = if (success) SnackbarType.INFO else SnackbarType.ERROR,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_cloud_sync,
                            text = "Sync All",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.syncAllData() }
                        )
                    }

                    // Ligne 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_delete_sweep,
                            text = "Clear DB",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.clearAllLocalData { success ->
                                    showSnackbar(
                                        message = if (success) "Cleared !" else "Failed to clear",
                                        type = if (success) SnackbarType.SUCCESS else SnackbarType.ERROR,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_cloud_download,
                            text = "Merge",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.mergeAllFromServer { success ->
                                    showSnackbar(
                                        message = if (success) "Merge OK" else "Merge failed",
                                        type = if (success) SnackbarType.SUCCESS else SnackbarType.ERROR,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    }

                    // Ligne 4 - Token
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 🔹 Relogin
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_refresh,
                            text = "Re-login",
                            modifier = Modifier.weight(1f),
                            hasBackground = true,
                            backgroundColor = if (isTokenValid) mediumGreen else redMedium,
                            onClick = {
                                showSnackbar(
                                    message = "Merci de vous reconnecter pour renouveler la session",
                                    type = SnackbarType.WARNING,
                                    duration = SnackbarDuration.Short
                                )
                                onRequireLogin()
                            }
                        )

                        // 🔹 Verify Token
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_rounded_check_circle,
                            text = "Verify Token",
                            modifier = Modifier.weight(1f),
                            hasBackground = true,
                            backgroundColor = if (isTokenValid) mediumGreen else redMedium,
                            onClick = {
                                if (TokenManager.token.isNullOrBlank()) {
                                    showSnackbar("No token", type = SnackbarType.ERROR, duration = SnackbarDuration.Short)
                                } else {
                                    viewModel.verifyToken { valid ->
                                        showSnackbar(
                                            message = if (valid) "Token is valid" else "Token invalid",
                                            type = if (valid) SnackbarType.SUCCESS else SnackbarType.ERROR,
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // Ligne 5 — WS + Check Unsynced sur la même ligne
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionIconWithTextButton(
                            iconRes = if (!isWsConnected) R.drawable.ic_cloud_alert else R.drawable.ic_cloud_done,
                            text = if (isWsConnected) "WS OK" else "Restart WS",
                            clickable = !isWsConnected,
                            hasBackground = true,
                            backgroundColor = if (isWsConnected) mediumGreen else redMedium,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!isWsConnected) {
                                    val token = TokenManager.token
                                    if (token != null) {
                                        viewModel.restartWebSocket(token)
                                        showSnackbar(
                                            message = "WebSocket restarted",
                                            type = SnackbarType.INFO,
                                            duration = SnackbarDuration.Short
                                        )
                                    } else {
                                        showSnackbar(
                                            message = "No token",
                                            type = SnackbarType.ERROR,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        )

                        val hasUnsynced by viewModel.hasUnsyncedData.collectAsState()
                        ActionIconWithTextButton(
                            iconRes = if (hasUnsynced) R.drawable.ic_cloud_alert else R.drawable.ic_cloud_done,
                            text = if (hasUnsynced) "Unsynced data!" else "All synced",
                            hasBackground = true,
                            backgroundColor = if (hasUnsynced) redMedium else mediumGreen,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.checkUnsynced()
                                showSnackbar(
                                    message = if (hasUnsynced) "Des données non synchronisées existent"
                                    else "Tout est synchronisé ✅",
                                    type = if (hasUnsynced) SnackbarType.WARNING else SnackbarType.SUCCESS,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        )
                    }
                }

        // 🔹 Séparateur entre boutons et liste des tables (non-scrollable, FIXÉ)
        Spacer(modifier = Modifier.height(8.dp))
        TitledDivider(title = "Tables")
        Spacer(modifier = Modifier.height(4.dp))

        // 🔹 LazyColumn ne contient QUE les tables (scrollable).
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 🔹 Tables : row inline reproduisant le visuel EntityListRow (Box outer
            // vertical 5dp → Row 44dp bg/bgRecessed → name Box 1/2 of row width +
            // sync icon aligné verticalement + pill X/Y collé à droite + chevron).
            tables.forEach { (title, config) ->
                item {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val nameBoxWidth = maxWidth / 2
                        // Stats unique observation (icon + pill partagent l'état)
                        val stats = config.statsFlow?.collectAsState(initial = EntityStats.EMPTY)?.value
                        val (statsColor, statsIcon) = when {
                            stats == null -> lightGrayBlue to null
                            stats.pendingDeletion > 0 -> redMedium to R.drawable.ic_cloud_alert
                            stats.unsynced > 0 -> yellowMedium to R.drawable.ic_cloud_off
                            stats.total == 0 -> lightGrayBlue to null
                            else -> mediumGreen to R.drawable.ic_cloud_done
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(
                                        appColors.bgRecessed,
                                        shape = MaterialTheme.shapes.small,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Name Box : 1/2 of row width
                                Box(
                                    modifier = Modifier
                                        .width(nameBoxWidth)
                                        .height(44.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            navController.navigate(Routes.syncTableDetail(title))
                                        }
                                        .background(
                                            appColors.bgSurface,
                                            shape = MaterialTheme.shapes.small,
                                        )
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = title,
                                        color = appColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // Sync icon : offset fixe depuis le name Box → alignée
                                // verticalement entre toutes les rows. Slot 20dp réservé
                                // même si icône absente (stats.total == 0 → neutre).
                                Spacer(modifier = Modifier.width(16.dp))
                                Box(
                                    modifier = Modifier.size(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (statsIcon != null) {
                                        Icon(
                                            painter = painterResource(statsIcon),
                                            contentDescription = null,
                                            tint = statsColor,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Pill X/Y collé à droite (avant le chevron)
                                if (stats != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statsColor.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "${stats.total - stats.unsynced}/${stats.total}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = statsColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                ActionIconButton(
                                    iconRes = R.drawable.ic_keyboard_arrow_right,
                                    onClick = {
                                        navController.navigate(Routes.syncTableDetail(title))
                                    },
                                    customBackgroundColor = blueMedium,
                                    tint = appColors.textPrimary,
                                    boxSize = 44.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Badge compact sous le titre de section : icone d'etat sync (HORS du pill, a
 * gauche) + pill `X/Y`. 4 etats :
 * - pendingDeletion > 0 : icone cloud_alert + pill rouge
 * - unsynced > 0       : icone cloud_off + pill jaune
 * - total == 0          : pas d'icone, pill neutre (lightGrayBlue)
 * - tout synced         : icone cloud_done + pill vert (mediumGreen)
 *
 * T4.2 Phase 4.2 + restyle 2026-05-07 (icones a gauche + theme bleu/orange).
 */
@Composable
private fun EntityStatsBadge(statsFlow: Flow<EntityStats>) {
    val stats by statsFlow.collectAsState(initial = EntityStats.EMPTY)
    val (fg, iconRes) = when {
        stats.pendingDeletion > 0 -> redMedium to R.drawable.ic_cloud_alert
        stats.unsynced > 0 -> yellowMedium to R.drawable.ic_cloud_off
        stats.total == 0 -> lightGrayBlue to null
        else -> mediumGreen to R.drawable.ic_cloud_done
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = fg.copy(alpha = 0.15f),
        ) {
            Text(
                text = "${stats.total - stats.unsynced}/${stats.total}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
