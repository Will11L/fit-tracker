package com.example.sportapp.feature.health.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.ButtonPrimaryColor
import com.example.sportapp.designsystem.theme.lightBlue
import com.example.sportapp.designsystem.theme.lightGreen
import com.example.sportapp.designsystem.theme.brightPurple
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.turquoise
import com.example.sportapp.designsystem.theme.yellowMedium

/**
 * Source unique des sections du hub Santé (miroir `HEALTH_SECTIONS` web) : icône,
 * titre et couleur d'identité — l'ordre EST l'ordre des pages du pager. Partagée par
 * la barre d'onglets du hub ([HealthDashboardScreen]) et les items de la section
 * Santé du drawer (1 item par section, cf. [HealthNavRequest]).
 */
data class HealthSection(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    val color: Color,
)

val HEALTH_SECTIONS: List<HealthSection> = listOf(
    HealthSection(R.drawable.ic_exercise, R.string.health_dash_steps_title, lightGreen),
    HealthSection(R.drawable.ic_rounded_ecg_heart, R.string.health_dash_cardio_title, orangeMedium),
    HealthSection(R.drawable.ic_rounded_bedtime, R.string.health_dash_sleep_title, ButtonPrimaryColor),
    HealthSection(R.drawable.ic_rounded_spo2, R.string.health_dash_spo2_title, lightBlue),
    HealthSection(R.drawable.ic_rounded_local_fire, R.string.health_dash_energy_title, turquoise),
    HealthSection(R.drawable.ic_rounded_monitor_weight, R.string.health_dash_weight_title, yellowMedium),
    HealthSection(R.drawable.ic_rounded_psychology, R.string.health_dash_stress_title, brightPurple),
)
