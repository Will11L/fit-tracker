import {
  afterNextRender,
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
import { LineChart, type LineSeriesOption } from 'echarts/charts';
import {
  GridComponent,
  type GridComponentOption,
  LegendComponent,
  type LegendComponentOption,
  MarkLineComponent,
  type MarkLineComponentOption,
  TooltipComponent,
  type TooltipComponentOption,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ComposeOption } from 'echarts/core';
import { themedAxisTooltip } from './chart-tooltip';

echarts.use([
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  CanvasRenderer,
]);

type ChartOption = ComposeOption<
  | LineSeriesOption
  | GridComponentOption
  | TooltipComponentOption
  | LegendComponentOption
  | MarkLineComponentOption
>;

/** Une série de MultiLineChart : nom (légende), valeurs (alignées sur xLabels), couleur. `null` dans
 *  `data` = point sauté (gap, ECharts ne relie pas), pour les buckets sans valeur comparable. */
export interface LineSeries {
  name: string;
  data: (number | null)[];
  color: string;
}

/**
 * Graphe multi-lignes — miroir de MultiLineChart.kt (Android = Vico) : lignes lissées (1 par
 * série), légende + tooltips interactifs, axes thémés, fond bgRecessed. État vide si aucune série.
 * Rendu via ECharts (modulaire, canvas). API adaptée (series[] vs data+metrics Android).
 *
 * Options : `markLineValue` trace une ligne repère horizontale pointillée (couleur primaire, style
 * goals-achievement-chart — ex. l'objectif 100 %) et garantit sa visibilité (l'échelle Y l'inclut) ;
 * `valueSuffix` suffixe les valeurs des axes/tooltips (ex. « % »).
 */
@Component({
  selector: 'app-multi-line-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mlc" [style.height.px]="height()">
      @if (series().length === 0) {
        <div class="mlc__empty">{{ emptyText() }}</div>
      } @else {
        <div #chart class="mlc__chart"></div>
      }
    </div>
  `,
  styles: [
    `
      .mlc {
        width: 100%;
        box-sizing: border-box;
        border-radius: var(--radius-md);
        background: var(--app-bg-recessed);
        padding: var(--space-3);
      }
      .mlc__empty {
        width: 100%;
        height: 100%;
        box-sizing: border-box;
        display: flex;
        align-items: center;
        justify-content: center;
        border: 1.5px solid var(--app-primary-action);
        border-radius: var(--radius-md);
        color: var(--app-primary-action);
      }
      .mlc__chart {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class MultiLineChart {
  readonly series = input<LineSeries[]>([]);
  readonly xLabels = input<string[]>([]);
  readonly emptyText = input('No data selected');
  readonly height = input(200);
  /** Légende ECharts intégrée. Off par défaut = fidèle au composant Figma/Android (pas de légende
   *  dans le chart -> le plot occupe toute la hauteur) ; on l'active quand les séries n'ont pas de
   *  clé couleur externe (écran Stats : jusqu'à 8 muscles). */
  readonly showLegend = input(false);
  /** Valeur Y d'une ligne repère horizontale (pointillés, couleur primaire) — ex. 100 (% objectif).
   *  `null` (défaut) = pas de repère. Quand fixée, l'échelle Y inclut toujours le repère. */
  readonly markLineValue = input<number | null>(null);
  /** Suffixe d'unité des valeurs (axe Y + tooltips), ex. « % ». Vide par défaut. */
  readonly valueSuffix = input('');

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      const series = this.series();
      const labels = this.xLabels();
      const el = this.chartEl();
      if (series.length === 0 || !el) {
        this.teardown();
        return;
      }
      const showLegend = this.showLegend();
      const markLineValue = this.markLineValue();
      const valueSuffix = this.valueSuffix();
      if (!this.chart) {
        this.chart = echarts.init(el.nativeElement);
        this.resizeObs = new ResizeObserver(() => this.chart?.resize());
        this.resizeObs.observe(el.nativeElement);
      }
      this.chart.setOption(
        this.buildOption(series, labels, showLegend, markLineValue, valueSuffix),
        true,
      );
    });
    inject(DestroyRef).onDestroy(() => this.teardown());
  }

  private teardown(): void {
    this.resizeObs?.disconnect();
    this.resizeObs = null;
    this.chart?.dispose();
    this.chart = null;
  }

  private cssVar(name: string): string {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#888888';
  }

  private resolveColor(css: string): string {
    const m = css.match(/var\((--[\w-]+)\)/);
    return m ? this.cssVar(m[1]) : css;
  }

  private buildOption(
    series: LineSeries[],
    labels: string[],
    showLegend: boolean,
    markLineValue: number | null,
    valueSuffix: string,
  ): ChartOption {
    // Figma : ligne d'axe X+Y, grille et labels partagent une seule couleur (#5E78A0 = --app-divider).
    const axisColor = this.cssVar('--app-divider');
    // Ligne repère : échelle Y bornée pour la garder toujours visible (repère + 5 % de headroom),
    // comme goals-achievement-chart. Sans repère → échelle auto d'ECharts.
    let yMax: number | undefined;
    if (markLineValue != null) {
      const values = series.flatMap((s) => s.data.filter((v): v is number => v != null));
      const dataMax = Math.max(0, ...values);
      yMax = Math.ceil(Math.max(markLineValue, dataMax) * 1.05);
    }
    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 12, top: 8, bottom: showLegend ? 52 : 4, containLabel: true },
      tooltip: themedAxisTooltip(valueSuffix),
      ...(showLegend
        ? {
            legend: {
              bottom: 0,
              textStyle: { color: axisColor, fontSize: 11 },
              icon: 'roundRect',
              itemWidth: 12,
              itemHeight: 8,
              itemGap: 10,
            },
          }
        : {}),
      xAxis: {
        type: 'category',
        data: labels,
        boundaryGap: false,
        axisLabel: { color: axisColor, fontSize: 11 },
        axisLine: { lineStyle: { color: axisColor } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        min: 0,
        ...(yMax !== undefined ? { max: yMax } : {}),
        axisLabel: {
          color: axisColor,
          fontSize: 11,
          ...(valueSuffix ? { formatter: `{value}${valueSuffix}` } : {}),
        },
        axisLine: { show: true, lineStyle: { color: axisColor } },
        // traits horizontaux (grille) : pointillés, fins et plus discrets que les axes pleins
        splitLine: { lineStyle: { color: axisColor, type: 'dashed', width: 0.5, opacity: 0.5 } },
      },
      series: series.map((s, i) => ({
        name: s.name,
        type: 'line' as const,
        smooth: 0.4,
        symbol: 'circle',
        symbolSize: 5,
        data: s.data,
        lineStyle: { width: 2, color: this.resolveColor(s.color) },
        itemStyle: { color: this.resolveColor(s.color) },
        // Ligne repère attachée à une seule série (sinon dessinée N fois) ; couleur primaire pointillée.
        ...(markLineValue != null && i === 0
          ? {
              markLine: {
                silent: true,
                symbol: 'none',
                label: { show: false },
                lineStyle: {
                  color: this.cssVar('--app-primary-action'),
                  type: 'dashed',
                  width: 2,
                  opacity: 0.85,
                },
                data: [{ yAxis: markLineValue }],
              },
            }
          : {}),
      })),
    };
  }
}
