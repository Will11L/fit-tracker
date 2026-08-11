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
import { BarChart, type BarSeriesOption } from 'echarts/charts';
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

echarts.use([BarChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer]);

type ChartOption = ComposeOption<
  BarSeriesOption | GridComponentOption | TooltipComponentOption | MarkLineComponentOption
>;

/** Une barre du graphe d'objectifs : label + % de réalisation (cap-free) + couleur + SKIPPED. */
export interface GoalBar {
  label: string;
  value: number;
  color: string;
  skipped: boolean;
}

/**
 * Graphe de réalisation des objectifs — miroir de GoalsAchievementChart.kt (refonte 2026-05-09) :
 * 1 barre par muscle/groupe/zone, hauteur = % d'achievement cap-free (peut dépasser 100), couleur
 * par barre (palette par zone, miroir paletteForZone), barres SKIPPED en alpha 0.4, ligne
 * pointillée 100 % (couleur primaire) toujours visible (échelle Y = max(100, rawMax) + 5 % de
 * headroom). Rendu ECharts (init impératif + ResizeObserver + tokens CSS résolus).
 */
@Component({
  selector: 'app-goals-achievement-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="gac__box">
      <div #chart class="gac"></div>
    </div>
  `,
  styles: [
    `
      /* Container foncé arrondi (miroir Android : Box bgRecessed, coins 8dp, padding interne). */
      .gac__box {
        height: 250px;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 12px 12px 8px 4px;
      }
      .gac {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class GoalsAchievementChart {
  readonly data = input<GoalBar[]>([]);

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      const data = this.data();
      const el = this.chartEl();
      if (!el) return;
      if (!this.chart) {
        this.chart = echarts.init(el.nativeElement);
        this.resizeObs = new ResizeObserver(() => this.chart?.resize());
        this.resizeObs.observe(el.nativeElement);
      }
      this.chart.setOption(this.buildOption(data), true);
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

  /** #rrggbb → rgba(.., alpha) pour les barres SKIPPED (alpha 0.4, miroir Android). */
  private withAlpha(hex: string, alpha: number): string {
    const h = hex.replace('#', '');
    if (h.length !== 6) return hex;
    const r = parseInt(h.slice(0, 2), 16);
    const g = parseInt(h.slice(2, 4), 16);
    const b = parseInt(h.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }

  private buildOption(data: GoalBar[]): ChartOption {
    const text = this.cssVar('--app-text-tertiary');
    const split = this.cssVar('--app-divider');
    const primary = this.cssVar('--app-primary-action');
    // Échelle Y : max(100, max bar) + 5 % de headroom — la ligne 100 % reste toujours visible.
    const rawMax = Math.max(100, ...data.map((d) => d.value));
    const yMax = Math.ceil(rawMax * 1.05);
    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 12, top: 16, bottom: 4, containLabel: true },
      tooltip: themedAxisTooltip(' %'),
      xAxis: {
        type: 'category',
        data: data.map((d) => d.label),
        axisLabel: {
          fontSize: 10,
          interval: 0,
          rotate: data.length > 6 ? 35 : 0,
          // Label X coloré comme sa barre (miroir Android, SKIPPED atténué).
          color: (_value?: string | number, index?: number): string => {
            const bar = index !== undefined ? data[index] : undefined;
            if (!bar) return text;
            return bar.skipped ? this.withAlpha(bar.color, 0.5) : bar.color;
          },
        },
        axisLine: { lineStyle: { color: split } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: yMax,
        axisLabel: { color: text, fontSize: 11, formatter: '{value}%' },
        splitLine: { lineStyle: { color: split, type: 'dashed', opacity: 0.4 } },
      },
      series: [
        {
          type: 'bar',
          data: data.map((d) => ({
            value: d.value,
            itemStyle: {
              color: d.skipped ? this.withAlpha(d.color, 0.4) : d.color,
              borderRadius: [4, 4, 0, 0],
            },
          })),
          barMaxWidth: 28,
          // Ligne 100 % target (spécifique Goals) : pointillés marqués, couleur primaire.
          markLine: {
            silent: true,
            symbol: 'none',
            label: { show: false },
            lineStyle: { color: primary, type: 'dashed', width: 2, opacity: 0.85 },
            data: [{ yAxis: 100 }],
          },
        },
      ],
    };
  }
}
