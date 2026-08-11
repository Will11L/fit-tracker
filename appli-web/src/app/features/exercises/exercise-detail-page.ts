import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalEquipment } from '@core/models/equipment.model';
import { LocalExerciseEquipment } from '@core/models/exercise-equipment.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomSelect } from '@designsystem/common_components/custom-select';
import { MultiSelectDropdown } from '@designsystem/common_components/multi-select-dropdown';
import { HorizontalNumberPicker } from '@designsystem/common_components/horizontal-number-picker';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { CustomDatePickerDialog } from '@designsystem/common_components/custom-date-picker-dialog';
import { AppIcon } from '@designsystem/icons/app-icon';
import { SettingsStore } from '../settings/settings-store';
import { StatsSectionChart, type StatsSeries } from '../stats/stats-section-chart';
import { resolveCssColor } from '../stats/palette-util';
import { ExerciseRepository } from './exercise.repository';

type RangeKey = 'W1' | 'D30' | 'M3' | 'M6' | 'Y1' | 'ALL' | 'CUSTOM';
type MetricKey = 'Weight' | 'Sets' | 'Volume';

const RANGE_CHIPS: { key: RangeKey; label: string }[] = [
  { key: 'W1', label: '1 semaine' },
  { key: 'D30', label: '30 jours' },
  { key: 'M3', label: '3 mois' },
  { key: 'M6', label: '6 mois' },
  { key: 'Y1', label: '1 an' },
  { key: 'ALL', label: 'Tout' },
  // Icône seule (miroir stats-page) : le libellé rendait la ligne de chips trop longue.
  { key: 'CUSTOM', label: '' },
];

const KG_TO_LBS = 2.2046226218;
const REST_OPTIONS_SECONDS = [30, 45, 60, 90, 120, 180];

/** Stat agrégée d'un jour pour l'exercice (miroir ExerciseDailyStatsRow Android). */
interface DailyStat {
  dayIso: string;
  maxWeight: number;
  setCount: number;
  volume: number;
}

/** Ligne « Dernières séances » (miroir ActualWorkoutExerciseWithWorkoutDateAndSets). */
interface LastSessionVm {
  workoutUuid: string;
  date: string; // YYYY-MM-DD
  setsCount: number;
  totalReps: number;
}

/**
 * Écran détail Exercice — miroir flat de ExerciseScreen.kt (Android) :
 * - Actions : retour, favori (orange si favori), sync, ⋮ (modifier / supprimer via bottom sheet).
 * - 3 cards détail (bgRecessed, crayon en haut à droite) : Séries/Reps/Repos · Équipement+Description ·
 *   Instructions — chacune avec son dialog d'édition (miroir des 3 dialogs Android).
 * - Stats : chips de période (1 semaine → Tout + Personnalisé) + chart Progression multi-courbes
 *   (Poids max vert · Séries bleu · Volume orange) branché sur les séries réelles (sémantique exacte
 *   de observeExerciseDailyStats : pendingDeletion exclu à chaque jointure, group by jour) + légende
 *   cliquable filtrante (miroir StatsSection/StatFilterButton).
 * - Dernières séances : 3 plus récentes (date · séries · reps totales · lien vers la séance).
 * Mode `embedded` (colonne détail du master-detail /exercises) : pas de title bar (TitledDivider du nom
 * à la place), pas de bouton retour (la sélection vit dans la liste de gauche), suppression sans navigation.
 * Différé vs Android : la page anatomie Delavier Method (le bouton est présent → snackbar « Bientôt disponible »).
 */
@Component({
  selector: 'app-exercise-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    ActionIconButton,
    ConfirmationDialog,
    FormDialog,
    CustomTextField,
    CustomSelect,
    MultiSelectDropdown,
    HorizontalNumberPicker,
    OptionsBottomSheet,
    CustomDatePickerDialog,
    StatsSectionChart,
    AppIcon,
  ],
  template: `
    <section class="page">
      @if (!embedded()) {
        <app-screen-title-bar [title]="exercise()?.name ?? 'Exercice'" />
      }

      @if (!exercise()) {
        <div class="page__missing">Exercice introuvable.</div>
      } @else {
        <div class="page__body">
          <!-- Cadre UNIQUE : nom (embedded) + barre d'actions + 3 sections détail, séparés par un filet. -->
          <div class="maincard">
          @if (embedded()) {
            <app-titled-divider [title]="exercise()!.name" />
          }

          <!-- Barre d'actions (miroir ExerciseActionBar : back · favori · sync · Delavier · ⋮).
               En mode embedded, pas de bouton retour (la liste de gauche tient la sélection). -->
          <div class="actions">
            <app-action-icon-button
              [icon]="exercise()!.isFavorite ? 'star' : 'star_border'"
              [backgroundColor]="
                exercise()!.isFavorite
                  ? 'var(--c-orange-medium)'
                  : 'color-mix(in srgb, var(--app-text-tertiary) 70%, transparent)'
              "
              (clicked)="toggleFav()"
            />
            <app-action-icon-button
              [icon]="exercise()!.synced ? 'cloud_done' : 'cloud_off'"
              [hasBackground]="false"
              [tint]="exercise()!.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
              (clicked)="refresh()"
            />
            <!-- Delavier Method (≈ ExerciseActionBar.kt : book sur fond selectedFill) — pas encore porté. -->
            <app-action-icon-button icon="book" backgroundColor="var(--app-selected-fill)" (clicked)="delavierSoon()" />
            <app-action-icon-button icon="more_vert" (clicked)="showOptions.set(true)" />
          </div>

          <div class="sep"></div>

          <!-- Cards 1 + 2 côte à côte (gain de hauteur, demande user), filet vertical entre les deux. -->
          <div class="cards-row">
          <!-- Card 1 — Séries / Reps / Repos (miroir ExerciseScreenDetails section 1). -->
          <div class="card">
            <app-action-icon-button
              class="card__edit"
              icon="edit"
              (clicked)="openEditStats()"
            />
            <p class="card__line"><span class="card__label">Séries :</span> {{ exercise()!.recommendedSets ?? 'N/A' }}</p>
            <p class="card__line"><span class="card__label">Reps :</span> {{ exercise()!.recommendedReps || 'N/A' }}</p>
            <p class="card__line"><span class="card__label">Repos :</span> {{ restLabel(exercise()!.restTimeSeconds) }}</p>
          </div>

          <div class="sep sep--v"></div>

          <!-- Card 2 — Équipement + Description (miroir section 2). -->
          <div class="card">
            <app-action-icon-button
              class="card__edit"
              icon="edit"
              (clicked)="openEditInfo()"
            />
            <p class="card__line">
              <span class="card__label">Équipement :</span>
              {{ equipmentNames().length ? equipmentNames().join(', ') : 'Aucun équipement renseigné' }}
            </p>
            <p class="card__line">
              <span class="card__label">Description :</span>
              {{ exercise()!.description || 'Aucune description disponible' }}
            </p>
          </div>
          </div>

          <div class="sep"></div>

          <!-- Card 3 — Instructions (miroir section 3). -->
          <div class="card">
            <app-action-icon-button
              class="card__edit"
              icon="edit"
              (clicked)="openEditInstructions()"
            />
            @if (steps().length === 0) {
              <p class="card__line card__line--muted">Aucune instruction — ajoute des étapes avec le crayon.</p>
            } @else {
              <p class="card__instructions-title">Instructions</p>
              @for (step of steps(); track $index) {
                <p class="card__line"><span class="card__label">• Étape {{ $index + 1 }} :</span> {{ step }}</p>
              }
            }
          </div>
          </div>

          <!-- Cadre Stats : titre + chips de période + chart + légende métriques. -->
          <div class="maincard">
          <app-titled-divider title="Stats" />

          <!-- Chips de période (miroir RangeChipsRow, état local à la page). -->
          <div class="chips">
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

          <app-stats-section-chart
            [buckets]="chartBuckets()"
            [series]="chartSeries()"
            chartType="LINE"
            granularity="DAILY"
            metric="SETS"
            [height]="240"
          />

          <!-- Légende cliquable = filtre de visibilité des métriques (miroir StatFilterButton). -->
          <div class="chips">
            @for (m of metricDefs(); track m.key) {
              <button
                class="chip"
                [class.chip--sel]="visibleMetrics().has(m.key)"
                [style.border-color]="m.color"
                [style.color]="visibleMetrics().has(m.key) ? 'var(--app-text-primary)' : m.color"
                [style.background]="visibleMetrics().has(m.key) ? m.color : 'transparent'"
                (click)="toggleMetric(m.key)"
              >
                {{ m.label }}
              </button>
            }
          </div>
          </div>

          <!-- Cadre Dernières séances : titre + table, lignes séparées par un filet. -->
          <div class="maincard">
          <app-titled-divider title="Dernières séances" />

          <!-- Table 3 dernières séances (miroir LastSessionTableHeader/Row). -->
          <div class="sessions">
            <div class="sessions__header">
              <span class="sessions__cell sessions__cell--date">Date</span>
              <span class="sessions__cell sessions__cell--sets">Séries</span>
              <span class="sessions__cell sessions__cell--total">Total</span>
              <span class="sessions__cell sessions__cell--link"></span>
            </div>
            @if (lastSessions().length === 0) {
              <!-- Empty state au style des placeholders / empty-state chart : bgRecessed + cadre bleu. -->
              <div class="sessions__empty"><div class="sessions__empty-frame">Aucune séance récente</div></div>
            } @else {
              @for (s of lastSessions(); track s.workoutUuid) {
                <div class="sessions__row">
                  <span class="sessions__cell sessions__cell--date">{{ fmtDay(s.date) }}</span>
                  <span class="sessions__cell sessions__cell--sets">{{ s.setsCount }}</span>
                  <span class="sessions__cell sessions__cell--total">{{ s.totalReps }} reps</span>
                  <span class="sessions__cell sessions__cell--link">
                    <!-- Même style que la flèche des rows de la liste : fond blueMedium, icône claire. -->
                    <app-action-icon-button
                      icon="arrow_right_alt"
                      tint="var(--app-text-primary)"
                      backgroundColor="var(--c-blue-medium)"
                      (clicked)="goSession(s.workoutUuid)"
                    />
                  </span>
                </div>
              }
            }
          </div>
          </div>
        </div>
      }

      <!-- ⋮ options (miroir ExerciseMoreOptionsBottomSheet, sans Delavier). -->
      <app-options-bottom-sheet
        [open]="showOptions()"
        [title]="exercise()?.name ?? ''"
        [actions]="sheetActions"
        (actionSelected)="onSheetAction($event)"
        (dismissRequest)="showOptions.set(false)"
      />

      <app-confirmation-dialog
        [open]="showDelete()"
        title="Supprimer l'exercice ?"
        [message]="'Supprimer « ' + (exercise()?.name ?? '') + ' » ? Cette action est irréversible.'"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDelete()"
        (dismiss)="showDelete.set(false)"
      />

      <!-- Dialog 1 — séries / reps / repos (miroir EditSetsRepsRestDialog). -->
      <app-form-dialog
        [open]="showEditStats()"
        title="Modifier séries, reps et repos"
        confirmText="Enregistrer"
        (confirm)="confirmEditStats()"
        (dismiss)="showEditStats.set(false)"
      >
        <div class="form-reps">
          <app-horizontal-number-picker label="Reps min" [min]="1" [max]="100" [selected]="draftRepsMin()" (selectedChange)="draftRepsMin.set($event)" />
          <app-horizontal-number-picker label="Reps max" [min]="1" [max]="100" [selected]="draftRepsMax()" (selectedChange)="draftRepsMax.set($event)" />
        </div>
        <app-horizontal-number-picker label="Séries" [min]="1" [max]="10" [selected]="draftSets()" (selectedChange)="draftSets.set($event)" />
        <app-custom-select
          label="Temps de repos"
          [options]="restOptions"
          [selected]="draftRestLabel()"
          (select)="draftRestLabel.set($event)"
        />
      </app-form-dialog>

      <!-- Dialog 2 — description + équipement (miroir EditDescriptionEquipmentDialog). -->
      <app-form-dialog
        [open]="showEditInfo()"
        title="Modifier description et équipement"
        confirmText="Enregistrer"
        (confirm)="confirmEditInfo()"
        (dismiss)="showEditInfo.set(false)"
      >
        <app-custom-text-field
          label="Description"
          placeholder="Décris l'exercice…"
          [multiline]="true"
          [value]="draftDescription()"
          (valueChange)="draftDescription.set($event)"
        />
        <app-multi-select-dropdown
          label="Équipement"
          [options]="allEquipmentNames()"
          [selectedItems]="draftEquipmentNames()"
          (selectionChange)="draftEquipmentNames.set($event)"
        />
      </app-form-dialog>

      <!-- Dialog 3 — instructions (miroir EditInstructionsDialog : étapes + ajout/suppression). -->
      <app-form-dialog
        [open]="showEditInstructions()"
        title="Modifier les instructions"
        confirmText="Enregistrer"
        (confirm)="confirmEditInstructions()"
        (dismiss)="showEditInstructions.set(false)"
      >
        @for (step of draftSteps(); track $index) {
          <div class="form-step">
            <app-custom-text-field
              class="form-step__field"
              [label]="'Étape ' + ($index + 1)"
              placeholder="Décris l'étape…"
              [value]="step"
              (valueChange)="setDraftStep($index, $event)"
            />
            <app-action-icon-button
              icon="close"
              backgroundColor="var(--app-btn-danger-bg)"
              tint="var(--app-btn-danger-fg)"
              (clicked)="removeDraftStep($index)"
            />
          </div>
        }
        <app-action-icon-button icon="add" backgroundColor="var(--c-blue-medium)" (clicked)="addDraftStep()" />
      </app-form-dialog>

      <!-- Période personnalisée : 2 pickers séquentiels (miroir CustomRangePickerDialog). -->
      <app-custom-date-picker-dialog
        [open]="pickerStage() === 'start'"
        title="Sélectionner une période — Début"
        [initialIso]="customStart()"
        (confirm)="confirmCustomStart($event)"
        (dismiss)="pickerStage.set(null)"
      />
      <app-custom-date-picker-dialog
        [open]="pickerStage() === 'end'"
        title="Sélectionner une période — Fin"
        [initialIso]="customEnd()"
        (confirm)="confirmCustomEnd($event)"
        (dismiss)="pickerStage.set(null)"
      />
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; corps avec gouttière (--page-gutter). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        max-width: 760px;
      }
      .page__missing {
        padding: var(--space-5);
        text-align: center;
        color: var(--c-red-medium);
      }
      .actions {
        display: flex;
        /* Répartition avec de l'espace aussi aux extrémités de la ligne (demande user). */
        justify-content: space-evenly;
        align-items: center;
      }
      /* Cadres de la page (padding canonique 16px) : ① nom + actions + 3 sections détail (séparées
         par un filet secondBlue .sep) · ② Stats (titre + chips + chart + légende) · ③ Dernières
         séances (titre + table). */
      .maincard {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 16px;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .sep {
        height: 1px;
        background: var(--c-second-blue);
      }
      /* Cards Séries/Reps/Repos + Équipement/Description côte à côte, filet vertical entre les deux. */
      .cards-row {
        display: flex;
        gap: var(--space-3);
      }
      .cards-row > .card {
        flex: 1;
        min-width: 0;
      }
      .sep--v {
        width: 1px;
        height: auto;
        align-self: stretch;
      }
      /* Sections détail — miroir ExerciseScreenDetails (crayon en haut à droite), à plat dans le cadre. */
      .card {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .card__edit {
        position: absolute;
        top: var(--space-1);
        right: var(--space-1);
      }
      .card__line {
        margin: 0;
        font-size: 13px;
        line-height: 20px;
        color: var(--app-text-primary);
        padding-right: 36px; /* évite le crayon */
      }
      .card__line--muted {
        color: var(--app-text-tertiary);
      }
      .card__label {
        color: var(--app-primary-action);
      }
      .card__instructions-title {
        margin: 0 0 var(--space-2);
        text-align: center;
        font-size: 14px;
        color: var(--app-text-tertiary);
      }
      /* Chips période + légende métriques (mêmes pills que la page Stats). */
      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
        align-items: center;
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
      /* Table dernières séances — lignes à plat dans le cadre, séparées par un filet secondBlue. */
      .sessions {
        display: flex;
        flex-direction: column;
      }
      .sessions__header,
      .sessions__row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        height: 40px;
      }
      .sessions > *:not(:last-child) {
        border-bottom: 1px solid var(--c-second-blue);
      }
      .sessions__cell {
        text-align: center;
        font-size: 14px;
      }
      .sessions__header .sessions__cell {
        color: var(--app-text-secondary);
      }
      .sessions__cell--date { flex: 2.5; }
      .sessions__cell--sets { flex: 2; }
      .sessions__cell--total { flex: 2.5; }
      .sessions__cell--link {
        flex: 1;
        display: flex;
        justify-content: center;
      }
      /* Empty state — mêmes codes que l'empty state des charts (container bgRecessed + cadre primaryAction). */
      .sessions__empty {
        height: 72px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3);
        box-sizing: border-box;
      }
      .sessions__empty-frame {
        height: 100%;
        box-sizing: border-box;
        display: flex;
        align-items: center;
        justify-content: center;
        text-align: center;
        border: 1.5px solid var(--app-primary-action);
        border-radius: var(--radius-md);
        color: var(--app-primary-action);
        font-size: var(--font-size-body);
        padding: var(--space-2) var(--space-3);
      }
      .form-reps {
        display: flex;
        gap: var(--space-4);
      }
      .form-reps > * {
        flex: 1;
        min-width: 0;
      }
      .form-step {
        display: flex;
        align-items: flex-end;
        gap: var(--space-2);
      }
      .form-step__field {
        flex: 1;
        min-width: 0;
      }
    `,
  ],
})
export class ExerciseDetailPage {
  /** UUID de l'exercice (passé par la page combinée /exercises). */
  readonly uuid = input.required<string>();
  /** Mode embarqué (colonne détail du master-detail) : pas de title bar ni de bouton retour. */
  readonly embedded = input(false);

  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly repo = inject(ExerciseRepository);
  private readonly settings = inject(SettingsStore);
  private readonly router = inject(Router);
  private readonly snackbar = inject(SnackbarService);

  protected readonly rangeChips = RANGE_CHIPS;
  protected readonly restOptions = REST_OPTIONS_SECONDS.map((s) => this.restLabel(s));
  protected readonly sheetActions: SheetAction[] = [
    { label: 'Modifier les infos', icon: 'edit', color: 'var(--c-medium-green)' },
    { label: 'Supprimer', icon: 'delete_sweep', color: 'var(--c-red-medium)' },
  ];

  // ─── Données Dexie ───
  private readonly exercises = this.repo.exercises;
  private readonly equipments = toSignal(from(liveQuery(() => this.db.equipments.toArray())), {
    initialValue: [] as LocalEquipment[],
  });
  private readonly exerciseEquipment = toSignal(from(liveQuery(() => this.db.exercise_equipment.toArray())), {
    initialValue: [] as LocalExerciseEquipment[],
  });
  private readonly workouts = toSignal(from(liveQuery(() => this.db.actual_workouts.toArray())), {
    initialValue: [] as LocalActualWorkout[],
  });
  private readonly awExercises = toSignal(from(liveQuery(() => this.db.actual_workout_exercises.toArray())), {
    initialValue: [] as LocalActualWorkoutExercise[],
  });
  private readonly sets = toSignal(from(liveQuery(() => this.db.actual_workout_sets.toArray())), {
    initialValue: [] as LocalActualWorkoutSet[],
  });

  protected readonly exercise = computed(
    () => this.exercises().find((e) => e.uuid === this.uuid() && !e.pendingDeletion) ?? null,
  );

  protected readonly allEquipmentNames = computed(() =>
    this.equipments()
      .filter((eq) => !eq.pendingDeletion)
      .map((eq) => eq.name)
      .sort((a, b) => a.localeCompare(b)),
  );

  protected readonly equipmentNames = computed(() => {
    const byUuid = new Map(this.equipments().map((eq) => [eq.uuid, eq]));
    return this.exerciseEquipment()
      .filter((ee) => ee.exerciseUUID === this.uuid() && !ee.pendingDeletion)
      .map((ee) => byUuid.get(ee.equipmentUUID)?.name)
      .filter((n): n is string => !!n)
      .sort((a, b) => a.localeCompare(b));
  });

  protected readonly steps = computed(
    () => (this.exercise()?.instructions ?? []).filter((s) => s.trim().length > 0),
  );

  // ─── État UI ───
  protected readonly showOptions = signal(false);
  protected readonly showDelete = signal(false);
  protected readonly showEditStats = signal(false);
  protected readonly showEditInfo = signal(false);
  protected readonly showEditInstructions = signal(false);

  protected readonly draftRepsMin = signal(8);
  protected readonly draftRepsMax = signal(12);
  protected readonly draftSets = signal(3);
  protected readonly draftRestLabel = signal('N/A');
  protected readonly draftDescription = signal('');
  protected readonly draftEquipmentNames = signal<string[]>([]);
  protected readonly draftSteps = signal<string[]>(['']);

  // ─── Stats : période + agrégation quotidienne (miroir observeExerciseDailyStats) ───
  protected readonly rangeKind = signal<RangeKey>('W1');
  protected readonly customStart = signal<string>(this.todayIso());
  protected readonly customEnd = signal<string>(this.todayIso());
  protected readonly pickerStage = signal<'start' | 'end' | null>(null);
  protected readonly visibleMetrics = signal<Set<MetricKey>>(new Set(['Weight', 'Sets', 'Volume']));

  private readonly bounds = computed(() => {
    const today = new Date();
    let start: Date;
    let end = today;
    switch (this.rangeKind()) {
      case 'W1': start = this.addDays(today, -7); break;
      case 'D30': start = this.addDays(today, -30); break;
      case 'M3': start = this.minusMonths(today, 3); break;
      case 'M6': start = this.minusMonths(today, 6); break;
      case 'Y1': start = this.minusMonths(today, 12); break;
      case 'ALL': start = new Date(2000, 0, 1); break;
      case 'CUSTOM':
        start = this.parseIso(this.customStart());
        end = this.parseIso(this.customEnd());
        break;
    }
    return { startIso: this.toIso(start), endIso: this.toIso(end) };
  });

  /** Sets de l'exercice joints à leur séance (pendingDeletion exclu à chaque table, comme le DAO). */
  private readonly dailyStats = computed<DailyStat[]>(() => {
    const { startIso, endIso } = this.bounds();
    const aweOfExercise = new Map(
      this.awExercises()
        .filter((e) => !e.pendingDeletion && e.exerciseUUID === this.uuid())
        .map((e) => [e.uuid, e]),
    );
    const awMap = new Map(this.workouts().filter((w) => !w.pendingDeletion).map((w) => [w.uuid, w]));
    const byDay = new Map<string, DailyStat>();
    for (const s of this.sets()) {
      if (s.pendingDeletion) continue;
      const awe = aweOfExercise.get(s.actualWorkoutExerciseUUID);
      if (!awe) continue;
      const aw = awMap.get(awe.actualWorkoutUUID);
      if (!aw || !aw.date) continue;
      const day = aw.date.slice(0, 10);
      if (day < startIso || day > endIso) continue;
      const row = byDay.get(day) ?? { dayIso: day, maxWeight: 0, setCount: 0, volume: 0 };
      row.maxWeight = Math.max(row.maxWeight, s.weight);
      row.setCount += 1;
      row.volume += s.weight * s.reps;
      byDay.set(day, row);
    }
    return [...byDay.values()].sort((a, b) => a.dayIso.localeCompare(b.dayIso));
  });

  protected readonly chartBuckets = computed(() => this.dailyStats().map((d) => d.dayIso));

  /** Mêmes couleurs qu'Android : Poids max vert, Volume orange, Séries bleu (primaryAction). */
  protected readonly metricDefs = computed<{ key: MetricKey; label: string; color: string }[]>(() => {
    const unit = this.settings.settings().weightUnit === 'LBS' ? 'lbs' : 'kg';
    return [
      { key: 'Weight', label: `Poids max (${unit})`, color: resolveCssColor('var(--c-medium-green)') },
      { key: 'Sets', label: 'Séries', color: resolveCssColor('var(--app-primary-action)') },
      { key: 'Volume', label: `Volume (${unit})`, color: resolveCssColor('var(--c-orange-medium)') },
    ];
  });

  protected readonly chartSeries = computed<StatsSeries[]>(() => {
    const stats = this.dailyStats();
    if (stats.length === 0) return [];
    const lbs = this.settings.settings().weightUnit === 'LBS';
    const conv = (v: number): number => Math.round((lbs ? v * KG_TO_LBS : v) * 10) / 10;
    const value: Record<MetricKey, (d: DailyStat) => number> = {
      Weight: (d) => conv(d.maxWeight),
      Sets: (d) => d.setCount,
      Volume: (d) => conv(d.volume),
    };
    return this.metricDefs()
      .filter((m) => this.visibleMetrics().has(m.key))
      .map((m) => ({ name: m.label, data: stats.map(value[m.key]), color: m.color }));
  });

  /** 3 séances les plus récentes contenant l'exercice (miroir observeLast3SessionsForExercise). */
  protected readonly lastSessions = computed<LastSessionVm[]>(() => {
    const awMap = new Map(this.workouts().filter((w) => !w.pendingDeletion).map((w) => [w.uuid, w]));
    const setsByAwe = new Map<string, LocalActualWorkoutSet[]>();
    for (const s of this.sets()) {
      if (s.pendingDeletion) continue;
      const arr = setsByAwe.get(s.actualWorkoutExerciseUUID);
      if (arr) arr.push(s);
      else setsByAwe.set(s.actualWorkoutExerciseUUID, [s]);
    }
    const rows: LastSessionVm[] = [];
    for (const awe of this.awExercises()) {
      if (awe.pendingDeletion || awe.exerciseUUID !== this.uuid()) continue;
      const aw = awMap.get(awe.actualWorkoutUUID);
      if (!aw || !aw.date) continue;
      const aweSets = setsByAwe.get(awe.uuid) ?? [];
      rows.push({
        workoutUuid: aw.uuid,
        date: aw.date.slice(0, 10),
        setsCount: aweSets.length,
        totalReps: aweSets.reduce((acc, s) => acc + s.reps, 0),
      });
    }
    return rows.sort((a, b) => b.date.localeCompare(a.date)).slice(0, 3);
  });

  constructor() {
    this.refresh();
  }

  // ─── Actions ───
  private readonly location = inject(Location);

  /** Retour programmatique (ex. après suppression). Plus de bouton « retour » UI (web desktop). */
  private back(): void {
    this.location.back();
  }

  protected refresh(): void {
    void this.sync.syncAll().catch(() => undefined);
  }

  protected toggleFav(): void {
    const e = this.exercise();
    if (e) void this.repo.update(e.uuid, { isFavorite: !e.isFavorite });
  }

  /** Bouton Delavier Method : écran anatomie Android pas encore porté sur le web. */
  protected delavierSoon(): void {
    this.snackbar.info("Bientôt disponible — la page anatomie (Delavier Method) n'est pas encore portée sur le web.");
  }

  protected onSheetAction(label: string): void {
    this.showOptions.set(false);
    if (label === 'Modifier les infos') this.openEditInfo();
    else if (label === 'Supprimer') this.showDelete.set(true);
  }

  protected confirmDelete(): void {
    const e = this.exercise();
    this.showDelete.set(false);
    if (!e) return;
    void this.repo.remove(e.uuid);
    // Embedded : la row disparaît de la liste → le parent repasse en état vide (pas de navigation).
    if (!this.embedded()) this.back();
  }

  protected goSession(workoutUuid: string): void {
    void this.router.navigate(['/session', workoutUuid]);
  }

  // ─── Dialog 1 : séries / reps / repos ───
  protected openEditStats(): void {
    const e = this.exercise();
    if (!e) return;
    const [min, max] = this.parseReps(e.recommendedReps);
    this.draftRepsMin.set(min);
    this.draftRepsMax.set(max);
    this.draftSets.set(e.recommendedSets ?? 3);
    this.draftRestLabel.set(this.restLabel(e.restTimeSeconds));
    this.showEditStats.set(true);
  }

  protected confirmEditStats(): void {
    const e = this.exercise();
    this.showEditStats.set(false);
    if (!e) return;
    const min = Math.min(this.draftRepsMin(), this.draftRepsMax());
    const max = Math.max(this.draftRepsMin(), this.draftRepsMax());
    const idx = this.restOptions.indexOf(this.draftRestLabel());
    void this.repo.update(e.uuid, {
      recommendedReps: `${min}-${max}`,
      recommendedSets: this.draftSets(),
      // Repos inchangé si le label courant ne vient pas de la liste (ex. valeur "N/A").
      ...(idx >= 0 ? { restTimeSeconds: REST_OPTIONS_SECONDS[idx] } : {}),
    });
  }

  // ─── Dialog 2 : description + équipement ───
  protected openEditInfo(): void {
    const e = this.exercise();
    if (!e) return;
    this.draftDescription.set(e.description ?? '');
    this.draftEquipmentNames.set(this.equipmentNames());
    this.showEditInfo.set(true);
  }

  protected confirmEditInfo(): void {
    const e = this.exercise();
    this.showEditInfo.set(false);
    if (!e) return;
    const desc = this.draftDescription().trim();
    void this.repo.update(e.uuid, { description: desc.length ? desc : null });
    const nameToUuid = new Map(this.equipments().filter((eq) => !eq.pendingDeletion).map((eq) => [eq.name, eq.uuid]));
    const uuids = this.draftEquipmentNames()
      .map((n) => nameToUuid.get(n))
      .filter((u): u is string => !!u);
    void this.repo.setEquipments(e.uuid, uuids);
  }

  // ─── Dialog 3 : instructions ───
  protected openEditInstructions(): void {
    const e = this.exercise();
    if (!e) return;
    const existing = (e.instructions ?? []).filter((s) => s.trim().length > 0);
    this.draftSteps.set(existing.length ? existing : ['']);
    this.showEditInstructions.set(true);
  }

  protected setDraftStep(index: number, value: string): void {
    this.draftSteps.update((steps) => steps.map((s, i) => (i === index ? value : s)));
  }

  protected addDraftStep(): void {
    this.draftSteps.update((steps) => [...steps, '']);
  }

  protected removeDraftStep(index: number): void {
    this.draftSteps.update((steps) => steps.filter((_, i) => i !== index));
  }

  protected confirmEditInstructions(): void {
    const e = this.exercise();
    this.showEditInstructions.set(false);
    if (!e) return;
    const steps = this.draftSteps().map((s) => s.trim()).filter((s) => s.length > 0);
    void this.repo.update(e.uuid, { instructions: steps.length ? steps : null });
  }

  // ─── Période ───
  protected selectRange(key: RangeKey): void {
    if (key === 'CUSTOM') {
      this.pickerStage.set('start');
      return;
    }
    this.rangeKind.set(key);
  }

  protected confirmCustomStart(iso: string): void {
    this.customStart.set(iso);
    this.pickerStage.set('end');
  }

  protected confirmCustomEnd(iso: string): void {
    if (iso < this.customStart()) {
      this.customEnd.set(this.customStart());
      this.customStart.set(iso);
    } else {
      this.customEnd.set(iso);
    }
    this.rangeKind.set('CUSTOM');
    this.pickerStage.set(null);
  }

  protected toggleMetric(key: MetricKey): void {
    const next = new Set(this.visibleMetrics());
    if (next.has(key)) next.delete(key);
    else next.add(key);
    this.visibleMetrics.set(next);
  }

  // ─── Helpers ───
  /** Format repos (miroir formatRestTime Android) : "1m 30s" / "45s" / "N/A". */
  protected restLabel(seconds: number | null | undefined): string {
    if (seconds == null || seconds <= 0) return 'N/A';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (m > 0 && s > 0) return `${m}m ${s}s`;
    if (m > 0) return `${m}m`;
    return `${s}s`;
  }

  /** Date des dernières séances au format dd/MM/yyyy (ex. 11/06/2026). */
  protected fmtDay(iso: string): string {
    const [y, m, d] = iso.split('-');
    return y && m && d ? `${d}/${m}/${y}` : iso;
  }

  private parseReps(reps: string | null | undefined): [number, number] {
    if (reps) {
      const m = reps.match(/(\d+)\s*-\s*(\d+)/);
      if (m) return [Number(m[1]), Number(m[2])];
      const n = Number(reps);
      if (!isNaN(n)) return [n, n];
    }
    return [8, 12];
  }

  private addDays(d: Date, n: number): Date {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  }

  /** Soustraction de mois avec clamp fin de mois (sémantique LocalDate.minusMonths). */
  private minusMonths(d: Date, n: number): Date {
    const y = d.getFullYear();
    const m = d.getMonth() - n;
    const lastDay = new Date(y, m + 1, 0).getDate();
    return new Date(y, m, Math.min(d.getDate(), lastDay));
  }

  private parseIso(iso: string): Date {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  private toIso(d: Date): string {
    const pad = (n: number): string => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  private todayIso(): string {
    return this.toIso(new Date());
  }
}
