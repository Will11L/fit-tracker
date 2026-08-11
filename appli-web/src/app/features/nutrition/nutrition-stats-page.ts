import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { DateRangePickerDialog } from '@designsystem/common_components/date-range-picker-dialog';
import { DonutChartComponent, type DonutSlice } from '@designsystem/common_components/donut-chart';
import { RadarChartComponent } from '@designsystem/common_components/radar-chart';
import { MultiLineChart } from '@designsystem/common_components/multi-line-chart';
import {
  SegmentedIconToggle,
  type SegmentItem,
} from '@designsystem/common_components/segmented-icon-toggle';
import { AppIcon } from '@designsystem/icons/app-icon';
import { SyncEngine } from '@core/sync/sync-engine';
import { MealRepository } from './meal.repository';
import { NutritionGoalRepository } from './nutrition-goal.repository';
import { FoodRepository } from './food.repository';
import {
  NutritionSummaryPanel,
  macroRadarData,
  type MacroTargets,
} from './nutrition-summary-panel';
import {
  MacroKey,
  MacroTargetKey,
  MACRO_COLOR,
  MACRO_KEYS,
  MACRO_LABEL,
  MACRO_TARGET_KEYS,
  MACRO_UNIT,
} from './macro-colors';
import { addDaysRange, computeBounds, RANGE_CHIPS, RangeKey } from './range-utils';
import {
  aggregateNutrition,
  earliestMealDate,
  formatBucketLabel,
  macroPercentSeries,
  periodMacroProfile,
  topFoodsByMacro,
  type TopFood,
} from './nutrition-stats-utils';
import {
  aggregateByOrigin,
  computeVariety,
  originDonutSlices,
  originRadarData,
  varietyDonutSlices,
  varietyRadarData,
  type OriginSlice,
  type VarietyStats,
} from './nutrition-category-stats';
import { NutritionStatsChart } from './nutrition-stats-chart';
import { FIBER_G_PER_1000_KCAL } from './goal-macros';

/** Représentation au choix d'une section catégorie : cercle (donut) ou radar. UPPER_CASE (politique 11). */
type CategoryView = 'CIRCLE' | 'RADAR';

/** Vue-modèle d'une carte de nutriment (un par macro), recalculée sur la période globale. */
interface MacroCard {
  key: MacroKey;
  label: string;
  color: string;
  unit: string;
  consumed: number[];
  target: number[];
  topFoods: TopFood[];
}

/**
 * Stats Nutrition (`/nutrition/stats`, V6 NUTRITION_DESIGN §5.6) — une GRILLE de cartes, une par
 * nutriment (Calories / Glucides / Lipides / Protéines / Fibres), affichées toutes en même temps.
 * Chaque carte porte son titre coloré (MACRO_COLOR), son mini-graphe (barres/courbes selon le toggle
 * global, sur la période globale, comparé à sa cible active par jour) et sa liste « Top aliments »
 * (agrégée par aliment, triée décroissante). Sélecteurs globaux en haut : période (rangeChips) +
 * type de graphe (barres/courbes). Agrégation 100 % client (Dexie), dans l'esprit des Stats sport.
 *
 * NB Android : l'écran y est trop petit pour la grille — un sélecteur de macro y est conservé
 * (un graphe + top aliments à la fois). Cf. mémoire nutrition-android-nav-mode.
 */
@Component({
  selector: 'app-nutrition-stats-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    DateRangePickerDialog,
    DonutChartComponent,
    RadarChartComponent,
    NutritionSummaryPanel,
    SegmentedIconToggle,
    NutritionStatsChart,
    MultiLineChart,
    AppIcon,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Stats Nutrition" />

      <div class="page__body">
        <div class="toolbar">
          <div class="chips chips--range">
            @for (r of rangeChips; track r.key) {
              <button
                class="chip chip--range"
                [class.chip--range-sel]="rangeKind() === r.key"
                (click)="selectRange(r.key)"
              >
                @if (r.key === 'CUSTOM') {
                  <app-icon name="calendar_today" [size]="15" color="currentColor" />
                }
                {{ r.label }}
              </button>
            }
          </div>
          <!-- Sélecteur d'affichage PARTAGÉ (Cercle/Radar) : pilote Origine + Variété ensemble, tout à droite. -->
          <app-segmented-icon-toggle
            [items]="viewSegments"
            [selected]="categoryView()"
            (select)="setCategoryView($event)"
          />
        </div>

        <!-- Ligne de synthèse + grille de détail = un seul bloc « grille 3×N » : .synthesis porte le
             gap VERTICAL (.toprow ↔ .cards) = gap horizontal entre colonnes (page-gutter) → espacement
             uniforme (le gap de .page__body, plus petit, ne s'applique plus entre ces deux blocs). -->
        <div class="synthesis">
        <!-- Ligne de synthèse (S4) sur UNE ligne (3 colonnes), alignée colonne par colonne avec la
             grille du détail ci-dessous (.cards) — mêmes largeurs / gap / breakpoints : Origine des
             calories + Variété + Profil macros (radar). -->
        <div class="toprow">
          <section class="catpanel">
            <!-- Graphe pleine largeur (mêmes hauteur/largeur que le Profil macros = grille du dessous),
                 légende DANS le graphe juste dessous, toggle Cercle/Radar EN BAS du panneau.
                 Titre placé DANS le cadre thirdBlue (gagne la hauteur de l'en-tête séparé). -->
            <div class="catpanel__body">
              <app-titled-divider title="Origine des calories" />
              <div class="chartwrap">
                @if (categoryView() === 'CIRCLE') {
                  <app-donut-chart
                    [slices]="originDonut()"
                    [height]="catChartHeight"
                    [centerLabel]="round(originTotalKcal()).toString()"
                    centerSub="kcal"
                    emptyText="—"
                  />
                } @else {
                  <app-radar-chart
                    [axes]="originRadar().axes"
                    [series]="originRadar().series"
                    [height]="catChartHeight"
                    radius="80%"
                    valueSuffix=" kcal"
                    [showLegend]="false"
                    [showAxisPercent]="true"
                    emptyText="—"
                  />
                }
              </div>
              <!-- Légende (règne dominant + part) → même structure que Variété / Profil : sans elle, le
                   cadre était plus court d'une ligne et laissait un espace vide sous lui (grid stretch). -->
              <span class="chart-caption">{{ originCaption() }}</span>
            </div>
          </section>

          <section class="catpanel">
            <!-- Symétrique à Origine : graphe pleine largeur. La ligne diversité / monotonie est posée
                 en overlay dans le coin bas-gauche du cadre thirdBlue (même légende que « moyenne / jour »
                 du Profil macros), pas en encart sous le graphe. Titre placé DANS le cadre. -->
            <div class="catpanel__body catpanel__body--overlay">
              <app-titled-divider title="Variété" />
              <div class="chartwrap">
                @if (categoryView() === 'CIRCLE') {
                  <app-donut-chart
                    [slices]="varietyDonut()"
                    [height]="catChartHeight"
                    [centerLabel]="variety().distinctGroups.toString()"
                    centerSub="groupes"
                    emptyText="—"
                  />
                } @else {
                  <app-radar-chart
                    [axes]="varietyRadar().axes"
                    [series]="varietyRadar().series"
                    [height]="catChartHeight"
                    radius="80%"
                    valueSuffix=" kcal"
                    [showLegend]="false"
                    [showAxisPercent]="true"
                    emptyText="—"
                  />
                }
              </div>
              <span class="chart-caption">{{ varietyCaption() }}</span>
            </div>
          </section>

          <!-- Profil macros de la période (composant DS radar), 3e colonne de la ligne de synthèse :
               profil consommé brut, ou consommé vs cible (2 séries) si une cible était active sur la
               période. Réutilise macroRadarData. -->
          <section class="catpanel">
            <!-- Profil macros : anneaux concentriques (Cercle) ou radar (Radar), sans légende. La légende
                 « moyenne / jour » est posée en overlay dans le coin bas-gauche du cadre thirdBlue.
                 Titre placé DANS le cadre ; le graphe garde sa hauteur via .profile-chartarea (min-height)
                 → cadre aligné avec Origine/Variété dans les 2 modes. -->
            <div class="profile-chartwrap">
              <app-titled-divider title="Profil macros" />
              <div class="profile-chartarea" [style.min-height.px]="catChartHeight">
                @if (categoryView() === 'CIRCLE') {
                  <app-nutrition-summary-panel
                    display="concentric"
                    [kcal]="profileMacros().kcal"
                    [macros]="profileMacros()"
                    [targets]="profileTargets()"
                    [showMicros]="false"
                  />
                } @else {
                  <app-radar-chart
                    [axes]="profileRadar().axes"
                    [series]="profileRadar().series"
                    [height]="catChartHeight"
                    radius="80%"
                    [valueSuffix]="hasGoalTarget() ? '' : ' g'"
                    [showLegend]="false"
                    [showAxisPercent]="true"
                    emptyText="Aucun aliment sur cette période."
                  />
                }
              </div>
              <span class="chart-caption">{{ profileCaption() }}</span>
            </div>
          </section>
        </div>

        <div class="cards">
          @for (card of cards(); track card.key) {
            <article class="card">
              <div class="card__panel">
                <app-titled-divider
                  [title]="card.label + ' (' + card.unit + ')'"
                  [color]="card.color"
                />
                <app-nutrition-stats-chart
                  [buckets]="agg().buckets"
                  [consumed]="card.consumed"
                  [target]="card.target"
                  [color]="card.color"
                  chartType="LINE"
                  [granularity]="bounds().gran"
                  [unit]="card.unit"
                  [height]="260"
                />
                <app-titled-divider title="Top aliments" [color]="card.color" />
                @if (card.topFoods.length === 0) {
                  <p class="top__empty">Aucun aliment sur cette période.</p>
                } @else {
                  <ol class="top">
                    @for (f of card.topFoods; track f.key; let i = $index) {
                      <li class="top__row">
                        <span class="top__rank">{{ i + 1 }}</span>
                        <span class="top__name">{{ f.displayName }}</span>
                        <span class="top__share">{{ round(f.share * 100) }} %</span>
                        <span class="top__value" [style.color]="card.color"
                          >{{ formatValue(card.key, f.value) }} {{ card.unit }}</span
                        >
                      </li>
                    }
                  </ol>
                }
              </div>
            </article>
          }

          <!-- 6e graphe (synthèse) : 6e cellule de la grille, alignée avec les 5 cartes macros — les
               5 macros en % de l'objectif par bucket (multi-lignes, couleurs macro + légende, ligne
               repère 100 %). Buckets sans objectif actif → point sauté. -->
          <article class="card">
            <div class="card__panel">
              <app-titled-divider title="Macros — % de l'objectif" />
              <app-multi-line-chart
                [series]="macroPercent()"
                [xLabels]="percentLabels()"
                [markLineValue]="100"
                valueSuffix=" %"
                [showLegend]="true"
                [height]="320"
                emptyText="Aucun objectif actif sur la période."
              />
            </div>
          </article>
        </div>
        </div>
      </div>
    </section>

    <app-date-range-picker-dialog
      [open]="pickerOpen()"
      title="Sélectionner une période"
      [initialStart]="customStart()"
      [initialEnd]="customEnd()"
      (confirm)="confirmCustomRange($event)"
      (dismiss)="pickerOpen.set(false)"
    />
  `,
  styles: [
    `
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        /* Large : la grille de cartes profite de toute la largeur dispo (5 graphes côte à côte
           sur un grand écran) au lieu d'être capée à 1200px (qui forçait 3+2 + marges vides). */
        max-width: 1700px;
        margin: 0 auto;
        box-sizing: border-box;
        width: 100%;
      }
      .toolbar {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
        align-items: center;
      }
      .chips--range {
        flex: 1;
        min-width: 0;
      }
      .chip {
        height: 32px;
        padding: 0 12px;
        border-radius: var(--radius-md);
        border: 1px solid;
        background: transparent;
        cursor: pointer;
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        white-space: nowrap;
      }
      .chip--range {
        border-color: color-mix(in srgb, var(--app-text-secondary) 60%, transparent);
        color: var(--c-light-gray-blue);
        display: inline-flex;
        align-items: center;
        gap: 5px;
      }
      .chip--range-sel {
        border-color: var(--app-primary-action);
        background: var(--app-primary-action);
        color: var(--app-text-primary);
      }
      /* Bloc grille (ligne de synthèse + cartes détail) : gap vertical entre .toprow et .cards =
         le même que le gap horizontal entre colonnes (page-gutter) → espacement uniforme (grid 3×N). */
      .synthesis {
        /* Espace de la grille intérieure (entre les graphes) = moitié de la gouttière de page —
           appliqué horizontalement (.toprow / .cards) ET verticalement (.synthesis), via cascade. */
        --grid-gap: calc(var(--page-gutter) / 2);
        display: flex;
        flex-direction: column;
        gap: var(--grid-gap);
      }
      /* Ligne de synthèse : Origine + Variété + Profil macros sur 3 colonnes égales (chacun un seul
         graphe, piloté par le sélecteur partagé). */
      .toprow {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: var(--grid-gap);
        align-items: start;
      }
      @media (max-width: 1024px) {
        .toprow {
          grid-template-columns: repeat(2, 1fr);
        }
      }
      @media (max-width: 640px) {
        .toprow {
          grid-template-columns: 1fr;
        }
      }
      .catpanel {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        min-width: 0;
      }
      /* Corps du panneau : titre (titled-divider) + graphe pleine largeur (= taille du Profil macros /
         grille du dessous), dans un cadre thirdBlue. */
      .catpanel__body {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
      }
      /* Variante Variété : ancre l'overlay de la légende diversité / monotonie (coin bas-gauche). */
      .catpanel__body--overlay {
        position: relative;
      }
      /* Le graphe (donut/radar) porte déjà son propre fond thirdBlue : on le neutralise ici pour ne
         pas doubler le cadre (le corps du panneau le porte). Scopé à la page Stats nutrition. */
      .catpanel__body ::ng-deep .rc,
      .catpanel__body ::ng-deep .dc {
        background: transparent;
        padding: 0;
      }
      .chartwrap {
        width: 100%;
      }
      /* Légende sous le graphe, dans le même cadre thirdBlue : entrées [carré couleur][libellé][x %]
         sur 2 colonnes réparties équitablement sur la largeur (espaces égaux) avec un peu de marge au
         bord ; lignes alignées au même x dans chaque colonne. Texte sans fond (transparent). */
      .legend {
        list-style: none;
        margin: 0;
        padding: 0 var(--space-2);
        display: grid;
        grid-template-columns: auto auto;
        justify-content: space-evenly;
        gap: var(--space-1) var(--space-3);
      }
      .legend__row {
        display: inline-flex;
        align-items: center;
        gap: var(--space-1);
      }
      .legend__dot {
        width: 12px;
        height: 12px;
        border-radius: 3px;
        flex: none;
      }
      .legend__label {
        color: var(--app-text-primary);
        font-size: 14px;
        white-space: nowrap;
      }
      .legend__pct {
        font-size: 14px;
        font-weight: var(--font-weight-bold);
        font-variant-numeric: tabular-nums;
      }
      /* Cadre thirdBlue du Profil macros (radar OU barres) : porte le titre + la zone graphe.
         position relative pour la légende en overlay. Ajustements barres scopés via ::ng-deep. */
      .profile-chartwrap {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
      }
      /* Zone graphe seule (sous le titre) : porte le min-height inline (= catChartHeight) en content-box,
         pour garder la MÊME hauteur de graphe qu'Origine/Variété dans les 2 modes (titre exclu du calcul). */
      .profile-chartarea {
        box-sizing: content-box;
        display: flex;
        flex-direction: column;
      }
      /* Le radar/les barres portent déjà leur propre fond thirdBlue : on le neutralise (le wrap le porte). */
      .profile-chartwrap ::ng-deep .rc,
      .profile-chartwrap ::ng-deep .nsp {
        background: transparent;
        padding: 0;
      }
      /* Barres : remplissent la hauteur du cadre, centrées verticalement (margin auto). */
      .profile-chartwrap ::ng-deep app-nutrition-summary-panel {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;
      }
      .profile-chartwrap ::ng-deep .nsp {
        flex: 1;
        gap: var(--space-3);
      }
      .profile-chartwrap ::ng-deep .nsp__head + .nsp__row {
        margin-top: auto;
      }
      .profile-chartwrap ::ng-deep .nsp__row:last-child {
        margin-bottom: auto;
      }
      .profile-chartwrap ::ng-deep .nsp__kcal {
        justify-content: flex-end;
      }
      .profile-chartwrap ::ng-deep .nsp__head-aside {
        display: none;
      }
      /* Ligne d'infos (« Monotonie… » / « Moyenne / jour… ») en FLUX normal sous le graphe → gap
         uniforme (space-3, le gap du cadre) entre le graphe et l'info, symétrique avec le titre au-dessus.
         (Avant : overlay collé au bas du cadre, sans gap défini.) */
      .chart-caption {
        margin: 0;
        font-size: 11px;
        font-style: italic;
        color: var(--c-gray-blue);
        pointer-events: none;
      }
      .cards {
        display: grid;
        /* 3 graphes par ligne (plus grands) sur desktop ; 2 puis 1 sur écrans plus étroits.
           repeat(3) garde toutes les cartes à la même largeur : 5 cartes macros + le 6e graphe
           % objectif = 6 cellules, soit 2 lignes pleines, alignées avec la ligne de synthèse. */
        grid-template-columns: repeat(3, 1fr);
        /* Espace entre graphes = la gouttière (vertical + horizontal), comme entre panneaux des autres pages. */
        gap: var(--grid-gap);
        align-items: start;
      }
      @media (max-width: 1024px) {
        .cards {
          grid-template-columns: repeat(2, 1fr);
        }
      }
      @media (max-width: 640px) {
        .cards {
          grid-template-columns: 1fr;
        }
      }
      .card {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        box-sizing: border-box;
        /* Sans ça, la largeur min-content du graphe élargit la colonne 1fr (= minmax(auto,1fr)) et la
           grille déborde à droite : on autorise la carte à rétrécir → 3 colonnes égales, alignées sur
           la ligne de synthèse (.toprow, même repeat(3,1fr)). Miroir de .catpanel { min-width: 0 }. */
        min-width: 0;
      }
      /* Cadre thirdBlue (comme la ligne de synthèse) : sert au [titre + graphe] ET au
         [titre « Top aliments » + liste d'aliments]. */
      .card__panel {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        min-width: 0;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
      }
      /* Le graphe porte déjà son propre fond thirdBlue : on le neutralise (le panneau le porte). */
      .card__panel ::ng-deep .nsc,
      .card__panel ::ng-deep .mlc {
        background: transparent;
        padding: 0;
        border-radius: 0;
      }
      .top {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
      }
      /* Rangées plates (pas de pilule individuelle) séparées par un filet horizontal — comme les
         aliments d'un repas dans le journal (MacroEntryRow [divider]). Le cadre thirdBlue est porté
         par .card__panel ; le padding horizontal vient du panneau (rows à plat dedans). */
      .top__row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        padding: 8px 0;
        border-bottom: 1px solid var(--c-second-blue);
      }
      .top__row:last-child {
        border-bottom: none;
      }
      .top__rank {
        min-width: 20px;
        font-size: 12px;
        color: var(--c-light-gray-blue);
        font-variant-numeric: tabular-nums;
      }
      .top__name {
        flex: 1;
        min-width: 0;
        color: var(--app-text-primary);
        font-size: 14px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .top__share {
        font-size: 12px;
        color: var(--app-text-tertiary);
        font-variant-numeric: tabular-nums;
      }
      .top__value {
        font-size: 14px;
        font-weight: var(--font-weight-bold);
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
      }
      .top__empty {
        margin: 0;
        font-size: 13px;
        color: var(--app-text-secondary);
      }
    `,
  ],
})
export class NutritionStatsPage {
  private readonly sync = inject(SyncEngine);
  protected readonly mealRepo = inject(MealRepository);
  protected readonly goalRepo = inject(NutritionGoalRepository);
  private readonly foodRepo = inject(FoodRepository);

  protected readonly rangeChips = RANGE_CHIPS;
  protected readonly rangeKind = signal<RangeKey>('D30');
  protected readonly customStart = signal(addDaysRange(new Date(), 0));
  protected readonly customEnd = signal(addDaysRange(new Date(), 0));
  protected readonly pickerOpen = signal(false);

  /** Nombre de lignes « Top aliments » affichées par carte (top 5 max). */
  private readonly topPerCard = 5;

  /**
   * Hauteur commune des graphes de la ligne de synthèse (Origine / Variété / Profil macros) — homogène
   * avec le radar Profil macros, pris comme référence de taille (« = grille du dessous »).
   */
  protected readonly catChartHeight = 300;

  protected readonly bounds = computed(() => {
    const earliest = earliestMealDate(this.mealRepo.meals());
    return computeBounds(this.rangeKind(), this.customStart(), this.customEnd(), earliest);
  });

  protected readonly agg = computed(() => {
    const { startIso, endIso, gran } = this.bounds();
    return aggregateNutrition(
      this.mealRepo.entries(),
      this.mealRepo.meals(),
      this.goalRepo.goals(),
      startIso,
      endIso,
      gran,
    );
  });

  /** Map foodUUID → groupe curaté (catalogue), pour classer chaque entry par origine / variété. */
  private readonly foodGroupByUuid = computed(() => {
    const map = new Map<string, string | null>();
    for (const f of this.foodRepo.foods()) map.set(f.uuid, f.foodGroup);
    return map;
  });

  /** Répartition par origine (4 règnes) sur la période, en kcal — donut + légende. */
  protected readonly originSlices = computed<OriginSlice[]>(() => {
    const { startIso, endIso } = this.bounds();
    return aggregateByOrigin(
      this.mealRepo.entries(),
      this.mealRepo.meals(),
      this.foodGroupByUuid(),
      startIso,
      endIso,
    );
  });

  protected readonly originDonut = computed<DonutSlice[]>(() => originDonutSlices(this.originSlices()));

  /** Mêmes données que le donut, en représentation radar (4 axes règnes, kcal par origine). */
  protected readonly originRadar = computed(() => originRadarData(this.originSlices()));

  protected readonly originTotalKcal = computed(() =>
    this.originSlices().reduce((sum, s) => sum + s.kcal, 0),
  );

  /** Panneau Variété : groupes distincts + sources triées + signal de monotonie. */
  protected readonly variety = computed<VarietyStats>(() => {
    const { startIso, endIso } = this.bounds();
    return computeVariety(
      this.mealRepo.entries(),
      this.mealRepo.meals(),
      this.foodGroupByUuid(),
      startIso,
      endIso,
    );
  });

  /** Donut / radar des groupes de la Variété — mêmes données (kcal par groupe), 2 représentations. */
  protected readonly varietyDonut = computed<DonutSlice[]>(() => varietyDonutSlices(this.variety().groups));
  protected readonly varietyRadar = computed(() => varietyRadarData(this.variety().groups));

  /** Sélecteur de représentation par section (Cercle = donut / Radar). États UPPER_CASE (politique 11). */
  protected readonly viewSegments: SegmentItem[] = [
    { value: 'RADAR', icon: 'radar', description: 'Radar' },
    { value: 'CIRCLE', icon: 'donut_large', description: 'Cercle' },
  ];
  // Défaut RADAR app-wide (« vue ouverte »), pas le cercle/donut. Un SEUL sélecteur partagé pilote
  // Origine + Variété ensemble (placé tout à droite de la ligne des périodes).
  protected readonly categoryView = signal<CategoryView>('RADAR');

  protected setCategoryView(view: string): void {
    this.categoryView.set(view as CategoryView);
  }

  /** Une carte par macro (toutes affichées en même temps), recalculée sur la période globale. */
  protected readonly cards = computed<MacroCard[]>(() => {
    const { startIso, endIso } = this.bounds();
    const a = this.agg();
    const entries = this.mealRepo.entries();
    const meals = this.mealRepo.meals();
    return MACRO_KEYS.map((key) => {
      const hasTarget = (MACRO_TARGET_KEYS as readonly string[]).includes(key);
      return {
        key,
        label: MACRO_LABEL[key],
        color: MACRO_COLOR[key],
        unit: MACRO_UNIT[key],
        consumed: a.consumed[key],
        // Les fibres n'ont pas de champ goal : la cible est dérivée du kcal de l'objectif
        // (15 g / 1000 kcal), par bucket — même source que le journal (fiberTargetG).
        target:
          key === 'fiber'
            ? a.target.kcal.map((k) => (k / 1000) * FIBER_G_PER_1000_KCAL)
            : hasTarget
              ? a.target[key as MacroTargetKey]
              : [],
        topFoods: topFoodsByMacro(entries, meals, startIso, endIso, key).slice(0, this.topPerCard),
      };
    });
  });

  /**
   * 6e graphe (synthèse multi-lignes) : 5 séries « % de l'objectif » (1 par macro) par bucket, +
   * étiquettes X formatées (J/M ou W##). Builder pur réutilisant l'agrégat (cibles via activeGoalAt).
   */
  protected readonly macroPercent = computed(() => macroPercentSeries(this.agg()));
  protected readonly percentLabels = computed(() => {
    const gran = this.bounds().gran;
    return this.agg().buckets.map((b) => formatBucketLabel(b, gran));
  });

  /**
   * Profil macro moyen /jour de la période (consommé + cible si active) — alimente le radar.
   * `bounds().days` = nb de jours calendaires (jours sans saisie comptés 0, cohérent /jour).
   */
  protected readonly periodProfile = computed(() =>
    periodMacroProfile(this.agg(), this.bounds().days),
  );

  protected readonly hasGoalTarget = computed(() => this.periodProfile().target !== null);

  /**
   * Axes + séries du radar : si une cible était active → 2 séries consommé vs objectif (% de la
   * cible, fibres dérivées du kcal cible) ; sinon 1 série « profil consommé » brut (g). Période sans
   * aliment ni cible → tableaux vides (placeholder du composant).
   */
  /** Profil consommé moyen /jour de la période (kcal + macros) — alimente le radar ET les barres. */
  protected readonly profileMacros = computed(() => this.periodProfile().consumed);
  /** Cible du Profil (fibres dérivées du kcal cible) — null si aucune cible active sur la période. */
  protected readonly profileTargets = computed<MacroTargets | null>(() => {
    const t = this.periodProfile().target;
    return t ? { ...t, fiber: (t.kcal / 1000) * FIBER_G_PER_1000_KCAL } : null;
  });

  protected readonly profileRadar = computed(() => {
    const p = this.periodProfile();
    if (!p.target && p.consumed.kcal <= 0) return { axes: [], series: [] };
    return macroRadarData(p.consumed, this.profileTargets(), { value: 'Consommé', target: 'Cible' });
  });

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }

  /** Légende sous le Profil macros (le titre de section reste « Profil macros »). */
  protected profileCaption(): string {
    return this.hasGoalTarget()
      ? 'Moyenne / jour — consommé vs cible'
      : 'Moyenne / jour (consommé)';
  }

  /**
   * Légende diversité / monotonie de la Variété, posée en overlay dans le cadre thirdBlue (comme la
   * légende « moyenne / jour » du Profil macros) : signal de monotonie si un aliment/groupe domine,
   * sinon bonne diversité, sinon période vide. Texte identique à l'ancien encart .infoline.
   */
  protected varietyCaption(): string {
    const v = this.variety();
    if (v.monotony.active) {
      const what = v.monotony.kind === 'FOOD' ? "l'aliment" : 'le groupe';
      return `Monotonie : ${what} « ${v.monotony.label} » pèse ${this.round(v.monotony.share * 100)} % des calories.`;
    }
    if (v.distinctGroups > 0) {
      return `Bonne diversité : aucun aliment ni groupe ne dépasse ${this.round(v.thresholdShare * 100)} % des calories.`;
    }
    return 'Aucun aliment sur cette période.';
  }

  /** Légende sous Origine des calories : règne dominant + sa part. Donne au panneau la MÊME structure
      (titre + graphe + légende) que Variété / Profil → même hauteur, fini l'espace vide sous le cadre. */
  protected originCaption(): string {
    const slices = this.originDonut();
    const total = slices.reduce((sum, s) => sum + s.value, 0);
    if (total <= 0) return 'Aucun aliment sur cette période.';
    const top = slices.reduce((a, b) => (b.value > a.value ? b : a));
    return `${top.label} — ${this.round((top.value / total) * 100)} % des calories.`;
  }

  /** kcal en entiers, grammes à la décimale (cohérent avec le reste de la page). */
  protected formatValue(key: MacroKey, v: number): number {
    return key === 'kcal' ? this.round(v) : this.round1(v);
  }

  protected round(v: number): number {
    return Math.round(v);
  }

  protected round1(v: number): number {
    return Math.round(v * 10) / 10;
  }

  protected selectRange(key: RangeKey): void {
    if (key === 'CUSTOM') {
      this.pickerOpen.set(true);
      return;
    }
    this.rangeKind.set(key);
  }

  /** Plage personnalisée confirmée (start/end déjà réordonnés par le dialog). */
  protected confirmCustomRange(range: { start: string; end: string }): void {
    this.customStart.set(range.start);
    this.customEnd.set(range.end);
    this.rangeKind.set('CUSTOM');
    this.pickerOpen.set(false);
  }
}
