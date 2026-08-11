package com.example.sportapp.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.CustomRadioButton
import com.example.sportapp.designsystem.common_components.CustomSwitch
import com.example.sportapp.designsystem.theme.appColors

/**
 * Card style app : titre appColors.primaryAction + content + bg appColors.bgRecessed.
 * Composant partage entre tous les SettingsXxxScreen.
 */
@Composable
fun SettingsSectionCard(title: String, content: @Composable () -> Unit) {
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

/** Row toggle (titre + description + Switch). */
@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = appColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary,
            )
        }
        CustomSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Liste de radios verticales pour selectionner une valeur dans un set. */
@Composable
fun <T> SettingsRadioOptions(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).selectable(
                    selected = (value == selected),
                    onClick = { onSelected(value) },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomRadioButton(selected = (value == selected), onClick = null)
                Text(
                    text = label,
                    color = appColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Row d'entree de categorie (utilise dans la liste principale SettingsScreen).
 * Affiche titre + description + chevron a droite, tap -> navigue vers sous-ecran.
 */
@Composable
fun SettingsCategoryRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = appColors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary,
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_keyboard_arrow_right),
            contentDescription = null,
            tint = appColors.textTertiary,
        )
    }
}

/**
 * Header standard d'un sous-ecran Settings : back button a gauche + titre centre.
 */
@Composable
fun SettingsSubScreenHeader(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(appColors.bgRecessed),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_left_alt),
            contentDescription = "Back",
            tint = appColors.textPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .clickable(onClick = onBack),
        )
        Text(
            text = title,
            color = appColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
