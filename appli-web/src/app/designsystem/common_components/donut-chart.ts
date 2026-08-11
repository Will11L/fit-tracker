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
import { PieChart, type PieSeriesOption } from 'echarts/charts';
import { TooltipComponent, type TooltipComponentOption } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ComposeOption } from 'echarts/core';

echarts.use([PieChart, TooltipComponent, CanvasRenderer]);

type ChartOption = ComposeOption<PieSeriesOption | TooltipComponentOption>;

/** Une part du donut : libellé (tooltip), valeur (> 0 pour être tracée), couleur (token CSS ou hex). */
export interface DonutSlice {
  label: string;
  value: number;
  color: string;
}

/**
 * Donut / anneau de répartition générique (design system) — composant réutilisable rendu en ECharts
 * modulaire (PieChart), couleurs résolues via tokens CSS (rendu propre dark + light), libellé central
 * optionnel superposé en CSS. Même pattern de cycle de vie que RadarChartComponent /
 * NutritionStatsChart (init impératif + ResizeObserver + cleanup DestroyRef). La légende est laissée
 * à l'appelant (le donut reste sobre) : `tooltip` au survol + libellé central pour le total.
 */
@Component({
  selector: 'app-donut-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dc" [class.dc--fill]="fill()" [style.height.px]="fill() ? null : height()">
      @if (total() <= 0) {
        <div class="dc__empty">{{ emptyText() }}</div>
      } @else {
        <div #chart class="dc__chart"></div>
        @if (centerLabel()) {
          <div class="dc__center">
            <span
              class="dc__center-label"
              [style.color]="centerColor()"
              [style.font-size.px]="centerLabelFont()"
            >{{ centerLabel() }}</span>
            @if (centerSub()) {
              <span class="dc__center-sub" [style.font-size.px]="centerSubFont()">{{ centerSub() }}</span>
            }
          </div>
        }
      }
    </div>
  `,
  styles: [
    `
      .dc {
        position: relative;
        width: 100%;
        box-sizing: border-box;
        border-radius: var(--radius-md);
        background: var(--app-bg-recessed);
        padding: var(--space-3);
      }
      /* Mode « fill » : remplit la hauteur du parent flex (place verticale max pour répartir les
         libellés) au lieu d'une hauteur px fixe. Le host doit être une colonne flex à hauteur définie.
         Même principe que .rc--fill du radar. */
      .dc--fill {
        flex: 1;
        min-height: 0;
      }
      .dc__empty {
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
      .dc__chart {
        width: 100%;
        height: 100%;
      }
      .dc__center {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 2px;
        pointer-events: none;
      }
      .dc__center-label {
        color: var(--app-text-primary);
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        line-height: 1.1;
      }
      .dc__center-sub {
        color: var(--c-gray-blue);
        /* Même taille que l'unité « g/kg » des tuiles (12px), au lieu d'un % du diamètre. */
        font-size: 12px;
      }
    `,
  ],
})
export class DonutChartComponent {
  readonly slices = input<DonutSlice[]>([]);
  readonly height = input(220);
  /** Remplit la hauteur du parent flex (colonne flex à hauteur définie) au lieu de `height` px fixe → le
   *  canvas, et donc la place verticale pour répartir les libellés, prend toute la hauteur dispo. Off par
   *  défaut. Même pattern que RadarChartComponent.fill. */
  readonly fill = input(false);
  /** Texte central principal (ex. total). Vide = pas de libellé central. */
  readonly centerLabel = input('');
  /** Sous-texte central (ex. unité). */
  readonly centerSub = input('');
  /** Couleur du libellé central (le total). Défaut = bleu kcal (--macro-kcal) = look validé du donut
   *  « répartition des calories ». Un usage non-kcal (ex. comptage) peut surcharger. */
  readonly centerColor = input('var(--macro-kcal)');
  /** Facteur d'échelle du texte central (libellé + sous-texte). Défaut 1.21 = taille validée appliquée à
   *  TOUS les donuts (base + 2×10 %). Surcharger pour un cas particulier. */
  readonly centerScale = input(1.21);
  readonly emptyText = input('No data');
  /** Affiche le % de chaque part À CÔTÉ de la part (étiquettes + lignes de rappel). On par défaut. */
  readonly showSliceLabels = input(true);

  protected readonly total = computed(() => this.slices().reduce((s, x) => s + Math.max(0, x.value), 0));

  private readonly chartEl = viewChild<ElementRef<HTMLDivElement>>('chart');
  private chart: echarts.ECharts | null = null;
  private resizeObs: ResizeObserver | null = null;
  // Canvas hors-écran réutilisé pour MESURER la largeur réelle des libellés (→ marge exacte à réserver).
  private measureCtx: CanvasRenderingContext2D | null = null;
  // Largeur courante du canvas (px), tenue à jour par le ResizeObserver → le texte central peut suivre
  // la taille de l'anneau (qui dépend de la largeur) quand la fenêtre est redimensionnée.
  private readonly chartWidth = signal(0);

  // Texte central TOUJOURS proportionnel au RAYON de l'anneau (donc à la largeur) → il s'adapte à la
  // taille RÉELLE du donut (étroit → petit texte), pas à `height`. Sinon un donut grand en hauteur mais
  // étroit (ex. Stats : height=300 dans une colonne ~1/3) donnait un centre énorme qui écrasait l'anneau.
  // Fallback hauteur seulement avant la 1re mesure de largeur. `centerScale` ajuste (ex. +10 %).
  protected readonly centerLabelFont = computed(() => {
    const scale = this.centerScale();
    const w = this.chartWidth();
    if (w > 0) {
      return Math.round(this.computeRadius(w, this.showSliceLabels())[1] * 0.205 * scale);
    }
    return Math.round(this.height() * 0.085 * scale);
  });
  /** Sous-texte central (« kcal ») : 0,7× le libellé central (→ suit l'anneau) dès que la largeur est
   *  mesurée ; fallback 12px × échelle avant. */
  protected readonly centerSubFont = computed(() =>
    this.chartWidth() > 0
      ? Math.round(this.centerLabelFont() * 0.7)
      : Math.round(12 * this.centerScale()),
  );

  constructor() {
    effect(() => {
      const slices = this.slices().filter((s) => s.value > 0);
      const showLabels = this.showSliceLabels();
      const el = this.chartEl();
      if (slices.length === 0 || !el) {
        this.teardown();
        return;
      }
      if (!this.chart) {
        this.chart = echarts.init(el.nativeElement);
        // Rayon basé sur la LARGEUR → on le recalcule à chaque resize (pas seulement chart.resize()).
        // On met aussi `chartWidth` à jour → le texte central suit la taille de l'anneau.
        this.resizeObs = new ResizeObserver(() => {
          if (!this.chart) return;
          const w = el.nativeElement.clientWidth;
          this.chartWidth.set(w);
          this.chart.resize();
          this.chart.setOption({ series: [{ radius: this.computeRadius(w, this.showSliceLabels()) }] });
        });
        this.resizeObs.observe(el.nativeElement);
      }
      this.chart.setOption(this.buildOption(slices, showLabels, this.radiusForWidth(showLabels)), true);
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

  /** Résout une chaîne de tokens imbriqués (ex. var(--macro-carbs) -> var(--c-turquoise) -> #..). */
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

  /**
   * Rayons [intérieur, extérieur] EN PIXELS, proportionnels à la LARGEUR du conteneur (et non à
   * min(largeur, hauteur) comme le défaut ECharts) → l'anneau garde le même rapport avec la largeur
   * du cadre quelle que soit sa hauteur. Marge extérieure pour les libellés ; plus grand sans libellés.
   */
  private radiusForWidth(showLabels: boolean): [number, number] {
    return this.computeRadius(this.chartEl()?.nativeElement.clientWidth ?? 0, showLabels);
  }

  /** Idem mais pour une largeur `w` donnée (largeur paramétrée) → réutilisable par le texte central
   *  pour suivre la taille de l'anneau au redimensionnement. */
  private computeRadius(w: number, showLabels: boolean): [number, number] {
    // Sans étiquettes : anneau plus grand (aucune marge de texte à réserver).
    if (!showLabels) {
      return [Math.round(w * 0.2175), Math.round(w * 0.27)];
    }
    // Avec étiquettes externes : on PLAFONNE le rayon extérieur pour réserver, de chaque côté, la place
    // EXACTE du libellé le plus large (MESURÉE, pas devinée) + la ligne de rappel + le bleedMargin
    // ECharts. Un donut LARGE (ex. showcase) garde son rayon proportionnel (le plafond ne mord pas) ;
    // un donut ÉTROIT rétrécit juste son anneau dans la place libre au lieu de tronquer le libellé en
    // « Glucides… ». Bande (épaisseur) conservée au même rapport rayon qu'à l'origine [0.1725, 0.21].
    const LEADER = 14 + 10; // labelLine length + length2 (extension horizontale max du rappel)
    const BLEED = 10; // bleedMargin ECharts (marge mini texte ↔ bord du graphe)
    // Part « libellé » de la marge plafonnée à 25 % de la largeur : un donut étroit aux libellés très
    // longs (ex. Stats « Variété » : noms de groupes) garde un anneau VISIBLE, les libellés au-delà étant
    // tronqués par ECharts (compromis assumé quand le conteneur n'a vraiment plus la place). Les donuts
    // larges / à libellés courts (Objectifs, showcase, Origine) ne sont pas affectés (min() ne mord pas).
    const reserve = Math.min(this.maxLabelWidth(), w * 0.25) + LEADER + BLEED + 4;
    const outer = Math.min(w * 0.21, Math.max(0, w / 2 - reserve));
    const inner = outer * (0.1725 / 0.21);
    return [Math.round(inner), Math.round(outer)];
  }

  /** Largeur (px) de la LIGNE la plus large des étiquettes (nom / « NN % » empilés) — mesurée via canvas. */
  private maxLabelWidth(): number {
    const slices = this.slices().filter((s) => s.value > 0);
    if (!slices.length) return 0;
    const total = slices.reduce((s, x) => s + x.value, 0) || 1;
    if (!this.measureCtx) {
      this.measureCtx = document.createElement('canvas').getContext('2d');
    }
    const ctx = this.measureCtx;
    if (!ctx) return 0;
    ctx.font = 'normal 11px sans-serif'; // = label.fontSize 11 + police par défaut ECharts
    let max = 0;
    for (const s of slices) {
      // Le % est SOUS le nom (2 lignes) : la ligne la plus large fait foi.
      const pct = `${Math.round((s.value / total) * 100)} %`;
      max = Math.max(max, ctx.measureText(s.label).width, ctx.measureText(pct).width);
    }
    return max;
  }

  private buildOption(slices: DonutSlice[], showLabels: boolean, radius: [number, number]): ChartOption {
    const border = this.cssVar('--app-bg-recessed');
    const round1 = (v: number) => Math.round(v * 10) / 10;
    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        // Tooltip = « chip » à la couleur de la part survolée, comme les badges d'aliments du catalogue :
        // texte vif (oklch l+0.1 c×1.25) + bordure dans la couleur, fond neutre thirdBlue
        // (--app-bg-recessed) sur lequel le texte/la bordure colorés ressortent ; coins peu arrondis (--radius-md),
        // valeur + % arrondis à 1 décimale. Tout le style vit dans le HTML du formatter → le conteneur
        // ECharts est rendu transparent / sans bordure / sans padding / sans ombre.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- params echarts (union TopLevelFormatterParams)
        formatter: (p: any) =>
          `<span style="display:inline-block;padding:3px 9px;border-radius:var(--radius-md);` +
          `border:1px solid ${p.color};background:var(--app-bg-recessed);` +
          `color:oklch(from ${p.color} calc(l + 0.1) calc(c * 1.25) h);font-weight:600;font-size:12px;">` +
          `${p.name ?? ''} : ${round1(p.value ?? 0)} (${round1(p.percent ?? 0)} %)</span>`,
        backgroundColor: 'transparent',
        borderWidth: 0,
        padding: 0,
        extraCssText: 'box-shadow:none;',
      },
      series: [
        {
          type: 'pie',
          // Rayon EN PIXELS, proportionnel à la LARGEUR du conteneur (radiusForWidth), pas à min(w,h)
          // comme le défaut ECharts → l'anneau garde le même rapport avec la largeur du cadre, quelle
          // que soit la hauteur. Épaisseur doublée conservée ; marge extérieure pour les libellés.
          radius,
          center: ['50%', '50%'],
          avoidLabelOverlap: showLabels,
          // Nom de l'élément + % à côté de chaque part (couleur de la part), au bout de la ligne de
          // rappel (alignement par défaut = près de la part). La marge pour que le texte ne soit pas
          // tronqué est garantie côté rayon (radiusForWidth réserve une marge horizontale constante).
          label: showLabels
            ? {
                show: true,
                // Écart vertical minimal entre deux libellés voisins (ex. « Fibres 3 % » juste sous
                // « Protéines 17 % ») pendant la répartition anti-chevauchement → ils respirent un peu.
                minMargin: 12,
                // % SOUS l'intitulé (demande user 2026-07-15 — gain de largeur, plus aéré).
                formatter: (p: { name?: string; percent?: number }) =>
                  `${p.name ?? ''}\n${Math.round(p.percent ?? 0)} %`,
                color: 'inherit',
                fontSize: 11,
              }
            : { show: false },
          // Lignes de rappel allongées (radial `length` + horizontal `length2`) pour aérer les
          // étiquettes par rapport à l'anneau — aligné sur les anneaux concentriques (LEADER_OUT 14 / HSEG 10).
          labelLine: showLabels ? { show: true, length: 14, length2: 10 } : { show: false },
          itemStyle: { borderColor: border, borderWidth: 2 },
          data: slices.map((s) => ({
            name: s.label,
            value: s.value,
            itemStyle: { color: this.resolveColor(s.color) },
          })),
        },
      ],
    };
  }
}
