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
import { CustomChart, type CustomSeriesOption } from 'echarts/charts';
import { GridComponent, type GridComponentOption } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ComposeOption } from 'echarts/core';
import type { SleepPhasePoint } from './health-aggregations';

echarts.use([CustomChart, GridComponent, CanvasRenderer]);

type ChartOption = ComposeOption<CustomSeriesOption | GridComponentOption>;

/** Écart max (min) ponté entre deux slices : au-delà = vraie rupture (données absentes). */
const BRIDGE_MIN = 5;

/**
 * Hypnogramme « Cette nuit » — pendant web de `HypnogramChart.kt` (Android). Custom series
 * ECharts (le visualMap ne colore pas les segments d'une step line — vérifié SSR) : x = temps
 * (minutes relatives à minuit du jour de réveil, la veille au soir en négatif), y = 4 niveaux
 * en axe CATÉGORIE (labels de phases), ÉVEILLÉ EN HAUT → PROFOND EN BAS. Aspect créneau,
 * miroir Android : paliers 2,5 px bouts carrés colorés par famille, montées FINES (1 px) en
 * dégradé vertical ADOUCI (couleurs des deux paliers tirées à mi-chemin vers le gris discret).
 * Les petits écarts entre slices (≤ 5 min : arrondis d'import + micro-trous HC) sont pontés ;
 * un vrai trou (> 5 min) = rupture sans montée. Hauteur par défaut = 0,42 × largeur.
 */
@Component({
  selector: 'app-sleep-hypnogram-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div
    class="shc"
    [style.height.px]="height()"
    [style.aspect-ratio]="height() === null ? '1 / 0.42' : null"
  >
    <div #chart class="shc__canvas"></div>
  </div>`,
  styles: [
    `
      .shc {
        width: 100%;
      }
      .shc__canvas {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class SleepHypnogramChart {
  /** Chronologie triée (cf. sleepPhaseTimeline) : début relatif, durée, famille 0..3. */
  readonly points = input<SleepPhasePoint[]>([]);
  /** Couleurs par famille (ordre buckets : profond / léger / paradoxal / éveillé). */
  readonly phaseColors = input<string[]>([]);
  readonly height = input<number | null>(null);

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      const el = this.chartEl();
      if (!el) return;
      const opt = this.buildOption(this.points(), this.phaseColors());
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
  /** Mélange 50/50 de deux couleurs #RRGGBB (dégradé adouci des montées, miroir lerp Android). */
  private mix(a: string, b: string): string {
    const pa = a.match(/^#([0-9a-f]{6})$/i);
    const pb = b.match(/^#([0-9a-f]{6})$/i);
    if (!pa || !pb) return a;
    const na = parseInt(pa[1], 16);
    const nb = parseInt(pb[1], 16);
    const ch = (shift: number) =>
      Math.round(((na >> shift) & 255) * 0.5 + ((nb >> shift) & 255) * 0.5);
    return `#${((ch(16) << 16) | (ch(8) << 8) | ch(0)).toString(16).padStart(6, '0')}`;
  }
  /** "HH:MM" d'une minute relative à minuit du jour de réveil (négatif = la veille). */
  private hhmm(min: number): string {
    const m = ((Math.round(min) % 1440) + 1440) % 1440;
    return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  }

  private buildOption(points: SleepPhasePoint[], phaseColors: string[]): ChartOption {
    if (points.length === 0) return {};
    const axisLabel = this.cssVar('--app-text-tertiary');
    const grid = this.cssVar('--app-divider');
    const soften = this.cssVar('--c-light-gray-blue');
    const colors = [0, 1, 2, 3].map((b) => this.resolveColor(phaseColors[b] ?? 'var(--app-divider)'));
    const minX = points[0].startMin;
    const maxX = Math.max(...points.map((p) => p.startMin + p.minutes));

    // Un item par slice : [début, famille, fin PONTÉE (écart ≤ 5 min → début de la
    // slice suivante : le palier rejoint exactement la montée), famille précédente
    // (-1 = pas de montée : 1re slice, même palier, ou vrai trou > 5 min).
    const data = points.map((p, i) => {
      const prev = points[i - 1];
      const next = points[i + 1];
      const end = p.startMin + p.minutes;
      const gapNext = next ? next.startMin - end : Infinity;
      const bridgedEnd = gapNext >= 0 && gapNext <= BRIDGE_MIN ? next!.startMin : end;
      const gapPrev = prev ? p.startMin - (prev.startMin + prev.minutes) : Infinity;
      const riserFrom = prev && prev.bucket !== p.bucket && gapPrev <= BRIDGE_MIN ? prev.bucket : -1;
      return [p.startMin, p.bucket, bridgedEnd, riserFrom];
    });

    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 10, top: 10, bottom: 4, containLabel: true },
      xAxis: {
        type: 'value',
        min: minX,
        max: maxX,
        axisLabel: { color: axisLabel, fontSize: 10, formatter: (v: number) => this.hhmm(v) },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
      },
      // Axe catégorie MUET : 1 bande par famille (½ bande de marge haut/bas offerte par
      // boundaryGap), sans labels — la légende à points vit sous le chart (dans la card).
      yAxis: {
        type: 'category',
        data: ['0', '1', '2', '3'],
        axisLabel: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: grid, type: 'dashed', width: 1, opacity: 0.25 } },
      },
      series: [
        {
          type: 'custom',
          renderItem: (_params, api) => {
            const bucket = api.value(1) as number;
            const [x1, y] = api.coord([api.value(0), bucket]);
            const [x2] = api.coord([api.value(2), bucket]);
            const children: object[] = [
              // Palier : trait 2,5 px bouts carrés à la couleur de la famille.
              {
                type: 'line',
                shape: { x1, y1: y, x2, y2: y },
                style: { stroke: colors[bucket], lineWidth: 2.5, lineCap: 'butt' },
              },
            ];
            const riserFrom = api.value(3) as number;
            if (riserFrom >= 0) {
              const [, yPrev] = api.coord([api.value(0), riserFrom]);
              const topFirst = yPrev < y; // yPrev plus HAUT à l'écran (px plus petit)
              children.push({
                type: 'line',
                shape: { x1, y1: yPrev, x2: x1, y2: y },
                // Montée fine en dégradé vertical adouci (mi-chemin vers le gris).
                style: {
                  stroke: new echarts.graphic.LinearGradient(0, topFirst ? 0 : 1, 0, topFirst ? 1 : 0, [
                    { offset: 0, color: this.mix(colors[riserFrom], soften) },
                    { offset: 1, color: this.mix(colors[bucket], soften) },
                  ]),
                  lineWidth: 1,
                },
              });
            }
            return { type: 'group', children } as never;
          },
          data,
        },
      ],
    };
  }
}
