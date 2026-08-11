package com.example.sportapp.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ====================================================================
// TYPOGRAPHIE — alimente MaterialTheme.typography (consommee par les
// composants Material3 quand on ne surcharge pas explicitement le style).
// En pratique, la majorite des Text() de l'app surcharge fontSize/fontWeight
// inline, donc ce bloc impacte surtout les widgets Material par defaut
// (Button label, TopAppBar title, etc.).
// ====================================================================

val Typography = Typography(
    // bodyLarge = texte par defaut des composants Material (corps de Card, Dialog, etc.)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(   // titre principal (TopAppBar, Dialog title)
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(   // texte petits labels (chips, badges)
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)