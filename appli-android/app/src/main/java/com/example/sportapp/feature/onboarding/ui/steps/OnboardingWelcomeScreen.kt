package com.example.sportapp.feature.onboarding.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.ui.OnboardingViewModel
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun OnboardingWelcomeScreen(viewModel: OnboardingViewModel) {
    val firstNameDraft by viewModel.firstNameDraft.collectAsState()
    val appName = stringResource(R.string.app_name)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Card appColors.bgRecessed : titre principal en blue + subtitle en gris.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(appColors.bgRecessed)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title, appName),
                style = MaterialTheme.typography.headlineMedium,
                color = appColors.primaryAction,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = appColors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Hors card : prompt + input
        Text(
            text = stringResource(R.string.onboarding_welcome_prompt_name),
            style = MaterialTheme.typography.titleMedium,
            color = appColors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(
            value = firstNameDraft,
            onValueChange = { viewModel.updateFirstNameDraft(it) },
            placeholder = stringResource(R.string.onboarding_welcome_placeholder_name),
            label = stringResource(R.string.onboarding_welcome_label_name),
            singleLine = true,
        )
    }
}
