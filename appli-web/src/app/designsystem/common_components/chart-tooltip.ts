import type { TooltipComponentOption } from 'echarts/components';

/** Résout un token CSS depuis la racine du document (couleurs du thème → valeur concrète). */
function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#888888';
}

const round1 = (v: number): number => Math.round(v * 10) / 10;

/**
 * Tooltip ECharts thémé pour les graphes en ligne / barres (`trigger: 'axis'`), aligné sur le tooltip
 * du radar : fond thirdBlue, bordure first-blue, coins arrondis, largeur mini (boîte ≥ carrée).
 * En-tête = valeur d'axe (date / semaine) en titled-divider gris-bleu ; une ligne par série, nom à
 * GAUCHE, valeur à DROITE en gras, le tout dans la couleur de la série ; valeurs à 1 décimale (+ suffixe
 * optionnel, ex. « % »), null → « – ». Partagé par NutritionStatsChart et MultiLineChart.
 * [suffixBySeries] (optionnel) : suffixe PAR NOM de série (prime sur [valueSuffix]) — pour les charts
 * à unités mixtes (ex. energy-week-chart : barres kcal + courbe distance en m/km).
 */
export function themedAxisTooltip(
  valueSuffix = '',
  suffixBySeries?: Record<string, string>,
): TooltipComponentOption {
  return {
    trigger: 'axis',
    backgroundColor: cssVar('--app-bg-recessed'),
    borderColor: cssVar('--c-first-blue'),
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: cssVar('--app-text-primary'), fontSize: 12 },
    extraCssText: `border-radius: ${cssVar('--radius-md')}; min-width: 160px;`,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- params echarts (union TopLevelFormatterParams)
    formatter: (params: any) => {
      const arr = Array.isArray(params) ? params : [params];
      const axisLabel = arr[0]?.axisValueLabel ?? arr[0]?.name ?? '';
      const head =
        `<div style="display:flex;align-items:center;gap:6px;margin-bottom:5px;color:var(--c-gray-blue);font-weight:600">` +
        `<span style="flex:1;height:1px;background:var(--c-gray-blue);opacity:.6"></span>` +
        `<span>${axisLabel}</span>` +
        `<span style="flex:1;height:1px;background:var(--c-gray-blue);opacity:.6"></span></div>`;
      const rows = arr
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- params echarts
        .map((p: any) => {
          const raw = Array.isArray(p.value) ? p.value[p.value.length - 1] : p.value;
          // Série sans nom : ECharts génère « series\u00000 » (rendu « series() ») → valeur seule.
          const rawName: string = p.seriesName ?? '';
          const name = /^series\u0000?\d*$/.test(rawName) ? '' : rawName;
          const suffix = suffixBySeries?.[name] ?? valueSuffix;
          const valTxt = raw == null || Number.isNaN(raw) ? '–' : `${round1(raw)}${suffix}`;
          // Mono-série sans nom (ex. % d'achievement) : valeur seule, alignée à droite (pas de libellé vide).
          if (!name) {
            return `<div style="text-align:right;font-weight:700;color:${p.color};padding:2px 0">${valTxt}</div>`;
          }
          return (
            `<div style="display:flex;justify-content:space-between;gap:14px;color:${p.color};padding:2px 0">` +
            `<span>${name}</span><span style="font-weight:700">${valTxt}</span></div>`
          );
        })
        .join('');
      return head + rows;
    },
  };
}
