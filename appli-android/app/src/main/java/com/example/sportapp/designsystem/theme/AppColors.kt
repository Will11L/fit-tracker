package com.example.sportapp.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de couleur sémantiques de l'app (rôle -> couleur), déclinés en 2 thèmes.
 * Les primitives brutes restent dans Color.kt. La palette charts fixe vit dans ChartColors.kt.
 * Se lit dans un composable via la propriété `appColors` : `appColors.bgSurface`.
 */
data class AppColors(
    // — Fonds —
    val bgScreen: Color,        // fond global d'un écran (Scaffold / Box racine de chaque screen)
    val bgSurface: Color,       // fond des cartes/boîtes posées sur bgScreen (ExerciseCard, bottom sheet container, dialogs, etc.)
    val bgRecessed: Color,      // fond "enfoncé" plus sombre que bgScreen (rows alternées, sous-blocs, drawer sections)
    val bgBottomNav: Color,     // fond de la BottomNavBar ET des onglets non-sélectionnés (DualTabMenu, TasksHeader)
    val bgButton: Color,        // fond des boutons d'action carrés (ActionIcon, ActionIconButton, ActionTextButton, DateNavBar)
    // — Sélection / action —
    val selectedFill: Color,    // fond d'un élément sélectionné (onglet actif, item BottomNav actif, ToggleButton "on")
    val primaryAction: Color,   // couleur d'accent principale = boutons primaires (Validate/Save/CTA), FAB, switches actifs, progress bars
    // — Texte / icônes —
    val textPrimary: Color,     // texte principal lisible (titres, body, valeurs saisies, labels de formulaire)
    val textSecondary: Color,   // texte secondaire (sous-titres, headers de tableau, captions discrètes)
    val textTertiary: Color,    // texte tertiaire / désactivé (placeholders, hints, onglets non-sélectionnés, tutoriels)
    val textOnSelected: Color,  // texte/icône posé sur selectedFill (doit contraster avec le fond sélectionné)
    val accentText: Color,      // texte d'accent bleu clair (titres drawer, liens, indicateurs spéciaux)
    // — Dividers —
    val divider: Color,         // ligne de séparation discrète (Divider HorizontalDivider standard)
    val dividerStrong: Color,   // ligne de séparation appuyée (séparateur de section, contour de carte)
    // — Surfaces teintées : priorité —
    val priorityHigh: Color,    // fond des badges/rows priorité haute (PriorityPicker, GoalsTab rows HIGH)
    val priorityMedium: Color,  // fond priorité moyenne (MEDIUM)
    val priorityLow: Color,     // fond priorité basse (LOW)
    // — Surfaces teintées : task type —
    val taskRowGreenBg: Color,        // fond des rows RoutineTask de type "vert" (ex. routine quotidienne validée)
    val taskRowGreenNameBox: Color,   // sous-box du nom dans une row verte
    val taskRowOrangeBg: Color,       // fond des rows RoutineTask de type "orange" (ex. tâche hebdo)
    val taskRowOrangeNameBox: Color,  // sous-box du nom dans une row orange
    // — Snackbar (status semantique : icon + border + action button) —
    val snackbarSuccess: Color, // vert (icon + bordure + bouton d'action) sur snackbar de type SUCCESS
    val snackbarWarning: Color, // orange (icon + bordure + bouton d'action) sur snackbar de type WARNING
    val snackbarError: Color,   // rouge (icon + bordure + bouton d'action) sur snackbar de type ERROR
)

/** Thème sombre — valeurs actuelles de l'app (référence les primitives de Color.kt). */
val appColorsDark = AppColors(
    bgScreen = blueBackground,             // fond global d'un écran (Scaffold / Box racine de chaque screen)
    bgSurface = boxBlue,                   // fond des cartes/boîtes posées sur bgScreen (ExerciseCard, bottom sheet container, dialogs)
    bgRecessed = thirdBlue,                // fond "enfoncé" plus sombre que bgScreen (rows alternées, sous-blocs, drawer sections)
    bgBottomNav = secondBlue,              // fond de la BottomNavBar ET des onglets non-sélectionnés (DualTabMenu, TasksHeader)
    bgButton = boxBlue,                    // fond des boutons d'action carrés (ActionIcon, ActionIconButton, DateNavBar)
    selectedFill = firstBlue,              // fond d'un élément sélectionné (onglet actif, item BottomNav actif, ToggleButton "on")
    primaryAction = ButtonPrimaryColor,    // couleur d'accent principale = boutons primaires (Validate/Save/CTA), FAB, switches actifs, progress bars
    textPrimary = Color.White,             // texte principal lisible (titres, body, valeurs saisies, labels de formulaire)
    textSecondary = GrayBlue,              // texte secondaire (sous-titres, headers de tableau, captions discrètes)
    textTertiary = Color.LightGray,        // texte tertiaire / désactivé (placeholders, hints, onglets non-sélectionnés, tutoriels)
    textOnSelected = Color.White,          // texte/icône posé sur selectedFill (doit contraster avec le fond sélectionné)
    accentText = lightBlue,                // texte d'accent bleu clair (titres drawer, liens, indicateurs spéciaux)
    divider = GrayBlue,                    // ligne de séparation discrète (Divider HorizontalDivider standard)
    dividerStrong = firstBlue,             // ligne de séparation appuyée (séparateur de section, contour de carte)
    priorityHigh = redMedium,                // fond des badges/rows priorité haute (PriorityPicker, GoalsTab rows HIGH)
    priorityMedium = orangeMedium,           // fond priorité moyenne (MEDIUM)
    priorityLow = mediumGreen,             // fond priorité basse (LOW)
    taskRowGreenBg = Color(0xFF071F14),    // fond des rows RoutineTask de type "vert" (ex. routine quotidienne validée)
    taskRowGreenNameBox = Color(0xFF1A4D33), // sous-box du nom dans une row verte
    taskRowOrangeBg = Color(0xFF1F1408),   // fond des rows RoutineTask de type "orange" (ex. tâche hebdo)
    taskRowOrangeNameBox = Color(0xFF52320E), // sous-box du nom dans une row orange
    snackbarSuccess = mediumGreen,         // accent vert sur snackbar SUCCESS
    snackbarWarning = orangeMedium,        // accent orange sur snackbar WARNING
    snackbarError = redMedium,             // accent rouge sur snackbar ERROR
)

/** Thème clair — garde la teinte bleu nuit, luminance inversée, saturation réduite. */
val appColorsLight = AppColors(
    bgScreen = Color(0xFFFFFFFF),          // fond global d'un écran (Scaffold / Box racine de chaque screen)
    bgSurface = Color(0xFFFFFFFF),         // fond des cartes/boîtes posées sur bgScreen (ExerciseCard, bottom sheet container, dialogs)
    bgRecessed = Color(0xFFE1E7EF),        // fond "enfoncé" plus sombre que bgScreen (rows alternées, sous-blocs, drawer sections)
    bgBottomNav = Color(0xFFF2F5F9),       // fond de la BottomNavBar ET des onglets non-sélectionnés (DualTabMenu, TasksHeader)
    bgButton = Color(0xFFFFFFFF),          // fond des boutons d'action carrés (ActionIcon, ActionIconButton, DateNavBar)
    selectedFill = Color(0xFFD3E4F7),      // fond d'un élément sélectionné (onglet actif, item BottomNav actif, ToggleButton "on")
    primaryAction = ButtonPrimaryColor,    // inchangé : lisible sur les 2 thèmes — boutons primaires, FAB, switches actifs, progress bars
    textPrimary = Color(0xFF1A2330),       // texte principal lisible (titres, body, valeurs saisies, labels de formulaire)
    textSecondary = Color(0xFF5A6472),     // texte secondaire (sous-titres, headers de tableau, captions discrètes)
    textTertiary = Color(0xFF9AA3B0),      // texte tertiaire / désactivé (placeholders, hints, onglets non-sélectionnés, tutoriels)
    textOnSelected = Color(0xFF1A2330),    // texte/icône posé sur selectedFill (doit contraster avec le fond sélectionné)
    accentText = Color(0xFF1F86C4),        // texte d'accent bleu clair (titres drawer, liens, indicateurs spéciaux)
    divider = Color(0xFFD0D8E2),           // ligne de séparation discrète (Divider HorizontalDivider standard)
    dividerStrong = Color(0xFFC5D2E2),     // ligne de séparation appuyée (séparateur de section, contour de carte)
    priorityHigh = Color(0xFFF3CFD0),      // fond des badges/rows priorité haute (PriorityPicker, GoalsTab rows HIGH)
    priorityMedium = Color(0xFFF0DBB8),    // fond priorité moyenne (MEDIUM)
    priorityLow = Color(0xFFC7E3D5),       // fond priorité basse (LOW)
    taskRowGreenBg = Color(0xFFE3F2EA),    // fond des rows RoutineTask de type "vert" (ex. routine quotidienne validée)
    taskRowGreenNameBox = Color(0xFFBFE0CD), // sous-box du nom dans une row verte
    taskRowOrangeBg = Color(0xFFF5EBDD),   // fond des rows RoutineTask de type "orange" (ex. tâche hebdo)
    taskRowOrangeNameBox = Color(0xFFE6CFA8), // sous-box du nom dans une row orange
    snackbarSuccess = Color(0xFF1F7A47),   // accent vert sur snackbar SUCCESS (light)
    snackbarWarning = Color(0xFFB57E1A),   // accent orange sur snackbar WARNING (light)
    snackbarError = Color(0xFF9A2D24),     // accent rouge sur snackbar ERROR (light)
)

/** Default = dark : tant qu'aucun callsite n'utilise les tokens, l'app reste identique. */
val LocalAppColors = staticCompositionLocalOf { appColorsDark }

/** Raccourci de lecture des tokens dans un composable. */
val appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
