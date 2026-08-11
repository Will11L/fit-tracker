package com.example.sportapp.designsystem.theme

import androidx.compose.ui.graphics.Color

// ====================================================================
// PRIMITIVES BRUTES — palette de couleurs reutilisables.
// Ces valeurs alimentent AppColors.kt (tokens semantiques dark/light)
// ainsi que quelques composants legacy qui n'ont pas encore ete migres
// vers appColors.*. Modifier ici = impacte tous les callsites.
// ====================================================================

// Palette Material3 par defaut (generee par Android Studio) — consommee
// uniquement par Theme.kt pour MaterialTheme.colorScheme (defaults widgets
// Material non personnalises).
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Background "brand" injecte dans MaterialTheme.colorScheme.background / onBackground
val BrandBackgroundDark = Color(0xFF030506)
val BrandOnBackgroundDark = Color(0xFF4D9CFD)
val BrandBackgroundLight = Color(0xFFFFFFFF)
val BrandOnBackgroundLight = Color(0xFF000000)

// Bleus principaux de l'app — fondation visuelle du theme dark.
// firstBlue = bleu moyen-fonce (AppColors.selectedFill, dividerStrong)
// secondBlue = bleu plus sombre (AppColors.bgBottomNav)
// thirdBlue = quasi-noir bleute (AppColors.bgRecessed)
val firstBlue = Color(0xFF153A6B)
val secondBlue = Color(0xFF0F1C26)
val thirdBlue = Color(0xFF091216)

// Variantes claires/grises bleutees
val lightGrayBlue = Color(0xFF7B9DD0) // charts/toggles (StatsScreen, ChartTypeToggle, DrawerContent)
val GrayBlue = Color(0xFF5E78A0)      // AppColors.textSecondary + divider en dark
val boxBlue = Color(0xFF1E2A3C)       // AppColors.bgSurface + bgButton en dark

// Couleurs accents (charts/badges/tags + AppColors snackbar/priority)
val lightGreen = Color(0xFF00D572)
val mediumGreen = Color(0xFF008444)  // AppColors.snackbarSuccess + priorityLow
val yellowMedium = Color(0xFFC4A000)
val orangeMedium = Color(0xFFC4841F) // AppColors.snackbarWarning + priorityMedium
val darkOrange = Color(0xFF9D5300)
val redMedium = Color(0xFFB3403E)    // AppColors.snackbarError + priorityHigh
val redDark = Color(0xFF7A2E2C)
val blueMedium = Color(0xFF245682)   // weightColor / accent direct (charts, boutons)
val turquoise = Color(0xFF15BCAB)    // miroir du token web --c-turquoise (accent Sante : distance & calories)

// Gris neutres
val darkGray = Color(0xFF13151A)

val lightBlue = Color(0xFF4FC3F7)          // AppColors.accentText (drawer titles, liens)
val ButtonPrimaryColor = Color(0xFF2377CA) // AppColors.primaryAction (dark + light)

val lightPurple = Color(0xFF6C2AE7)
val brightPurple = Color(0xFF8A40EF)       // miroir du token web --c-bright-purple — violet lisible sur fond sombre (stress · macro protéines), mi-chemin lightPurple ↔ #A855F7
val mediumPurple = Color(0xFF30166A)       // volumeColor (charts)

val blueBackground = Color(0xFF101720)     // AppColors.bgScreen en dark

// ====================================================================
// ALIAS LEGACY encore utilises directement par quelques composants.
// A migrer vers appColors.* a terme — mais sans rush.
// ====================================================================

val dividerColor = GrayBlue                  // EditExerciseDialog, ExerciseCard, MuscleCard, RoutineTaskRow

// Drawer
val drawerContainerColor = blueBackground    // DrawerContent.kt

// TextField (OutlinedTextField defaults : CustomTextField + variants)
val focusedTextColor = GrayBlue              // label focused
val unfocusedTextColor = Color.White         // label sans focus
val cursorColor = Color.White                // curseur

// Screens specifiques (1-2 callsites chacun)
val SessionTabExerciseBackground = boxBlue           // AdminUsersScreen, TasksHeader
val WeekViewProgramCardBackground = thirdBlue        // ExerciseCard
val SessionExerciseScreenBackground = blueBackground // MuscleOptionsBottomSheet
val UiShowcaseCardBackground = Color(0xFF141E29)     // UiShowcaseScreen _SectionCard (milieu thirdBlue/boxBlue)
