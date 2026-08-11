package com.example.sportapp.feature.settings.ui

import android.app.Activity
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.core.data.ServerUrlPreset
import com.example.sportapp.core.data.ServerUrlRepository
import com.example.sportapp.feature.settings.ServerUrlViewModel
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.appColors

/**
 * Settings -> Server URL (admin users uniquement).
 *
 * Permet de basculer entre PC LAN / Pi prod / URL custom sans rebuild +
 * reinstall. Le changement est persiste en DataStore mais n'est applique
 * qu'au prochain demarrage de l'app (les `by lazy` du RetrofitInstance
 * figent l'URL au 1er touch). Apres Apply : dialog "Restart required" qui
 * propose de killer le process (le user relance manuellement).
 *
 * Visibilite UI : la section est cachee en release (cf. SettingsScreen).
 */
@Composable
fun ServerUrlSettingsScreen(
    onBack: () -> Unit,
    viewModel: ServerUrlViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val context = LocalContext.current

    var showRestartDialog by remember { mutableStateOf(false) }

    // Buffer local pour le champ Custom : on edit dans l'UI puis on persiste
    // au tap "Apply" (evite d'ecrire 1 fois par char dans DataStore).
    var customDraft by remember(snapshot.customUrl) { mutableStateOf(snapshot.customUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        SettingsSubScreenHeader(
            title = stringResource(R.string.settings_category_server_url),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard(title = stringResource(R.string.settings_server_url_section_preset)) {
                Text(
                    text = stringResource(R.string.settings_server_url_description),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )

                SettingsRadioOptions(
                    options = listOf(
                        ServerUrlPreset.PC_LAN to stringResource(R.string.settings_server_url_preset_pc_lan),
                        ServerUrlPreset.PI_PROD to stringResource(R.string.settings_server_url_preset_pi_prod),
                        ServerUrlPreset.CUSTOM to stringResource(R.string.settings_server_url_preset_custom),
                    ),
                    selected = snapshot.preset,
                    onSelected = viewModel::setPreset,
                )

                // Affiche l'URL effective resolue pour le preset choisi (read-only
                // info). En CUSTOM, on prefere afficher le draft (ce que l'user
                // est en train de taper) plutot que la valeur persistee.
                val effectiveApi = when (snapshot.preset) {
                    ServerUrlPreset.PC_LAN -> ServerUrlRepository.PC_LAN_API
                    ServerUrlPreset.PI_PROD -> ServerUrlRepository.PI_PROD_API
                    ServerUrlPreset.CUSTOM -> if (customDraft.isBlank()) {
                        stringResource(R.string.settings_server_url_custom_placeholder)
                    } else {
                        ServerUrlRepository.normalize(customDraft.trim()).first
                    }
                }
                Text(
                    text = stringResource(R.string.settings_server_url_effective, effectiveApi),
                    color = appColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (snapshot.preset == ServerUrlPreset.CUSTOM) {
                SettingsSectionCard(title = stringResource(R.string.settings_server_url_section_custom)) {
                    Text(
                        text = stringResource(R.string.settings_server_url_custom_helper),
                        color = appColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    CustomTextField(
                        value = customDraft,
                        onValueChange = { customDraft = it },
                        placeholder = stringResource(R.string.settings_server_url_custom_placeholder),
                        label = stringResource(R.string.settings_server_url_custom_label),
                    )
                    // Persiste le draft (utile pour "Test connection" qui lit DataStore).
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.bgSurface)
                            .clickable { viewModel.setCustomUrl(customDraft.trim()) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_server_url_save_custom),
                            color = appColors.textPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_server_url_section_test)) {
                Text(
                    text = stringResource(R.string.settings_server_url_test_helper),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.primaryAction)
                            .clickable(
                                enabled = testResult !is ServerUrlViewModel.TestResult.Running,
                            ) { viewModel.testConnection() }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_server_url_test_button),
                            color = appColors.textPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    val (statusText, statusColor) = when (val r = testResult) {
                        is ServerUrlViewModel.TestResult.Idle -> "" to appColors.textTertiary
                        is ServerUrlViewModel.TestResult.Running ->
                            stringResource(R.string.settings_server_url_test_running) to appColors.textSecondary
                        is ServerUrlViewModel.TestResult.Success ->
                            stringResource(R.string.settings_server_url_test_success, r.httpStatus) to appColors.snackbarSuccess
                        is ServerUrlViewModel.TestResult.Failure ->
                            stringResource(R.string.settings_server_url_test_failure, r.message) to appColors.snackbarError
                    }
                    if (statusText.isNotEmpty()) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_server_url_section_apply)) {
                Text(
                    text = stringResource(R.string.settings_server_url_apply_helper),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors.primaryAction)
                        .clickable {
                            // Persiste le draft Custom au cas ou l'user n'a pas
                            // tape "Save" -- evite la perte d'edit.
                            if (snapshot.preset == ServerUrlPreset.CUSTOM) {
                                viewModel.setCustomUrl(customDraft.trim())
                            }
                            showRestartDialog = true
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_server_url_apply_button),
                        color = appColors.textPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            containerColor = appColors.bgRecessed,
            titleContentColor = appColors.textPrimary,
            textContentColor = appColors.textSecondary,
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.settings_server_url_restart_title)) },
            text = { Text(stringResource(R.string.settings_server_url_restart_message)) },
            confirmButton = {
                DialogPrimaryButton(
                    text = stringResource(R.string.settings_server_url_restart_quit),
                    onClick = {
                    showRestartDialog = false
                    // Kill propre : finishAffinity sur l'activity active +
                    // killProcess. L'user relance manuellement depuis le launcher.
                    (context as? ComponentActivity)?.finishAffinity()
                        ?: (context as? Activity)?.finishAffinity()
                    Process.killProcess(Process.myPid())
                })
            },
            dismissButton = {
                DialogSecondaryButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showRestartDialog = false },
                )
            },
        )
    }
}
