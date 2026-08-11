package com.example.sportapp.wear

/**
 * Encodage du canal live montre → téléphone. **Miroir** du parseur côté phone
 * (`com.example.sportapp.feature.health.wear.HealthLivePayload`) : pas de module
 * partagé, donc garder ce format strictement synchrone avec l'app téléphone.
 * Format : `steps=<long>;hr=<int|->;dist=<int|->;cal=<int|->;ts=<epochMillis>` (`-` = absent).
 * `dist` (m) et `cal` (kcal totales) = agrégats quotidiens `DISTANCE_DAILY`/`CALORIES_DAILY`,
 * persistés côté téléphone (décision Option B) ; steps/hr restent affichage-only.
 */
object WearLivePayload {
    const val PATH = "/health/live"

    /** Path de la requête pull reçue du téléphone (miroir de
     *  `HealthLivePayload.REQUEST_PATH`) → la montre répond sur [PATH]. */
    const val REQUEST_PATH = "/health/live/request"

    fun encode(steps: Long, hr: Int?, distanceM: Int?, caloriesKcal: Int?, timestampMillis: Long): String =
        "steps=$steps;hr=${hr ?: "-"};dist=${distanceM ?: "-"};cal=${caloriesKcal ?: "-"};ts=$timestampMillis"
}
