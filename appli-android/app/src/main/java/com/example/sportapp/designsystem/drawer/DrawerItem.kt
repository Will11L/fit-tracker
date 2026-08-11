package com.example.sportapp.designsystem.drawer

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class DrawerItem(
    val title: String,
    @DrawableRes val iconRes: Int,
    // Teinte de l'icône (additif) : null = teinte uniforme du drawer (textPrimary). Surchargeable pour
    // marquer un domaine (ex. Santé en vert), miroir de l'accent de section du drawer web.
    val iconTint: Color? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit
)
