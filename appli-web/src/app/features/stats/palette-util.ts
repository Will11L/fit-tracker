/**
 * Port 1:1 de core/data/PaletteUtil.kt (Android) : génère une palette de `count`
 * couleurs dérivées de `zoneColorHex` en faisant varier conjointement la
 * luminosité HSL (±38 % autour de 50 % à spread=1), la teinte (±10°) et la
 * saturation (désaturation progressive des nuances claires). Stats utilise
 * spread=1.0 pour distinguer 35 muscles.
 */
export function paletteForZone(zoneColorHex: string, count: number, spread = 1.0): string[] {
  if (count <= 0) return [];
  const [baseHue, baseSat] = hexToHsl(zoneColorHex);
  const s = Math.min(1, Math.max(0, spread));
  return Array.from({ length: count }, (_, i) => {
    const t = count === 1 ? 0.5 : i / (count - 1);
    // Centré sur 0.5, range proportionnel au spread (s=1 : [0.12..0.88]).
    const lightness = 0.5 + (t - 0.5) * 0.76 * s;
    const hue = (baseHue + (t - 0.5) * 20 * s + 360) % 360;
    // Sombre (t=0) = saturation pleine ; clair (t=1) = sat * (1 - 0.5*s).
    const saturation = baseSat * (1 - 0.5 * t * s);
    return hslToHex(hue, saturation, lightness);
  });
}

/** Couleurs par zone, résolues du thème courant (code couleur partagé stats / listes / détails). */
export function zoneColorMap(): Record<string, string> {
  return {
    Chest: resolveCssColor('var(--app-primary-action)'),
    Back: resolveCssColor('var(--c-orange-medium)'),
    Shoulders: resolveCssColor('var(--app-accent-text)'),
    Arms: resolveCssColor('var(--c-red-medium)'),
    Legs: resolveCssColor('var(--c-medium-green)'),
    Core: resolveCssColor('var(--c-yellow-medium)'),
    Other: resolveCssColor('var(--c-medium-purple)'),
  };
}

/** Nuance par groupe (spread 0.55 resserré) : chaque groupe = une nuance de la couleur de sa zone. */
export function groupShadeMap(
  muscles: { muscleGroup?: string | null; zone?: string | null }[],
): Map<string, string> {
  const zoneColors = zoneColorMap();
  const byZone = new Map<string, string[]>();
  for (const m of muscles) {
    if (!m.muscleGroup || !m.zone) continue;
    const arr = byZone.get(m.zone) ?? [];
    if (!arr.includes(m.muscleGroup)) arr.push(m.muscleGroup);
    byZone.set(m.zone, arr);
  }
  const out = new Map<string, string>();
  for (const [zone, groups] of byZone) {
    groups.sort();
    const shades = paletteForZone(zoneColors[zone] ?? '#888888', groups.length, 0.55);
    groups.forEach((g, i) => out.set(g, shades[i]));
  }
  return out;
}

/** Résout `var(--token)` en couleur concrète (hex) via le :root courant ; hex passé tel quel. */
export function resolveCssColor(css: string): string {
  const m = css.match(/var\((--[\w-]+)\)/);
  const v = m ? getComputedStyle(document.documentElement).getPropertyValue(m[1]).trim() : css;
  return v || '#888888';
}

/** #rrggbb → [hue 0-360, sat 0-1, lightness 0-1] (mêmes conventions que ColorUtils Android). */
function hexToHsl(hex: string): [number, number, number] {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16) / 255;
  const g = parseInt(h.slice(2, 4), 16) / 255;
  const b = parseInt(h.slice(4, 6), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let hue: number;
  if (max === r) hue = ((g - b) / d + (g < b ? 6 : 0)) * 60;
  else if (max === g) hue = ((b - r) / d + 2) * 60;
  else hue = ((r - g) / d + 4) * 60;
  return [hue, s, l];
}

function hslToHex(h: number, s: number, l: number): string {
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;
  let r = 0, g = 0, b = 0;
  if (h < 60) [r, g, b] = [c, x, 0];
  else if (h < 120) [r, g, b] = [x, c, 0];
  else if (h < 180) [r, g, b] = [0, c, x];
  else if (h < 240) [r, g, b] = [0, x, c];
  else if (h < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  const to = (v: number): string => Math.round((v + m) * 255).toString(16).padStart(2, '0');
  return `#${to(r)}${to(g)}${to(b)}`;
}
