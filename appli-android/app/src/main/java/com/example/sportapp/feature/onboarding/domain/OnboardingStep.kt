package com.example.sportapp.feature.onboarding.domain

/**
 * Étapes du flow B1 onboarding (4 écrans guidés post-signup).
 * Ordre : WELCOME -> BIO -> PREFERENCES -> PERMISSIONS -> done.
 *
 * UPPER_CASE conforme à la politique 11 (états cross-stack).
 *
 * NOTE 2026-05-11 : décision user — les anciens steps Muscles + Exercises
 * sont SUPPRIMÉS. On laisse le starter pack pré-seed (V8.4) tel quel,
 * l'user désélectionne ce qu'il ne veut pas plus tard via les écrans
 * Muscles/Exercises classiques. Pas de friction onboarding.
 *
 * BIO ajouté 2026-05-11 : birthDate / sex / heightCm / weightKg pour
 * alimenter Nutrition future (BMR/TDEE) + personnalisation. Skippable.
 */
enum class OnboardingStep(val index: Int, val total: Int = 4) {
    WELCOME(0),
    PREFERENCES(1),   // PREFERENCES avant BIO -- l'user choisit ses units (kg/lbs, cm/inches)
    BIO(2),           // ...avant de saisir height/weight (labels adaptés).
    PERMISSIONS(3),
    ;

    fun next(): OnboardingStep? = entries.getOrNull(index + 1)
    fun previous(): OnboardingStep? = entries.getOrNull(index - 1)

    companion object {
        fun fromIndex(i: Int): OnboardingStep = entries.getOrElse(i.coerceIn(0, entries.size - 1)) { WELCOME }
    }
}
