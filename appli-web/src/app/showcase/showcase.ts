import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, signal, viewChild } from '@angular/core';
import { ThemeService } from '@designsystem/theme/theme.service';
import { AppIcon } from '@designsystem/icons/app-icon';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { ActionTextButton } from '@designsystem/common_components/action-text-button';
import { CustomCheckbox } from '@designsystem/common_components/custom-checkbox';
import { CustomRadioButton } from '@designsystem/common_components/custom-radio-button';
import { CustomSpacer } from '@designsystem/common_components/custom-spacer';
import { CustomSwitch } from '@designsystem/common_components/custom-switch';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { DialogPrimaryButton, DialogSecondaryButton } from '@designsystem/common_components/dialog-buttons';
import { LabeledProgressBar } from '@designsystem/common_components/labeled-progress-bar';
import { ProgressBarPrimitive } from '@designsystem/common_components/progress-bar-primitive';
import { StatusIcon } from '@designsystem/common_components/status-icon';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { DetailRow } from '@designsystem/common_components/detail-row';
import { DetailRowWithIndentation } from '@designsystem/common_components/detail-row-with-indentation';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ListFrame } from '@designsystem/common_components/list-frame';
import { ListRow } from '@designsystem/common_components/list-row';
import { EntityListRow } from '@designsystem/common_components/entity-list-row';
import { EntityRowTrailing } from '@designsystem/common_components/entity-row-trailing';
import { GenericEntityCard } from '@designsystem/common_components/generic-entity-card';
import { ExpandableCard } from '@designsystem/common_components/expandable-card';
import { OptionRow } from '@designsystem/common_components/option-row';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { SummaryItem } from '@designsystem/common_components/summary-item';
import { SummaryRow, type SummaryItemData } from '@designsystem/common_components/summary-row';
import { CustomSelect } from '@designsystem/common_components/custom-select';
import { SingleSelectDropdown } from '@designsystem/common_components/single-select-dropdown';
import { MultiSelectDropdown } from '@designsystem/common_components/multi-select-dropdown';
import { FilterDropdown } from '@designsystem/common_components/filter-dropdown';
import { FilterPanel } from '@designsystem/common_components/filter-panel';
import { FilterThresholdRow, type FilterThresholdOp } from '@designsystem/common_components/filter-threshold-row';
import { CustomHourPicker } from '@designsystem/common_components/custom-hour-picker';
import { DateRangePickerDialog } from '@designsystem/common_components/date-range-picker-dialog';
import { CollapsibleSection } from '@designsystem/common_components/collapsible-section';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { ListSearchHeader } from '@designsystem/common_components/list-search-header';
import { ColumnHeaderActionsCard, type SortDir } from '@designsystem/common_components/column-header-actions-card';
import { TabRowCustom } from '@designsystem/common_components/tab-row-custom';
import { DualTabMenu } from '@designsystem/common_components/dual-tab-menu';
import { SegmentedIconButton } from '@designsystem/common_components/segmented-icon-button';
import { SegmentedIconToggle, type SegmentItem } from '@designsystem/common_components/segmented-icon-toggle';
import { DrawerIconCountIndicator } from '@designsystem/common_components/drawer-icon-count-indicator';
import { DrawerMiniProgress } from '@designsystem/common_components/drawer-mini-progress';
import { DrawerItem } from '@designsystem/common_components/drawer-item';
import { DrawerSection } from '@designsystem/common_components/drawer-section';
import { DrawerFooter } from '@designsystem/common_components/drawer-footer';
import { BottomNavBar, type BottomNavItemData } from '@designsystem/common_components/bottom-nav-bar';
import { WheelPicker } from '@designsystem/common_components/wheel-picker';
import { HorizontalNumberPicker } from '@designsystem/common_components/horizontal-number-picker';
import { HmsWheelPicker } from '@designsystem/common_components/hms-wheel-picker';
import { TimeRangePickerBar } from '@designsystem/common_components/time-range-picker-bar';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomDatePickerDialog } from '@designsystem/common_components/custom-date-picker-dialog';
import { DateField } from '@designsystem/common_components/date-field';
import { DataTable } from '@designsystem/common_components/data-table';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { MultiLineChart, type LineSeries } from '@designsystem/common_components/multi-line-chart';
import { RadarChartComponent, type RadarAxis, type RadarSeries } from '@designsystem/common_components/radar-chart';
import { ConcentricRingsChart } from '@designsystem/common_components/concentric-rings-chart';
import { DonutChartComponent, type DonutSlice } from '@designsystem/common_components/donut-chart';
import { StatsChartCard } from '@designsystem/common_components/stats-chart-card';
import { DataGridPaginationBar } from '@designsystem/common_components/data-grid-pagination-bar';
import { CalendarMonthGrid } from '@designsystem/common_components/calendar-month-grid';
import { AppSnackbarHost, type SnackbarEvent } from '@designsystem/common_components/app-snackbar-host';
import { SetRow, type SetRowData } from '@designsystem/common_components/set-row';
import { PhasePickerDialog } from '@designsystem/common_components/phase-picker-dialog';
import { StatusPickerDialog, type StatusOption } from '@designsystem/common_components/status-picker-dialog';
import { ExercisePickerBottomSheet, type ExercisePickerItem } from '@designsystem/common_components/exercise-picker-bottom-sheet';
import {
  NutritionSummaryPanel,
  type MacroAmounts,
  type MacroTargets,
  type SummaryDisplay,
} from '@features/nutrition/nutrition-summary-panel';
import { macroRingViews } from '@features/nutrition/macro-rings-chart';
import { MACRO_COLOR } from '@features/nutrition/macro-colors';
import { type MicroNutrients } from '@features/nutrition/micros';
import { microLineItems } from '@features/nutrition/micro-colors';
import { MacroEntryRow, type MacroEntryRowData } from '@features/nutrition/macro-entry-row';

interface Swatch {
  name: string;
  varName: string;
  /** Libellé humain optionnel (ex. groupes d'aliments) ; affiché au-dessus du nom de token. */
  label?: string;
}

/** Une famille de tokens couleur de _colors.scss, rendue en bloc de swatches. */
interface SwatchGroup {
  title: string;
  swatches: Swatch[];
}

/**
 * Page vitrine du design system — équivalent web de UiShowcaseScreen (Android).
 * Une section par composant de designsystem/common_components (live + états).
 */
@Component({
  selector: 'app-showcase',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppIcon,
    ActionIconButton,
    ActionIconWithTextButton,
    ActionTextButton,
    CustomCheckbox,
    CustomRadioButton,
    CustomSpacer,
    CustomSwitch,
    CustomTextField,
    DialogPrimaryButton,
    DialogSecondaryButton,
    LabeledProgressBar,
    ProgressBarPrimitive,
    StatusIcon,
    TitledDivider,
    DetailRow,
    DetailRowWithIndentation,
    EmptyListRow,
    ListFrame,
    ListRow,
    EntityListRow,
    EntityRowTrailing,
    GenericEntityCard,
    ExpandableCard,
    OptionRow,
    ScreenTitleBar,
    SummaryItem,
    SummaryRow,
    CustomSelect,
    SingleSelectDropdown,
    MultiSelectDropdown,
    FilterDropdown,
    FilterPanel,
    FilterThresholdRow,
    CustomHourPicker,
    DateRangePickerDialog,
    CollapsibleSection,
    StyledSearchField,
    ListSearchHeader,
    ColumnHeaderActionsCard,
    TabRowCustom,
    DualTabMenu,
    SegmentedIconButton,
    SegmentedIconToggle,
    DrawerIconCountIndicator,
    DrawerMiniProgress,
    DrawerItem,
    DrawerSection,
    DrawerFooter,
    BottomNavBar,
    WheelPicker,
    HorizontalNumberPicker,
    HmsWheelPicker,
    TimeRangePickerBar,
    AppBottomSheet,
    ConfirmationDialog,
    FormDialog,
    CustomDatePickerDialog,
    DateField,
    DataTable,
    OptionsBottomSheet,
    MultiLineChart,
    RadarChartComponent,
    ConcentricRingsChart,
    DonutChartComponent,
    StatsChartCard,
    DataGridPaginationBar,
    CalendarMonthGrid,
    AppSnackbarHost,
    SetRow,
    PhasePickerDialog,
    StatusPickerDialog,
    ExercisePickerBottomSheet,
    NutritionSummaryPanel,
    MacroEntryRow,
  ],
  template: `
    <main class="showcase">
      <app-screen-title-bar title="Design System - Showcase" />

      <div class="showcase__body">
      <nav class="tabs">
        @for (t of categories; track t.id) {
          <button class="tab" [class.tab--active]="category() === t.id" (click)="category.set(t.id)">{{ t.label }}</button>
        }
        <!-- Bascule de thème : style ActionIconButton (fond --app-bg-button par défaut), icône lune/soleil
             selon le thème courant (soleil en dark = passer en clair ; lune en clair = passer en dark). -->
        <app-action-icon-button
          class="tabs__theme"
          [icon]="theme.resolved() === 'dark' ? 'light_mode' : 'dark_mode'"
          (clicked)="theme.toggle()"
        />
      </nav>

      <div class="explorer">
          <aside class="explorer__nav" #navList>
            <app-styled-search-field
              [value]="navFilter()"
              (valueChange)="navFilter.set($event)"
              placeholderText="Filtrer…"
            />
            <div class="explorer__nav-divider"></div>
            @for (item of filteredNav(); track item.id) {
              <button
                type="button"
                class="explorer__item"
                [attr.data-navid]="item.id"
                [class.explorer__item--active]="sel() === item.id"
                (click)="selectComp(item.id)"
              >{{ item.label }}</button>
            }
            @if (filteredNav().length === 0) {
              <p class="muted">Aucun composant.</p>
            }
          </aside>
        <div class="explorer__detail" #detail (click)="onDetailClick($event)">

      @if (category() === 'foundations') {
      <p class="muted foundations-intro">
        Toute la palette du thème courant ({{ theme.mode() }}), groupée par famille — primitives
        --c-*, sémantiques --app-*, macros, micros, origines, groupes d'aliments et boutons. Les
        pastilles résolvent var(--token) selon le thème actif ; le nom affiché est le token.
      </p>
      @for (g of swatchGroups; track g.title) {
      <section class="card">
        <h2>{{ g.title }}</h2>
        <div class="swatches">
          @for (s of g.swatches; track s.varName) {
            <div class="swatch">
              <div class="swatch__chip" [style.background]="'var(' + s.varName + ')'"></div>
              <div class="swatch__text">
                <span class="swatch__name">{{ s.label ?? s.name }}</span>
                @if (s.label) {
                  <span class="swatch__token">{{ s.name }}</span>
                }
              </div>
            </div>
          }
        </div>
      </section>
      }
      }

      @if (category() === 'components') {
      <section class="card">
        <h2>DialogPrimaryButton / DialogSecondaryButton</h2>
        <div class="row">
          <app-dialog-primary-button>Primaire</app-dialog-primary-button>
          <app-dialog-secondary-button>Secondaire</app-dialog-secondary-button>
          <app-dialog-primary-button [disabled]="true">Désactivé</app-dialog-primary-button>
        </div>
      </section>

      <section class="card">
        <h2>CustomTextField</h2>
        <app-custom-text-field
          label="Nom de l'exercice"
          placeholder="Ex. Développé couché"
          [value]="name()"
          (valueChange)="name.set($event)"
        />
        <p class="muted">Saisi : {{ name() || '—' }}</p>
      </section>

      <section class="card">
        <h2>DateField</h2>
        <p class="muted">
          Déclencheur de date : conteneur recessed + bouton icône calendrier (fond first-blue), seul
          cliquable → ouvre un CustomDatePickerDialog câblé par l'appelant.
        </p>
        <app-date-field [value]="dfDate()" (clicked)="dfPickerOpen.set(true)" />
        <app-custom-date-picker-dialog
          [open]="dfPickerOpen()"
          title="Choisir une date"
          [initialIso]="dfDate() || null"
          (confirm)="dfDate.set($event); dfPickerOpen.set(false)"
          (dismiss)="dfPickerOpen.set(false)"
        />
        <h3 class="sub">États</h3>
        <app-date-field value="24/06/2026" />
        <app-date-field value="" placeholder="Sélectionner une date" />
      </section>

      <section class="card">
        <h2>CustomSwitch</h2>
        <div class="row">
          <app-custom-switch [checked]="true" />
          <app-custom-switch [checked]="false" />
        </div>
      </section>

      <section class="card">
        <h2>CustomCheckbox</h2>
        <div class="row">
          <app-custom-checkbox [checked]="true" />
          <app-custom-checkbox [checked]="false" />
        </div>
      </section>

      <section class="card">
        <h2>CustomRadioButton</h2>
        <div class="row">
          <app-custom-radio-button [selected]="radio() === 'a'" (clicked)="radio.set('a')" />
          <app-custom-radio-button [selected]="radio() === 'b'" (clicked)="radio.set('b')" />
          <app-custom-radio-button [selected]="radio() === 'c'" (clicked)="radio.set('c')" />
          <span class="muted">sélection : {{ radio() }}</span>
        </div>
      </section>

      <section class="card">
        <h2>TitledDivider</h2>
        <app-titled-divider title="Section" />
      </section>

      <section class="card">
        <h2>LabeledProgressBar (seuils de couleur)</h2>
        <app-labeled-progress-bar [progress]="0.15" />
        <app-labeled-progress-bar [progress]="0.45" />
        <app-labeled-progress-bar [progress]="0.8" />
        <app-labeled-progress-bar [progress]="1" />
        <h3 class="sub">ProgressBarPrimitive (brut)</h3>
        <app-progress-bar-primitive [progress]="0.6" color="var(--app-accent-text)" />
      </section>

      <section class="card">
        <h2>CustomSpacer</h2>
        <div class="row spacer-demo">
          <span class="box"></span>
          <app-custom-spacer [width]="24" />
          <span class="box"></span>
          <span class="muted">(gap horizontal de 24px entre les 2 carrés)</span>
        </div>
      </section>

      <section class="card">
        <h2>ActionIconButton</h2>
        <div class="row" style="background: var(--app-bg-screen); padding: var(--space-4); border-radius: var(--radius-md);">
          <app-action-icon-button icon="add" (clicked)="bump('add')" />
          <app-action-icon-button icon="edit" backgroundColor="var(--c-medium-green)" (clicked)="bump('edit')" />
          <app-action-icon-button icon="delete" backgroundColor="var(--c-red-medium)" (clicked)="bump('delete')" />
          <app-action-icon-button icon="search" (clicked)="bump('search')" />
        </div>
        <h3 class="sub">Sans fond (hasBackground = false)</h3>
        <div class="row">
          <app-action-icon-button icon="arrow_back" [hasBackground]="false" (clicked)="bump('back')" />
          <app-action-icon-button icon="chevron_right" [hasBackground]="false" (clicked)="bump('next')" />
          <app-action-icon-button icon="more_vert" [hasBackground]="false" (clicked)="bump('menu')" />
          <app-action-icon-button icon="refresh" [hasBackground]="false" tint="var(--app-accent-text)" (clicked)="bump('refresh')" />
          <app-action-icon-button icon="close" [hasBackground]="false" [disabled]="true" />
        </div>
        <p class="muted">Dernier clic : {{ lastAction() || '—' }}</p>
      </section>

      <section class="card">
        <h2>ActionIconWithTextButton</h2>
        <div class="row">
          <app-action-icon-with-text-button icon="add" text="Ajouter" (clicked)="bump('Ajouter')" />
          <app-action-icon-with-text-button icon="refresh" text="Synchroniser" (clicked)="bump('Synchroniser')" />
          <app-action-icon-with-text-button
            icon="delete"
            text="Supprimer"
            backgroundColor="var(--app-snackbar-error)"
            (clicked)="bump('Supprimer')"
          />
          <app-action-icon-with-text-button icon="check" [disabled]="true">Désactivé</app-action-icon-with-text-button>
        </div>
      </section>

      <section class="card">
        <h2>ActionTextButton</h2>
        <div class="row">
          <app-action-text-button (clicked)="bump('Annuler')">Annuler</app-action-text-button>
          <app-action-text-button text="Sans fond" [hasBackground]="false" textColor="var(--app-primary-action)" (clicked)="bump('Sans fond')" />
          <app-action-text-button [disabled]="true">Désactivé</app-action-text-button>
        </div>
      </section>

      <section class="card">
        <h2>StatusIcon (toutes teintes)</h2>
        <div class="row status-row">
          <div class="status-item">
            <app-status-icon icon="check_circle" tint="var(--app-snackbar-success)" [size]="24" />
            <span class="muted">Success</span>
          </div>
          <div class="status-item">
            <app-status-icon icon="warning" tint="var(--app-snackbar-warning)" [size]="24" />
            <span class="muted">Warning</span>
          </div>
          <div class="status-item">
            <app-status-icon icon="error" tint="var(--app-snackbar-error)" [size]="24" />
            <span class="muted">Error</span>
          </div>
          <div class="status-item">
            <app-status-icon icon="info" tint="var(--app-accent-text)" [size]="24" />
            <span class="muted">Info</span>
          </div>
          <div class="status-item">
            <app-status-icon icon="cloud_done" tint="var(--app-primary-action)" [size]="24" />
            <span class="muted">Synced</span>
          </div>
          <div class="status-item">
            <app-status-icon icon="cloud_off" tint="var(--c-yellow-medium)" [size]="24" />
            <span class="muted">Offline</span>
          </div>
        </div>
      </section>

      <section class="card">
        <h2>StyledSearchField</h2>
        <app-styled-search-field
          [value]="search()"
          (valueChange)="search.set($event)"
          placeholderText="Rechercher un exercice…"
        />
        <p class="muted">Saisi : {{ search() || '—' }}</p>
      </section>

      <section class="card">
        <h2>CustomSelect</h2>
        <app-custom-select label="Difficulté" [selected]="selectVal()" [options]="selectOptions" (select)="selectVal.set($event)" />
        <p class="muted">Sélection : {{ selectVal() }}</p>
      </section>

      <section class="card">
        <h2>SingleSelectDropdown</h2>
        <app-single-select-dropdown
          label="Jour de la semaine"
          [selected]="singleVal()"
          [options]="dayOptions"
          [disabledOptions]="['Monday']"
          (select)="singleVal.set($event)"
        />
      </section>

      <section class="card">
        <h2>MultiSelectDropdown</h2>
        <app-multi-select-dropdown
          label="Muscles ciblés"
          [options]="muscleOptions"
          [selectedItems]="multiVal()"
          (selectionChange)="multiVal.set($event)"
        />
        <p class="muted">{{ multiVal().join(', ') || '—' }}</p>
      </section>

      <section class="card">
        <h2>FilterDropdown</h2>
        <app-filter-dropdown label="Filtre" [options]="filterOptions" [selected]="filterVal()" (select)="filterVal.set($event)" />
      </section>

      <section class="card">
        <h2>FilterPanel</h2>
        <p class="muted">
          Cadre filtre repliable réutilisable (le « cadre » du Catalogue d'aliments). Le composant ne
          porte QUE le cadre animé + son contenu projeté : le déclencheur « Filtres » et le
          « Réinitialiser » sont fournis par la page (ici : bouton ci-dessous, reset dans l'en-tête de la
          section micros). Référence à copier pour le catalogue et, plus tard, les recettes.
        </p>
        <!-- Largeur ≈ colonne master du catalogue (2/5) → cadre + grilles 2 colonnes rendent pareil. Le
             conteneur flex (gap space-2) laisse la marge négative du cadre fermé annuler le gap proprement. -->
        <div style="display: flex; flex-direction: column; gap: var(--space-2); max-width: 480px;">
          <!-- Déclencheur fourni par la page (comme le bouton « Filtres » de la toolbar du catalogue). -->
          <app-action-icon-with-text-button
            icon="tune"
            [text]="fpButtonLabel()"
            backgroundColor="var(--c-first-blue)"
            (clicked)="fpOpen.set(!fpOpen())"
          />
          <app-filter-panel [open]="fpOpen()">
            <app-titled-divider title="Catégorie" />
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--space-2) var(--space-4);">
              <app-filter-dropdown label="Règne" [options]="filterOptions" [selected]="fpRealm()" (select)="fpRealm.set($event)" [raised]="true" />
              <app-filter-dropdown label="Groupe" [options]="filterOptions" [selected]="fpGroup()" (select)="fpGroup.set($event)" [raised]="true" />
            </div>
            <app-titled-divider title="Macros" />
            <div style="display: grid; grid-template-columns: minmax(0, max-content) minmax(0, max-content); justify-content: space-between; gap: var(--space-2) var(--space-4);">
              @for (n of fpMacros; track n.key) {
                <app-filter-threshold-row
                  [label]="n.abbr"
                  [labelWidth]="'2.25rem'"
                  [placeholder]="n.unit + '/100g'"
                  [step]="n.step"
                  [op]="fpOpFor(n.key)"
                  (opChange)="fpSetOp(n.key, $event)"
                  [value]="fpRawFor(n.key)"
                  (valueChange)="fpSetRaw(n.key, $event)"
                />
              }
            </div>
            <app-collapsible-section
              cta="les micros"
              [open]="fpMicrosOpen()"
              (openChange)="fpMicrosOpen.set($event)"
            >
              <app-action-icon-with-text-button
                header-trailing
                icon="filter_alt_off"
                text="Réinitialiser"
                [disabled]="fpActiveCount() === 0"
                (clicked)="fpReset()"
              />
              <app-titled-divider title="Vitamines & minéraux" />
              <div style="display: grid; grid-template-columns: minmax(0, max-content) minmax(0, max-content); justify-content: space-between; gap: var(--space-2) var(--space-4);">
                @for (n of fpMicros; track n.key) {
                  <app-filter-threshold-row
                    [label]="n.abbr"
                    [labelWidth]="'3rem'"
                    [placeholder]="n.unit + '/100g'"
                    [op]="fpOpFor(n.key)"
                    (opChange)="fpSetOp(n.key, $event)"
                    [value]="fpRawFor(n.key)"
                    (valueChange)="fpSetRaw(n.key, $event)"
                  />
                }
              </div>
            </app-collapsible-section>
          </app-filter-panel>
        </div>
      </section>

      <section class="card">
        <h2>FilterThresholdRow</h2>
        <app-filter-threshold-row
          label="Fibres"
          placeholder="g / 100 g"
          [op]="ftrOp()"
          (opChange)="ftrOp.set($event)"
          [value]="ftrVal()"
          (valueChange)="ftrVal.set($event)"
        />
        <p class="muted">{{ ftrOp() === 'gte' ? '≥' : '≤' }} {{ ftrVal() || '—' }}</p>
      </section>

      <section class="card">
        <h2>CustomHourPicker</h2>
        <p class="muted">
          Sélecteur d'heure « HH:MM » au style app (2 roues Heures/Minutes) — remplace l'input time
          natif. Vide = aucune heure (facultatif).
        </p>
        <div style="max-width: 320px;">
          <app-custom-hour-picker
            label="Heure indicative (facultatif)"
            [value]="hourVal()"
            (valueChange)="hourVal.set($event)"
          />
        </div>
        <p class="muted">Valeur : {{ hourVal() || '— (aucune)' }}</p>
      </section>

      <section class="card">
        <h2>DateRangePickerDialog</h2>
        <p class="muted">
          Sélecteur de plage : deux calendriers côte à côte (Début | Fin), surlignage de la plage,
          empilé sur écran étroit. Émet le couple début / fin en ISO.
        </p>
        <app-action-icon-with-text-button
          icon="calendar_today"
          text="Choisir une plage"
          (clicked)="drpOpen.set(true)"
        />
        <p class="muted">Plage : {{ drpRange() ? drpRange()!.start + ' → ' + drpRange()!.end : '—' }}</p>
        <app-date-range-picker-dialog
          [open]="drpOpen()"
          [initialStart]="drpRange()?.start ?? null"
          [initialEnd]="drpRange()?.end ?? null"
          (confirm)="drpRange.set($event); drpOpen.set(false)"
          (dismiss)="drpOpen.set(false)"
        />
      </section>

      <section class="card">
        <h2>CollapsibleSection</h2>
        <p class="muted">
          Bandeau repliable : titre + CTA chevron « Afficher / Masquer … » en bleu accent, contenu
          projeté déroulé en animation. Replié par défaut (ex. section micros des panneaux de filtres).
        </p>
        <app-collapsible-section
          title="Vitamines &amp; minéraux"
          cta="les micros"
          [open]="csOpen()"
          (openChange)="csOpen.set($event)"
        >
          <p class="muted" style="margin: 0;">
            Contenu projeté (ici une ligne ; ailleurs : la grille de seuils micros des filtres).
          </p>
        </app-collapsible-section>
        <p class="muted">État : {{ csOpen() ? 'ouvert' : 'replié' }}</p>
      </section>

      <section class="card">
        <h2>TabRowCustom</h2>
        <app-tab-row-custom [items]="tabItems" [selectedIndex]="tabIndex()" (tabSelected)="tabIndex.set($event)" />
      </section>

      <section class="card">
        <h2>DualTabMenu</h2>
        <app-dual-tab-menu
          [topTabs]="dtmTopTabs"
          [subTabsMap]="dtmSubMap"
          [selectedTopIndex]="dtmTop()"
          [selectedSubIndex]="dtmSub()"
          (topTabSelected)="onDtmTop($event)"
          (subTabSelected)="dtmSub.set($event)"
        />
      </section>

      <section class="card">
        <h2>SegmentedIconButton</h2>
        <div class="row">
          <app-segmented-icon-button [selected]="true" icon="show_chart" description="Sélectionné" />
          <app-segmented-icon-button [selected]="false" icon="bar_chart" description="Non sélectionné" />
        </div>
      </section>

      <section class="card">
        <h2>WheelPicker</h2>
        <div style="max-width: 120px;">
          <app-wheel-picker [min]="0" [max]="23" [selected]="wheelVal()" (selectedChange)="wheelVal.set($event)" />
        </div>
        <p class="muted">Valeur : {{ wheelVal() }}</p>
      </section>

      <section class="card">
        <h2>DataTable</h2>
        <p class="muted">
          Tableau de données générique (cellules typées : bool ✓/–, date raccourcie, uuid mono, null —, zebra).
        </p>
        <app-data-table [columns]="tableCols" [rows]="tableRows" />
      </section>

      <section class="card">
        <h2>HorizontalNumberPicker</h2>
        <app-horizontal-number-picker
          label="Répétitions (cible 8–12)"
          [min]="1"
          [max]="20"
          [selected]="hnpVal()"
          [targetMin]="8"
          [targetMax]="12"
          (selectedChange)="hnpVal.set($event)"
        />
        <p class="muted">Valeur : {{ hnpVal() }}</p>
      </section>

      <section class="card">
        <h2>DrawerIconCountIndicator</h2>
        <div class="row">
          <app-drawer-icon-count-indicator icon="mail" [count]="3" />
          <app-drawer-icon-count-indicator icon="notifications" [count]="12" color="var(--app-snackbar-warning)" />
        </div>
      </section>

      <section class="card">
        <h2>DrawerMiniProgress</h2>
        <div class="col">
          <app-drawer-mini-progress [progress]="30" />
          <app-drawer-mini-progress [progress]="75" />
          <app-drawer-mini-progress [progress]="100" />
        </div>
      </section>
      }

      @if (category() === 'icons') {
        @for (g of iconGroups; track g.title) {
          <section class="card">
            <h2>{{ g.title }} ({{ g.icons.length }})</h2>
            <div class="icon-grid">
              @for (n of g.icons; track n) {
                <div class="icon-cell">
                  <app-icon
                    [name]="n"
                    [size]="24"
                    [color]="n === 'cloud_off' ? 'var(--c-yellow-medium)' : 'var(--app-primary-action)'"
                  />
                  <span class="icon-cell__name">{{ n }}</span>
                </div>
              }
            </div>
          </section>
        }
      }

      @if (category() === 'components') {
      <section class="card">
        <h2>ExpandableCard</h2>
        <p class="muted">
          Carte dépliable : en-tête secondBlue (info + actions + chevron animé à droite) au-dessus d'un
          corps thirdBlue qui se déroule / enroule en animation. Le chevron est le seul contrôle.
        </p>
        <div class="col">
          <app-expandable-card>
            <span header><strong>Petit-déjeuner</strong> · 320 kcal</span>
            <app-action-icon-button actions icon="add" [size]="34" [iconSize]="20" />
            <div class="col">
              <span>Flocons d'avoine — 180 kcal</span>
              <span>Lait — 140 kcal</span>
            </div>
          </app-expandable-card>
          <app-expandable-card [expanded]="false">
            <span header><strong>Déjeuner</strong> · 620 kcal (replié par défaut)</span>
            <div class="col">
              <span>Riz complet — 300 kcal</span>
              <span>Poulet — 220 kcal</span>
              <span>Légumes — 100 kcal</span>
            </div>
          </app-expandable-card>
        </div>
      </section>
      <section class="card">
        <h2>DetailRow</h2>
        <div class="col">
          <app-detail-row icon="fitness_center" label="Séries" value="4 × 12" />
          <app-detail-row icon="scale" label="Charge" value="80 kg" valueColor="var(--app-accent-text)" />
        </div>
        <h3 class="sub">DetailRowWithIndentation</h3>
        <app-detail-row-with-indentation
          icon="notes"
          label="Note"
          value="Texte long qui passe à la ligne sous lui-même : l'indentation reste alignée sur le début du texte, pas sous l'icône."
        />
      </section>

      <section class="card">
        <h2>OptionRow</h2>
        <div class="col">
          <app-option-row label="Modifier le profil" icon="edit" (clicked)="bump('edit profil')" />
          <app-option-row label="Supprimer le compte" icon="delete" (clicked)="bump('delete compte')" />
        </div>
      </section>

      <section class="card">
        <h2>EmptyListRow</h2>
        <div class="col">
          <app-empty-list-row text="Aucune tâche aujourd'hui" icon="event_busy" />
          <app-empty-list-row text="Liste vide (sans icône)" />
        </div>
      </section>

      <section class="card">
        <h2>ListFrame / ListRow</h2>
        <div class="col">
          <!-- Cadre thirdBlue + rows à plat séparées par un filet inset. Pattern partagé : aliments d'un
               repas, catalogue, historique des objectifs, recettes & repas. La row projette N'IMPORTE
               QUEL contenu (texte simple OU bloc riche multi-ligne) → réutilisable tel quel côté sport. -->
          <app-list-frame>
            <app-list-row (clicked)="bump('list row 1')">
              <span style="flex: 1; min-width: 0; color: var(--app-text-primary)">Row cliquable</span>
              <app-action-icon-button icon="more_vert" [size]="34" [iconSize]="20" (click)="$event.stopPropagation()" />
            </app-list-row>
            <app-list-row [selected]="true" (clicked)="bump('list row 2')">
              <span style="flex: 1; min-width: 0; color: var(--app-text-primary)">Row sélectionnée (liseré primaryAction)</span>
              <app-action-icon-button icon="more_vert" [size]="34" [iconSize]="20" (click)="$event.stopPropagation()" />
            </app-list-row>
            <!-- Contenu riche (2 lignes) projeté dans la row : titre + sous-titre. Mêmes filet/coins/
                 sélection que les rows simples (ex. lignes macros « bare » des ingrédients/journal). -->
            <app-list-row [clickable]="false">
              <span style="flex: 1; min-width: 0; display: flex; flex-direction: column">
                <span style="color: var(--app-text-primary); font-size: 14px">Contenu riche multi-ligne</span>
                <span style="color: var(--c-gray-blue); font-size: 12px">Sous-titre / métadonnées projetés</span>
              </span>
              <app-action-icon-button icon="more_vert" [size]="34" [iconSize]="20" />
            </app-list-row>
            <app-list-row [clickable]="false">
              <span style="flex: 1; min-width: 0; color: var(--app-text-primary)">Row non cliquable (filet inset, dernière sans filet)</span>
              <app-action-icon-button icon="more_vert" [size]="34" [iconSize]="20" />
            </app-list-row>
          </app-list-frame>
        </div>
      </section>

      <section class="card">
        <h2>EntityListRow</h2>
        <div class="col">
          <!-- Style Planned (Android) : nom weight 2.6 + groupe icônes weight 1.4 (centré) + sets×reps weight 1.4 (centré). -->
          <app-entity-list-row
            name="Bench Press"
            [nameWeight]="2.6"
            backgroundColor="var(--app-bg-recessed)"
            nameBoxColor="var(--app-bg-surface)"
            [contentEndPadding]="4"
            (nameClick)="bump('Bench Press')"
          >
            <div trailing style="flex: 1.4; display: flex; align-items: center; justify-content: center;">
              <div style="width: 40px; display: flex; align-items: center; justify-content: center;"><app-status-icon icon="cloud_done" tint="var(--app-primary-action)" [size]="20" /></div>
              <div style="width: 40px; display: flex; align-items: center; justify-content: center;"><app-status-icon icon="check_circle" tint="var(--app-snackbar-success)" [size]="20" /></div>
            </div>
            <div trailing style="flex: 1.4; display: flex; align-items: center; justify-content: flex-end; padding-right: 12px; color: var(--app-text-primary); font-size: var(--font-size-body); font-weight: var(--font-weight-medium);">3 × 10</div>
          </app-entity-list-row>
          <!-- Style Routine (Android) : dragHandle leading (box 40) + nom weight 1 (boîte cliquable bg-surface, comme Bench Press) + sync (40) + checkbox bleu-gris (44), gaps 8 ; sans bordure. -->
          <app-entity-list-row
            name="Morning routine"
            [nameWeight]="1"
            [nameMaxLines]="1"
            backgroundColor="var(--app-bg-recessed)"
            nameBoxColor="var(--app-bg-surface)"
            [contentEndPadding]="8"
            (nameClick)="bump('routine')"
          >
            <div leading style="width: 40px; display: flex; align-items: center; justify-content: center;">
              <app-icon name="drag_indicator" [size]="24" color="var(--app-text-secondary)" />
            </div>
            <div trailing style="width: 40px; display: flex; align-items: center; justify-content: center; margin-left: var(--space-2);">
              <app-status-icon icon="cloud_done" tint="var(--app-primary-action)" [size]="20" />
            </div>
            <div trailing style="width: 44px; display: flex; align-items: center; justify-content: center; margin-left: var(--space-2);">
              <app-icon name="check_box_outline_blank" [size]="24" color="var(--app-text-secondary)" />
            </div>
          </app-entity-list-row>
          <!-- Style PendingDeletion : même distribution pondérée, grisée. -->
          <app-entity-list-row
            name="Squat — pending deletion"
            [nameWeight]="2.6"
            backgroundColor="var(--app-bg-recessed)"
            [isPendingDeletion]="true"
            [contentEndPadding]="4"
            (nameClick)="bump('squat')"
          >
            <div trailing style="flex: 1.4; display: flex; align-items: center; justify-content: center;">
              <div style="width: 40px; display: flex; align-items: center; justify-content: center;"><app-status-icon icon="cloud_off" tint="var(--c-yellow-medium)" [size]="20" /></div>
              <div style="width: 40px; display: flex; align-items: center; justify-content: center;"><app-status-icon icon="delete" tint="var(--app-text-tertiary)" [size]="20" /></div>
            </div>
            <div trailing style="flex: 1.4; display: flex; align-items: center; justify-content: flex-end; padding-right: 12px; color: var(--app-text-tertiary); font-size: var(--font-size-body); font-weight: var(--font-weight-medium);">3 × 10</div>
          </app-entity-list-row>
          <!-- Style « catalogue » (Exercices / Muscles / Matériel) : zone trailing via la convention
               partagée appEntityRowTrailing → sync (cloud) + favori + flèche, boutons d'action PLEINE
               HAUTEUR (fond collé haut/bas, pas de liseré) + gap aéré. Un seul endroit à régler. -->
          <app-entity-list-row
            name="Développé couché"
            [nameMaxLines]="1"
            backgroundColor="var(--app-bg-recessed)"
            [contentEndPadding]="6"
          >
            <span trailing appEntityRowTrailing>
              <app-icon name="cloud_done" [size]="22" color="var(--app-primary-action)" />
              <app-action-icon-button
                [icon]="scFav() ? 'star' : 'star_border'"
                [backgroundColor]="scFav() ? 'var(--c-orange-medium)' : 'var(--app-bg-button)'"
                [tint]="scFav() ? 'var(--app-on-accent)' : 'var(--app-text-primary)'"
                (clicked)="scFav.set(!scFav())"
              />
              <app-action-icon-button
                icon="arrow_right_alt"
                tint="var(--app-text-primary)"
                [backgroundColor]="scSelected() ? 'var(--app-primary-action)' : 'var(--c-blue-medium)'"
                (clicked)="scSelected.set(!scSelected())"
              />
            </span>
          </app-entity-list-row>
        </div>
      </section>

      <section class="card">
        <h2>SegmentedIconToggle</h2>
        <h3 class="sub">2 segments</h3>
        <app-segmented-icon-toggle [items]="seg2Items" [selected]="seg2()" (select)="seg2.set($event)" />
        <h3 class="sub">3 segments</h3>
        <app-segmented-icon-toggle [items]="seg3Items" [selected]="seg3()" (select)="seg3.set($event)" />
        <h3 class="sub">5 segments — variante Goals (width 36, icône 16)</h3>
        <app-segmented-icon-toggle
          [items]="seg5Items"
          [selected]="seg5()"
          [width]="36"
          [iconSize]="16"
          unselectedBorderColor="var(--app-text-secondary)"
          (select)="seg5.set($event)"
        />
      </section>

      <section class="card">
        <h2>GenericEntityCard</h2>
        <p class="muted">Cliquer le header pour déplier / replier.</p>
        <app-generic-entity-card title="Bench Press" icon="exercise">
          <app-icon headerTrailing name="star" [size]="20" color="var(--c-yellow-medium)" />
          <app-detail-row details icon="fitness_center" label="Séries" value="4 × 12" />
          <app-detail-row details icon="scale" label="Charge" value="80 kg" />
          <app-action-icon-button actions icon="edit" backgroundColor="var(--c-medium-green)" (clicked)="bump('edit')" />
          <app-action-icon-button actions icon="delete" backgroundColor="var(--c-red-medium)" (clicked)="bump('delete')" />
        </app-generic-entity-card>
      </section>

      <section class="card">
        <h2>SummaryItem</h2>
        <div class="row">
          <app-summary-item icon="exercise" value="12" label="Séances" iconTint="var(--app-primary-action)" />
          <app-summary-item icon="timer" value="5h" label="Durée" iconTint="var(--app-snackbar-success)" [compact]="true" />
        </div>
      </section>

      <section class="card">
        <h2>ColumnHeaderActionsCard</h2>
        <app-column-header-actions-card
          columnName="weight"
          [sortDir]="colSort()"
          [filterValue]="colFilter()"
          (filterValueChange)="colFilter.set($event)"
          (setSort)="colSort.set($event)"
          filterPlaceholder="Filtrer weight…"
        />
      </section>

      <section class="card">
        <h2>HmsWheelPicker</h2>
        <app-hms-wheel-picker
          [hours]="hmsH()"
          [minutes]="hmsM()"
          [seconds]="hmsS()"
          (hoursChange)="hmsH.set($event)"
          (minutesChange)="hmsM.set($event)"
          (secondsChange)="hmsS.set($event)"
        />
      </section>

      <section class="card">
        <h2>TimeRangePickerBar</h2>
        <app-time-range-picker-bar
          label="Plage horaire"
          [minMinutes]="0"
          [maxMinutes]="1439"
          [stepMinutes]="15"
          [startMinutes]="trStart()"
          [endMinutes]="trEnd()"
          (rangeChange)="onTimeRange($event)"
        />
        <p class="muted">{{ trStart() }} → {{ trEnd() }} (minutes)</p>
      </section>

      <section class="card">
        <h2>AppBottomSheet</h2>
        <app-dialog-secondary-button (clicked)="showSheet.set(true)">Ouvrir le bottom sheet</app-dialog-secondary-button>
        <app-bottom-sheet [open]="showSheet()" (dismissRequest)="showSheet.set(false)">
          <div style="padding: 0 var(--space-4) var(--space-4);">
            <app-titled-divider title="Contenu du sheet" />
            <p class="muted">Clique le fond pour fermer.</p>
          </div>
        </app-bottom-sheet>
      </section>

      <section class="card">
        <h2>StatsChartCard</h2>
        <app-stats-chart-card title="Progression (8 semaines)" [isEmpty]="false" emptyText="Aucune donnée">
          <app-multi-line-chart chart [series]="chartSeries" [xLabels]="chartLabels" [height]="280" />
        </app-stats-chart-card>
      </section>

      <section class="card">
        <h2>DataGridPaginationBar</h2>
        <app-data-grid-pagination-bar
          [totalCount]="237"
          [pageSize]="pageSizeVal()"
          [currentPage]="pageIndex()"
          [pageSizeOptions]="[25, 50, 100]"
          (prev)="pageIndex.set(pageIndex() - 1)"
          (next)="pageIndex.set(pageIndex() + 1)"
          (pageSizeChange)="pageSizeVal.set($event); pageIndex.set(0)"
        />
      </section>

      <section class="card">
        <h2>SetRow</h2>
        <div class="col">
          <app-set-row
            [set]="demoSet"
            [targetRepsMin]="8"
            [targetRepsMax]="12"
            (indexClick)="bump('index')"
            (editRepsClick)="bump('edit reps')"
            (editWeightClick)="bump('edit weight')"
            (deleteClick)="bump('delete set')"
            (addNoteClick)="bump('note set')"
          />
          <app-set-row [set]="demoSetDrop" [targetRepsMin]="8" [targetRepsMax]="12" />
          <app-set-row [set]="demoSetSkipped" [targetRepsMin]="8" [targetRepsMax]="12" />
        </div>
        <p class="muted">{{ lastAction() || '—' }}</p>
      </section>

      <section class="card">
        <h2>DrawerItem</h2>
        <div style="background: var(--app-bg-recessed); border-radius: 8px; overflow: hidden;">
          <app-drawer-item icon="calendar_month" label="Calendrier" [active]="diActive() === 'cal'" (clicked)="diActive.set('cal')" />
          <app-drawer-item icon="notifications" label="Notifications" [active]="diActive() === 'notif'" (clicked)="diActive.set('notif')">
            <app-drawer-icon-count-indicator trailing icon="mail" [count]="3" />
          </app-drawer-item>
          <app-drawer-item icon="fitness_center" label="Bench Day" [active]="diActive() === 'bench'" (clicked)="diActive.set('bench')">
            <app-drawer-mini-progress trailing [progress]="60" />
          </app-drawer-item>
        </div>
      </section>

      <section class="card">
        <h2>DrawerSection</h2>
        <app-drawer-section title="Activité">
          <app-drawer-item icon="home" label="Accueil" [active]="dsActive() === 'home'" (clicked)="dsActive.set('home')" />
          <app-drawer-item icon="calendar_month" label="Calendrier" [active]="dsActive() === 'cal'" (clicked)="dsActive.set('cal')" />
          <app-drawer-item icon="equalizer" label="Statistiques" [active]="dsActive() === 'stats'" (clicked)="dsActive.set('stats')" />
        </app-drawer-section>
      </section>

      <section class="card">
        <h2>DrawerFooter</h2>
        <app-drawer-footer text="Synchronisé">
          <app-icon trailing name="wifi" [size]="20" color="var(--app-snackbar-success)" />
          <app-icon trailing name="cloud_done" [size]="20" color="var(--app-primary-action)" />
          <app-icon trailing name="router" [size]="20" color="var(--app-snackbar-success)" />
        </app-drawer-footer>
      </section>
      }

      @if (category() === 'components') {
      <section class="card">
        <h2>ScreenTitleBar</h2>
        <div class="col">
          <app-screen-title-bar title="Mes exercices" />
          <app-screen-title-bar title="Cliquable — revenir à aujourd'hui" [clickable]="true" (clicked)="bump('title bar')" />
        </div>
      </section>

      <section class="card">
        <h2>ListSearchHeader</h2>
        <app-list-search-header
          [searchQuery]="lshSearch()"
          (searchQueryChange)="lshSearch.set($event)"
          searchPlaceholder="Rechercher…"
          resultsCountText="24 résultats · tri A → Z"
          [allSynced]="false"
          (syncClick)="bump('sync')"
          (moreClick)="bump('more')"
          (sortChange)="bump('sort: ' + $event)"
        />
      </section>

      <section class="card">
        <h2>SummaryRow</h2>
        <h3 class="sub">SummaryRow — standard</h3>
        <app-summary-row [items]="summaryItems" />
        <h3 class="sub">SummaryRow — compact (3+ cellules)</h3>
        <app-summary-row [items]="summaryItemsCompact" [compact]="true" />
      </section>

      <section class="card">
        <h2>ConfirmationDialog</h2>
        <app-dialog-primary-button color="var(--c-red-medium)" (clicked)="showConfirm.set(true)">Supprimer…</app-dialog-primary-button>
        <app-confirmation-dialog
          [open]="showConfirm()"
          title="Supprimer l'exercice ?"
          message="Cette action est irréversible."
          confirmButtonText="Supprimer"
          dismissButtonText="Annuler"
          (confirm)="bump('supprimé'); showConfirm.set(false)"
          (dismiss)="showConfirm.set(false)"
        />
      </section>

      <section class="card">
        <h2>FormDialog</h2>
        <app-dialog-secondary-button (clicked)="showForm.set(true)">Ouvrir le formulaire</app-dialog-secondary-button>
        <app-form-dialog
          [open]="showForm()"
          title="Nouvel exercice"
          confirmText="Créer"
          (confirm)="bump('créé: ' + (name() || '?')); showForm.set(false)"
          (dismiss)="showForm.set(false)"
        >
          <app-custom-text-field label="Nom" placeholder="Ex. Squat" [value]="name()" (valueChange)="name.set($event)" />
          <app-custom-select label="Difficulté" [selected]="selectVal()" [options]="selectOptions" (select)="selectVal.set($event)" />
        </app-form-dialog>
      </section>

      <section class="card">
        <h2>CustomDatePickerDialog</h2>
        <app-action-icon-with-text-button
          icon="calendar_month"
          text="Choisir une date"
          backgroundColor="var(--c-first-blue)"
          (clicked)="showDate.set(true)"
        />
        <app-custom-date-picker-dialog
          [open]="showDate()"
          title="Date de naissance"
          [initialIso]="dateResult() || null"
          (confirm)="dateResult.set($event); showDate.set(false)"
          (dismiss)="showDate.set(false)"
        />
        <p class="muted">Date : {{ dateResult() || '—' }}</p>
      </section>

      <section class="card">
        <h2>OptionsBottomSheet</h2>
        <app-dialog-secondary-button (clicked)="showOptions.set(true)">Ouvrir les options</app-dialog-secondary-button>
        <app-options-bottom-sheet
          [open]="showOptions()"
          title="Actions"
          [actions]="sheetActions"
          (dismissRequest)="showOptions.set(false)"
          (actionSelected)="bump('action: ' + $event); showOptions.set(false)"
        />
        <p class="muted">Dernière action : {{ lastAction() || '—' }}</p>
      </section>

      <section class="card">
        <h2>MultiLineChart</h2>
        <app-multi-line-chart [series]="chartSeries" [xLabels]="chartLabels" [height]="280" />
      </section>

      <section class="card">
        <h2>RadarChart</h2>
        <h3 class="sub">Macros du jour (% objectif) — 2 séries, remplissage de zone</h3>
        <app-radar-chart [axes]="macroAxes" [series]="macroRadarSeries" [height]="300" />
        <h3 class="sub">Équilibre par zone (multi-série, sport)</h3>
        <app-radar-chart [axes]="balanceAxes" [series]="balanceRadarSeries" [height]="320" />
      </section>

      <section class="card">
        <h2>ConcentricRingsChart</h2>
        <p class="muted">
          Anneaux concentriques (primitif ConcentricRings + libellé central) — graphe DS générique.
          Démo : profil macros du jour vs cible (kcal extérieur → fibres centre), étiquettes « en étoile ».
        </p>
        <!-- Cadre thirdBlue (recessed + padding) autour du graphe, à l'intérieur de la card bleue —
             même présentation que l'exemple DonutChart ci-dessous. -->
        <div style="width: 100%; border-radius: var(--radius-md); background: var(--app-bg-recessed); padding: var(--space-2);">
          <app-concentric-rings-chart
            [rings]="concentricDemoRings"
            [centerText]="demoDayKcal.toString()"
            [centerColor]="concentricKcalColor"
          />
        </div>
      </section>

      <section class="card">
        <h2>DonutChart</h2>
        <p class="muted">
          Donut de répartition (PieChart ECharts) — graphe cercle DS générique. Étiquettes « nom + % »
          à côté des parts avec ligne de rappel allongée, libellé central optionnel (total).
          Démo : répartition des kcal du jour par macro.
        </p>
        <!-- Donut « exemple » = RÉPLIQUE EXACTE de celui de la page Objectifs « Répartition des calories »
             (cadre recessed padding space-2 + mode [fill], host en colonne flex ; total kcal en bleu,
             texte central +10%). C'est la configuration de référence à réutiliser ailleurs dans l'app —
             reproduit la structure .breakdown__donut / .breakdown__donut-chart d'Objectifs, ici à taille
             fixe (360×300) faute de grille à hauteur contrainte. -->
        <div
          style="max-width: 420px; height: 300px; margin: 0 auto; border-radius: var(--radius-md); background: var(--app-bg-recessed); padding: var(--space-2); display: flex; align-items: stretch;"
        >
          <app-donut-chart
            style="flex: 1.5; min-width: 0; display: flex; flex-direction: column; min-height: 0;"
            [slices]="donutDemoSlices"
            [fill]="true"
            [height]="190"
            [centerLabel]="demoDayKcal.toString()"
            centerSub="kcal"
            emptyText="—"
          />
        </div>
      </section>

      <section class="card">
        <h2>PhasePickerDialog</h2>
        <app-dialog-secondary-button (clicked)="showPhase.set(true)">Choisir la phase</app-dialog-secondary-button>
        <app-phase-picker-dialog
          [open]="showPhase()"
          (phaseSelected)="bump('phase: ' + $event); showPhase.set(false)"
          (dismiss)="showPhase.set(false)"
        />
      </section>

      <section class="card">
        <h2>StatusPickerDialog</h2>
        <app-dialog-secondary-button (clicked)="showStatus.set(true)">Changer le statut</app-dialog-secondary-button>
        <app-status-picker-dialog
          [open]="showStatus()"
          title="Statut de la série"
          [options]="statusOptions"
          [selected]="statusVal()"
          (confirm)="statusVal.set($event); bump('statut: ' + $event); showStatus.set(false)"
          (dismiss)="showStatus.set(false)"
        />
        <p class="muted">Statut : {{ statusVal() }}</p>
      </section>

      <section class="card">
        <h2>ExercisePickerBottomSheet</h2>
        <app-dialog-secondary-button (clicked)="showExPicker.set(true)">Ajouter un exercice</app-dialog-secondary-button>
        <app-exercise-picker-bottom-sheet
          [open]="showExPicker()"
          title="Ajouter un exercice"
          [exercises]="exPickerItems"
          [equipmentOptions]="exEquipments"
          (selectExercise)="bump('ajout: ' + $event); showExPicker.set(false)"
          (viewExercise)="bump('voir: ' + $event)"
          (dismissRequest)="showExPicker.set(false)"
        />
      </section>

      <section class="card">
        <h2>CalendarMonthGrid</h2>
        <div class="democal-panel">
          <div class="demo-weekdays">
            <span>Lun</span><span>Mar</span><span>Mer</span><span>Jeu</span><span>Ven</span><span>Sam</span><span>Dim</span>
          </div>
          <app-calendar-month-grid [year]="calYear" [month]="calMonth" [firstDayOffset]="calOffset">
            <ng-template let-iso>
              @let st = calStatus(iso);
              <div class="democal" [class.democal--today]="iso === calToday">
                <app-icon [name]="st.icon" [size]="15" [color]="st.color" />
                <span class="democal__num">{{ calDayNum(iso) }}</span>
              </div>
            </ng-template>
          </app-calendar-month-grid>
        </div>
      </section>

      <section class="card">
        <h2>AppSnackbarHost</h2>
        <app-snackbar-host
          [snackbars]="demoSnacks"
          (actionClick)="bump('snack action: ' + $event)"
          (secondaryActionClick)="bump('snack secondary: ' + $event)"
        />
        <p class="muted">Dernière action : {{ lastAction() || '—' }}</p>
      </section>

      <section class="card">
        <h2>BottomNavBar</h2>
        <p class="muted">Largeur = contenu (barre flottante), pas plein écran.</p>
        <app-bottom-nav-bar [items]="bnbItems" [selected]="bnbVal()" (select)="bnbVal.set($event)" />
        <p class="muted">Sélection : {{ bnbVal() }}</p>
      </section>
      }

      @if (category() === 'nutrition') {
      <section class="card">
        <h2>NutritionSummaryPanel — affichage live (ligne / barre / radar)</h2>
        <p class="muted">Panneau partagé catalogue (T5) + recettes (T7). Bascule l'affichage :</p>
        <app-segmented-icon-toggle [items]="nspDisplayItems" [selected]="nspDisplay()" (select)="setNspDisplay($event)" />
        <h3 class="sub">Aliment (per 100 g, sans cible) — micros affichés</h3>
        <app-nutrition-summary-panel
          [kcal]="demoFoodKcal"
          [macros]="demoFoodMacros"
          [micros]="demoFoodMicros"
          [display]="nspDisplay()"
          unitSuffix="/ 100 g"
        />
      </section>

      <section class="card">
        <h2>NutritionSummaryPanel — états</h2>
        <h3 class="sub">Profil brut, mode barres (sans cible) — barres relatives au plus grand macro</h3>
        <app-nutrition-summary-panel [kcal]="demoFoodKcal" [macros]="demoFoodMacros" [micros]="demoFoodMicros" display="bar" />
        <h3 class="sub">Avec cibles (objectifs) — avancement vs cible, mode barres</h3>
        <app-nutrition-summary-panel
          [kcal]="demoDayKcal"
          [macros]="demoDayMacros"
          [micros]="demoDayMicros"
          [targets]="demoTargets"
          display="bar"
        />
        <h3 class="sub">Avec cibles — radar (% objectif + repère)</h3>
        <app-nutrition-summary-panel [kcal]="demoDayKcal" [macros]="demoDayMacros" [targets]="demoTargets" display="radar" />
        <h3 class="sub">Mode ligne, sans micros (showMicros = false)</h3>
        <app-nutrition-summary-panel [kcal]="demoFoodKcal" [macros]="demoFoodMacros" display="line" [showMicros]="false" />
        <h3 class="sub">Sodium au-dessus du plafond → barre d'alerte</h3>
        <app-nutrition-summary-panel [kcal]="demoFoodKcal" [macros]="demoFoodMacros" [micros]="demoSaltyMicros" display="bar" />
      </section>

      <section class="card">
        <h2>MacroEntryRow</h2>
        <p class="muted">
          Ligne aliment / ingrédient partagée (aliments des repas du journal + ingrédients des recettes) :
          macros colorées + grammes + menu ⋮ + chevron qui déroule les micros. Liste façon corps de carte
          repas (cadre thirdBlue, lignes séparées par un filet), sans en-tête.
        </p>
        <div class="mer-demo">
          <app-macro-entry-row [data]="demoEntryRow">
            <span trailing class="mer-demo__qty">60 g</span>
            <app-action-icon-button
              trailing
              icon="more_vert"
              backgroundColor="var(--c-first-blue)"
              [size]="34"
              [iconSize]="20"
            />
          </app-macro-entry-row>
          <app-macro-entry-row [data]="demoEntryRowNoMicros" [divider]="false">
            <span trailing class="mer-demo__qty">120 g</span>
            <app-action-icon-button
              trailing
              icon="more_vert"
              backgroundColor="var(--c-first-blue)"
              [size]="34"
              [iconSize]="20"
            />
          </app-macro-entry-row>
        </div>
      </section>
      }
        </div>
      </div>
      </div>
    </main>
  `,
  styles: [
    `
      /* Cadre de démo « liste façon corps de carte repas » (MacroEntryRow), sans en-tête. */
      .mer-demo {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-1) var(--space-2) var(--space-1) var(--space-3);
      }
      .mer-demo__qty {
        color: var(--app-text-tertiary);
        font-size: 13px;
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
      }
      .showcase {
        display: flex;
        flex-direction: column;
      }
      /* Gouttière de page sur le CORPS uniquement → la barre de titre (ScreenTitleBar) reste pleine
         largeur edge-to-edge comme les autres écrans, au lieu d'être prise dans la gouttière. */
      .showcase__body {
        display: flex;
        flex-direction: column;
        gap: var(--space-5);
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
      }
      /* Explorer master-detail : liste (1/4) + détail (3/4). Onglets galerie → 1 colonne pleine largeur. */
      .explorer {
        display: grid;
        grid-template-columns: 1fr 3fr;
        gap: 0;
        align-items: start;
        /* Collé à la ligne d'onglets : annule la gouttière verticale de .showcase (gap space-5). */
        margin-top: calc(var(--space-5) * -1);
      }
      .explorer__nav {
        position: sticky;
        top: var(--space-3);
        display: flex;
        flex-direction: column;
        gap: 2px;
        max-height: calc(100vh - 120px);
        overflow: auto;
        /* Panneau foncé (thirdBlue = --app-bg-recessed) sous les onglets. */
        background: var(--app-bg-recessed);
        padding: var(--space-3);
      }
      /* Le champ de recherche a le même fond thirdBlue (--app-bg-recessed) que le panneau → on ne le
         voit pas. On l'encadre d'un liseré d'une AUTRE couleur de fond (--app-bg-surface) pour le
         délimiter, sans changer la couleur du champ lui-même (il reste thirdBlue). */
      .explorer__nav app-styled-search-field {
        display: block;
        background: var(--app-bg-surface);
        padding: var(--space-1);
        border-radius: var(--radius-md);
      }
      /* Petit divider sous le champ de recherche, pour le séparer des items de la liste (T5). */
      .explorer__nav-divider {
        height: 1px;
        /* grayBlue (--c-gray-blue #5e78a0, primitive identique dans les 2 thèmes) — demandé explicitement ;
           bien contrasté sur le panneau --app-bg-recessed en clair comme en sombre. */
        background: var(--c-gray-blue);
        margin: var(--space-2) 0;
      }
      .explorer__item {
        text-align: left;
        font-family: var(--font-family-base);
        font-size: 13px;
        padding: var(--space-2) var(--space-3);
        border: none;
        border-radius: var(--radius-md);
        background: transparent;
        color: var(--app-text-secondary);
        cursor: pointer;
      }
      /* Survol = first-blue (sauf l'item sélectionné, qui ne réagit pas au survol). */
      .explorer__item:not(.explorer__item--active):hover {
        background: var(--c-first-blue);
        color: #ffffff;
      }
      .explorer__item--active {
        background: var(--app-primary-action);
        color: #ffffff;
      }
      .explorer__detail {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-5);
        /* Panneau foncé (thirdBlue = --app-bg-recessed) sous les onglets ; les cards (plus claires) ressortent. */
        background: var(--app-bg-recessed);
        padding: var(--space-4);
        /* Divider gris-bleu entre les 2 colonnes (même couleur/épaisseur que la division onglets/colonnes). */
        border-left: 1px solid var(--app-divider);
      }
      /* Bordure transparente par défaut (pas de décalage) ; bleue sur la card sélectionnée. */
      .explorer__detail > section {
        box-sizing: border-box;
        border: 1px solid transparent;
      }
      .explorer__detail > section.sc-active {
        border-color: var(--app-primary-action);
      }
      /* T4 : card masquée quand elle ne matche pas la recherche (miroir de la liste de gauche). */
      .explorer__detail > section.sc-hidden {
        display: none;
      }
      @media (max-width: 720px) {
        .explorer {
          grid-template-columns: 1fr;
        }
        .explorer__nav {
          position: static;
          max-height: none;
          flex-direction: row;
          flex-wrap: wrap;
        }
      }
      h2 {
        font-size: var(--font-size-subtitle);
        margin: 0 0 var(--space-3);
        color: var(--app-primary-action);
        font-weight: 600;
      }
      h3.sub {
        font-size: var(--font-size-subtitle);
        margin: var(--space-4) 0 var(--space-2);
        color: var(--app-text-secondary);
      }
      .muted {
        color: var(--app-text-secondary);
        font-size: var(--font-size-caption);
        margin: var(--space-2) 0 0;
      }
      .card {
        background: var(--c-ui-showcase-card);
        border-radius: var(--radius-md);
        padding: var(--space-5);
      }
      .row {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-4);
        align-items: center;
      }
      .col {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .tabs {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-1);
        border-bottom: 1px solid var(--app-divider);
        /* Ligne d'onglets sur fond foncé thirdBlue (--app-bg-recessed). */
        background: var(--app-bg-recessed);
      }
      .tab {
        appearance: none;
        -webkit-appearance: none;
        background: transparent;
        border: none;
        border-bottom: 2px solid transparent;
        color: var(--app-text-secondary);
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        padding: var(--space-2) var(--space-3);
        cursor: pointer;
        transition: color 0.15s ease, border-color 0.15s ease;
      }
      .tab:hover {
        color: var(--app-text-primary);
      }
      .tab--active {
        color: var(--app-primary-action);
        border-bottom-color: var(--app-primary-action);
        font-weight: var(--font-weight-medium);
      }
      /* Bascule de thème (dark/clair) à l'extrémité droite de la ligne d'onglets, centrée verticalement. */
      .tabs__theme {
        margin-left: auto;
        align-self: center;
      }
      .swatches {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
        gap: var(--space-3);
      }
      .swatch {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .swatch__chip {
        width: 28px;
        height: 28px;
        border-radius: var(--radius-sm);
        border: 1px solid var(--app-divider);
      }
      .swatch__text {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .swatch__name {
        font-size: var(--font-size-caption);
        color: var(--app-text-secondary);
      }
      .swatch__token {
        font-size: 11px;
        color: var(--app-text-tertiary);
        font-family: monospace;
      }
      .foundations-intro {
        margin: 0;
      }
      .spacer-demo .box {
        width: 28px;
        height: 28px;
        border-radius: var(--radius-sm);
        background: var(--app-selected-fill);
        display: inline-block;
      }
      .status-row {
        gap: var(--space-5);
      }
      .status-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-1);
      }
      .icon-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
        gap: var(--space-3);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-4);
      }
      .icon-cell {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-2) var(--space-1);
      }
      .icon-cell__name {
        font-size: var(--font-size-caption);
        color: var(--app-text-secondary);
        text-align: center;
      }
      .democal-panel {
        max-width: 400px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3);
      }
      .demo-weekdays {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        margin-bottom: var(--space-2);
      }
      .demo-weekdays span {
        text-align: center;
        font-size: 13px;
        font-weight: 600;
        color: var(--c-light-gray-blue);
      }
      .democal {
        aspect-ratio: 43 / 52;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 8px;
        box-sizing: border-box;
        padding: 4px;
        border-radius: 8px;
        border: 1px solid transparent;
      }
      .democal--today {
        border-color: var(--app-primary-action);
      }
      .democal__num {
        font-size: 16px;
        line-height: 1;
        font-weight: var(--font-weight-medium);
        color: var(--app-text-tertiary);
      }
    `,
  ],
})
export class Showcase {
  protected readonly theme = inject(ThemeService);
  protected readonly category = signal<'foundations' | 'components' | 'icons' | 'nutrition'>('foundations');
  protected readonly categories: { id: 'foundations' | 'components' | 'icons' | 'nutrition'; label: string }[] = [
    // Onglets = structure de dossiers du projet (designsystem/{theme,common_components,icons} + features).
    { id: 'foundations', label: 'Foundations' }, // designsystem/theme
    { id: 'components', label: 'Common Components' }, // designsystem/common_components
    { id: 'icons', label: 'Icons' }, // designsystem/icons
    { id: 'nutrition', label: 'Nutrition' }, // features/nutrition
  ];

  // ----- Explorer master-detail : liste filtrable (gauche) + composant sélectionné (droite) -----
  private readonly detailRef = viewChild<ElementRef<HTMLElement>>('detail');
  private readonly navListRef = viewChild<ElementRef<HTMLElement>>('navList');
  /** Liste des composants de l'onglet courant (dérivée des <section> rendues). */
  protected readonly nav = signal<{ id: string; label: string }[]>([]);
  /** Texte du filtre de la colonne de gauche. */
  protected readonly navFilter = signal('');
  /** Id du composant affiché à droite. */
  protected readonly sel = signal('');

  protected readonly filteredNav = computed(() => {
    const q = this.navFilter().toLowerCase().trim();
    const items = this.nav();
    return q ? items.filter((i) => i.label.toLowerCase().includes(q)) : items;
  });

  // La liste de gauche est dérivée des <section class="card"> RENDUES. On la (re)construit APRÈS le
  // rendu (queueMicrotask) : au changement d'onglet, un effect() classique lit le DOM AVANT que l'@if
  // du détail ait basculé → il listait les sections de l'ANCIEN onglet (ex. Foundations sur Common
  // Components). Le microtask s'exécute une fois le DOM à jour. Galerie (icons) → pas de liste.
  private readonly _navSync = effect(() => {
    const cat = this.category(); // dépendance : relance au changement d'onglet
    queueMicrotask(() => this.rebuildNav(cat));
  });

  // T4 : la colonne de droite reflète le filtre — les cards qui ne matchent pas la recherche sont
  // masquées (display:none), exactement comme les items de la liste de gauche (filteredNav). Sans
  // filtre actif, tout reste visible (évite de tout masquer pendant que `nav` se reconstruit).
  private readonly _detailFilter = effect(() => {
    const active = this.navFilter().trim().length > 0;
    const visible = new Set(this.filteredNav().map((i) => i.id));
    const host = this.detailRef()?.nativeElement;
    if (!host) return;
    host.querySelectorAll<HTMLElement>(':scope > section.card').forEach((el) => {
      const id = el.dataset['scid'];
      el.classList.toggle('sc-hidden', active && !!id && !visible.has(id));
    });
  });

  private rebuildNav(cat: string): void {
    if (cat !== this.category()) return; // onglet déjà rechangé entre-temps → le run suivant gère
    const host = this.detailRef()?.nativeElement;
    if (!host) {
      this.nav.set([]);
      return;
    }
    const secs = Array.from(host.querySelectorAll<HTMLElement>(':scope > section.card'));
    const items = secs.map((el, i) => {
      const id = `sc-${i}`;
      el.dataset['scid'] = id;
      // Libellé = titre (h2) nettoyé : token (--c-*/--app-*/--macro-*…) + compteur « (N) » retirés → juste l'idée.
      const raw = (el.querySelector('h2')?.textContent ?? `Composant ${i + 1}`).trim();
      const label = raw.replace(/--[\w-]+\*/g, '').replace(/\s*\(\d+\)\s*$/, '').replace(/\s+/g, ' ').trim();
      return { id, label };
    });
    items.sort((a, b) => a.label.localeCompare(b.label));
    // T6 : la colonne de droite (Common Components) suit le même ordre alpha que la liste de gauche.
    // On réordonne VISUELLEMENT via la propriété flex `order` ; l'ordre DOM reste inchangé (le test
    // foundations qui lit querySelectorAll dans l'ordre source reste vert).
    if (cat === 'components') {
      const byId = new Map(secs.map((el) => [el.dataset['scid'], el] as const));
      items.forEach((it, rank) => {
        const sec = byId.get(it.id);
        if (sec) sec.style.order = String(rank);
      });
    }
    const first = items[0]?.id ?? '';
    this.nav.set(items);
    this.sel.set(first);
    this.markSelected(first);
  }

  /**
   * Sélectionne un composant et fait défiler jusqu'à sa section. On interroge le DOM vivant et on
   * défile la FENÊTRE (le vrai scroller — l'outlet du shell n'a pas d'overflow) : défilement fiable
   * quel que soit l'état du rendu (scrollIntoView échouait silencieusement dans ce layout).
   */
  protected selectComp(id: string): void {
    this.sel.set(id);
    this.markSelected(id);
    const el = this.detailRef()?.nativeElement.querySelector<HTMLElement>(`[data-scid="${id}"]`);
    if (!el) return;
    const top = el.getBoundingClientRect().top + window.scrollY - 16;
    window.scrollTo({ top, behavior: 'smooth' });
  }

  /** Applique la bordure bleue à la card sélectionnée (les autres la perdent). */
  private markSelected(id: string): void {
    this.detailRef()
      ?.nativeElement.querySelectorAll<HTMLElement>(':scope > section.card')
      .forEach((el) => el.classList.toggle('sc-active', el.dataset['scid'] === id));
  }

  /** Clic dans la colonne de droite → sélectionne la card dans la liste de gauche (+ bordure + scroll de la liste). */
  protected onDetailClick(event: Event): void {
    const id = (event.target as HTMLElement).closest<HTMLElement>('section[data-scid]')?.dataset['scid'];
    if (!id) return;
    this.sel.set(id);
    this.markSelected(id);
    this.scrollNavTo(id);
  }

  /** Fait défiler la liste de gauche (scroll interne) pour rendre l'item visible (centré), sans bouger la fenêtre. */
  private scrollNavTo(id: string): void {
    const nav = this.navListRef()?.nativeElement;
    const item = nav?.querySelector<HTMLElement>(`[data-navid="${id}"]`);
    if (!nav || !item) return;
    const navR = nav.getBoundingClientRect();
    const itemR = item.getBoundingClientRect();
    if (itemR.top >= navR.top && itemR.bottom <= navR.bottom) return; // déjà visible → pas de saut
    const top = nav.scrollTop + (itemR.top - navR.top) - nav.clientHeight / 2 + item.clientHeight / 2;
    nav.scrollTo({ top, behavior: 'smooth' });
  }
  protected readonly name = signal('');
  protected readonly radio = signal('b');
  protected readonly lastAction = signal('');
  // EntityListRow style « catalogue » : favori toggle + sélection (flèche) de l'exemple trailing.
  protected readonly scFav = signal(true);
  protected readonly scSelected = signal(false);
  // Registre d'icônes groupé par type (cf. page Figma « Icons » : 6 catégories). Ligatures Material
  // Symbols fiables (certaines icônes Figma récentes/custom absentes de la police web sont substituées).
  protected readonly iconGroups: { title: string; icons: string[] }[] = [
    {
      title: 'Navigation & flèches',
      icons: ['home', 'menu', 'grid_view', 'keyboard_arrow_left', 'keyboard_arrow_right', 'keyboard_arrow_up', 'keyboard_arrow_down', 'north', 'south', 'north_east', 'arrow_back', 'arrow_forward', 'arrow_upward', 'arrow_downward', 'double_arrow', 'subdirectory_arrow_right', 'arrow_circle_right', 'fast_forward', 'calendar_view_day', 'chevron_left', 'chevron_right'],
    },
    {
      title: 'États & feedback',
      icons: ['check', 'check_circle', 'cancel', 'close', 'error', 'warning', 'info', 'help', 'question_mark', 'check_indeterminate_small', 'not_started', 'flag', 'schedule', 'check_box', 'check_box_outline_blank', 'hourglass_empty', 'pending', 'priority_high', 'do_not_disturb_on'],
    },
    {
      title: 'Sync & connectivité',
      icons: ['cloud', 'cloud_done', 'cloud_off', 'cloud_upload', 'cloud_download', 'cloud_sync', 'sync', 'sync_problem', 'wifi', 'wifi_off', 'signal_wifi_off', 'router', 'link', 'link_off', 'add_link', 'refresh', 'signal_cellular_alt', 'network_check'],
    },
    {
      title: 'Actions & édition',
      icons: ['add', 'add_box', 'add_circle', 'edit', 'delete', 'delete_forever', 'delete_sweep', 'content_copy', 'share', 'sort', 'sort_by_alpha', 'more_vert', 'more_horiz', 'repeat', 'replay', 'restart_alt', 'build', 'save', 'search'],
    },
    {
      title: 'Média & temps',
      icons: ['play_arrow', 'play_circle', 'pause', 'pause_circle', 'stop_circle', 'timer', 'av_timer', 'more_time', 'fast_rewind'],
    },
    {
      title: 'Contenu & domaine',
      icons: ['fitness_center', 'exercise', 'local_fire_department', 'bedtime', 'book', 'neurology', 'monitoring', 'query_stats', 'pie_chart', 'equalizer', 'bar_chart', 'show_chart', 'layers', 'notes', 'list_alt', 'format_list_numbered', 'calendar_month', 'calendar_today', 'star', 'star_border', 'account_circle', 'language', 'logout', 'settings', 'chat', 'mail', 'notifications', 'palette', 'trending_up', 'trending_down'],
    },
  ];

  protected readonly summaryItems: SummaryItemData[] = [
    { icon: 'exercise', value: '12', label: 'Séances', iconTint: 'var(--app-primary-action)' },
    { icon: 'local_fire_department', value: '8.4k', label: 'Volume', iconTint: 'var(--app-snackbar-warning)' },
  ];
  protected readonly summaryItemsCompact: SummaryItemData[] = [
    { icon: 'exercise', value: '12', label: 'Séances', iconTint: 'var(--app-primary-action)' },
    { icon: 'local_fire_department', value: '8.4k', label: 'Volume', iconTint: 'var(--app-snackbar-warning)' },
    { icon: 'timer', value: '5h', label: 'Durée', iconTint: 'var(--app-snackbar-success)' },
  ];

  // — Lot 4 (selects & search) état démo —
  protected readonly search = signal('');
  protected readonly lshSearch = signal('');
  protected readonly selectVal = signal('Option A');
  protected readonly selectOptions = ['Option A', 'Option B', 'Option C'];
  protected readonly singleVal = signal('Tuesday');
  protected readonly dayOptions = ['Monday', 'Tuesday', 'Wednesday', 'Sunday'];
  protected readonly multiVal = signal<string[]>(['Chest', 'Back']);
  protected readonly muscleOptions = ['Chest', 'Back', 'Legs', 'Shoulders', 'Arms'];
  protected readonly filterVal = signal<string | null>('Tous');
  protected readonly filterOptions = ['Tous', 'Favoris', 'Récents'];
  // --- Démo FilterPanel : déclencheur (page) + cadre repliable (TOUS macros + micros, comme le catalogue) ---
  protected readonly fpOpen = signal(false);
  protected readonly fpMicrosOpen = signal(false);
  protected readonly fpRealm = signal<string | null>('Tous');
  protected readonly fpGroup = signal<string | null>('Tous');
  /** Mêmes 5 macros + 10 micros (abréviation + unité) que le catalogue, en données de démo locales. */
  protected readonly fpMacros = [
    { key: 'kcal', abbr: 'kcal', unit: 'kcal', step: 10 },
    { key: 'carbs', abbr: 'Gluc.', unit: 'g', step: 1 },
    { key: 'fat', abbr: 'Lip.', unit: 'g', step: 1 },
    { key: 'protein', abbr: 'Prot.', unit: 'g', step: 1 },
    { key: 'fiber', abbr: 'Fib.', unit: 'g', step: 1 },
  ];
  protected readonly fpMicros = [
    { key: 'iron', abbr: 'Fe', unit: 'mg' },
    { key: 'calcium', abbr: 'Ca', unit: 'mg' },
    { key: 'magnesium', abbr: 'Mg', unit: 'mg' },
    { key: 'zinc', abbr: 'Zn', unit: 'mg' },
    { key: 'potassium', abbr: 'K', unit: 'mg' },
    { key: 'sodium', abbr: 'Na', unit: 'mg' },
    { key: 'vitC', abbr: 'Vit C', unit: 'mg' },
    { key: 'vitD', abbr: 'Vit D', unit: 'µg' },
    { key: 'vitB12', abbr: 'Vit B12', unit: 'µg' },
    { key: 'vitA', abbr: 'Vit A', unit: 'µg' },
  ];
  /** Seuils saisis (op + valeur) par clé de nutriment — même schéma que `thresholdInputs` du catalogue. */
  protected readonly fpInputs = signal<Record<string, { op: FilterThresholdOp; value: string }>>({});
  protected fpOpFor(key: string): FilterThresholdOp {
    return this.fpInputs()[key]?.op ?? 'gte';
  }
  protected fpRawFor(key: string): string {
    return this.fpInputs()[key]?.value ?? '';
  }
  protected fpSetOp(key: string, op: FilterThresholdOp): void {
    this.fpInputs.update((m) => ({ ...m, [key]: { op, value: m[key]?.value ?? '' } }));
  }
  protected fpSetRaw(key: string, value: string): void {
    this.fpInputs.update((m) => ({ ...m, [key]: { op: m[key]?.op ?? 'gte', value } }));
  }
  protected readonly fpActiveCount = computed(
    () =>
      (this.fpRealm() !== 'Tous' ? 1 : 0) +
      (this.fpGroup() !== 'Tous' ? 1 : 0) +
      Object.values(this.fpInputs()).filter((t) => t.value.trim()).length,
  );
  protected readonly fpButtonLabel = computed(() =>
    this.fpActiveCount() > 0 ? `Filtres (${this.fpActiveCount()})` : 'Filtres',
  );
  protected fpReset(): void {
    this.fpRealm.set('Tous');
    this.fpGroup.set('Tous');
    this.fpInputs.set({});
  }
  // --- Démo FilterThresholdRow (standalone) ---
  protected readonly ftrOp = signal<FilterThresholdOp>('gte');
  protected readonly ftrVal = signal('');
  /** Démo CustomHourPicker (heure HH:MM). */
  protected readonly hourVal = signal('08:00');
  /** Démo DateRangePickerDialog. */
  protected readonly drpOpen = signal(false);
  protected readonly drpRange = signal<{ start: string; end: string } | null>(null);
  /** Démo CollapsibleSection. */
  protected readonly csOpen = signal(false);
  protected readonly colSort = signal<SortDir>('NONE');
  protected readonly colFilter = signal('');

  // — Lot 5 (tabs & segments) état démo —
  protected readonly tabItems = ['Aperçu', 'Détails', 'Historique'];
  protected readonly tabIndex = signal(0);
  protected readonly dtmTopTabs = ['Semaine', 'Mois'];
  protected readonly dtmSubMap: Record<string, string[]> = {
    Semaine: ['Lun', 'Mar', 'Mer'],
    Mois: ['Jan', 'Fév', 'Mar'],
  };
  protected readonly dtmTop = signal(0);
  protected readonly dtmSub = signal(0);
  // 3 variantes du M10 Figma (Segments=2 / 3 / 5).
  protected readonly seg2Items: SegmentItem[] = [
    { value: 'equalizer', icon: 'equalizer', description: 'Barres' },
    { value: 'monitoring', icon: 'monitoring', description: 'Courbe' },
  ];
  protected readonly seg2 = signal('equalizer');
  protected readonly seg3Items: SegmentItem[] = [
    { value: 'reps', icon: 'repeat', description: 'Répétitions' },
    { value: 'order', icon: 'format_list_numbered', description: 'Ordre' },
    { value: 'exercise', icon: 'exercise', description: 'Exercice' },
  ];
  protected readonly seg3 = signal('reps');
  protected readonly seg5Items: SegmentItem[] = [
    { value: 'alpha', icon: 'sort_by_alpha', description: 'Alphabétique' },
    { value: 'palette', icon: 'palette', description: 'Par zone' },
    { value: 'pct_desc', icon: 'trending_down', description: '% décroissant' },
    { value: 'pct_asc', icon: 'trending_up', description: '% croissant' },
    { value: 'priority', icon: 'priority_high', description: 'Priorité' },
  ];
  protected readonly seg5 = signal('alpha');
  protected readonly diActive = signal('cal');
  protected readonly dsActive = signal('home');
  protected readonly bnbItems: BottomNavItemData[] = [
    { value: 'menu', icon: 'menu', label: 'Menu' },
    { value: 'calendar', icon: 'calendar_month', label: 'Calendrier' },
    { value: 'home', icon: 'home', label: 'Accueil' },
    { value: 'chrono', icon: 'timer', label: 'Chrono' },
    { value: 'stats', icon: 'monitoring', label: 'Stats' },
  ];
  protected readonly bnbVal = signal('home');

  protected onDtmTop(i: number): void {
    this.dtmTop.set(i);
    this.dtmSub.set(0);
  }

  // — DataTable : données de démo (illustrent les types de cellules) —
  protected readonly tableCols = ['uuid', 'name', 'reps', 'done', 'updatedAt', 'note'];
  protected readonly tableRows: Record<string, unknown>[] = [
    { uuid: 'a1b2c3d4-e5f6', name: 'Bench Press', reps: 10, done: true, updatedAt: '2026-06-20T14:32:00Z', note: 'PR' },
    { uuid: 'f6e5d4c3-b2a1', name: 'Back Squat', reps: 8, done: false, updatedAt: '2026-06-21T09:05:00Z', note: null },
    { uuid: '0a1b2c3d-4e5f', name: 'Deadlift', reps: 5, done: true, updatedAt: '2026-06-22T18:40:00Z', note: null },
  ];

  // — Lot 6 (pickers) état démo —
  protected readonly wheelVal = signal(8);
  protected readonly hnpVal = signal(8);
  protected readonly hmsH = signal(1);
  protected readonly hmsM = signal(30);
  protected readonly hmsS = signal(0);
  protected readonly trStart = signal(480);
  protected readonly trEnd = signal(600);

  protected onTimeRange(e: { start: number; end: number }): void {
    this.trStart.set(e.start);
    this.trEnd.set(e.end);
  }

  // — Lot 7 (dialogs & sheets) état démo —
  protected readonly showConfirm = signal(false);
  protected readonly showForm = signal(false);
  protected readonly showDate = signal(false);
  protected readonly showSheet = signal(false);
  protected readonly showOptions = signal(false);
  protected readonly dateResult = signal('');
  protected readonly dfDate = signal('');
  protected readonly dfPickerOpen = signal(false);
  protected readonly sheetActions: SheetAction[] = [
    { label: 'Modifier', icon: 'edit', color: 'var(--app-bg-button)' },
    { label: 'Dupliquer', icon: 'content_copy', color: 'var(--app-bg-button)' },
    { label: 'Supprimer', icon: 'delete', color: 'var(--c-red-dark)' },
  ];

  // — Lot 8 (charts & data) état démo —
  protected readonly chartLabels = ['S1', 'S2', 'S3', 'S4', 'S5', 'S6', 'S7', 'S8'];
  protected readonly chartSeries: LineSeries[] = [
    // Magnitudes comparables (un seul axe Y) -> les 3 lignes restent visibles et ondulées.
    { name: 'Volume', data: [42, 48, 45, 52, 50, 56, 54, 60], color: 'var(--app-primary-action)' },
    { name: 'Charge', data: [30, 36, 33, 41, 38, 45, 43, 49], color: 'var(--app-snackbar-warning)' },
    { name: 'Séries', data: [20, 26, 23, 31, 28, 35, 32, 39], color: 'var(--app-snackbar-success)' },
  ];
  protected readonly pageIndex = signal(0);
  protected readonly pageSizeVal = signal(50);

  // — RadarChart : ex. 1 macros nutrition (axes = macros, % objectif) —
  protected readonly macroAxes: RadarAxis[] = [
    { label: 'Protéines', max: 120 },
    { label: 'Glucides', max: 120 },
    { label: 'Lipides', max: 120 },
    { label: 'Fibres', max: 120 },
  ];
  protected readonly macroRadarSeries: RadarSeries[] = [
    // Couleur via token nutrition imbriqué (var(--macro-kcal) -> var(--c-button-primary)), area on.
    { name: 'Consommé', values: [95, 110, 70, 60], color: 'var(--macro-kcal)', area: true },
    { name: 'Objectif', values: [100, 100, 100, 100], color: 'var(--app-text-secondary)' },
  ];
  // — RadarChart : ex. 2 multi-série (équilibre musculaire par zone, sport) —
  protected readonly balanceAxes: RadarAxis[] = [
    { label: 'Pecs' },
    { label: 'Dos' },
    { label: 'Jambes' },
    { label: 'Épaules' },
    { label: 'Bras' },
    { label: 'Core' },
  ];
  protected readonly balanceRadarSeries: RadarSeries[] = [
    { name: 'Cette semaine', values: [82, 65, 90, 50, 70, 40], color: 'var(--app-primary-action)', area: true },
    { name: 'Semaine -1', values: [60, 70, 75, 45, 55, 60], color: 'var(--app-snackbar-warning)' },
    { name: 'Semaine -2', values: [40, 50, 55, 30, 45, 50], color: 'var(--app-snackbar-success)' },
  ];

  // — NutritionSummaryPanel : état démo (catalogue T5 + recettes T7) —
  protected readonly nspDisplayItems: SegmentItem[] = [
    { value: 'line', icon: 'notes', description: 'Ligne' },
    { value: 'bar', icon: 'bar_chart', description: 'Barres' },
    { value: 'radar', icon: 'radar', description: 'Radar' },
  ];
  protected readonly nspDisplay = signal<SummaryDisplay>('bar');
  protected setNspDisplay(v: string): void {
    this.nspDisplay.set(v as SummaryDisplay);
  }
  // Aliment per-100 g (sans cible).
  protected readonly demoFoodKcal = 389;
  protected readonly demoFoodMacros: MacroAmounts = { protein: 13, carbs: 66, fat: 7, fiber: 10 };
  protected readonly demoFoodMicros: MicroNutrients = {
    ironPer100g: 4.7, calciumPer100g: 52, magnesiumPer100g: 138, zincPer100g: 3.1,
    potassiumPer100g: 350, sodiumPer100g: 7, vitaminCPer100g: null, vitaminDPer100g: null,
    vitaminB12Per100g: null, vitaminAPer100g: null,
  };
  // Cumul d'un jour + cibles (avancement vs objectif).
  protected readonly demoDayKcal = 1850;
  protected readonly demoDayMacros: MacroAmounts = { protein: 120, carbs: 190, fat: 62, fiber: 24 };
  protected readonly demoDayMicros: MicroNutrients = {
    ironPer100g: 11, calciumPer100g: 620, magnesiumPer100g: 280, zincPer100g: 8,
    potassiumPer100g: 2400, sodiumPer100g: 1600, vitaminCPer100g: 65, vitaminDPer100g: 3,
    vitaminB12Per100g: 2, vitaminAPer100g: 540,
  };
  protected readonly demoTargets: MacroTargets = { kcal: 2515, protein: 180, carbs: 250, fat: 80, fiber: 38 };
  // Aliment salé (sodium au-dessus du plafond → alerte).
  protected readonly demoSaltyMicros: MicroNutrients = {
    ...this.demoFoodMicros, sodiumPer100g: 2400,
  };
  // Ligne aliment/ingrédient (MacroEntryRow) : macros + micros (Avoine 60 g), et une variante sans micros.
  protected readonly demoEntryRow: MacroEntryRowData = {
    name: 'Avoine, crue',
    kcal: this.demoFoodKcal * 0.6,
    carbs: this.demoFoodMacros.carbs * 0.6,
    fat: this.demoFoodMacros.fat * 0.6,
    protein: this.demoFoodMacros.protein * 0.6,
    fiber: (this.demoFoodMacros.fiber ?? 0) * 0.6,
    micros: microLineItems(this.demoFoodMicros, 0.6),
  };
  protected readonly demoEntryRowNoMicros: MacroEntryRowData = {
    name: 'Blanc de poulet', kcal: 198, carbs: 0, fat: 4.3, protein: 37, fiber: null, micros: [],
  };
  // Anneaux concentriques (ConcentricRingsChart) — démo via l'adaptateur macro (profil du jour vs cible).
  protected readonly concentricDemoRings = macroRingViews(this.demoDayKcal, this.demoDayMacros, this.demoTargets);
  protected readonly concentricKcalColor = MACRO_COLOR.kcal;
  // Donut « répartition des kcal par macro » (DonutChart) — kcal Atwater du profil du jour (4/9/4/2).
  protected readonly donutDemoSlices: DonutSlice[] = [
    { label: 'Glucides', value: 760, color: MACRO_COLOR.carbs },
    { label: 'Lipides', value: 558, color: MACRO_COLOR.fat },
    { label: 'Protéines', value: 480, color: MACRO_COLOR.protein },
    { label: 'Fibres', value: 48, color: MACRO_COLOR.fiber },
  ];

  // — Lot 9 (organisms) état démo —
  private readonly _now = new Date();
  protected readonly calYear = this._now.getFullYear();
  protected readonly calMonth = this._now.getMonth();
  protected readonly calOffset = (new Date(this._now.getFullYear(), this._now.getMonth(), 1).getDay() + 6) % 7;
  protected readonly calToday = `${this._now.getFullYear()}-${this.pad2(this._now.getMonth() + 1)}-${this.pad2(this._now.getDate())}`;
  protected readonly demoSnacks: SnackbarEvent[] = [
    { id: 's1', message: 'Séance enregistrée', type: 'SUCCESS' },
    { id: 's2', message: 'Synchronisation en attente', type: 'WARNING' },
    { id: 's3', message: 'Échec de connexion', type: 'ERROR', actionLabel: 'Réessayer', secondaryActionLabel: 'Ignorer' },
    { id: 's4', message: 'Nouvelle version disponible', type: 'INFO', actionLabel: 'Voir' },
  ];

  protected pad2(n: number): string {
    return n.toString().padStart(2, '0');
  }
  protected calDayNum(iso: string): string {
    return String(Number(iso.slice(8, 10)));
  }
  // Statuts démo cyclés par jour (= mapping CalendarDay.kt : done/in-progress/skipped/missed).
  private readonly calStatuses = [
    { icon: 'check', color: 'var(--c-medium-green)' },
    { icon: 'arrow_circle_up', color: 'var(--c-orange-medium)' },
    { icon: 'close', color: 'var(--c-red-medium)' },
    { icon: 'check_indeterminate_small', color: 'var(--c-dark-orange)' },
  ];
  protected calStatus(iso: string): { icon: string; color: string } {
    return this.calStatuses[Number(iso.slice(8, 10)) % this.calStatuses.length];
  }

  // — Lot 10 (composants feature-spécifiques) état démo —
  protected readonly demoSet: SetRowData = { setOrder: 1, reps: 10, weight: 80, status: 'DONE', isDropset: false, pendingDeletion: false, hasNote: true };
  protected readonly demoSetDrop: SetRowData = { setOrder: 2, reps: 6, weight: 60, status: 'IN_PROGRESS', isDropset: true, pendingDeletion: false, hasNote: false };
  protected readonly demoSetSkipped: SetRowData = { setOrder: 3, reps: 14, weight: 70, status: 'SKIPPED', isDropset: false, pendingDeletion: false, hasNote: false };
  protected readonly showPhase = signal(false);
  protected readonly showStatus = signal(false);
  protected readonly statusVal = signal('IN_PROGRESS');
  protected readonly statusOptions: StatusOption[] = [
    { value: 'NOT_STARTED', label: 'Non démarré', icon: 'help', color: 'var(--app-text-tertiary)' },
    { value: 'IN_PROGRESS', label: 'En cours', icon: 'pending', color: 'var(--app-snackbar-warning)' },
    { value: 'DONE', label: 'Terminé', icon: 'check_circle', color: 'var(--app-snackbar-success)' },
    { value: 'SKIPPED', label: 'Sauté', icon: 'cancel', color: 'var(--app-snackbar-error)' },
  ];
  protected readonly showExPicker = signal(false);
  protected readonly exPickerItems: ExercisePickerItem[] = [
    { uuid: 'e1', name: 'Développé couché', equipments: ['Barre', 'Banc'] },
    { uuid: 'e2', name: 'Squat', equipments: ['Barre'] },
    { uuid: 'e3', name: 'Curl haltères', equipments: ['Haltères'] },
    { uuid: 'e4', name: 'Tractions', equipments: ['Barre de traction'] },
  ];
  protected readonly exEquipments = ['Tous', 'Barre', 'Banc', 'Haltères', 'Barre de traction'];

  protected bump(action: string): void {
    this.lastAction.set(action);
  }
  // Toutes les familles de tokens couleur de _colors.scss, groupées (réf. visuelle complète, façon
  // page Foundations Figma Android). Pastille = var(--token) résolu selon le thème actif ; nom = token.
  protected readonly swatchGroups: SwatchGroup[] = [
    // 1. Primitives (--c-*) — palette brute, sous-groupée par teinte.
    {
      title: 'Primitives --c-* · Bleus',
      swatches: [
        { name: 'first-blue', varName: '--c-first-blue' },
        { name: 'second-blue', varName: '--c-second-blue' },
        { name: 'third-blue', varName: '--c-third-blue' },
        { name: 'box-blue', varName: '--c-box-blue' },
        { name: 'blue-medium', varName: '--c-blue-medium' },
        { name: 'button-primary', varName: '--c-button-primary' },
        { name: 'light-blue', varName: '--c-light-blue' },
        { name: 'blue-background', varName: '--c-blue-background' },
        { name: 'ui-showcase-card', varName: '--c-ui-showcase-card' },
      ],
    },
    {
      title: 'Primitives --c-* · Gris-bleus & gris',
      swatches: [
        { name: 'gray-blue', varName: '--c-gray-blue' },
        { name: 'light-gray-blue', varName: '--c-light-gray-blue' },
        { name: 'dark-gray', varName: '--c-dark-gray' },
      ],
    },
    {
      title: 'Primitives --c-* · Verts',
      swatches: [
        { name: 'light-green', varName: '--c-light-green' },
        { name: 'medium-green', varName: '--c-medium-green' },
      ],
    },
    {
      title: 'Primitives --c-* · Jaunes & oranges',
      swatches: [
        { name: 'yellow-medium', varName: '--c-yellow-medium' },
        { name: 'orange-medium', varName: '--c-orange-medium' },
        { name: 'dark-orange', varName: '--c-dark-orange' },
      ],
    },
    {
      title: 'Primitives --c-* · Rouges',
      swatches: [
        { name: 'red-medium', varName: '--c-red-medium' },
        { name: 'red-dark', varName: '--c-red-dark' },
      ],
    },
    {
      title: 'Primitives --c-* · Violets',
      swatches: [
        { name: 'light-purple', varName: '--c-light-purple' },
        { name: 'medium-purple', varName: '--c-medium-purple' },
      ],
    },
    {
      title: 'Primitives --c-* · Turquoise',
      swatches: [{ name: 'turquoise', varName: '--c-turquoise' }],
    },
    // 2. Sémantiques (--app-*) — rôle → couleur, résolus selon le thème courant (conservées + complétées).
    {
      title: 'Sémantiques --app-*',
      swatches: [
        { name: 'bg-screen', varName: '--app-bg-screen' },
        { name: 'bg-surface', varName: '--app-bg-surface' },
        { name: 'bg-recessed', varName: '--app-bg-recessed' },
        { name: 'bg-bottom-nav', varName: '--app-bg-bottom-nav' },
        { name: 'bg-button', varName: '--app-bg-button' },
        { name: 'selected-fill', varName: '--app-selected-fill' },
        { name: 'primary-action', varName: '--app-primary-action' },
        { name: 'text-primary', varName: '--app-text-primary' },
        { name: 'text-secondary', varName: '--app-text-secondary' },
        { name: 'text-tertiary', varName: '--app-text-tertiary' },
        { name: 'text-on-selected', varName: '--app-text-on-selected' },
        { name: 'accent-text', varName: '--app-accent-text' },
        { name: 'divider', varName: '--app-divider' },
        { name: 'divider-strong', varName: '--app-divider-strong' },
        { name: 'priority-high', varName: '--app-priority-high' },
        { name: 'priority-medium', varName: '--app-priority-medium' },
        { name: 'priority-low', varName: '--app-priority-low' },
        { name: 'task-row-green-bg', varName: '--app-task-row-green-bg' },
        { name: 'task-row-green-name-box', varName: '--app-task-row-green-name-box' },
        { name: 'task-row-orange-bg', varName: '--app-task-row-orange-bg' },
        { name: 'task-row-orange-name-box', varName: '--app-task-row-orange-name-box' },
        { name: 'snackbar-success', varName: '--app-snackbar-success' },
        { name: 'snackbar-warning', varName: '--app-snackbar-warning' },
        { name: 'snackbar-error', varName: '--app-snackbar-error' },
      ],
    },
    // 3. Macros nutrition (--macro-*).
    {
      title: 'Macros --macro-*',
      swatches: [
        { name: 'kcal', varName: '--macro-kcal' },
        { name: 'protein', varName: '--macro-protein' },
        { name: 'carbs', varName: '--macro-carbs' },
        { name: 'fat', varName: '--macro-fat' },
        { name: 'fiber', varName: '--macro-fiber' },
        { name: 'sugar', varName: '--macro-sugar', label: 'Sucres (plafond OMS)' },
      ],
    },
    // 4. Micros nutrition (--micro-*).
    {
      title: 'Micros --micro-*',
      swatches: [
        { name: 'mineral', varName: '--micro-mineral' },
        { name: 'vitamin', varName: '--micro-vitamin' },
      ],
    },
    // 5. Origines / règnes d'aliment (--food-*).
    {
      title: 'Origines aliment --food-*',
      swatches: [
        { name: 'animal', varName: '--food-animal' },
        { name: 'vegetal', varName: '--food-vegetal' },
        { name: 'supplement', varName: '--food-supplement' },
        { name: 'other', varName: '--food-other' },
      ],
    },
    // 6. Groupes d'aliment (--food-grp-*) — 17 teintes, avec libellé FR (cf. food-category.ts).
    {
      title: "Groupes d'aliment --food-grp-*",
      swatches: [
        { name: 'viande-rouge', varName: '--food-grp-viande-rouge', label: 'Viande rouge' },
        { name: 'viande-blanche', varName: '--food-grp-viande-blanche', label: 'Viande blanche' },
        { name: 'poisson', varName: '--food-grp-poisson', label: 'Poisson' },
        { name: 'fruits-de-mer', varName: '--food-grp-fruits-de-mer', label: 'Fruits de mer' },
        { name: 'oeuf', varName: '--food-grp-oeuf', label: 'Œufs' },
        { name: 'laitage', varName: '--food-grp-laitage', label: 'Laitages' },
        { name: 'legumineuse', varName: '--food-grp-legumineuse', label: 'Légumineuses' },
        { name: 'legume', varName: '--food-grp-legume', label: 'Légumes' },
        { name: 'fruit', varName: '--food-grp-fruit', label: 'Fruits' },
        { name: 'cereale-feculent', varName: '--food-grp-cereale-feculent', label: 'Céréales & féculents' },
        { name: 'noix-graine', varName: '--food-grp-noix-graine', label: 'Noix & graines' },
        { name: 'matiere-grasse', varName: '--food-grp-matiere-grasse', label: 'Matières grasses' },
        { name: 'produit-sucre', varName: '--food-grp-produit-sucre', label: 'Produits sucrés' },
        { name: 'boisson', varName: '--food-grp-boisson', label: 'Boissons' },
        { name: 'plat-compose', varName: '--food-grp-plat-compose', label: 'Plats composés' },
        { name: 'complement', varName: '--food-grp-complement', label: 'Compléments' },
        { name: 'autre', varName: '--food-grp-autre', label: 'Autre' },
      ],
    },
    // 7. Boutons d'action à fond sémantique + icône sur accent.
    {
      title: 'Boutons --app-btn-* / --app-on-accent',
      swatches: [
        { name: 'btn-danger-bg', varName: '--app-btn-danger-bg' },
        { name: 'btn-danger-fg', varName: '--app-btn-danger-fg' },
        { name: 'on-accent', varName: '--app-on-accent' },
      ],
    },
  ];
}
