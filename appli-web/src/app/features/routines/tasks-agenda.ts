import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { LocalTask, RecurrenceKind } from '@core/models/task.model';
import { LocalTaskCheck } from '@core/models/task-check.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ProgressBarPrimitive } from '@designsystem/common_components/progress-bar-primitive';
import { progressColor } from '@designsystem/common_components/labeled-progress-bar';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { CalendarMonthGrid } from '@designsystem/common_components/calendar-month-grid';
import { StatusIcon } from '@designsystem/common_components/status-icon';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { OptionRow } from '@designsystem/common_components/option-row';
import { CustomCheckbox } from '@designsystem/common_components/custom-checkbox';
import { CustomRadioButton } from '@designsystem/common_components/custom-radio-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomDatePickerDialog } from '@designsystem/common_components/custom-date-picker-dialog';
import { DateField } from '@designsystem/common_components/date-field';
import { AppIcon } from '@designsystem/icons/app-icon';
import { occursOn } from './task-occurrence';

/**
 * Onglet Agenda des Routines — miroir de TasksCalendarScreen.kt + TasksCalendarViewModel.kt :
 * avancement du mois (barre + % + sync + X/Y + ✓ + bouton +), recherche (remplace la grille si
 * query non vide), grille mensuelle (CalendarTaskDay : icône d'état overdue rouge / complet vert /
 * partiel orange / pending bleu + numéro de jour, aujourd'hui bordé), clic jour → bottom sheet des
 * tâches du jour (DayTasksBottomSheet : checkbox + titre barré si fait + icône type + heure + badge
 * relatif "dans Xh"/"en retard Xd" + boutons delete/edit + row Ajouter), création/édition via
 * TaskFormDialog (récurrence NONE/WEEKLY/MONTHLY/YEARLY, jours de semaine, dates début/fin, heure,
 * rappel) avec RecurrenceEditModeDialog (cette occurrence seulement → exclusion + fork NONE / série
 * entière) pour les tâches récurrentes. Les DAILY restent dans l'onglet Quotidien.
 * CRUD local optimiste (Dexie) + sync best-effort. Pas de scheduling de rappel côté web (le champ
 * reminderMinutesBefore est édité et synchronisé ; les notifications restent Android).
 */

interface DayCell {
  totalCount: number;
  doneCount: number;
  hasOverdue: boolean;
}

interface DayRowUi {
  task: LocalTask;
  isChecked: boolean;
  badge: { text: string; color: string } | null;
}

interface ReminderPreset {
  minutes: number | null;
  label: string;
}

type RecurrenceEditMode = 'ONLY_THIS' | 'ALL';
type DatePickerTarget = 'due' | 'start' | 'end';

const RECURRENCE_CHIPS: { kind: RecurrenceKind; label: string }[] = [
  { kind: 'NONE', label: 'Aucune' },
  { kind: 'WEEKLY', label: 'Hebdo' },
  { kind: 'MONTHLY', label: 'Mensuel' },
  { kind: 'YEARLY', label: 'Annuel' },
];

const REMINDER_PRESETS: ReminderPreset[] = [
  { minutes: null, label: 'Aucun' },
  { minutes: 15, label: '15 min' },
  { minutes: 60, label: '1 heure' },
  { minutes: 24 * 60, label: '1 jour' },
];

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-tasks-agenda',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TitledDivider,
    ActionIconButton,
    ProgressBarPrimitive,
    StyledSearchField,
    CalendarMonthGrid,
    StatusIcon,
    AppBottomSheet,
    OptionRow,
    CustomCheckbox,
    CustomRadioButton,
    FormDialog,
    ConfirmationDialog,
    CustomTextField,
    CustomDatePickerDialog,
    DateField,
    AppIcon,
  ],
  template: `
    <div class="agenda">
      <!-- RoutineTasksProgressBar (agrégé sur le mois) : barre + % + sync + X/Y + ✓ + + -->
      <!-- (pas de divider "Avancement" ici : la colonne a déjà son TitledDivider "Agenda") -->
      <div class="progress">
        <app-progress-bar-primitive
          class="progress__bar"
          [progress]="monthProgress()"
          [color]="progressBarColor()"
          troughColor="var(--app-bg-surface)"
        />
        <span class="progress__pct" [style.color]="progressBarColor()">{{ monthPercent() }}%</span>
        <app-action-icon-button
          [icon]="isSync() ? 'cloud_done' : 'cloud_off'"
          [tint]="isSync() ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
          [hasBackground]="false"
          (clicked)="syncNow()"
        />
        <span class="progress__count">{{ monthDone() }}/{{ monthTotal() }}</span>
        <app-icon name="check" [size]="24" color="var(--c-medium-green)" />
        <app-action-icon-button icon="add" (clicked)="openCreate(selectedDate() ?? today)" />
      </div>

      <!-- C.2 : recherche — si query non vide, remplace la grille par la liste des résultats. -->
      <app-styled-search-field [(value)]="searchQuery" placeholderText="Rechercher une tâche…" />

      @if (searchQuery().trim() === '') {
        <app-titled-divider title="Agenda tâches" />

        <div class="cal">
          <div class="cal__header">
            <app-action-icon-button icon="chevron_left" backgroundColor="var(--c-first-blue)" (clicked)="prevMonth()" />
            <span class="cal__month">{{ monthLabel() }}</span>
            <app-action-icon-button icon="chevron_right" backgroundColor="var(--c-first-blue)" (clicked)="nextMonth()" />
          </div>

          <div class="cal__weekdays">
            @for (w of weekdayLabels; track $index) {
              <span>{{ w }}</span>
            }
          </div>

          <app-calendar-month-grid [year]="year()" [month]="month()" [firstDayOffset]="firstDayOffset()">
            <ng-template let-iso>
              <button class="day" [class.day--today]="iso === today" (click)="selectedDate.set(iso)">
                <span class="day__markers">
                  @switch (statusOf(iso)) {
                    @case ('overdue') { <app-status-icon icon="close" tint="var(--c-red-medium)" /> }
                    @case ('completed') { <app-status-icon icon="check" tint="var(--c-medium-green)" /> }
                    @case ('inProgress') { <app-status-icon icon="arrow_forward" tint="var(--c-orange-medium)" /> }
                    @case ('pending') { <app-status-icon icon="arrow_forward" tint="var(--c-blue-medium)" /> }
                  }
                </span>
                <span class="day__num">{{ dayNumOf(iso) }}</span>
              </button>
            </ng-template>
          </app-calendar-month-grid>
        </div>
      } @else {
        @if (searchResults().length === 0) {
          <p class="search-empty">Aucune tâche correspondante</p>
        } @else {
          <div class="search-results">
            @for (t of searchResults(); track t.uuid) {
              <button type="button" class="search-row" (click)="requestEdit(t, null)">
                <span class="search-row__title">{{ t.title }}</span>
                @if (kindLabel(t)) {
                  <span class="search-row__kind">{{ kindLabel(t) }}</span>
                }
              </button>
            }
          </div>
        }
      }
    </div>

    <!-- DayTasksBottomSheet : tâches du jour sélectionné -->
    <app-bottom-sheet [open]="selectedDate() !== null" (dismissRequest)="selectedDate.set(null)">
      <div class="sheet">
        <app-titled-divider [title]="selectedDateLabel()" />

        @if (dayRows().length === 0) {
          <div class="sheet__empty">
            <app-icon name="check_circle" [size]="48" color="color-mix(in srgb, var(--c-blue-medium) 40%, transparent)" />
            <span>Aucune tâche ce jour.</span>
          </div>
        } @else {
          <div class="sheet__rows">
            @for (row of dayRows(); track row.task.uuid) {
              <div class="dayrow" [style.background]="rowBg(row.task)">
                <span class="dayrow__check">
                  <app-custom-checkbox [checked]="row.isChecked" (checkedChange)="toggleDone(row.task, $event)" />
                </span>
                <button type="button" class="dayrow__main" (click)="requestEdit(row.task, selectedDate())">
                  <span class="dayrow__title" [class.dayrow__title--done]="row.isChecked">{{ row.task.title }}</span>
                  <app-icon [name]="typeIcon(row.task)" [size]="16" [color]="typeIconColor(row.task)" />
                  @if (row.task.dueTime) {
                    <span class="dayrow__time">{{ row.task.dueTime }}</span>
                  }
                  @if (row.badge) {
                    <span class="dayrow__badge" [style.color]="row.badge.color">{{ row.badge.text }}</span>
                  }
                </button>
                <button type="button" class="dayrow__btn dayrow__btn--delete" (click)="taskToDelete.set(row.task)">
                  <app-icon name="delete_forever" [size]="20" color="var(--app-text-primary)" />
                </button>
                <button type="button" class="dayrow__btn dayrow__btn--edit" (click)="requestEdit(row.task, selectedDate())">
                  <app-icon name="edit" [size]="20" color="var(--app-text-primary)" />
                </button>
              </div>
            }
          </div>
        }

        <app-titled-divider title="Actions" />
        <app-option-row
          label="Ajouter une tâche"
          icon="add"
          [hasBackground]="true"
          backgroundColor="var(--app-primary-action)"
          (clicked)="openCreate(selectedDate() ?? today)"
        />
      </div>
    </app-bottom-sheet>

    <!-- Confirmation suppression -->
    <app-confirmation-dialog
      [open]="taskToDelete() !== null"
      title="Supprimer la tâche"
      [message]="'Supprimer « ' + (taskToDelete()?.title ?? '') + ' » ?'"
      confirmButtonText="Supprimer"
      dismissButtonText="Annuler"
      confirmButtonColor="var(--c-red-medium)"
      (confirm)="confirmDelete()"
      (dismiss)="taskToDelete.set(null)"
    />

    <!-- RecurrenceEditModeDialog : portée de l'édition d'une tâche récurrente -->
    <app-form-dialog
      [open]="showEditModeDialog()"
      title="Appliquer l'édition à…"
      confirmText="Suivant"
      [confirmEnabled]="true"
      (confirm)="confirmEditMode()"
      (dismiss)="cancelEditMode()"
    >
      <p class="mode__msg">Cette tâche se répète. Choisissez la portée des modifications.</p>
      <button type="button" class="mode__row" (click)="editModeChoice.set('ONLY_THIS')">
        <app-custom-radio-button [selected]="editModeChoice() === 'ONLY_THIS'" (clicked)="editModeChoice.set('ONLY_THIS')" />
        <span>Cette occurrence seulement</span>
      </button>
      <button type="button" class="mode__row" (click)="editModeChoice.set('ALL')">
        <app-custom-radio-button [selected]="editModeChoice() === 'ALL'" (clicked)="editModeChoice.set('ALL')" />
        <span>Toutes les occurrences (série entière)</span>
      </button>
    </app-form-dialog>

    <!-- TaskFormDialog : création / édition -->
    <app-form-dialog
      [open]="showTaskForm()"
      [title]="editingTask() ? 'Modifier la tâche' : 'Nouvelle tâche'"
      [confirmText]="editingTask() ? 'Enregistrer' : 'Ajouter'"
      [confirmEnabled]="formValid()"
      [disabledReason]="formError()"
      (confirm)="submitForm()"
      (dismiss)="showTaskForm.set(false)"
    >
      <app-custom-text-field
        label="Titre"
        placeholder="ex. Payer loyer"
        [value]="formTitle()"
        (valueChange)="formTitle.set($event)"
      />

      <div class="field">
        <span class="field__label">Répéter</span>
        <div class="chips">
          @for (c of recurrenceChips; track c.kind) {
            <button
              type="button"
              class="chip"
              [class.chip--sel]="formKind() === c.kind"
              (click)="formKind.set(c.kind)"
            >
              {{ c.label }}
            </button>
          }
        </div>
      </div>

      @if (formKind() === 'NONE') {
        <div class="field">
          <span class="field__label">Date d'échéance</span>
          <app-date-field [value]="longDate(formDueDate())" (clicked)="datePickerTarget.set('due')" />
        </div>
      } @else {
        <div class="field">
          <span class="field__label">Date de début</span>
          <app-date-field [value]="longDate(formStartDate())" (clicked)="datePickerTarget.set('start')" />
        </div>
      }

      @if (formKind() === 'WEEKLY') {
        <div class="field">
          <span class="field__label">Jours</span>
          <div class="chips">
            @for (w of weekdayLabelsFull; track $index) {
              <button
                type="button"
                class="chip chip--day"
                [class.chip--sel]="formWeekdays().has($index)"
                (click)="toggleWeekday($index)"
              >
                {{ w }}
              </button>
            }
          </div>
        </div>
      }

      @if (formKind() !== 'NONE') {
        <div class="field">
          <span class="field__label field__label--row">
            Date de fin (optionnel)
            @if (formEndDate()) {
              <button type="button" class="field__clear" (click)="formEndDate.set(null)">Supprimer</button>
            }
          </span>
          <app-date-field [value]="formEndDate() ? longDate(formEndDate()!) : ''" placeholder="Sans fin (jusqu'à annulation)" (clicked)="datePickerTarget.set('end')" />
        </div>
      }

      <app-custom-text-field
        label="Heure (optionnel)"
        placeholder="HH:MM (optionnel)"
        [value]="formDueTime()"
        (valueChange)="onDueTimeInput($event)"
      />

      <div class="field">
        <span class="field__label">Rappel</span>
        <div class="chips">
          @for (p of reminderPresets; track p.label) {
            <button
              type="button"
              class="chip"
              [class.chip--sel]="formReminder() === p.minutes"
              (click)="formReminder.set(p.minutes)"
            >
              {{ p.label }}
            </button>
          }
        </div>
      </div>
    </app-form-dialog>

    <!-- DatePicker (due / start / end) -->
    <app-custom-date-picker-dialog
      [open]="datePickerTarget() !== null"
      [initialIso]="datePickerInitial()"
      [title]="datePickerTitle()"
      [minYear]="currentYear - 1"
      [maxYear]="currentYear + 5"
      (confirm)="onDatePicked($event)"
      (dismiss)="datePickerTarget.set(null)"
    />
  `,
  styles: [
    `
      /* Base commune des boutons custom du composant (rows, chips, cellules). */
      button {
        background: transparent;
        border: none;
        cursor: pointer;
        font-family: var(--font-family-base);
        text-align: left;
      }
      /* Groupes de mise en page partagés (budget CSS). */
      .progress,
      .cal__header,
      .search-row,
      .dayrow,
      .dayrow__main,
      .mode__row,
      .field__label--row,
      .day__markers {
        display: flex;
        align-items: center;
      }
      .agenda,
      .search-results,
      .sheet,
      .sheet__rows,
      .sheet__empty,
      .field {
        display: flex;
        flex-direction: column;
      }
      .agenda {
        gap: var(--space-3);
      }
      /* Container avancement (bgRecessed) — même look que l'onglet Quotidien. */
      .progress {
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
        white-space: nowrap;
      }
      /* Boîte calendrier (bgRecessed) — même pattern que calendar-page. */
      .cal {
        width: 100%;
        max-width: 520px;
        margin: 0 auto;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
        box-sizing: border-box;
      }
      .cal__header {
        justify-content: space-between;
        /* Espace mois ↔ ligne des initiales de jours (L M M J V S D). */
        margin-bottom: var(--space-3);
      }
      .cal__month {
        display: inline-flex;
        align-items: center;
        height: 40px; /* = hauteur des boutons chevrons (size 40) */
        background: var(--c-first-blue);
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        color: var(--app-text-tertiary);
        font-size: var(--font-size-subtitle);
        text-transform: capitalize;
      }
      .cal__weekdays {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 6px;
        margin-bottom: var(--space-2);
      }
      .cal__weekdays span {
        text-align: center;
        color: var(--c-light-gray-blue);
        font-size: var(--font-size-caption);
      }
      /* Cellule jour (CalendarTaskDay) : icône d'état 18px + numéro, aujourd'hui bordé. */
      .day {
        aspect-ratio: 1;
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 2px;
        border: 1.5px solid transparent;
        border-radius: var(--radius-sm);
        color: var(--app-text-tertiary);
      }
      .day:hover {
        background: var(--app-bg-surface);
      }
      .day--today {
        border-color: var(--app-primary-action);
      }
      .day__markers {
        height: 18px;
      }
      .day__num {
        font-size: var(--font-size-body);
      }
      .search-empty {
        margin: 0;
        padding: var(--space-5) 0;
        text-align: center;
        color: var(--app-text-tertiary);
        font-size: 14px;
      }
      .search-results {
        gap: 6px;
      }
      /* Row résultat (TaskSearchResultRow) : titre à gauche + label kind/date à droite. */
      .search-row {
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-sm);
        padding: 10px var(--space-3);
      }
      .search-row__title {
        flex: 1;
        color: var(--app-text-primary);
        font-size: 14px;
      }
      .search-row__kind {
        color: var(--c-blue-medium);
        font-size: 12px;
        font-weight: 600;
      }
      .sheet {
        gap: var(--space-3);
        padding: 0 var(--space-4) var(--space-4);
      }
      .sheet__empty {
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-4) 0;
        color: var(--app-text-tertiary);
        font-size: 14px;
      }
      .sheet__rows {
        gap: var(--space-2);
        max-height: 360px;
        overflow-y: auto;
      }
      /* Row du jour (DayTaskItem) : checkbox + titre/type/heure/badge + delete + edit. */
      .dayrow {
        gap: 6px;
        border-radius: var(--radius-sm);
        padding: 6px var(--space-2);
      }
      .dayrow__check,
      .dayrow__btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 36px;
        height: 36px;
        flex-shrink: 0;
        border-radius: var(--radius-sm);
      }
      .dayrow__main {
        flex: 1;
        min-width: 0;
        gap: 6px;
        padding: 4px;
      }
      .dayrow__title {
        color: var(--app-text-primary);
        font-size: 14px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .dayrow__title--done {
        text-decoration: line-through;
      }
      .dayrow__time {
        color: var(--c-blue-medium);
        font-size: 13px;
        font-weight: 600;
        white-space: nowrap;
      }
      .dayrow__badge {
        font-size: 12px;
        white-space: nowrap;
      }
      .dayrow__btn--delete {
        background: var(--c-red-medium);
      }
      .dayrow__btn--edit {
        background: var(--app-primary-action);
      }
      .mode__msg {
        margin: 0;
        color: var(--app-text-tertiary);
        font-size: 13px;
      }
      .mode__row {
        gap: var(--space-2);
        width: 100%;
        padding: var(--space-1) 0;
        color: var(--app-text-primary);
        font-size: 14px;
      }
      .field {
        gap: 4px;
      }
      .field__label {
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
      .field__label--row {
        justify-content: space-between;
      }
      .field__clear {
        color: var(--app-text-tertiary);
        font-size: 10px;
      }
      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
      .chip {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-sm);
        padding: 6px 10px;
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
      .chip--day {
        width: 36px;
        height: 36px;
        padding: 0;
        font-size: 13px;
        text-align: center;
      }
      .chip--sel {
        background: var(--app-primary-action);
        color: var(--app-text-primary);
        font-weight: 600;
      }
    `,
  ],
})
export class TasksAgenda {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly snackbar = inject(SnackbarService);

  protected readonly today = todayIso();
  protected readonly currentYear = new Date().getFullYear();
  /** Abréviations Lun Mar Mer Jeu Ven Sam Dim, Monday-first. */
  protected readonly weekdayLabels = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
  protected readonly weekdayLabelsFull = ['Lu', 'Ma', 'Me', 'Je', 'Ve', 'Sa', 'Di'];
  protected readonly recurrenceChips = RECURRENCE_CHIPS;
  protected readonly reminderPresets = REMINDER_PRESETS;

  /** Mois affiché {y, m(0-11)}. */
  private readonly cursor = signal({ y: new Date().getFullYear(), m: new Date().getMonth() });
  protected readonly year = computed(() => this.cursor().y);
  protected readonly month = computed(() => this.cursor().m);

  protected readonly selectedDate = signal<string | null>(null);
  protected readonly searchQuery = signal('');

  // — Données offline-first : tâches non-DAILY actives (les DAILY restent dans Quotidien). —
  private readonly tasks = toSignal(
    from(
      liveQuery(() =>
        this.db.tasks.filter((t) => !t.pendingDeletion && t.isActive && t.recurrenceKind !== 'DAILY').toArray(),
      ),
    ),
    { initialValue: [] as LocalTask[] },
  );
  private readonly checks = toSignal(
    from(liveQuery(() => this.db.task_checks.filter((c) => !c.pendingDeletion).toArray())),
    { initialValue: [] as LocalTaskCheck[] },
  );

  /** ISO de tous les jours du mois affiché. */
  private readonly monthDayIsos = computed(() => {
    const { y, m } = this.cursor();
    const days = new Date(y, m + 1, 0).getDate();
    const out: string[] = [];
    for (let d = 1; d <= days; d++) out.push(`${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`);
    return out;
  });

  /** Set "taskUUID|dateIso" des occurrences cochées. */
  private readonly checkedSet = computed(
    () => new Set(this.checks().filter((c) => c.isChecked).map((c) => `${c.taskUUID}|${c.occurrenceDate}`)),
  );

  /** Map iso → compteurs du jour (miroir dayCells du VM Android, expansion via occursOn). */
  private readonly dayCells = computed(() => {
    const cells = new Map<string, DayCell>();
    const tasks = this.tasks();
    const checked = this.checkedSet();
    for (const iso of this.monthDayIsos()) {
      let total = 0;
      let done = 0;
      let hasOverdue = false;
      for (const t of tasks) {
        if (!occursOn(t, iso)) continue;
        total++;
        const isDone = checked.has(`${t.uuid}|${iso}`);
        if (isDone) done++;
        else if (iso < this.today) hasOverdue = true;
      }
      if (total > 0) cells.set(iso, { totalCount: total, doneCount: done, hasOverdue });
    }
    return cells;
  });

  // — Compteurs agrégés sur le mois (pattern RoutineTasksProgressBar) —
  protected readonly monthDone = computed(() => {
    let n = 0;
    for (const c of this.dayCells().values()) n += c.doneCount;
    return n;
  });
  protected readonly monthTotal = computed(() => {
    let n = 0;
    for (const c of this.dayCells().values()) n += c.totalCount;
    return n;
  });
  protected readonly monthProgress = computed(() => {
    const total = this.monthTotal();
    return total === 0 ? 0 : this.monthDone() / total;
  });
  protected readonly monthPercent = computed(() => Math.round(this.monthProgress() * 100));
  protected readonly progressBarColor = computed(() => progressColor(this.monthProgress()));
  protected readonly isSync = computed(
    () => this.tasks().filter((t) => !t.synced).length + this.checks().filter((c) => !c.synced).length === 0,
  );

  protected readonly monthLabel = computed(() => {
    const { y, m } = this.cursor();
    return new Date(y, m, 1).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  });
  protected readonly firstDayOffset = computed(() => {
    const { y, m } = this.cursor();
    return (new Date(y, m, 1).getDay() + 6) % 7; // Monday-first
  });

  /** Résultats de recherche (titre contains, tri alpha). */
  protected readonly searchResults = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    if (q === '') return [];
    return this.tasks()
      .filter((t) => t.title.toLowerCase().includes(q))
      .sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()));
  });

  /** Rows du jour sélectionné (tri dueTime puis titre + badge relatif). */
  protected readonly dayRows = computed<DayRowUi[]>(() => {
    const date = this.selectedDate();
    if (!date) return [];
    const checksByTask = new Map(
      this.checks()
        .filter((c) => c.occurrenceDate === date)
        .map((c) => [c.taskUUID, c]),
    );
    return this.tasks()
      .filter((t) => occursOn(t, date))
      .sort((a, b) => (a.dueTime ?? '99:99').localeCompare(b.dueTime ?? '99:99') || a.title.localeCompare(b.title))
      .map((t) => {
        const isChecked = checksByTask.get(t.uuid)?.isChecked === true;
        return { task: t, isChecked, badge: this.relativeBadge(t, date, isChecked) };
      });
  });

  protected readonly selectedDateLabel = computed(() => {
    const iso = this.selectedDate();
    return iso ? this.longDate(iso) : '';
  });

  // — Dialogs —
  protected readonly taskToDelete = signal<LocalTask | null>(null);

  protected readonly showTaskForm = signal(false);
  protected readonly editingTask = signal<LocalTask | null>(null);
  /** Mode d'édition résolu (null = ALL implicite — création ou tâche NONE). */
  private editMode: RecurrenceEditMode | null = null;
  /** Date d'occurrence cliquée (null si édition depuis la recherche → ONLY_THIS dégrade en ALL). */
  private editOccurrenceDate: string | null = null;

  protected readonly showEditModeDialog = signal(false);
  protected readonly editModeChoice = signal<RecurrenceEditMode>('ONLY_THIS');
  private pendingEditTask: LocalTask | null = null;

  protected readonly formTitle = signal('');
  protected readonly formKind = signal<RecurrenceKind>('NONE');
  protected readonly formDueDate = signal(todayIso());
  protected readonly formStartDate = signal(todayIso());
  protected readonly formEndDate = signal<string | null>(null);
  protected readonly formWeekdays = signal<Set<number>>(new Set());
  protected readonly formDueTime = signal('');
  protected readonly formReminder = signal<number | null>(null);

  protected readonly datePickerTarget = signal<DatePickerTarget | null>(null);

  protected readonly formValid = computed(() => {
    if (this.formTitle().trim() === '') return false;
    if (this.formKind() === 'WEEKLY' && this.formWeekdays().size === 0) return false;
    return true;
  });
  protected readonly formError = computed(() => {
    if (this.formTitle().trim() === '') return 'Titre requis';
    if (this.formKind() === 'WEEKLY' && this.formWeekdays().size === 0) return 'Sélectionne au moins un jour';
    return '';
  });

  protected readonly datePickerInitial = computed(() => {
    switch (this.datePickerTarget()) {
      case 'due':
        return this.formDueDate();
      case 'start':
        return this.formStartDate();
      case 'end':
        return this.formEndDate() ?? this.formStartDate();
      default:
        return null;
    }
  });
  protected readonly datePickerTitle = computed(() => {
    switch (this.datePickerTarget()) {
      case 'due':
        return "Date d'échéance";
      case 'start':
        return 'Date de début';
      case 'end':
        return 'Date de fin (optionnel)';
      default:
        return 'Date';
    }
  });

  // — Helpers d'affichage —
  protected dayNumOf(iso: string): number {
    return Number(iso.slice(8, 10));
  }

  /** Statut cellule (priorité CalendarTaskDay) : overdue > complet > partiel > pending. */
  protected statusOf(iso: string): 'overdue' | 'completed' | 'inProgress' | 'pending' | 'none' {
    const cell = this.dayCells().get(iso);
    if (!cell) return 'none';
    if (cell.hasOverdue) return 'overdue';
    if (cell.doneCount === cell.totalCount) return 'completed';
    if (cell.doneCount > 0) return 'inProgress';
    return 'pending';
  }

  /** Label kind du résultat de recherche : dueDate brute (NONE) ou label de récurrence. */
  protected kindLabel(t: LocalTask): string {
    switch (t.recurrenceKind) {
      case 'NONE':
        return t.dueDate ?? '';
      case 'WEEKLY':
        return 'Hebdo';
      case 'MONTHLY':
        return 'Mensuel';
      case 'YEARLY':
        return 'Annuel';
      default:
        return '';
    }
  }

  /** Teinte de row par type (TaskTypeStyle) : NONE orange, W/M/Y vert. */
  protected rowBg(t: LocalTask): string {
    return t.recurrenceKind === 'NONE' ? 'var(--app-task-row-orange-bg)' : 'var(--app-task-row-green-bg)';
  }
  protected typeIcon(t: LocalTask): string {
    return t.recurrenceKind === 'NONE' ? 'calendar_today' : 'repeat';
  }
  protected typeIconColor(t: LocalTask): string {
    return t.recurrenceKind === 'NONE' ? 'var(--c-orange-medium)' : 'var(--c-medium-green)';
  }

  protected longDate(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }

  /** Badge relatif (computeRelativeBadge Android) : "dans Xd/Xh/Xm" / "en retard Xd/Xh/Xm". */
  private relativeBadge(task: LocalTask, dateIso: string, isChecked: boolean): { text: string; color: string } | null {
    if (isChecked) return null;
    const dayDiff = this.daysBetween(this.today, dateIso);
    if (dayDiff > 0) return { text: `dans ${dayDiff}j`, color: 'var(--app-primary-action)' };
    if (dayDiff < 0) return { text: `en retard ${-dayDiff}j`, color: 'var(--c-red-medium)' };

    // Aujourd'hui : il faut une heure pour situer.
    if (!task.dueTime || !/^\d{2}:\d{2}$/.test(task.dueTime)) return null;
    const [hh, mm] = task.dueTime.split(':').map(Number);
    const now = new Date();
    const diffMinutes = hh * 60 + mm - (now.getHours() * 60 + now.getMinutes());
    if (diffMinutes > 60) return { text: `dans ${Math.floor(diffMinutes / 60)}h`, color: 'var(--app-primary-action)' };
    if (diffMinutes >= 1)
      return {
        text: `dans ${diffMinutes}m`,
        color: diffMinutes < 30 ? 'var(--c-orange-medium)' : 'var(--app-primary-action)',
      };
    if (diffMinutes === 0) return null;
    if (diffMinutes > -60) return { text: `en retard ${-diffMinutes}m`, color: 'var(--c-red-medium)' };
    return { text: `en retard ${Math.floor(-diffMinutes / 60)}h`, color: 'var(--c-red-medium)' };
  }

  private daysBetween(fromIso: string, toIso: string): number {
    const [fy, fm, fd] = fromIso.split('-').map(Number);
    const [ty, tm, td] = toIso.split('-').map(Number);
    return Math.round((new Date(ty, tm - 1, td).getTime() - new Date(fy, fm - 1, fd).getTime()) / 86_400_000);
  }

  // — Navigation mois —
  protected prevMonth(): void {
    const { y, m } = this.cursor();
    this.cursor.set(m === 0 ? { y: y - 1, m: 11 } : { y, m: m - 1 });
  }
  protected nextMonth(): void {
    const { y, m } = this.cursor();
    this.cursor.set(m === 11 ? { y: y + 1, m: 0 } : { y, m: m + 1 });
  }

  // — Sync (bouton nuage : push direct, comme Android) —
  protected syncNow(): void {
    void this.sync.syncAll().catch(() => this.snackbar.error('Échec de la synchronisation des tâches'));
  }

  // — Toggle done (occurrence = jour sélectionné) —
  protected async toggleDone(task: LocalTask, nowChecked: boolean): Promise<void> {
    const date = this.selectedDate() ?? task.dueDate;
    if (!date) return;
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

  // — Suppression —
  protected async confirmDelete(): Promise<void> {
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

  // — Création / édition —
  protected openCreate(defaultDate: string): void {
    this.editingTask.set(null);
    this.editMode = null;
    this.editOccurrenceDate = null;
    this.formTitle.set('');
    this.formKind.set('NONE');
    this.formDueDate.set(defaultDate);
    this.formStartDate.set(defaultDate);
    this.formEndDate.set(null);
    this.formWeekdays.set(new Set());
    this.formDueTime.set('');
    this.formReminder.set(null);
    this.showTaskForm.set(true);
  }

  /**
   * Dispatch édition (miroir B.4 Android) : tâche récurrente (W/M/Y) → dialog de portée
   * d'abord ; NONE → formulaire direct. `occurrenceDate` = jour cliqué dans le sheet
   * (null depuis la recherche → ONLY_THIS dégrade en ALL, comme Android).
   */
  protected requestEdit(task: LocalTask, occurrenceDate: string | null): void {
    this.editOccurrenceDate = occurrenceDate;
    const isRecurring =
      task.recurrenceKind === 'WEEKLY' || task.recurrenceKind === 'MONTHLY' || task.recurrenceKind === 'YEARLY';
    if (isRecurring) {
      this.pendingEditTask = task;
      this.editModeChoice.set('ONLY_THIS');
      this.showEditModeDialog.set(true);
    } else {
      this.editMode = null;
      this.openEditForm(task);
    }
  }

  protected confirmEditMode(): void {
    const task = this.pendingEditTask;
    this.showEditModeDialog.set(false);
    this.pendingEditTask = null;
    if (!task) return;
    this.editMode = this.editModeChoice();
    this.openEditForm(task);
  }
  protected cancelEditMode(): void {
    this.showEditModeDialog.set(false);
    this.pendingEditTask = null;
    this.editOccurrenceDate = null;
  }

  private openEditForm(task: LocalTask): void {
    this.editingTask.set(task);
    this.formTitle.set(task.title);
    this.formKind.set(task.recurrenceKind);
    this.formDueDate.set(task.dueDate ?? this.today);
    this.formStartDate.set(task.recurrenceStartDate ?? this.today);
    this.formEndDate.set(task.recurrenceEndDate ?? null);
    this.formWeekdays.set(new Set(task.recurrenceWeekdays ?? []));
    this.formDueTime.set(task.dueTime ?? '');
    this.formReminder.set(task.reminderMinutesBefore ?? null);
    this.showTaskForm.set(true);
  }

  protected toggleWeekday(idx: number): void {
    const next = new Set(this.formWeekdays());
    if (next.has(idx)) next.delete(idx);
    else next.add(idx);
    this.formWeekdays.set(next);
  }

  protected onDueTimeInput(input: string): void {
    this.formDueTime.set(
      [...input]
        .filter((c) => /\d|:/.test(c))
        .join('')
        .slice(0, 5),
    );
  }

  protected onDatePicked(iso: string): void {
    switch (this.datePickerTarget()) {
      case 'due':
        this.formDueDate.set(iso);
        break;
      case 'start':
        this.formStartDate.set(iso);
        break;
      case 'end':
        this.formEndDate.set(iso);
        break;
    }
    this.datePickerTarget.set(null);
  }

  protected async submitForm(): Promise<void> {
    if (!this.formValid()) return;
    const kind = this.formKind();
    const title = this.formTitle().trim();
    const dueTime = /^\d{2}:\d{2}$/.test(this.formDueTime().trim()) ? this.formDueTime().trim() : null;
    const now = new Date().toISOString();
    const editing = this.editingTask();
    this.showTaskForm.set(false);

    if (editing && this.editMode === 'ONLY_THIS' && this.editOccurrenceDate) {
      // Mode "cette occurrence seulement" : exclusion de la date sur l'originale + fork NONE.
      await this.db.tasks.update(editing.uuid, {
        excludedDates: [...new Set([...(editing.excludedDates ?? []), this.editOccurrenceDate])],
        synced: false,
        updatedAt: now,
      });
      const forked: LocalTask = {
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        title,
        notes: editing.notes ?? null,
        isActive: true,
        order: 0,
        recurrenceKind: 'NONE',
        dueDate: this.editOccurrenceDate,
        dueTime,
        periodUUID: null,
        recurrenceWeekdays: null,
        recurrenceStartDate: null,
        recurrenceEndDate: null,
        excludedDates: [],
        reminderMinutesBefore: this.formReminder(),
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.tasks.put(forked);
    } else if (editing) {
      // Mode ALL : remplacement complet des champs de récurrence.
      await this.db.tasks.update(editing.uuid, {
        title,
        recurrenceKind: kind,
        dueDate: kind === 'NONE' ? this.formDueDate() : null,
        dueTime,
        recurrenceWeekdays: kind === 'WEEKLY' ? [...this.formWeekdays()].sort((a, b) => a - b) : null,
        recurrenceStartDate: kind !== 'NONE' ? this.formStartDate() : null,
        recurrenceEndDate: kind !== 'NONE' ? this.formEndDate() : null,
        reminderMinutesBefore: this.formReminder(),
        synced: false,
        updatedAt: now,
      });
    } else {
      const row: LocalTask = {
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        title,
        notes: null,
        isActive: true,
        order: 0,
        recurrenceKind: kind,
        dueDate: kind === 'NONE' ? this.formDueDate() : null,
        dueTime,
        periodUUID: null,
        recurrenceWeekdays: kind === 'WEEKLY' ? [...this.formWeekdays()].sort((a, b) => a - b) : null,
        recurrenceStartDate: kind !== 'NONE' ? this.formStartDate() : null,
        recurrenceEndDate: kind !== 'NONE' ? this.formEndDate() : null,
        excludedDates: [],
        reminderMinutesBefore: this.formReminder(),
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.tasks.put(row);
      this.snackbar.info('Tâche ajoutée');
    }

    this.editingTask.set(null);
    this.editMode = null;
    this.editOccurrenceDate = null;
    void this.sync.syncAll().catch(() => undefined);
  }
}
