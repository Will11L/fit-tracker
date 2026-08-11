package com.example.sportapp.core.stats

/**
 * Volume agrege d'une zone musculaire sur la periode (deja somme sur les
 * buckets), en KG canonique. La couleur de l'axe et la conversion d'unite
 * d'affichage sont appliquees cote UI (pas ici) pour garder le builder pur et
 * testable sans dependance Compose.
 *
 *  - [zone]   : nom de zone EN canonique (Chest / Back / ... — cf. [Zones.ALL]).
 *  - [volume] : Σ poids·reps·coef sur la periode, en KG.
 */
data class ZoneVolumeDatum(
    val zone: String,
    val volume: Float,
)

/**
 * Construit les donnees du radar « equilibre / symetrie d'entrainement » a
 * partir du volume par zone. Port 1:1 de `appli-web/src/app/features/stats/
 * zone-radar-data.ts` :
 *  - ordre d'entree conserve (les appelants passent toujours les 6 zones
 *    canoniques dans l'ordre [Zones.ALL] → hexagone stable, lecture symetrie) ;
 *  - une zone a 0 parmi des zones non nulles GARDE son axe (a 0) ;
 *  - liste vide ou tout a 0 → retourne `emptyList()` (l'UI affiche un
 *    placeholder plutot que de tracer un radar plat).
 */
fun buildZoneVolumeRadar(zones: List<ZoneVolumeDatum>): List<ZoneVolumeDatum> =
    if (zones.isEmpty() || zones.all { it.volume <= 0f }) emptyList() else zones
