import type { RadarAxis, RadarSeries } from '@designsystem/common_components/radar-chart';

/**
 * Volume agrégé d'une zone musculaire sur la période (déjà sommé sur les buckets + unité d'affichage
 * appliquée), avec la couleur de la zone (token CSS ou hex) pour colorer le nom de l'axe.
 */
export interface ZoneVolume {
  /** Libellé de l'axe = nom de zone (Chest / Back / …, EN canonique comme le reste des Stats). */
  zone: string;
  /** Couleur du nom d'axe (token `var(--…)` ou hex) = couleur de la zone. */
  color: string;
  /** Volume agrégé sur la période (Σ poids·reps·coef), unité d'affichage déjà appliquée. */
  volume: number;
}

/**
 * Construit les axes + l'unique série radar « équilibre / symétrie d'entraînement » à partir du
 * volume par zone : un axe par zone (ordre d'entrée conservé), nom d'axe coloré par zone, une seule
 * série remplie en accent unique. Pure & testable — aucune dépendance Angular ni résolution CSS (les
 * couleurs sont fournies par l'appelant ; le composant radar résout les tokens et partage l'échelle).
 * Renvoie des tableaux vides si aucun volume (→ placeholder du composant) pour ne pas tracer un radar
 * plat.
 */
export function zoneVolumeRadar(
  zones: ZoneVolume[],
  seriesColor: string,
  seriesName = 'Volume',
): { axes: RadarAxis[]; series: RadarSeries[] } {
  if (zones.length === 0 || zones.every((z) => z.volume <= 0)) return { axes: [], series: [] };
  return {
    axes: zones.map((z) => ({ label: z.zone, color: z.color })),
    series: [{ name: seriesName, values: zones.map((z) => z.volume), color: seriesColor, area: true }],
  };
}
