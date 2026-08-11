import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  viewChild,
} from '@angular/core';
import * as echarts from 'echarts/core';
import {
  LineChart,
  ScatterChart,
  type LineSeriesOption,
  type ScatterSeriesOption,
} from 'echarts/charts';
import {
  GridComponent,
  type GridComponentOption,
  MarkLineComponent,
  type MarkLineComponentOption,
  TooltipComponent,
  type TooltipComponentOption,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ComposeOption } from 'echarts/core';
import { themedAxisTooltip } from '@designsystem/common_components/chart-tooltip';
import { SLOT_MINUTES } from './health-aggregations';

echarts.use([LineChart, ScatterChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer]);

type ChartOption = ComposeOption<
  | LineSeriesOption
  | ScatterSeriesOption
  | GridComponentOption
  | TooltipComponentOption
  | MarkLineComponentOption
>;

/**
 * Chart en ligne de la section Santé — pendant web de `TrendLineChart.kt` (Android). Courbe lissée +
 * aire dégradée (couleur du domaine → transparent), échelle Y resserrée autour des données (`scale`,
 * pas depuis 0), graduations pointillées, jours vides = gap (`null`, aucune interpolation). markLine de
 * moyenne optionnelle (lightGrayBlue) avec sa valeur (chiffre seul) calée en haut à droite, mêmes codes
 * que `health-bar-chart`. Utilisé pour les vues « 7 derniers jours » de Pas & FC (parité Android).
 * Hauteur par défaut = ratio Android (0,46 × largeur mesurée) ; [height] px non-null force une
 * hauteur fixe.
 */
@Component({
  selector: 'app-health-line-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div
    class="hlc"
    [style.height.px]="height()"
    [style.aspect-ratio]="height() === null ? '1 / 0.46' : null"
  >
    <div #chart class="hlc__canvas"></div>
  </div>`,
  styles: [
    `
      .hlc {
        width: 100%;
      }
      .hlc__canvas {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class HealthLineChart {
  readonly values = input<(number | null)[]>([]);
  /** WEEK (défaut) : 1 label fourni par point. INTRADAY : 48 tranches de 30 min,
   *  repères horaires calculés (0/6/12/18 h), [labels] ignoré. */
  readonly mode = input<'WEEK' | 'INTRADAY'>('WEEK');
  /** Libellés sous chaque point (quantième du jour). */
  readonly labels = input<string[]>([]);
  /** Couleur d'identité du domaine (vert pas / orange FC…). */
  readonly color = input('var(--c-light-green)');
  /** Valeur de la ligne de moyenne (null = pas de ligne). */
  readonly average = input<number | null>(null);
  /** Suffixe d'unité des tooltips (ex. ' pas', ' bpm'). */
  readonly valueSuffix = input('');
  /** Intitulé de la série dans le tooltip (ex. « Pas ») ; vide = valeur seule. */
  readonly seriesName = input('');
  /** Hauteur fixe en px ; null (défaut) = ratio Android (hauteur = 0,46 × largeur). */
  readonly height = input<number | null>(null);
  /** Non-null = marque chaque slot VIDE d'un point de cette couleur, POSÉ SUR la courbe
   *  (valeur interpolée invisible par laquelle la courbe passe ; bords = à plat sur la
   *  valeur la plus proche) — miroir `emptySlotColor` du TrendLineChart Android (ex.
   *  jours sans pesée en rouge). Affichage seul (le web est en lecture seule). */
  readonly emptySlotColor = input<string | null>(null);
  /** Couleur PAR POINT mesuré (aligné sur [values], null = couleur de la courbe) —
   *  miroir `pointColor` Android (ex. catégories de stress vert → rouge). */
  readonly pointColors = input<(string | null)[] | null>(null);

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      const el = this.chartEl();
      if (!el) return;
      // Lecture réactive des inputs.
      const opt = this.buildOption(
        this.values(),
        this.mode(),
        this.labels(),
        this.color(),
        this.average(),
        this.valueSuffix(),
        this.seriesName(),
        this.emptySlotColor(),
        this.pointColors(),
      );
      if (!this.chart) {
        this.chart = echarts.init(el.nativeElement);
        this.resizeObs = new ResizeObserver(() => this.chart?.resize());
        this.resizeObs.observe(el.nativeElement);
      }
      this.chart.setOption(opt, true);
    });
    inject(DestroyRef).onDestroy(() => {
      this.resizeObs?.disconnect();
      this.resizeObs = null;
      this.chart?.dispose();
      this.chart = null;
    });
  }

  private cssVar(name: string): string {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#888888';
  }
  private resolveColor(css: string): string {
    const m = css.match(/var\((--[\w-]+)\)/);
    return m ? this.cssVar(m[1]) : css;
  }
  /** #RRGGBB → rgba(...) avec alpha (pour l'aire dégradée). Renvoie la couleur telle quelle si non hex. */
  private withAlpha(color: string, alpha: number): string {
    const m = color.match(/^#([0-9a-f]{6})$/i);
    if (!m) return color;
    const n = parseInt(m[1], 16);
    return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
  }

  private buildOption(
    values: (number | null)[],
    mode: 'WEEK' | 'INTRADAY',
    labels: string[],
    color: string,
    average: number | null,
    valueSuffix: string,
    seriesName: string,
    emptySlotColor: string | null,
    pointColors: (string | null)[] | null,
  ): ChartOption {
    const axisLabel = this.cssVar('--app-text-tertiary'); // clair (parité charts de la page)
    const grid = this.cssVar('--app-divider'); // GrayBlue (gridlines pointillées)
    const line = this.resolveColor(color);
    const avgColor = this.cssVar('--c-light-gray-blue');
    const slotsPerHour = 60 / SLOT_MINUTES; // 2 tranches de 30 min par heure
    const isIntraday = mode === 'INTRADAY';

    // Slots vides marqués ([emptySlotColor]) : la courbe passe par un point interpolé
    // INVISIBLE (symbolSize 0 — linéaire entre voisins, bords à plat sur la valeur la
    // plus proche) et un scatter pose le point coloré au même endroit → pile SUR la
    // courbe (leçon du TrendLineChart Android : l'interpolation à côté du tracé se voit).
    const measured = values.map((v, i) => (v != null ? i : -1)).filter((i) => i >= 0);
    const missing: [number, number][] = [];
    const lineData: (number | null | { value: number; symbolSize?: number; itemStyle?: { color: string } })[] =
      values.map((v, i) => {
        if (v != null) {
          // Point mesuré : couleur par valeur ([pointColors], ex. catégorie de stress).
          const pc = pointColors?.[i];
          return pc ? { value: v, itemStyle: { color: this.resolveColor(pc) } } : v;
        }
        if (!emptySlotColor || measured.length === 0) return null;
        const first = measured[0];
        const last = measured[measured.length - 1];
        let filled: number;
        if (i < first) filled = values[first]!;
        else if (i > last) filled = values[last]!;
        else {
          const prev = measured.filter((k) => k < i).pop()!;
          const next = measured.find((k) => k > i)!;
          const t = (i - prev) / (next - prev);
          filled = values[prev]! * (1 - t) + values[next]! * t;
        }
        missing.push([i, filled]);
        return { value: filled, symbolSize: 0 };
      });
    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 10, top: 12, bottom: 4, containLabel: true },
      tooltip: themedAxisTooltip(valueSuffix),
      xAxis: {
        type: 'category',
        // INTRADAY : catégories = index de tranche, repères horaires aux 6 h
        // (mêmes codes que health-bar-chart). WEEK : 1 label fourni par point.
        data: isIntraday ? values.map((_, i) => String(i)) : labels,
        boundaryGap: false,
        axisLabel: isIntraday
          ? {
              color: axisLabel,
              fontSize: 10,
              interval: (index: number) => index % (6 * slotsPerHour) === 0,
              formatter: (_v: string, index: number) => `${index / slotsPerHour}h`,
            }
          : { color: axisLabel, fontSize: 10, interval: 0 },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        // Échelle resserrée autour des valeurs (pas depuis 0 : les faibles variations restent lisibles).
        scale: true,
        axisLabel: { color: axisLabel, fontSize: 10 },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: grid, type: 'dashed', width: 1, opacity: 0.35 } },
      },
      series: [
        {
          type: 'line',
          name: seriesName,
          data: lineData,
          smooth: 0.4,
          symbol: 'circle',
          symbolSize: 5,
          // Parité TrendLineChart Android : la courbe RELIE les mesures successives
          // par-dessus les jours vides (aucun point visible inventé).
          connectNulls: true,
          lineStyle: { width: 2, color: line },
          itemStyle: { color: line },
          // Aire dégradée sous la courbe : couleur (subtile) → transparent en descendant.
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: this.withAlpha(line, 0.28) },
                { offset: 1, color: 'transparent' },
              ],
            },
          },
          // Ligne de moyenne optionnelle (pointillé lightGrayBlue) + valeur (chiffre seul) en haut à droite.
          ...(average != null
            ? {
                markLine: {
                  silent: true,
                  symbol: 'none',
                  label: {
                    show: true,
                    position: 'insideEndTop',
                    formatter: Math.round(average).toLocaleString('fr-FR'),
                    color: avgColor,
                    fontSize: 10,
                  },
                  lineStyle: { color: avgColor, type: 'dashed', width: 1.5, opacity: 0.9 },
                  data: [{ yAxis: average }],
                },
              }
            : {}),
        },
        // Points des jours vides (posés sur la courbe) — hors tooltip (valeur estimée).
        ...(missing.length > 0
          ? [
              {
                type: 'scatter' as const,
                data: missing,
                symbolSize: 6,
                itemStyle: { color: this.resolveColor(emptySlotColor!) },
                silent: true,
                tooltip: { show: false },
                z: 3,
              },
            ]
          : []),
      ],
    };
  }
}
