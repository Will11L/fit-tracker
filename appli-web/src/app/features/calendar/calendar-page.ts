import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { Router } from '@angular/router';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalPlannedWorkout } from '@core/models/planned-workout.model';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { LocalMuscle } from '@core/models/muscle.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { AuthService } from '@core/auth/auth.service';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { uuidv4 } from '@core/utils/uuid';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { FramedSection } from '@designsystem/common_components/framed-section';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { SummaryRow, type SummaryItemData } from '@designsystem/common_components/summary-row';
import { LabeledProgressBar } from '@designsystem/common_components/labeled-progress-bar';
import { CalendarMonthGrid } from '@designsystem/common_components/calendar-month-grid';
import { StatusIcon } from '@designsystem/common_components/status-icon';
import { AppIcon } from '@designsystem/icons/app-icon';
import { DonutChartComponent, type DonutSlice } from '@designsystem/common_components/donut-chart';
import { RadarChartComponent, type RadarAxis, type RadarSeries } from '@designsystem/common_components/radar-chart';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import {
  buildDayOptions,
  VIEW_ACTUAL_LABEL,
  VIEW_PLANNED_LABEL,
  CREATE_NEW_LABEL,
  FROM_PLANNED_LABEL,
} from './day-options';

/** Statut visuel d'une cellule jour (miroir de la logique CalendarDay/CalendarViewScreen Android). */
type DayStatus = 'completed' | 'skipped' | 'rest' | 'missed' | 'inProgress' | 'fallback';

const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

/** Zones anatomiques + libellé FR + couleur (mêmes tokens que la page Stats `zoneColors`). */
const ZONES = ['Chest', 'Back', 'Shoulders', 'Arms', 'Legs', 'Core'] as const;
const ZONE_FR: Record<string, string> = {
  Chest: 'Pectoraux', Back: 'Dos', Shoulders: 'Épaules', Arms: 'Bras', Legs: 'Jambes', Core: 'Abdos',
};
const ZONE_COLOR: Record<string, string> = {
  Chest: 'var(--app-primary-action)',
  Back: 'var(--c-orange-medium)',
  Shoulders: 'var(--app-accent-text)',
  Arms: 'var(--c-red-medium)',
  Legs: 'var(--c-medium-green)',
  Core: 'var(--c-yellow-medium)',
};

/** Une entrée de la légende (miroir de LegendEntry / CalendarLegendBottomSheet.kt). */
interface LegendEntry {
  icon: string;
  label: string;
  color: string;
}

/**
 * Écran Calendrier — miroir de CalendarViewScreen.kt : avancement du mois (barre + bouton info
 * → légende), résumé (semaines parfaites / jours faits / prochaine séance), grille mensuelle
 * Monday-first avec marqueurs de statut par jour (croisement séance réalisée `actual_workout`
 * × séance planifiée `planned_workout`). Clic sur un jour : ouvre toujours le bottom sheet
 * d'options du jour. En tête, jusqu'à deux actions de consultation selon l'état du jour —
 * « Voir la séance du jour » (→ écran Séance de l'actual du jour, si présent) et « Voir la
 * séance planifiée » (→ /planning?day=<jour>, si une séance est planifiée ce jour de semaine).
 * Puis toujours les deux actions d'écriture : créer une nouvelle séance via dialog nom, ou
 * démarrer depuis la séance planifiée du jour (= copie exercices + séries).
 * Données offline-first (Dexie liveQuery), écritures optimistes → syncAll.
 */
@Component({
  selector: 'app-calendar-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    FramedSection,
    ActionIconButton,
    LabeledProgressBar,
    SummaryRow,
    CalendarMonthGrid,
    StatusIcon,
    AppIcon,
    DonutChartComponent,
    RadarChartComponent,
    OptionsBottomSheet,
    AppBottomSheet,
    FormDialog,
    CustomTextField,
  ],
  template: `
    <section class="page">
      @if (!embedded()) { <app-screen-title-bar title="Calendrier" /> }

      <div class="page__body">
        <!-- Avancement du mois + calendrier + cards « Ce mois » : TOUT dans un même cadre
             thirdBlue (demande user 2026-07-15). Les sous-cadres, même fond, s'y fondent. -->
        <div class="mainframe">
        <!-- Avancement du mois + répartition par zone dans le même cadre : barre, puis 2 colonnes —
             donut (proportions, légende on-chart) à gauche, radar (équilibre par zone) à droite. -->
        <app-framed-section title="Avancement du mois">
          <div class="progress-row">
            <app-labeled-progress-bar
              class="progress-row__bar"
              [progress]="monthProgress()"
              troughColor="var(--c-second-blue)"
            />
            <app-action-icon-button icon="info" (clicked)="showLegend.set(true)" />
          </div>
          <div class="zone-charts">
            <app-donut-chart
              class="zone-charts__item"
              [slices]="zoneSlices()"
              [height]="200"
              [showSliceLabels]="true"
              [centerLabel]="zoneTotalLabel()"
              centerSub="séries"
              centerColor="var(--app-text-primary)"
              emptyText="Aucune séance"
            />
            <app-radar-chart
              class="zone-charts__item"
              [axes]="zoneRadar().axes"
              [series]="zoneRadar().series"
              [height]="200"
              [showLegend]="false"
              valueSuffix=" séries"
              emptyText="Aucune séance"
            />
          </div>
        </app-framed-section>

        <!-- Calendrier (gauche, plafonné 520px) + panneau « Ce mois » (droite) : cards empilées
             verticalement, comble l'espace à droite du calendrier. Empilé sous 900px. -->
        <div class="cal-row">
        <div class="cal">
          <div class="cal__header">
            <app-action-icon-button icon="chevron_left" backgroundColor="var(--c-first-blue)" (clicked)="prevMonth()" />
            <button type="button" class="cal__month" (click)="goToday()" title="Revenir à aujourd'hui">
              {{ monthLabel() }}
              @if (!isCurrentMonth()) {
                <app-icon name="today" [size]="16" color="var(--app-primary-action)" />
              }
            </button>
            <app-action-icon-button icon="chevron_right" backgroundColor="var(--c-first-blue)" (clicked)="nextMonth()" />
          </div>

          <div class="cal__weekdays">
            @for (w of weekdayLabels; track $index) {
              <span>{{ w }}</span>
            }
          </div>

          <app-calendar-month-grid [year]="year()" [month]="month()" [firstDayOffset]="firstDayOffset()">
            <ng-template let-iso>
              <button class="day" [class.day--today]="iso === todayIso" (click)="onDayClick(iso)">
                <span class="day__markers">
                  @if (cloudOf(iso) === 'done') {
                    <app-status-icon icon="cloud_done" tint="var(--app-primary-action)" />
                  } @else if (cloudOf(iso) === 'off') {
                    <app-status-icon icon="cloud_off" tint="var(--c-yellow-medium)" />
                  }
                  @switch (statusOf(iso)) {
                    @case ('rest') { <app-status-icon icon="bedtime" tint="var(--c-blue-medium)" /> }
                    @case ('missed') { <app-status-icon icon="check_indeterminate_small" tint="var(--c-dark-orange)" /> }
                    @case ('completed') { <app-status-icon icon="check" tint="var(--c-medium-green)" /> }
                    @case ('skipped') { <app-status-icon icon="close" tint="var(--c-red-medium)" /> }
                    @case ('inProgress') { <app-status-icon icon="arrow_forward" tint="var(--c-orange-medium)" /> }
                    @case ('fallback') { <app-status-icon icon="arrow_forward" tint="var(--c-dark-orange)" /> }
                  }
                </span>
                <span class="day__num">{{ dayNumOf(iso) }}</span>
              </button>
            </ng-template>
          </app-calendar-month-grid>
        </div>
        <!-- Résumé « Ce mois » : cards empilées verticalement, panneau à droite du calendrier. -->
        <app-framed-section class="cal-cards">
          <app-summary-row class="month-summary" [items]="summaryItems()" [compact]="true" />
        </app-framed-section>
        </div>
        </div>
      </div>

      <!-- Miroir DayOptionsBottomSheet.kt : options d'un jour sans séance. -->
      <app-options-bottom-sheet
        [open]="showDayOptions()"
        title="Options du jour"
        [actions]="dayOptions()"
        (dismissRequest)="closeDayOptions()"
        (actionSelected)="onDayOptionSelected($event)"
      />

      <!-- Miroir CreateActualWorkoutDialog.kt : nom de la nouvelle séance. -->
      <app-form-dialog
        [open]="showCreateDialog()"
        title="Démarrer une séance"
        confirmText="Démarrer"
        [confirmEnabled]="!createInvalid()"
        [disabledReason]="createDisabledReason()"
        (confirm)="onCreateConfirm()"
        (dismiss)="closeCreateDialog()"
      >
        <app-custom-text-field placeholder="Nom de la séance" [(value)]="createName" />
      </app-form-dialog>

      <!-- Miroir CalendarLegendBottomSheet.kt : légende des marqueurs. -->
      <app-bottom-sheet [open]="showLegend()" (dismissRequest)="showLegend.set(false)">
        <div class="legend">
          <app-titled-divider title="Légende" />
          <div class="legend__grid">
            @for (e of legendEntries; track e.label) {
              <div class="legend__cell">
                <app-status-icon [icon]="e.icon" [tint]="e.color" [size]="18" />
                <span class="legend__label">{{ e.label }}</span>
              </div>
            }
          </div>
        </div>
      </app-bottom-sheet>
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
      /* Cadre UNIQUE thirdBlue englobant Avancement + calendrier + cards « Ce mois »
         (les sous-cadres, même fond recessed, s'y fondent visuellement). */
      .mainframe {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Pas de padding propre : les sous-cadres (même fond, ils se fondent) portent déjà le
           padding canonique 16px — sinon il se cumulerait (32px visuels). */
        padding: 0;
      }
      /* Résumé « Ce mois » (panneau à droite du calendrier) : cards empilées verticalement (pleine
         largeur), fond second-blue pour ressortir sur le cadre. */
      .month-summary ::ng-deep .sr {
        flex-direction: column;
        align-items: stretch;
        gap: var(--space-2);
      }
      .month-summary ::ng-deep .si {
        background: var(--c-second-blue);
      }
      .progress-row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .progress-row__bar {
        flex: 1;
        min-width: 0;
      }
      /* Calendrier + panneau « Ce mois » côte à côte ; empilé sous 900px (comme la page nutrition). */
      .cal-row {
        display: flex;
        gap: var(--space-3);
        align-items: flex-start;
      }
      .cal {
        flex: 1 1 auto;
        min-width: 0;
        max-width: 520px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique 16px ; bas réduit (space-2) : l'en-tête bord-à-bord occupe le haut → symétrie. */
        padding: 16px 16px var(--space-2);
        box-sizing: border-box;
        /* Clippe le bandeau d'en-tête (marges négatives, bord-à-bord) aux coins arrondis. */
        overflow: hidden;
      }
      /* Panneau « Ce mois » (cards) à GAUCHE du calendrier (order -1) : comble l'espace restant. */
      .cal-cards {
        order: -1;
        flex: 1 1 0;
        min-width: 180px;
      }
      /* 2 colonnes sous la barre : donut (proportions) à gauche, radar (équilibre par zone) à droite. */
      .zone-charts {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--space-4);
        align-items: center;
        margin-top: var(--space-3);
      }
      .zone-charts__item {
        min-width: 0;
      }
      @media (max-width: 700px) {
        .zone-charts {
          grid-template-columns: 1fr;
        }
      }
      @media (max-width: 900px) {
        .cal-row {
          flex-direction: column;
        }
        .cal {
          max-width: none;
        }
      }
      /* En-tête « card » bord-à-bord (= calendrier nutrition) : bandeau second-blue qui touche le haut
         et les côtés (marges négatives annulant le padding), chevrons collés aux bords → gain de hauteur. */
      .cal__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        background: var(--c-second-blue);
        border-radius: var(--radius-md) var(--radius-md) 0 0;
        margin: -16px -16px var(--space-3);
        padding: 0;
      }
      /* Titre = bouton « revenir à aujourd'hui » (first-blue, 34px = chevrons). */
      .cal__month {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        height: 34px;
        background: var(--c-first-blue);
        border: none;
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        cursor: pointer;
        color: var(--app-text-tertiary);
        font-family: var(--font-family-base);
        font-size: 15px;
        font-weight: var(--font-weight-medium);
        text-transform: capitalize;
      }
      .cal__weekdays {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 6px;
        margin-bottom: var(--space-2);
      }
      /* Initiales L M M J V S D : même style que la démo CalendarMonthGrid du showcase. */
      .cal__weekdays span {
        text-align: center;
        font-size: 13px;
        font-weight: 600;
        color: var(--c-light-gray-blue);
      }
      .day {
        aspect-ratio: 1;
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 2px;
        background: transparent;
        border: 1px solid transparent;
        border-radius: var(--radius-sm);
        cursor: pointer;
        color: var(--app-text-tertiary);
      }
      .day:hover {
        background: var(--app-bg-surface);
      }
      .day--today {
        border-color: var(--app-primary-action);
      }
      .day__markers {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 2px;
        height: 18px;
      }
      .day__num {
        font-size: var(--font-size-body);
        font-weight: var(--font-weight-medium);
      }
      .legend {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        padding: 0 var(--space-4) var(--space-3);
      }
      .legend__grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: var(--space-3);
      }
      @media (max-width: 420px) {
        .legend__grid {
          grid-template-columns: repeat(2, 1fr);
        }
      }
      .legend__cell {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-sm);
        padding: 8px 10px;
      }
      .legend__label {
        color: var(--app-text-tertiary);
        font-size: 13px;
        white-space: nowrap;
      }
    `,
  ],
})
export class CalendarPage {
  /** Mode embarqué (hub Home) : masque la title bar (le hub fournit les onglets). */
  readonly embedded = input(false);

  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly snackbar = inject(SnackbarService);

  /** Abréviations Lun Mar Mer Jeu Ven Sam Dim. */
  protected readonly weekdayLabels = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
  protected readonly todayIso = this.isoOfDate(new Date());

  /**
   * Actions du bottom sheet jour (= DayOptionsBottomSheet.kt), dépendantes de l'état du jour
   * visé : actions de consultation en tête (seulement si pertinentes), puis les 2 actions
   * d'écriture seulement si le jour est encore vide (pas de séance existante). Cf. buildDayOptions (testé).
   */
  protected readonly dayOptions = computed<SheetAction[]>(() => {
    const iso = this.pendingDateIso();
    if (!iso) return buildDayOptions({ hasActual: false, hasPlannedSession: false });
    return buildDayOptions({
      hasActual: this.actualByDay().has(iso),
      hasPlannedSession: this.isPlannedSession(iso, this.plannedByWeekday()),
    });
  });

  /** Entrées de la légende (= CalendarLegendBottomSheet.kt, mêmes couples icône/couleur). */
  protected readonly legendEntries: LegendEntry[] = [
    { icon: 'check', label: 'Terminé', color: 'var(--c-medium-green)' },
    { icon: 'bedtime', label: 'Repos', color: 'var(--c-blue-medium)' },
    { icon: 'close', label: 'Passé', color: 'var(--c-red-medium)' },
    { icon: 'arrow_forward', label: 'Planifié', color: 'var(--c-orange-medium)' },
    { icon: 'cloud_done', label: 'Synchro', color: 'var(--app-primary-action)' },
    { icon: 'check_indeterminate_small', label: 'Manqué', color: 'var(--c-red-medium)' },
  ];

  /** Mois affiché {y, m(0-11)}. */
  private readonly cursor = signal(this.currentMonth());

  protected readonly year = computed(() => this.cursor().y);
  protected readonly month = computed(() => this.cursor().m);

  // ---------- Overlays ----------
  protected readonly showLegend = signal(false);
  protected readonly showDayOptions = signal(false);
  protected readonly showCreateDialog = signal(false);
  /** Jour visé par le sheet d'options / le dialog de création. */
  private readonly pendingDateIso = signal<string | null>(null);
  protected readonly createName = signal('');

  protected readonly createInvalid = computed(() => {
    const t = this.createName().trim();
    return t.length === 0 || t.toLowerCase() === 'rest day';
  });
  protected readonly createDisabledReason = computed(() => {
    const t = this.createName().trim();
    if (t.length === 0) return 'Le nom ne peut pas être vide.';
    if (t.toLowerCase() === 'rest day') return '« Rest Day » n’est pas autorisé.';
    return '';
  });

  private readonly actualWorkouts = toSignal(
    from(liveQuery(() => this.db.actual_workouts.filter((w) => !w.pendingDeletion).toArray())),
    { initialValue: [] as LocalActualWorkout[] },
  );
  private readonly plannedWorkouts = toSignal(
    from(liveQuery(() => this.db.planned_workouts.filter((w) => !w.pendingDeletion).toArray())),
    { initialValue: [] as LocalPlannedWorkout[] },
  );

  // Données du donut « volume par zone » du mois affiché (séances réalisées × muscles travaillés).
  private readonly actualExercises = toSignal(
    from(liveQuery(() => this.db.actual_workout_exercises.filter((e) => !e.pendingDeletion).toArray())),
    { initialValue: [] as LocalActualWorkoutExercise[] },
  );
  private readonly actualSets = toSignal(
    from(liveQuery(() => this.db.actual_workout_sets.filter((s) => !s.pendingDeletion).toArray())),
    { initialValue: [] as LocalActualWorkoutSet[] },
  );
  private readonly exerciseMuscles = toSignal(
    from(liveQuery(() => this.db.exercise_muscles.filter((x) => !x.pendingDeletion).toArray())),
    { initialValue: [] as LocalExerciseMuscle[] },
  );
  private readonly muscles = toSignal(
    from(liveQuery(() => this.db.muscles.filter((m) => !m.pendingDeletion).toArray())),
    { initialValue: [] as LocalMuscle[] },
  );

  /** Une séance réalisée par jour (la dernière si plusieurs). */
  private readonly actualByDay = computed(() => {
    const map = new Map<string, LocalActualWorkout>();
    for (const w of this.actualWorkouts()) map.set(w.date, w);
    return map;
  });

  /** Une séance planifiée par jour de semaine ("Monday"…). */
  private readonly plannedByWeekday = computed(() => {
    const map = new Map<string, LocalPlannedWorkout>();
    for (const p of this.plannedWorkouts()) map.set(p.dayOfWeek.trim(), p);
    return map;
  });

  protected readonly firstDayOffset = computed(() => {
    const { y, m } = this.cursor();
    return (new Date(y, m, 1).getDay() + 6) % 7; // Monday-first
  });

  protected readonly monthLabel = computed(() => {
    const { y, m } = this.cursor();
    return new Date(y, m, 1).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  });

  /** ISO de tous les jours du mois affiché. */
  private readonly monthDayIsos = computed(() => {
    const { y, m } = this.cursor();
    const days = new Date(y, m + 1, 0).getDate();
    const out: string[] = [];
    for (let d = 1; d <= days; d++) out.push(this.iso(y, m, d));
    return out;
  });

  /** Complétion du mois = séances faites / séances planifiées (hors Rest Day). */
  protected readonly monthProgress = computed(() => {
    const actual = this.actualByDay();
    const planned = this.plannedByWeekday();
    let plannedSessions = 0;
    let done = 0;
    for (const iso of this.monthDayIsos()) {
      if (this.isPlannedSession(iso, planned)) {
        plannedSessions++;
        if (actual.get(iso)?.isDone) done++;
      }
    }
    return plannedSessions === 0 ? 0 : done / plannedSessions;
  });

  /** Compteurs du mois affiché (1 passe) : séances planifiées, manquées (passées non faites),
   *  à faire (aujourd'hui/futur non faites). Alimente les tuiles du panneau « Ce mois ». */
  protected readonly monthCounts = computed(() => {
    const actual = this.actualByDay();
    const planned = this.plannedByWeekday();
    let plannedCount = 0;
    let missed = 0;
    let remaining = 0;
    for (const iso of this.monthDayIsos()) {
      if (!this.isPlannedSession(iso, planned)) continue;
      plannedCount++;
      if (actual.get(iso)?.isDone === true) continue;
      if (iso < this.todayIso) missed++;
      else remaining++;
    }
    return { plannedCount, missed, remaining };
  });

  protected readonly summaryItems = computed<SummaryItemData[]>(() => {
    const next = this.nextWorkoutIso();
    const c = this.monthCounts();
    return [
      {
        icon: 'fitness_center',
        value: `${c.plannedCount} séances`,
        label: 'Planifiées',
        iconTint: 'var(--app-accent-text)',
      },
      {
        icon: 'check_circle',
        value: `${this.completedDays()} jours`,
        label: 'Fait',
        iconTint: 'var(--c-medium-green)',
      },
      {
        icon: 'schedule',
        value: `${c.remaining}`,
        label: 'À faire',
        iconTint: 'var(--c-orange-medium)',
      },
      {
        icon: 'close',
        value: `${c.missed}`,
        label: 'Manquées',
        iconTint: 'var(--c-red-medium)',
      },
      {
        icon: 'local_fire_department',
        value: `${this.perfectWeeks()} semaines`,
        label: 'Série',
        iconTint: 'var(--c-orange-medium)',
      },
      {
        icon: 'calendar_month',
        value: next ? this.shortDate(next) : '—',
        label: 'À venir',
        iconTint: 'var(--app-accent-text)',
      },
    ];
  });

  private readonly completedDays = computed(() => {
    const actual = this.actualByDay();
    return this.monthDayIsos().filter((iso) => actual.get(iso)?.isDone).length;
  });

  // ─── Donut « volume par zone » du mois affiché ───
  /** « Séries effectives » par zone sur le mois : Σ du coefficient de ciblage muscle→exercice sur les
   *  séries DONE (reps > 0), ventilé par zone. Métrique d'ÉQUILIBRE insensible à la charge (≠ tonnage
   *  de la page Stats, biaisé vers les grosses zones/charges) → petites et grosses zones comparées
   *  équitablement, et les exercices au poids du corps (charge 0) comptent aussi. */
  private readonly zoneVolume = computed<Record<string, number>>(() => {
    const monthIsos = new Set(this.monthDayIsos());
    const monthActualUuids = new Set<string>();
    for (const w of this.actualWorkouts()) if (monthIsos.has(w.date)) monthActualUuids.add(w.uuid);
    if (monthActualUuids.size === 0) return {};

    // aweUuid → exerciseUUID (limité aux séances du mois affiché)
    const exerciseByAwe = new Map<string, string>();
    for (const e of this.actualExercises()) {
      if (monthActualUuids.has(e.actualWorkoutUUID)) exerciseByAwe.set(e.uuid, e.exerciseUUID);
    }
    // exerciseUUID → [{ muscleUUID, coefficient }]
    const musclesByExercise = new Map<string, { muscleUUID: string; coefficient: number }[]>();
    for (const x of this.exerciseMuscles()) {
      const link = { muscleUUID: x.muscleUUID, coefficient: x.coefficient };
      const arr = musclesByExercise.get(x.exerciseUUID);
      if (arr) arr.push(link);
      else musclesByExercise.set(x.exerciseUUID, [link]);
    }
    // muscleUUID → zone
    const zoneByMuscle = new Map<string, string>();
    for (const m of this.muscles()) if (m.zone) zoneByMuscle.set(m.uuid, m.zone);

    const vol: Record<string, number> = {};
    for (const s of this.actualSets()) {
      if (s.status !== 'DONE' || s.reps <= 0) continue;
      const exUuid = exerciseByAwe.get(s.actualWorkoutExerciseUUID);
      if (!exUuid) continue;
      // Chaque série faite compte pour son coefficient de ciblage (série « effective »), sans la charge.
      for (const em of musclesByExercise.get(exUuid) ?? []) {
        const zone = zoneByMuscle.get(em.muscleUUID);
        if (!zone) continue;
        vol[zone] = (vol[zone] ?? 0) + em.coefficient;
      }
    }
    return vol;
  });

  /** Parts du donut : une par zone ayant du volume, dans l'ordre canonique, couleur de zone. */
  protected readonly zoneSlices = computed<DonutSlice[]>(() => {
    const vol = this.zoneVolume();
    return ZONES.map((z) => ({ label: ZONE_FR[z], value: vol[z] ?? 0, color: ZONE_COLOR[z] })).filter(
      (s) => s.value > 0,
    );
  });

  private readonly zoneTotal = computed(() => this.zoneSlices().reduce((sum, s) => sum + s.value, 0));

  /** Libellé central du donut = total des séries effectives du mois (compacté en « k »). */
  protected readonly zoneTotalLabel = computed(() => this.formatVol(this.zoneTotal()));

  /** Radar « équilibre par zone » : un axe par zone (couleur de zone), une série remplie = séries
   *  effectives par zone. Vide si aucune donnée (→ placeholder du composant). */
  protected readonly zoneRadar = computed<{ axes: RadarAxis[]; series: RadarSeries[] }>(() => {
    const vol = this.zoneVolume();
    const values = ZONES.map((z) => vol[z] ?? 0);
    if (values.every((v) => v <= 0)) return { axes: [], series: [] };
    return {
      axes: ZONES.map((z) => ({ label: ZONE_FR[z], color: ZONE_COLOR[z] })),
      series: [{ name: 'Séries', values, color: 'var(--app-primary-action)', area: true }],
    };
  });

  /** 1er jour (aujourd'hui inclus, +7) avec une séance planifiée non faite. */
  private readonly nextWorkoutIso = computed(() => {
    const planned = this.plannedByWeekday();
    const actual = this.actualByDay();
    const todayDone = actual.get(this.todayIso)?.isDone === true;
    for (let i = 0; i <= 7; i++) {
      const iso = this.addDays(this.todayIso, i);
      if (this.isPlannedSession(iso, planned) && !(iso === this.todayIso && todayDone)) return iso;
    }
    return null;
  });

  /** Semaines (lun→dim) intersectant le mois où toutes les séances planifiées sont faites. */
  private readonly perfectWeeks = computed(() => {
    const actual = this.actualByDay();
    const planned = this.plannedByWeekday();
    const { y, m } = this.cursor();
    const start = this.iso(y, m, 1);
    const end = this.iso(y, m, new Date(y, m + 1, 0).getDate());
    const cur = this.currentMonth();
    const effectiveEnd = cur.y === y && cur.m === m && this.todayIso < end ? this.todayIso : end;

    const firstMonday = this.addDays(start, -this.weekdayIdx(start));
    const lastSunday = this.addDays(end, 6 - this.weekdayIdx(end));

    let total = 0;
    let weekStart = firstMonday;
    while (weekStart <= lastSunday) {
      const days: string[] = [];
      for (let i = 0; i < 7; i++) {
        const d = this.addDays(weekStart, i);
        if (d >= start && d <= effectiveEnd) days.push(d);
      }
      if (days.length > 0) {
        const perfect = days.every((d) =>
          this.isPlannedSession(d, planned) ? actual.get(d)?.isDone === true : true,
        );
        if (perfect) total++;
      }
      weekStart = this.addDays(weekStart, 7);
    }
    return total;
  });

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }

  protected prevMonth(): void {
    const { y, m } = this.cursor();
    this.cursor.set(m === 0 ? { y: y - 1, m: 11 } : { y, m: m - 1 });
  }

  protected nextMonth(): void {
    const { y, m } = this.cursor();
    this.cursor.set(m === 11 ? { y: y + 1, m: 0 } : { y, m: m + 1 });
  }

  /** Revient au mois courant (clic sur le titre). */
  protected goToday(): void {
    this.cursor.set(this.currentMonth());
  }

  /** Vrai si le mois affiché = mois courant (masque l'icône « today » du titre). */
  protected readonly isCurrentMonth = computed(() => {
    const cur = this.currentMonth();
    const { y, m } = this.cursor();
    return y === cur.y && m === cur.m;
  });

  protected dayNumOf(iso: string): number {
    return Number(iso.slice(8, 10));
  }

  protected cloudOf(iso: string): 'done' | 'off' | null {
    const aw = this.actualByDay().get(iso);
    if (!aw) return null;
    return aw.synced ? 'done' : 'off';
  }

  protected statusOf(iso: string): DayStatus {
    const aw = this.actualByDay().get(iso);
    const planned = this.plannedByWeekday().get(this.weekdayNameOf(iso));
    const isToday = iso === this.todayIso;
    const isPast = iso < this.todayIso;
    const isFuture = iso > this.todayIso;

    const plannedIsRest = !planned || planned.name.trim().toLowerCase() === 'rest day';
    const plannedHasSession = !plannedIsRest;
    const hasActual = !!aw;
    const actualNotDone = hasActual && !aw!.isDone;

    if (hasActual && aw!.isDone) return 'completed';
    if (actualNotDone && (isPast || (isToday && plannedIsRest))) return 'skipped';
    if (!hasActual && plannedIsRest) return 'rest';
    if (!hasActual && isPast && plannedHasSession) return 'missed';
    if ((!hasActual && (isToday || isFuture) && plannedHasSession) || (actualNotDone && isToday && !plannedIsRest))
      return 'inProgress';
    // Miroir du else Android (CalendarDay fallback) : actual pas fini sur un jour futur.
    return 'fallback';
  }

  /** Clic jour : ouvre toujours le sheet d'options (les actions s'adaptent à l'état du jour). */
  protected onDayClick(iso: string): void {
    this.pendingDateIso.set(iso);
    this.showDayOptions.set(true);
  }

  protected closeDayOptions(): void {
    this.showDayOptions.set(false);
    this.pendingDateIso.set(null);
  }

  protected onDayOptionSelected(label: string): void {
    const iso = this.pendingDateIso();
    this.showDayOptions.set(false);
    if (!iso) return;

    if (label === VIEW_ACTUAL_LABEL) {
      this.pendingDateIso.set(null);
      this.viewActualOfDay(iso);
      return;
    }
    if (label === VIEW_PLANNED_LABEL) {
      this.pendingDateIso.set(null);
      this.viewPlannedOfDay(iso);
      return;
    }
    if (label === CREATE_NEW_LABEL) {
      // On garde pendingDateIso pour le dialog de création (consommé à la confirmation).
      this.createName.set('');
      this.showCreateDialog.set(true);
      return;
    }
    if (label === FROM_PLANNED_LABEL) {
      this.pendingDateIso.set(null);
      void this.startFromPlanned(iso);
    }
  }

  /** « Voir la séance du jour » → écran Séance de l'actual du jour (ou snackbar si absent). */
  private viewActualOfDay(iso: string): void {
    const aw = this.actualByDay().get(iso);
    if (!aw) {
      this.snackbar.info('Aucune séance ce jour');
      return;
    }
    void this.router.navigate(['/session', aw.uuid]);
  }

  /**
   * « Voir la séance planifiée » → /planning, avec le jour de semaine en queryParam pour que
   * planning-page pré-sélectionne ce jour. Le planning est hebdomadaire (pas de route par
   * date) : on cible donc le jour de semaine, pas la date.
   */
  private viewPlannedOfDay(iso: string): void {
    const planned = this.plannedByWeekday().get(this.weekdayNameOf(iso));
    if (!planned || planned.name.trim().toLowerCase() === 'rest day') {
      this.snackbar.info(`Repos — aucune séance planifiée pour ${this.frWeekday(iso)}`);
      return;
    }
    void this.router.navigate(['/planning'], { queryParams: { day: this.weekdayNameOf(iso) } });
  }

  protected closeCreateDialog(): void {
    this.showCreateDialog.set(false);
    this.pendingDateIso.set(null);
  }

  protected onCreateConfirm(): void {
    if (this.createInvalid()) return;
    const iso = this.pendingDateIso();
    const name = this.createName().trim();
    this.closeCreateDialog();
    if (!iso) return;
    void this.createNewActualWorkout(iso, name);
  }

  /**
   * Crée une séance vide nommée sur la date (= createNewActualWorkoutForDate du
   * CalendarViewModel) : dédoublonne par jour, marque les exercices planifiés du jour
   * comme ignorés, sync, puis navigue vers la séance.
   */
  private async createNewActualWorkout(iso: string, name: string): Promise<void> {
    const uid = this.auth.currentUser()?.id;
    if (!uid) {
      this.snackbar.error('Identifiant utilisateur introuvable');
      return;
    }
    try {
      const existing = await this.findActualByDay(iso);
      if (existing) {
        void this.router.navigate(['/session', existing.uuid]);
        return;
      }

      const now = new Date().toISOString();
      const uuid = uuidv4();
      const actual: LocalActualWorkout = {
        uuid,
        userId: uid,
        name,
        date: iso,
        notes: null,
        location: null,
        isDone: false,
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.actual_workouts.put(actual);

      // Séance custom à la place du planning : on ignore les exercices planifiés du jour.
      const planned = this.plannedByWeekday().get(this.weekdayNameOf(iso));
      if (planned && planned.name.trim().toLowerCase() !== 'rest day') {
        const pwes = (
          await this.db.planned_workout_exercises.where('plannedWorkoutUUID').equals(planned.uuid).toArray()
        ).filter((e) => !e.pendingDeletion);
        for (const pwe of pwes) {
          await this.db.planned_workout_exercises.update(pwe.uuid, {
            ignored: true,
            synced: false,
            updatedAt: now,
          });
        }
      }

      this.triggerSync();
      void this.router.navigate(['/session', uuid]);
    } catch {
      this.snackbar.error('Échec de création de la séance');
    }
  }

  /**
   * Démarre la séance planifiée du jour (= startActualWorkoutFromPlannedOnDate du
   * CalendarViewModel) : copie les exercices planifiés (non ignorés) + leurs séries
   * vides, sync, puis navigue vers la séance.
   */
  private async startFromPlanned(iso: string): Promise<void> {
    const uid = this.auth.currentUser()?.id;
    if (!uid) {
      this.snackbar.error('Identifiant utilisateur introuvable');
      return;
    }
    try {
      const planned = this.plannedByWeekday().get(this.weekdayNameOf(iso));
      if (!planned || planned.name.trim().toLowerCase() === 'rest day') {
        this.snackbar.info(`Aucune séance planifiée pour ${this.frWeekday(iso)}`);
        return;
      }

      const existing = await this.findActualByDay(iso);
      if (existing) {
        void this.router.navigate(['/session', existing.uuid]);
        return;
      }

      const now = new Date().toISOString();
      const workoutUuid = uuidv4();
      const actual: LocalActualWorkout = {
        uuid: workoutUuid,
        userId: uid,
        name: planned.name,
        date: iso,
        notes: null,
        location: null,
        isDone: false,
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.actual_workouts.put(actual);

      const pwes = (
        await this.db.planned_workout_exercises.where('plannedWorkoutUUID').equals(planned.uuid).toArray()
      )
        .filter((e) => !e.ignored && !e.pendingDeletion)
        .sort((a, b) => a.order - b.order);

      const awes: LocalActualWorkoutExercise[] = [];
      const sets: LocalActualWorkoutSet[] = [];
      for (const pwe of pwes) {
        const aweUuid = uuidv4();
        awes.push({
          uuid: aweUuid,
          actualWorkoutUUID: workoutUuid,
          exerciseUUID: pwe.exerciseUUID,
          sets: pwe.sets,
          reps: pwe.reps,
          phase: pwe.phase,
          status: 'NOT_STARTED',
          order: pwe.order,
          addedManually: false,
          updatedAt: now,
          synced: false,
          pendingDeletion: false,
        });
        for (let i = 1; i <= pwe.sets; i++) {
          sets.push({
            uuid: uuidv4(),
            actualWorkoutExerciseUUID: aweUuid,
            setOrder: i,
            reps: 0,
            weight: 0,
            isDropset: false,
            notes: null,
            recommendation: null,
            status: 'NOT_STARTED',
            updatedAt: now,
            synced: false,
            pendingDeletion: false,
          });
        }
      }
      await this.db.actual_workout_exercises.bulkPut(awes);
      await this.db.actual_workout_sets.bulkPut(sets);

      this.triggerSync();
      void this.router.navigate(['/session', workoutUuid]);
    } catch {
      this.snackbar.error('Échec de création de la séance');
    }
  }

  /** Dernière séance réalisée (non supprimée) sur un jour donné. */
  private async findActualByDay(iso: string): Promise<LocalActualWorkout | undefined> {
    const rows = await this.db.actual_workouts.where('date').equals(iso).toArray();
    return rows.filter((w) => !w.pendingDeletion).at(-1);
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }

  private frWeekday(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('fr-FR', { weekday: 'long' });
  }

  private shortDate(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
  }

  /** Volume compact : ≥ 1000 → « 12.3k », sinon arrondi entier. */
  private formatVol(v: number): string {
    return v >= 1000 ? `${(v / 1000).toFixed(1)}k` : Math.round(v).toString();
  }

  private isPlannedSession(iso: string, planned: Map<string, LocalPlannedWorkout>): boolean {
    const p = planned.get(this.weekdayNameOf(iso));
    return !!p && p.name.trim().toLowerCase() !== 'rest day';
  }

  private weekdayNameOf(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return WEEKDAYS[new Date(y, m - 1, d).getDay()];
  }

  private weekdayIdx(iso: string): number {
    const [y, m, d] = iso.split('-').map(Number);
    return (new Date(y, m - 1, d).getDay() + 6) % 7; // Monday-first 0..6
  }

  private addDays(iso: string, n: number): string {
    const [y, m, d] = iso.split('-').map(Number);
    return this.isoOfDate(new Date(y, m - 1, d + n));
  }

  private iso(y: number, m: number, d: number): string {
    return `${y}-${this.pad(m + 1)}-${this.pad(d)}`;
  }

  private isoOfDate(date: Date): string {
    return `${date.getFullYear()}-${this.pad(date.getMonth() + 1)}-${this.pad(date.getDate())}`;
  }

  private pad(n: number): string {
    return n.toString().padStart(2, '0');
  }

  private currentMonth(): { y: number; m: number } {
    const now = new Date();
    return { y: now.getFullYear(), m: now.getMonth() };
  }
}
