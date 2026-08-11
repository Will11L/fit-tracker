package com.example.sportapp.feature.onboarding.ui.steps

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.ui.OnboardingViewModel
import com.example.sportapp.designsystem.common_components.CustomSwitch
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.orangeMedium

/**
 * Étape 3 (dernière) du flow B1 onboarding.
 *
 * **Convention placement settings** (décision user 2026-05-11) :
 * - Permissions système Android (notif, location, etc.) -> ici (call to action
 *   vers Settings.ACTION_APP_*).
 * - Toggle SWITCHES boolean settings (autoSyncOnWifi, sound, vibration, etc.)
 *   -> ici par défaut.
 * - SELECTORS exclusifs (RadioGroup, TimePicker) -> step 2 (Preferences).
 *
 * Quand on AJOUTE un nouveau setting boolean dans `OnboardingPreferences`
 * ou `AppSettings`, exposer le Switch ici si l'user doit pouvoir le régler
 * dès l'onboarding. Sinon laisser pour les écrans Settings classiques.
 */
@Composable
fun OnboardingPermissionsScreen(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesDraft.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()

    var notifEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    // Outer Column fillMaxSize -- inner scrollable Column weight(1f) +
    // phrase finale outside-scroll pinned au bottom (cohérent même si on
    // ajoute plus de cards plus tard).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
    ) {
      Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
      ) {
        // Page main title centré hors card
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Card 1 : System notifications (Android system permission)
        SectionCard(title = stringResource(R.string.onboarding_permissions_card_notif)) {
            Text(
                text = stringResource(
                    if (notifEnabled) R.string.onboarding_permissions_notif_enabled
                    else R.string.onboarding_permissions_notif_disabled
                ),
                color = if (notifEnabled) appColors.textTertiary else orangeMedium,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!notifEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(appColors.primaryAction)
                        .clickable {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_permissions_notif_open),
                        color = appColors.textPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = stringResource(R.string.onboarding_permissions_notif_helper),
                    color = appColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Card 2 : In-app feedback (sound + vibration globaux AppSettings)
        SectionCard(title = stringResource(R.string.onboarding_permissions_card_feedback)) {
            ToggleRow(
                label = stringResource(R.string.onboarding_permissions_toggle_sound),
                checked = appSettings.soundOnInAppNotification,
                onCheckedChange = { viewModel.setSoundOnInAppNotification(it) },
            )
            ToggleRow(
                label = stringResource(R.string.onboarding_permissions_toggle_vibration),
                checked = appSettings.vibrateOnInAppNotification,
                onCheckedChange = { viewModel.setVibrateOnInAppNotification(it) },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Card 3 : Auto sync on wifi
        SectionCard(title = stringResource(R.string.onboarding_permissions_card_autosync)) {
            ToggleRow(
                label = stringResource(R.string.onboarding_permissions_toggle_autosync),
                checked = prefs.autoSyncOnWifi,
                onCheckedChange = { viewModel.setAutoSyncOnWifi(it) },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Card 4 : Sample workouts (demo tour)
        SectionCard(title = stringResource(R.string.onboarding_permissions_card_samples)) {
            Text(
                text = stringResource(R.string.onboarding_permissions_samples_desc),
                color = appColors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            ToggleRow(
                label = stringResource(R.string.onboarding_permissions_toggle_samples),
                checked = prefs.runDemoTour,
                onCheckedChange = { viewModel.setRunDemoTour(it) },
            )
        }
      }  // end inner scrollable Column

      // Phrase finale outside-scroll, petite respiration au-dessus pour ne
      // pas coller au scrollable, et près du footer grâce au bottom=4dp du
      // Column outer.
      Text(
          text = stringResource(R.string.onboarding_permissions_footer_finish),
          color = appColors.textTertiary,
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      )
    }
}

/**
 * Row label + CustomSwitch -- pattern réutilisé pour les toggles boolean
 * dans les SectionCard. Évite la dup de Row{Text,weight,Switch}.
 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
        CustomSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Card section appColors.bgRecessed : titre en appColors.primaryAction + content custom.
 * Pattern réutilisé Preferences/Permissions onboarding.
 */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgRecessed)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = appColors.primaryAction,
        )
        content()
    }
}
