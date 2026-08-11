package com.example.sportapp.designsystem.common_components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
fun ForceSheetSystemBars(
    lightStatusBars: Boolean = false,
    lightNavBars: Boolean = false,
    disableNavBarContrastEnforced: Boolean = true
) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return

    LaunchedEffect(disableNavBarContrastEnforced) {
        if (disableNavBarContrastEnforced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = lightStatusBars
        controller.isAppearanceLightNavigationBars = lightNavBars
    }
}
