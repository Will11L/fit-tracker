package com.example.sportapp.core.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Genere une palette de [count] couleurs derivees de [zoneColor] en faisant
 * varier conjointement :
 *  - la luminosite HSL : range +/- 38% autour de 50% a spread=1, reduit
 *    proportionnellement pour spread<1.
 *  - la teinte HSL : +/- 10deg a spread=1, reduit proportionnellement.
 *  - la saturation : desaturation progressive pour les nuances claires
 *    (sombre = sat plein, clair = sat * (1 - 0.5*spread)).
 *
 * @param spread facteur de dispersion (0..1, default 1.0). Plus spread est
 *   bas, plus les nuances restent proches de la couleur de zone de
 *   reference. Stats utilise spread=1.0 (default) pour distinguer 35
 *   muscles ; Goals utilise spread=0.4 (1 semaine = peu d'elements par
 *   zone, on prefere des nuances proches du vert/rouge/bleu de reference
 *   plutot que des derives gris-clair). User feedback runtime 2026-05-09 :
 *   "quads devient presque vert gris clair, trop eloigne du vert initial".
 *
 * Utilise par StatsScreen (spread=1.0) et GoalsTabContent (spread=0.4) pour
 * generer une palette stable par zone (17 muscle_groups et 35 muscles
 * precis - refactor 3-niveaux 2026-05-08).
 */
fun paletteForZone(zoneColor: Color, count: Int, spread: Float = 1.0f): List<Color> {
    if (count <= 0) return emptyList()
    val argb = zoneColor.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    val baseHue = hsl[0]
    val baseSat = hsl[1]
    val s = spread.coerceIn(0f, 1f)
    return (0 until count).map { i ->
        val t = if (count == 1) 0.5f else i.toFloat() / (count - 1)
        // Centered around 0.5, range scaled by spread.
        val lightness = 0.5f + (t - 0.5f) * 0.76f * s  // s=1 : [0.12..0.88], s=0.4 : [0.348..0.652]
        val hueShift = (t - 0.5f) * 20f * s            // s=1 : +/-10deg, s=0.4 : +/-4deg
        val hue = (baseHue + hueShift + 360f) % 360f
        // Saturation desaturee progressivement pour les nuances claires :
        // sombre (t=0) = sat plein ; clair (t=1) = sat * (1 - 0.5*s).
        // s=1 : sat * 0.5 ; s=0.4 : sat * 0.8 (nuances plus saturees, plus
        // proches de la couleur zone de reference).
        val saturation = baseSat * (1f - 0.5f * t * s)
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
    }
}
