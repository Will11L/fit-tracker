package com.example.sportapp.feature.onboarding.data

/**
 * Helpers de formatage poids + longueurs selon les unités préférées de l'user.
 *
 * Stockage interne : poids en KG, longueurs en CM (canonique). L'affichage
 * convertit selon WeightUnit / LengthUnit choisis dans onboarding/Settings.
 *
 * Conversion :
 * - 1 kg = 2.20462 lbs (lbs = pound)
 * - 1 cm = 0.3937 inches (1 inch = 2.54 cm)
 */

private const val KG_TO_LBS = 2.20462
private const val CM_TO_INCHES = 0.3937

/** Format poids "X kg" ou "Y lbs" selon `unit`. Arrondi à 1 décimale. */
fun formatWeight(kg: Float, unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> "%.1f kg".format(kg)
    WeightUnit.LBS -> "%.1f lbs".format(kg * KG_TO_LBS)
}

/** Format poids sans suffixe, juste la valeur convertie (pour SetRow column compact).
 *  Affiche entier si pile, sinon 1 décimale. */
fun formatWeightValue(kg: Float, unit: WeightUnit): String {
    val v: Float = if (unit == WeightUnit.LBS) (kg * KG_TO_LBS).toFloat() else kg
    return if (v == v.toInt().toFloat()) "${v.toInt()}" else "%.1f".format(v)
}

/** Format volume avec compaction k/M (pour Stats Y axis + total volumes).
 *  En LBS : convertit le volume kg cumulé en lbs avant compaction. */
fun formatVolume(kg: Float, unit: WeightUnit): String {
    val value = if (unit == WeightUnit.LBS) (kg * KG_TO_LBS).toFloat() else kg
    if (value <= 0f) return "0"
    return when {
        value >= 1_000_000f -> "%.1fM".format(value / 1_000_000f)
        value >= 10_000f -> "%.0fk".format(value / 1_000f)
        value >= 1_000f -> "%.1fk".format(value / 1_000f)
        else -> "${value.toInt()}"
    }
}

/** Suffixe court d'unité ("kg" / "lbs") pour axis labels et chart legends. */
fun weightLabel(unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> "kg"
    WeightUnit.LBS -> "lbs"
}

/** Convertit kg vers lbs (pour input/edit). */
fun kgToLbs(kg: Float): Float = (kg * KG_TO_LBS).toFloat()

/** Convertit lbs vers kg (pour input/edit en lbs sauvegardé en kg). */
fun lbsToKg(lbs: Float): Float = (lbs / KG_TO_LBS).toFloat()

/** Format longueur "X cm" ou "Y in" selon `unit`. Arrondi à 1 décimale. */
fun formatLength(cm: Float, unit: LengthUnit): String = when (unit) {
    LengthUnit.CM -> "%.1f cm".format(cm)
    LengthUnit.INCHES -> "%.1f in".format(cm * CM_TO_INCHES)
}
