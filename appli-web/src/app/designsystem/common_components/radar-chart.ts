import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import * as echarts from 'echarts/core';
import { RadarChart, type RadarSeriesOption } from 'echarts/charts';
import {
  RadarComponent,
  type RadarComponentOption,
  TooltipComponent,
  type TooltipComponentOption,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ComposeOption } from 'echarts/core';

// Le système de coordonnées radar (RadarComponent) est un install distinct de la série radar
// (RadarChart) en ECharts modulaire — les 2 sont requis. La légende est rendue en HTML (sous le
// graphe), pas via le LegendComponent ECharts → pas besoin de l'installer.
echarts.use([RadarChart, RadarComponent, TooltipComponent, CanvasRenderer]);

type ChartOption = ComposeOption<
  RadarSeriesOption | RadarComponentOption | TooltipComponentOption
>;

/** Un axe du radar : libellé + max optionnel (retombe sur le max global des séries si absent). */
export interface RadarAxis {
  label: string;
  max?: number;
  /**
   * Couleur du nom de l'axe (token CSS `var(...)` ou hex), mappée vers `indicator[].color`.
   * Optionnel + rétro-compatible : sans couleur, l'axe garde la couleur de nom par défaut.
   */
  color?: string;
}

/** Une série du radar : nom (légende), valeurs alignées sur les axes, couleur, remplissage on/off. */
export interface RadarSeries {
  name: string;
  values: number[];
  color: string;
  area?: boolean;
}

/** Dégradé radial ECharts (centre→bord) pour le remplissage de zone (objet natif, pas de canvas). */
interface RadarRadialGradient {
  type: 'radial';
  x: number;
  y: number;
  r: number;
  colorStops: { offset: number; color: string }[];
}

/**
 * Diagramme radar / Kiviat générique (design system) — composant réutilisable multi-séries pour
 * les macros nutrition ET les pages sport. Axes thémés, couleurs résolues via tokens CSS (rendu
 * propre dark + light), remplissage de zone optionnel par série. Rendu ECharts modulaire (init
 * impératif + ResizeObserver + cleanup DestroyRef), même pattern que GoalsAchievementChart /
 * MultiLineChart.
 */
@Component({
  selector: 'app-radar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rc" [class.rc--fill]="fill()" [style.height.px]="fill() ? null : height()">
      @if (axes().length === 0 || series().length === 0) {
        <div class="rc__empty">{{ emptyText() }}</div>
      } @else {
        <div #chart class="rc__chart"></div>
        @if (showLegend()) {
          <!-- Légende cliquable SOUS le graphe : tap → masque/affiche la série. Désélectionnée =
               pastille bordée à fond transparent + texte gris-bleu ; sélectionnée = texte couleur série. -->
          <div class="rc__legend">
            @for (item of legendItems(); track item.name) {
              <button type="button" class="rc__legend-item" (click)="toggleSeries(item.name)">
                <span
                  class="rc__legend-swatch"
                  [style.border-color]="item.color"
                  [style.background]="item.selected ? item.color : 'transparent'"
                ></span>
                <span
                  class="rc__legend-text"
                  [style.color]="item.selected ? item.color : 'var(--c-gray-blue)'"
                  >{{ item.name }}</span
                >
              </button>
            }
          </div>
        }
      }
    </div>
  `,
  styles: [
    `
      .rc {
        width: 100%;
        box-sizing: border-box;
        border-radius: var(--radius-md);
        background: var(--app-bg-recessed);
        padding: var(--space-3);
        display: flex;
        flex-direction: column;
      }
      /* Mode « fill » : remplit la hauteur du parent flex (au lieu d'une hauteur px fixe). */
      .rc--fill {
        flex: 1;
        min-height: 0;
      }
      .rc__empty {
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
      .rc__chart {
        flex: 1 1 auto;
        min-height: 0;
        width: 100%;
      }
      /* Légende cliquable sous le graphe (rangée centrée, wrap si étroit). */
      .rc__legend {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        align-items: center;
        gap: var(--space-1) var(--space-3);
        padding-top: var(--space-2);
      }
      .rc__legend-item {
        display: inline-flex;
        align-items: center;
        gap: var(--space-1);
        background: transparent;
        border: none;
        padding: 2px 4px;
        cursor: pointer;
        font-family: var(--font-family-base);
        font-size: 12px;
        line-height: 1;
      }
      /* Pastille : bordure toujours à la couleur de la série ; fond plein si sélectionnée, transparent sinon. */
      .rc__legend-swatch {
        width: 12px;
        height: 12px;
        border-radius: 3px;
        border: 1.5px solid;
        box-sizing: border-box;
        flex: none;
      }
      .rc__legend-text {
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class RadarChartComponent {
  readonly axes = input<RadarAxis[]>([]);
  readonly series = input<RadarSeries[]>([]);
  readonly height = input(280);
  /** Rayon du radar (echarts radar.radius). null = défaut interne (58% avec libellés %, 68% sinon).
   *  L'augmenter (ex. '82%') fait remplir davantage la boîte → moins de blanc autour (haut/bas). */
  readonly radius = input<string | null>(null);
  /** Remplit la hauteur du parent flex (parent = flex column) au lieu de `height` px fixe. Off par défaut. */
  readonly fill = input(false);
  /** Légende ECharts (1 entrée par série). On par défaut : utile dès qu'il y a plusieurs séries. */
  readonly showLegend = input(true);
  /**
   * Remplissage des séries `area:true` en dégradé radial centre→bord (défaut on). Off → aplat
   * translucide rétro-compatible. La couleur du polygone (ligne) reste mono-couleur dans tous les cas.
   */
  readonly areaGradient = input(true);
  /** Ajoute « Nom X % » sur les axes → la légende devient inutile. */
  readonly showAxisPercent = input(false);
  /** 'auto' : mono-série = part du total, multi-séries = valeur (déjà un %). 'value' : toujours la valeur
   *  (radar dont les valeurs SONT déjà des %, ex. couverture micros). */
  readonly axisPercentMode = input<'auto' | 'value'>('auto');
  /** Unité affichée après chaque valeur dans le tooltip (ex. ' kcal', ' g'). Vide → auto : ' %' si les
   *  valeurs SONT déjà des pourcentages (mode 'value' ou ≥2 séries), sinon rien. */
  readonly valueSuffix = input('');
  readonly emptyText = input('No data');

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;

  /** Séries masquées via la légende (par nom). Filtrées du tracé ; l'échelle reste calculée sur tout. */
  private readonly deselected = signal<ReadonlySet<string>>(new Set());

  /**
   * Entrées de la légende HTML cliquable : 1 par série, couleur en token brut (résolu par le
   * navigateur via [style], pas par le composant) + état sélectionné (= série visible).
   */
  protected readonly legendItems = computed(() => {
    const des = this.deselected();
    return this.series().map((s) => ({ name: s.name, color: s.color, selected: !des.has(s.name) }));
  });

  /** Tap sur une entrée de légende → masque/affiche la série (re-render du radar via l'effect). */
  protected toggleSeries(name: string): void {
    const next = new Set(this.deselected());
    next.has(name) ? next.delete(name) : next.add(name);
    this.deselected.set(next);
  }

  constructor() {
    effect(() => {
      const axes = this.axes();
      const series = this.series();
      const el = this.chartEl();
      if (axes.length === 0 || series.length === 0 || !el) {
        this.teardown();
        return;
      }
      const areaGradient = this.areaGradient();
      const deselected = this.deselected();
      const showAxisPercent = this.showAxisPercent();
      const axisPercentMode = this.axisPercentMode();
      if (!this.chart) {
        this.chart = echarts.init(el.nativeElement);
        this.resizeObs = new ResizeObserver(() => this.chart?.resize());
        this.resizeObs.observe(el.nativeElement);
      }
      this.chart.setOption(
        this.buildOption(axes, series, areaGradient, deselected, showAxisPercent, axisPercentMode),
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

  /** Résout une chaîne de tokens imbriqués (ex. var(--macro-protein) -> var(--c-light-purple) -> #6c2ae7). */
  private resolveColor(css: string): string {
    let v = css.trim();
    let guard = 0;
    while (v.startsWith('var(') && guard++ < 10) {
      const m = v.match(/var\((--[\w-]+)\)/);
      if (!m) break;
      v = this.cssVar(m[1]);
    }
    return v;
  }

  /** #rrggbb -> rgba(.., alpha) pour le remplissage de zone (area). */
  private withAlpha(hex: string, alpha: number): string {
    const h = hex.replace('#', '');
    if (h.length !== 6) return hex;
    const r = parseInt(h.slice(0, 2), 16);
    const g = parseInt(h.slice(2, 4), 16);
    const b = parseInt(h.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }

  /**
   * Couleur de remplissage d'une série `area:true`, mono-couleur. Défaut : dégradé RADIAL natif
   * ECharts centre→bord — plus transparent au centre (alpha ~0.05) → plus opaque vers les bords
   * (alpha ~0.35). `gradient=false` → aplat translucide (alpha 0.25) rétro-compatible.
   */
  private areaFill(color: string, gradient: boolean): string | RadarRadialGradient {
    if (!gradient) return this.withAlpha(color, 0.25);
    return {
      type: 'radial',
      x: 0.5,
      y: 0.5,
      r: 0.5,
      colorStops: [
        { offset: 0, color: this.withAlpha(color, 0.05) },
        { offset: 1, color: this.withAlpha(color, 0.35) },
      ],
    };
  }

  /**
   * Lignes du nom d'axe, ADAPTATIF selon la place disponible (demande user 2026-07-15) :
   * — axes des CÔTÉS (place verticale sous le libellé) → % sur sa propre ligne DESSOUS,
   *   nom découpé un mot par ligne s'il est long (connecteurs « & » collés au mot précédent) ;
   * — axes HAUT/BAS (le cadre coupe juste au-dessus/en dessous) → tout sur UNE ligne (% à droite).
   * `horizontalFactor` = |cos(angle)| de l'axe (0 = haut/bas, 1 = gauche/droite).
   */
  private axisNameLines(label: string, pct: number | null, horizontalFactor: number): string[] {
    // Haut/bas : pas de place verticale → une seule ligne « Nom X % ».
    if (horizontalFactor < 0.3) return [pct === null ? label : `${label} ${pct} %`];
    const lines: string[] = [];
    if (label.length <= 15) {
      lines.push(label);
    } else {
      for (const w of label.split(/\s+/).filter(Boolean)) {
        if (w.length <= 1 && lines.length) lines[lines.length - 1] += ` ${w}`;
        else lines.push(w);
      }
    }
    if (pct !== null) lines.push(`${pct} %`);
    return lines;
  }

  /** Largeur (px) d'une ligne au font des noms d'axes (11px) — pour centrer les lignes entre elles. */
  private measureCtx: CanvasRenderingContext2D | null = null;
  private lineWidth(text: string): number {
    if (!this.measureCtx) this.measureCtx = document.createElement('canvas').getContext('2d');
    if (!this.measureCtx) return 0;
    this.measureCtx.font = 'normal 11px sans-serif';
    return this.measureCtx.measureText(text).width;
  }

  private buildOption(
    axes: RadarAxis[],
    series: RadarSeries[],
    areaGradient: boolean,
    deselected: ReadonlySet<string>,
    showAxisPercent: boolean,
    axisPercentMode: 'auto' | 'value',
  ): ChartOption {
    const axisName = this.cssVar('--app-text-tertiary');
    const split = this.cssVar('--app-divider');
    // Échelle partagée : les axes sans max explicite retombent sur le max global des séries
    // (+5 % de marge) -> tous les axes restent comparables. Calculé sur TOUTES les séries (même
    // masquées) → l'échelle ne saute pas quand on masque une série via la légende.
    const allValues = series.flatMap((s) => s.values);
    const globalMax = Math.max(1, Math.ceil(Math.max(0, ...allValues) * 1.05));
    // Légende externe (HTML, sous le graphe) : pas de composant legend ECharts. Les séries masquées
    // sont filtrées du tracé (le polygone disparaît) ; le radar occupe tout le canvas (centre 50/50).
    const shown = series.filter((s) => !deselected.has(s.name));
    // Étiquette « Nom X % » par axe. 'value' OU ≥2 séries → la valeur EST déjà un % (couverture / % de la
    // cible) → on l'affiche ; sinon (mono-série 'auto') → part du total (valeur / somme).
    const pctBase = showAxisPercent && series.length > 0 ? series[0] : null;
    const useValue = axisPercentMode === 'value' || series.length >= 2;
    // Unité du tooltip : suffixe explicite si fourni, sinon ' %' quand les valeurs sont déjà des % (useValue).
    const valueUnit = this.valueSuffix() || (useValue ? ' %' : '');
    const pctSum = pctBase && !useValue ? pctBase.values.reduce((acc, v) => acc + Math.max(0, v), 0) : 0;
    // Styles rich par axe (blocs multi-lignes centrés) — rempli en construisant les indicators.
    const richStyles: Record<string, object> = {};
    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        // Tooltip stylé app : fond thirdBlue, bordure first-blue, coins --radius-md ; chaque ligne d'axe
        // (Calories, Glucides…) dans sa couleur d'axe (repli = couleur de la série), valeurs à 1 décimale.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- params echarts (union TopLevelFormatterParams)
        formatter: (p: any) => {
          const vals: number[] = Array.isArray(p.value) ? p.value : [];
          const r1 = (v: number) => Math.round(v * 10) / 10;
          // Ligne : libellé à gauche, valeur à droite (space-between) → remplit la largeur (boîte ≥ carrée).
          const rows = axes
            .map((a, i) => {
              const c = a.color ? this.resolveColor(a.color) : (p.color ?? 'inherit');
              return `<div style="display:flex;justify-content:space-between;gap:14px;color:${c};padding:2px 0">` +
                `<span>${a.label}</span><span style="font-weight:700">${r1(vals[i] ?? 0)}${valueUnit}</span></div>`;
            })
            .join('');
          // En-tête = titled-divider gris-bleu (titre centré entre deux filets) reproduit en HTML.
          const head = p.name
            ? `<div style="display:flex;align-items:center;gap:6px;margin-bottom:5px;color:var(--c-gray-blue);font-weight:600">` +
              `<span style="flex:1;height:1px;background:var(--c-gray-blue);opacity:.6"></span>` +
              `<span>${p.name}</span>` +
              `<span style="flex:1;height:1px;background:var(--c-gray-blue);opacity:.6"></span></div>`
            : '';
          return head + rows;
        },
        backgroundColor: this.cssVar('--app-bg-recessed'),
        borderColor: this.cssVar('--c-first-blue'),
        borderWidth: 1,
        padding: [8, 12],
        textStyle: { color: this.cssVar('--app-text-primary'), fontSize: 12 },
        extraCssText: `border-radius: ${this.cssVar('--radius-md')}; min-width: 160px;`,
      },
      radar: {
        center: ['50%', '50%'],
        // Rayon réduit quand les axes portent « Nom X % » (libellés plus longs → plus de marge).
        radius: this.radius() ?? (showAxisPercent ? '58%' : '68%'),
        indicator: axes.map((a, i) => {
          const v = pctBase ? Math.max(0, pctBase.values[i] ?? 0) : 0;
          const pct =
            !pctBase ? null : useValue ? Math.round(v) : pctSum > 0 ? Math.round((v / pctSum) * 100) : null;
          // |cos(angle)| de l'axe : 0 = haut/bas (place horizontale large), 1 = gauche/droite (étroit).
          // Top = 90°, axes répartis tous les 360/N° ; |cos| symétrique → indépendant du sens ECharts.
          const horiz = Math.abs(Math.cos(((90 + (i * 360) / axes.length) * Math.PI) / 180));
          const lines = this.axisNameLines(a.label, pct, horiz);
          // Multi-lignes : lignes CENTRÉES entre elles via un bloc rich par axe (largeur = ligne
          // la plus large, align center) — le % tombe centré sous l'intitulé.
          const name =
            lines.length > 1 ? lines.map((l) => `{n${i}|${l}}`).join('\n') : lines[0];
          if (lines.length > 1) {
            richStyles[`n${i}`] = {
              width: Math.ceil(Math.max(...lines.map((l) => this.lineWidth(l)))),
              align: 'center',
              fontSize: 11,
              lineHeight: 13,
              ...(a.color ? { color: this.resolveColor(a.color) } : { color: axisName }),
            };
          }
          return {
            name,
            max: a.max ?? globalMax,
            // Couleur par axe optionnelle (résolue depuis un token CSS) → nom d'axe coloré.
            ...(a.color ? { color: this.resolveColor(a.color) } : {}),
          };
        }),
        shape: 'polygon',
        splitNumber: 4,
        axisName: { color: axisName, fontSize: 11, lineHeight: 13, rich: richStyles },
        axisLine: { lineStyle: { color: split, opacity: 0.6 } },
        splitLine: { lineStyle: { color: split, opacity: 0.4 } },
        splitArea: { show: false },
      },
      series: [
        {
          type: 'radar',
          data: shown.map((s) => {
            const color = this.resolveColor(s.color);
            return {
              name: s.name,
              value: s.values,
              symbol: 'circle',
              symbolSize: 4,
              lineStyle: { color, width: 2 },
              itemStyle: { color },
              ...(s.area ? { areaStyle: { color: this.areaFill(color, areaGradient) } } : {}),
            };
          }),
        },
      ],
    };
  }
}
