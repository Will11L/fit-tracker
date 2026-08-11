import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { LocalFood } from '@core/models/food.model';
import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomHourPicker } from '@designsystem/common_components/custom-hour-picker';
import {
  OptionsBottomSheet,
  type SheetAction,
} from '@designsystem/common_components/options-bottom-sheet';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { ProgressBarPrimitive } from '@designsystem/common_components/progress-bar-primitive';
import { ProgressRing } from '@designsystem/common_components/progress-ring';
import { CalendarMonthGrid } from '@designsystem/common_components/calendar-month-grid';
import { ExpandableCard } from '@designsystem/common_components/expandable-card';
import { ConcentricRings, type ConcentricRing } from '@designsystem/common_components/concentric-rings';
import { RadarChartComponent } from '@designsystem/common_components/radar-chart';
import { RevealIn } from '@designsystem/common_components/reveal-in';
import {
  SegmentedIconToggle,
  type SegmentItem,
} from '@designsystem/common_components/segmented-icon-toggle';
import { AppIcon } from '@designsystem/icons/app-icon';
import { MACRO_COLOR, SUGAR_COLOR } from './macro-colors';
import { MICRO_COLOR, microLineItems } from './micro-colors';
import { MacroEntryRow, type MacroEntryRowData } from './macro-entry-row';
import { RING_MACRO_KEYS, dailyTotalsForMonth, type DayRingTotals } from './journal-month-utils';
import { microRows as buildMicroRows } from './micros';
import { SyncEngine } from '@core/sync/sync-engine';
import { FoodRepository } from './food.repository';
import { MealRepository } from './meal.repository';
import { NutritionGoalRepository } from './nutrition-goal.repository';
import { HydrationRepository } from './hydration.repository';
import { HydrationCard } from './hydration-card';
import { activeWaterGoalMl, dayHydrationMl } from './hydration';
import { FoodPickerSheet, parseMacro } from './food-picker-sheet';
import { MealPresetsSheet } from './meal-presets-sheet';
import {
  macroRadarData,
  microRadarData,
  type MacroAmounts,
  type MacroTargets,
} from './nutrition-summary-panel';
import {
  JournalSection,
  MacroTotals,
  buildSections,
  entrySugarG,
  entryTotals,
  fiberTargetG,
  legacyMealsToHeal,
  sugarLimitsG,
  sumMicroTotals,
  sumSugarG,
  sumTotals,
  todayIso,
} from './journal-utils';

/** Vue d'une section du bandeau (macros / micros) : barres de progression, anneaux circulaires ou radar. */
type SectionView = 'BARS' | 'RINGS' | 'RADAR';
const SUMMARY_VIEW_KEY = 'nutrition.summaryView';

function readSectionView(key: string): SectionView {
  const v = localStorage.getItem(key);
  // Défaut = 1er du sélecteur (Anneaux) ; un choix explicitement stocké (BARS/RADAR) reste prioritaire.
  return v === 'BARS' || v === 'RADAR' ? v : 'RINGS';
}

/** Cible du dialog de quantité : ajout (section + food) ou édition d'une entry existante. */
type QtyTarget =
  | { kind: 'add'; section: JournalSection; food: LocalFood }
  | { kind: 'edit'; entry: LocalMealEntry };

/**
 * Journal Nutrition (`/nutrition`, V4 NUTRITION_DESIGN §5.1) — navigation par jour (calendrier mensuel),
 * bandeau cumuls du jour vs cibles actives (kcal + P/G/L, barres de progression style app),
 * sections = meal_presets (D10, vides tant qu'aucune entry — pas de rows fantômes §3.4) + repas
 * ad hoc. Actions : ajouter un repas, ajouter un aliment (FoodPickerSheet : catalogue / OFF /
 * créer), éditer/supprimer une entry, supprimer un repas, dupliquer un repas passé.
 */
@Component({
  selector: 'app-nutrition-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    EmptyListRow,
    ActionIconButton,
    ActionIconWithTextButton,
    FormDialog,
    ConfirmationDialog,
    CustomTextField,
    CustomHourPicker,
    OptionsBottomSheet,
    AppBottomSheet,
    ProgressBarPrimitive,
    ProgressRing,
    CalendarMonthGrid,
    ConcentricRings,
    RadarChartComponent,
    SegmentedIconToggle,
    AppIcon,
    RevealIn,
    ExpandableCard,
    MacroEntryRow,
    FoodPickerSheet,
    MealPresetsSheet,
    HydrationCard,
  ],
  template: `
    <section class="page" [class.page--embedded]="embedded()">
      @if (!embedded()) { <app-screen-title-bar title="Nutrition" /> }

      <div class="page__body">
        <div class="split">
        <!-- COLONNE GAUCHE : bandeau résumé du jour EN HAUT, calendrier mensuel (cases 4 anneaux)
             EN-DESSOUS ; sur /nutrition, la sélection du jour se fait directement sur le calendrier. En
             mode embarqué (dashboard Accueil) le calendrier est masqué : on ne garde alors que le
             résumé du jour (toujours sur aujourd'hui ; la vue complète calendrier + jour vit sur /nutrition). -->
        <div class="split__cal">
          <!-- Bandeau cumuls du jour vs cibles actives (barres ou anneaux, style app). -->
        <div class="banner">
          @if (summaryView() === 'RADAR') {
            <!-- En-tête radar : « Macros » au-dessus du radar gauche, « Vitamines & minéraux »
                 au-dessus du radar droit, bascule à l'extrême droite — le tout sur une seule ligne. -->
            <div class="banner__radar-head">
              <app-titled-divider class="banner__td" title="Macros" />
              <div class="banner__radar-head__right">
                <app-titled-divider class="banner__td" title="Micros" />
                <app-segmented-icon-toggle
                  [items]="viewSegments"
                  [selected]="summaryView()"
                  (select)="setSummaryView($event)"
                />
              </div>
            </div>

            <!-- Radar : macros (gauche) + micros (droite) côte à côte, tous deux visibles. -->
            <div
              class="banner__radars"
              [class.mode-anim--right]="slideDir() === 'right'"
              [class.mode-anim--left]="slideDir() === 'left'"
            >
              <div class="banner__radar">
                <app-radar-chart
                  [axes]="macroRadar().axes"
                  [series]="macroRadar().series"
                  [height]="230"
                  [showLegend]="false"
                  [showAxisPercent]="true"
                  emptyText="Aucune donnée"
                />
                @if (!activeGoal()) {
                  <p class="banner__hint">
                    Aucune cible définie — définis tes cibles dans la page Objectifs.
                  </p>
                }
              </div>
              <div class="banner__radar">
                <app-radar-chart
                  [axes]="microRadar().axes"
                  [series]="microRadar().series"
                  [height]="230"
                  [showLegend]="false"
                  [showAxisPercent]="true"
                  axisPercentMode="value"
                  emptyText="Aucune donnée"
                />
              </div>
            </div>
          } @else {
            <!-- Macros : en-tête + bascule barres/anneaux. -->
            <div class="banner__head">
              <app-titled-divider class="banner__td" title="Macros" />
              <app-segmented-icon-toggle
                [items]="viewSegments"
                [selected]="summaryView()"
                (select)="setSummaryView($event)"
              />
            </div>

            <!-- Barres / anneaux : empilé pleine largeur, micros repliables. -->
            @switch (summaryView()) {
              @case ('BARS') {
                @for (row of bannerRows(); track row.label) {
                  <div
                    class="banner__row"
                    [class.mode-anim--right]="slideDir() === 'right'"
                    [class.mode-anim--left]="slideDir() === 'left'"
                  >
                    <span class="banner__label" [style.color]="row.color">{{ row.label }}</span>
                    <app-progress-bar-primitive
                      class="banner__bar"
                      [progress]="row.progress"
                      [color]="row.color"
                      [markerAt]="row.markerAt"
                      troughColor="var(--app-bg-surface)"
                    />
                    <span class="banner__value">
                      <span [style.color]="row.color">{{ row.valueText }}</span> {{ row.targetText }}
                    </span>
                  </div>
                }
              }
              @default {
                <!-- Macros : les 6 anneaux (5 macros + sucres) sur UNE ligne (demande user
                     2026-07-14), cellules en largeur égale — miroir du weight(1f) Android.
                     Les micros gardent le .rings de base (10 anneaux sur 2 lignes). -->
                <div
                  class="rings rings--row"
                  [class.mode-anim--right]="slideDir() === 'right'"
                  [class.mode-anim--left]="slideDir() === 'left'"
                >
                  @for (row of bannerRows(); track row.label) {
                    <div class="rings__cell">
                      <app-progress-ring
                        [progress]="row.progress"
                        [color]="row.color"
                        [label]="row.centerValue"
                        [sublabel]="row.centerSub"
                      />
                      <span class="rings__caption" [style.color]="row.color">{{ row.label }}</span>
                    </div>
                  }
                </div>
              }
            }

            @if (!activeGoal()) {
              <p class="banner__hint">
                Aucune cible définie — définis tes cibles dans la page Objectifs.
              </p>
            }

            <!-- Micros (secondaire, repliable) : en-tête « Vitamines & minéraux » (gauche) + chevron
                 afficher/masquer (droite) sur une seule ligne ; le contenu se déplie en dessous. -->
            <div class="banner__head banner__head--micros">
              @if (showMicros()) {
                <app-titled-divider class="banner__td" title="Micros" />
              }
              <button type="button" class="micros-toggle" (click)="showMicros.set(!showMicros())">
                <app-icon [name]="showMicros() ? 'expand_less' : 'expand_more'" [size]="18" />
                {{ showMicros() ? 'Masquer les micros' : 'Afficher les micros' }}
              </button>
            </div>

            <div class="micros-reveal" [class.micros-reveal--open]="showMicros()">
              <div class="micros-clip">
                <div class="micros-body">
              @switch (summaryView()) {
                @case ('BARS') {
                  @for (row of microRows(); track row.label) {
                    <div
                      class="banner__row"
                      [class.mode-anim--right]="slideDir() === 'right'"
                      [class.mode-anim--left]="slideDir() === 'left'"
                    >
                      <span class="banner__label" [style.color]="row.color">{{ row.label }}</span>
                      <app-progress-bar-primitive
                        class="banner__bar"
                        [progress]="row.progress"
                        [color]="row.color"
                        troughColor="var(--app-bg-surface)"
                      />
                      <span class="banner__value">
                        <span [style.color]="row.color">{{ row.valueText }}</span> {{ row.targetText }}
                      </span>
                    </div>
                  }
                }
                @default {
                  <div
                    class="rings"
                    [class.mode-anim--right]="slideDir() === 'right'"
                    [class.mode-anim--left]="slideDir() === 'left'"
                  >
                    @for (row of microRows(); track row.label) {
                      <div class="rings__cell">
                        <app-progress-ring
                          [progress]="row.progress"
                          [color]="row.color"
                          [label]="row.centerValue"
                          [sublabel]="row.centerSub"
                        />
                        <span class="rings__caption" [style.color]="row.color">{{ row.label }}</span>
                      </div>
                    }
                  </div>
                }
              }
              <p class="banner__hint banner__hint--spaced">
                Cibles = VNR UE (1169/2011). Sodium = plafond repère (≤ 2000 mg).
              </p>
              <!-- Note plafond sucres : sous la note VNR, même style (demande user 2026-07-14). -->
              <p class="banner__hint banner__hint--spaced">
                Sucres (totaux) : plafond = 5 % de la cible kcal en g, max 100 g — repère « idéal »
                à la moitié.
              </p>
                </div>
              </div>
            </div>
          }
        </div>

          @if (!embedded()) {
          <div class="cal-row">
          <div class="cal">
            <div class="cal__header">
              <app-action-icon-button icon="chevron_left" backgroundColor="var(--c-first-blue)" (clicked)="prevMonth()" />
              <button
                type="button"
                class="cal__month"
                (click)="goToday()"
                title="Revenir à aujourd'hui"
              >
                {{ monthLabel() }}
                @if (!isCurrentMonth()) {
                  <app-icon name="today" [size]="16" color="var(--app-primary-action)" />
                }
              </button>
              <app-action-icon-button icon="chevron_right" backgroundColor="var(--c-first-blue)" (clicked)="nextMonth()" />
            </div>

            <div class="cal__body">
            <div class="cal__weekdays">
              @for (w of weekdayLabels; track $index) {
                <span>{{ w }}</span>
              }
            </div>

            <app-calendar-month-grid
              [year]="calYear()"
              [month]="calMonth()"
              [firstDayOffset]="firstDayOffset()"
            >
              <ng-template let-iso>
                <button
                  type="button"
                  class="daycell"
                  [class.daycell--today]="iso === today"
                  [class.daycell--selected]="iso === day()"
                  (click)="day.set(iso)"
                  [title]="dayCellTitle(iso)"
                >
                  <app-concentric-rings [size]="46" [rings]="ringsFor(iso)" [gap]="1.5">
                    <span class="daycell__num">{{ dayNumOf(iso) }}</span>
                    @if (ringDataFor(iso).hasData) {
                      <span class="daycell__kcal">{{ round(ringDataFor(iso).totals.kcal) }}</span>
                    }
                  </app-concentric-rings>
                </button>
              </ng-template>
            </app-calendar-month-grid>
            </div>
          </div>
          <aside class="cal-side">
            <app-titled-divider title="Ce mois" />
            <div class="month-rings">
              <app-concentric-rings [size]="96" [rings]="monthRings()" [gap]="2">
                <span class="month-rings__val" [style.color]="macro.kcal">{{ monthStats().avgKcal }}</span>
                <span class="month-rings__unit">kcal / j</span>
              </app-concentric-rings>
            </div>
            <app-titled-divider title="Jours" icon="check" />
            <div class="stat">
              <span class="stat__value" style="color: var(--c-first-blue)">{{ monthStats().filledDays }}<span class="stat__unit">/ {{ monthStats().totalDays }}</span></span>
              <app-progress-bar-primitive
                class="stat__bar"
                [progress]="monthStats().fillRatio"
                color="var(--c-first-blue)"
                troughColor="var(--app-bg-surface)"
              />
            </div>
            <!-- Moyenne / jour des macros sur les jours saisis : petites cards colorées par macro. -->
            <app-titled-divider title="Moy. / jour" />
            <div class="macrocards">
              <div class="macrocard">
                <span class="stat__label" [style.color]="macro.carbs">Glucides</span>
                <span class="stat__value" [style.color]="macro.carbs">{{ monthStats().avgCarbs }}<span class="stat__unit">g</span></span>
              </div>
              <div class="macrocard">
                <span class="stat__label" [style.color]="macro.fat">Lipides</span>
                <span class="stat__value" [style.color]="macro.fat">{{ monthStats().avgFat }}<span class="stat__unit">g</span></span>
              </div>
              <div class="macrocard">
                <span class="stat__label" [style.color]="macro.protein">Protéines</span>
                <span class="stat__value" [style.color]="macro.protein">{{ monthStats().avgProtein }}<span class="stat__unit">g</span></span>
              </div>
              <div class="macrocard">
                <span class="stat__label" [style.color]="macro.fiber">Fibres</span>
                <span class="stat__value" [style.color]="macro.fiber">{{ monthStats().avgFiber }}<span class="stat__unit">g</span></span>
              </div>
            </div>
          </aside>
          </div>
          }
        </div>

        <!-- COLONNE DROITE : repas du jour sélectionné (la sélection du jour + le résumé sont
             au-dessus, dans la colonne de gauche / au-dessus en empilement).
             Entre en slide-down + fade ; re-animée (fondu seul) au changement de jour. -->
        <div class="split__detail" [appRevealIn]="day()">
        <!-- Hydratation : card au-dessus des repas, suit le jour sélectionné. -->
        <app-hydration-card
          [consumedMl]="hydration().consumedMl"
          [goalMl]="hydration().goalMl"
          [canUndo]="canUndoWater()"
          (add)="addWater($event)"
          (custom)="openWaterCustom()"
          (undo)="undoWater()"
          (editGoal)="openWaterGoal()"
        />

        <!-- Sections du journal : presets + repas ad hoc. -->
        @for (s of sections(); track s.key) {
          <app-expandable-card>
            <!-- En-tête (info, à gauche du chevron) : nom · heure · totaux du repas. -->
            <div header class="meal__head">
              <span class="meal__name">{{ s.name }}</span>
              @if (s.defaultTime) {
                <span class="meal__time">{{ s.defaultTime }}</span>
              }
            </div>
            <!-- Actions (à droite, avant le chevron) : totaux du repas + ajouter un aliment + menu. -->
            <div actions class="meal__actions">
              @if (s.entries.length > 0) {
                <span class="meal__totals">
                  <span [style.color]="macro.kcal">{{ round(s.totals.kcal) }} kcal</span> ·
                  <span [style.color]="macro.carbs">G {{ round1(s.totals.carbs) }}</span> ·
                  <span [style.color]="macro.fat">L {{ round1(s.totals.fat) }}</span> ·
                  <span [style.color]="macro.protein">P {{ round1(s.totals.protein) }}</span> ·
                  <span [style.color]="macro.fiber">F {{ round1(s.totals.fiber) }}</span>
                </span>
              }
              <app-action-icon-button
                icon="add"
                backgroundColor="var(--app-primary-action)"
                [size]="34"
                [iconSize]="20"
                (clicked)="openPicker(s)"
              />
              <app-action-icon-button
                icon="more_vert"
                backgroundColor="var(--c-first-blue)"
                [size]="34"
                [iconSize]="20"
                (clicked)="mealForOptions.set(s)"
              />
            </div>

            <!-- Corps déroulé/enroulé : aliments du repas (ou état vide). -->
            @if (s.entries.length === 0) {
              <app-empty-list-row
                text="Aucun aliment — ajoute-en un (bouton +)."
                backgroundColor="transparent"
                contentColor="var(--c-gray-blue)"
                [verticalPadding]="0"
              />
            } @else {
              @for (e of s.entries; track e.uuid; let last = $last) {
                <app-macro-entry-row [data]="entryRow(e)" [divider]="!last">
                  <!-- Grammes effectifs seuls (le libellé de portion serait redondant). -->
                  <span trailing class="entry__qty">{{ round(e.quantityG) }} g</span>
                  <!-- Menu (⋮) de la row : fond firstBlue + icône blanche (défaut). -->
                  <app-action-icon-button
                    trailing
                    icon="more_vert"
                    backgroundColor="var(--c-first-blue)"
                    [size]="34"
                    [iconSize]="20"
                    (clicked)="entryForOptions.set(e)"
                  />
                </app-macro-entry-row>
              }
            }
          </app-expandable-card>
        }

        @if (sections().length === 0) {
          <app-empty-list-row
            text="Aucun repas — ajoute-en un via « Gérer les repas »."
            icon="restaurant"
          />
        }

        <app-titled-divider title="Actions" />
        <!-- Actions du jour, en bas des repas : gérer les repas (récurrents) · dupliquer un repas passé · ajouter une collation (ponctuelle). -->
        <div class="dayactions">
          <app-action-icon-with-text-button
            icon="tune"
            text="Gérer les repas"
            backgroundColor="var(--c-first-blue)"
            (clicked)="showPresets.set(true)"
          />
          <app-action-icon-with-text-button
            class="dayactions__push"
            icon="content_copy"
            text="Dupliquer un repas passé"
            backgroundColor="var(--c-first-blue)"
            [disabled]="pastMeals().length === 0"
            (clicked)="showDuplicate.set(true)"
          />
          <app-action-icon-with-text-button
            icon="add"
            text="Ajouter une collation"
            (clicked)="openAddMeal()"
          />
        </div>
        </div>
        </div>
      </div>

      <!-- Gestion des périodes habituelles (meal_presets, D10). -->
      <app-meal-presets-sheet
        [open]="showPresets()"
        (dismissRequest)="showPresets.set(false)"
        (duplicateRequested)="showPresets.set(false); showDuplicate.set(true)"
      />

      <!-- Recherche/ajout d'aliment (catalogue / OFF / créer). -->
      <app-food-picker-sheet
        [open]="pickerSection() !== null"
        (dismissRequest)="pickerSection.set(null)"
        (foodPicked)="onFoodPicked($event)"
      />

      <!-- Quantité (ajout ou édition d'une entry) : grammes + portions nommées de l'aliment. -->
      <app-form-dialog
        [open]="qtyTarget() !== null"
        [title]="qtyTitle()"
        confirmText="Valider"
        [confirmEnabled]="qtyValid()"
        disabledReason="Quantité en grammes requise (> 0)"
        (confirm)="confirmQty()"
        (dismiss)="qtyTarget.set(null)"
      >
        @if (qtyChips().length) {
          <div class="portions">
            @for (c of qtyChips(); track c.key) {
              <button
                type="button"
                class="portions__chip"
                [class.portions__chip--selected]="qtyPortionLabel() === c.snapshot"
                (click)="usePortion(c.snapshot, c.grams)"
              >
                {{ c.label }}
              </button>
            }
          </div>
        }
        <app-custom-text-field
          label="Quantité (g)"
          placeholder="100"
          [value]="qtyValue()"
          (valueChange)="onQtyInput($event)"
        />
      </app-form-dialog>

      <!-- Réglage de l'objectif d'hydratation (ml / jour) — édité depuis la card. -->
      <app-form-dialog
        [open]="showWaterGoal()"
        title="Objectif d'hydratation"
        confirmText="Enregistrer"
        [confirmEnabled]="waterGoalValid()"
        disabledReason="Volume en ml requis (> 0)"
        (confirm)="submitWaterGoal()"
        (dismiss)="showWaterGoal.set(false)"
      >
        <app-custom-text-field
          label="Objectif (ml / jour)"
          placeholder="2000"
          [value]="waterGoalInput()"
          (valueChange)="onWaterGoalInput($event)"
        />
        <p class="water-goal-reco">~2 L/j (F) · ~2,5 L/j (H)</p>
      </app-form-dialog>

      <!-- Ajout d'eau perso (ml). -->
      <app-form-dialog
        [open]="showWaterDialog()"
        title="Ajouter de l'eau"
        confirmText="Ajouter"
        [confirmEnabled]="waterValid()"
        disabledReason="Volume en ml requis (> 0)"
        (confirm)="submitWaterCustom()"
        (dismiss)="showWaterDialog.set(false)"
      >
        <app-custom-text-field
          label="Volume (ml)"
          placeholder="500"
          [value]="waterInput()"
          (valueChange)="onWaterInput($event)"
        />
      </app-form-dialog>

      <!-- Ajout d'une collation (repas ponctuel ad hoc, sans preset). -->
      <app-form-dialog
        [open]="showAddMeal()"
        title="Ajouter une collation"
        confirmText="Ajouter"
        [confirmEnabled]="mealName().trim().length > 0"
        disabledReason="Nom requis"
        (confirm)="submitAddMeal()"
        (dismiss)="showAddMeal.set(false)"
      >
        <app-custom-text-field
          label="Nom"
          placeholder="Ex. En-cas de 16h"
          [value]="mealName()"
          (valueChange)="mealName.set($event)"
        />
        <app-custom-hour-picker
          label="Heure du repas (facultatif)"
          [value]="mealTime()"
          (valueChange)="mealTime.set($event)"
        />
      </app-form-dialog>

      <!-- Dupliquer un repas passé : liste des repas non vides des jours précédents. -->
      <app-bottom-sheet [open]="showDuplicate()" (dismissRequest)="showDuplicate.set(false)">
        <div class="dup">
          <app-titled-divider title="Dupliquer un repas passé" />
          @for (m of pastMeals(); track m.meal.uuid) {
            <div class="dup__row">
              <!-- Gauche : nom du repas + date dessous. -->
              <div class="dup__main">
                <span class="dup__name">{{ m.meal.name }}</span>
                <span class="dup__date">{{ formatShortDate(m.meal.date) }}</span>
              </div>
              <!-- Macros (code couleur) puis nombre d'aliments : répartis équitablement avec le bloc nom/date (space-between sur .dup__row). -->
              <span class="dup__macros">
                <span [style.color]="macro.kcal">{{ round(m.totals.kcal) }} kcal</span> ·
                <span [style.color]="macro.carbs">G {{ round1(m.totals.carbs) }}</span> ·
                <span [style.color]="macro.fat">L {{ round1(m.totals.fat) }}</span> ·
                <span [style.color]="macro.protein">P {{ round1(m.totals.protein) }}</span> ·
                <span [style.color]="macro.fiber">F {{ round1(m.totals.fiber) }}</span>
              </span>
              <span class="dup__count">{{ m.count }} aliment{{ m.count > 1 ? 's' : '' }}</span>
              <!-- Droite : bouton dupliquer (fond = couleur du bouton « Dupliquer un repas passé »). -->
              <app-action-icon-button
                icon="content_copy"
                backgroundColor="var(--c-first-blue)"
                [size]="34"
                [iconSize]="20"
                (clicked)="duplicateMeal(m.meal)"
              />
            </div>
          }
        </div>
      </app-bottom-sheet>

      <!-- ⋮ entry : modifier la quantité / supprimer. -->
      <app-options-bottom-sheet
        [open]="entryForOptions() !== null"
        [title]="entryForOptions()?.displayName ?? ''"
        [actions]="entryActions()"
        (dismissRequest)="entryForOptions.set(null)"
        (actionSelected)="onEntryOption($event)"
      />

      <!-- ⋮ repas : supprimer (avec ses entries). -->
      <app-options-bottom-sheet
        [open]="mealForOptions() !== null"
        [title]="mealForOptions()?.name ?? ''"
        [actions]="mealActions()"
        (dismissRequest)="mealForOptions.set(null)"
        (actionSelected)="onMealOption($event)"
      />

      <app-confirmation-dialog
        [open]="mealToDelete() !== null"
        title="Supprimer le repas"
        [message]="deleteMealMsg()"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDeleteMeal()"
        (dismiss)="mealToDelete.set(null)"
      />
    </section>
  `,
  styles: [
    `
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        box-sizing: border-box;
        width: 100%;
      }
      /* Master-detail 2 colonnes (même pattern que muscles-page / planning-page). */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__cal {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        position: sticky;
        top: var(--space-3);
      }
      .split__detail {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
        .split__cal,
        .split__detail {
          flex: none;
          width: 100%;
          position: static;
        }
      }
      /* Mode embarqué (dashboard Accueil) : calendrier masqué → une seule colonne, le bandeau
         résumé du jour s'empile au-dessus des repas (indépendamment de la largeur du viewport,
         la colonne du dashboard étant déjà étroite). */
      .page--embedded .split {
        flex-direction: column;
        /* La gouttière (= gap du .split) est neutralisée à 0 dans le dashboard Accueil : sans ça,
           le bandeau résumé du jour (.banner) collerait au divider « Repas ». On rétablit un écart
           vertical explicite entre les deux sous-colonnes empilées. */
        gap: var(--space-5);
      }
      .page--embedded .split__cal,
      .page--embedded .split__detail {
        flex: none;
        width: 100%;
        position: static;
      }
      /* Calendrier mensuel (cases à 4 anneaux). */
      /* Calendrier + panneau « Repères » côte à côte : le panneau comble l'espace à droite (le
         calendrier, plafonné à 520px, flottait centré) et aligne le bloc sur la largeur du résumé. */
      .cal-row {
        display: flex;
        gap: var(--space-3);
        align-items: stretch;
      }
      .cal {
        flex: 1 1 auto;
        min-width: 0;
        max-width: 520px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique 16px ; bas réduit (space-2) : l'en-tête bord-à-bord occupe déjà le haut →
           l'ancien space-4 laissait un blanc dissymétrique sous la dernière semaine. */
        padding: 16px 16px var(--space-2);
        box-sizing: border-box;
        /* Clippe le bandeau d'en-tête (marges négatives, bord-à-bord) aux coins arrondis de la card. */
        overflow: hidden;
        /* Flex column → le corps (.cal__body, flex:1) répartit ses lignes (initiales + semaines) de
           façon ÉQUITABLE (space-evenly) dans la hauteur dispo. En-tête bord-à-bord reste collé en haut. */
        display: flex;
        flex-direction: column;
      }
      .cal__body {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
      }
      /* Bande verticale à droite, aussi haute que le calendrier (align-items: stretch) :
         légende des 4 anneaux + mini-stats du mois affiché. */
      .cal-side {
        flex: 0 0 132px;
        min-width: 0;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3);
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      /* Cards « Moy. / jour » : 1 par macro (g/jour), fond bg-surface ; réutilise .stat__label/value/unit. */
      .macrocards {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .macrocard {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: var(--space-2);
        background: var(--c-second-blue);
        border-radius: var(--radius-sm);
        padding: var(--space-1) var(--space-2);
      }
      /* Valeur des cards macro réduite à ~la taille du libellé (moins imposante que le X/30 des Jours). */
      .macrocard .stat__value {
        font-size: 11px;
      }
      /* Anneaux concentriques du mois (kcal + G/L/P, comme une case du calendrier) : centrés,
         moyenne kcal/jour au centre. */
      .month-rings {
        display: flex;
        justify-content: center;
        padding: var(--space-1) 0 var(--space-2);
      }
      .month-rings__val {
        font-size: 16px;
        font-weight: 700;
        line-height: 1;
      }
      .month-rings__unit {
        font-size: 8px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.03em;
        color: var(--c-gray-blue);
      }
      /* Jours : valeur X/30 + barre de remplissage sur UNE ligne. */
      .stat {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .stat__label {
        font-size: 10px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--c-gray-blue);
      }
      .stat__value {
        font-size: 14px;
        font-weight: 700;
        line-height: 1;
        color: var(--app-text-primary);
        white-space: nowrap;
      }
      /* Unité (kcal, g, / N) plus petite et grise, même quand la valeur est colorée (macro). */
      .stat__unit {
        margin-left: 3px;
        font-size: 11px;
        font-weight: 600;
        color: var(--c-gray-blue);
      }
      .stat__bar {
        flex: 1;
        min-width: 0;
      }
      /* Écran étroit : calendrier pleine largeur, panneau Repères empilé en dessous. */
      @media (max-width: 900px) {
        .cal-row {
          flex-direction: column;
        }
        .cal {
          max-width: none;
        }
        .cal-side {
          flex: none;
          width: 100%;
        }
      }
      .cal__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        /* En-tête « card » BORD-À-BORD (comme le header d'une carte repas / app-expandable-card) :
           marges négatives qui annulent le padding de la card → le bandeau second-blue touche le haut
           et les côtés ; coins arrondis en haut seulement (raccordé au corps), clippé via .cal overflow.
           Padding = 0 → les chevrons collent aux bords (gauche/droite + haut/bas) du bandeau. */
        background: var(--c-second-blue);
        border-radius: var(--radius-md) var(--radius-md) 0 0;
        margin: -16px -16px var(--space-3);
        padding: 0;
      }
      .cal__month {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        height: 34px; /* = hauteur des boutons chevrons (défaut 34) */
        background: var(--c-first-blue);
        border: none;
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        cursor: pointer;
        color: var(--app-text-tertiary);
        font-family: var(--font-family-base);
        font-size: 15px; /* = .meal__name (noms de repas, colonne droite) */
        font-weight: var(--font-weight-medium);
        text-transform: capitalize;
      }
      .cal__weekdays {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 6px;
        /* Air sous les initiales = air au-dessus (header margin-bottom space-3) → ligne aérée symétriquement. */
        margin-bottom: var(--space-3);
      }
      /* Initiales L M M J V S D : même style que la démo CalendarMonthGrid du showcase. */
      .cal__weekdays span {
        text-align: center;
        font-size: 13px;
        font-weight: 600;
        color: var(--c-light-gray-blue);
      }
      .daycell {
        aspect-ratio: 1;
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: transparent;
        border: 1px solid transparent;
        border-radius: var(--radius-sm);
        cursor: pointer;
        padding: 2px;
        box-sizing: border-box;
      }
      /* Hover : pas de fond, juste une bordure de la couleur du hover (bg-surface). Exclut le jour
         sélectionné (orange) et aujourd'hui (bleu) pour que leur bordure reste visible au survol. */
      .daycell:not(.daycell--selected):not(.daycell--today):hover {
        border-color: var(--app-bg-surface);
      }
      .daycell--today {
        border-color: var(--app-primary-action);
      }
      /* Jour sélectionné : même bordure, en orange (pas de fond). */
      .daycell--selected {
        border-color: var(--c-orange-medium);
      }
      .daycell__num {
        color: var(--app-text-primary);
        font-size: 12px;
        font-weight: var(--font-weight-medium);
      }
      .daycell__kcal {
        color: var(--app-text-tertiary);
        font-size: 8px;
        font-variant-numeric: tabular-nums;
      }
      .banner {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
        display: flex;
        flex-direction: column;
        /* Espace UNIFORME entre titre ↔ graphe ↔ texte : source unique de l'espacement vertical
           (les marges de section sont retirées). space-3 → space-4 le 2026-07-14 (retour user :
           divider Macros / anneaux / barres un poil trop rapprochés). */
        gap: var(--space-4);
      }
      .banner__head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-3);
      }
      /* En-tête micros (titre V&M, affiché seulement déplié, + chevron) : air au-dessus pour le séparer
         de la section macros ; pas de marge en dessous (l'air avant les barres vit dans le dépli animé
         → pas d'espace mort une fois replié). */
      .banner__head--micros {
        margin-top: 0;
        margin-bottom: 0;
      }
      /* Titres de section du bandeau (Macros / Vitamines & minéraux) : titled-dividers (traits +
         titre centré, cohérent avec « Calendrier » / « Résumé du jour ») ; flex:1 pour occuper la
         ligne à gauche du toggle. */
      .banner__td {
        flex: 1 1 0;
        min-width: 0;
      }
      /* En-tête du mode radar : « Macros » (moitié gauche, calée sur le radar macros) + bloc droit
         (« Vitamines & minéraux » + bascule). Les deux moitiés (flex:1) calquent les 2 radars 50/50,
         donc « Vitamines & minéraux » s'aligne au-dessus du radar droit et la bascule file à droite. */
      .banner__radar-head {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .banner__radar-head__right {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
      }
      /* Mode radar : macros (gauche) + micros (droite) côte à côte (50/50, jamais empilés). */
      .banner__radars {
        display: flex;
        gap: var(--space-3);
      }
      .banner__radar {
        flex: 1 1 0;
        min-width: 0;
      }
      .banner__row {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .rings {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-3) var(--space-4);
        justify-content: center;
        padding: var(--space-2) 0;
      }
      .rings__cell {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-1);
        width: 84px;
      }
      .rings__caption {
        color: var(--app-text-secondary);
        font-size: 12px;
        text-align: center;
      }
      /* Variante « une ligne » (anneaux macros) : pas de wrap, cellules en largeur égale. */
      .rings--row {
        flex-wrap: nowrap;
      }
      .rings--row .rings__cell {
        width: auto;
        flex: 1 1 0;
        min-width: 0;
      }
      /* Chevron afficher/masquer les micros : dans l'en-tête « Vitamines & minéraux », collé à droite
         (margin-left:auto → reste à droite même quand le titre V&M est masqué, micros repliés). */
      .micros-toggle {
        margin-left: auto;
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        background: transparent;
        border: none;
        padding: var(--space-1) 0;
        cursor: pointer;
        color: var(--app-accent-text);
        font-family: var(--font-family-base);
        font-size: 13px;
        font-weight: var(--font-weight-medium);
      }
      /* Dépli animé des micros (même technique que .entry__micros-reveal / ExpandableCard : la grille
         anime la hauteur 0fr↔1fr, le clip masque le débordement → contenu toujours dans le DOM).
         margin-top négatif = annule la gouttière flex du bandeau au-dessus du dépli, pour que l'espace
         entre l'en-tête micros et les barres vive ENTIÈREMENT dans le clip (padding-top) → il s'anime
         et disparaît au repli (plus de gouttière statique visible quand c'est replié). */
      .micros-reveal {
        display: grid;
        grid-template-rows: 0fr;
        margin-top: calc(-1 * var(--space-2));
        transition: grid-template-rows var(--motion-base) var(--motion-ease);
      }
      .micros-reveal--open {
        grid-template-rows: 1fr;
      }
      .micros-clip {
        overflow: hidden;
        min-height: 0;
      }
      /* L'espace en-tête→barres vit sur le corps INTERNE (pas sur le clip) : le padding d'un clip
         fait partie de sa propre boîte → il ne se replie pas. Ici, clippé/animé avec le contenu. */
      .micros-body {
        padding-top: var(--space-4);
      }
      .banner__label {
        width: 76px;
        flex-shrink: 0;
        color: var(--app-text-secondary);
        font-size: 13px;
      }
      .banner__bar {
        flex: 1;
      }
      .banner__value {
        min-width: 130px;
        text-align: right;
        color: var(--app-text-primary);
        font-size: 13px;
        font-variant-numeric: tabular-nums;
      }
      .banner__hint {
        margin: 0;
        font-size: 12px;
        font-style: italic;
        color: var(--app-text-tertiary);
      }
      /* Transition de mode : classes globales .mode-anim--left/right (styles.scss). */
      /* Note VNR sous les micros : un peu d'air après la dernière barre (Vit. A). */
      .banner__hint--spaced {
        margin-top: var(--space-2);
        color: var(--c-gray-blue);
      }
      .dayactions {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        flex-wrap: wrap;
      }
      /* Espace dynamique : pousse « Dupliquer » + « Ajouter » à droite ; « Gérer » reste à gauche. */
      .dayactions__push {
        margin-left: auto;
      }
      /* En-tête du repas (slot « info » de la carte dépliable) : nom · heure · totaux. */
      .meal__head {
        display: flex;
        align-items: baseline;
        flex-wrap: wrap;
        gap: var(--space-2);
      }
      .meal__name {
        color: var(--app-text-primary);
        font-size: 15px;
        font-weight: 600;
      }
      .meal__time {
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
      /* Totaux du repas (kcal · G · L · P · F colorés), dans l'en-tête à côté du nom. */
      /* Totaux du repas : valeurs colorées (spans macro) ; les « · » héritent du gris-bleu du conteneur. */
      .meal__totals {
        margin-right: var(--space-2);
        font-size: 12px;
        font-weight: var(--font-weight-medium);
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
        color: var(--c-gray-blue);
      }
      /* Slot « actions » de la carte (à droite, avant le chevron) : totaux + boutons du repas. */
      .meal__actions {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      /* Grammes (+ portion) de l'aliment, projetés à droite de la ligne (slot [trailing] de MacroEntryRow). */
      .entry__qty {
        flex-shrink: 0;
        margin-right: var(--space-2);
        white-space: nowrap;
        text-align: right;
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
      /* a11y : pas d'animation si l'utilisateur a demandé moins de mouvement (micros du bandeau). */
      @media (prefers-reduced-motion: reduce) {
        .micros-reveal {
          transition: none;
        }
      }
      .portions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
      }
      /* Chip de portion : bordure colorée + fond transparent par défaut ; fond plein si sélectionnée. */
      .portions__chip {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        background: var(--c-third-blue);
        color: var(--c-blue-medium);
        border: 1.5px solid var(--c-blue-medium);
        border-radius: var(--radius-pill);
        padding: 6px var(--space-3);
        font-family: var(--font-family-base);
        font-size: 12px;
        line-height: 1;
        cursor: pointer;
        transition: background 0.15s ease, color 0.15s ease;
      }
      .portions__chip--selected {
        background: var(--c-blue-medium);
        color: #fff;
      }
      .dup {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding: 0 var(--space-4) var(--space-3);
      }
      .dup__row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-3);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 8px var(--space-3);
      }
      /* Gauche : nom du repas + date (en colonne). */
      .dup__main {
        flex: 0 1 auto;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .dup__name {
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .dup__date {
        color: var(--c-gray-blue);
        font-size: 12px;
      }
      .dup__count {
        color: var(--c-gray-blue);
        font-size: 12px;
        white-space: nowrap;
        flex-shrink: 0;
      }
      /* Macros : chaque valeur garde sa couleur (spans) ; les « · » héritent du gris-bleu du conteneur. */
      .dup__macros {
        color: var(--c-gray-blue);
        font-size: 12px;
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
        flex-shrink: 0;
      }
      /* Repère EFSA sous le champ du dialog d'objectif d'hydratation. */
      .water-goal-reco {
        margin: var(--space-1) 0 0;
        color: var(--c-gray-blue);
        font-size: 11px;
      }

    `,
  ],
})
export class NutritionPage {
  /**
   * Mode embarqué (dashboard Accueil) : masque la title bar « Nutrition » et le calendrier mensuel,
   * ne garde que le bandeau résumé du jour + les repas (vue jour réutilisée, pas de duplication).
   * La route /nutrition garde sa vue complète (calendrier + jour).
   */
  readonly embedded = input(false);

  private readonly sync = inject(SyncEngine);
  protected readonly foodRepo = inject(FoodRepository);
  protected readonly mealRepo = inject(MealRepository);
  protected readonly goalRepo = inject(NutritionGoalRepository);
  protected readonly hydrationRepo = inject(HydrationRepository);
  private readonly router = inject(Router);

  /** Convention code couleur par macro (lue dans le template pour colorer kcal/P/G/L/fibres). */
  protected readonly macro = MACRO_COLOR;

  /** Jour sélectionné (master-detail) — aujourd'hui par défaut. */
  protected readonly day = signal(todayIso());
  protected readonly today = todayIso();

  // -------------------- Calendrier mensuel (colonne gauche) --------------------

  /** Abréviations Lun Mar Mer Jeu Ven Sam Dim, semaine lundi-first. */
  protected readonly weekdayLabels = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  /** Mois affiché {y, m(0-11)} — initialisé sur le mois du jour sélectionné. */
  private readonly cursor = signal(this.monthOf(todayIso()));

  protected readonly calYear = computed(() => this.cursor().y);
  protected readonly calMonth = computed(() => this.cursor().m);

  protected readonly firstDayOffset = computed(() => {
    const { y, m } = this.cursor();
    return (new Date(y, m, 1).getDay() + 6) % 7; // lundi-first
  });

  protected readonly monthLabel = computed(() => {
    const { y, m } = this.cursor();
    return new Date(y, m, 1).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  });

  protected readonly isCurrentMonth = computed(() => {
    const cur = this.monthOf(this.today);
    return this.cursor().y === cur.y && this.cursor().m === cur.m;
  });

  /** ISO de tous les jours du mois affiché. */
  private readonly monthDayIsos = computed(() => {
    const { y, m } = this.cursor();
    const days = new Date(y, m + 1, 0).getDate();
    const out: string[] = [];
    for (let d = 1; d <= days; d++) out.push(`${y}-${this.pad(m + 1)}-${this.pad(d)}`);
    return out;
  });

  /** Cumuls + progression par jour du mois (helper pur), pour les 4 anneaux des cases. */
  private readonly monthRingData = computed<Map<string, DayRingTotals>>(() =>
    dailyTotalsForMonth(
      this.monthDayIsos(),
      this.mealRepo.entries(),
      this.mealRepo.meals(),
      this.goalRepo.goals(),
    ),
  );

  /** Stats du mois affiché (panneau « Repères » à droite du calendrier) : moyenne kcal sur les
   *  jours saisis + nombre de jours saisis sur le total de jours du mois. */
  protected readonly monthStats = computed(() => {
    const map = this.monthRingData();
    let filledDays = 0;
    let sumKcal = 0;
    let sumProtein = 0;
    let sumCarbs = 0;
    let sumFat = 0;
    let sumFiber = 0;
    for (const iso of this.monthDayIsos()) {
      const d = map.get(iso);
      if (d?.hasData) {
        filledDays += 1;
        sumKcal += d.totals.kcal;
        sumProtein += d.totals.protein;
        sumCarbs += d.totals.carbs;
        sumFat += d.totals.fat;
        sumFiber += d.totals.fiber;
      }
    }
    const totalDays = this.monthDayIsos().length;
    const avg = (sum: number) => (filledDays > 0 ? Math.round(sum / filledDays) : 0);
    return {
      filledDays,
      totalDays,
      fillRatio: totalDays > 0 ? filledDays / totalDays : 0,
      avgKcal: avg(sumKcal),
      avgProtein: avg(sumProtein),
      avgCarbs: avg(sumCarbs),
      avgFat: avg(sumFat),
      avgFiber: avg(sumFiber),
    };
  });

  /** Couleur de chaque anneau (extérieur kcal → intérieur protéines), convention MACRO_COLOR. */
  private readonly ringWidths: Record<string, number> = { kcal: 5, carbs: 3, fat: 3, protein: 3 };

  /** Anneaux concentriques « du mois » (mêmes 4 macros que les cases du calendrier) : progression
   *  moyenne d'adhérence par macro sur les jours saisis. 0 partout si aucune cible (anneaux vides). */
  protected readonly monthRings = computed<ConcentricRing[]>(() => {
    const map = this.monthRingData();
    let filled = 0;
    const sum: Record<string, number> = { kcal: 0, carbs: 0, fat: 0, protein: 0 };
    for (const iso of this.monthDayIsos()) {
      const d = map.get(iso);
      if (d?.hasData) {
        filled += 1;
        for (const key of RING_MACRO_KEYS) sum[key] += d.progress[key];
      }
    }
    return RING_MACRO_KEYS.map((key) => ({
      progress: filled > 0 ? sum[key] / filled : 0,
      color: MACRO_COLOR[key],
      width: this.ringWidths[key],
    }));
  });

  protected ringDataFor(iso: string): DayRingTotals {
    return (
      this.monthRingData().get(iso) ?? {
        date: iso,
        totals: { kcal: 0, protein: 0, carbs: 0, fat: 0, fiber: 0 },
        hasData: false,
        targets: { kcal: null, carbs: null, fat: null, protein: null },
        progress: { kcal: 0, carbs: 0, fat: 0, protein: 0 },
      }
    );
  }

  /** 4 anneaux concentriques d'une case : kcal (extérieur) puis glucides / lipides / protéines. */
  protected ringsFor(iso: string): ConcentricRing[] {
    const data = this.ringDataFor(iso);
    return RING_MACRO_KEYS.map((key) => ({
      progress: data.progress[key],
      color: MACRO_COLOR[key],
      width: this.ringWidths[key],
    }));
  }

  protected dayCellTitle(iso: string): string {
    const data = this.ringDataFor(iso);
    if (!data.hasData) return `${iso} — aucune donnée`;
    return `${iso} — ${this.round(data.totals.kcal)} kcal`;
  }

  protected prevMonth(): void {
    const { y, m } = this.cursor();
    this.cursor.set(m === 0 ? { y: y - 1, m: 11 } : { y, m: m - 1 });
  }

  protected nextMonth(): void {
    const { y, m } = this.cursor();
    this.cursor.set(m === 11 ? { y: y + 1, m: 0 } : { y, m: m + 1 });
  }

  protected dayNumOf(iso: string): number {
    return Number(iso.slice(8, 10));
  }

  private monthOf(iso: string): { y: number; m: number } {
    const [y, m] = iso.split('-').map(Number);
    return { y, m: m - 1 };
  }

  private pad(n: number): string {
    return n.toString().padStart(2, '0');
  }

  // -------------------- Journal du jour --------------------

  private readonly dayMeals = computed(() =>
    this.mealRepo
      .meals()
      .filter((m) => m.date === this.day())
      .sort((a, b) => a.orderIndex - b.orderIndex),
  );

  protected readonly sections = computed(() =>
    buildSections(this.mealRepo.presets(), this.dayMeals(), this.mealRepo.entries()),
  );

  protected readonly dayTotals = computed<MacroTotals>(() => {
    const mealUuids = new Set(this.dayMeals().map((m) => m.uuid));
    return sumTotals(this.mealRepo.entries().filter((e) => mealUuids.has(e.mealUUID)));
  });

  protected readonly activeGoal = computed(() =>
    this.goalRepo.activeGoalFor(this.goalRepo.goals(), this.day()),
  );

  // -------------------- Hydratation (card au-dessus des repas) --------------------

  /** uuids des aliments marqués eau (isWater), pour l'auto-comptage. */
  private readonly waterFoodUuids = computed(
    () => new Set(this.foodRepo.foods().filter((f) => f.isWater).map((f) => f.uuid)),
  );

  /** État d'hydratation du jour : total consommé (prises + boissons eau) + objectif WATER_ML. */
  protected readonly hydration = computed(() => {
    const day = this.day();
    const dayMealUuids = new Set(this.dayMeals().map((m) => m.uuid));
    return {
      consumedMl: dayHydrationMl(
        day,
        this.hydrationRepo.intakes(),
        dayMealUuids,
        this.mealRepo.entries(),
        this.waterFoodUuids(),
      ),
      goalMl: activeWaterGoalMl(this.hydrationRepo.healthGoals(), day),
    };
  });

  /** Y a-t-il une prise manuelle annulable ce jour ? (pilote le bouton undo) */
  protected readonly canUndoWater = computed(() =>
    this.hydrationRepo.intakes().some((w) => w.date === this.day()),
  );

  /** Dialog de saisie perso (ml). */
  protected readonly showWaterDialog = signal(false);
  protected readonly waterInput = signal('');
  protected readonly waterValid = computed(() => {
    const n = Number(this.waterInput());
    return Number.isFinite(n) && n > 0;
  });

  protected addWater(ml: number): void {
    void this.hydrationRepo.addWater(this.day(), ml);
  }
  protected undoWater(): void {
    void this.hydrationRepo.undoLastWater(this.day());
  }
  protected openWaterCustom(): void {
    this.waterInput.set('');
    this.showWaterDialog.set(true);
  }
  protected onWaterInput(v: string): void {
    this.waterInput.set(v.replace(/[^0-9]/g, ''));
  }
  protected submitWaterCustom(): void {
    const n = Number(this.waterInput());
    if (Number.isFinite(n) && n > 0) {
      this.addWater(Math.round(n));
      this.showWaterDialog.set(false);
    }
  }

  /** Dialog de réglage de l'objectif d'hydratation (ml/jour), ouvert depuis la card. */
  protected readonly showWaterGoal = signal(false);
  protected readonly waterGoalInput = signal('');
  protected readonly waterGoalValid = computed(() => {
    const n = Number(this.waterGoalInput());
    return Number.isFinite(n) && n > 0;
  });
  protected readonly currentWaterGoal = computed(() => this.hydration().goalMl);
  protected openWaterGoal(): void {
    const g = this.currentWaterGoal();
    this.waterGoalInput.set(g !== null ? String(g) : '');
    this.showWaterGoal.set(true);
  }
  protected onWaterGoalInput(v: string): void {
    this.waterGoalInput.set(v.replace(/[^0-9]/g, ''));
  }
  protected submitWaterGoal(): void {
    const n = Number(this.waterGoalInput());
    if (Number.isFinite(n) && n > 0) {
      void this.hydrationRepo.setWaterGoal(Math.round(n));
      this.showWaterGoal.set(false);
    }
  }

  /** Total sucres du jour (g) depuis les snapshots per-100g — recalcul réactif, comme dayTotals. */
  private readonly daySugarG = computed(() => {
    const mealUuids = new Set(this.dayMeals().map((m) => m.uuid));
    return sumSugarG(this.mealRepo.entries().filter((e) => mealUuids.has(e.mealUUID)));
  });

  /**
   * Lignes du bandeau : cumul / cible + barre. Chaque macro porte sa couleur dédiée (convention
   * code couleur P/G/L/kcal partagée), pas une couleur fonction de l'avancement. Dernière ligne =
   * Sucres, un PLAFOND (5 % de la cible kcal en g, max 100 g — cf. sugarLimitsG, fallback 2000 →
   * 100 g), pas une cible à remplir : affiché « ≤ limite », repère « idéal » à la moitié sur la
   * barre, couleur d'alerte si dépassé (pattern plafond Sodium des micros).
   */
  protected readonly bannerRows = computed(() => {
    const t = this.dayTotals();
    const g = this.activeGoal();
    const fiberTarget = fiberTargetG(g?.kcal);
    const rows = [
      { label: 'Calories', value: t.kcal, target: g?.kcal ?? null, unit: 'kcal', color: MACRO_COLOR.kcal },
      { label: 'Glucides', value: t.carbs, target: g?.carbsG ?? null, unit: 'g', color: MACRO_COLOR.carbs },
      { label: 'Lipides', value: t.fat, target: g?.fatG ?? null, unit: 'g', color: MACRO_COLOR.fat },
      { label: 'Protéines', value: t.protein, target: g?.proteinG ?? null, unit: 'g', color: MACRO_COLOR.protein },
      { label: 'Fibres', value: t.fiber, target: fiberTarget, unit: 'g', color: MACRO_COLOR.fiber },
    ];
    const mapped = rows.map((r) => {
      const ratio = r.target ? r.value / r.target : 0;
      return {
        label: r.label,
        progress: Math.min(1, ratio),
        color: r.color,
        // valueText = le consommé (coloré, écho de la barre) ; targetText = la référence (gris).
        valueText: `${Math.round(r.value)}`,
        targetText: r.target ? `/ ${Math.round(r.target)} ${r.unit}` : `${r.unit}`,
        centerValue: `${Math.round(r.value)}`,
        centerSub: r.target ? `/ ${Math.round(r.target)}` : r.unit,
        markerAt: null as number | null,
      };
    });
    const sugar = this.daySugarG();
    const { limitG, idealG } = sugarLimitsG(g?.kcal);
    const exceeded = sugar > limitG;
    mapped.push({
      label: 'Sucres',
      progress: Math.min(1, sugar / limitG),
      // Alerte orange en dépassement (comme le plafond Sodium des micros), framboise sinon.
      color: exceeded ? 'var(--app-snackbar-warning)' : SUGAR_COLOR,
      valueText: `${Math.round(sugar)}`,
      targetText: `≤ ${Math.round(limitG)} g`,
      centerValue: `${Math.round(sugar)}`,
      centerSub: `≤ ${Math.round(limitG)}`,
      markerAt: idealG / limitG,
    });
    return mapped;
  });

  /**
   * Données radar des macros (G/L/P/F), même rendu que la page Catalogue : avec une cible active →
   * 2 tracés (Consommé en % de la cible + repère Cible à 100) ; sinon profil brut en grammes.
   */
  protected readonly macroRadar = computed(() => {
    const t = this.dayTotals();
    const g = this.activeGoal();
    const macros: MacroAmounts = { protein: t.protein, carbs: t.carbs, fat: t.fat, fiber: t.fiber };
    const targets: MacroTargets | null = g
      ? { kcal: g.kcal, protein: g.proteinG, carbs: g.carbsG, fat: g.fatG, fiber: fiberTargetG(g.kcal) }
      : null;
    return macroRadarData(macros, targets, { value: 'Consommé', target: 'Cible' });
  });

  // -------------------- Bandeau micros (secondaire, repliable + bascule) --------------------

  protected readonly viewSegments: SegmentItem[] = [
    { value: 'RINGS', icon: 'donut_large', description: 'Anneaux' },
    { value: 'BARS', icon: 'bar_chart', description: 'Barres' },
    { value: 'RADAR', icon: 'radar', description: 'Radar' },
  ];

  protected readonly showMicros = signal(false);
  // Mode d'affichage PARTAGÉ macros ↔ micros : une seule bascule pilote les deux sections.
  protected readonly summaryView = signal<SectionView>(readSectionView(SUMMARY_VIEW_KEY));

  /** Sens de la dernière bascule de mode (null au chargement → pas d'animation initiale). */
  protected readonly slideDir = signal<'left' | 'right' | null>(null);

  protected setSummaryView(view: string): void {
    // Direction = ordre visuel du sélecteur (viewSegments) : suivant → arrive de droite.
    const order = this.viewSegments.map((s) => s.value);
    this.slideDir.set(order.indexOf(view) > order.indexOf(this.summaryView()) ? 'right' : 'left');
    this.summaryView.set(view as SectionView);
    localStorage.setItem(SUMMARY_VIEW_KEY, view);
  }

  /** Cumul jour des 10 micros (partagé entre les barres/anneaux et le radar des micros). */
  private readonly dayMicroTotals = computed(() => {
    const mealUuids = new Set(this.dayMeals().map((m) => m.uuid));
    return sumMicroTotals(this.mealRepo.entries().filter((e) => mealUuids.has(e.mealUUID)));
  });

  /** Données radar des micros (couverture VNR %), même rendu que la page Catalogue. */
  protected readonly microRadar = computed(() => microRadarData(this.dayMicroTotals()));

  /**
   * Lignes micros du jour : cumul vs VNR UE (objectif) ou plafond Sodium. Teinte par famille
   * (minéraux / vitamines, pas de couleur cible imposée) ; alerte rouge si le plafond Sodium est
   * dépassé. Sodium affiché « ≤ cible » (plafond), les autres « / cible » (objectif).
   */
  protected readonly microRows = computed(() => {
    return buildMicroRows(this.dayMicroTotals()).map((r) => ({
      label: r.label,
      progress: r.progress,
      // Teinte par famille (MICRO_COLOR → tokens --micro-* : minéraux rouge / vitamines doré),
      // source unique avec rows/picker/détail/résumé ; orange d'alerte si le plafond Sodium est
      // dépassé (cohérent avec nutrition-summary-panel, le rouge minéral rendrait l'alerte invisible).
      color: r.exceeded ? 'var(--app-snackbar-warning)' : MICRO_COLOR[r.key],
      valueText: `${this.round1(r.value)}`,
      targetText: `${r.isLimit ? '≤' : '/'} ${r.target} ${r.unit}`,
      centerValue: `${this.round1(r.value)}`,
      centerSub: `${r.isLimit ? '≤' : '/'} ${r.target}`,
    }));
  });

  protected goToday(): void {
    const t = todayIso();
    this.day.set(t);
    this.cursor.set(this.monthOf(t));
  }

  protected readonly showPresets = signal(false);


  // -------------------- Ajout d'aliment (picker + quantité) --------------------

  protected readonly pickerSection = signal<JournalSection | null>(null);
  protected readonly qtyTarget = signal<QtyTarget | null>(null);
  protected readonly qtyValue = signal('100');
  protected readonly qtyPortionLabel = signal<string | null>(null);

  protected openPicker(section: JournalSection): void {
    this.pickerSection.set(section);
  }

  protected onFoodPicked(food: LocalFood): void {
    const section = this.pickerSection();
    this.pickerSection.set(null);
    if (!section) return;
    this.qtyValue.set('100');
    this.qtyPortionLabel.set(null);
    this.qtyTarget.set({ kind: 'add', section, food });
  }

  protected readonly qtyTitle = computed(() => {
    const t = this.qtyTarget();
    if (!t) return '';
    return t.kind === 'add' ? t.food.name : `Modifier — ${t.entry.displayName}`;
  });

  /** Portions nommées de l'aliment ciblé — proposées à l'ajout (food) comme à l'édition (entry.foodUUID). */
  protected readonly qtyPortions = computed(() => {
    const t = this.qtyTarget();
    if (!t) return [];
    const foodUuid = t.kind === 'add' ? t.food.uuid : t.entry.foodUUID;
    if (!foodUuid) return [];
    return this.foodRepo.portions().filter((p) => p.foodUUID === foodUuid);
  });

  /** L'aliment ciblé par le dialog de quantité est-il de l'eau (isWater) ? */
  private readonly qtyFoodIsWater = computed(() => {
    const t = this.qtyTarget();
    if (!t) return false;
    if (t.kind === 'add') return t.food.isWater;
    return this.foodRepo.foods().find((f) => f.uuid === t.entry.foodUUID)?.isWater ?? false;
  });

  /**
   * Chips de quantité. Aliment eau : toujours les volumes standards (250 mL · 500 mL · 1 L)
   * en libellés purs, PLUS ses portions propres non couvertes (dédup par grammage) — pas de
   * FoodPortions persistées. Autres aliments : les portions nommées avec « (X g) ».
   */
  protected readonly qtyChips = computed<{ key: string; label: string; grams: number; snapshot: string }[]>(() => {
    const portions = this.qtyPortions();
    if (!this.qtyFoodIsWater()) {
      return portions.map((p) => ({
        key: p.uuid,
        label: `${p.label} (${this.round(p.grams)} g)`,
        grams: p.grams,
        snapshot: p.label,
      }));
    }
    const standard = [250, 500, 1000];
    const chips = standard.map((ml) => ({
      key: `std-${ml}`,
      label: this.volumeLabel(ml),
      grams: ml,
      snapshot: this.volumeLabel(ml),
    }));
    for (const p of portions) {
      if (!standard.includes(Math.round(p.grams))) {
        chips.push({ key: p.uuid, label: p.label, grams: p.grams, snapshot: p.label });
      }
    }
    return chips;
  });

  /** Grammes → volume pur (1 g = 1 ml) : « 1 L » si litres entiers, sinon « N mL ». */
  private volumeLabel(grams: number): string {
    const ml = Math.round(grams);
    return ml % 1000 === 0 ? `${ml / 1000} L` : `${ml} mL`;
  }

  protected readonly qtyValid = computed(() => {
    const v = parseMacro(this.qtyValue());
    return v !== null && v > 0;
  });

  protected usePortion(label: string, grams: number): void {
    this.qtyValue.set(String(grams));
    this.qtyPortionLabel.set(label);
  }

  protected onQtyInput(value: string): void {
    this.qtyValue.set(value);
    this.qtyPortionLabel.set(null);
  }

  protected confirmQty(): void {
    const target = this.qtyTarget();
    const qty = parseMacro(this.qtyValue());
    if (!target || qty === null || qty <= 0) return;
    this.qtyTarget.set(null);
    if (target.kind === 'edit') {
      void this.mealRepo.updateEntry(target.entry.uuid, {
        quantityG: qty,
        portionLabel: this.qtyPortionLabel(),
      });
      return;
    }
    void this.ensureMeal(target.section).then((mealUuid) =>
      this.mealRepo.addEntryFromFood(mealUuid, target.food, qty, this.qtyPortionLabel()),
    );
  }

  /** Meal row de la section, créé à la première entry seulement (§3.4 — pas de rows fantômes). */
  private async ensureMeal(section: JournalSection): Promise<string> {
    if (section.meal) return section.meal.uuid;
    return this.mealRepo.createMeal({
      date: this.day(),
      name: section.name,
      orderIndex: section.orderIndex,
      presetUuid: section.presetUuid,
    });
  }

  // -------------------- Options entry (modifier / supprimer) --------------------

  protected readonly entryForOptions = signal<LocalMealEntry | null>(null);
  /** Actions de la row aliment : « voir les détails » (si issu d'un aliment du catalogue), modifier, supprimer. */
  protected readonly entryActions = computed<SheetAction[]>(() => {
    const actions: SheetAction[] = [];
    if (this.entryForOptions()?.foodUUID) {
      actions.push({ label: "Voir les détails de l'aliment", icon: 'info', color: 'var(--app-primary-action)' });
    }
    actions.push({ label: 'Modifier la quantité', icon: 'edit', color: 'var(--c-first-blue)' });
    actions.push({ label: 'Supprimer', icon: 'delete', color: 'var(--c-red-medium)' });
    return actions;
  });

  protected onEntryOption(label: string): void {
    const entry = this.entryForOptions();
    this.entryForOptions.set(null);
    if (!entry) return;
    if (label === "Voir les détails de l'aliment") {
      if (entry.foodUUID) {
        void this.router.navigate(['/nutrition/foods'], { queryParams: { food: entry.foodUUID } });
      }
    } else if (label === 'Modifier la quantité') {
      this.qtyValue.set(String(entry.quantityG));
      this.qtyPortionLabel.set(null);
      this.qtyTarget.set({ kind: 'edit', entry });
    } else if (label === 'Supprimer') {
      void this.mealRepo.removeEntry(entry.uuid);
    }
  }

  // -------------------- Options repas (supprimer) --------------------

  protected readonly mealForOptions = signal<JournalSection | null>(null);
  protected readonly mealToDelete = signal<LocalMeal | null>(null);
  /** « Supprimer le repas » seulement si un meal existe (une section preset vide n'a pas de meal à supprimer). */
  protected readonly mealActions = computed<SheetAction[]>(() => {
    const actions: SheetAction[] = [
      { label: 'Ajouter un aliment', icon: 'add', color: 'var(--app-primary-action)' },
    ];
    if (this.mealForOptions()?.meal) {
      actions.push({ label: 'Supprimer le repas', icon: 'delete', color: 'var(--c-red-medium)' });
    }
    return actions;
  });

  protected onMealOption(label: string): void {
    const section = this.mealForOptions();
    this.mealForOptions.set(null);
    if (!section) return;
    if (label === 'Ajouter un aliment') this.openPicker(section);
    else if (label === 'Supprimer le repas' && section.meal) this.mealToDelete.set(section.meal);
  }

  protected readonly deleteMealMsg = computed(() => {
    const m = this.mealToDelete();
    return m ? `Supprimer « ${m.name} » et tous ses aliments ?` : '';
  });

  protected confirmDeleteMeal(): void {
    const m = this.mealToDelete();
    this.mealToDelete.set(null);
    if (m) void this.mealRepo.removeMeal(m.uuid);
  }

  // -------------------- Ajouter un repas ad hoc --------------------

  protected readonly showAddMeal = signal(false);
  protected readonly mealName = signal('');
  /** Heure du repas saisie dans le dialog (« HH:MM », vide = aucune). */
  protected readonly mealTime = signal('');

  protected openAddMeal(): void {
    this.mealName.set('');
    this.mealTime.set('');
    this.showAddMeal.set(true);
  }

  protected submitAddMeal(): void {
    const name = this.mealName().trim();
    if (!name) return;
    this.showAddMeal.set(false);
    const time = this.mealTime().trim() || null;
    const maxOrder = Math.max(-1, ...this.sections().map((s) => s.orderIndex));
    void this.mealRepo.createMeal({ date: this.day(), name, orderIndex: maxOrder + 1, time });
  }

  // -------------------- Dupliquer un repas passé --------------------

  protected readonly showDuplicate = signal(false);

  /** Repas non vides des jours antérieurs au jour affiché (plus récents d'abord, max 30). */
  protected readonly pastMeals = computed(() => {
    const entries = this.mealRepo.entries();
    const byMeal = new Map<string, LocalMealEntry[]>();
    for (const e of entries) {
      const list = byMeal.get(e.mealUUID) ?? [];
      list.push(e);
      byMeal.set(e.mealUUID, list);
    }
    return this.mealRepo
      .meals()
      .filter((m) => m.date < this.day() && (byMeal.get(m.uuid)?.length ?? 0) > 0)
      .sort((a, b) =>
        a.date === b.date ? a.orderIndex - b.orderIndex : b.date.localeCompare(a.date),
      )
      .slice(0, 30)
      .map((meal) => {
        const mealEntries = byMeal.get(meal.uuid) ?? [];
        return { meal, count: mealEntries.length, totals: sumTotals(mealEntries) };
      });
  });

  /** Copie le repas passé sur le jour affiché : nouveau Meal + entries re-snapshotées telles quelles. */
  protected duplicateMeal(source: LocalMeal): void {
    this.showDuplicate.set(false);
    const sourceEntries = this.mealRepo.entries().filter((e) => e.mealUUID === source.uuid);
    const maxOrder = Math.max(-1, ...this.sections().map((s) => s.orderIndex));
    void this.mealRepo
      .createMeal({ date: this.day(), name: source.name, orderIndex: maxOrder + 1 })
      .then(async (mealUuid) => {
        for (const e of sourceEntries) {
          await this.mealRepo.addEntry({
            mealUUID: mealUuid,
            foodUUID: e.foodUUID,
            recipeUUID: e.recipeUUID,
            displayName: e.displayName,
            quantityG: e.quantityG,
            portionLabel: e.portionLabel,
            kcalPer100g: e.kcalPer100g,
            proteinPer100g: e.proteinPer100g,
            carbsPer100g: e.carbsPer100g,
            fatPer100g: e.fatPer100g,
            fiberPer100g: e.fiberPer100g,
            sugarPer100g: e.sugarPer100g,
            satFatPer100g: e.satFatPer100g,
            saltPer100g: e.saltPer100g,
            ironPer100g: e.ironPer100g,
            calciumPer100g: e.calciumPer100g,
            magnesiumPer100g: e.magnesiumPer100g,
            zincPer100g: e.zincPer100g,
            potassiumPer100g: e.potassiumPer100g,
            sodiumPer100g: e.sodiumPer100g,
            vitaminCPer100g: e.vitaminCPer100g,
            vitaminDPer100g: e.vitaminDPer100g,
            vitaminB12Per100g: e.vitaminB12Per100g,
            vitaminAPer100g: e.vitaminAPer100g,
          });
        }
      });
  }

  // -------------------- Helpers d'affichage --------------------

  protected formatShortDate(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }
  /**
   * Vue d'une ligne aliment du repas (nom + macros dérivées du snapshot + micros présents colorés)
   * pour la ligne partagée MacroEntryRow. Les fibres ne sont affichées que si l'entry les connaît.
   */
  protected entryRow(e: LocalMealEntry): MacroEntryRowData {
    const t = entryTotals(e);
    return {
      name: e.displayName,
      kcal: t.kcal,
      carbs: t.carbs,
      fat: t.fat,
      protein: t.protein,
      fiber: e.fiberPer100g != null ? t.fiber : null,
      micros: this.entryMicros(e),
      // Sucres consommés (à l'échelle de la quantité) en tête du dépli micros de la ligne.
      sugarG: entrySugarG(e),
    };
  }

  /** Micros consommés de l'entry (mis à l'échelle de la quantité), colorés par famille (micro-colors). */
  protected entryMicros(e: LocalMealEntry) {
    return microLineItems(e, e.quantityG / 100);
  }

  protected round(v: number): number {
    return Math.round(v);
  }
  protected round1(v: number): number {
    return Math.round(v * 10) / 10;
  }

  /** Garde-fou : ne relancer le heal qu'une fois par batch détecté (l'effect re-tourne à chaque liveQuery). */
  private healing = false;

  constructor() {
    void this.sync.syncAll().catch(() => undefined);

    // Auto-réparage des repas legacy (presetUuid null) une fois presets + meals chargés.
    effect(() => {
      const presets = this.mealRepo.presets();
      const meals = this.mealRepo.meals();
      if (this.healing || presets.length === 0) return;
      const toHeal = legacyMealsToHeal(presets, meals);
      if (toHeal.length === 0) return;
      this.healing = true;
      void this.mealRepo.healPresetLinks(toHeal).finally(() => (this.healing = false));
    });
  }
}
