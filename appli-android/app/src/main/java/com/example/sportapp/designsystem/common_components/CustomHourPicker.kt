package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

/**
 * Sélecteur d'heure « HH:MM » FACULTATIF — port du `custom-hour-picker` web :
 * label, aperçu (« HH:MM » accent, ou « Aucune heure » tertiaire tant que vide),
 * roues heures 0-23 / minutes 0-59 (position d'affichage 12:00 quand vide —
 * toucher une roue pose la valeur), bouton « Effacer l'heure » quand posée.
 * `value` = "" quand aucune heure choisie.
 */
@Composable
fun CustomHourPicker(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").find(value.trim())
    val isSet = match != null
    val hours = (match?.groupValues?.get(1)?.toIntOrNull() ?: 12).coerceIn(0, 23)
    val minutes = (match?.groupValues?.get(2)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    fun pad2(v: Int) = v.toString().padStart(2, '0')

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // « Effacer l'heure » sur la LIGNE DU LABEL (hauteur constante : le dialog ne
        // s'agrandit pas quand une heure est posée — retour user 2026-07-14).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = appColors.textTertiary, fontSize = 12.sp)
            if (isSet) {
                Text(
                    text = stringResource(R.string.hour_picker_clear),
                    color = appColors.accentText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onValueChange("") }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        // Aperçu de la valeur courante (accent quand posée, tertiaire sinon).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(appColors.bgRecessed)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isSet) "${pad2(hours)}:${pad2(minutes)}" else stringResource(R.string.hour_picker_none),
                color = if (isSet) appColors.accentText else appColors.textTertiary,
                fontSize = if (isSet) 18.sp else 14.sp,
                fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Largeur EXPLICITE : le WheelPicker n'a pas de largeur intrinsèque (sans elle,
            // la 1re roue prenait toute la ligne et masquait les minutes).
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.hour_picker_hours),
                    color = appColors.textTertiary,
                    fontSize = 11.sp,
                )
                WheelPicker(
                    range = 0..23,
                    selected = hours,
                    onSelected = { onValueChange("${pad2(it)}:${pad2(minutes)}") },
                    modifier = Modifier.width(88.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.hour_picker_minutes),
                    color = appColors.textTertiary,
                    fontSize = 11.sp,
                )
                WheelPicker(
                    range = 0..59,
                    selected = minutes,
                    onSelected = { onValueChange("${pad2(hours)}:${pad2(it)}") },
                    modifier = Modifier.width(88.dp),
                )
            }
        }
    }
}
