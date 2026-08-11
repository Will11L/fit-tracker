package com.example.sportapp.feature.onboarding.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.domain.OnboardingStep
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun OnboardingHeader(step: OnboardingStep, title: String) {
    val progress = (step.index + 1).toFloat() / step.total.toFloat()
    Column(modifier = Modifier.fillMaxWidth().background(appColors.bgRecessed)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = appColors.primaryAction,
            trackColor = appColors.primaryAction.copy(alpha = 0.2f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_header_step, step.index + 1, step.total),
            color = appColors.primaryAction,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }
}
