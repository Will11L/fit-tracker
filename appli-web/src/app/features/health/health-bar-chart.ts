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
import { SLOT_MINUTES } from './health-aggregations';

echarts.use([BarChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer]);

type ChartOption = ComposeOption<
  BarSeriesOption | GridComponentOption | TooltipComponentOption | MarkLineComponentOption
>;

/**
 * Chart en barres de la section Santé — miroir de `HealthBarChart.kt` (Android). Deux modes :
 * - INTRADAY : 48 tranches de 30 min, barres fines arrondies, repères d'axe aux 6 h (0/6/12/18 h),
 *   tranches futures non affichées (valeurs déjà clippées à 0 par l'appelant).
 * - WEEK : une barre par jour (quantième sous chaque barre), axe masqué, avec une ligne pointillée de
 *   moyenne des jours renseignés par-dessus les barres.
 * Rendu ECharts (init impératif + ResizeObserver + tokens CSS résolus), fond transparent : le quadrant
 * thirdBlue de la page fait le fond.
 * Hauteur par défaut = ratio effectif du bloc Android (HealthBarChart.kt est en hauteur FIXE
 * 110 dp de barres + axe + labels ≈ 134 dp pour ≈ 320 dp utiles sur device → 0,42 × largeur) ;
 * [height] px non-null force une hauteur fixe (vues WEEK actuelles).
 */
@Component({
  selector: 'app-health-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div
    class="hbc"
    [class.hbc--fill]="fill()"
    [style.height]="!fill() && height() !== null ? height() + 'px' : null"
    [style.aspect-ratio]="!fill() && height() === null ? '1 / 0.42' : null"
  >
    <div #chart class="hbc__canvas"></div>
  </div>`,
  styles: [
    `
      :host {
        display: block;
        position: relative;
      }
      .hbc {
        width: 100%;
      }
      /* Mode fill : ABSOLU dans son slot — le canvas absorbe la hauteur disponible
         sans y CONTRIBUER (sinon boucle : canvas → hauteur de rangée subgrid auto →
         100 % plus grand → resize → croissance infinie de la page). */
      .hbc--fill {
        position: absolute;
        inset: 0;
        width: auto;
      }
      .hbc__canvas {
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class HealthBarChart {
  readonly values = input<number[]>([]);
  readonly mode = input<'INTRADAY' | 'WEEK'>('WEEK');
  /** WEEK : libellés sous chaque barre (quantième du jour). Ignoré en INTRADAY (axe horaire calculé). */
  readonly labels = input<string[]>([]);
  /** Couleur des barres (identité de la métrique : vert pas / orange FC / bleu sommeil…). */
  readonly color = input('var(--c-light-green)');
  /** WEEK : valeur de la ligne pointillée de moyenne (null = pas de ligne). */
  readonly average = input<number | null>(null);
  /** Suffixe d'unité des tooltips (ex. ' pas', ' bpm', ' min'). */
  readonly valueSuffix = input('');
  /** Intitulé de la série simple dans le tooltip (ex. « Pas ») ; vide = valeur seule. */
  readonly seriesName = input('');
  /** Hauteur fixe en px ; null (défaut) = ratio Android effectif (hauteur = 0,42 × largeur). */
  readonly height = input<number | null>(null);
  /** true = remplit la hauteur DISPONIBLE de son conteneur (cards aux rangées égalisées
   *  par subgrid : le chart absorbe l'espace restant) — prime sur [height]/ratio. */
  readonly fill = input(false, { transform: booleanAttribute });
  /** Mode empilé optionnel (miroir `stackedValues` Android) : par slot, un segment par entrée
   *  de [stackColors]/[stackLabels] (ex. phases de sommeil). Non-null → prime sur [values]/[color]. */
  readonly stackedValues = input<number[][] | null>(null);
  readonly stackColors = input<string[]>([]);
  readonly stackLabels = input<string[]>([]);

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
        this.stackedValues(),
        this.stackColors(),
        this.stackLabels(),
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

  private buildOption(
    values: number[],
    mode: 'INTRADAY' | 'WEEK',
    labels: string[],
    color: string,
    average: number | null,
    valueSuffix: string,
    seriesName: string,
    stackedValues: number[][] | null,
    stackColors: string[],
    stackLabels: string[],
  ): ChartOption {
    const axis = this.cssVar('--app-divider'); // GrayBlue (ligne d'axe / ticks)
    const axisLabel = this.cssVar('--app-text-tertiary'); // clair : quantièmes/heures sous les barres (parité Android textTertiary)
    const barColor = this.resolveColor(color);
    const slotsPerHour = 60 / SLOT_MINUTES; // 2 tranches de 30 min par heure
    const isIntraday = mode === 'INTRADAY';
    const slotCount = stackedValues ? stackedValues.length : values.length;

    // INTRADAY : catégories = index de tranche ; label ("Xh") seulement aux repères de 6 h.
    // WEEK : une catégorie par jour (quantième fourni).
    const categories = isIntraday
      ? Array.from({ length: slotCount }, (_, i) => String(i))
      : labels;

    return {
      backgroundColor: 'transparent',
      grid: { left: 4, right: 8, top: 10, bottom: 4, containLabel: true },
      tooltip: themedAxisTooltip(valueSuffix),
      xAxis: {
        type: 'category',
        data: categories,
        axisLabel: isIntraday
          ? {
              color: axisLabel,
              fontSize: 10,
              interval: (index: number) => index % (6 * slotsPerHour) === 0,
              formatter: (_v: string, index: number) => `${index / slotsPerHour}h`,
            }
          : { color: axisLabel, fontSize: 10, interval: 0 },
        // INTRADAY : axe horaire visible (grayBlue) + petits traits sortants pointant
        // vers les repères horaires (miroir Android, tickLen 4). WEEK : axe masqué
        // (quantième seul, miroir Android), pas de ticks.
        axisLine: { lineStyle: { color: isIntraday ? axis : 'transparent' } },
        axisTick: isIntraday
          ? {
              show: true,
              alignWithLabel: true,
              length: 4,
              interval: (index: number) => index % (6 * slotsPerHour) === 0,
              lineStyle: { color: axis },
            }
          : { show: false },
      },
      yAxis: {
        type: 'value',
        min: 0,
        // Axe Y masqué (barres nues) : la valeur exacte se lit au survol (tooltip).
        show: false,
        splitLine: { show: false },
      },
      series: this.buildSeries(values, isIntraday, barColor, average, seriesName, stackedValues, stackColors, stackLabels),
    };
  }

  /** Séries : une barre simple, ou N segments empilés (mode [stackedValues], ex. phases de sommeil). */
  private buildSeries(
    values: number[],
    isIntraday: boolean,
    barColor: string,
    average: number | null,
    seriesName: string,
    stackedValues: number[][] | null,
    stackColors: string[],
    stackLabels: string[],
  ): BarSeriesOption[] {
    // Barre = ¼ de la bande de catégorie, INTRADAY comme WEEK (miroir Android :
    // `barWidth = slotWidth * 0.25f` pour tous les modes → 25 % barre / 75 % espace).
    const sizing = { barWidth: '25%' };
    // WEEK : ligne pointillée de moyenne des jours renseignés (lightGrayBlue, par-dessus les barres)
    // + sa valeur (juste le chiffre) juste au-dessus de la ligne, toujours calée à droite, même
    // teinte que la ligne. En mode empilé, rattachée à la dernière série (axe valeur partagé).
    const markLine =
      !isIntraday && average != null
        ? {
            markLine: {
              silent: true,
              symbol: 'none',
              label: {
                show: true,
                position: 'insideEndTop' as const,
                formatter: Math.round(average).toLocaleString('fr-FR'),
                color: this.cssVar('--c-light-gray-blue'),
                fontSize: 10,
              },
              lineStyle: { color: this.cssVar('--c-light-gray-blue'), type: 'dashed' as const, width: 1.5, opacity: 0.9 },
              data: [{ yAxis: average }],
            },
          }
        : {};

    if (!stackedValues) {
      return [
        {
          type: 'bar',
          name: seriesName,
          data: values,
          itemStyle: { color: barColor, borderRadius: [2, 2, 0, 0] },
          ...sizing,
          ...markLine,
        },
      ];
    }
    return stackColors.map((c, i) => ({
      type: 'bar',
      name: stackLabels[i] ?? '',
      stack: 'stages',
      data: stackedValues.map((row) => row[i] ?? 0),
      // Seule la dernière série arrondit le haut (barre d'un seul tenant, ~Android).
      itemStyle: {
        color: this.resolveColor(c),
        borderRadius: i === stackColors.length - 1 ? [2, 2, 0, 0] : [0, 0, 0, 0],
      },
      ...sizing,
      ...(i === stackColors.length - 1 ? markLine : {}),
    }));
  }
}
