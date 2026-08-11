package com.example.sportapp.feature.onboarding.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors
import java.time.LocalDate

/**
 * Widget read-only inspiré du `CustomTextField` (fond appColors.bgRecessed + label
 * appColors.primaryAction en haut), trailing icon calendrier qui trigger
 * `onClick` (= ouvre le BirthDatePickerDialog).
 *
 * Format affichage : MM/DD/YYYY (US). Stockage interne reste ISO YYYY-MM-DD.
 * `isoValue` null -> placeholder "MM/DD/YYYY" en gris.
 */
@Composable
fun BirthDateField(
    isoValue: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayValue = isoValue?.let { iso ->
        runCatching {
            val d = LocalDate.parse(iso)
            "%02d/%02d/%d".format(d.monthValue, d.dayOfMonth, d.year)
        }.getOrNull()
    }
    val hasValue = displayValue != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(appColors.bgRecessed)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_bio_card_birthdate),
            color = appColors.primaryAction,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayValue ?: stringResource(R.string.onboarding_bio_birthdate_placeholder),
                color = if (hasValue) appColors.textPrimary else appColors.textTertiary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                iconRes = R.drawable.ic_calendar_month,
                onClick = onClick,
            )
        }
    }
}
