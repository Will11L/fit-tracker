import { zoneVolumeRadar, type ZoneVolume } from './zone-radar-data';

describe('zoneVolumeRadar — radar volume par zone (équilibre d’entraînement)', () => {
  const zones: ZoneVolume[] = [
    { zone: 'Chest', color: 'var(--app-primary-action)', volume: 1200 },
    { zone: 'Back', color: 'var(--c-orange-medium)', volume: 900 },
    { zone: 'Legs', color: 'var(--c-medium-green)', volume: 1500 },
  ];

  it('un axe par zone (ordre conservé), nom d’axe coloré par zone, sans max imposé', () => {
    const { axes } = zoneVolumeRadar(zones, '#ffffff');
    expect(axes.map((a) => a.label)).toEqual(['Chest', 'Back', 'Legs']);
    expect(axes.map((a) => a.color)).toEqual([
      'var(--app-primary-action)',
      'var(--c-orange-medium)',
      'var(--c-medium-green)',
    ]);
    // Pas de max par axe → échelle partagée gérée par le composant radar (axes comparables).
    expect(axes.every((a) => a.max === undefined)).toBe(true);
  });

  it('une seule série remplie en accent unique, valeurs alignées sur les axes', () => {
    const { series } = zoneVolumeRadar(zones, 'var(--app-primary-action)', 'Volume');
    expect(series.length).toBe(1);
    expect(series[0].name).toBe('Volume');
    expect(series[0].values).toEqual([1200, 900, 1500]);
    expect(series[0].color).toBe('var(--app-primary-action)');
    expect(series[0].area).toBe(true);
  });

  it('série nommée « Volume » par défaut', () => {
    expect(zoneVolumeRadar(zones, '#ffffff').series[0].name).toBe('Volume');
  });

  it('aucun volume (liste vide ou tout à 0) → axes + séries vides (placeholder du composant)', () => {
    expect(zoneVolumeRadar([], '#ffffff')).toEqual({ axes: [], series: [] });
    expect(zoneVolumeRadar(zones.map((z) => ({ ...z, volume: 0 })), '#ffffff')).toEqual({
      axes: [],
      series: [],
    });
  });

  it('une zone à 0 parmi des zones non nulles GARDE son axe (valeur 0) — hexagone stable', () => {
    // La page Stats sport passe toujours les 6 zones canoniques (axe à 0 si pas de volume) pour
    // une lecture de symétrie stable : seul le « tout à 0 » bascule en placeholder, pas une zone
    // isolée. On verrouille qu'une zone à 0 n'est jamais filtrée tant qu'au moins une a du volume.
    const mixed: ZoneVolume[] = [
      { zone: 'Chest', color: 'var(--c1)', volume: 1200 },
      { zone: 'Back', color: 'var(--c2)', volume: 0 },
      { zone: 'Legs', color: 'var(--c3)', volume: 1500 },
    ];
    const { axes, series } = zoneVolumeRadar(mixed, '#ffffff');
    expect(axes.map((a) => a.label)).toEqual(['Chest', 'Back', 'Legs']);
    expect(series[0].values).toEqual([1200, 0, 1500]); // l'axe Back reste, à 0
  });
});
