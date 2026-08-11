package com.example.sportapp.feature.onboarding.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

/**
 * Footer commun des 3 steps :
 * - Back : box style ActionIconButton appColors.bgSurface, texte sans icon.
 *   Caché sur l'étape Welcome (rien à backer).
 * - Skip : TextButton plain (subtil, gris).
 * - Next/Finish : box style ActionIconButton appColors.primaryAction, texte sans icon.
 */
@Composable
fun OnboardingFooter(
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    nextEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onBack != null) {
            FooterTextOnlyButton(
                text = stringResource(R.string.common_back),
                backgroundColor = appColors.bgSurface,
                textColor = appColors.textPrimary,
                onClick = onBack,
            )
        } else {
            Spacer(modifier = Modifier.width(80.dp))
        }
        TextButton(onClick = onSkip) {
            Text(
                text = stringResource(R.string.onboarding_footer_skip),
                color = appColors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        FooterTextOnlyButton(
            text = nextLabel,
            backgroundColor = appColors.primaryAction,
            clickable = nextEnabled,
            onClick = onNext,
        )
    }
}

/**
 * Mini-button text-only style ActionIconButton (box clip + background +
 * clickable). Local au footer onboarding -- pas dans common_components/
 * tant qu'il n'a qu'un seul use case.
 */
@Composable
private fun FooterTextOnlyButton(
    text: String,
    backgroundColor: Color,
    textColor: Color = appColors.textPrimary,
    clickable: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor, shape = MaterialTheme.shapes.small)
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
