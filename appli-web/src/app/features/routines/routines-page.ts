import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { LocalRoutinePeriod } from '@core/models/routine-period.model';
import { LocalTask } from '@core/models/task.model';
import { LocalTaskCheck } from '@core/models/task-check.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ProgressBarPrimitive } from '@designsystem/common_components/progress-bar-primitive';
import { progressColor } from '@designsystem/common_components/labeled-progress-bar';
import { EntityListRow } from '@designsystem/common_components/entity-list-row';
import { CustomCheckbox } from '@designsystem/common_components/custom-checkbox';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomSelect } from '@designsystem/common_components/custom-select';
import { TimeRangePickerBar } from '@designsystem/common_components/time-range-picker-bar';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { AppIcon } from '@designsystem/icons/app-icon';
import { TasksAgenda } from './tasks-agenda';
import { occursOn } from './task-occurrence';

/**
 * Écran Routines — miroir flat de TasksScreen.kt + RoutineTasksScreen.kt. Les onglets Android
 * Quotidien | Agenda sont affichés ici côte à côte (2 colonnes : jour à gauche, mois à droite,
 * empilées sur écran étroit) — le web a la place. Colonne Quotidien :
 * DateNavBar (◀ date ▶, retour à aujourd'hui au clic) + barre d'avancement (RoutineTasksProgressBar :
 * barre + % + nuage sync + X/Y + ✓ + bouton +) + sections par période (header TitledDivider cliquable
 * "Nom • HH:MM-HH:MM" → sheet Modifier/Supprimer) avec rows de tâches (RoutineTaskRow : handle/icône à
 * gauche, nom cliquable → sheet, nuage sync + checkbox à droite ; non-DAILY teintées vert W/M/Y /
 * orange NONE) + section "Autres tâches aujourd'hui" (non-DAILY sans période assignable).
 * Toggle d'une checkbox = création/màj d'un task_check (occurrence_date = jour sélectionné).
 * CRUD local optimiste (Dexie) + sync best-effort, comme les autres pages.
 *
 * Colonne Agenda : composant dédié TasksAgenda (miroir TasksCalendarScreen.kt).
 *
 * Différé vs Android : drag & drop de réordonnancement des tâches DAILY ; édition complète des tâches
 * non-DAILY (récurrence W/M/Y, "seulement cette occurrence") depuis CET onglet (disponible dans
 * l'Agenda) ; rappels de période (champs absents du modèle web).
 */

interface TaskRowUi {
  task: LocalTask;
  isChecked: boolean;
  isCheckSynced: boolean;
}

interface PeriodSection {
  period: LocalRoutinePeriod;
  rows: TaskRowUi[];
}

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function shiftIsoDay(dateIso: string, delta: number): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  const shifted = new Date(y, m - 1, d + delta);
  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}-${String(shifted.getDate()).padStart(2, '0')}`;
}

function hhmmToMinutes(hhmm: string): number | null {
  const [hh, mm] = hhmm.split(':').map(Number);
  if (Number.isNaN(hh) || Number.isNaN(mm) || hh < 0 || hh > 23 || mm < 0 || mm > 59) return null;
  return hh * 60 + mm;
}

function minutesToHHmm(m: number): string {
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

@Component({
  selector: 'app-routines-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet,
    ScreenTitleBar,
    TitledDivider,
    ActionIconButton,
    ProgressBarPrimitive,
    EntityListRow,
    CustomCheckbox,
    EmptyListRow,
    FormDialog,
    ConfirmationDialog,
    CustomTextField,
    CustomSelect,
    TimeRangePickerBar,
    OptionsBottomSheet,
    AppIcon,
    TasksAgenda,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Routines" />

      <div class="page__body page__body--columns">
        <!-- Colonne gauche : Quotidien (jour sélectionné) -->
        <div class="col">
          <app-titled-divider title="Quotidien" />
          <!-- DateNavBar (◀ date ▶) -->
          <div class="datenav">
            <app-action-icon-button icon="chevron_left" (clicked)="prevDay()" />
            <button
              type="button"
              class="datenav__date"
              [style.color]="isToday() ? 'var(--app-primary-action)' : 'var(--app-text-tertiary)'"
              (click)="goToday()"
            >
              {{ selectedDate() }}
            </button>
            <app-action-icon-button
              icon="chevron_right"
              [disabled]="isToday()"
              [tint]="isToday() ? 'var(--app-divider)' : 'var(--app-text-primary)'"
              (clicked)="nextDay()"
            />
          </div>

          <app-titled-divider title="Avancement" />

          <!-- RoutineTasksProgressBar : barre + % + sync + X/Y + ✓ + bouton + -->
          <div class="progress">
            <app-progress-bar-primitive
              class="progress__bar"
              [progress]="progress()"
              [color]="progressBarColor()"
              troughColor="var(--app-bg-surface)"
            />
            <span class="progress__pct" [style.color]="progressBarColor()">{{ percent() }}%</span>
            <app-action-icon-button
              [icon]="isSync() ? 'cloud_done' : 'cloud_off'"
              [tint]="isSync() ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
              [hasBackground]="false"
              (clicked)="showSyncConfirm.set(true)"
            />
            <span class="progress__count">{{ doneCount() }}/{{ totalCount() }}</span>
            <app-icon name="check" [size]="24" color="var(--c-medium-green)" />
            <app-action-icon-button icon="add" (clicked)="showAddSheet.set(true)" />
          </div>

          <!-- Sections par période : grille responsive (mur de périodes, 1 colonne sur écran étroit) -->
          <!-- Drag & drop (mirror Android RoutineTasksScreen) : poignée 6 points des tâches DAILY
               draggable ; drop sur le header = début de période (moveTaskToPeriodTop), sur une row
               DAILY = juste après elle (moveTaskAfterAnchor), sur la zone pointillée de fin (visible
               pendant le drag) = fin de période (moveTaskToPeriodEnd). Cross-période OK. -->
          <div class="period-grid">
            @for (section of sections(); track section.period.uuid) {
              <div class="period-section">
                <button
                  type="button"
                  class="period-header"
                  [class.period-header--drop]="dropTarget() === 'top_' + section.period.uuid"
                  (click)="periodForOptions.set(section.period)"
                  (dragover)="onDragOver($event, 'top_' + section.period.uuid)"
                  (dragleave)="onDragLeave('top_' + section.period.uuid)"
                  (drop)="onDropTop($event, section.period.uuid)"
                >
                  <span class="period-header__line"></span>
                  <span class="period-header__title">{{ periodLabel(section.period) }}</span>
                  <span class="period-header__line"></span>
                </button>

                @if (section.rows.length === 0) {
                  <app-empty-list-row text="Aucune tâche pour cette période" icon="check_circle" />
                } @else {
                  <div class="rows">
                    @for (row of section.rows; track row.task.uuid) {
                      <div
                        class="drag-wrap"
                        [class.drag-wrap--after]="isDaily(row.task) && dropTarget() === 'after_' + row.task.uuid"
                        [class.drag-wrap--dragging]="draggingUuid() === row.task.uuid"
                        (dragover)="isDaily(row.task) && onDragOver($event, 'after_' + row.task.uuid)"
                        (dragleave)="onDragLeave('after_' + row.task.uuid)"
                        (drop)="isDaily(row.task) && onDropAfter($event, row.task.uuid, section.period.uuid)"
                      >
                        <ng-container *ngTemplateOutlet="taskRow; context: { $implicit: row }" />
                      </div>
                    }
                  </div>
                }

                <!-- Zone de drop "fin de période" : visible uniquement pendant un drag -->
                @if (draggingUuid() !== null) {
                  <div
                    class="drop-end"
                    [class.drop-end--active]="dropTarget() === 'end_' + section.period.uuid"
                    (dragover)="onDragOver($event, 'end_' + section.period.uuid)"
                    (dragleave)="onDragLeave('end_' + section.period.uuid)"
                    (drop)="onDropEnd($event, section.period.uuid)"
                  ></div>
                }
              </div>
            }
          </div>

          @if (sections().length === 0) {
            <app-empty-list-row text="Aucune période. Clique sur + pour en ajouter une." icon="schedule" />
          }

          <!-- Autres tâches aujourd'hui (non-DAILY sans période assignable) -->
          @if (otherTasks().length > 0) {
            <app-titled-divider title="Autres tâches aujourd'hui" />
            <div class="rows">
              @for (row of otherTasks(); track row.task.uuid) {
                <ng-container *ngTemplateOutlet="taskRow; context: { $implicit: row }" />
              }
            </div>
          }
        </div>

        <!-- Colonne droite : Agenda (mois) -->
        <div class="col">
          <app-titled-divider title="Agenda" />
          <app-tasks-agenda />
        </div>
      </div>

      <!-- Row de tâche (RoutineTaskRow) : handle/icône + nom + nuage sync + checkbox -->
      <ng-template #taskRow let-row>
        <app-entity-list-row
          [name]="row.task.title"
          [nameMaxLines]="1"
          [backgroundColor]="rowBg(row.task)"
          [nameBoxColor]="rowNameBg(row.task)"
          [isPendingDeletion]="row.task.pendingDeletion"
          [contentEndPadding]="8"
          (nameClick)="taskForOptions.set(row.task)"
        >
          <span
            leading
            class="row-leading"
            [class.row-leading--grab]="isDaily(row.task)"
            [attr.draggable]="isDaily(row.task) ? 'true' : null"
            (dragstart)="onDragStart($event, row.task)"
            (dragend)="onDragEnd()"
          >
            <app-icon [name]="leadingIcon(row.task)" [size]="22" [color]="leadingIconColor(row.task)" />
          </span>
          <span trailing class="row-trailing">
            <app-icon
              [name]="row.isCheckSynced ? 'cloud_done' : 'cloud_off'"
              [size]="20"
              [color]="row.isCheckSynced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
            />
            <span class="row-check">
              <app-custom-checkbox [checked]="row.isChecked" (checkedChange)="toggleTask(row.task, $event)" />
            </span>
          </span>
        </app-entity-list-row>
      </ng-template>

      <!-- Sheet "+" : Ajouter une tâche / Ajouter une période -->
      <app-options-bottom-sheet
        [open]="showAddSheet()"
        title="Ajouter"
        [actions]="addActions"
        (dismissRequest)="showAddSheet.set(false)"
        (actionSelected)="onAddAction($event)"
      />

      <!-- Sheet options tâche -->
      <app-options-bottom-sheet
        [open]="taskForOptions() !== null"
        [title]="taskForOptions()?.title ?? ''"
        [actions]="taskActions()"
        (dismissRequest)="taskForOptions.set(null)"
        (actionSelected)="onTaskAction($event)"
      />

      <!-- Sheet options période -->
      <app-options-bottom-sheet
        [open]="periodForOptions() !== null"
        [title]="periodForOptions()?.name ?? ''"
        [actions]="periodActions"
        (dismissRequest)="periodForOptions.set(null)"
        (actionSelected)="onPeriodAction($event)"
      />

      <!-- Dialog création/édition période -->
      <app-form-dialog
        [open]="showPeriodForm()"
        [title]="editingPeriod() ? 'Modifier la période' : 'Ajouter une période'"
        [confirmText]="editingPeriod() ? 'Enregistrer' : 'Ajouter'"
        [confirmEnabled]="periodFormValid()"
        [disabledReason]="periodFormError()"
        (confirm)="submitPeriodForm()"
        (dismiss)="showPeriodForm.set(false)"
      >
        <app-custom-text-field
          label="Nom"
          placeholder="Nom de la période…"
          [value]="periodName()"
          (valueChange)="periodName.set($event)"
        />
        <app-time-range-picker-bar
          label="Plage horaire"
          [startMinutes]="periodStart()"
          [endMinutes]="periodEnd()"
          [stepMinutes]="5"
          (rangeChange)="periodStart.set($event.start); periodEnd.set($event.end)"
        />
      </app-form-dialog>

      <!-- Dialog création/édition tâche (DAILY : titre + période) -->
      <app-form-dialog
        [open]="showTaskForm()"
        [title]="editingTask() ? 'Modifier la tâche' : 'Ajouter une tâche'"
        [confirmText]="editingTask() ? 'Enregistrer' : 'Ajouter'"
        [confirmEnabled]="taskFormValid()"
        [disabledReason]="taskFormError()"
        (confirm)="submitTaskForm()"
        (dismiss)="showTaskForm.set(false)"
      >
        <app-custom-text-field
          label="Titre"
          placeholder="ex. Étirements"
          [value]="taskTitle()"
          (valueChange)="taskTitle.set($event)"
        />
        <app-custom-select
          label="Période"
          [selected]="taskPeriodLabel()"
          [options]="periodOptions()"
          (select)="taskPeriodLabel.set($event)"
        />
      </app-form-dialog>

      <!-- Confirmations -->
      <app-confirmation-dialog
        [open]="showSyncConfirm()"
        title="Synchroniser la routine"
        message="Synchroniser tes tâches de routine maintenant ?"
        confirmButtonText="Synchroniser"
        dismissButtonText="Annuler"
        (confirm)="confirmSync()"
        (dismiss)="showSyncConfirm.set(false)"
      />
      <app-confirmation-dialog
        [open]="periodToDelete() !== null"
        title="Supprimer la période"
        message="Supprimer cette période ? Toutes les tâches qu'elle contient seront aussi supprimées. Action irréversible."
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        confirmButtonColor="var(--c-red-medium)"
        (confirm)="confirmDeletePeriod()"
        (dismiss)="periodToDelete.set(null)"
      />
      <app-confirmation-dialog
        [open]="taskToDelete() !== null"
        title="Supprimer la tâche"
        message="Supprimer cette tâche ? Action irréversible."
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        confirmButtonColor="var(--c-red-medium)"
        (confirm)="confirmDeleteTask()"
        (dismiss)="taskToDelete.set(null)"
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
      }
      /* 2 colonnes Quotidien | Agenda (empilées sur écran étroit). Gap horizontal =
         --page-gutter : le gap standard entre panneaux côte à côte (cf. _spacing.scss,
         même valeur que le master-detail de planning/session). */
      .page__body--columns {
        display: grid;
        grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
        gap: var(--space-3) var(--page-gutter);
        align-items: start;
      }
      @media (max-width: 1100px) {
        .page__body--columns {
          grid-template-columns: minmax(0, 1fr);
        }
      }
      .col {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        min-width: 0;
      }
      .datenav {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-3);
      }
      .datenav__date {
        font-family: var(--font-family-base);
        font-size: 14px;
        font-weight: 600;
        background: var(--app-bg-recessed);
        border: none;
        border-radius: var(--radius-md);
        padding: 8px 12px;
        cursor: pointer;
      }
      /* Container avancement (bgRecessed) — ordre Android : barre, %, sync, X/Y, ✓, + */
      .progress {
        display: flex;
        align-items: center;
        gap: 6px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
      }
      .progress__bar {
        flex: 1;
      }
      .progress__pct {
        font-size: 14px;
        font-weight: 600;
      }
      .progress__count {
        color: var(--app-text-secondary);
        font-size: 13px;
        font-weight: var(--font-weight-medium);
        white-space: nowrap;
      }
      /* Header de période = TitledDivider cliquable (RoutinePeriodHeaderDropItem) */
      .period-header {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        width: 100%;
        padding: 6px 0;
        background: transparent;
        border: none;
        cursor: pointer;
        font-family: var(--font-family-base);
      }
      .period-header__line {
        flex: 1;
        height: 1px;
        background: var(--app-divider);
      }
      .period-header__title {
        color: var(--app-divider);
        font-weight: 600;
        font-size: var(--font-size-body);
      }
      /* Grille de périodes responsive (même pattern que le mur de citations de quotes-page). */
      .period-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
        /* Gap généreux entre blocs de période (vertical en colonne, horizontal en grille). */
        gap: var(--space-5) var(--space-6);
        align-items: start;
      }
      .period-section {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .rows {
        display: flex;
        flex-direction: column;
      }
      /* — Drag & drop — */
      .period-header--drop .period-header__line {
        background: var(--app-primary-action);
      }
      .period-header--drop .period-header__title {
        color: var(--app-primary-action);
      }
      .drag-wrap {
        position: relative;
      }
      .drag-wrap--dragging {
        opacity: 0.45;
      }
      /* Ligne d'insertion "après cette row" (= GapDropZone actif Android). */
      .drag-wrap--after::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: -2px;
        height: 3px;
        border-radius: 2px;
        background: var(--app-primary-action);
        pointer-events: none;
      }
      /* Zone "fin de période", pointillée pendant le drag. */
      .drop-end {
        height: 26px;
        margin-top: 4px;
        border-radius: var(--radius-md);
        border: 1.5px dashed var(--app-divider);
        box-sizing: border-box;
      }
      .drop-end--active {
        border-color: var(--app-primary-action);
        background: color-mix(in srgb, var(--app-primary-action) 10%, transparent);
      }
      /* Zone icône de type (= drag handle box 40dp Android) : un peu plus large pour respirer. */
      .row-leading {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        flex-shrink: 0;
      }
      /* Poignée 6 points des tâches DAILY : draggable. */
      .row-leading--grab {
        cursor: grab;
      }
      .row-leading--grab:active {
        cursor: grabbing;
      }
      .row-trailing {
        display: inline-flex;
        align-items: center;
        gap: var(--space-3);
        /* Respiration entre le nom (boîte cliquable) et le nuage sync. */
        padding-left: var(--space-4);
        flex-shrink: 0;
      }
      /* Checkbox centrée dans une boîte 44px (= Box(44.dp) Android) : zone de clic + air à droite. */
      .row-check {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
      }
    `,
  ],
})
export class RoutinesPage {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly snackbar = inject(SnackbarService);

  protected readonly selectedDate = signal(todayIso());
  protected readonly isToday = computed(() => this.selectedDate() === todayIso());

  // — Données offline-first (Dexie liveQuery → signals) —
  protected readonly periods = toSignal(
    from(liveQuery(() => this.db.routine_periods.filter((p) => !p.pendingDeletion).toArray())),
    { initialValue: [] as LocalRoutinePeriod[] },
  );
  private readonly tasks = toSignal(
    from(liveQuery(() => this.db.tasks.filter((t) => !t.pendingDeletion && t.isActive).toArray())),
    { initialValue: [] as LocalTask[] },
  );
  private readonly checks = toSignal(
    from(liveQuery(() => this.db.task_checks.filter((c) => !c.pendingDeletion).toArray())),
    { initialValue: [] as LocalTaskCheck[] },
  );

  private readonly checksByDate = computed(() =>
    this.checks().filter((c) => c.occurrenceDate === this.selectedDate()),
  );

  /** Tâches visibles au jour sélectionné : DAILY toujours, non-DAILY si occurrence ce jour. */
  private readonly visibleTasks = computed(() => {
    const date = this.selectedDate();
    return this.tasks().filter((t) => t.recurrenceKind === 'DAILY' || occursOn(t, date));
  });

  /** Partition (mirror du VM Android) : sections par période + "autres" sans période assignable. */
  private readonly partition = computed(() => {
    const checksMap = new Map(this.checksByDate().map((c) => [c.taskUUID, c]));
    const sortedPeriods = [...this.periods()].sort((a, b) => a.startTime.localeCompare(b.startTime));
    const visible = this.visibleTasks();
    const nonDaily = visible.filter((t) => t.recurrenceKind !== 'DAILY');

    const periodOf = (t: LocalTask): LocalRoutinePeriod | null => {
      const due = t.dueTime;
      if (!due) return null;
      return sortedPeriods.find((p) => due >= p.startTime && due <= p.endTime) ?? null;
    };
    const assignment = new Map(nonDaily.map((t) => [t.uuid, periodOf(t)]));

    const toRow = (t: LocalTask): TaskRowUi => {
      const check = checksMap.get(t.uuid);
      return {
        task: t,
        isChecked: check?.isChecked === true,
        isCheckSynced: check ? t.synced && check.synced : t.synced,
      };
    };

    const sections: PeriodSection[] = sortedPeriods.map((period) => {
      // non-DAILY (planifiées à heure précise) en premier triées par dueTime, puis DAILY par order.
      const nonDailyIn = nonDaily
        .filter((t) => assignment.get(t.uuid)?.uuid === period.uuid)
        .sort((a, b) => (a.dueTime ?? '').localeCompare(b.dueTime ?? ''));
      const dailyIn = visible
        .filter((t) => t.recurrenceKind === 'DAILY' && t.periodUUID === period.uuid)
        .sort((a, b) => a.order - b.order);
      return { period, rows: [...nonDailyIn, ...dailyIn].map(toRow) };
    });

    const others = nonDaily
      .filter((t) => assignment.get(t.uuid) === null)
      .sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()))
      .map(toRow);

    return { sections, others };
  });

  protected readonly sections = computed(() => this.partition().sections);
  protected readonly otherTasks = computed(() => this.partition().others);

  private readonly allRows = computed(() => [
    ...this.partition().sections.flatMap((s) => s.rows),
    ...this.partition().others,
  ]);
  protected readonly totalCount = computed(() => this.allRows().length);
  protected readonly doneCount = computed(() => this.allRows().filter((r) => r.isChecked).length);
  protected readonly progress = computed(() => {
    const total = this.totalCount();
    return total === 0 ? 0 : this.doneCount() / total;
  });
  protected readonly percent = computed(() => Math.round(this.progress() * 100));
  protected readonly progressBarColor = computed(() => progressColor(this.progress()));
  protected readonly isSync = computed(
    () =>
      this.visibleTasks().filter((t) => !t.synced).length +
        this.checksByDate().filter((c) => !c.synced).length ===
      0,
  );

  // — Sheets / dialogs —
  protected readonly showSyncConfirm = signal(false);
  protected readonly showAddSheet = signal(false);
  protected readonly taskForOptions = signal<LocalTask | null>(null);
  protected readonly periodForOptions = signal<LocalRoutinePeriod | null>(null);
  protected readonly taskToDelete = signal<LocalTask | null>(null);
  protected readonly periodToDelete = signal<LocalRoutinePeriod | null>(null);

  protected readonly showPeriodForm = signal(false);
  protected readonly editingPeriod = signal<LocalRoutinePeriod | null>(null);
  protected readonly periodName = signal('');
  protected readonly periodStart = signal(6 * 60);
  protected readonly periodEnd = signal(12 * 60);

  protected readonly showTaskForm = signal(false);
  protected readonly editingTask = signal<LocalTask | null>(null);
  protected readonly taskTitle = signal('');
  protected readonly taskPeriodLabel = signal('');

  protected readonly addActions: SheetAction[] = [
    { label: 'Ajouter une tâche', icon: 'list_alt', color: 'var(--c-blue-medium)' },
    { label: 'Ajouter une période', icon: 'schedule', color: 'var(--app-selected-fill)' },
  ];
  protected readonly periodActions: SheetAction[] = [
    { label: 'Modifier', icon: 'edit', color: 'var(--c-blue-medium)' },
    { label: 'Supprimer', icon: 'delete', color: 'var(--c-red-medium)' },
  ];
  /** Édition réservée aux DAILY (édition complète W/M/Y/NONE différée, cf. en-tête). */
  protected readonly taskActions = computed<SheetAction[]>(() => {
    const t = this.taskForOptions();
    const del: SheetAction = { label: 'Supprimer', icon: 'delete', color: 'var(--c-red-medium)' };
    if (t?.recurrenceKind === 'DAILY') {
      return [{ label: 'Modifier', icon: 'edit', color: 'var(--c-blue-medium)' }, del];
    }
    return [del];
  });

  protected readonly periodFormValid = computed(
    () => this.periodName().trim().length > 0 && this.periodStart() < this.periodEnd(),
  );
  protected readonly periodFormError = computed(() => {
    if (this.periodName().trim().length === 0) return 'Le nom de période ne peut pas être vide';
    if (this.periodStart() >= this.periodEnd()) return 'La fin doit être après le début';
    return '';
  });
  protected readonly periodOptions = computed(() => this.periods().map((p) => this.periodLabel(p)));
  protected readonly taskFormValid = computed(
    () => this.taskTitle().trim().length > 0 && this.taskPeriodLabel().length > 0,
  );
  protected readonly taskFormError = computed(() => {
    if (this.taskTitle().trim().length === 0) return 'Le titre ne peut pas être vide';
    if (this.taskPeriodLabel().length === 0) return 'Sélectionne une période';
    return '';
  });

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }

  // — Helpers d'affichage —
  protected periodLabel(p: LocalRoutinePeriod): string {
    return `${p.name} • ${p.startTime}-${p.endTime}`;
  }

  /** Couleurs de row par type (TaskTypeStyle.kt) : DAILY neutre, W/M/Y vert, NONE orange. */
  protected rowBg(t: LocalTask): string {
    if (t.recurrenceKind === 'NONE') return 'var(--app-task-row-orange-bg)';
    if (t.recurrenceKind !== 'DAILY') return 'var(--app-task-row-green-bg)';
    return 'var(--app-bg-recessed)';
  }
  protected rowNameBg(t: LocalTask): string {
    if (t.recurrenceKind === 'NONE') return 'var(--app-task-row-orange-name-box)';
    if (t.recurrenceKind !== 'DAILY') return 'var(--app-task-row-green-name-box)';
    return 'var(--app-bg-surface)';
  }
  /** Icône à gauche : drag_indicator (DAILY, décoratif — D&D différé), repeat (W/M/Y), calendar (NONE). */
  protected leadingIcon(t: LocalTask): string {
    if (t.recurrenceKind === 'NONE') return 'calendar_today';
    if (t.recurrenceKind !== 'DAILY') return 'repeat';
    return 'drag_indicator';
  }
  protected leadingIconColor(t: LocalTask): string {
    if (t.recurrenceKind === 'NONE') return 'var(--c-orange-medium)';
    if (t.recurrenceKind !== 'DAILY') return 'var(--c-medium-green)';
    return 'var(--app-text-secondary)';
  }

  // — Navigation par jour —
  protected prevDay(): void {
    this.selectedDate.set(shiftIsoDay(this.selectedDate(), -1));
  }
  protected nextDay(): void {
    const next = shiftIsoDay(this.selectedDate(), 1);
    const today = todayIso();
    this.selectedDate.set(next > today ? today : next);
  }
  protected goToday(): void {
    this.selectedDate.set(todayIso());
  }

  // — Sheets —
  protected onAddAction(label: string): void {
    this.showAddSheet.set(false);
    if (label === 'Ajouter une tâche') this.openAddTask();
    else if (label === 'Ajouter une période') this.openAddPeriod();
  }
  protected onTaskAction(label: string): void {
    const t = this.taskForOptions();
    this.taskForOptions.set(null);
    if (!t) return;
    if (label === 'Modifier') this.openEditTask(t);
    else if (label === 'Supprimer') this.taskToDelete.set(t);
  }
  protected onPeriodAction(label: string): void {
    const p = this.periodForOptions();
    this.periodForOptions.set(null);
    if (!p) return;
    if (label === 'Modifier') this.openEditPeriod(p);
    else if (label === 'Supprimer') this.periodToDelete.set(p);
  }

  // — Checks (toggle) —
  protected async toggleTask(task: LocalTask, nowChecked: boolean): Promise<void> {
    const date = this.selectedDate();
    const now = new Date().toISOString();
    const checkedAt = nowChecked ? now : null;
    const existing = await this.db.task_checks
      .where('taskUUID')
      .equals(task.uuid)
      .and((c) => c.occurrenceDate === date)
      .first();
    if (existing) {
      await this.db.task_checks.update(existing.uuid, {
        isChecked: nowChecked,
        checkedAt,
        pendingDeletion: false,
        synced: false,
        updatedAt: now,
      });
    } else {
      const row: LocalTaskCheck = {
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        taskUUID: task.uuid,
        occurrenceDate: date,
        isChecked: nowChecked,
        checkedAt,
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.task_checks.put(row);
    }
    void this.sync.syncAll().catch(() => undefined);
  }

  // — Périodes —
  protected openAddPeriod(): void {
    this.editingPeriod.set(null);
    this.periodName.set('');
    this.periodStart.set(6 * 60);
    this.periodEnd.set(12 * 60);
    this.showPeriodForm.set(true);
  }
  protected openEditPeriod(p: LocalRoutinePeriod): void {
    this.editingPeriod.set(p);
    this.periodName.set(p.name);
    this.periodStart.set(hhmmToMinutes(p.startTime) ?? 6 * 60);
    this.periodEnd.set(hhmmToMinutes(p.endTime) ?? 12 * 60);
    this.showPeriodForm.set(true);
  }
  protected async submitPeriodForm(): Promise<void> {
    if (!this.periodFormValid()) return;
    const name = this.periodName().trim();
    const startTime = minutesToHHmm(this.periodStart());
    const endTime = minutesToHHmm(this.periodEnd());
    const now = new Date().toISOString();
    const editing = this.editingPeriod();
    if (editing) {
      await this.db.routine_periods.update(editing.uuid, {
        name,
        startTime,
        endTime,
        synced: false,
        updatedAt: now,
      });
      this.snackbar.info('Période modifiée');
    } else {
      const maxOrder = Math.max(0, ...this.periods().map((p) => p.order));
      const row: LocalRoutinePeriod = {
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        name,
        startTime,
        endTime,
        order: maxOrder + 1,
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.routine_periods.put(row);
      this.snackbar.info('Période ajoutée');
    }
    this.showPeriodForm.set(false);
    void this.sync.syncAll().catch(() => undefined);
  }
  protected async confirmDeletePeriod(): Promise<void> {
    const p = this.periodToDelete();
    this.periodToDelete.set(null);
    if (!p) return;
    const now = new Date().toISOString();
    // Cascade (mirror Android) : tâches DAILY de la période + leurs checks + la période.
    const tasks = await this.db.tasks
      .where('periodUUID')
      .equals(p.uuid)
      .and((t) => t.recurrenceKind === 'DAILY' && !t.pendingDeletion)
      .toArray();
    for (const t of tasks) {
      await this.db.task_checks
        .where('taskUUID')
        .equals(t.uuid)
        .modify({ pendingDeletion: true, synced: false, updatedAt: now });
      await this.db.tasks.update(t.uuid, { pendingDeletion: true, synced: false, updatedAt: now });
    }
    await this.db.routine_periods.update(p.uuid, { pendingDeletion: true, synced: false, updatedAt: now });
    this.snackbar.info('Période supprimée');
    void this.sync.syncAll().catch(() => undefined);
  }

  // — Tâches (DAILY) —
  protected openAddTask(): void {
    this.editingTask.set(null);
    this.taskTitle.set('');
    const first = this.periods()[0];
    this.taskPeriodLabel.set(first ? this.periodLabel(first) : '');
    this.showTaskForm.set(true);
  }
  protected openEditTask(t: LocalTask): void {
    this.editingTask.set(t);
    this.taskTitle.set(t.title);
    const period = this.periods().find((p) => p.uuid === t.periodUUID);
    this.taskPeriodLabel.set(period ? this.periodLabel(period) : '');
    this.showTaskForm.set(true);
  }
  private periodFromLabel(): LocalRoutinePeriod | null {
    return this.periods().find((p) => this.periodLabel(p) === this.taskPeriodLabel()) ?? null;
  }
  protected async submitTaskForm(): Promise<void> {
    if (!this.taskFormValid()) return;
    const period = this.periodFromLabel();
    if (!period) return;
    const title = this.taskTitle().trim();
    const now = new Date().toISOString();
    const editing = this.editingTask();
    if (editing) {
      const periodChanged = editing.periodUUID !== period.uuid;
      const order = periodChanged ? (await this.maxTaskOrder(period.uuid)) + 1 : editing.order;
      await this.db.tasks.update(editing.uuid, {
        title,
        periodUUID: period.uuid,
        order,
        synced: false,
        updatedAt: now,
      });
      this.snackbar.info('Tâche modifiée');
    } else {
      const row: LocalTask = {
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        title,
        notes: null,
        isActive: true,
        order: (await this.maxTaskOrder(period.uuid)) + 1,
        recurrenceKind: 'DAILY',
        dueDate: null,
        dueTime: null,
        periodUUID: period.uuid,
        recurrenceWeekdays: null,
        recurrenceStartDate: todayIso(),
        recurrenceEndDate: null,
        excludedDates: [],
        reminderMinutesBefore: null,
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.tasks.put(row);
      this.snackbar.info('Tâche ajoutée');
    }
    this.showTaskForm.set(false);
    void this.sync.syncAll().catch(() => undefined);
  }
  private async maxTaskOrder(periodUUID: string): Promise<number> {
    const tasks = await this.db.tasks
      .where('periodUUID')
      .equals(periodUUID)
      .and((t) => !t.pendingDeletion)
      .toArray();
    return Math.max(0, ...tasks.map((t) => t.order));
  }
  protected async confirmDeleteTask(): Promise<void> {
    const t = this.taskToDelete();
    this.taskToDelete.set(null);
    if (!t) return;
    const now = new Date().toISOString();
    await this.db.task_checks
      .where('taskUUID')
      .equals(t.uuid)
      .modify({ pendingDeletion: true, synced: false, updatedAt: now });
    await this.db.tasks.update(t.uuid, { pendingDeletion: true, synced: false, updatedAt: now });
    void this.sync.syncAll().catch(() => undefined);
  }

  // — Drag & drop (mirror RoutineTasksScreenViewModel.moveTask*) —
  protected readonly draggingUuid = signal<string | null>(null);
  /** Cible survolée : 'top_<periodUuid>' | 'after_<taskUuid>' | 'end_<periodUuid>'. */
  protected readonly dropTarget = signal<string | null>(null);

  protected isDaily(t: LocalTask): boolean {
    return t.recurrenceKind === 'DAILY';
  }

  protected onDragStart(ev: DragEvent, task: LocalTask): void {
    if (!this.isDaily(task)) return;
    ev.dataTransfer?.setData('text/plain', task.uuid); // requis par Firefox pour démarrer le drag
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'move';
    this.draggingUuid.set(task.uuid);
  }

  protected onDragEnd(): void {
    this.draggingUuid.set(null);
    this.dropTarget.set(null);
  }

  protected onDragOver(ev: DragEvent, targetKey: string): void {
    if (this.draggingUuid() === null) return;
    ev.preventDefault(); // autorise le drop
    if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'move';
    this.dropTarget.set(targetKey);
  }

  protected onDragLeave(targetKey: string): void {
    if (this.dropTarget() === targetKey) this.dropTarget.set(null);
  }

  protected onDropTop(ev: DragEvent, periodUuid: string): void {
    this.finishDrop(ev, (uuid) => this.moveTask(uuid, periodUuid, { kind: 'top' }));
  }

  protected onDropAfter(ev: DragEvent, anchorTaskUuid: string, periodUuid: string): void {
    this.finishDrop(ev, (uuid) => this.moveTask(uuid, periodUuid, { kind: 'after', anchorUuid: anchorTaskUuid }));
  }

  protected onDropEnd(ev: DragEvent, periodUuid: string): void {
    this.finishDrop(ev, (uuid) => this.moveTask(uuid, periodUuid, { kind: 'end' }));
  }

  private finishDrop(ev: DragEvent, move: (draggedUuid: string) => Promise<void>): void {
    ev.preventDefault();
    const uuid = this.draggingUuid() ?? ev.dataTransfer?.getData('text/plain') ?? '';
    this.draggingUuid.set(null);
    this.dropTarget.set(null);
    if (!uuid) return;
    void move(uuid).catch(() => this.snackbar.error('Échec du déplacement de la tâche'));
  }

  /** Tâches DAILY actives d'une période, triées par order (= getActiveDailyByPeriod Android). */
  private async dailyTasksOf(periodUuid: string): Promise<LocalTask[]> {
    const tasks = await this.db.tasks
      .where('periodUUID')
      .equals(periodUuid)
      .and((t) => t.recurrenceKind === 'DAILY' && !t.pendingDeletion && t.isActive)
      .toArray();
    return tasks.sort((a, b) => a.order - b.order);
  }

  /**
   * Déplace une tâche DAILY (cross-période OK) : insertion à la position cible puis
   * renumérotation 1..n de la période cible ET de l'ancienne période si différente.
   * Toute row touchée passe synced=false (+ updatedAt) puis push sync — mirror Android.
   */
  private async moveTask(
    draggedUuid: string,
    targetPeriodUuid: string,
    position: { kind: 'top' } | { kind: 'after'; anchorUuid: string } | { kind: 'end' },
  ): Promise<void> {
    if (position.kind === 'after' && position.anchorUuid === draggedUuid) return;
    const dragged = await this.db.tasks.get(draggedUuid);
    if (!dragged || dragged.recurrenceKind !== 'DAILY') return;

    const oldPeriodUuid = dragged.periodUUID ?? null;
    const now = new Date().toISOString();

    const target = (await this.dailyTasksOf(targetPeriodUuid)).filter((t) => t.uuid !== draggedUuid);
    let insertIndex: number;
    if (position.kind === 'top') insertIndex = 0;
    else if (position.kind === 'end') insertIndex = target.length;
    else {
      const anchorIndex = target.findIndex((t) => t.uuid === position.anchorUuid);
      insertIndex = anchorIndex === -1 ? target.length : anchorIndex + 1;
    }
    target.splice(insertIndex, 0, { ...dragged, periodUUID: targetPeriodUuid });

    for (let i = 0; i < target.length; i++) {
      const t = target[i];
      const desiredOrder = i + 1;
      const desiredPeriod = t.uuid === draggedUuid ? targetPeriodUuid : t.periodUUID;
      if (t.order !== desiredOrder || t.periodUUID !== desiredPeriod) {
        await this.db.tasks.update(t.uuid, {
          order: desiredOrder,
          periodUUID: desiredPeriod,
          synced: false,
          updatedAt: now,
        });
      }
    }

    if (oldPeriodUuid && oldPeriodUuid !== targetPeriodUuid) {
      const olds = await this.dailyTasksOf(oldPeriodUuid);
      for (let i = 0; i < olds.length; i++) {
        if (olds[i].order !== i + 1) {
          await this.db.tasks.update(olds[i].uuid, { order: i + 1, synced: false, updatedAt: now });
        }
      }
    }

    void this.sync.syncAll().catch(() => undefined);
  }

  // — Sync —
  protected confirmSync(): void {
    this.showSyncConfirm.set(false);
    void this.sync
      .syncAll()
      .catch(() => this.snackbar.error('Échec de la synchronisation des routines'));
  }
}
