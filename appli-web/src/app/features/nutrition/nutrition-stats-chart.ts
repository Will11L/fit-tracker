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
import { BarChart, type BarSeriesOption, LineChart, type LineSeriesOption } from 'echarts/charts';
import {
  GridComponent,
  type GridComponentOption,
  TooltipComponent,
  type TooltipComponentOption,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ComposeOption } from 'echarts/core';
import { themedAxisTooltip } from '@designsystem/common_components/chart-tooltip';

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

type ChartOption = ComposeOption<
  LineSeriesOption | BarSeriesOption | GridComponentOption | TooltipComponentOption
>;

/**
 * Graphe d'une macro de la page Stats Nutrition : 1 série « consommé » (barres ou courbe, couleur
 * de la macro via la convention --macro-*) + 1 courbe de cible optionnelle (pointillés) — kcal/macros
 * par jour ou semaine, comparaison à la cible active. Buckets X 'YYYY-MM-DD' → 'J/M' (DAILY) ou
 * 'YYYY-WW' → 'W##' (WEEKLY). Réutilise les conventions echarts des Stats sport.
 */
@Component({
  selector: 'app-nutrition-stats-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="nsc" [style.height.px]="height()">
      @if (emptyMessage(); as msg) {
        <div class="nsc__empty">{{ msg }}</div>
      } @else {
        <div #chart class="nsc__chart"></div>
      }
    </div>
  `,
  styles: [
    `
      .nsc {
        width: 100%;
        box-sizing: border-box;
        border-radius: var(--radius-md);
        background: var(--app-bg-recessed);
        padding: var(--space-3);
      }
      .nsc__empty {
        width: 100%;
        height: 100%;
        box-sizing: border-box;
        display: flex;
        align-items: center;
        justify-content: center;
        text-align: center;
        white-space: pre-line;
        border: 1.5px solid var(--app-primary-action);
        border-radius: var(--radius-md);
        color: var(--app-primary-action);
        font-size: var(--font-size-body);
      }
      .nsc__chart {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class NutritionStatsChart {
  readonly buckets = input<string[]>([]);
  /** Valeurs consommées alignées sur buckets. */
  readonly consumed = input<number[]>([]);
  /** Valeurs de cible alignées sur buckets (vide = pas de courbe de cible, ex. fibres). */
  readonly target = input<number[]>([]);
  /** Couleur de la macro (token CSS). */
  readonly color = input<string>('var(--app-primary-action)');
  readonly chartType = input<'BAR' | 'LINE'>('BAR');
  readonly granularity = input<'DAILY' | 'WEEKLY'>('WEEKLY');
  readonly unit = input<string>('');
  readonly height = input(300);

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  protected readonly emptyMessage = (): string | null => {
    if (this.buckets().length === 0) return 'Aucune donnée sur la période.';
    if (this.consumed().every((v) => v === 0)) return 'Aucun aliment saisi sur la période.';
    return null;
  };

  constructor() {
    effect(() => {
      const el = this.chartEl();
      if (this.emptyMessage() !== null || !el) {
        this.teardown();
        return;
      }
      if (!this.chart) {
        this.chart = echarts.init(el.nativeElement);
        this.resizeObs = new ResizeObserver(() => this.chart?.resize());
        this.resizeObs.observe(el.nativeElement);
      }
      this.chart.setOption(this.option(), true);
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

  /** Résout un token CSS (var(--x)) en couleur effective pour echarts (canvas n'évalue pas les vars). */
  private resolve(token: string): string {
    const t = token.trim();
    const m = /^var\((--[\w-]+)\)$/.exec(t);
    return m ? this.cssVar(m[1]) : t;
  }

  private formatBucket(bucket: string): string {
    if (this.granularity() === 'DAILY') {
      const [, m, d] = bucket.split('-');
      return m && d ? `${Number(d)}/${Number(m)}` : bucket;
    }
    const week = Number(bucket.slice(bucket.lastIndexOf('-') + 1));
    return Number.isNaN(week) ? bucket : `W${week}`;
  }

  private option(): ChartOption {
    const axisColor = this.cssVar('--app-divider');
    const color = this.resolve(this.color());
    const hasTarget = this.target().length > 0 && this.target().some((v) => v > 0);
    const u = this.unit();

    const consumedSeries: LineSeriesOption | BarSeriesOption =
      this.chartType() === 'BAR'
        ? {
            name: 'Consommé',
            type: 'bar',
            barWidth: '55%',
            data: this.consumed().map((v) => Math.round(v * 10) / 10),
            itemStyle: { color, borderRadius: [4, 4, 0, 0] },
          }
        : {
            name: 'Consommé',
            type: 'line',
            smooth: 0.4,
            symbol: 'circle',
            symbolSize: 5,
            data: this.consumed().map((v) => Math.round(v * 10) / 10),
            lineStyle: { width: 2.5, color },
            itemStyle: { color },
            areaStyle: { color, opacity: 0.12 },
          };

    const series: (LineSeriesOption | BarSeriesOption)[] = [consumedSeries];
    if (hasTarget) {
      series.push({
        name: 'Cible',
        type: 'line',
        smooth: false,
        symbol: 'none',
        data: this.target().map((v) => Math.round(v * 10) / 10),
        lineStyle: { width: 2, type: 'dashed', color: this.cssVar('--app-text-secondary') },
        itemStyle: { color: this.cssVar('--app-text-secondary') },
      });
    }

    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 12, top: 22, bottom: 4, containLabel: true },
      tooltip: themedAxisTooltip(u ? ` ${u}` : ''),
      xAxis: {
        type: 'category',
        data: this.buckets().map((b) => this.formatBucket(b)),
        boundaryGap: this.chartType() === 'BAR',
        axisLabel: {
          color: axisColor,
          fontSize: 10,
          interval: 'auto',
          rotate: this.buckets().length > 10 ? 45 : 0,
        },
        axisLine: { lineStyle: { color: axisColor } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLabel: { color: axisColor, fontSize: 11 },
        axisLine: { show: true, lineStyle: { color: axisColor } },
        splitLine: { lineStyle: { color: axisColor, type: 'dashed', width: 0.5, opacity: 0.5 } },
      },
      series,
    };
  }
}
