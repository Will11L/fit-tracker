import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalExercise } from '@core/models/exercise.model';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { SingleSelectDropdown } from '@designsystem/common_components/single-select-dropdown';
import { CustomDatePickerDialog } from '@designsystem/common_components/custom-date-picker-dialog';
import { AppIcon } from '@designsystem/icons/app-icon';
import { SettingsStore } from '../settings/settings-store';
import { StatsSectionChart, type StatsSeries } from '../stats/stats-section-chart';
import { groupShadeMap, resolveCssColor, zoneColorMap } from '../stats/palette-util';
import { MuscleRepository } from './muscle.repository';

type RangeKey = 'W1' | 'D30' | 'M3' | 'M6' | 'Y1' | 'ALL' | 'CUSTOM';
type MetricKey = 'Sets' | 'Volume';

const RANGE_CHIPS: { key: RangeKey; label: string }[] = [
  { key: 'W1', label: '1 semaine' },
  { key: 'D30', label: '30 jours' },
  { key: 'M3', label: '3 mois' },
  { key: 'M6', label: '6 mois' },
  { key: 'Y1', label: '1 an' },
  { key: 'ALL', label: 'Tout' },
  { key: 'CUSTOM', label: 'Personnalisé' },
];

const KG_TO_LBS = 2.2046226218;

// Hiérarchie canonique (miroir core/data/Zones.kt + MuscleGroups.kt) pour le dialog zone/groupe.
const ZONES_ALL = ['Chest', 'Back', 'Shoulders', 'Arms', 'Legs', 'Core'];
const GROUPS_ALL = [
  'Pecs',
  'Lats', 'Rhomboids', 'Erector Spinae', 'Traps',
  'Delts',
  'Biceps', 'Triceps', 'Brachialis', 'Forearms',
  'Quads', 'Hamstrings', 'Glutes', 'Calves', 'Adductors',
  'Abs', 'Obliques',
];

/** Stat hebdo agrégée du muscle (miroir MuscleWeeklyVolumeRow Android). */
interface WeeklyStat {
  weekIso: string; // YYYY-WW (sémantique SQLite strftime %Y-%W)
  setCount: number;
  volume: number;
}

/**
 * Écran détail Muscle — miroir flat de MuscleScreen.kt (Android, refonte 2026-06-11) :
 * - Actions : retour · favori (orange si favori) · sync · Delavier (book, snackbar « Bientôt
 *   disponible ») · supprimer (rouge, ConfirmationDialog).
 * - Card Détails (Zone + Groupe) avec crayon → FormDialog « Modifier zone et groupe »
 *   (2 SingleSelectDropdown sur les listes canoniques Zones/MuscleGroups).
 * - Stats : chips de période + chart volume hebdo (sémantique exacte de observeMuscleWeeklyVolume :
 *   buckets %Y-%W, pendingDeletion exclu sets/awe/aw, sans coefficient) + légende cliquable
 *   Séries (bleu) / Volume hebdo (orange).
 * - Exercices liés : rows nom + flèche blueMedium → /exercise/:uuid.
 * Mode `embedded` (colonne détail du master-detail /muscles) : pas de title bar ni de bouton retour.
 */
@Component({
  selector: 'app-muscle-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    ActionIconButton,
    ConfirmationDialog,
    FormDialog,
    SingleSelectDropdown,
    CustomDatePickerDialog,
    StatsSectionChart,
    AppIcon,
  ],
  template: `
    <section class="page">
      @if (!embedded()) {
        <app-screen-title-bar [title]="muscle()?.name ?? 'Muscle'" />
      }

      @if (!muscle()) {
        <div class="page__missing">Muscle introuvable.</div>
      } @else {
        <div class="page__body">
          <!-- Cadre UNIQUE : nom (embedded) + barre d'actions + détails, séparés par un filet. -->
          <div class="maincard">
          @if (embedded()) {
            <app-titled-divider [title]="muscle()!.name" />
          }

          <!-- Barre d'actions (miroir MuscleScreen.kt : back · favori · sync · Delavier · supprimer). -->
          <div class="actions">
            <app-action-icon-button
              [icon]="muscle()!.isFavorite ? 'star' : 'star_border'"
              [backgroundColor]="
                muscle()!.isFavorite
                  ? 'var(--c-orange-medium)'
                  : 'color-mix(in srgb, var(--app-text-tertiary) 70%, transparent)'
              "
              (clicked)="toggleFav()"
            />
            <app-action-icon-button
              [icon]="muscle()!.synced ? 'cloud_done' : 'cloud_off'"
              [hasBackground]="false"
              [tint]="muscle()!.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
              (clicked)="refresh()"
            />
            <!-- Delavier Method (book sur fond selectedFill) — page anatomie pas encore portée. -->
            <app-action-icon-button icon="book" backgroundColor="var(--app-selected-fill)" (clicked)="delavierSoon()" />
            <app-action-icon-button
              icon="delete_forever"
              backgroundColor="var(--app-btn-danger-bg)"
              tint="var(--app-btn-danger-fg)"
              (clicked)="showDelete.set(true)"
            />
          </div>

          <div class="sep"></div>

          <!-- Section Zone + Groupe avec crayon (miroir cadre Détails MuscleScreen). -->
          <div class="card">
            <app-action-icon-button
              class="card__edit"
              icon="edit"
              (clicked)="openEditZoneGroup()"
            />
            <p class="card__line"><span class="card__label">Zone :</span> {{ muscle()!.zone || '—' }}</p>
            <p class="card__line"><span class="card__label">Groupe :</span> {{ muscle()!.muscleGroup || '—' }}</p>
          </div>
          </div>

          <!-- Cadre Stats : titre + chips de période + chart + légende. -->
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
            granularity="WEEKLY"
            metric="SETS"
            [height]="240"
          />

          <!-- Légende cliquable = filtre de visibilité (miroir StatFilterButton : Séries / Volume hebdo). -->
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

          <!-- Cadre Exercices liés : titre + rows à plat, séparées par un filet. -->
          <div class="maincard">
          <app-titled-divider title="Exercices liés" />

          <div class="exos">
          @if (relatedExercises().length === 0) {
            <div class="empty"><div class="empty__frame">Aucun exercice lié à ce muscle.</div></div>
          } @else {
            @for (r of relatedRows(); track r.ex.uuid) {
              <div class="exo-row">
                <span class="exo-row__name">{{ r.ex.name }}</span>
                <!-- Coef du muscle courant (teinté couleur du muscle) + chips groupe/zone dominants. -->
                <span class="exo-row__tags">
                  @if (r.coef !== null) {
                    <span class="row__tag" [style.--tag-c]="muscleColor()">×{{ r.coef }}</span>
                  }
                  @if (r.group) {
                    <span class="row__tag" [style.--tag-c]="r.groupColor">{{ r.group }}</span>
                  }
                  @if (r.zone) {
                    <span class="row__tag" [style.--tag-c]="r.zoneColor">{{ r.zone }}</span>
                  }
                </span>
                <!-- Séries × reps recommandées : colonne dédiée (alignée inter-rows). -->
                <span class="exo-row__setsreps">{{ r.setsReps }}</span>
                <!-- Dernière fois fait : colonne dédiée (alignée verticalement inter-rows). -->
                <span class="exo-row__ago">{{ r.lastDone }}</span>
                <app-action-icon-button
                  icon="arrow_right_alt"
                  tint="var(--app-text-primary)"
                  backgroundColor="var(--c-blue-medium)"
                  (clicked)="goExercise(r.ex.uuid)"
                />
              </div>
            }
          }
          </div>
          </div>
        </div>
      }

      <app-confirmation-dialog
        [open]="showDelete()"
        title="Supprimer le muscle ?"
        message="Supprimer ce muscle ? Cette action est irréversible."
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDelete()"
        (dismiss)="showDelete.set(false)"
      />

      <!-- Dialog zone + groupe (miroir FormDialog MuscleScreen : 2 selects canoniques). -->
      <app-form-dialog
        [open]="showEditZoneGroup()"
        title="Modifier zone et groupe"
        confirmText="Enregistrer"
        (confirm)="confirmEditZoneGroup()"
        (dismiss)="showEditZoneGroup.set(false)"
      >
        <app-single-select-dropdown
          label="Zone"
          [selected]="draftZone()"
          [options]="zonesAll"
          (select)="draftZone.set($event)"
        />
        <app-single-select-dropdown
          label="Groupe"
          [selected]="draftGroup()"
          [options]="groupsAll"
          (select)="draftGroup.set($event)"
        />
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
        /* Répartition avec de l'espace aussi aux extrémités de la ligne (miroir Exercices). */
        justify-content: space-evenly;
        align-items: center;
      }
      /* Cadres de la page (padding canonique 16px) : ① nom + actions + détails (séparés par un
         filet secondBlue .sep) · ② Stats · ③ Exercices liés. */
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
      /* Section détails — crayon en haut à droite, à plat dans le cadre. */
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
        font-weight: var(--font-weight-medium);
        padding-right: 36px; /* évite le crayon */
      }
      .card__label {
        color: var(--app-primary-action);
        font-weight: normal;
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
      /* Rows exercices liés — à plat dans le cadre, séparées par un filet secondBlue. */
      .exos {
        display: flex;
        flex-direction: column;
      }
      .exos > *:not(:last-child) {
        border-bottom: 1px solid var(--c-second-blue);
      }
      .exo-row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        padding: 6px var(--space-3);
      }
      .exo-row__name {
        flex: 1;
        min-width: 0;
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      /* Colonne chips : coef + groupe/zone, alignées à gauche sur la même verticale inter-rows. */
      .exo-row__tags {
        /* Part plus large : 3 chips + gaps débordaient d'un tiers strict (surplomb sur séries×reps). */
        flex: 1.6;
        min-width: 0;
        display: flex;
        justify-content: flex-start;
        align-items: center;
        gap: var(--space-2);
        white-space: nowrap;
      }
      /* Séries×reps et « il y a X » : largeurs égales ENTRE ELLES, alignement inter-rows conservé. */
      .exo-row__setsreps {
        flex: 0.7;
        min-width: 0;
        color: var(--app-primary-action);
        font-size: 12px;
        white-space: nowrap;
      }
      .exo-row__ago {
        flex: 0.7;
        min-width: 0;
        color: var(--c-blue-medium);
        font-size: 12px;
        white-space: nowrap;
      }
      /* Chips au style du badge catégorie du catalogue (miroir des listes Exercices/Muscles). */
      .row__tag {
        font-size: 11px;
        font-weight: var(--font-weight-medium);
        line-height: 1;
        padding: 4px 9px;
        border-radius: 999px;
        color: var(--tag-c);
        color: oklch(from var(--tag-c) calc(l + 0.1) calc(c * 1.25) h);
        background: color-mix(in srgb, var(--tag-c) 20%, transparent);
      }
      /* Empty state — mêmes codes que l'empty state des charts (bgRecessed + cadre primaryAction). */
      .empty {
        height: 72px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3);
        box-sizing: border-box;
      }
      .empty__frame {
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
    `,
  ],
})
export class MuscleDetailPage {
  /** UUID du muscle (passé par la page combinée /muscles). */
  readonly uuid = input.required<string>();
  /** Mode embarqué (colonne détail du master-detail) : pas de title bar ni de bouton retour. */
  readonly embedded = input(false);

  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly repo = inject(MuscleRepository);
  private readonly settings = inject(SettingsStore);
  private readonly router = inject(Router);
  private readonly snackbar = inject(SnackbarService);

  protected readonly rangeChips = RANGE_CHIPS;
  protected readonly zonesAll = ZONES_ALL;
  protected readonly groupsAll = GROUPS_ALL;

  // ─── Données Dexie ───
  private readonly muscles = this.repo.muscles;
  private readonly exerciseMuscles = toSignal(from(liveQuery(() => this.db.exercise_muscles.toArray())), {
    initialValue: [] as LocalExerciseMuscle[],
  });
  private readonly exercises = toSignal(from(liveQuery(() => this.db.exercises.toArray())), {
    initialValue: [] as LocalExercise[],
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

  protected readonly muscle = computed(
    () => this.muscles().find((m) => m.uuid === this.uuid() && !m.pendingDeletion) ?? null,
  );

  /** UUIDs des exercices liés au muscle (jonction exercise_muscles, miroir des JOIN DAO). */
  private readonly linkedExerciseUuids = computed(() => {
    const set = new Set<string>();
    for (const em of this.exerciseMuscles()) {
      if (em.muscleUUID === this.uuid()) set.add(em.exerciseUUID);
    }
    return set;
  });

  /** Exercices liés (miroir observeExercisesByMuscle), triés par nom. */
  protected readonly relatedExercises = computed(() =>
    this.exercises()
      .filter((e) => !e.pendingDeletion && this.linkedExerciseUuids().has(e.uuid))
      .sort((a, b) => a.name.localeCompare(b.name)),
  );

  /** Couleur du muscle courant (nuance de son groupe, repli couleur de zone) — teinte la chip coef. */
  protected readonly muscleColor = computed(() => {
    const m = this.muscle();
    if (!m) return '';
    const shade = m.muscleGroup ? groupShadeMap(this.muscles()).get(m.muscleGroup) : undefined;
    return shade ?? (m.zone ? (zoneColorMap()[m.zone] ?? '') : '');
  });

  /** Rows exercices liés enrichies : coef du muscle COURANT + chips groupe/zone du muscle DOMINANT
   *  de l'exercice (même code couleur que les listes). */
  protected readonly relatedRows = computed(() => {
    const muscleUuid = this.uuid();
    const zoneColors = zoneColorMap();
    const groups = groupShadeMap(this.muscles());
    const byUuid = new Map(this.muscles().map((m) => [m.uuid, m]));
    const linksByEx = new Map<string, LocalExerciseMuscle[]>();
    for (const em of this.exerciseMuscles()) {
      const arr = linksByEx.get(em.exerciseUUID) ?? [];
      arr.push(em);
      linksByEx.set(em.exerciseUUID, arr);
    }
    // Dernière séance contenant chaque exercice (pendingDeletion exclu aw/awe, comme le chart).
    const awDates = new Map(
      this.workouts().filter((w) => !w.pendingDeletion && w.date).map((w) => [w.uuid, w.date!.slice(0, 10)]),
    );
    const lastByEx = new Map<string, string>();
    for (const awe of this.awExercises()) {
      if (awe.pendingDeletion) continue;
      const d = awDates.get(awe.actualWorkoutUUID);
      if (!d) continue;
      const cur = lastByEx.get(awe.exerciseUUID);
      if (!cur || d > cur) lastByEx.set(awe.exerciseUUID, d);
    }
    return this.relatedExercises().map((e) => {
      const links = linksByEx.get(e.uuid) ?? [];
      const coef = links.find((l) => l.muscleUUID === muscleUuid)?.coefficient ?? null;
      const top = links.length ? links.reduce((a, b) => (b.coefficient > a.coefficient ? b : a)) : null;
      const m = top ? byUuid.get(top.muscleUUID) : undefined;
      const last = lastByEx.get(e.uuid);
      return {
        ex: e,
        coef,
        group: m?.muscleGroup ?? '',
        groupColor: (m?.muscleGroup ? groups.get(m.muscleGroup) : undefined) ?? (m?.zone ? zoneColors[m.zone] : '') ?? '',
        zone: m?.zone ?? '',
        zoneColor: m?.zone ? (zoneColors[m.zone] ?? '') : '',
        lastDone: last ? this.agoLabel(last) : 'jamais fait',
        setsReps: e.recommendedSets && e.recommendedReps ? `${e.recommendedSets} × ${e.recommendedReps}` : '',
      };
    });
  });

  /** « il y a X » depuis un jour ISO (aujourd'hui / hier / j / sem / mois / an). */
  private agoLabel(dayIso: string): string {
    const days = Math.max(
      0,
      Math.round((this.parseIso(this.todayIso()).getTime() - this.parseIso(dayIso).getTime()) / 86_400_000),
    );
    if (days === 0) return "aujourd'hui";
    if (days === 1) return 'hier';
    if (days < 7) return `il y a ${days} j`;
    if (days < 30) return `il y a ${Math.floor(days / 7)} sem`;
    if (days < 365) return `il y a ${Math.floor(days / 30)} mois`;
    const years = Math.floor(days / 365);
    return `il y a ${years} an${years > 1 ? 's' : ''}`;
  }

  // ─── État UI ───
  protected readonly showDelete = signal(false);
  protected readonly showEditZoneGroup = signal(false);
  protected readonly draftZone = signal('');
  protected readonly draftGroup = signal('');

  // ─── Stats : période + agrégation hebdo (miroir observeMuscleWeeklyVolume) ───
  protected readonly rangeKind = signal<RangeKey>('W1');
  protected readonly customStart = signal<string>(this.todayIso());
  protected readonly customEnd = signal<string>(this.todayIso());
  protected readonly pickerStage = signal<'start' | 'end' | null>(null);
  protected readonly visibleMetrics = signal<Set<MetricKey>>(new Set(['Sets', 'Volume']));

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

  /** Volume/séries par semaine (pendingDeletion exclu sets/awe/aw comme le DAO ; sans coefficient). */
  private readonly weeklyStats = computed<WeeklyStat[]>(() => {
    const { startIso, endIso } = this.bounds();
    const linked = this.linkedExerciseUuids();
    const aweMap = new Map(
      this.awExercises().filter((e) => !e.pendingDeletion && linked.has(e.exerciseUUID)).map((e) => [e.uuid, e]),
    );
    const awMap = new Map(this.workouts().filter((w) => !w.pendingDeletion).map((w) => [w.uuid, w]));
    const byWeek = new Map<string, WeeklyStat>();
    for (const s of this.sets()) {
      if (s.pendingDeletion) continue;
      const awe = aweMap.get(s.actualWorkoutExerciseUUID);
      if (!awe) continue;
      const aw = awMap.get(awe.actualWorkoutUUID);
      if (!aw || !aw.date) continue;
      const day = aw.date.slice(0, 10);
      if (day < startIso || day > endIso) continue;
      const week = this.weekBucket(day);
      const row = byWeek.get(week) ?? { weekIso: week, setCount: 0, volume: 0 };
      row.setCount += 1;
      row.volume += s.weight * s.reps;
      byWeek.set(week, row);
    }
    return [...byWeek.values()].sort((a, b) => a.weekIso.localeCompare(b.weekIso));
  });

  protected readonly chartBuckets = computed(() => this.weeklyStats().map((w) => w.weekIso));

  /** Mêmes couleurs qu'Android : Séries bleu (primaryAction), Volume hebdo orange. */
  protected readonly metricDefs = computed<{ key: MetricKey; label: string; color: string }[]>(() => {
    const unit = this.settings.settings().weightUnit === 'LBS' ? 'lbs' : 'kg';
    return [
      { key: 'Sets', label: 'Séries', color: resolveCssColor('var(--app-primary-action)') },
      { key: 'Volume', label: `Volume hebdo (${unit})`, color: resolveCssColor('var(--c-orange-medium)') },
    ];
  });

  protected readonly chartSeries = computed<StatsSeries[]>(() => {
    const stats = this.weeklyStats();
    if (stats.length === 0) return [];
    const lbs = this.settings.settings().weightUnit === 'LBS';
    const conv = (v: number): number => Math.round((lbs ? v * KG_TO_LBS : v) * 10) / 10;
    const value: Record<MetricKey, (w: WeeklyStat) => number> = {
      Sets: (w) => w.setCount,
      Volume: (w) => conv(w.volume),
    };
    return this.metricDefs()
      .filter((m) => this.visibleMetrics().has(m.key))
      .map((m) => ({ name: m.label, data: stats.map(value[m.key]), color: m.color }));
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
    const m = this.muscle();
    if (m) void this.repo.update(m.uuid, { isFavorite: !m.isFavorite });
  }

  /** Bouton Delavier Method : écran anatomie Android pas encore porté sur le web. */
  protected delavierSoon(): void {
    this.snackbar.info("Bientôt disponible — la page anatomie (Delavier Method) n'est pas encore portée sur le web.");
  }

  protected confirmDelete(): void {
    const m = this.muscle();
    this.showDelete.set(false);
    if (!m) return;
    void this.repo.remove(m.uuid);
    // Embedded : la row disparaît de la liste → le parent repasse en placeholders (pas de navigation).
    if (!this.embedded()) this.back();
  }

  protected goExercise(uuid: string): void {
    void this.router.navigate(['/exercise', uuid]);
  }

  // ─── Dialog zone + groupe ───
  protected openEditZoneGroup(): void {
    const m = this.muscle();
    if (!m) return;
    this.draftZone.set(m.zone ?? '');
    this.draftGroup.set(m.muscleGroup ?? '');
    this.showEditZoneGroup.set(true);
  }

  protected confirmEditZoneGroup(): void {
    const m = this.muscle();
    this.showEditZoneGroup.set(false);
    if (!m) return;
    void this.repo.update(m.uuid, {
      zone: this.draftZone() || null,
      muscleGroup: this.draftGroup() || null,
    });
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

  // ─── Helpers dates ───
  /** Bucket semaine équivalent SQLite strftime('%Y-%W') : 00-53, lundi premier jour. */
  private weekBucket(day: string): string {
    const d = this.parseIso(day);
    const jan1 = new Date(d.getFullYear(), 0, 1);
    const dayOfYear = Math.round((d.getTime() - jan1.getTime()) / 86400000) + 1;
    const mondayBased = (d.getDay() + 6) % 7;
    const week = Math.floor((dayOfYear - 1 - mondayBased + 7) / 7);
    return `${d.getFullYear()}-${week.toString().padStart(2, '0')}`;
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
