import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { LocalMuscle } from '@core/models/muscle.model';
import { LocalMuscleGoal } from '@core/models/muscle-goal.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { AppIcon } from '@designsystem/icons/app-icon';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { FramedSection } from '@designsystem/common_components/framed-section';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { LabeledProgressBar } from '@designsystem/common_components/labeled-progress-bar';
import { SegmentedIconToggle, type SegmentItem } from '@designsystem/common_components/segmented-icon-toggle';
import { FilterDropdown } from '@designsystem/common_components/filter-dropdown';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { StatusPickerDialog, type StatusOption } from '@designsystem/common_components/status-picker-dialog';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { HorizontalNumberPicker } from '@designsystem/common_components/horizontal-number-picker';
import { GoalsAchievementChart, type GoalBar } from './goals-achievement-chart';
import { paletteForZone, resolveCssColor } from '../stats/palette-util';

// ─── Hiérarchie anatomique (miroir core/data/Zones.kt — ordre canonique du tri PALETTE) ───
const ZONES_ALL = ['Chest', 'Back', 'Shoulders', 'Arms', 'Legs', 'Core'];

type ViewMode = 'MUSCLE' | 'GROUP' | 'ZONE';
type SortMode = 'ALPHA' | 'PALETTE' | 'PERCENT_DESC' | 'PERCENT_ASC' | 'PRIORITY';

const PRIORITY_ORDER: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
const TARGET_OPTIONS = ['12+', '6-12', '3-5'];

/** Goal enrichi (= GoalWithPercent Android) : % cap-free + flag SKIPPED + hiérarchie du muscle. */
interface GoalVm {
  goal: LocalMuscleGoal;
  /** `done` recalculé client (sets validés de la semaine, miroir muscleDoneCount Android). */
  done: number;
  muscleName: string;
  group: string | null;
  zone: string | null;
  targetMin: number;
  percent: number;
  skipped: boolean;
}

/** Card groupée (modes GROUP/ZONE) : clé + couleur (palette par zone) + goals triés. */
interface GroupVm {
  key: string;
  color: string;
  goals: GoalVm[];
}

/**
 * Parse le `target` d'un goal ("12+", "6-12", "12") → minimum à atteindre
 * (miroir parseTargetMinimum, GoalUtils.kt). Parsing fail → jamais atteint.
 */
function parseTargetMinimum(target: string): number {
  if (target.endsWith('+')) {
    const v = parseInt(target.slice(0, -1), 10);
    return Number.isNaN(v) ? Number.MAX_SAFE_INTEGER : v;
  }
  if (target.includes('-')) {
    const v = parseInt(target.split('-')[0], 10);
    return Number.isNaN(v) ? Number.MAX_SAFE_INTEGER : v;
  }
  const v = parseInt(target, 10);
  return Number.isNaN(v) ? Number.MAX_SAFE_INTEGER : v;
}

function normalizeStatus(s: string | null | undefined): string {
  return (s ?? 'IN_PROGRESS').trim().toUpperCase().replace(/ /g, '_');
}

/**
 * Ligne de goal — miroir de GoalRow.kt : Muscle (clic → options) | Prio (clic → picker) |
 * Fait | À faire (clic → picker) | Statut (icône). Poids de colonnes 5/2/2/2/2 alignés sur
 * le header table (refonte Android 2026-05-09 iter 2).
 */
@Component({
  selector: 'app-goal-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="grow">
      <button class="grow__name" (click)="nameClick.emit()">{{ vm().muscleName }}</button>
      <span class="grow__cell">
        <button
          class="grow__prio"
          [style.border-color]="prioColor()"
          (click)="priorityClick.emit()"
          [attr.aria-label]="'Changer la priorité'"
        >
          <app-icon [name]="prioIcon()" [size]="18" [color]="prioColor()" />
        </button>
      </span>
      <span class="grow__cell grow__done">{{ vm().done }}</span>
      <span class="grow__cell">
        <button class="grow__target" (click)="targetClick.emit()">{{ vm().goal.target }}</button>
      </span>
      <span class="grow__cell">
        <app-icon [name]="statusIcon()" [size]="22" [color]="statusColor()" />
      </span>
    </div>
  `,
  styles: [
    `
      /* Row plate sur le cadre thirdBlue (style page séance) : fond transparent,
         nom en boîte second-blue, lignes séparées par un divider (géré sur :host). */
      :host {
        display: block;
      }
      :host:not(:last-child) {
        border-bottom: 1px solid var(--c-second-blue);
      }
      /* minmax(0, …) : pistes strictement proportionnelles (min 0), donc le bouton « À faire » ne peut
         plus élargir sa colonne au-delà de sa part → mêmes largeurs de colonnes que le header (alignés). */
      .grow {
        display: grid;
        grid-template-columns: minmax(0, 5fr) minmax(0, 2fr) minmax(0, 2fr) minmax(0, 2fr) minmax(0, 2fr);
        align-items: center;
        height: 36px;
      }
      .grow__name {
        height: 28px;
        background: var(--c-second-blue);
        border: none;
        border-radius: var(--radius-md);
        padding: 0 6px 0 14px;
        text-align: left;
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        cursor: pointer;
      }
      .grow__cell {
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .grow__done {
        color: var(--app-text-primary);
        font-size: 14px;
      }
      .grow__prio {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        background: transparent;
        border: 1px solid;
        border-radius: var(--radius-sm);
        cursor: pointer;
        padding: 0;
      }
      .grow__target {
        width: 100%;
        max-width: 48px;
        min-width: 0;
        height: 28px;
        background: var(--c-second-blue);
        border: none;
        border-radius: var(--radius-sm);
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        cursor: pointer;
      }
    `,
  ],
})
export class GoalRow {
  readonly vm = input.required<GoalVm>();
  readonly nameClick = output<void>();
  readonly targetClick = output<void>();
  readonly priorityClick = output<void>();

  protected prioIcon(): string {
    switch (this.vm().goal.priority.toUpperCase()) {
      case 'HIGH': return 'north';
      case 'LOW': return 'south';
      default: return 'north_east';
    }
  }
  protected prioColor(): string {
    switch (this.vm().goal.priority.toUpperCase()) {
      case 'HIGH': return 'var(--app-priority-high)';
      case 'LOW': return 'var(--app-priority-low)';
      default: return 'var(--app-priority-medium)';
    }
  }
  protected statusIcon(): string {
    switch (normalizeStatus(this.vm().goal.status)) {
      case 'DONE': return 'check';
      case 'SKIPPED': return 'close';
      // ic_arrow_progress Android absent de la police web → arrow_circle_up (convention set-row).
      case 'IN_PROGRESS': return 'arrow_circle_up';
      default: return 'remove';
    }
  }
  protected statusColor(): string {
    switch (normalizeStatus(this.vm().goal.status)) {
      case 'DONE': return 'var(--c-medium-green)';
      case 'SKIPPED': return 'var(--c-red-medium)';
      case 'IN_PROGRESS': return 'var(--c-blue-medium)';
      default: return 'var(--app-text-tertiary)';
    }
  }
}

/**
 * Écran Objectifs — refonte fidèle de GoalsTabContent.kt (onglet hebdo, refonte Android 2026-05-09) :
 * - Header semaine : ◀ / nuage sync / plage de dates (clic = retour semaine courante) / indicateur
 *   « tout fait » / ▶ (miroir GoalsHeader).
 * - Barre de progression globale (calculateGoalProgress : DONE compté à 100 %) + ⋮ → sheet « Ajouter ».
 * - Toggles : 3 niveaux d'affichage MUSCLE/GROUP/ZONE + 5 tris (ALPHA / PALETTE / % desc / % asc /
 *   PRIORITY), appliqués simultanément à la liste ET au chart footer.
 * - Liste : rows plates (MUSCLE) ou cards à bordure teintée + label flottant (GROUP/ZONE), palette
 *   par zone via paletteForZone spread 0.4 (cohérente Stats).
 * - Chart footer : % d'achievement cap-free, ligne pointillée 100 % toujours visible, SKIPPED alpha 0.4.
 * - `done` recalculé 100 % client (miroir muscleDoneCount du VM Android) : sets de la semaine dont
 *   reps ≥ reps attendues OU status DONE, par lien exercise_muscle. Auto-complétion DONE quand la
 *   cible est atteinte (miroir autoCompleteFinishedGoals).
 * - CRUD : ajout (muscle + objectif + priorité), changement statut/priorité/objectif, suppression,
 *   copie des objectifs de la semaine précédente (semaine vide).
 * Différé vs Android : « Voir le muscle » (pas de page détail muscle web), dialog de confirmation sync.
 */
@Component({
  selector: 'app-goals-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    FramedSection,
    ActionIconButton,
    LabeledProgressBar,
    SegmentedIconToggle,
    FilterDropdown,
    FormDialog,
    ConfirmationDialog,
    StatusPickerDialog,
    OptionsBottomSheet,
    HorizontalNumberPicker,
    GoalsAchievementChart,
    GoalRow,
    AppIcon,
  ],
  template: `
    <section class="page">
      @if (!embedded()) { <app-screen-title-bar title="Objectifs" /> }

      <div class="page__body">
        <!-- ── Cadre unique « Objectifs » : header semaine + progression + toggles + lignes de goals
             + graphe footer % d'achievement ── -->
        <app-framed-section title="Objectifs">
          <div class="gsec">
            <!-- Header semaine (miroir GoalsHeader) -->
            <div class="whead">
              <app-action-icon-button icon="chevron_left" (clicked)="changeWeek(-1)" />
              <app-action-icon-button
                [icon]="allSynced() ? 'cloud_done' : 'cloud_off'"
                [hasBackground]="false"
                [tint]="allSynced() ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                (clicked)="showSyncConfirm.set(true)"
              />
              <button class="whead__range" (click)="onRangeClick()">{{ week().label }}</button>
              <span
                class="whead__alldone"
                [class.whead__alldone--done]="allDone()"
                [attr.aria-label]="allDone() ? 'Tous les objectifs sont faits' : 'Objectifs en cours'"
              >
                <app-icon
                  [name]="allDone() ? 'check' : 'arrow_circle_up'"
                  [size]="22"
                  [color]="allDone() ? 'var(--app-text-primary)' : 'var(--c-blue-medium)'"
                />
              </span>
              <app-action-icon-button icon="chevron_right" (clicked)="changeWeek(1)" />
            </div>

            <!-- Progression globale + ⋮ (miroir GoalsProgressBar) -->
            <div class="progress">
              <app-labeled-progress-bar
                class="progress__bar"
                [progress]="overallProgress()"
                troughColor="var(--c-second-blue)"
              />
              <app-action-icon-button icon="add" (clicked)="openAdd()" />
            </div>

            <!-- Toggles : 3 niveaux + 5 tris (liste ET chart) -->
            <div class="toggles">
              <app-segmented-icon-toggle
                [items]="viewModeItems"
                [selected]="viewMode()"
                [width]="36"
                [iconSize]="16"
                (select)="setViewMode($event)"
              />
              <app-segmented-icon-toggle
                [items]="sortItems"
                [selected]="sortMode()"
                [width]="36"
                [iconSize]="16"
                (select)="setSortMode($event)"
              />
            </div>

            @if (weekGoals().length === 0) {
              <!-- Semaine vide (miroir EmptyGoalsWeekState) -->
              <div class="empty">
                <span class="empty__title">Aucun objectif cette semaine</span>
                <span class="empty__hint">Tu peux copier les objectifs de la semaine dernière pour démarrer vite.</span>
                <button class="empty__copy" (click)="copyFromLastWeek()">
                  <app-icon name="content_copy" [size]="20" color="var(--app-text-primary)" />
                  Copier les objectifs de la semaine dernière
                </button>
              </div>
            } @else {
              <!-- Header table affiché une seule fois (refonte Android 2026-05-09) -->
              <div class="thead">
                <span class="thead__cell thead__cell--muscle">Muscle</span>
                <span class="thead__cell">Prio.</span>
                <span class="thead__cell">Fait</span>
                <span class="thead__cell">À faire</span>
                <span class="thead__cell">Statut</span>
              </div>

              @if (viewMode() === 'MUSCLE') {
                <div class="list">
                  @for (g of sortedList(); track g.goal.uuid) {
                    <app-goal-row
                      [vm]="g"
                      (nameClick)="optionsFor.set(g)"
                      (targetClick)="targetFor.set(g)"
                      (priorityClick)="priorityFor.set(g)"
                    />
                  }
                </div>
              } @else {
                <!-- Cards groupées par groupe musculaire / zone (miroir ZoneGoalsCard) -->
                <div class="cards">
                  @for (grp of groupedCards(); track grp.key) {
                    <div
                      class="card"
                      [style.border-color]="mix(grp.color, 35)"
                      [style.background]="mix(grp.color, 8)"
                    >
                      <span class="card__label" [style.color]="grp.color" [style.border-color]="mix(grp.color, 35)">
                        {{ grp.key }}
                      </span>
                      <div class="card__rows">
                        @for (g of grp.goals; track g.goal.uuid) {
                          <app-goal-row
                            [vm]="g"
                            (nameClick)="optionsFor.set(g)"
                            (targetClick)="targetFor.set(g)"
                            (priorityClick)="priorityFor.set(g)"
                          />
                        }
                      </div>
                    </div>
                  }
                </div>
              }
            }
          </div>
          <!-- Graphe footer % d'achievement (ligne 100 % pointillée), dans le même cadre. -->
          <app-goals-achievement-chart class="goals-chart" [data]="chartBars()" />
        </app-framed-section>
      </div>

      <!-- ── Sheet options d'un goal (miroir MuscleOptionsBottomSheet) ── -->
      <app-options-bottom-sheet
        [open]="optionsFor() !== null"
        [title]="optionsFor()?.muscleName ?? ''"
        [actions]="goalActions()"
        (dismissRequest)="optionsFor.set(null)"
        (actionSelected)="onGoalAction($event)"
      />

      <!-- ── Ajout d'un objectif (miroir dialog Add muscle) ── -->
      <app-form-dialog
        [open]="showAdd()"
        title="Ajouter un muscle aux objectifs"
        confirmText="Ajouter"
        [confirmEnabled]="canAdd()"
        [disabledReason]="addDisabledReason()"
        (confirm)="confirmAdd()"
        (dismiss)="showAdd.set(false)"
      >
        <app-filter-dropdown
          label="Choisir un muscle"
          [options]="muscleNames()"
          [selected]="addMuscleName()"
          (select)="addMuscleName.set($event)"
        />
        <app-filter-dropdown
          label="Choisir un objectif"
          [options]="targetOptions"
          [selected]="addTarget()"
          (select)="addTarget.set($event)"
        />
        <div class="prio-pick">
          <span class="prio-pick__label">Priorité</span>
          <div class="prio-pick__row">
            @for (p of priorityOptions; track p.value) {
              <button
                class="prio-pick__btn"
                [class.prio-pick__btn--sel]="addPriority() === p.value"
                [style.border-color]="p.color"
                [style.color]="p.color"
                (click)="addPriority.set(p.value)"
              >
                <app-icon [name]="p.icon" [size]="16" [color]="p.color" />
                {{ p.label }}
              </button>
            }
          </div>
        </div>
      </app-form-dialog>

      <!-- ── Changement d'objectif (miroir TargetPickerDialog : presets + valeur libre) ── -->
      <app-form-dialog
        [open]="targetFor() !== null"
        title="Changer l'objectif"
        confirmText="Fermer"
        (confirm)="targetFor.set(null)"
        (dismiss)="targetFor.set(null)"
      >
        @for (opt of targetOptions; track opt) {
          <button
            class="tgt__row"
            [class.tgt__row--sel]="targetFor()?.goal?.target === opt"
            (click)="applyTarget(opt)"
          >
            <span>{{ opt }}</span>
            @if (targetFor()?.goal?.target === opt) {
              <app-icon name="check" [size]="20" color="var(--app-primary-action)" />
            }
          </button>
        }
        <app-horizontal-number-picker
          label="Ou choisis une valeur exacte"
          [min]="1"
          [max]="20"
          [selected]="customTargetValue()"
          (selectedChange)="applyTarget($event.toString())"
        />
      </app-form-dialog>

      <!-- ── Changement de statut (miroir ChangeGoalStatusDialog) ── -->
      <app-status-picker-dialog
        [open]="statusFor() !== null"
        title="Changer le statut de l'objectif"
        [options]="statusOptions"
        [selected]="statusForSelected()"
        (confirm)="confirmStatus($event)"
        (dismiss)="statusFor.set(null)"
      />

      <!-- ── Changement de priorité (miroir EditPriorityDialog) ── -->
      <app-status-picker-dialog
        [open]="priorityFor() !== null"
        title="Changer la priorité"
        [options]="priorityOptions"
        [selected]="priorityFor()?.goal?.priority ?? ''"
        (confirm)="confirmPriority($event)"
        (dismiss)="priorityFor.set(null)"
      />

      <!-- ── Confirmations sync / retour semaine courante / suppression ── -->
      <app-confirmation-dialog
        [open]="showSyncConfirm()"
        title="Synchroniser les objectifs"
        message="Pousser les objectifs locaux vers le serveur maintenant ?"
        confirmButtonText="Synchroniser"
        dismissButtonText="Annuler"
        (confirm)="confirmSync()"
        (dismiss)="showSyncConfirm.set(false)"
      />
      <app-confirmation-dialog
        [open]="showResetConfirm()"
        title="Revenir à la semaine courante"
        message="Tu consultes une autre semaine. Revenir à la semaine en cours ?"
        confirmButtonText="Oui"
        dismissButtonText="Annuler"
        (confirm)="confirmReset()"
        (dismiss)="showResetConfirm.set(false)"
      />
      <app-confirmation-dialog
        [open]="toDelete() !== null"
        title="Supprimer l'objectif"
        [message]="'Supprimer l\\'objectif « ' + (toDelete()?.muscleName ?? '') + ' » de cette semaine ?'"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDelete()"
        (dismiss)="toDelete.set(null)"
      />
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; corps avec gouttière (--page-gutter).
         3 cadres thirdBlue empilés (header+progression / affichage+lignes / graphe),
         espacés comme les cadres de la colonne calendrier (space-2). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      /* Contenu interne d'un cadre : empilement vertical aéré (header→barre, toggles→liste). */
      .gsec {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* Graphe footer % d'achievement (même cadre que les lignes de goals) : espace du contenu au-dessus. */
      .goals-chart {
        display: block;
        margin-top: var(--space-3);
      }
      /* Header semaine : ◀ nuage plage ✓ ▶ (miroir GoalsHeader, SpaceBetween). */
      .whead {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
      }
      .whead__range {
        height: 40px;
        padding: 0 var(--space-3);
        background: var(--app-bg-button);
        border: none;
        border-radius: var(--radius-md);
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        cursor: pointer;
      }
      .whead__alldone {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: var(--radius-md);
        border: 1.5px solid var(--c-blue-medium);
        background: var(--app-bg-recessed);
        box-sizing: border-box;
      }
      .whead__alldone--done {
        border-color: var(--app-primary-action);
        background: var(--app-primary-action);
      }
      .progress {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .progress__bar {
        flex: 1;
        min-width: 0;
      }
      .toggles {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        flex-wrap: wrap;
      }
      /* Header table 5/2/2/2/2 aligné sur les rows (refonte Android iter 2). */
      .thead {
        display: grid;
        grid-template-columns: minmax(0, 5fr) minmax(0, 2fr) minmax(0, 2fr) minmax(0, 2fr) minmax(0, 2fr);
        align-items: center;
      }
      .thead__cell {
        text-align: center;
        color: var(--app-text-secondary);
        font-size: var(--font-size-caption);
      }
      .thead__cell--muscle {
        text-align: left;
        padding-left: 14px;
      }
      .list {
        display: flex;
        flex-direction: column;
      }
      /* Cards GROUP/ZONE : bordure + fond teintés zone, label flottant (miroir ZoneGoalsCard). */
      .cards {
        display: flex;
        flex-direction: column;
        gap: var(--space-5);
        padding-top: 8px;
      }
      .card {
        position: relative;
        border: 1px solid;
        border-radius: var(--radius-lg);
        padding: 14px 6px 6px;
      }
      .card__label {
        position: absolute;
        top: -10px;
        left: 12px;
        padding: 1px 8px;
        background: var(--app-bg-screen);
        border: 1px solid;
        border-radius: var(--radius-md);
        font-size: 11px;
        font-weight: 600;
      }
      .card__rows {
        display: flex;
        flex-direction: column;
      }
      /* Semaine vide (miroir EmptyGoalsWeekState). */
      .empty {
        display: flex;
        flex-direction: column;
        gap: 10px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-lg);
        padding: var(--space-4);
      }
      .empty__title {
        color: var(--app-text-primary);
        font-size: 16px;
        font-weight: 600;
      }
      .empty__hint {
        color: var(--app-text-tertiary);
        font-size: 13px;
      }
      .empty__copy {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-2);
        height: 40px;
        background: var(--app-bg-button);
        border: none;
        border-radius: var(--radius-md);
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        cursor: pointer;
      }
      /* Sélecteur de priorité dans le dialog d'ajout (miroir PriorityPicker). */
      .prio-pick__label {
        display: block;
        font-size: var(--font-size-caption);
        color: var(--app-text-primary);
        padding: 0 0 var(--space-1) var(--space-1);
      }
      .prio-pick__row {
        display: flex;
        gap: var(--space-2);
      }
      .prio-pick__btn {
        flex: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        height: 36px;
        background: transparent;
        border: 1px solid;
        border-radius: var(--radius-md);
        font-family: var(--font-family-base);
        font-size: 13px;
        cursor: pointer;
        opacity: 0.55;
      }
      .prio-pick__btn--sel {
        opacity: 1;
        background: var(--app-bg-recessed);
      }
      /* Rows du picker d'objectif (miroir TargetPickerDialog presets). */
      .tgt__row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        width: 100%;
        box-sizing: border-box;
        background: var(--app-bg-recessed);
        border: none;
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        cursor: pointer;
      }
      .tgt__row--sel {
        background: color-mix(in srgb, var(--app-primary-action) 10%, transparent);
        color: var(--app-primary-action);
      }
    `,
  ],
})
export class GoalsPage {
  /** Mode embarqué (hub Home) : masque la title bar (le hub fournit les onglets). */
  readonly embedded = input(false);

  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  // ─── État UI ───
  protected readonly weekOffset = signal(0);
  protected readonly viewMode = signal<ViewMode>('MUSCLE');
  protected readonly sortMode = signal<SortMode>('ALPHA');

  protected readonly showAdd = signal(false);
  protected readonly addMuscleName = signal<string | null>(null);
  protected readonly addTarget = signal<string | null>(null);
  protected readonly addPriority = signal('LOW');
  protected readonly optionsFor = signal<GoalVm | null>(null);
  protected readonly statusFor = signal<GoalVm | null>(null);
  protected readonly priorityFor = signal<GoalVm | null>(null);
  protected readonly targetFor = signal<GoalVm | null>(null);
  protected readonly toDelete = signal<GoalVm | null>(null);
  protected readonly showSyncConfirm = signal(false);
  protected readonly showResetConfirm = signal(false);

  protected readonly targetOptions = TARGET_OPTIONS;

  protected readonly viewModeItems: SegmentItem[] = [
    { value: 'MUSCLE', icon: 'view_list', description: 'Vue par muscle (liste plate)' },
    { value: 'GROUP', icon: 'layers', description: 'Vue par groupe musculaire' },
    { value: 'ZONE', icon: 'grid_view', description: 'Vue par zone' },
  ];
  protected readonly sortItems: SegmentItem[] = [
    { value: 'ALPHA', icon: 'sort_by_alpha', description: 'Trier par ordre alphabétique' },
    { value: 'PALETTE', icon: 'palette', description: 'Trier par couleur de zone' },
    { value: 'PERCENT_DESC', icon: 'trending_down', description: 'Trier par % de réalisation (haut en premier)' },
    { value: 'PERCENT_ASC', icon: 'trending_up', description: 'Trier par % de réalisation (bas en premier)' },
    { value: 'PRIORITY', icon: 'priority_high', description: 'Trier par priorité' },
  ];
  protected readonly statusOptions: StatusOption[] = [
    { value: 'IN_PROGRESS', label: 'En cours', icon: 'arrow_circle_up', color: 'var(--c-orange-medium)' },
    { value: 'DONE', label: 'Fait', icon: 'check', color: 'var(--c-medium-green)' },
    { value: 'SKIPPED', label: 'Passé', icon: 'close', color: 'var(--c-red-medium)' },
  ];
  protected readonly priorityOptions: StatusOption[] = [
    { value: 'HIGH', label: 'Haute', icon: 'north', color: 'var(--app-priority-high)' },
    { value: 'MEDIUM', label: 'Moyenne', icon: 'north_east', color: 'var(--app-priority-medium)' },
    { value: 'LOW', label: 'Basse', icon: 'south', color: 'var(--app-priority-low)' },
  ];

  // ─── Données Dexie ───
  private readonly allGoals = toSignal(
    from(liveQuery(() => this.db.muscle_goals.filter((g) => !g.pendingDeletion).toArray())),
    { initialValue: [] as LocalMuscleGoal[] },
  );
  private readonly muscles = toSignal(from(liveQuery(() => this.db.muscles.toArray())), {
    initialValue: [] as LocalMuscle[],
  });
  private readonly workouts = toSignal(from(liveQuery(() => this.db.actual_workouts.toArray())), {
    initialValue: [] as LocalActualWorkout[],
  });
  private readonly awExercises = toSignal(
    from(liveQuery(() => this.db.actual_workout_exercises.toArray())),
    { initialValue: [] as LocalActualWorkoutExercise[] },
  );
  private readonly sets = toSignal(from(liveQuery(() => this.db.actual_workout_sets.toArray())), {
    initialValue: [] as LocalActualWorkoutSet[],
  });
  private readonly exMuscles = toSignal(from(liveQuery(() => this.db.exercise_muscles.toArray())), {
    initialValue: [] as LocalExerciseMuscle[],
  });

  constructor() {
    void this.sync.syncAll().catch(() => undefined);

    // ── Auto-complétion (miroir autoCompleteFinishedGoals) : les goals dont la cible est
    // atteinte passent DONE automatiquement. S'éteint seul (status DONE → plus éligible).
    effect(() => {
      const eligible = this.enriched().filter((g) => this.shouldAutoComplete(g));
      if (eligible.length === 0) return;
      void (async () => {
        const now = new Date().toISOString();
        for (const g of eligible) {
          await this.db.muscle_goals.update(g.goal.uuid, { status: 'DONE', synced: false, updatedAt: now });
        }
        await this.sync.syncAll().catch(() => undefined);
      })();
    });
  }

  // ─── Semaine sélectionnée (miroir CustomDateUtils : ISO week %G-W%V, lundi → dimanche) ───
  protected readonly week = computed(() => {
    const offset = this.weekOffset();
    const today = new Date();
    const monday = new Date(
      today.getFullYear(),
      today.getMonth(),
      today.getDate() - ((today.getDay() + 6) % 7) + offset * 7,
    );
    const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
    const { year, week } = this.isoWeek(monday);
    const iso = `${year.toString().padStart(4, '0')}-W${week.toString().padStart(2, '0')}`;
    const fmt = (d: Date): string =>
      `${d.getDate().toString().padStart(2, '0')}/${(d.getMonth() + 1).toString().padStart(2, '0')}`;
    return {
      iso,
      startIso: this.toIso(monday),
      endIso: this.toIso(sunday),
      label: `${fmt(monday)} au ${fmt(sunday)}`,
    };
  });

  /** Semaine ISO 8601 (jeudi pivot) — sémantique WeekFields.ISO / %G-W%V. */
  private isoWeek(d: Date): { year: number; week: number } {
    const t = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    t.setDate(t.getDate() + 3 - ((t.getDay() + 6) % 7));
    const year = t.getFullYear();
    const jan1 = new Date(year, 0, 1);
    const week = Math.ceil(((t.getTime() - jan1.getTime()) / 86400000 + 1) / 7);
    return { year, week };
  }

  private toIso(d: Date): string {
    const pad = (n: number): string => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  // ─── Goals de la semaine + `done` recalculé client (miroir muscleDoneCount Android) ───
  protected readonly weekGoals = computed(() =>
    this.allGoals().filter((g) => g.weekISO === this.week().iso),
  );

  /** Sets validés par muscle sur la semaine : reps ≥ reps attendues OU status DONE, par lien. */
  private readonly doneByMuscle = computed(() => {
    const { startIso, endIso } = this.week();
    const awMap = new Map(this.workouts().filter((w) => !w.pendingDeletion).map((w) => [w.uuid, w]));
    // Exercices de séance de la semaine (date du parent dans [lundi..dimanche]).
    const weekExercises = this.awExercises().filter((e) => {
      if (e.pendingDeletion) return false;
      const aw = awMap.get(e.actualWorkoutUUID);
      if (!aw || !aw.date) return false;
      const day = aw.date.slice(0, 10);
      return day >= startIso && day <= endIso;
    });
    const exercisesByExercise = new Map<string, LocalActualWorkoutExercise[]>();
    for (const e of weekExercises) {
      const arr = exercisesByExercise.get(e.exerciseUUID);
      if (arr) arr.push(e);
      else exercisesByExercise.set(e.exerciseUUID, [e]);
    }
    const setsByAwe = new Map<string, LocalActualWorkoutSet[]>();
    for (const s of this.sets()) {
      if (s.pendingDeletion) continue;
      const arr = setsByAwe.get(s.actualWorkoutExerciseUUID);
      if (arr) arr.push(s);
      else setsByAwe.set(s.actualWorkoutExerciseUUID, [s]);
    }
    const out = new Map<string, number>();
    for (const link of this.exMuscles()) {
      if (link.pendingDeletion) continue;
      const matching = exercisesByExercise.get(link.exerciseUUID);
      if (!matching) continue;
      let total = 0;
      for (const ex of matching) {
        const expectedReps = parseInt(ex.reps.split('-')[0], 10) || 0;
        for (const s of setsByAwe.get(ex.uuid) ?? []) {
          if (s.reps >= expectedReps || normalizeStatus(s.status) === 'DONE') total++;
        }
      }
      if (total > 0) out.set(link.muscleUUID, (out.get(link.muscleUUID) ?? 0) + total);
    }
    return out;
  });

  /** Goals enrichis : done calculé + % cap-free ; target invalide / muscle introuvable exclus du %. */
  private readonly enriched = computed<GoalVm[]>(() => {
    const muscleByUuid = new Map(this.muscles().map((m) => [m.uuid, m]));
    const doneMap = this.doneByMuscle();
    return this.weekGoals().flatMap((goal) => {
      const muscle = muscleByUuid.get(goal.muscleUUID);
      if (!muscle) return [];
      const targetMin = parseTargetMinimum(goal.target);
      if (targetMin <= 0) return [];
      const done = doneMap.get(goal.muscleUUID) ?? 0;
      return [{
        goal,
        done,
        muscleName: muscle.name,
        group: muscle.muscleGroup ?? null,
        zone: muscle.zone ?? null,
        targetMin,
        percent: targetMin >= Number.MAX_SAFE_INTEGER ? 0 : (done * 100) / targetMin,
        skipped: normalizeStatus(goal.status) === 'SKIPPED',
      }];
    });
  });

  protected readonly allSynced = computed(() => this.weekGoals().every((g) => g.synced));
  protected readonly allDone = computed(
    () => this.weekGoals().length > 0 && this.weekGoals().every((g) => normalizeStatus(g.status) === 'DONE'),
  );

  /**
   * Progression globale (miroir calculateGoalProgress : DONE compté à 100 %). Cible nulle ou données
   * non chargées (aucun goal contributif) → 0 : évite le flash 100% au 1er paint (jamais x/0 borné à 1).
   */
  protected readonly overallProgress = computed(() => {
    const goals = this.enriched();
    if (goals.length === 0) return 0;
    let totalDone = 0;
    let totalTarget = 0;
    for (const g of goals) {
      if (g.targetMin >= Number.MAX_SAFE_INTEGER) continue;
      totalDone += normalizeStatus(g.goal.status) === 'DONE' ? g.targetMin : g.done;
      totalTarget += g.targetMin;
    }
    return totalTarget > 0 ? totalDone / totalTarget : 0;
  });

  // ─── Palette par zone (spread 0.4 spécifique Goals : nuances proches de la couleur de zone) ───
  private readonly zoneColors = computed<Record<string, string>>(() => ({
    Chest: resolveCssColor('var(--app-primary-action)'),
    Back: resolveCssColor('var(--c-orange-medium)'),
    Shoulders: resolveCssColor('var(--app-accent-text)'),
    Arms: resolveCssColor('var(--c-red-medium)'),
    Legs: resolveCssColor('var(--c-medium-green)'),
    Core: resolveCssColor('var(--c-yellow-medium)'),
    Other: resolveCssColor('var(--c-medium-purple)'),
  }));

  private shadesByZone(keysWithZone: [string, string][]): Map<string, string> {
    const zoneColors = this.zoneColors();
    const byZone = new Map<string, string[]>();
    for (const [key, zone] of keysWithZone) {
      const arr = byZone.get(zone);
      if (arr) { if (!arr.includes(key)) arr.push(key); }
      else byZone.set(zone, [key]);
    }
    const out = new Map<string, string>();
    for (const [zone, keys] of byZone) {
      keys.sort();
      const shades = paletteForZone(zoneColors[zone] ?? zoneColors['Other'], keys.length, 0.4);
      keys.forEach((k, i) => out.set(k, shades[i]));
    }
    return out;
  }

  private readonly groupColorMap = computed(() =>
    this.shadesByZone(
      this.muscles()
        .filter((m) => m.muscleGroup && m.zone)
        .map((m) => [m.muscleGroup!, m.zone!] as [string, string]),
    ),
  );
  private readonly muscleColorMap = computed(() =>
    this.shadesByZone(this.muscles().filter((m) => m.zone).map((m) => [m.name, m.zone!] as [string, string])),
  );

  private colorForKey(mode: ViewMode, key: string, zone: string | null): string {
    if (mode === 'ZONE') return this.zoneColors()[key] ?? this.zoneColors()['Other'];
    const map = mode === 'GROUP' ? this.groupColorMap() : this.muscleColorMap();
    return map.get(key) ?? this.zoneColors()[zone ?? 'Other'] ?? this.zoneColors()['Other'];
  }

  // ─── Tris (miroir sortGoalsList / sortGroupedKeys — 5 modes) ───
  private sortList(goals: GoalVm[]): GoalVm[] {
    const byName = (a: GoalVm, b: GoalVm): number =>
      a.muscleName.toLowerCase().localeCompare(b.muscleName.toLowerCase());
    switch (this.sortMode()) {
      case 'ALPHA':
        return [...goals].sort(byName);
      case 'PALETTE':
        return [...goals].sort((a, b) => {
          const ia = this.zoneIdx(a.zone);
          const ib = this.zoneIdx(b.zone);
          return ia !== ib ? ia - ib : byName(a, b);
        });
      case 'PERCENT_DESC':
        return [...goals].sort((a, b) => b.percent - a.percent);
      case 'PERCENT_ASC':
        return [...goals].sort((a, b) => a.percent - b.percent);
      case 'PRIORITY':
        return [...goals].sort((a, b) => {
          const pa = PRIORITY_ORDER[a.goal.priority.toUpperCase()] ?? Number.MAX_SAFE_INTEGER;
          const pb = PRIORITY_ORDER[b.goal.priority.toUpperCase()] ?? Number.MAX_SAFE_INTEGER;
          return pa !== pb ? pa - pb : byName(a, b);
        });
    }
  }

  private zoneIdx(zone: string | null): number {
    const i = ZONES_ALL.indexOf(zone ?? '');
    return i < 0 ? Number.MAX_SAFE_INTEGER : i;
  }

  /** % agrégé d'un groupe : sum(done) / sum(targetMin) (miroir aggregatePercent). */
  private aggregatePercent(goals: GoalVm[]): number {
    const totalDone = goals.reduce((a, g) => a + g.done, 0);
    const totalTarget = goals.reduce(
      (a, g) => a + (g.targetMin >= Number.MAX_SAFE_INTEGER ? 0 : g.targetMin),
      0,
    );
    return totalTarget > 0 ? (totalDone * 100) / totalTarget : 0;
  }

  /** Groupes triés selon le sort mode ; items intra-groupe triés alpha (lisibilité intra-card). */
  private sortGrouped(grouped: Map<string, GoalVm[]>, keyToZone: (k: string) => string | null): GroupVm[] {
    let entries = [...grouped.entries()];
    const byKey = (a: [string, GoalVm[]], b: [string, GoalVm[]]): number =>
      a[0].toLowerCase().localeCompare(b[0].toLowerCase());
    switch (this.sortMode()) {
      case 'ALPHA':
        entries = entries.sort(byKey);
        break;
      case 'PALETTE':
        entries = entries.sort((a, b) => {
          const ia = this.zoneIdx(keyToZone(a[0]));
          const ib = this.zoneIdx(keyToZone(b[0]));
          return ia !== ib ? ia - ib : byKey(a, b);
        });
        break;
      case 'PERCENT_DESC':
        entries = entries.sort((a, b) => this.aggregatePercent(b[1]) - this.aggregatePercent(a[1]));
        break;
      case 'PERCENT_ASC':
        entries = entries.sort((a, b) => this.aggregatePercent(a[1]) - this.aggregatePercent(b[1]));
        break;
      case 'PRIORITY':
        entries = entries.sort((a, b) => {
          const min = (gs: GoalVm[]): number =>
            Math.min(...gs.map((g) => PRIORITY_ORDER[g.goal.priority.toUpperCase()] ?? Number.MAX_SAFE_INTEGER));
          const pa = min(a[1]);
          const pb = min(b[1]);
          return pa !== pb ? pa - pb : byKey(a, b);
        });
        break;
    }
    const mode = this.viewMode();
    return entries.map(([key, goals]) => ({
      key,
      color: this.colorForKey(mode, key, keyToZone(key)),
      goals: [...goals].sort((a, b) => a.muscleName.toLowerCase().localeCompare(b.muscleName.toLowerCase())),
    }));
  }

  protected readonly sortedList = computed(() => this.sortList(this.enriched()));

  protected readonly groupedCards = computed<GroupVm[]>(() => {
    const goals = this.enriched();
    const mode = this.viewMode();
    if (mode === 'MUSCLE') return [];
    const grouped = new Map<string, GoalVm[]>();
    for (const g of goals) {
      const key = (mode === 'GROUP' ? g.group : g.zone) ?? 'Other';
      const arr = grouped.get(key);
      if (arr) arr.push(g);
      else grouped.set(key, [g]);
    }
    const keyToZone =
      mode === 'ZONE'
        ? (k: string): string | null => k
        : (k: string): string | null => goals.find((g) => g.group === k)?.zone ?? null;
    return this.sortGrouped(grouped, keyToZone);
  });

  // ─── Chart footer (miroir chartData : 1 barre par muscle/groupe/zone selon le mode) ───
  protected readonly chartBars = computed<GoalBar[]>(() => {
    const mode = this.viewMode();
    if (mode === 'MUSCLE') {
      return this.sortedList().map((g) => ({
        label: g.muscleName,
        value: Math.round(g.percent),
        color: this.colorForKey('MUSCLE', g.muscleName, g.zone),
        skipped: g.skipped,
      }));
    }
    return this.groupedCards().map((grp) => ({
      label: grp.key,
      value: Math.round(this.aggregatePercent(grp.goals)),
      color: grp.color,
      skipped: grp.goals.length > 0 && grp.goals.every((g) => g.skipped),
    }));
  });

  // ─── Dialog d'ajout ───
  protected readonly muscleNames = computed(() =>
    this.muscles()
      .filter((m) => !m.pendingDeletion)
      .map((m) => m.name)
      .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase())),
  );

  private readonly addIsDuplicate = computed(() => {
    const name = this.addMuscleName();
    if (!name) return false;
    const uuid = this.muscles().find((m) => m.name === name)?.uuid;
    return uuid !== undefined && this.weekGoals().some((g) => g.muscleUUID === uuid);
  });

  protected readonly canAdd = computed(
    () => !!this.addMuscleName() && !!this.addTarget() && !this.addIsDuplicate(),
  );
  protected readonly addDisabledReason = computed(() => {
    if (!this.addMuscleName()) return 'Choisis un muscle';
    if (this.addIsDuplicate()) return 'Cet objectif existe déjà cette semaine';
    if (!this.addTarget()) return 'Choisis un objectif';
    return '';
  });

  /** Valeur du number picker du dialog objectif (target courante si numérique, sinon 12). */
  protected readonly customTargetValue = computed(() => {
    const t = this.targetFor()?.goal?.target ?? '';
    const v = parseInt(t, 10);
    return Number.isNaN(v) || `${v}` !== t ? 12 : v;
  });

  protected readonly statusForSelected = computed(() => normalizeStatus(this.statusFor()?.goal?.status));

  /** Actions du sheet d'un goal (miroir MuscleOptionsBottomSheet, garde auto-complétion incluse). */
  protected readonly goalActions = computed<SheetAction[]>(() => {
    const vm = this.optionsFor();
    if (!vm) return [];
    const isDone = normalizeStatus(vm.goal.status) === 'DONE';
    const actions: SheetAction[] = [
      { label: 'Changer le statut', icon: 'edit', color: 'var(--c-blue-medium)' },
    ];
    // Si DONE avec cible atteinte, repasser « en cours » re-déclencherait l'auto-complétion
    // immédiatement (miroir canManuallyToggleProgress Android) → action masquée.
    if (!(isDone && vm.done >= vm.targetMin)) {
      actions.push(
        isDone
          ? { label: 'Repasser en cours', icon: 'arrow_circle_up', color: 'var(--c-orange-medium)' }
          : { label: 'Marquer comme fait', icon: 'check', color: 'var(--c-medium-green)' },
      );
    }
    actions.push({ label: 'Supprimer', icon: 'delete', color: 'var(--c-red-medium)' });
    return actions;
  });

  // ─── Handlers ───
  protected setViewMode(v: string): void {
    this.viewMode.set(v as ViewMode);
  }
  protected setSortMode(v: string): void {
    this.sortMode.set(v as SortMode);
  }
  protected changeWeek(delta: number): void {
    this.weekOffset.update((o) => o + delta);
  }
  protected onRangeClick(): void {
    if (this.weekOffset() !== 0) this.showResetConfirm.set(true);
  }
  protected confirmReset(): void {
    this.weekOffset.set(0);
    this.showResetConfirm.set(false);
  }
  protected confirmSync(): void {
    this.showSyncConfirm.set(false);
    void this.sync.syncAll().catch(() => undefined);
  }

  protected mix(color: string, pct: number): string {
    return `color-mix(in srgb, ${color} ${pct}%, transparent)`;
  }

  /** Ouvre directement le dialog d'ajout d'un muscle (form réinitialisé). */
  protected openAdd(): void {
    this.addMuscleName.set(null);
    this.addTarget.set(null);
    this.addPriority.set('LOW');
    this.showAdd.set(true);
  }

  protected onGoalAction(label: string): void {
    const vm = this.optionsFor();
    this.optionsFor.set(null);
    if (!vm) return;
    if (label === 'Changer le statut') this.statusFor.set(vm);
    else if (label === 'Marquer comme fait') void this.updateStatus(vm.goal.uuid, 'DONE');
    else if (label === 'Repasser en cours') void this.updateStatus(vm.goal.uuid, 'IN_PROGRESS');
    else if (label === 'Supprimer') this.toDelete.set(vm);
  }

  protected async confirmAdd(): Promise<void> {
    const name = this.addMuscleName();
    const target = this.addTarget();
    const uuid = name ? this.muscles().find((m) => m.name === name)?.uuid : undefined;
    if (!name || !target || !uuid) return;
    const row: LocalMuscleGoal = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      muscleUUID: uuid,
      priority: this.addPriority(),
      done: 0,
      target,
      weekISO: this.week().iso,
      status: 'IN_PROGRESS',
      addedManually: true,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.muscle_goals.put(row);
    this.showAdd.set(false);
    void this.sync.syncAll().catch(() => undefined);
  }

  protected confirmStatus(status: string): void {
    const vm = this.statusFor();
    this.statusFor.set(null);
    if (!vm) return;
    void this.updateStatus(vm.goal.uuid, status);
  }

  protected confirmPriority(priority: string): void {
    const vm = this.priorityFor();
    this.priorityFor.set(null);
    if (!vm) return;
    void this.db.muscle_goals
      .update(vm.goal.uuid, { priority, synced: false, updatedAt: new Date().toISOString() })
      .then(() => this.sync.syncAll().catch(() => undefined));
  }

  /** Change l'objectif + re-évalue DONE/IN_PROGRESS sur le done courant (miroir updateMuscleGoalTarget). */
  protected async applyTarget(target: string): Promise<void> {
    const vm = this.targetFor();
    this.targetFor.set(null);
    if (!vm) return;
    const now = new Date().toISOString();
    await this.db.muscle_goals.update(vm.goal.uuid, { target, synced: false, updatedAt: now });
    const status = normalizeStatus(vm.goal.status);
    if (status !== 'SKIPPED') {
      const shouldBeDone = vm.done >= parseTargetMinimum(target);
      if (shouldBeDone && status !== 'DONE') {
        await this.db.muscle_goals.update(vm.goal.uuid, { status: 'DONE', synced: false, updatedAt: now });
      } else if (!shouldBeDone && status === 'DONE') {
        await this.db.muscle_goals.update(vm.goal.uuid, { status: 'IN_PROGRESS', synced: false, updatedAt: now });
      }
    }
    void this.sync.syncAll().catch(() => undefined);
  }

  protected confirmDelete(): void {
    const vm = this.toDelete();
    this.toDelete.set(null);
    if (!vm) return;
    void this.db.muscle_goals
      .update(vm.goal.uuid, { pendingDeletion: true, synced: false, updatedAt: new Date().toISOString() })
      .then(() => this.sync.syncAll().catch(() => undefined));
  }

  /** Copie les objectifs de la semaine précédente (semaine vide → tout ; sinon muscles absents). */
  protected async copyFromLastWeek(): Promise<void> {
    const thisIso = this.week().iso;
    const offset = this.weekOffset();
    // Semaine précédente : même calcul, offset - 1.
    const today = new Date();
    const prevMonday = new Date(
      today.getFullYear(),
      today.getMonth(),
      today.getDate() - ((today.getDay() + 6) % 7) + (offset - 1) * 7,
    );
    const { year, week } = this.isoWeek(prevMonday);
    const prevIso = `${year.toString().padStart(4, '0')}-W${week.toString().padStart(2, '0')}`;

    const all = await this.db.muscle_goals.filter((g) => !g.pendingDeletion).toArray();
    const current = all.filter((g) => g.weekISO === thisIso);
    const existing = new Set(current.map((g) => g.muscleUUID));
    const previous = all.filter((g) => g.weekISO === prevIso);
    const toCopy = current.length === 0 ? previous : previous.filter((g) => !existing.has(g.muscleUUID));
    if (toCopy.length === 0) return;

    const now = new Date().toISOString();
    await this.db.muscle_goals.bulkPut(
      toCopy.map((g) => ({
        ...g,
        uuid: uuidv4(),
        weekISO: thisIso,
        done: 0,
        status: 'NOT_STARTED',
        addedManually: true,
        synced: false,
        pendingDeletion: false,
        updatedAt: now,
      })),
    );
    void this.sync.syncAll().catch(() => undefined);
  }

  // ─── Internes ───
  private shouldAutoComplete(g: GoalVm): boolean {
    const status = normalizeStatus(g.goal.status);
    if (g.goal.pendingDeletion) return false;
    if (status === 'DONE' || status === 'SKIPPED') return false;
    return g.done >= g.targetMin;
  }

  private async updateStatus(uuid: string, status: string): Promise<void> {
    await this.db.muscle_goals.update(uuid, { status, synced: false, updatedAt: new Date().toISOString() });
    void this.sync.syncAll().catch(() => undefined);
  }
}
