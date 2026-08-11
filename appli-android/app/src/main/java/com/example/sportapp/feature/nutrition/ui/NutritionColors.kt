package com.example.sportapp.feature.nutrition.ui

import androidx.compose.ui.graphics.Color
import com.example.sportapp.designsystem.theme.ButtonPrimaryColor
import com.example.sportapp.designsystem.theme.brightPurple
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.MicroFamily

/**
 * SOURCE UNIQUE de la couleur des macros / familles de micros — port 1:1 des
 * tokens `--macro-*` / `--micro-*` du web (`_colors.scss`), eux-mêmes adossés aux
 * primitives de la palette app (jamais de M3 brut, cf. mémoire widget_app_style).
 * Une couleur dédiée par macro, façon palette par zone des Stats sport.
 *
 * 2 valeurs n'ont pas de primitive Color.kt dédiée et sont définies ici comme
 * port direct du token web : turquoise glucides (`--c-turquoise`) et jaune
 * vitamines (`--micro-vitamin`).
 */
private val macroTurquoise = Color(0xFF15BCAB)   // --c-turquoise (glucides)
private val microVitaminYellow = Color(0xFFE3B505) // --micro-vitamin

/**
 * Sucres (plafond OMS du bandeau Journal) : teinte dédiée VOLONTAIREMENT hors MacroKey —
 * les sucres sont une limite (≤), pas une cible « à remplir », et ne doivent pas entrer
 * dans les boucles macros (anneaux du calendrier, stats, radar). Port du token web
 * `--macro-sugar` (framboise moyen — itéré user 2026-07-14 : #e879c7 trop clair,
 * #c2185b trop sombre sur thirdBlue).
 */
val sugarColor = Color(0xFFD6489B)               // --macro-sugar

fun macroColor(key: MacroKey): Color = when (key) {
    MacroKey.KCAL -> ButtonPrimaryColor      // --macro-kcal
    MacroKey.PROTEIN -> brightPurple          // --macro-protein
    MacroKey.CARBS -> macroTurquoise         // --macro-carbs
    MacroKey.FAT -> orangeMedium             // --macro-fat
    MacroKey.FIBER -> mediumGreen            // --macro-fiber
}

fun microColor(family: MicroFamily): Color = when (family) {
    MicroFamily.MINERAL -> redMedium         // --micro-mineral
    MicroFamily.VITAMIN -> microVitaminYellow // --micro-vitamin
}
