package com.example.sportapp.feature.health.wear

/**
 * Payload du canal live montre → téléphone (Wearable Data Layer, path [MESSAGE_PATH]).
 * Format texte compact `steps=<long>;hr=<int|->;dist=<int|->;cal=<int|->;ts=<epochMillis>`
 * (`-` = valeur absente). Le module `:wear` réplique l'encodage (pas de module partagé) —
 * garder les deux côtés synchrones.
 *
 * `distanceM` (m) et `caloriesKcal` (kcal TOTALES, BMR inclus) viennent des agrégats quotidiens
 * de la montre (Health Services `DISTANCE_DAILY` / `CALORIES_DAILY`) → **persistés** côté téléphone
 * (`health_metrics`, cf. [com.example.sportapp.feature.health.data.HealthImporter]) : Samsung n'écrit
 * rien dans HC pour ces types → aucun risque de double-comptage (décision Option B 2026-07-06). Les
 * pas/FC restent affichage-only (HC est leur source persistée). `decode` tolère l'absence de
 * `dist`/`cal` (rétro-compat avec les anciens messages steps/hr seuls).
 */
data class HealthLivePayload(
    val steps: Long,
    val hr: Int?,               // bpm, null si pas encore de mesure
    val timestampMillis: Long,
    val distanceM: Int? = null,       // distance du jour (m), null si non exposée par la montre
    val caloriesKcal: Int? = null,    // calories TOTALES du jour (kcal, BMR inclus)
) {
    fun encode(): String =
        "steps=$steps;hr=${hr ?: "-"};dist=${distanceM ?: "-"};cal=${caloriesKcal ?: "-"};ts=$timestampMillis"

    companion object {
        const val MESSAGE_PATH = "/health/live"

        /** Path de la requête pull téléphone → montre (message vide, réveille la
         *  montre même app fermée) ; la montre répond sur [MESSAGE_PATH]. */
        const val REQUEST_PATH = "/health/live/request"

        /** Parse le payload ; `null` si `steps`/`ts` absents ou invalides. `dist`/`cal` optionnels. */
        fun decode(raw: String): HealthLivePayload? {
            val map = raw.split(";").mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }.toMap()
            val steps = map["steps"]?.toLongOrNull() ?: return null
            val ts = map["ts"]?.toLongOrNull() ?: return null
            val hr = map["hr"]?.let { if (it == "-") null else it.toIntOrNull() }
            val dist = map["dist"]?.let { if (it == "-") null else it.toIntOrNull() }
            val cal = map["cal"]?.let { if (it == "-") null else it.toIntOrNull() }
            return HealthLivePayload(
                steps = steps,
                hr = hr,
                timestampMillis = ts,
                distanceM = dist,
                caloriesKcal = cal,
            )
        }
    }
}
