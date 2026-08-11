package com.example.sportapp.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ====================================================================
// SCHEMA MATERIAL3 — alimente MaterialTheme.colorScheme.
// Ces couleurs sont consommees par les composants Material standards
// (Button, FAB, Card, TextField default colors, etc.) lorsqu'on ne
// surcharge PAS explicitement leur couleur. La plupart de l'app
// surcharge via appColors.*, donc ce schema sert surtout aux defaults
// des widgets Material3 non personnalises.
// ====================================================================

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,           // couleur primaire Material3 (boutons par defaut, etc.)
    secondary = PurpleGrey80,     // couleur secondaire (chips, badges Material par defaut)
    tertiary = Pink80,            // couleur tertiaire (peu utilise)

    background = BrandBackgroundDark,   // fond global Material (rarement lu, on utilise plutot appColors.bgScreen)
    onBackground = BrandOnBackgroundDark, // texte/icones sur background

    /* Other default colors to override
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
     */
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,           // pendant clair de Purple80
    secondary = PurpleGrey40,
    tertiary = Pink40,

    background = BrandBackgroundLight,
    onBackground = BrandOnBackgroundLight,
    /* Other default colors to override
    background = Color(0xFF00FF00),
    onBackground = Color(0xFF00FF00),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Theme racine de l'app. Wrappe le contenu dans :
 *  1) MaterialTheme (defaults Material3 ci-dessus)
 *  2) CompositionLocalProvider de LocalAppColors → expose `appColors.*` partout
 *
 * Le toggle dark/light s'effectue via `darkTheme` (par defaut = setting systeme).
 * `dynamicColor = false` = on ignore le Material You Android 12+ (palette imposee
 * par l'app, pas par l'utilisateur).
 */
@Composable
fun SportAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Injection de la palette AppColors (dark ou light) dans le CompositionLocal,
    // → `appColors.bgScreen` etc. seront resolus automatiquement dans tout le sous-arbre.
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) appColorsDark else appColorsLight
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}