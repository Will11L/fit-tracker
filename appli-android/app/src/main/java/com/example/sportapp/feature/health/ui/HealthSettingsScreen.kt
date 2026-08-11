package com.example.sportapp.feature.health.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CustomSwitch
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.health.domain.HealthConnectAvailability
import com.example.sportapp.feature.health.domain.HealthSnapshot
import com.example.sportapp.feature.health.domain.SleepSessionReading
import com.example.sportapp.feature.health.wear.WearLiveStatus
import com.example.sportapp.feature.settings.ui.SettingsSubScreenHeader
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sous-écran Settings « Connecter les données santé » : statut de connexion
 * Health Connect, flow de permission par type, aperçu de lecture (read-only) et
 * fallback si HC est indisponible. Aucune écriture vers HC.
 */
@Composable
fun HealthSettingsScreen(
    onBack: () -> Unit,
    viewModel: HealthConnectViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val wearLiveUi by viewModel.wearLiveUi.collectAsState()
    val samplingEnabled by viewModel.samplingEnabled.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.permissionRequestContract(),
    ) { granted -> viewModel.onPermissionsResult(granted) }

    // Launcher séparé pour la permission de lecture en arrière-plan (échantillonnage) :
    // demandée après les permissions de premier plan, comme un 2ᵉ palier.
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.permissionRequestContract(),
    ) { granted -> viewModel.onBackgroundPermissionResult(granted) }

    // Retour de l'écran système HC (grant / révocation) -> on relit l'état HC et on
    // interroge la montre (pull à la demande, même app montre fermée).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        viewModel.pullWatch()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen),
    ) {
        SettingsSubScreenHeader(
            title = stringResource(R.string.health_title),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.availability) {
                HealthConnectAvailability.NOT_SUPPORTED -> UnavailableCard()
                HealthConnectAvailability.UPDATE_REQUIRED -> UpdateRequiredCard(
                    onUpdate = { context.startActivity(Intent(Intent.ACTION_VIEW, viewModel.healthConnectStoreUri())) },
                )
                HealthConnectAvailability.INSTALLED -> {
                    StatusCard(
                        state = state,
                        onConnect = { permissionLauncher.launch(viewModel.permissions) },
                        onManage = { context.startActivity(viewModel.healthConnectSettingsIntent()) },
                    )
                    if (state.connectionState != HealthConnectionState.DISCONNECTED) {
                        SnapshotCard(
                            snapshot = state.snapshot,
                            loading = state.loading,
                            onRefresh = {
                                viewModel.refresh()
                                viewModel.pullWatch()
                            },
                        )
                        StepSamplingCard(
                            enabled = samplingEnabled,
                            onToggle = { want ->
                                viewModel.onSamplingToggle(want) {
                                    backgroundPermissionLauncher.launch(viewModel.backgroundPermissions)
                                }
                            },
                        )
                    }
                    TypesInfoCard()
                }
            }

            // Canal live montre (Wearable Data Layer) — indépendant de Health
            // Connect, affichage-only (rien n'est persisté).
            WearLiveCard(wearLiveUi)
        }
    }
}

/* ----------------------------- Cards ----------------------------- */

@Composable
private fun StatusCard(
    state: HealthConnectUiState,
    onConnect: () -> Unit,
    onManage: () -> Unit,
) {
    val (statusLabelRes, statusColor) = when (state.connectionState) {
        HealthConnectionState.CONNECTED -> R.string.health_status_connected to appColors.snackbarSuccess
        HealthConnectionState.PARTIAL -> R.string.health_status_partial to appColors.snackbarWarning
        HealthConnectionState.DISCONNECTED -> R.string.health_status_disconnected to appColors.textTertiary
    }

    SectionCard(title = stringResource(R.string.health_status_title)) {
        Text(
            text = stringResource(statusLabelRes),
            color = statusColor,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.health_status_desc),
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        if (state.connectionState != HealthConnectionState.CONNECTED) {
            PrimaryButton(
                text = stringResource(R.string.health_connect_button),
                onClick = onConnect,
            )
        }
        SecondaryButton(
            text = stringResource(R.string.health_manage_button),
            onClick = onManage,
        )
    }
}

@Composable
private fun SnapshotCard(snapshot: HealthSnapshot?, loading: Boolean, onRefresh: () -> Unit) {
    val refreshLabel = stringResource(R.string.health_refresh)
    SectionCard(
        title = stringResource(R.string.health_snapshot_title),
        // Relecture manuelle à la demande (Samsung Health pousse vers HC par
        // intermittence) : bouton aligné à droite du titre du cadre Aperçu.
        titleTrailing = {
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_refresh,
                hasBackground = false,
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = refreshLabel },
            )
        },
    ) {
        if (loading && snapshot == null) {
            Text(
                text = stringResource(R.string.health_snapshot_loading),
                color = appColors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        val steps = snapshot?.stepsToday
        val distance = snapshot?.distanceMeters
        val kcal = snapshot?.activeKcal
        val avgHr = snapshot?.avgHeartRateBpm
        val lastHr = snapshot?.lastHeartRate
        val spo2 = snapshot?.spo2
        val sleepSessions = snapshot?.sleepSessions.orEmpty()

        SnapshotRow(
            label = stringResource(R.string.health_type_steps),
            value = steps?.let { stringResource(R.string.health_snapshot_steps, it) } ?: EMPTY,
        )
        SnapshotRow(
            label = stringResource(R.string.health_type_distance),
            value = distance?.let {
                stringResource(R.string.health_snapshot_distance, String.format(Locale.getDefault(), "%.2f", it / 1000))
            } ?: EMPTY,
        )
        SnapshotRow(
            label = stringResource(R.string.health_type_active_calories),
            value = kcal?.let { stringResource(R.string.health_snapshot_calories, it.roundToInt()) } ?: EMPTY,
        )
        // FC : dernière mesure (fraîche, comme la montre) + moyenne 24 h (= valeur
        // poussée au serveur). Pattern « les deux » (décision produit 2026-07-03).
        if (lastHr == null && avgHr == null) {
            SnapshotRow(label = stringResource(R.string.health_type_heart_rate), value = EMPTY)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.health_type_heart_rate),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (lastHr != null) {
                        Text(
                            text = stringResource(R.string.health_hr_last, lastHr.value.roundToInt(), lastHr.time),
                            color = appColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (lastHr != null && avgHr != null) {
                        Text(
                            text = "  ·  ",
                            color = appColors.textTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (avgHr != null) {
                        Text(
                            text = stringResource(R.string.health_hr_avg, avgHr.roundToInt()),
                            color = appColors.textTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        SnapshotRow(
            label = stringResource(R.string.health_type_spo2),
            value = spo2?.let { stringResource(R.string.health_snapshot_spo2, it.value.roundToInt(), it.time) } ?: EMPTY,
        )
        // Sommeil : temps dormi + temps au lit, une tranche par session
        // (nuit + éventuelle(s) sieste(s)). Décision produit 2026-07-03.
        if (sleepSessions.isEmpty()) {
            SnapshotRow(label = stringResource(R.string.health_type_sleep), value = EMPTY)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.health_type_sleep),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                sleepSessions.forEach { SleepSessionBlock(it) }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.health_snapshot_window),
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TypesInfoCard() {
    SectionCard(title = stringResource(R.string.health_types_title)) {
        Text(
            text = stringResource(R.string.health_types_desc),
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Toggle « vraies tranches de pas » : active un worker en arrière-plan qui
 * échantillonne le total de pas HC toutes les ~15 min pour construire de vraies
 * barres intraday (Samsung ne fournit qu'un record jour-entier proraté → barres plates).
 * Requiert la permission de lecture en arrière-plan (demandée à l'activation).
 */
@Composable
private fun StepSamplingCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SectionCard(title = stringResource(R.string.health_sampling_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.health_sampling_toggle),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            CustomSwitch(checked = enabled, onCheckedChange = onToggle)
        }
        Text(
            text = stringResource(R.string.health_sampling_desc),
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Canal live montre : valeurs + fraîcheur, ou « interrogation… » / « non connectée ». */
@Composable
private fun WearLiveCard(ui: WearLiveUi) {
    SectionCard(title = stringResource(R.string.health_wear_title)) {
        val display = ui.display
        if (display == null) {
            Text(
                text = stringResource(
                    if (ui.status == WearLiveStatus.QUERYING) R.string.health_wear_querying
                    else R.string.health_wear_disconnected,
                ),
                color = appColors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.health_wear_steps, display.steps),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "  ·  ",
                color = appColors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = display.hr?.let { stringResource(R.string.health_wear_hr, it) }
                    ?: stringResource(R.string.health_wear_hr_waiting),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val stale = ui.status == WearLiveStatus.STALE
        Text(
            text = stringResource(R.string.health_wear_freshness, display.ageSeconds) +
                (if (stale) stringResource(R.string.health_wear_stale) else ""),
            color = if (stale) appColors.textTertiary else appColors.snackbarSuccess,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun UnavailableCard() {
    SectionCard(title = stringResource(R.string.health_status_title)) {
        Text(
            text = stringResource(R.string.health_unavailable),
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UpdateRequiredCard(onUpdate: () -> Unit) {
    SectionCard(title = stringResource(R.string.health_status_title)) {
        Text(
            text = stringResource(R.string.health_update_required),
            color = appColors.snackbarWarning,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = stringResource(R.string.health_update_button),
            onClick = onUpdate,
        )
    }
}

/* --------------------------- Primitives -------------------------- */

private const val EMPTY = "—"

/**
 * Card section (fond appColors.bgRecessed, titre appColors.primaryAction).
 * Réplique locale du pattern utilisé dans les écrans Settings / onboarding.
 */
@Composable
private fun SectionCard(
    title: String,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgRecessed)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (titleTrailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = appColors.primaryAction,
                )
                titleTrailing()
            }
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = appColors.primaryAction,
            )
        }
        content()
    }
}

@Composable
private fun SnapshotRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = appColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Tranche de sommeil : plage horaire + temps dormi (accent) et temps au lit. */
@Composable
private fun SleepSessionBlock(session: SleepSessionReading) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${session.startTime} – ${session.endTime}",
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.health_sleep_asleep, formatHm(session.asleepMinutes)),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "  ·  ",
                color = appColors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.health_sleep_in_bed, formatHm(session.inBedMinutes)),
                color = appColors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Minutes -> "Xh Ym" (format i18n réutilisé). */
@Composable
private fun formatHm(minutes: Long): String =
    stringResource(R.string.health_snapshot_sleep, (minutes / 60).toInt(), (minutes % 60).toInt())

/** CTA plein (couleur d'accent). Pattern inline aligné sur l'onboarding. */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(appColors.primaryAction)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = appColors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Bouton secondaire discret (fond appColors.bgButton). */
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(appColors.bgButton)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = appColors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
