import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalExercise } from '@core/models/exercise.model';
import { LocalPlannedWorkout } from '@core/models/planned-workout.model';
import { LocalPlannedWorkoutExercise } from '@core/models/planned-workout-exercise.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { FramedSection } from '@designsystem/common_components/framed-section';
import { ListRow } from '@designsystem/common_components/list-row';
import { LabeledProgressBar, progressColor } from '@designsystem/common_components/labeled-progress-bar';
import { ProgressBarPrimitive } from '@designsystem/common_components/progress-bar-primitive';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { EntityListRow } from '@designsystem/common_components/entity-list-row';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { SummaryRow, type SummaryItemData } from '@designsystem/common_components/summary-row';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomSelect } from '@designsystem/common_components/custom-select';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { ExercisePickerBottomSheet, type ExercisePickerItem } from '@designsystem/common_components/exercise-picker-bottom-sheet';
import { PhasePickerDialog } from '@designsystem/common_components/phase-picker-dialog';
import { StatusPickerDialog, type StatusOption } from '@designsystem/common_components/status-picker-dialog';
import { AppIcon } from '@designsystem/icons/app-icon';
import { RevealIn } from '@designsystem/common_components/reveal-in';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const FR_LABEL: Record<string, string> = {
  Monday: 'Lundi',
  Tuesday: 'Mardi',
  Wednesday: 'Mercredi',
  Thursday: 'Jeudi',
  Friday: 'Vendredi',
  Saturday: 'Samedi',
  Sunday: 'Dimanche',
};
const REST_LABEL = 'Jour de repos';
const REST_NAME_ERROR = 'Le nom ne peut pas être « Rest Day ».';

/** Lundi (ISO) de la semaine contenant `d`, au format YYYY-MM-DD local. */
function isoOf(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function weekBounds(): { start: string; end: string } {
  const now = new Date();
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7));
  const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
  return { start: isoOf(monday), end: isoOf(sunday) };
}
/** Nom EN canonique (Monday…) du jour d'une date YYYY-MM-DD. */
function dayOfWeekFromDate(dateIso: string): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  return DAYS[(new Date(y, m - 1, d).getDay() + 6) % 7];
}
function isFillerName(name: string): boolean {
  return name.trim().toLowerCase() === 'rest day';
}

/** Résultat de progression d'un jour planifié — miroir de calcDayProgressResult (WeekViewScreen.kt). */
interface DayProgressResult {
  progress: number;
  hasActual: boolean;
  completed: boolean;
  isBuilding: boolean;
}

interface ExoRowVm {
  pwe: LocalPlannedWorkoutExercise;
  name: string;
  statusIcon: string;
  statusTint: string;
}
interface PhaseVm {
  label: string;
  empty: string;
  rows: ExoRowVm[];
}

interface DayVm {
  day: string;
  label: string;
  isToday: boolean;
  isFiller: boolean;
  workout: LocalPlannedWorkout | null;
  /** Nom affiché sous le pill (nom user-typed, ou « Jour de repos » pour le filler). */
  name: string;
  synced: boolean;
  result: DayProgressResult;
  statusIcon: string;
  statusTint: string;
  showBar: boolean;
  percent: number;
  barColor: string;
  labelColor: string;
}

/**
 * Écran Planning — miroir de WeekViewScreen.kt + PlannedWorkoutScreen.kt en master-detail
 * (même pattern 2 colonnes que session-page) : « Avancement de la semaine » (LabeledProgressBar
 * + bouton ⋮ → WeekCompletionBottomSheet : sync / tout marquer terminé / non terminé) puis
 * split — **gauche** : 7 rows-jour Lundi→Dimanche (bgRecessed, bordure bleue réservée au jour
 * courant) avec pill du jour (bgSurface), icône de statut (construction = en construction,
 * ✓ = terminée, ↑ = séance réelle en cours, ▶ = planifiée non démarrée), nuage de sync,
 * PlannedDayProgressBar (nom + barre + %), bouton ⋮ (renommer / marquer terminée / dupliquer /
 * supprimer ; « Planifier une séance » sur un jour de repos) et **flèche → = seul déclencheur de
 * sélection** (fond primaryAction quand le jour est sélectionné) ; **droite** : détail du jour
 * sélectionné (PlannedWorkoutScreen.kt) — TitledDivider(nom), ligne PlannedWorkoutProgressBar
 * (barre + % + nuage sync avec confirmation + bouton + → ExercisePicker puis PhasePicker, comme
 * Android), total séries/reps (SummaryRow), puis exercices par phase Échauffement / Entraînement /
 * Récupération (sets × reps, statuts dont ignoré, nuage sync). Responsive : colonnes empilées
 * sous 900px (= session-page). Données offline-first (Dexie liveQuery, semaine courante),
 * CRUD local optimiste + sync best-effort.
 *
 * Clic sur le nom d'un exercice du détail (= PlannedExerciseOptionsBottomSheet.kt) : sheet
 * 3 actions — « Voir les détails de l'exercice » (→ /exercises avec le nom en recherche, le web
 * n'ayant pas d'écran détail dédié), « Changer le statut » (StatusPickerDialog DONE/PLANNED/
 * NOT_STARTED/SKIPPED), « Retirer de la séance planifiée » (confirmation → pendingDeletion).
 *
 * Différé vs Android : supersets ; cycles d'entraînement.
 */
@Component({
  selector: 'app-planning-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    FramedSection,
    ListRow,
    LabeledProgressBar,
    ProgressBarPrimitive,
    ActionIconButton,
    EntityListRow,
    EmptyListRow,
    SummaryRow,
    FormDialog,
    CustomTextField,
    CustomSelect,
    ConfirmationDialog,
    OptionsBottomSheet,
    ExercisePickerBottomSheet,
    PhasePickerDialog,
    StatusPickerDialog,
    AppIcon,
    RevealIn,
  ],
  template: `
    <section class="page">
      @if (!embedded()) { <app-screen-title-bar title="Programme" /> }

      <div class="page__body">
        <!-- Master-detail (même pattern que session-page) : jours (gauche) + détail du jour (droite). -->
        <div class="split">
          <div class="split__list">
            <!-- Cadre unique (flush) : avancement de la semaine EN HAUT + jours. Flush → les
                 app-list-row vont bord à bord (liseré du jour courant au bord) ; le bandeau semaine
                 et le sous-titre "Jours" gardent un retrait horizontal. -->
            <app-framed-section title="Avancement de la semaine" [flush]="true">
              <div class="week-progress">
                <app-labeled-progress-bar
                  class="week-progress__bar"
                  [progress]="weekProgress()"
                  troughColor="var(--app-bg-surface)"
                />
                <app-action-icon-button
                  icon="more_vert"
                  backgroundColor="var(--app-bg-surface)"
                  (clicked)="showWeekSheet.set(true)"
                />
              </div>
              <div class="joursdiv"><app-titled-divider title="Jours" /></div>
            @for (d of days(); track d.day) {
              <app-list-row class="dayrow" [selected]="d.isToday" [clickable]="false">
                <div class="day__left">
                  <div class="day__head">
                    <span class="day__pill">{{ d.label }}</span>
                    @if (!d.isFiller) {
                      <app-icon [name]="d.statusIcon" [size]="24" [color]="d.statusTint" />
                      <app-action-icon-button
                        [icon]="d.synced ? 'cloud_done' : 'cloud_off'"
                        [tint]="d.synced ? 'var(--c-blue-medium)' : 'var(--c-yellow-medium)'"
                        [hasBackground]="false"
                        [disabled]="d.synced"
                        (clicked)="syncPlanned()"
                      />
                    } @else {
                      <span class="day__rest">
                        <app-icon name="bedtime" [size]="24" color="var(--c-blue-medium)" />
                        Zzz…
                      </span>
                    }
                  </div>

                  <!-- PlannedDayProgressBar : nom + barre + % -->
                  <div class="dayprog">
                    <span class="dayprog__label" [style.color]="d.labelColor">{{ d.name }}</span>
                    @if (d.showBar) {
                      <app-progress-bar-primitive
                        class="dayprog__bar"
                        [progress]="d.result.progress"
                        [color]="d.barColor"
                        troughColor="var(--app-bg-surface)"
                      />
                      <span class="dayprog__pct" [style.color]="d.barColor">{{ d.percent }}%</span>
                    }
                  </div>
                </div>

                <div class="day__actions">
                  <app-action-icon-button icon="more_vert" (clicked)="openDayOptions(d)" />
                  @if (!d.isFiller) {
                    <!-- Seule la flèche sélectionne le jour (détail à droite) ; fond primaryAction si sélectionné. -->
                    <app-action-icon-button
                      icon="arrow_right_alt"
                      tint="var(--app-text-primary)"
                      [backgroundColor]="d.day === effectiveDay() ? 'var(--app-primary-action)' : 'var(--c-blue-medium)'"
                      (clicked)="selectedDay.set(d.day)"
                    />
                  }
                </div>
              </app-list-row>
            }
            </app-framed-section>
          </div>

          <!-- Droite : détail du jour sélectionné (≈ PlannedWorkoutScreen Android).
               Entre en slide-down + fade ; re-animée (fondu seul) au changement de jour. -->
          <div class="split__detail" [appRevealIn]="selectedDay()">
            @if (selectedDayVm(); as d) {
              @if (!d.isFiller && d.workout) {
                <div class="detail">
                  <!-- Cadre thirdBlue : nom de la séance en en-tête + barre (trough boxBlue) + actions
                       + tuiles totaux (secondBlue), comme l'en-tête exo de la page séance. -->
                  <app-framed-section [title]="d.name">
                    <div class="detail__progress">
                      <app-labeled-progress-bar
                        class="detail__bar"
                        [progress]="d.result.progress"
                        troughColor="var(--app-bg-surface)"
                      />
                      <app-action-icon-button
                        [icon]="d.synced ? 'cloud_done' : 'cloud_off'"
                        [hasBackground]="false"
                        [tint]="d.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                        (clicked)="showDetailSyncConfirm.set(true)"
                      />
                      <app-action-icon-button icon="add" (clicked)="showAddSheet.set(true)" />
                    </div>
                    <app-summary-row class="detail__summary" [items]="detailSummary()" />
                  </app-framed-section>
                  @for (phase of detailPhases(); track phase.label) {
                    <app-framed-section [title]="phase.label">
                    @if (phase.rows.length === 0) {
                      <app-empty-list-row [text]="phase.empty" icon="fitness_center" [verticalPadding]="0" />
                    } @else {
                      @for (row of phase.rows; track row.pwe.uuid) {
                        <app-entity-list-row
                          [name]="row.name"
                          [nameMaxLines]="1"
                          [nameWeight]="2.6"
                          backgroundColor="var(--c-second-blue)"
                          nameBoxColor="var(--app-bg-surface)"
                          [isPendingDeletion]="row.pwe.pendingDeletion"
                          [verticalPadding]="4"
                          [contentEndPadding]="12"
                          (nameClick)="exoForOptions.set(row)"
                        >
                          <span trailing class="exo-trailing">
                            <span class="exo-trailing__icons">
                              <span class="exo-trailing__icon-box">
                                <app-icon
                                  [name]="row.pwe.synced ? 'cloud_done' : 'cloud_off'"
                                  [size]="20"
                                  [color]="row.pwe.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                                />
                              </span>
                              <span class="exo-trailing__icon-box">
                                <app-icon [name]="row.statusIcon" [size]="20" [color]="row.statusTint" />
                              </span>
                            </span>
                            <span class="exo-trailing__sr">{{ row.pwe.sets }} × {{ row.pwe.reps }}</span>
                          </span>
                        </app-entity-list-row>
                      }
                    }
                    </app-framed-section>
                  }
                </div>
              } @else {
                <app-empty-list-row text="Jour de repos — aucune séance planifiée ce jour." icon="bedtime" />
              }
            } @else {
              <app-empty-list-row text="Sélectionne un jour à gauche." icon="touch_app" />
            }
          </div>
        </div>
      </div>

      <!-- WeekCompletionBottomSheet : actions globales de la semaine -->
      <app-options-bottom-sheet
        [open]="showWeekSheet()"
        title="Options d'avancement de la semaine"
        [actions]="weekActions"
        (dismissRequest)="showWeekSheet.set(false)"
        (actionSelected)="onWeekAction($event)"
      />

      <!-- WeekSessionOptionsBottomSheet : actions d'un jour -->
      <app-options-bottom-sheet
        [open]="dayForOptions() !== null"
        [title]="dayForOptions()?.name ?? ''"
        [actions]="dayActions()"
        (dismissRequest)="dayForOptions.set(null)"
        (actionSelected)="onDayAction($event)"
      />

      <!-- Dialog renommage (miroir AlertDialog rename) -->
      <app-form-dialog
        [open]="showRenameDialog()"
        title="Renommer la séance"
        confirmText="Renommer"
        [confirmEnabled]="renameName().trim().length > 0"
        disabledReason="Nom requis"
        (confirm)="confirmRename()"
        (dismiss)="showRenameDialog.set(false)"
      >
        <app-custom-text-field placeholder="Nouveau nom de séance" [(value)]="renameName" />
      </app-form-dialog>

      <!-- Dialog création (CreatePlannedWorkoutDialog) -->
      <app-form-dialog
        [open]="showCreateDialog()"
        [title]="'Planifier une séance pour ' + createDayLabel()"
        confirmText="Créer"
        [confirmEnabled]="createName().trim().length > 0"
        disabledReason="Nom requis"
        (confirm)="confirmCreate()"
        (dismiss)="showCreateDialog.set(false)"
      >
        <app-custom-text-field placeholder="Nom de la séance" [(value)]="createName" />
      </app-form-dialog>

      <!-- Dialog duplication (CopyPlannedWorkoutDialog) -->
      <app-form-dialog
        [open]="showCopyDialog()"
        title="Copier la séance"
        confirmText="Copier"
        [confirmEnabled]="true"
        (confirm)="confirmCopy()"
        (dismiss)="showCopyDialog.set(false)"
      >
        <span class="copy-hint">Choisir le jour cible :</span>
        <app-custom-select
          label="Jour"
          [selected]="copyTargetLabel()"
          [options]="copyDayOptions()"
          (select)="copyTargetLabel.set($event)"
        />
        <span class="copy-current">Jour actuel : {{ copySourceDayLabel() }}</span>
      </app-form-dialog>

      <!-- Confirmation sync du détail (= ConfirmationDialog PlannedWorkoutScreen) -->
      <app-confirmation-dialog
        [open]="showDetailSyncConfirm()"
        title="Synchroniser la séance"
        message="Synchroniser cette séance planifiée ?"
        confirmButtonText="Synchroniser"
        dismissButtonText="Annuler"
        confirmButtonColor="var(--app-primary-action)"
        (confirm)="confirmDetailSync()"
        (dismiss)="showDetailSyncConfirm.set(false)"
      />

      <!-- Ajout d'exercice planifié (= ExercisePickerBottomSheet → PhasePickerDialog Android) -->
      <app-exercise-picker-bottom-sheet
        [open]="showAddSheet()"
        title="Ajouter un exercice à la séance planifiée"
        [exercises]="addableExercises()"
        (selectExercise)="onPickExercise($event)"
        (dismissRequest)="showAddSheet.set(false)"
      />
      <app-phase-picker-dialog
        [open]="pendingExerciseUuid() !== null"
        (phaseSelected)="onPickPhase($event)"
        (dismiss)="pendingExerciseUuid.set(null)"
      />

      <!-- PlannedExerciseOptionsBottomSheet : options d'un exercice planifié (clic sur le nom) -->
      <app-options-bottom-sheet
        [open]="exoForOptions() !== null"
        [title]="exoForOptions()?.name ?? ''"
        [actions]="exoActions"
        (dismissRequest)="exoForOptions.set(null)"
        (actionSelected)="onExoAction($event)"
      />

      <!-- StatusPickerDialog : changement de statut de l'exercice planifié -->
      <app-status-picker-dialog
        [open]="exoForStatus() !== null"
        title="Modifier le statut de l'exercice planifié"
        [options]="exoStatusOptions"
        [selected]="normalizedStatus(exoForStatus()?.pwe?.status)"
        (confirm)="confirmExoStatus($event)"
        (dismiss)="exoForStatus.set(null)"
      />

      <!-- Confirmation retrait de la séance planifiée -->
      <app-confirmation-dialog
        [open]="exoToRemove() !== null"
        title="Confirmer la suppression"
        message="Retirer cet exercice de la séance planifiée ?"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        confirmButtonColor="var(--c-red-medium)"
        (confirm)="confirmExoRemove()"
        (dismiss)="exoToRemove.set(null)"
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
        gap: var(--space-2);
      }
      /* Bandeau semaine + sous-titre "Jours" : retrait horizontal (le corps flush n'a pas de padding),
         aligné sur le padding canonique 16px des cadres. */
      .week-progress {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        padding-inline: 16px;
      }
      .week-progress__bar {
        flex: 1;
      }
      .joursdiv {
        padding-inline: 16px;
      }
      /* Master-detail : jours (gauche) + détail du jour sélectionné (droite) — = session-page. */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__list {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding-bottom: var(--space-3);
      }
      .split__detail {
        flex: 1;
        min-width: 0;
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
        .split__list {
          flex: none;
          width: 100%;
        }
        .split__detail {
          width: 100%;
        }
      }
      /* Row jour = app-list-row (filet inset + liseré primaryAction du jour courant gérés par le
         composant). Un peu plus de respiration horizontale que le défaut + filet aligné dessus. */
      app-list-row.dayrow {
        padding-inline: var(--space-4);
      }
      app-list-row.dayrow::after {
        left: var(--space-4);
        right: var(--space-4);
      }
      /* ici seulement la disposition interne du contenu projeté. */
      .day__left {
        flex: 1;
        min-width: 0;
      }
      .day__head {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .day__pill {
        background: var(--app-bg-surface);
        border-radius: var(--radius-md);
        padding: 6px var(--space-3);
        color: var(--app-text-primary);
        font-size: 14px;
      }
      .day__rest {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        color: var(--c-blue-medium);
        font-size: 14px;
      }
      /* PlannedDayProgressBar : label + barre + % */
      .dayprog {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        padding: 6px 18px 0 6px;
      }
      .dayprog__label {
        font-size: 14px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        flex-shrink: 0;
        max-width: 60%;
      }
      .dayprog__bar {
        flex: 1;
      }
      .dayprog__pct {
        font-size: 13px;
        font-weight: 600;
      }
      .day__actions {
        display: flex;
        align-items: center;
        gap: 4px;
        flex-shrink: 0;
      }
      /* Détail (≈ PlannedWorkoutScreen) */
      .detail {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      /* Ligne d'en-tête du détail (= PlannedWorkoutProgressBar.kt) : barre + sync + bouton +. */
      .detail__progress {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      /* Tuiles totaux (séries/reps) → secondBlue dans le cadre thirdBlue (= en-tête exo page séance). */
      .detail__summary {
        margin-top: var(--space-2);
      }
      .detail__summary ::ng-deep .si {
        background: var(--c-second-blue);
      }
      /* Rows d'exercice d'une phase : fond secondBlue pour les distinguer (l'espacement vient du
         verticalPadding d'EntityListRow). */
      .detail__bar {
        flex: 1;
      }
      /* Répartition = PlannedExerciseRow.kt : nom 2.6 / groupe sync+statut 1.4 centré / sets×reps 1.4 à droite. */
      .exo-trailing {
        flex: 2.8;
        min-width: 0;
        display: flex;
        align-items: center;
      }
      /* 2 sous-boxes égales (≈ les 2 ActionIconButton 0.8 + 0.8 Android), chaque icône centrée. */
      .exo-trailing__icons {
        flex: 1.4;
        display: flex;
        align-items: center;
      }
      .exo-trailing__icon-box {
        flex: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }
      .exo-trailing__sr {
        flex: 1.4;
        color: var(--app-text-primary);
        font-size: 14px;
        text-align: end;
        white-space: nowrap;
      }
      .copy-hint {
        color: var(--app-text-primary);
        font-size: var(--font-size-body);
      }
      .copy-current {
        color: var(--app-text-tertiary);
        font-size: var(--font-size-caption);
      }
    `,
  ],
})
export class PlanningPage {
  /** Mode embarqué (hub Home) : masque la title bar (le hub fournit les onglets). */
  readonly embedded = input(false);

  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly snackbar = inject(SnackbarService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  // — Données offline-first (Dexie liveQuery → signals) —
  private readonly plannedWorkouts = toSignal(
    from(liveQuery(() => this.db.planned_workouts.filter((w) => !w.pendingDeletion).toArray())),
    { initialValue: [] as LocalPlannedWorkout[] },
  );
  private readonly plannedExercises = toSignal(
    from(liveQuery(() => this.db.planned_workout_exercises.toArray())),
    { initialValue: [] as LocalPlannedWorkoutExercise[] },
  );
  private readonly exercises = toSignal(from(liveQuery(() => this.db.exercises.toArray())), {
    initialValue: [] as LocalExercise[],
  });
  /** Séances réelles de la semaine courante + leurs exercices + leurs sets (3 queries chaînées). */
  private readonly weekActuals = toSignal(
    from(
      liveQuery(async () => {
        const { start, end } = weekBounds();
        const workouts = await this.db.actual_workouts.where('date').between(start, end, true, true).toArray();
        const exos = await this.db.actual_workout_exercises
          .where('actualWorkoutUUID')
          .anyOf(workouts.map((w) => w.uuid))
          .toArray();
        const sets = await this.db.actual_workout_sets
          .where('actualWorkoutExerciseUUID')
          .anyOf(exos.map((e) => e.uuid))
          .toArray();
        return { workouts, exos, sets };
      }),
    ),
    {
      initialValue: {
        workouts: [] as LocalActualWorkout[],
        exos: [] as LocalActualWorkoutExercise[],
        sets: [] as LocalActualWorkoutSet[],
      },
    },
  );

  // — Progression — miroir de calcDayProgressResult / weekProgress (WeekViewViewModel.kt) —

  /** Sets DONE capés par exercice planifié pour une séance planifiée + son actual éventuel. */
  private cappedDone(planned: LocalPlannedWorkout, actual: LocalActualWorkout): { done: number } {
    const { exos, sets } = this.weekActuals();
    const plannedEx = this.plannedExercises().filter(
      (e) => e.plannedWorkoutUUID === planned.uuid && !e.pendingDeletion && !e.ignored,
    );
    const plannedSetsByExercise = new Map<string, number>();
    for (const e of plannedEx) plannedSetsByExercise.set(e.exerciseUUID.trim(), e.sets);

    const actualExos = exos.filter((e) => e.actualWorkoutUUID === actual.uuid && !e.pendingDeletion);
    let done = 0;
    for (const [exUuid, plannedSets] of plannedSetsByExercise) {
      if (plannedSets <= 0) continue;
      const aweUuids = new Set(actualExos.filter((e) => e.exerciseUUID.trim() === exUuid).map((e) => e.uuid));
      const doneSets = sets.filter(
        (s) => aweUuids.has(s.actualWorkoutExerciseUUID) && !s.pendingDeletion && s.status.trim().toUpperCase() === 'DONE',
      ).length;
      done += Math.min(doneSets, plannedSets);
    }
    return { done };
  }

  private findActualFor(planned: LocalPlannedWorkout): LocalActualWorkout | null {
    return (
      this.weekActuals().workouts.find(
        (aw) =>
          aw.name === planned.name &&
          dayOfWeekFromDate(aw.date).toLowerCase() === planned.dayOfWeek.trim().toLowerCase() &&
          !aw.pendingDeletion,
      ) ?? null
    );
  }

  private dayProgress(planned: LocalPlannedWorkout): DayProgressResult {
    if (isFillerName(planned.name)) return { progress: 0, hasActual: false, completed: false, isBuilding: false };
    const plannedEx = this.plannedExercises().filter(
      (e) => e.plannedWorkoutUUID === planned.uuid && !e.pendingDeletion && !e.ignored,
    );
    const plannedTotalSets = plannedEx.reduce((acc, e) => acc + Math.max(0, e.sets), 0);
    if (plannedTotalSets === 0) return { progress: 0, hasActual: false, completed: false, isBuilding: true };

    const actual = this.findActualFor(planned);
    if (!actual) return { progress: 0, hasActual: false, completed: false, isBuilding: false };

    const { done } = this.cappedDone(planned, actual);
    const progress = Math.min(1, Math.max(0, done / plannedTotalSets));
    return { progress, hasActual: true, completed: actual.isDone || progress >= 0.999, isBuilding: false };
  }

  protected readonly weekProgress = computed(() => {
    let totalPlanned = 0;
    let totalDone = 0;
    for (const planned of this.plannedWorkouts()) {
      if (isFillerName(planned.name)) continue;
      const plannedEx = this.plannedExercises().filter(
        (e) => e.plannedWorkoutUUID === planned.uuid && !e.pendingDeletion && !e.ignored,
      );
      const plannedTotalSets = plannedEx.reduce((acc, e) => acc + Math.max(0, e.sets), 0);
      if (plannedTotalSets <= 0) continue;
      totalPlanned += plannedTotalSets;
      const actual = this.findActualFor(planned);
      if (actual) totalDone += this.cappedDone(planned, actual).done;
    }
    return totalPlanned === 0 ? 0 : Math.min(1, totalDone / totalPlanned);
  });

  // — Vue 7 jours (Lundi→Dimanche, filler « Jour de repos ») —
  protected readonly days = computed<DayVm[]>(() => {
    const byDay = new Map<string, LocalPlannedWorkout>();
    for (const w of this.plannedWorkouts()) byDay.set(this.canonicalDay(w.dayOfWeek), w);
    const today = DAYS[(new Date().getDay() + 6) % 7];

    return DAYS.map((day) => {
      const workout = byDay.get(day) ?? null;
      const isFiller = !workout || isFillerName(workout.name);
      const result = workout && !isFiller ? this.dayProgress(workout) : { progress: 0, hasActual: false, completed: false, isBuilding: false };

      // Icône de statut du jour (ordre Android : building > completed > hasActual > sinon)
      let statusIcon = 'autoplay';
      let statusTint = 'var(--c-red-dark)';
      if (result.isBuilding) {
        statusIcon = 'construction';
        statusTint = 'var(--c-dark-orange)';
      } else if (result.completed) {
        statusIcon = 'check';
        statusTint = 'var(--c-medium-green)';
      } else if (result.hasActual) {
        // ic_arrow_progress Android absent de la police web → arrow_circle_up (convention set-row).
        statusIcon = 'arrow_circle_up';
        statusTint = 'var(--c-blue-medium)';
      }

      return {
        day,
        label: FR_LABEL[day],
        isToday: day === today,
        isFiller,
        workout,
        name: isFiller ? REST_LABEL : workout!.name,
        synced: workout?.synced ?? true,
        result,
        statusIcon,
        statusTint,
        showBar: !isFiller && !result.isBuilding,
        percent: Math.round(result.progress * 100),
        barColor: progressColor(result.progress),
        labelColor: isFiller
          ? 'var(--app-text-tertiary)'
          : result.isBuilding
            ? 'var(--c-dark-orange)'
            : 'var(--app-primary-action)',
      };
    });
  });

  private canonicalDay(dayOfWeek: string): string {
    const lower = dayOfWeek.trim().toLowerCase();
    return DAYS.find((d) => d.toLowerCase() === lower) ?? dayOfWeek.trim();
  }

  // — Master-detail : jour sélectionné (défaut = aujourd'hui, comme session-page → 1er exo) —
  protected readonly selectedDay = signal<string | null>(null);
  protected readonly effectiveDay = computed(
    () => this.selectedDay() ?? DAYS[(new Date().getDay() + 6) % 7],
  );
  protected readonly selectedDayVm = computed(
    () => this.days().find((d) => d.day === this.effectiveDay()) ?? null,
  );

  private readonly detailExercises = computed(() => {
    const workout = this.selectedDayVm()?.workout;
    if (!workout) return [] as LocalPlannedWorkoutExercise[];
    return this.plannedExercises().filter((e) => e.plannedWorkoutUUID === workout.uuid && !e.pendingDeletion);
  });

  protected readonly detailSummary = computed<SummaryItemData[]>(() => {
    const exos = this.detailExercises();
    const totalSets = exos.reduce((acc, e) => acc + e.sets, 0);
    const totalReps = exos.reduce((acc, e) => acc + (parseInt(e.reps, 10) || 0), 0);
    return [
      { icon: 'check', value: String(totalSets), label: 'Séries totales', iconTint: 'var(--c-medium-green)' },
      { icon: 'refresh', value: String(totalReps), label: 'Reps totales', iconTint: 'var(--c-orange-medium)' },
    ];
  });

  /** 3 phases canoniques toujours affichées (= PlannedWorkoutScreen.kt : Warm-Up / Training / Post-Training). */
  protected readonly detailPhases = computed<PhaseVm[]>(() => {
    const exName = new Map(this.exercises().map((e) => [e.uuid, e.name]));
    const phases: { label: string; empty: string; keys: string[] }[] = [
      { label: 'Échauffement', empty: "Aucun exercice d'échauffement.", keys: ['WARMUP', 'WARM_UP'] },
      { label: 'Entraînement', empty: "Aucun exercice d'entraînement.", keys: ['TRAINING'] },
      { label: 'Récupération', empty: 'Aucun exercice de récupération.', keys: ['POST_TRAINING', 'POSTTRAINING'] },
    ];
    return phases.map(({ label, empty, keys }) => ({
      label,
      empty,
      rows: this.detailExercises()
        .filter((e) => keys.includes(e.phase.toUpperCase()))
        .sort((a, b) => a.order - b.order)
        .map((pwe) => ({
          pwe,
          name: exName.get(pwe.exerciseUUID) ?? '—',
          ...this.exerciseStatusIcon(pwe),
        })),
    }));
  });

  /** Icône/couleur de statut d'un exercice planifié — miroir de PlannedExerciseRow.kt. */
  private exerciseStatusIcon(pwe: LocalPlannedWorkoutExercise): { statusIcon: string; statusTint: string } {
    if (pwe.ignored) return { statusIcon: 'close', statusTint: 'var(--c-orange-medium)' };
    switch (pwe.status.replace(' ', '_').toUpperCase()) {
      case 'DONE':
        return { statusIcon: 'check', statusTint: 'var(--c-medium-green)' };
      case 'PLANNED':
        return { statusIcon: 'arrow_circle_up', statusTint: 'var(--c-blue-medium)' };
      case 'SKIPPED':
        return { statusIcon: 'cancel', statusTint: 'var(--c-red-medium)' };
      case 'NOT_STARTED':
        return { statusIcon: 'not_started', statusTint: 'var(--c-orange-medium)' };
      default:
        return { statusIcon: 'info', statusTint: 'var(--app-text-tertiary)' };
    }
  }

  // — Détail : sync + ajout d'exercice (= PlannedWorkoutScreen.kt) —
  protected readonly showDetailSyncConfirm = signal(false);
  protected readonly showAddSheet = signal(false);
  protected readonly pendingExerciseUuid = signal<string | null>(null);

  /** Exercices pas encore dans la séance planifiée — alimente le picker d'ajout. */
  protected readonly addableExercises = computed<ExercisePickerItem[]>(() => {
    const inWorkout = new Set(this.detailExercises().map((e) => e.exerciseUUID));
    return this.exercises()
      .filter((e) => !inWorkout.has(e.uuid))
      .map((e) => ({ uuid: e.uuid, name: e.name, equipments: [] }));
  });

  protected confirmDetailSync(): void {
    this.showDetailSyncConfirm.set(false);
    void this.sync
      .syncAll()
      .then(() => this.snackbar.success('Séance planifiée synchronisée.'))
      .catch(() => this.snackbar.error('Échec de la synchronisation.'));
  }

  /** Exercice choisi dans le picker → on demande la phase. */
  protected onPickExercise(exerciseUUID: string): void {
    this.showAddSheet.set(false);
    this.pendingExerciseUuid.set(exerciseUUID);
  }

  /** Phase choisie → crée le planned_workout_exercise (= addPlannedExerciseToPhase Android). */
  protected async onPickPhase(phase: string): Promise<void> {
    const exerciseUUID = this.pendingExerciseUuid();
    this.pendingExerciseUuid.set(null);
    const workout = this.selectedDayVm()?.workout;
    if (!exerciseUUID || !workout) return;
    const def = this.exercises().find((e) => e.uuid === exerciseUUID);
    if (!def) return;

    const maxOrderInPhase = this.detailExercises()
      .filter((e) => e.phase.toUpperCase() === phase.toUpperCase())
      .reduce((m, e) => Math.max(m, e.order), 0);

    const row: LocalPlannedWorkoutExercise = {
      uuid: uuidv4(),
      plannedWorkoutUUID: workout.uuid,
      exerciseUUID,
      sets: def.recommendedSets ?? 3,
      reps: String(parseInt(def.recommendedReps?.split('-')[0] ?? '', 10) || 10),
      phase,
      status: 'PLANNED',
      order: maxOrderInPhase + 1,
      ignored: false,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.planned_workout_exercises.put(row);
    void this.sync.syncAll().catch(() => undefined);
    this.snackbar.success(`« ${def.name} » ajouté.`);
  }

  // — Options d'un exercice planifié (= PlannedExerciseOptionsBottomSheet.kt) —
  protected readonly exoForOptions = signal<ExoRowVm | null>(null);
  protected readonly exoForStatus = signal<ExoRowVm | null>(null);
  protected readonly exoToRemove = signal<ExoRowVm | null>(null);

  protected readonly exoActions: SheetAction[] = [
    { label: "Voir les détails de l'exercice", icon: 'visibility', color: 'var(--c-blue-medium)' },
    { label: 'Changer le statut', icon: 'edit', color: 'var(--app-selected-fill)' },
    { label: 'Retirer de la séance planifiée', icon: 'delete_forever', color: 'var(--c-red-medium)' },
  ];

  /** Options de statut (= StatusPickerDialog de PlannedWorkoutScreen.kt, mêmes couples icône/couleur). */
  protected readonly exoStatusOptions: StatusOption[] = [
    { value: 'DONE', label: 'Terminé', icon: 'check', color: 'var(--c-medium-green)' },
    { value: 'PLANNED', label: 'Planifié', icon: 'arrow_circle_up', color: 'var(--app-primary-action)' },
    { value: 'NOT_STARTED', label: 'Non commencé', icon: 'not_started', color: 'var(--c-orange-medium)' },
    { value: 'SKIPPED', label: 'Ignoré', icon: 'cancel', color: 'var(--c-red-medium)' },
  ];

  /** Normalisation défensive du code statut (= .replace(" ", "_").uppercase() Android). */
  protected normalizedStatus(status: string | undefined): string {
    return (status ?? '').replace(' ', '_').toUpperCase();
  }

  protected onExoAction(label: string): void {
    const row = this.exoForOptions();
    this.exoForOptions.set(null);
    if (!row) return;
    if (label === "Voir les détails de l'exercice") {
      void this.router.navigate(['/exercises'], { queryParams: { q: row.name } });
    } else if (label === 'Changer le statut') {
      this.exoForStatus.set(row);
    } else if (label === 'Retirer de la séance planifiée') {
      this.exoToRemove.set(row);
    }
  }

  protected async confirmExoStatus(newStatus: string): Promise<void> {
    const row = this.exoForStatus();
    this.exoForStatus.set(null);
    if (!row || !newStatus) return;
    await this.db.planned_workout_exercises.update(row.pwe.uuid, {
      status: newStatus,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    void this.sync.syncAll().catch(() => undefined);
  }

  protected async confirmExoRemove(): Promise<void> {
    const row = this.exoToRemove();
    this.exoToRemove.set(null);
    if (!row) return;
    await this.db.planned_workout_exercises.update(row.pwe.uuid, {
      pendingDeletion: true,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    void this.sync.syncAll().catch(() => undefined);
  }

  // — Sheets / dialogs —
  protected readonly showWeekSheet = signal(false);
  protected readonly dayForOptions = signal<DayVm | null>(null);
  protected readonly showRenameDialog = signal(false);
  protected readonly renameName = signal('');
  protected readonly renameTarget = signal<LocalPlannedWorkout | null>(null);
  protected readonly showCreateDialog = signal(false);
  protected readonly createName = signal('');
  protected readonly createDay = signal<string>('Monday');
  protected readonly showCopyDialog = signal(false);
  protected readonly copySource = signal<LocalPlannedWorkout | null>(null);
  protected readonly copyTargetLabel = signal('');

  protected readonly weekActions: SheetAction[] = [
    { label: 'Synchroniser les séances', icon: 'cloud_upload', color: 'var(--app-primary-action)' },
    { label: 'Marquer les séances comme terminées', icon: 'check', color: 'var(--c-medium-green)' },
    { label: 'Marquer les séances comme non terminées', icon: 'close', color: 'var(--c-orange-medium)' },
  ];

  protected readonly dayActions = computed<SheetAction[]>(() => {
    const d = this.dayForOptions();
    if (!d) return [];
    if (d.isFiller) {
      return [{ label: 'Planifier une séance ce jour', icon: 'add', color: 'var(--app-selected-fill)' }];
    }
    const done = d.workout ? this.isPlannedWorkoutDone(d.workout) : false;
    return [
      { label: 'Renommer la séance planifiée', icon: 'edit', color: 'var(--c-blue-medium)' },
      done
        ? { label: 'Marquer comme non terminée', icon: 'check_indeterminate_small', color: 'var(--c-orange-medium)' }
        : { label: 'Marquer comme terminée', icon: 'check', color: 'var(--c-medium-green)' },
      { label: 'Dupliquer la séance planifiée', icon: 'content_copy', color: 'var(--app-selected-fill)' },
      { label: 'Supprimer la séance planifiée', icon: 'delete_forever', color: 'var(--c-red-medium)' },
    ];
  });

  protected readonly createDayLabel = computed(() => FR_LABEL[this.createDay()] ?? this.createDay());
  protected readonly copySourceDayLabel = computed(() => {
    const src = this.copySource();
    return src ? (FR_LABEL[this.canonicalDay(src.dayOfWeek)] ?? src.dayOfWeek) : '';
  });
  protected readonly copyDayOptions = computed(() => {
    const src = this.copySource();
    const current = src ? this.canonicalDay(src.dayOfWeek) : '';
    return DAYS.filter((d) => d !== current).map((d) => FR_LABEL[d]);
  });

  constructor() {
    void this.sync.syncAll().catch(() => undefined);

    // Pré-sélection du jour via ?day=<jour de semaine EN> (depuis « Voir la séance planifiée »
    // du Calendrier). On normalise le nom canonique (Monday…) avant de l'appliquer.
    const dayParam = this.route.snapshot.queryParamMap.get('day');
    if (dayParam) {
      const canonical = DAYS.find((d) => d.toLowerCase() === dayParam.trim().toLowerCase());
      if (canonical) this.selectedDay.set(canonical);
    }
  }

  private isPlannedWorkoutDone(planned: LocalPlannedWorkout): boolean {
    const actual = this.findActualFor(planned);
    return actual?.isDone === true;
  }

  // — Actions semaine —
  protected onWeekAction(label: string): void {
    this.showWeekSheet.set(false);
    if (label === 'Synchroniser les séances') this.syncPlanned();
    else if (label === 'Marquer les séances comme terminées') void this.markAllActuals(true);
    else if (label === 'Marquer les séances comme non terminées') void this.markAllActuals(false);
  }

  protected syncPlanned(): void {
    void this.sync.syncAll().catch(() => this.snackbar.error('Échec de la synchronisation'));
  }

  private async markAllActuals(isDone: boolean): Promise<void> {
    const now = new Date().toISOString();
    for (const aw of this.weekActuals().workouts) {
      if (!aw.pendingDeletion && aw.isDone !== isDone) {
        await this.db.actual_workouts.update(aw.uuid, { isDone, synced: false, updatedAt: now });
      }
    }
    void this.sync.syncAll().catch(() => undefined);
  }

  // — Actions jour —
  protected openDayOptions(d: DayVm): void {
    this.dayForOptions.set(d);
  }

  protected onDayAction(label: string): void {
    const d = this.dayForOptions();
    this.dayForOptions.set(null);
    if (!d) return;
    if (label === 'Planifier une séance ce jour') {
      this.createDay.set(d.day);
      this.createName.set('');
      this.showCreateDialog.set(true);
    } else if (label === 'Renommer la séance planifiée') {
      this.renameName.set(d.workout?.name ?? '');
      this.renameTarget.set(d.workout);
      this.showRenameDialog.set(true);
    } else if (label === 'Marquer comme terminée' || label === 'Marquer comme non terminée') {
      if (d.workout) void this.toggleDone(d.workout);
    } else if (label === 'Dupliquer la séance planifiée') {
      if (d.workout) {
        this.copySource.set(d.workout);
        this.copyTargetLabel.set(this.copyDayOptions()[0] ?? '');
        this.showCopyDialog.set(true);
      }
    } else if (label === 'Supprimer la séance planifiée') {
      if (d.workout) void this.deletePlanned(d.workout);
    }
  }

  private async toggleDone(planned: LocalPlannedWorkout): Promise<void> {
    const actual = this.findActualFor(planned);
    if (!actual) {
      this.snackbar.info('Aucune séance réelle cette semaine pour ce jour');
      return;
    }
    await this.db.actual_workouts.update(actual.uuid, {
      isDone: !actual.isDone,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    void this.sync.syncAll().catch(() => undefined);
  }

  private async deletePlanned(planned: LocalPlannedWorkout): Promise<void> {
    await this.db.planned_workouts.update(planned.uuid, {
      pendingDeletion: true,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    void this.sync.syncAll().catch(() => undefined);
  }

  protected async confirmRename(): Promise<void> {
    const target = this.renameTarget();
    const newName = this.renameName().trim();
    this.showRenameDialog.set(false);
    if (!target || newName.length === 0) return;
    if (isFillerName(newName)) {
      this.snackbar.info(REST_NAME_ERROR);
      return;
    }
    await this.db.planned_workouts.update(target.uuid, {
      name: newName,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    void this.sync.syncAll().catch(() => undefined);
  }

  protected async confirmCreate(): Promise<void> {
    const name = this.createName().trim();
    this.showCreateDialog.set(false);
    if (name.length === 0) return;
    if (isFillerName(name)) {
      this.snackbar.info(REST_NAME_ERROR);
      return;
    }
    const row: LocalPlannedWorkout = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      name,
      dayOfWeek: this.createDay(),
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.planned_workouts.put(row);
    void this.sync.syncAll().catch(() => undefined);
  }

  protected async confirmCopy(): Promise<void> {
    const source = this.copySource();
    this.showCopyDialog.set(false);
    if (!source) return;
    const targetDay = DAYS.find((d) => FR_LABEL[d] === this.copyTargetLabel());
    if (!targetDay) return;
    const now = new Date().toISOString();
    // Remplace la séance existante du jour cible le cas échéant (option A Android).
    const existing = this.plannedWorkouts().find((w) => this.canonicalDay(w.dayOfWeek) === targetDay);
    if (existing) {
      await this.db.planned_workouts.update(existing.uuid, { pendingDeletion: true, synced: false, updatedAt: now });
    }
    const copy: LocalPlannedWorkout = {
      uuid: uuidv4(),
      userId: source.userId,
      name: `${source.name} (Copy)`,
      dayOfWeek: targetDay,
      updatedAt: now,
      synced: false,
      pendingDeletion: false,
    };
    await this.db.planned_workouts.put(copy);
    void this.sync.syncAll().catch(() => undefined);
  }
}
