import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';
import { ProgressBarPrimitive } from '@designsystem/common_components/progress-bar-primitive';
import { ProgressRing } from '@designsystem/common_components/progress-ring';
import {
  RadarChartComponent,
  type RadarAxis,
  type RadarSeries,
} from '@designsystem/common_components/radar-chart';
import { DonutChartComponent, type DonutSlice } from '@designsystem/common_components/donut-chart';
import { ConcentricRingsChart } from '@designsystem/common_components/concentric-rings-chart';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { macroRingViews } from './macro-rings-chart';
import { MACRO_ABBR, MACRO_COLOR, MACRO_LABEL, MACRO_UNIT, SUGAR_COLOR, type MacroKey } from './macro-colors';
import { MICRO_COLOR, microLineItems } from './micro-colors';
import {
  MICRO_KEYS,
  MICRO_SHORT,
  microRows,
  type MicroNutrients,
  type MicroTotals,
} from './micros';

/** Mode d'affichage du résumé (prop `display`). */
export type SummaryDisplay = 'line' | 'bar' | 'radar' | 'donut' | 'rings' | 'concentric';

/** Macros d'un profil nutritionnel (grammes). Fibres optionnelles (D11). */
export interface MacroAmounts {
  protein: number;
  carbs: number;
  fat: number;
  fiber?: number | null;
}

/**
 * Cibles optionnelles (objectifs / valeurs de référence) pour la comparaison. Si fournies, les
 * barres et le radar affichent l'avancement vs cible ; sinon ils affichent le profil brut (barres
 * relatives au plus grand macro, radar à échelle partagée).
 */
export interface MacroTargets {
  kcal?: number | null;
  protein?: number | null;
  carbs?: number | null;
  fat?: number | null;
  fiber?: number | null;
}

/** Les 4 macros à barres/axes (kcal reste un chiffre d'en-tête, pas comparable aux grammes). */
const GRAM_MACRO_KEYS: MacroKey[] = ['carbs', 'fat', 'protein', 'fiber'];

function round(v: number): number {
  return Math.round(v);
}
function round1(v: number): number {
  return Math.round(v * 10) / 10;
}

/** Une ligne « barre » de macro : couleur dédiée + avancement 0..1. */
export interface MacroBarRow {
  key: MacroKey;
  label: string;
  color: string;
  value: number;
  unit: string;
  target: number | null;
  /** Avancement borné 0..1 (vs cible si fournie, sinon relatif au plus grand macro du profil). */
  progress: number;
  valueText: string;
  targetText: string;
}

/**
 * Lignes barres des 4 macros (glucides / lipides / protéines / fibres). Couleur dédiée par macro
 * (macro-colors.ts), jamais fonction de l'avancement. Avec cibles → avancement vs cible ; sans
 * cible → barre relative au plus grand macro (comparaison intra-profil). Pure, testable.
 */
export function macroBarRows(macros: MacroAmounts, targets: MacroTargets | null): MacroBarRow[] {
  const values: Record<MacroKey, number> = {
    kcal: 0,
    carbs: macros.carbs ?? 0,
    fat: macros.fat ?? 0,
    protein: macros.protein ?? 0,
    fiber: macros.fiber ?? 0,
  };
  const useTargets = !!targets;
  const maxValue = Math.max(1, ...GRAM_MACRO_KEYS.map((k) => values[k]));
  return GRAM_MACRO_KEYS.map((k) => {
    const value = values[k];
    const target = (targets?.[k] ?? null) as number | null;
    const denom = useTargets ? (target && target > 0 ? target : value || 1) : maxValue;
    return {
      key: k,
      label: MACRO_LABEL[k],
      color: MACRO_COLOR[k],
      value,
      unit: MACRO_UNIT[k],
      target,
      progress: Math.max(0, Math.min(1, value / denom)),
      valueText: `${round(value)}`,
      targetText: target ? `/ ${round(target)} ${MACRO_UNIT[k]}` : `${MACRO_UNIT[k]}`,
    };
  });
}

/** Ligne « Sucres » du mode barres (information per-100 g) : teinte --macro-sugar + échelle relative. */
export interface SugarBarRow {
  color: string;
  value: number;
  progress: number;
  valueText: string;
}

/**
 * Ligne « Sucres » (information, teinte --macro-sugar) affichée sous les fibres en mode barres :
 * même échelle relative que les barres macros SANS cible (dénominateur = plus grand macro du
 * profil) pour se comparer visuellement aux glucides. null si les sucres ne sont pas renseignés.
 * VOLONTAIREMENT absente du radar / des anneaux / du donut : un plafond n'est pas une cible
 * (cohérent avec le bandeau du Journal). Pure, testable.
 */
export function sugarBarRow(sugarG: number | null | undefined, macros: MacroAmounts): SugarBarRow | null {
  if (sugarG == null) return null;
  const maxValue = Math.max(1, macros.carbs ?? 0, macros.fat ?? 0, macros.protein ?? 0, macros.fiber ?? 0);
  return {
    color: SUGAR_COLOR,
    value: sugarG,
    progress: Math.max(0, Math.min(1, sugarG / maxValue)),
    valueText: `${round(sugarG)}`,
  };
}

/** Une ligne « barre » de micro : teinte par famille (T3) + avancement vs VNR / plafond. */
export interface MicroSummaryRow {
  key: keyof MicroNutrients;
  label: string;
  color: string;
  value: number;
  unit: string;
  isLimit: boolean;
  target: number;
  progress: number;
  exceeded: boolean;
  valueText: string;
  targetText: string;
}

/**
 * Lignes barres des 10 micros, cumul vs VNR UE (objectif) ou plafond Sodium. Teinte par famille
 * (MICRO_COLOR, T3 : minéraux vs vitamines) ; couleur d'alerte si le plafond Sodium est dépassé.
 * Pure, testable.
 */
export function microSummaryRows(micros: MicroNutrients): MicroSummaryRow[] {
  const totals = MICRO_KEYS.reduce(
    (acc, k) => ({ ...acc, [k]: micros[k] ?? 0 }),
    {} as MicroTotals,
  );
  return microRows(totals).map((r) => ({
    key: r.key,
    label: r.label,
    // Dépassement de plafond (Sodium) : orange d'avertissement, distinct du rouge minéral
    // (--micro-mineral == --app-snackbar-error) qui rendait l'alerte invisible. Doublé d'un signal
    // non chromatique (icône ⚠ + gras) côté template pour la lisibilité daltonisme/thème sombre.
    color: r.exceeded ? 'var(--app-snackbar-warning)' : MICRO_COLOR[r.key],
    value: r.value,
    unit: r.unit,
    isLimit: r.isLimit,
    target: r.target,
    progress: r.progress,
    exceeded: r.exceeded,
    valueText: `${round1(r.value)}`,
    targetText: `${r.isLimit ? '≤' : '/'} ${r.target} ${r.unit}`,
  }));
}

/**
 * Données radar des macros (via le composant radar T2). Avec cibles → 2 tracés superposés : la série
 * « valeur » en % de la cible (100 = atteint, axes plafonnés à 120) + une série repère « cible » à
 * 100 ; sans cible → 1 tracé de valeurs brutes (g), échelle partagée gérée par le radar. Les noms de
 * légende sont personnalisables (`labels`) pour distinguer clairement les 2 tracés selon la page
 * (ex. « Consommé » vs « Cible » sur Stats, « Réel (7 j) » vs « Cible » sur Objectifs). Pure, testable.
 */
export function macroRadarData(
  macros: MacroAmounts,
  targets: MacroTargets | null,
  labels: { value?: string; target?: string } = {},
): { axes: RadarAxis[]; series: RadarSeries[] } {
  const rows = macroBarRows(macros, targets);
  const valueName = labels.value ?? 'Profil';
  const targetName = labels.target ?? 'Objectif';
  // Nom d'axe coloré par macro (MACRO_COLOR), cohérent avec les barres ; le remplissage du
  // polygone reste mono-couleur (limite ECharts radar — pas de dégradé par direction natif).
  if (targets) {
    return {
      axes: rows.map((r) => ({ label: r.label, max: 120, color: r.color })),
      series: [
        {
          name: valueName,
          values: rows.map((r) => (r.target && r.target > 0 ? round((r.value / r.target) * 100) : 0)),
          color: 'var(--macro-kcal)',
          area: true,
        },
        { name: targetName, values: rows.map(() => 100), color: 'var(--app-text-secondary)' },
      ],
    };
  }
  return {
    axes: rows.map((r) => ({ label: r.label, color: r.color })),
    series: [{ name: valueName, values: rows.map((r) => r.value), color: 'var(--macro-kcal)', area: true }],
  };
}

/**
 * Données radar des micros : un axe par micronutriment RENSEIGNÉ (valeur non nulle), valeur =
 * couverture VNR en % (plafonnée à 120 ; Sodium = % du plafond). Axes teintés par famille (T3).
 * Cas « aucun micro renseigné » → on retombe sur les 10 axes à 0 (radar « vide en données »), plus
 * parlant que le placeholder « Aucune donnée » et cohérent avec le radar macros qui montre toujours
 * ses axes. Symétrique de macroRadarData pour la section micros du panneau. Pure, testable.
 */
export function microRadarData(micros: MicroNutrients): { axes: RadarAxis[]; series: RadarSeries[] } {
  const all = microSummaryRows(micros);
  const present = all.filter((r) => r.value > 0);
  // Aucun micro renseigné → garder les 10 axes (série à 0) plutôt qu'un radar sans axes (placeholder).
  const rows = present.length > 0 ? present : all;
  return {
    axes: rows.map((r) => ({ label: r.label, max: 120, color: r.color })),
    series: [
      {
        name: 'Couverture VNR',
        // Remplissage bleu (comme le radar macros) ; les axes restent teintés par famille (T3).
        values: rows.map((r) => Math.round(Math.min(120, (r.target > 0 ? r.value / r.target : 0) * 100))),
        color: 'var(--macro-kcal)',
        area: true,
      },
    ],
  };
}

/** Une part de la répartition énergétique d'une macro : kcal apportées + part (0..1) de l'énergie macro. */
export interface MacroEnergyShare {
  key: MacroKey;
  label: string;
  color: string;
  /** kcal apportées par cette macro (facteurs Atwater + fibres 2 kcal/g, cf. food-kcal.ts D12). */
  kcal: number;
  /** Part de l'énergie macro totale (0..1) — 0 si le profil n'apporte aucune kcal. */
  share: number;
}

/** Facteurs énergétiques (kcal/g) : Atwater 4/4/9 + fibres 2 kcal/g (EU 1169/2011, cf. food-kcal.ts D12). */
const MACRO_KCAL_PER_G: Record<MacroKey, number> = { kcal: 0, carbs: 4, fat: 9, protein: 4, fiber: 2 };

/**
 * Répartition de l'énergie d'un profil macro : kcal apportées par chaque macro (glucides / lipides /
 * protéines / fibres) et part (%) de l'énergie macro totale — données du donut « répartition macro »
 * (1 couleur par macro, macro-colors.ts). Ordre canonique G/L/P/F (GRAM_MACRO_KEYS). Profil sans kcal
 * → parts à 0 (le donut affiche son placeholder « Aucune donnée »). Pure, testable.
 */
export function macroEnergyShares(macros: MacroAmounts): MacroEnergyShare[] {
  const grams: Record<MacroKey, number> = {
    kcal: 0,
    carbs: macros.carbs ?? 0,
    fat: macros.fat ?? 0,
    protein: macros.protein ?? 0,
    fiber: macros.fiber ?? 0,
  };
  const kcalByKey = GRAM_MACRO_KEYS.map((k) => Math.max(0, grams[k]) * MACRO_KCAL_PER_G[k]);
  const total = kcalByKey.reduce((s, v) => s + v, 0);
  return GRAM_MACRO_KEYS.map((k, i) => ({
    key: k,
    label: MACRO_LABEL[k],
    color: MACRO_COLOR[k],
    kcal: kcalByKey[i],
    share: total > 0 ? kcalByKey[i] / total : 0,
  }));
}

/**
 * Panneau « résumé macros + micros » réutilisable (catalogue T5 + recettes T7) — mutualise le
 * langage visuel du bandeau du Journal : calories en en-tête, macros en barres colorées (1 couleur
 * par macro, macro-colors.ts), micros en liste/barres colorées par famille (micro-colors.ts, T3),
 * et une option radar (composant radar T2). 5 affichages au choix via `display` : ligne / barre /
 * radar / anneaux (progress rings) / cercle (donut = répartition des kcal par macro, composant donut
 * DS). Cibles optionnelles (`targets`) → avancement vs objectif ; sinon profil brut (le donut, lui,
 * est toujours une répartition %, indépendante des cibles).
 *
 * Pure côté logique : les lignes/axes/parts sont calculés par des helpers exportés (macroBarRows,
 * microSummaryRows, microLineItems, macroRadarData, macroEnergyShares), testables sans Angular ni canvas.
 */
@Component({
  selector: 'app-nutrition-summary-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon, ProgressBarPrimitive, ProgressRing, ConcentricRingsChart, RadarChartComponent, DonutChartComponent, TitledDivider],
  template: `
    <div class="nsp">
      <!-- En-tête : « Macros » + calories (moitié gauche, au-dessus du radar macros). En mode radar
           AVEC micros, « Vitamines & minéraux » s'ajoute au-dessus du radar droit, et la bascule
           d'affichage projetée par le parent (slot [panelToggle]) file à l'extrême droite — le tout
           sur une seule ligne (kcal reste un chiffre d'en-tête, pas une barre/un axe). -->
      <!-- En-tête masqué en mode anneaux concentriques : les étiquettes « en étoile » portent déjà
           nom + % de chaque macro, la ligne « Macros … kcal » ferait doublon. -->
      @if (display() !== 'concentric') {
      <div class="nsp__head" [class.nsp__head--split]="radarSideBySide() || donutSideBySide()">
        <div class="nsp__head-macro">
          @if (sectionHeadings()) {
            <app-titled-divider class="nsp__td" title="Macros" />
          } @else {
            <span class="nsp__macro-title">Macros</span>
          }
          <span class="nsp__kcal">
            <app-icon name="local_fire_department" [size]="16" [color]="macroColor.kcal" />
            <span class="nsp__kcal-value" [style.color]="macroColor.kcal">{{ round(kcal()) }} kcal</span>
            @if (kcalTarget() !== null) {
              <span class="nsp__kcal-target">/ {{ round(kcalTarget()!) }} kcal</span>
            }
            @if (unitSuffix()) {
              <span class="nsp__unit">{{ unitSuffix() }}</span>
            }
          </span>
        </div>
        <div class="nsp__head-aside">
          @if (radarSideBySide() || donutSideBySide()) {
            @if (sectionHeadings()) {
              <app-titled-divider class="nsp__td" title="Micros" />
            } @else {
              <span class="nsp__macro-title">Micros</span>
            }
          }
          <ng-content select="[panelToggle]"></ng-content>
        </div>
      </div>
      }

      <!-- Mode radar AVEC micros : radar macros (gauche) + radar micros (droite) CÔTE À CÔTE, note
           VNR commune centrée sous les deux (au lieu de l'empilement macros puis micros). Sans micros,
           le radar macros seul reste géré par le @case ('radar') ci-dessous. -->
      @if (radarSideBySide()) {
        <div class="nsp__radars">
          <div class="nsp__radar">
            <app-radar-chart
              [axes]="radar().axes"
              [series]="radar().series"
              [height]="radarHeight()"
              [showLegend]="hasTargets()"
              emptyText="Aucune donnée"
            />
          </div>
          <div class="nsp__radar">
            <app-radar-chart
              [axes]="microRadar().axes"
              [series]="microRadar().series"
              [height]="radarHeight()"
              [showLegend]="false"
              emptyText="Aucune donnée"
            />
          </div>
        </div>
        <p class="nsp__hint nsp__hint--center">
          Cibles = VNR UE (1169/2011). Sodium = plafond repère (≤ 2000 mg).
        </p>
      }

      <!-- Macros : ligne compacte / barres / radar selon display. -->
      @switch (display()) {
        @case ('line') {
          <p class="nsp__line">
            <span [style.color]="macroColor.carbs">G {{ round1(macros().carbs) }}</span> ·
            <span [style.color]="macroColor.fat">L {{ round1(macros().fat) }}</span> ·
            <span [style.color]="macroColor.protein">P {{ round1(macros().protein) }}</span>
            @if (macros().fiber != null) {
              · <span [style.color]="macroColor.fiber">F {{ round1(macros().fiber!) }}</span>
            }
          </p>
        }
        @case ('radar') {
          <!-- Radar macros SEUL : uniquement sans micros (avec micros → côte-à-côte géré en tête). -->
          @if (!radarSideBySide()) {
            <app-radar-chart
              [axes]="radar().axes"
              [series]="radar().series"
              [height]="radarHeight()"
              [showLegend]="hasTargets()"
              emptyText="Aucune donnée"
            />
          }
        }
        @case ('donut') {
          @if (donutSideBySide()) {
            <!-- Donut macros (gauche) + donut micros (droite) CÔTE À CÔTE (comme le mode radar) ; pas de
                 chevron repliable dans ce mode (les micros sont déjà visibles ici). -->
            <div class="nsp__donuts">
              <div class="nsp__donut">
                <app-donut-chart
                  class="nsp__donut-chart"
                  [slices]="donut()"
                  [height]="donutHeight()"
                  [centerLabel]="round(donutTotalKcal()).toString()"
                  centerSub="kcal"
                  emptyText="Aucune donnée"
                />
              </div>
              <div class="nsp__donut">
                <app-donut-chart
                  class="nsp__donut-chart"
                  [slices]="microDonut()"
                  [height]="donutHeight()"
                  emptyText="Aucune donnée"
                />
              </div>
            </div>
          } @else {
            <!-- Répartition des kcal du profil par macro (donut macros seul, sans micros). -->
            <div class="nsp__donut">
              <app-donut-chart
                class="nsp__donut-chart"
                [slices]="donut()"
                [height]="donutHeight()"
                [centerLabel]="round(donutTotalKcal()).toString()"
                centerSub="kcal"
                emptyText="Aucune donnée"
              />
            </div>
          }
        }
        @case ('rings') {
          <div class="nsp__rings">
            @for (row of macroBars(); track row.key) {
              <div class="nsp__ring-cell">
                <app-progress-ring
                  [progress]="row.progress"
                  [color]="row.color"
                  [label]="row.valueText"
                  [sublabel]="row.targetText"
                />
                <span class="nsp__ring-caption" [style.color]="row.color">{{ row.label }}</span>
              </div>
            }
          </div>
        }
        @case ('concentric') {
          <!-- Anneaux concentriques macro (kcal extérieur → fibres centre) + étiquettes « en étoile ».
               Composant DS générique réutilisable (partagé avec le Profil macros des Objectifs). -->
          <app-concentric-rings-chart
            [rings]="macroRings()"
            [centerText]="macroRingsCenterText()"
            [centerColor]="macroColor.kcal"
          />
        }
        @default {
          @for (row of displayBars(); track row.key) {
            <div class="nsp__row">
              <span class="nsp__label" [style.color]="row.color">{{ row.label }}</span>
              <app-progress-bar-primitive
                class="nsp__bar"
                [progress]="row.progress"
                [color]="row.color"
                troughColor="var(--app-bg-surface)"
              />
              <span class="nsp__value">
                <span [style.color]="row.color">{{ row.valueText }}</span> {{ row.targetText }}
              </span>
            </div>
          }
          <!-- Sucres (information per-100 g, teinte dédiée) : sous les fibres, mode barres SEULEMENT
               (jamais dans le radar / les anneaux / le donut : plafond ≠ cible, cohérent bandeau). -->
          @if (sugarRow(); as srow) {
            <div class="nsp__row">
              <span class="nsp__label" [style.color]="srow.color">Sucres</span>
              <app-progress-bar-primitive
                class="nsp__bar"
                [progress]="srow.progress"
                [color]="srow.color"
                troughColor="var(--app-bg-surface)"
              />
              <span class="nsp__value">
                <span [style.color]="srow.color">{{ srow.valueText }}</span> g
              </span>
            </div>
          }
        }
      }

      <!-- Micros (empilés sous les macros) : liste / anneaux / cercle (donut) sinon barres. Le mode
           radar AVEC micros est rendu côte-à-côte en tête (radarSideBySide) → exclu de ce bloc. -->
      @if (showMicros() && hasMicros() && !radarSideBySide() && !donutSideBySide()) {
        <button
          type="button"
          class="nsp__micros-toggle"
          [attr.aria-expanded]="microsOpen()"
          (click)="microsOpen.set(!microsOpen())"
        >
          @if (sectionHeadings()) {
            <app-titled-divider class="nsp__td" title="Micros" />
          } @else {
            <span class="nsp__subtitle">Micros</span>
          }
          <span class="nsp__micros-cta">
            <app-icon
              [name]="microsOpen() ? 'expand_less' : 'expand_more'"
              [size]="18"
              color="var(--app-accent-text)"
            />
            {{ microsOpen() ? 'Masquer les micros' : 'Afficher les micros' }}
          </span>
        </button>
        <div class="nsp__micros-reveal" [class.nsp__micros-reveal--open]="microsOpen()">
          <div class="nsp__micros-clip">
            <div class="nsp__micros-body">
        @if (display() === 'line') {
          @if (microLine().length) {
            <p class="nsp__micro-line">
              @for (mi of microLine(); track $index) {
                <span [style.color]="mi.color">{{ mi.short }} {{ mi.value }} {{ mi.unit }}</span
                >{{ $last ? '' : ' · ' }}
              }
            </p>
          } @else {
            <p class="nsp__hint">Aucun micronutriment renseigné.</p>
          }
        } @else if (display() === 'rings') {
          <div class="nsp__rings">
            @for (row of microBars(); track row.key) {
              <div class="nsp__ring-cell">
                <app-progress-ring
                  [progress]="row.progress"
                  [color]="row.color"
                  [label]="row.valueText"
                  [sublabel]="row.targetText"
                />
                <span class="nsp__ring-caption" [style.color]="row.color">{{ row.label }}</span>
              </div>
            }
          </div>
        } @else if (display() === 'donut') {
          @if (microDonut().length) {
            <div class="nsp__donut">
              <!-- Micros : le % de la part = part relative, ≠ la « couverture » de la légende → pas
                   d'étiquettes sur les parts ici (la légende porte la couverture, métrique pertinente). -->
              <app-donut-chart
                class="nsp__donut-chart"
                [slices]="microDonut()"
                [height]="donutHeight()"
                [showSliceLabels]="false"
                emptyText="Aucune donnée"
              />
              <ul class="nsp__donut-legend">
                @for (r of microDonutRows(); track r.key) {
                  <li class="nsp__donut-row">
                    <span class="nsp__donut-dot" [style.background]="r.color"></span>
                    <span class="nsp__donut-label">{{ r.label }}</span>
                    <span class="nsp__donut-pct" [style.color]="r.color">{{ round(r.coverage) }} %</span>
                  </li>
                }
              </ul>
            </div>
          } @else {
            <p class="nsp__hint">Aucun micronutriment renseigné.</p>
          }
        } @else {
          @for (row of microBars(); track row.key) {
            <div class="nsp__row" [class.nsp__row--alert]="row.exceeded">
              <span class="nsp__label" [class.nsp__label--alert]="row.exceeded" [style.color]="row.color">
                @if (row.exceeded) {
                  <app-icon name="warning" [size]="14" [color]="row.color" />
                }
                {{ row.label }}
              </span>
              <app-progress-bar-primitive
                class="nsp__bar"
                [progress]="row.progress"
                [color]="row.color"
                troughColor="var(--app-bg-surface)"
              />
              <span class="nsp__value">
                <span [style.color]="row.color">{{ row.valueText }}</span> {{ row.targetText }}
              </span>
            </div>
          }
        }
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .nsp {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
        display: flex;
        flex-direction: column;
        /* Espace UNIFORME (= page Stats nutrition) entre toutes les sections : titre ↔ graphe ↔ texte
           sous le graphe. Source unique de l'espacement vertical (les marges de section sont retirées). */
        gap: var(--space-3);
      }
      /* En-tête : « Macros » + calories (moitié gauche). En mode radar avec micros (--split), bloc
         droit « Vitamines & minéraux » + bascule projetée ; les 2 moitiés (flex:1) calquent les 2
         radars 50/50. Hors split, le bloc droit ne porte que la bascule, collée à l'extrême droite. */
      .nsp__head {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      /* Macros (gauche) + calories (centrées dans l'espace restant) — les calories tombent « pile
         entre » le titre Macros et la zone de droite (bascule, ou titre V&M en mode radar). */
      .nsp__head-macro {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        align-items: baseline;
        gap: var(--space-2);
      }
      .nsp__head-aside {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .nsp__head--split .nsp__head-aside {
        flex: 1 1 0;
        min-width: 0;
        justify-content: space-between;
      }
      .nsp__macro-title {
        color: var(--c-gray-blue);
        font-size: 14px;
        font-weight: 600;
      }
      /* En-tête en titled-divider (mode sectionHeadings) : occupe la ligne à gauche du kcal / de la
         bascule / du CTA — son trait droit se raccourcit pour leur laisser la place (comme le bandeau). */
      .nsp__td {
        flex: 1 1 0;
        min-width: 0;
      }
      .nsp__kcal {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        align-items: baseline;
        justify-content: center;
        gap: var(--space-2);
      }
      .nsp__kcal-value {
        font-size: 18px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
      .nsp__kcal-target {
        color: var(--app-text-tertiary);
        font-size: 13px;
        font-variant-numeric: tabular-nums;
      }
      .nsp__unit {
        color: var(--app-text-tertiary);
        font-size: 12px;
        font-style: italic;
      }
      .nsp__line,
      .nsp__micro-line {
        margin: 0;
        font-size: 13px;
      }
      /* Macros (ligne compacte) : « · » en gris-bleu ; chaque valeur garde sa couleur via son span. */
      .nsp__line {
        color: var(--c-gray-blue);
      }
      .nsp__micro-line {
        font-size: 12px;
        color: var(--app-text-secondary);
      }
      .nsp__row {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .nsp__label {
        width: 76px;
        flex-shrink: 0;
        font-size: 13px;
      }
      /* Plafond dépassé (Sodium) : signal non chromatique en plus de l'orange — icône ⚠ + gras. */
      .nsp__label--alert {
        display: inline-flex;
        align-items: center;
        gap: 3px;
        font-weight: 700;
      }
      .nsp__row--alert .nsp__value {
        font-weight: 700;
      }
      .nsp__bar {
        flex: 1;
      }
      .nsp__value {
        min-width: 110px;
        text-align: right;
        color: var(--app-text-primary);
        font-size: 13px;
        font-variant-numeric: tabular-nums;
      }
      .nsp__subtitle {
        /* Sous-titre micros affiché SOUS les macros (modes empilés) → il respire des deux côtés :
           au-dessus (séparation d'avec la section macros) et en dessous (avant le contenu micros). */
        margin-top: var(--space-4);
        margin-bottom: var(--space-4);
        color: var(--c-gray-blue);
        font-size: 14px;
        font-weight: 600;
      }
      /* Bandeau micros repliable : sous-titre (gauche) + CTA chevron « Afficher/Masquer » (droite). */
      .nsp__micros-toggle {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        width: 100%;
        background: transparent;
        border: none;
        padding: 0;
        cursor: pointer;
      }
      .nsp__micros-toggle .nsp__subtitle {
        margin: 0;
      }
      .nsp__micros-cta {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        flex-shrink: 0;
        color: var(--app-accent-text);
        font-family: var(--font-family-base);
        font-size: 12px;
        white-space: nowrap;
      }
      /* Déroulé animé des micros : hauteur 0fr↔1fr + clip (même motion que les autres déroulés). */
      .nsp__micros-reveal {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows var(--motion-base) var(--motion-ease);
      }
      .nsp__micros-reveal--open {
        grid-template-rows: 1fr;
      }
      .nsp__micros-clip {
        overflow: hidden;
        min-height: 0;
      }
      .nsp__micros-body {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding-top: var(--space-3);
      }
      @media (prefers-reduced-motion: reduce) {
        .nsp__micros-reveal {
          transition: none;
        }
      }
      .nsp__hint {
        margin: 0;
        font-size: 12px;
        font-style: italic;
        color: var(--app-text-tertiary);
      }
      /* Note VNR centrée sous les deux radars (mode radar côte à côte). */
      .nsp__hint--center {
        margin-top: var(--space-2);
        text-align: center;
        color: var(--c-gray-blue);
      }
      /* Mode radar : radar macros (gauche) + radar micros (droite) côte à côte (50/50, jamais empilés). */
      .nsp__radars {
        display: flex;
        gap: var(--space-3);
      }
      .nsp__radar {
        flex: 1 1 0;
        min-width: 0;
      }
      /* Mode donut côte à côte : donut macros + donut micros, chacun ~moitié de la largeur. */
      .nsp__donuts {
        display: flex;
        gap: var(--space-3);
      }
      .nsp__donuts > .nsp__donut {
        flex: 1 1 0;
        min-width: 0;
      }
      /* Affichage anneaux (mode rings) : un anneau de progression par macro/micro + légende dessous,
         même rendu que le Résumé du jour du Journal (page Nutrition). */
      .nsp__rings {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-3) var(--space-4);
        justify-content: center;
        padding: var(--space-2) 0;
      }
      .nsp__ring-cell {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-1);
        width: 84px;
      }
      .nsp__ring-caption {
        color: var(--app-text-secondary);
        font-size: 12px;
        text-align: center;
      }
      /* Mode cercle : donut (gauche) + légende verticale (droite), macros ET micros. */
      .nsp__donut {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .nsp__donut-chart {
        flex: 1;
        min-width: 0;
      }
      /* Légende du donut : pastille couleur + libellé + part / couverture en %, en colonne à droite. */
      .nsp__donut-legend {
        flex: 1;
        min-width: 0;
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .nsp__donut-row {
        display: flex;
        align-items: center;
        gap: var(--space-1);
        font-size: 13px;
      }
      .nsp__donut-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        flex-shrink: 0;
      }
      .nsp__donut-label {
        color: var(--app-text-secondary);
      }
      .nsp__donut-pct {
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class NutritionSummaryPanel {
  /** Calories du profil (chiffre d'en-tête). */
  readonly kcal = input.required<number>();
  /** Macros en grammes (protéines / glucides / lipides / fibres optionnelles). */
  readonly macros = input.required<MacroAmounts>();
  /** Micros (10 valeurs per-X, nullables) — section affichée seulement si fournie. */
  readonly micros = input<MicroNutrients | null>(null);
  /** Sucres (g, information) : ligne dédiée sous les fibres en mode barres seulement (détail aliment).
      null (défaut) = pas de ligne — les autres pages du panneau restent inchangées. */
  readonly sugar = input<number | null>(null);
  /** Mode d'affichage : ligne / barres / radar / anneaux (rings) / anneaux concentriques (concentric) / cercle (donut) (défaut : barres). */
  readonly display = input<SummaryDisplay>('bar');
  /** Cibles optionnelles → avancement vs objectif (sinon profil brut). */
  readonly targets = input<MacroTargets | null>(null);
  /** Afficher la section micros (si des micros sont fournis). */
  readonly showMicros = input(true);
  /** Suffixe d'unité de l'en-tête (ex. « / 100 g » catalogue, « / portion » recette). */
  readonly unitSuffix = input('');
  /** Hauteur du radar (mode radar). Réduite (230) → moins de blanc au-dessus du tracé (le radar est
      centré dans son conteneur ; rayon inchangé → la légende reste visible). */
  readonly radarHeight = input(230);
  /** Hauteur du donut (mode cercle). */
  readonly donutHeight = input(220);
  /** Mode barres : préfixer les barres macros d'une barre kcal (consommé vs cible kcal). Opt-in. */
  readonly showKcalBar = input(false);
  /** En-têtes « Macros » / « Micros » en titled-divider (au lieu de simples libellés) — opt-in posé
      par le détail catalogue + recettes ; OFF ailleurs (ex. Profil macros des Stats, qui porte déjà
      son propre titled-divider « Profil macros » → pas de doublon). */
  readonly sectionHeadings = input(false);

  /** Déroulé des micros (chevron « Afficher les micros »). Replié par défaut. */
  protected readonly microsOpen = signal(false);

  protected readonly macroColor = MACRO_COLOR;
  protected readonly round = round;
  protected readonly round1 = round1;

  protected readonly hasTargets = computed(() => !!this.targets());
  protected readonly hasMicros = computed(() => this.micros() !== null);
  /** Mode radar AVEC micros présents → radars macros + micros côte à côte (sinon empilement normal). */
  protected readonly radarSideBySide = computed(
    () => this.display() === 'radar' && this.showMicros() && this.hasMicros(),
  );
  /** Mode donut AVEC micros → donut macros + donut micros côte à côte (pas de chevron repliable). */
  protected readonly donutSideBySide = computed(
    () => this.display() === 'donut' && this.showMicros() && this.hasMicros(),
  );
  protected readonly kcalTarget = computed(() => this.targets()?.kcal ?? null);

  protected readonly macroBars = computed(() => macroBarRows(this.macros(), this.targets()));
  /**
   * Barres affichées en mode « barre » : optionnellement préfixées d'une barre kcal (showKcalBar)
   * — avancement kcal consommé vs cible kcal, même langage que les barres macros, au-dessus des G/L/P/F.
   */
  protected readonly displayBars = computed<MacroBarRow[]>(() => {
    const rows = this.macroBars();
    if (!this.showKcalBar()) return rows;
    const kcal = this.kcal();
    const target = this.kcalTarget();
    const denom = target && target > 0 ? target : kcal || 1;
    const kcalRow: MacroBarRow = {
      key: 'kcal',
      label: MACRO_LABEL.kcal,
      color: MACRO_COLOR.kcal,
      value: kcal,
      unit: 'kcal',
      target: target ?? null,
      progress: Math.max(0, Math.min(1, kcal / denom)),
      valueText: `${round(kcal)}`,
      targetText: target ? `/ ${round(target)} kcal` : 'kcal',
    };
    return [kcalRow, ...rows];
  });
  /** Ligne « Sucres » du mode barres (null si sucres non fournis) — jamais dans le radar. */
  protected readonly sugarRow = computed(() => sugarBarRow(this.sugar(), this.macros()));
  /** Anneaux concentriques macro (mode « concentric ») — adaptateur partagé macroRingViews. */
  protected readonly macroRings = computed(() => macroRingViews(this.kcal(), this.macros(), this.targets()));
  /** Total kcal au centre de la pile d'anneaux. */
  protected readonly macroRingsCenterText = computed(() => round(this.kcal()).toString());
  protected readonly radar = computed(() => macroRadarData(this.macros(), this.targets()));
  protected readonly donutShares = computed(() => macroEnergyShares(this.macros()));
  protected readonly donut = computed<DonutSlice[]>(() =>
    // Libellé abrégé (Gluc./Lip./Prot./Fib.) : noms complets trop longs pour les parts du donut.
    this.donutShares().map((s) => ({ label: MACRO_ABBR[s.key], value: s.kcal, color: s.color })),
  );
  protected readonly donutTotalKcal = computed(() =>
    this.donutShares().reduce((sum, s) => sum + s.kcal, 0),
  );
  protected readonly microBars = computed(() => {
    const m = this.micros();
    return m ? microSummaryRows(m) : [];
  });
  protected readonly microLine = computed(() => {
    const m = this.micros();
    return m ? microLineItems(m) : [];
  });
  protected readonly microRadar = computed(() => {
    const m = this.micros();
    return m ? microRadarData(m) : { axes: [], series: [] };
  });

  /** Lignes du donut micros (mode cercle) : couverture VNR % par micro présent, teinte par famille. */
  protected readonly microDonutRows = computed(() =>
    this.microBars()
      .filter((r) => r.value > 0)
      .map((r) => ({
        key: r.key,
        label: r.label,
        color: r.color,
        coverage: r.target > 0 ? (r.value / r.target) * 100 : 0,
      })),
  );

  /** Parts du donut micros : taille = couverture VNR % (donut DS), couleur par famille.
   *  Libellé = abréviation (symbole chimique pour les minéraux : Ca, Mg, Fe…) — les noms complets sont trop
   *  longs pour les parts d'un donut à 10 entrées. */
  protected readonly microDonut = computed<DonutSlice[]>(() =>
    this.microDonutRows().map((r) => ({ label: MICRO_SHORT[r.key], value: r.coverage, color: r.color })),
  );
}
