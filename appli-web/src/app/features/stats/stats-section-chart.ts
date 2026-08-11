import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  viewChild,
} from '@angular/core';
import * as echarts from 'echarts/core';
import {
  BarChart,
  type BarSeriesOption,
  LineChart,
  type LineSeriesOption,
} from 'echarts/charts';
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

/** Une série d'une section Stats : nom, valeurs alignées sur les buckets, couleur résolue (hex). */
export interface StatsSeries {
  name: string;
  data: number[];
  color: string;
}

/**
 * Chart d'une section Stats — miroir de MuscleGroupVolumeChart.kt :
 * - LINE : multi-courbes lissées, 1 courbe par série, axe X = buckets (jours 'J/M' ou semaines 'W##').
 * - BAR  : 1 colonne par série = cumul total sur la période (coins arrondis en haut, labels X colorés).
 * Pas de légende intégrée : les filter chips sous le chart en font office (mêmes couleurs).
 * États vides fidèles Android : aucune donnée / pas assez de points (≤ 1 bucket).
 */
@Component({
  selector: 'app-stats-section-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ssc" [style.height.px]="height()">
      @if (emptyMessage(); as msg) {
        <div class="ssc__empty">{{ msg }}</div>
      } @else {
        <div #chart class="ssc__chart"></div>
      }
    </div>
  `,
  styles: [
    `
      .ssc {
        width: 100%;
        box-sizing: border-box;
        border-radius: var(--radius-md);
        background: var(--app-bg-recessed);
        padding: var(--space-3);
      }
      .ssc__empty {
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
      .ssc__chart {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class StatsSectionChart {
  readonly buckets = input<string[]>([]);
  readonly series = input<StatsSeries[]>([]);
  readonly chartType = input<'BAR' | 'LINE'>('BAR');
  readonly granularity = input<'DAILY' | 'WEEKLY'>('WEEKLY');
  readonly metric = input<'SETS' | 'EXERCISES' | 'TOTAL_WEIGHT'>('SETS');
  readonly height = input(300);

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  /** Miroir Android : empty si aucune série OU ≤ 1 bucket (pas de tendance traçable). */
  protected readonly emptyMessage = computed<string | null>(() => {
    if (this.series().length === 0) return 'Aucune donnée sur la période.';
    if (this.buckets().length <= 1)
      return 'Pas assez de points pour tracer une tendance.\nEssaie une période plus large.';
    return null;
  });

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
      const option =
        this.chartType() === 'BAR'
          ? this.barOption(this.series())
          : this.lineOption(this.series(), this.buckets(), this.granularity());
      this.chart.setOption(option, true);
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

  /** Format des valeurs Y selon la métrique : kg avec suffixe k/M, sinon entier. */
  private formatMetric(value: number): string {
    if (this.metric() !== 'TOTAL_WEIGHT') return `${Math.round(value)}`;
    if (value <= 0) return '0';
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
    if (value >= 10_000) return `${Math.round(value / 1_000)}k`;
    if (value >= 1_000) return `${(value / 1_000).toFixed(1)}k`;
    return `${Math.round(value)}`;
  }

  /** Unité affichée dans le tooltip selon la métrique (kg / exercices / séries). */
  private metricUnit(): string {
    return this.metric() === 'TOTAL_WEIGHT'
      ? ' kg'
      : this.metric() === 'EXERCISES'
        ? ' exercices'
        : ' séries';
  }

  /** Labels X fidèles Android : DAILY 'YYYY-MM-DD' → 'J/M', WEEKLY 'YYYY-WW' → 'W##'. */
  private formatBucket(bucket: string, gran: 'DAILY' | 'WEEKLY'): string {
    if (gran === 'DAILY') {
      const [, m, d] = bucket.split('-');
      return m && d ? `${Number(d)}/${Number(m)}` : bucket;
    }
    const week = Number(bucket.slice(bucket.lastIndexOf('-') + 1));
    return Number.isNaN(week) ? bucket : `W${week}`;
  }

  private lineOption(series: StatsSeries[], buckets: string[], gran: 'DAILY' | 'WEEKLY'): ChartOption {
    const axisColor = this.cssVar('--app-divider');
    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 12, top: 12, bottom: 4, containLabel: true },
      tooltip: themedAxisTooltip(this.metricUnit()),
      xAxis: {
        type: 'category',
        data: buckets.map((b) => this.formatBucket(b, gran)),
        boundaryGap: false,
        axisLabel: { color: axisColor, fontSize: 11 },
        axisLine: { lineStyle: { color: axisColor } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLabel: { color: axisColor, fontSize: 11, formatter: (v: number) => this.formatMetric(v) },
        axisLine: { show: true, lineStyle: { color: axisColor } },
        splitLine: { lineStyle: { color: axisColor, type: 'dashed', width: 0.5, opacity: 0.5 } },
      },
      series: series.map((s) => ({
        name: s.name,
        type: 'line' as const,
        smooth: 0.5,
        symbol: 'circle',
        symbolSize: 5,
        data: s.data,
        lineStyle: { width: 2.5, color: s.color },
        itemStyle: { color: s.color },
      })),
    };
  }

  /** BAR = cumul total par série sur la période (1 colonne par série, miroir BarChartBox). */
  private barOption(series: StatsSeries[]): ChartOption {
    const axisColor = this.cssVar('--app-divider');
    const cumuls = series.map((s) => ({
      name: s.name,
      value: Math.round(s.data.reduce((a, b) => a + b, 0) * 10) / 10,
      color: s.color,
    }));
    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 12, top: 12, bottom: 4, containLabel: true },
      tooltip: themedAxisTooltip(this.metricUnit()),
      xAxis: {
        type: 'category',
        data: cumuls.map((c) => c.name),
        axisLabel: {
          fontSize: 10,
          interval: 0,
          rotate: cumuls.length > 8 ? 45 : 0,
          color: (_value?: string | number, index?: number) => cumuls[index ?? 0]?.color ?? axisColor,
        },
        axisLine: { lineStyle: { color: axisColor } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLabel: { color: axisColor, fontSize: 11, formatter: (v: number) => this.formatMetric(v) },
        axisLine: { show: true, lineStyle: { color: axisColor } },
        splitLine: { lineStyle: { color: axisColor, type: 'dashed', width: 0.5, opacity: 0.5 } },
      },
      series: [
        {
          type: 'bar' as const,
          barWidth: '55%',
          data: cumuls.map((c) => ({
            value: c.value,
            itemStyle: { color: c.color, borderRadius: [5, 5, 0, 0] },
          })),
        },
      ],
    };
  }
}
