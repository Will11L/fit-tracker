package com.example.sportapp.wear.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.sportapp.wear.R
import com.example.sportapp.wear.WearHealthViewModel

private val FOREGROUND_PERMISSIONS = arrayOf(
    Manifest.permission.BODY_SENSORS,
    Manifest.permission.ACTIVITY_RECOGNITION,
)

private fun hasForegroundPermissions(context: Context): Boolean =
    FOREGROUND_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** BODY_SENSORS_BACKGROUND requise dès Android 13 (API 33) pour la FC en fond. */
private fun hasBackgroundPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BODY_SENSORS_BACKGROUND,
        ) == PackageManager.PERMISSION_GRANTED

@Composable
fun WearHealthApp(viewModel: WearHealthViewModel) {
    val context = LocalContext.current
    var foregroundGranted by remember { mutableStateOf(hasForegroundPermissions(context)) }
    var backgroundGranted by remember { mutableStateOf(hasBackgroundPermission(context)) }
    var backgroundSkipped by remember { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        foregroundGranted = hasForegroundPermissions(context)
        backgroundGranted = hasBackgroundPermission(context)
    }
    // Android 13+ : BODY_SENSORS_BACKGROUND se demande SÉPARÉMENT après le
    // premier plan (une demande simultanée est rejetée, cf. background location).
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { backgroundGranted = hasBackgroundPermission(context) }

    Scaffold(timeText = { TimeText() }) {
        when {
            !foregroundGranted -> PermissionContent(
                message = stringResource(R.string.wear_permission_needed),
                buttonLabel = stringResource(R.string.wear_permission_button),
                onGrant = { foregroundLauncher.launch(FOREGROUND_PERMISSIONS) },
            )
            !backgroundGranted && !backgroundSkipped -> BackgroundPermissionContent(
                onGrant = { backgroundLauncher.launch(Manifest.permission.BODY_SENSORS_BACKGROUND) },
                onSkip = { backgroundSkipped = true },
            )
            else -> {
                // Capteurs + heartbeat actifs uniquement en premier plan.
                LifecycleResumeEffect(Unit) {
                    viewModel.start()
                    onPauseOrDispose { viewModel.stop() }
                }
                LiveContent(viewModel)
            }
        }
    }
}

@Composable
private fun LiveContent(viewModel: WearHealthViewModel) {
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val hr by viewModel.hr.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.wear_title),
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary,
        )
        Spacer(Modifier.height(8.dp))
        Metric(
            label = stringResource(R.string.wear_steps_label),
            value = steps?.toString() ?: stringResource(R.string.wear_no_value),
        )
        Spacer(Modifier.height(6.dp))
        Metric(
            label = stringResource(R.string.wear_hr_label),
            value = hr?.let { stringResource(R.string.wear_hr_value, it) }
                ?: stringResource(R.string.wear_measuring),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (sending) R.string.wear_sending else R.string.wear_not_sending,
            ),
            style = MaterialTheme.typography.caption2,
            color = if (sending) MaterialTheme.colors.primary else MaterialTheme.colors.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.title2,
        color = MaterialTheme.colors.onSurface,
    )
}

@Composable
private fun PermissionContent(message: String, buttonLabel: String, onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onGrant) {
            Text(buttonLabel)
        }
    }
}

/** Étape séparée (Android 13+) : demande BODY_SENSORS_BACKGROUND (« tout le
 *  temps ») pour la FC en fond ; « Plus tard » continue en mode dégradé. */
@Composable
private fun BackgroundPermissionContent(onGrant: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.wear_background_needed),
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onGrant) {
            Text(stringResource(R.string.wear_background_button))
        }
        Spacer(Modifier.height(6.dp))
        Button(onClick = onSkip, colors = ButtonDefaults.secondaryButtonColors()) {
            Text(stringResource(R.string.wear_background_skip))
        }
    }
}
