import {
  booleanAttribute,
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
import { BarChart, LineChart, type BarSeriesOption, type LineSeriesOption } from 'echarts/charts';
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

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer]);

type ChartOption = ComposeOption<
  BarSeriesOption | LineSeriesOption | GridComponentOption | TooltipComponentOption | MarkLineComponentOption
>;

/**
 * Tendance 7 jours combinée « Distance & calories » : barres = calories d'activité
 * (turquoise, identité de section) + courbe lissée superposée = distance (trait clair
 * neutre), sur DEUX axes Y masqués (échelles indépendantes — kcal et m/km n'ont rien
 * à voir). Mêmes codes que health-bar-chart (barres ¼ de bande, coins arrondis, ligne
 * de moyenne kcal pointillée) et health-line-chart (courbe lissée, jours vides = gap).
 * [fill] absorbe la hauteur disponible de la card (position absolue — jamais de
 * contribution à la hauteur de rangée, cf. leçon de la boucle de croissance).
 */
@Component({
  selector: 'app-energy-week-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div
    class="ewc"
    [class.ewc--fill]="fill()"
    [style.height]="!fill() && height() !== null ? height() + 'px' : null"
    [style.aspect-ratio]="!fill() && height() === null ? '1 / 0.42' : null"
  >
    <div #chart class="ewc__canvas"></div>
  </div>`,
  styles: [
    `
      :host {
        display: block;
        position: relative;
      }
      .ewc {
        width: 100%;
      }
      .ewc--fill {
        position: absolute;
        inset: 0;
        width: auto;
      }
      .ewc__canvas {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class EnergyWeekChart {
  /** Quantièmes sous chaque slot (7 jours). */
  readonly labels = input<string[]>([]);
  /** Calories d'activité par jour (0 = jour vide, piste seule). */
  readonly kcal = input<number[]>([]);
  /** Moyenne kcal des jours renseignés (null = pas de ligne). */
  readonly kcalAverage = input<number | null>(null);
  /** Distance par jour pour la courbe (null = jour vide, jamais interpolé). */
  readonly distance = input<(number | null)[]>([]);
  /** Unité de la distance ('m' / 'km') pour le tooltip. */
  readonly distanceUnit = input('m');
  readonly height = input<number | null>(null);
  readonly fill = input(false, { transform: booleanAttribute });

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      const el = this.chartEl();
      if (!el) return;
      const opt = this.buildOption(
        this.labels(),
        this.kcal(),
        this.kcalAverage(),
        this.distance(),
        this.distanceUnit(),
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

  private buildOption(
    labels: string[],
    kcal: number[],
    kcalAverage: number | null,
    distance: (number | null)[],
    distanceUnit: string,
  ): ChartOption {
    const axisLabel = this.cssVar('--app-text-tertiary');
    const bar = this.cssVar('--c-turquoise');
    const line = this.cssVar('--app-text-primary');
    const avgColor = this.cssVar('--c-light-gray-blue');
    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 8, top: 10, bottom: 4, containLabel: true },
      // Unités MIXTES au survol : suffixe par série (kcal pour les barres, m/km pour la courbe).
      tooltip: themedAxisTooltip('', { Activité: ' kcal', Distance: ` ${distanceUnit}` }),
      xAxis: {
        type: 'category',
        data: labels,
        axisLabel: { color: axisLabel, fontSize: 10, interval: 0 },
        axisLine: { lineStyle: { color: 'transparent' } },
        axisTick: { show: false },
      },
      // Deux échelles indépendantes, toutes deux masquées (les valeurs se lisent au survol).
      yAxis: [
        { type: 'value', min: 0, show: false, splitLine: { show: false } },
        { type: 'value', scale: true, show: false, splitLine: { show: false } },
      ],
      series: [
        {
          type: 'bar',
          name: 'Activité',
          data: kcal,
          itemStyle: { color: bar, borderRadius: [2, 2, 0, 0] },
          barWidth: '25%',
          ...(kcalAverage != null
            ? {
                markLine: {
                  silent: true,
                  symbol: 'none',
                  label: {
                    show: true,
                    position: 'insideEndTop' as const,
                    formatter: Math.round(kcalAverage).toLocaleString('fr-FR'),
                    color: avgColor,
                    fontSize: 10,
                  },
                  lineStyle: { color: avgColor, type: 'dashed' as const, width: 1.5, opacity: 0.9 },
                  data: [{ yAxis: kcalAverage }],
                },
              }
            : {}),
        },
        {
          type: 'line',
          name: 'Distance',
          yAxisIndex: 1,
          data: distance,
          smooth: 0.4,
          symbol: 'circle',
          symbolSize: 5,
          connectNulls: false,
          lineStyle: { width: 2, color: line },
          itemStyle: { color: line },
        },
      ],
    };
  }
}
