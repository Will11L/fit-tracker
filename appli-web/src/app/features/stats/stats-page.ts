import { ChangeDetectionStrategy, Component, computed, inject, signal, type WritableSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { LocalExercise } from '@core/models/exercise.model';
import { LocalMuscle } from '@core/models/muscle.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { NgTemplateOutlet } from '@angular/common';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { SegmentedIconToggle, type SegmentItem } from '@designsystem/common_components/segmented-icon-toggle';
import { CustomDatePickerDialog } from '@designsystem/common_components/custom-date-picker-dialog';
import { RadarChartComponent } from '@designsystem/common_components/radar-chart';
import { AppIcon } from '@designsystem/icons/app-icon';
import { SettingsStore } from '../settings/settings-store';
import { SummaryRow, type SummaryItemData } from '@designsystem/common_components/summary-row';
import { StatsSectionChart, type StatsSeries } from './stats-section-chart';
import { paletteForZone, resolveCssColor } from './palette-util';
import { zoneVolumeRadar, type ZoneVolume } from './zone-radar-data';

// ─── Hiérarchie anatomique 3 niveaux (miroir core/data/Zones.kt + MuscleGroups.kt) ───
const ZONES_ALL = ['Chest', 'Back', 'Shoulders', 'Arms', 'Legs', 'Core'];
const GROUPS_ALL = [
  'Pecs',
  'Lats', 'Rhomboids', 'Erector Spinae', 'Traps',
  'Delts',
  'Biceps', 'Triceps', 'Brachialis', 'Forearms',
  'Quads', 'Hamstrings', 'Glutes', 'Calves', 'Adductors',
  'Abs', 'Obliques',
];
/** Display only (politique 11 : storage EN canonique) — utilisé par la card Fréquence. */
const ZONE_FR: Record<string, string> = {
  Chest: 'Pectoraux', Back: 'Dos', Shoulders: 'Épaules', Arms: 'Bras', Legs: 'Jambes', Core: 'Abdos', Other: 'Autre',
};

type Metric = 'SETS' | 'EXERCISES' | 'TOTAL_WEIGHT';
type ChartKind = 'BAR' | 'LINE';
type SortMode = 'ALPHA' | 'ZONE';
type Granularity = 'DAILY' | 'WEEKLY';
type RangeKey = 'W1' | 'D30' | 'M3' | 'M6' | 'Y1' | 'ALL' | 'CUSTOM';
type SectionId = 'zone' | 'group' | 'muscle' | 'exercise';

const RANGE_CHIPS: { key: RangeKey; label: string }[] = [
  { key: 'W1', label: '1 semaine' },
  { key: 'D30', label: '30 jours' },
  { key: 'M3', label: '3 mois' },
  { key: 'M6', label: '6 mois' },
  { key: 'Y1', label: '1 an' },
  { key: 'ALL', label: 'Tout' },
  // Chip icône SEULE (calendrier) : la ligne débordait même avec « Perso. » (user 2026-07-15).
  { key: 'CUSTOM', label: '' },
];

const KG_TO_LBS = 2.2046226218;

/** Fact = 1 set DONE joint à son exercice de séance + séance (miroir des JOIN DAO Android). */
interface Fact {
  day: string; // YYYY-MM-DD
  week: string; // YYYY-WW (sémantique SQLite strftime %Y-%W)
  workoutUuid: string;
  exerciseUuid: string;
  exerciseName: string | null; // null si exercise absent / pendingDeletion
  weight: number;
  reps: number;
  isDropset: boolean; // exclu du comptage de séries (métrique SETS), gardé pour le volume
}

interface MuscleLink {
  coef: number;
  muscle: string;
  group: string | null;
  zone: string | null;
}

interface ChipVm {
  name: string;
  color: string;
  selected: boolean;
}

interface SectionVm {
  id: SectionId;
  title: string;
  chartType: ChartKind;
  metric: Metric;
  buckets: string[];
  series: StatsSeries[];
  gran: Granularity;
  chips: ChipVm[];
}

/**
 * Écran Statistiques — refonte fidèle de StatsScreen.kt (Android) :
 * - Header : card « Fréquence d'entraînement » (séances / par semaine / groupe favori / total)
 *   + SortToggle (alpha / zone) + chips de période (1 semaine → Tout + Personnalisé).
 * - 4 sections de charts : Zone (6) → Groupe (17) → Muscle (35) → Exercice, chacune avec ses
 *   toggles INDÉPENDANTS ChartType (Bar/Line) + Metric (Séries/Exercices/Volume) et ses filter
 *   chips colorés (palette par zone via paletteForZone, port 1:1 de PaletteUtil.kt).
 * - Agrégation 100 % client (Dexie), sémantique exacte des queries ActualWorkoutSetDao :
 *   sets status DONE uniquement, pendingDeletion exclu à chaque jointure, volume = Σ poids·reps·coef
 *   (sans coef au niveau Exercice), sets = Σ coef (count brut au niveau Exercice), exercices =
 *   COUNT DISTINCT (séances distinctes au niveau Exercice), buckets jour si période ≤ 14 j sinon
 *   semaine `%Y-%W`.
 * Différé vs Android : sous-écrans MuscleStats/ExerciseStats (drill-down par entité).
 */
@Component({
  selector: 'app-stats-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, ScreenTitleBar, TitledDivider, SegmentedIconToggle, CustomDatePickerDialog, StatsSectionChart, RadarChartComponent, AppIcon, SummaryRow],
  template: `
    <section class="page">
      <app-screen-title-bar title="Statistiques" />

      <div class="page__body">
        <!-- Rangée d'en-tête (demande user 2026-07-15) : cadre « Tri & période » (boutons d'actions)
             à GAUCHE · cadre « Fréquence d'entraînement » à DROITE. -->
        <div class="toprow">
          <div class="toprow__cell">
            <div class="section">
              <app-titled-divider title="Tri & période" />
              <!-- Tri à GAUCHE de la ligne, chips de période à DROITE (space-between). -->
              <div class="controls">
                <app-segmented-icon-toggle
                  [items]="sortItems"
                  [selected]="sortMode()"
                  (select)="setSortMode($event)"
                />
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
              </div>
            </div>
          </div>
          <div class="toprow__cell">
            <div class="freq">
              <app-titled-divider title="Fréquence d'entraînement" />
              <!-- Tuiles icône + valeur + libellé, style « Complétion de la séance » (demande
                   user 2026-07-15). L'icône du groupe favori porte la couleur de la zone. -->
              <!-- Comme la Complétion de la séance : cellules qui huggent leur contenu,
                   espacement adaptatif (space-between, cf. CSS). -->
              <app-summary-row
                class="freq__summary"
                [items]="freqItems()"
                [compact]="true"
                tileBackground="var(--c-second-blue)"
              />
            </div>
          </div>
        </div>

        <!-- Section réutilisée (rendue via ngTemplateOutlet : 1re dans la rangée du haut, le reste dessous). -->
        <ng-template #sectionTpl let-s>
          <div class="section">
            <app-titled-divider [title]="s.title" />
            <div class="section__toggles">
              <app-segmented-icon-toggle
                [items]="chartTypeItems"
                [selected]="s.chartType"
                (select)="setChartType(s.id, $event)"
              />
              <app-segmented-icon-toggle
                [items]="metricItems"
                [selected]="s.metric"
                (select)="setMetric(s.id, $event)"
              />
            </div>
            <app-stats-section-chart
              [buckets]="s.buckets"
              [series]="s.series"
              [chartType]="s.chartType"
              [granularity]="s.gran"
              [metric]="s.metric"
            />
            <div class="chips">
              @for (c of s.chips; track c.name) {
                <button
                  class="chip"
                  [class.chip--sel]="c.selected"
                  [style.border-color]="c.color"
                  [style.color]="c.selected ? 'var(--app-text-primary)' : c.color"
                  [style.background]="c.selected ? c.color : 'transparent'"
                  (click)="toggleChip(s.id, c.name)"
                >
                  {{ c.name }}
                </button>
              }
            </div>
          </div>
        </ng-template>

        <!-- « Équilibre par zone (volume) » + « Séries / zone » côte à côte (demande user 2026-07-15).
             Radar du volume agrégé par zone (6 axes colorés, série accent), Σ poids·reps·coef. -->
        <div class="toprow">
          <div class="toprow__cell">
            <div class="section">
              <app-titled-divider title="Équilibre par zone (volume)" />
              <div class="radarwrap">
                <!-- % avec l'intitulé des axes ; hauteur 380 + rayon 70 % pour exploiter
                     l'espace (fill retiré : sans hauteur parent définie le chart devenait 0). -->
                <app-radar-chart
                  [axes]="zoneRadar().axes"
                  [series]="zoneRadar().series"
                  [height]="380"
                  [radius]="'70%'"
                  [showLegend]="false"
                  [showAxisPercent]="true"
                  emptyText="Aucune séance sur cette période."
                />
              </div>
            </div>
          </div>
          @if (sections()[0]; as s0) {
            <div class="toprow__cell">
              <ng-container *ngTemplateOutlet="sectionTpl; context: { $implicit: s0 }" />
            </div>
          }
        </div>

        <!-- « Séries / groupe » + « Séries / exercice » côte à côte (demande user 2026-07-15) ;
             « Séries / muscle » garde la pleine largeur en dessous (35 séries potentielles). -->
        <div class="toprow">
          @if (sections()[1]; as s1) {
            <div class="toprow__cell">
              <ng-container *ngTemplateOutlet="sectionTpl; context: { $implicit: s1 }" />
            </div>
          }
          @if (sections()[3]; as s3) {
            <div class="toprow__cell">
              <ng-container *ngTemplateOutlet="sectionTpl; context: { $implicit: s3 }" />
            </div>
          }
        </div>
        <div class="sections">
          @if (sections()[2]; as s2) {
            <ng-container *ngTemplateOutlet="sectionTpl; context: { $implicit: s2 }" />
          }
        </div>
      </div>
    </section>

    <!-- Période personnalisée : 2 pickers séquentiels (Début puis Fin), miroir CustomRangePickerDialog. -->
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
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; corps avec gouttière (--page-gutter). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* Card fréquence — miroir FrequencyCard (bgRecessed, label dessus / valeur dessous). */
      /* Cadre de section : titre (titled-divider) DANS le cadre, stats en dessous. */
      .freq {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Même padding que les cadres canoniques app-framed-section (page Séance). */
        padding: 16px;
      }
      /* Miroir .completion__summary (Séance) : cadre de tuile au contenu (hug), espacement
         adaptatif space-between (extrêmes alignées sur le padding du cadre), centré dans la
         hauteur restante. */
      .freq__summary {
        display: block;
        margin: auto 0;
      }
      .freq__summary ::ng-deep .sr {
        justify-content: space-between;
        gap: 0;
      }
      /* Tri à gauche · chips de période à droite, avec l'espace entre les deux. */
      .controls {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: var(--space-2);
        flex: 1;
      }
      .chips--range {
        justify-content: flex-end;
      }
      /* Le cadre Fréquence s'étire à la hauteur de la cellule (rangée à hauteurs égales). */
      .toprow__cell > .freq {
        flex: 1;
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
      /* FilterChip M3 (miroir RangeChipsRow / GroupFilterChips) : pill 32px, bordure colorée. */
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
      /* Radar équilibre par zone : borné (page Stats en pleine largeur) pour rester lisible. */
      /* Le radar (hauteur fixe) se CENTRE dans la hauteur de sa cellule (le voisin
         « Séries/zone » est plus haut à cause de ses toggles/chips). */
      .radarwrap {
        width: 100%;
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
      }
      /* Rangée du haut : radar zones + « Séries / zone » côte à côte (wrap sur écran étroit). */
      .toprow {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-3);
        align-items: stretch;
      }
      .toprow__cell {
        flex: 1 1 380px;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .toprow__cell > .section {
        flex: 1;
      }
      /* 4 sections : pleine largeur, un chart par ligne (Functional review 2026-06-10). */
      .sections {
        display: flex;
        flex-direction: column;
        gap: var(--space-5);
      }
      /* Chaque section (Séries/zone…) = UN cadre thirdBlue contenant titre + toggles + chart +
         chips (demande user 2026-07-15). Le fond propre du chart, même couleur, se fond dedans. */
      .section {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        min-width: 0;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Même padding que les cadres canoniques app-framed-section (page Séance). */
        padding: 16px;
      }
      .section__toggles {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
    `,
  ],
})
export class StatsPage {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly settings = inject(SettingsStore);

  protected readonly rangeChips = RANGE_CHIPS;
  protected readonly sortItems: SegmentItem[] = [
    { value: 'ALPHA', icon: 'sort_by_alpha', description: 'Trier par ordre alphabétique' },
    { value: 'ZONE', icon: 'palette', description: 'Trier par couleur de zone' },
  ];
  protected readonly chartTypeItems: SegmentItem[] = [
    { value: 'BAR', icon: 'bar_chart', description: 'Graphique en barres' },
    { value: 'LINE', icon: 'show_chart', description: 'Graphique en courbes' },
  ];
  protected readonly metricItems: SegmentItem[] = [
    { value: 'SETS', icon: 'repeat', description: 'Nombre de séries' },
    { value: 'EXERCISES', icon: 'format_list_numbered', description: "Nombre d'exercices" },
    { value: 'TOTAL_WEIGHT', icon: 'fitness_center', description: 'Poids total' },
  ];

  // ─── État UI (mêmes defaults qu'Android : range 1 semaine, tri ZONE, BAR + SETS partout) ───
  protected readonly rangeKind = signal<RangeKey>('W1');
  protected readonly customStart = signal<string>(this.todayIso());
  protected readonly customEnd = signal<string>(this.todayIso());
  protected readonly pickerStage = signal<'start' | 'end' | null>(null);
  protected readonly sortMode = signal<SortMode>('ZONE');

  private readonly chartTypes: Record<SectionId, WritableSignal<ChartKind>> = {
    zone: signal('BAR'), group: signal('BAR'), muscle: signal('BAR'), exercise: signal('BAR'),
  };
  private readonly metrics: Record<SectionId, WritableSignal<Metric>> = {
    zone: signal('SETS'), group: signal('SETS'), muscle: signal('SETS'), exercise: signal('SETS'),
  };
  /** Visibilité chips : zone/groupe init pleins ; muscle/exercice vides = « tous » (seed lazy). */
  private readonly visibles: Record<SectionId, WritableSignal<Set<string>>> = {
    zone: signal(new Set(ZONES_ALL)),
    group: signal(new Set(GROUPS_ALL)),
    muscle: signal(new Set<string>()),
    exercise: signal(new Set<string>()),
  };

  // ─── Données Dexie ───
  private readonly workouts = toSignal(
    from(liveQuery(() => this.db.actual_workouts.toArray())),
    { initialValue: [] as LocalActualWorkout[] },
  );
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
  private readonly muscles = toSignal(from(liveQuery(() => this.db.muscles.toArray())), {
    initialValue: [] as LocalMuscle[],
  });
  private readonly exercises = toSignal(from(liveQuery(() => this.db.exercises.toArray())), {
    initialValue: [] as LocalExercise[],
  });

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }

  // ─── Bornes de période (miroir StatsRange.computeBounds) ───
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
    const startIso = this.toIso(start);
    const endIso = this.toIso(end);
    const days = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
    const gran: Granularity = days <= 14 ? 'DAILY' : 'WEEKLY';
    return { startIso, endIso, days: Math.max(1, days), gran };
  });

  // ─── Jointures (miroir des JOIN DAO : pendingDeletion exclu à chaque table, status DONE) ───
  private readonly linksByExercise = computed(() => {
    const muscleByUuid = new Map(
      this.muscles().filter((m) => !m.pendingDeletion).map((m) => [m.uuid, m]),
    );
    const map = new Map<string, MuscleLink[]>();
    for (const em of this.exMuscles()) {
      if (em.pendingDeletion) continue;
      const m = muscleByUuid.get(em.muscleUUID);
      if (!m) continue;
      const link: MuscleLink = {
        coef: em.coefficient,
        muscle: m.name,
        group: m.muscleGroup ?? null,
        zone: m.zone ?? null,
      };
      const arr = map.get(em.exerciseUUID);
      if (arr) arr.push(link);
      else map.set(em.exerciseUUID, [link]);
    }
    return map;
  });

  private readonly facts = computed<Fact[]>(() => {
    const { startIso, endIso } = this.bounds();
    const aweMap = new Map(
      this.awExercises().filter((e) => !e.pendingDeletion).map((e) => [e.uuid, e]),
    );
    const awMap = new Map(this.workouts().filter((w) => !w.pendingDeletion).map((w) => [w.uuid, w]));
    const exNameMap = new Map(
      this.exercises().filter((e) => !e.pendingDeletion).map((e) => [e.uuid, e.name]),
    );
    const out: Fact[] = [];
    for (const s of this.sets()) {
      if (s.pendingDeletion || s.status !== 'DONE') continue;
      const awe = aweMap.get(s.actualWorkoutExerciseUUID);
      if (!awe) continue;
      const aw = awMap.get(awe.actualWorkoutUUID);
      if (!aw || !aw.date) continue;
      const day = aw.date.slice(0, 10);
      if (day < startIso || day > endIso) continue;
      out.push({
        day,
        week: this.weekBucket(day),
        workoutUuid: aw.uuid,
        exerciseUuid: awe.exerciseUUID,
        exerciseName: exNameMap.get(awe.exerciseUUID) ?? null,
        weight: s.weight,
        reps: s.reps,
        isDropset: s.isDropset,
      });
    }
    return out;
  });

  // ─── Mappings clé → zone (pour le tri ZONE et les palettes) ───
  private readonly muscleNameToZone = computed(() => {
    const map = new Map<string, string>();
    for (const m of this.muscles()) if (m.zone) map.set(m.name, m.zone);
    return map;
  });

  private readonly groupToZone = computed(() => {
    const map = new Map<string, string>();
    for (const m of this.muscles()) {
      if (m.muscleGroup && m.zone && !map.has(m.muscleGroup)) map.set(m.muscleGroup, m.zone);
    }
    return map;
  });

  /** Zone primaire de chaque exercice = zone du muscle au coefficient max (miroir exerciseNameToZone). */
  private readonly exerciseNameToZone = computed(() => {
    const links = this.linksByExercise();
    const exNameMap = new Map(
      this.exercises().filter((e) => !e.pendingDeletion).map((e) => [e.uuid, e.name]),
    );
    const map = new Map<string, string>();
    for (const [exUuid, ls] of links) {
      const name = exNameMap.get(exUuid);
      if (!name || ls.length === 0) continue;
      const top = ls.reduce((a, b) => (b.coef > a.coef ? b : a));
      if (top.zone) map.set(name, top.zone);
    }
    return map;
  });

  // ─── Palettes (port des couleurs de zone + paletteForZone, résolution CSS runtime) ───
  private readonly zoneColors = computed<Record<string, string>>(() => ({
    Chest: resolveCssColor('var(--app-primary-action)'),
    Back: resolveCssColor('var(--c-orange-medium)'),
    Shoulders: resolveCssColor('var(--app-accent-text)'),
    Arms: resolveCssColor('var(--c-red-medium)'),
    Legs: resolveCssColor('var(--c-medium-green)'),
    Core: resolveCssColor('var(--c-yellow-medium)'),
    Other: resolveCssColor('var(--c-medium-purple)'),
  }));

  /** Cycle de secours pour clés sans zone connue (miroir musclePalette Android). */
  private readonly fallbackPalette = computed<string[]>(() => [
    'var(--app-primary-action)', 'var(--c-orange-medium)', 'var(--app-accent-text)',
    'var(--c-red-medium)', 'var(--c-medium-green)', 'var(--c-yellow-medium)',
    'var(--c-medium-purple)', 'var(--c-light-green)', 'var(--c-blue-medium)',
    'var(--c-dark-orange)', 'var(--c-red-dark)', 'var(--c-light-purple)',
  ].map(resolveCssColor));

  /** Nuances par zone pour une liste de (clé → zone) : chaque clé reçoit une nuance de sa zone.
   *  `spread` resserre le dégradé autour de la couleur de base (cf. paletteForZone). */
  private shadesByZone(keysWithZone: [string, string][], spread = 1.0): Map<string, string> {
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
      const shades = paletteForZone(zoneColors[zone] ?? this.fallbackPalette()[0], keys.length, spread);
      keys.forEach((k, i) => out.set(k, shades[i]));
    }
    return out;
  }

  /** Dégradé RESSERRÉ (spread 0.55, demande user 2026-07-15) : ≤ 5 groupes par zone → les nuances
   *  restent proches de la couleur de base (fini le vert délavé quasi blanc), texte lisible.
   *  Les muscles héritent de ces couleurs ; les exercices gardent le spread large (plus de clés). */
  private readonly groupColorMap = computed(() =>
    this.shadesByZone(
      this.muscles()
        .filter((m) => m.muscleGroup && m.zone)
        .map((m) => [m.muscleGroup!, m.zone!] as [string, string]),
      0.55,
    ),
  );

  /**
   * Couleur d'un muscle = couleur de SON GROUPE (demande user 2026-07-15) : le dégradé par
   * muscle (35 nuances) s'éloignait trop de la couleur de zone — ex. Vastus lateralis et
   * Vastus intermedius, tous deux Quads, partagent désormais la nuance Quads. Repli : couleur
   * de la zone si le muscle n'a pas de groupe.
   */
  private readonly muscleColorMap = computed(() => {
    const groups = this.groupColorMap();
    const zoneColors = this.zoneColors();
    const out = new Map<string, string>();
    for (const m of this.muscles()) {
      const color =
        (m.muscleGroup ? groups.get(m.muscleGroup) : undefined) ??
        (m.zone ? zoneColors[m.zone] : undefined);
      if (color) out.set(m.name, color);
    }
    return out;
  });

  /**
   * Couleur d'un exercice = couleur du GROUPE de son muscle dominant (coef max) — même logique
   * que les muscles (demande user 2026-07-15) : Push-up/Squat rappellent leur groupe au lieu
   * de nuances délavées sans lien lisible. Repli : couleur de la zone du muscle dominant.
   */
  private readonly exerciseColorMap = computed(() => {
    const links = this.linksByExercise();
    const groups = this.groupColorMap();
    const zoneColors = this.zoneColors();
    const exNameMap = new Map(
      this.exercises().filter((e) => !e.pendingDeletion).map((e) => [e.uuid, e.name]),
    );
    const out = new Map<string, string>();
    for (const [exUuid, ls] of links) {
      const name = exNameMap.get(exUuid);
      if (!name || ls.length === 0) continue;
      const top = ls.reduce((a, b) => (b.coef > a.coef ? b : a));
      const color =
        (top.group ? groups.get(top.group) : undefined) ??
        (top.zone ? zoneColors[top.zone] : undefined);
      if (color) out.set(name, color);
    }
    return out;
  });

  /** Couleur d'une clé pour une section, avec fallback cycle (miroir Android). */
  private colorOf(id: SectionId, name: string, index: number): string {
    const fb = this.fallbackPalette();
    if (id === 'zone') return this.zoneColors()[name] ?? fb[index % fb.length];
    const map =
      id === 'group' ? this.groupColorMap() : id === 'muscle' ? this.muscleColorMap() : this.exerciseColorMap();
    return map.get(name) ?? fb[index % fb.length];
  }

  // ─── Agrégation (sémantique exacte des queries ActualWorkoutSetDao) ───
  private aggregate(id: SectionId, metric: Metric): { buckets: string[]; entries: [string, number[]][] } {
    const facts = this.facts();
    const gran = this.bounds().gran;
    const links = this.linksByExercise();
    const sums = new Map<string, Map<string, number>>();
    const distinct = new Map<string, Map<string, Set<string>>>();

    const add = (key: string, bucket: string, v: number): void => {
      let byBucket = sums.get(key);
      if (!byBucket) { byBucket = new Map(); sums.set(key, byBucket); }
      byBucket.set(bucket, (byBucket.get(bucket) ?? 0) + v);
    };
    const addDistinct = (key: string, bucket: string, v: string): void => {
      let byBucket = distinct.get(key);
      if (!byBucket) { byBucket = new Map(); distinct.set(key, byBucket); }
      let set = byBucket.get(bucket);
      if (!set) { set = new Set(); byBucket.set(bucket, set); }
      set.add(v);
    };

    for (const f of facts) {
      // Un dropset fait partie de la série parente → jamais compté comme une série à part
      // (métrique SETS). Le volume (TOTAL_WEIGHT) et les exercices distincts le gardent.
      if (metric === 'SETS' && f.isDropset) continue;
      const bucket = gran === 'DAILY' ? f.day : f.week;
      if (id === 'exercise') {
        // Niveau Exercice : pas de coefficient muscle ; EXERCISES = séances distinctes.
        if (!f.exerciseName) continue;
        if (metric === 'TOTAL_WEIGHT') add(f.exerciseName, bucket, f.weight * f.reps);
        else if (metric === 'SETS') add(f.exerciseName, bucket, 1);
        else addDistinct(f.exerciseName, bucket, f.workoutUuid);
      } else {
        const ls = links.get(f.exerciseUuid);
        if (!ls) continue;
        for (const l of ls) {
          const key =
            id === 'zone' ? (l.zone ?? 'Other') : id === 'group' ? (l.group ?? 'Other') : l.muscle;
          if (metric === 'TOTAL_WEIGHT') add(key, bucket, f.weight * f.reps * l.coef);
          else if (metric === 'SETS') add(key, bucket, l.coef);
          else addDistinct(key, bucket, f.exerciseUuid);
        }
      }
    }

    if (metric === 'EXERCISES') {
      for (const [key, byBucket] of distinct) {
        for (const [bucket, set] of byBucket) add(key, bucket, set.size);
      }
    }

    const bucketSet = new Set<string>();
    for (const byBucket of sums.values()) for (const b of byBucket.keys()) bucketSet.add(b);
    const buckets = [...bucketSet].sort();

    // Ordre canonique (Zones.ALL / MuscleGroups.ALL) ; muscle/exercice = alpha. 'Other' exclu
    // aux niveaux zone/groupe (miroir associateWith Android).
    const keys =
      id === 'zone' ? ZONES_ALL : id === 'group' ? GROUPS_ALL : [...sums.keys()].sort();
    const entries: [string, number[]][] = keys
      .map((k): [string, number[]] => [k, buckets.map((b) => sums.get(k)?.get(b) ?? 0)])
      .filter(([, data]) => data.some((v) => v > 0));
    return { buckets, entries };
  }

  /** Tri des séries (miroir sortSeriesByMode) : ALPHA = alpha ; ZONE = zone canonique puis alpha. */
  private sortEntries(
    entries: [string, number[]][],
    keyToZone: (k: string) => string | null,
  ): [string, number[]][] {
    if (this.sortMode() === 'ALPHA') return [...entries].sort((a, b) => a[0].localeCompare(b[0]));
    const zoneIdx = (k: string): number => {
      const z = keyToZone(k);
      if (z === null) return Number.MAX_SAFE_INTEGER;
      const i = ZONES_ALL.indexOf(z);
      return i < 0 ? Number.MAX_SAFE_INTEGER : i;
    };
    return [...entries].sort((a, b) => {
      const ia = zoneIdx(a[0]);
      const ib = zoneIdx(b[0]);
      return ia !== ib ? ia - ib : a[0].localeCompare(b[0]);
    });
  }

  // ─── View-models des 4 sections ───
  protected readonly sections = computed<SectionVm[]>(() =>
    (['zone', 'group', 'muscle', 'exercise'] as SectionId[]).map((id) => this.buildSection(id)),
  );

  private buildSection(id: SectionId): SectionVm {
    const metric = this.metrics[id]();
    const chartType = this.chartTypes[id]();
    const gran = this.bounds().gran;
    const { buckets, entries } = this.aggregate(id, metric);

    const keyToZone = (k: string): string | null => {
      if (id === 'zone') return ZONES_ALL.includes(k) ? k : null;
      if (id === 'group') return this.groupToZone().get(k) ?? null;
      if (id === 'muscle') return this.muscleNameToZone().get(k) ?? null;
      return this.exerciseNameToZone().get(k) ?? null;
    };
    const sorted = this.sortEntries(entries, keyToZone);

    // Visibilité effective : set vide = « tous » pour groupe/muscle/exercice (seed lazy Android).
    const raw = this.visibles[id]();
    const allNames = sorted.map(([k]) => k);
    const effective = id !== 'zone' && raw.size === 0 ? new Set(allNames) : raw;

    const lbs = metric === 'TOTAL_WEIGHT' && this.settings.settings().weightUnit === 'LBS';
    const series: StatsSeries[] = sorted
      .filter(([k]) => effective.has(k))
      .map(([name, data], i) => ({
        name,
        data: data.map((v) => Math.round((lbs ? v * KG_TO_LBS : v) * 10) / 10),
        color: this.colorOf(id, name, i),
      }));

    return {
      id,
      title: this.sectionTitle(id, metric),
      chartType,
      metric,
      buckets,
      series,
      gran,
      chips: this.buildChips(id, allNames, effective),
    };
  }

  private buildChips(id: SectionId, seriesNames: string[], effective: Set<string>): ChipVm[] {
    let names: string[];
    if (id === 'zone') {
      // Chips Zone : toujours les 6 zones, ordre selon le tri (miroir filterableZones).
      names = this.sortMode() === 'ALPHA' ? [...ZONES_ALL].sort() : ZONES_ALL;
    } else if (id === 'muscle') {
      // Chips Muscle : tous les muscles connus (même sans data), tri alpha ou par zone.
      const all = this.muscles().filter((m) => !m.pendingDeletion).map((m) => m.name);
      const m2z = this.muscleNameToZone();
      names =
        this.sortMode() === 'ALPHA'
          ? [...new Set(all)].sort()
          : [...new Set(all)].sort((a, b) => {
              const ia = ZONES_ALL.indexOf(m2z.get(a) ?? '');
              const ib = ZONES_ALL.indexOf(m2z.get(b) ?? '');
              const ka = ia < 0 ? Number.MAX_SAFE_INTEGER : ia;
              const kb = ib < 0 ? Number.MAX_SAFE_INTEGER : ib;
              return ka !== kb ? ka - kb : a.localeCompare(b);
            });
    } else {
      // Chips Groupe / Exercice : clés des séries triées (= ordre exact du chart).
      names = seriesNames;
    }
    return names.map((name, i) => ({
      name,
      color: this.colorOf(id, name, i),
      selected: effective.has(name),
    }));
  }

  private sectionTitle(id: SectionId, metric: Metric): string {
    const unit = this.settings.settings().weightUnit === 'LBS' ? 'lbs' : 'kg';
    const suffix =
      id === 'zone' ? 'Zone' : id === 'group' ? 'Groupe' : id === 'muscle' ? 'Muscle' : 'Exercice';
    if (metric === 'TOTAL_WEIGHT') return `Volume (${unit}) / ${suffix}`;
    if (metric === 'SETS') return `Séries / ${suffix}`;
    // Niveau Exercice : EXERCISES réinterprété en séances distinctes (miroir Android).
    return id === 'exercise' ? `Séances / ${suffix}` : `Exercices / ${suffix}`;
  }

  // ─── Card « Fréquence d'entraînement » (miroir FrequencyCard + frequencyStats) ───
  protected readonly freq = computed(() => {
    const { startIso, endIso, days } = this.bounds();
    const activeDays = new Set(
      this.workouts()
        .filter((w) => !w.pendingDeletion && w.isDone && w.date)
        .map((w) => w.date.slice(0, 10))
        .filter((d) => d >= startIso && d <= endIso),
    ).size;
    const perWeek = days <= 0 ? 0 : (activeDays * 7) / days;

    const metric = this.metrics['zone']();
    const { entries } = this.aggregate('zone', metric);
    let total = 0;
    let topZone: string | null = null;
    let topSum = -1;
    for (const [zone, data] of entries) {
      const sum = data.reduce((a, b) => a + b, 0);
      total += sum;
      if (sum > topSum) { topSum = sum; topZone = zone; }
    }

    const unit = this.settings.settings().weightUnit;
    let totalLabel: string;
    let totalValue: string;
    let totalSuffix = '';
    // Icône + teinte de la tuile « total » évoquant la métrique (miroir tuiles de la Séance).
    let totalIcon = 'stacked_bar_chart';
    let totalTint = 'var(--c-light-gray-blue)';
    // Libellés COURTS (user 2026-07-15) : la place va aux nombres, qui grandissent vite.
    if (metric === 'TOTAL_WEIGHT') {
      totalLabel = 'Volume';
      totalValue = this.formatVolume(unit === 'LBS' ? total * KG_TO_LBS : total);
      totalSuffix = ` ${unit === 'LBS' ? 'lbs' : 'kg'}`;
    } else if (metric === 'SETS') {
      totalLabel = 'Séries';
      totalValue = `${Math.round(total)}`;
      totalIcon = 'fitness_center';
      totalTint = 'var(--c-medium-green)';
    } else {
      totalLabel = 'Exos';
      totalValue = `${Math.round(total)}`;
      totalIcon = 'exercise';
      totalTint = 'var(--c-medium-green)';
    }

    return {
      sessions: activeDays,
      perWeek: perWeek.toFixed(1),
      topZone: topZone ? (ZONE_FR[topZone] ?? topZone) : '—',
      topZoneColor: topZone
        ? (this.zoneColors()[topZone] ?? 'var(--c-light-gray-blue)')
        : 'var(--c-light-gray-blue)',
      totalLabel,
      totalValue,
      totalSuffix,
      totalIcon,
      totalTint,
    };
  });

  /** Tuiles icône + valeur + libellé de la card Fréquence (style « Complétion » de la Séance).
   *  Couleurs évocatrices (user 2026-07-15) : séances faites (check vert-primaire), calendrier
   *  bleu, étoile FAVORI (token --app-favorite, l'orange des favoris de l'app), total = métrique. */
  protected readonly freqItems = computed<SummaryItemData[]>(() => {
    const f = this.freq();
    return [
      { icon: 'event_available', value: `${f.sessions}`, label: 'Séances', iconTint: 'var(--app-primary-action)' },
      { icon: 'calendar_month', value: f.perWeek, label: 'Par semaine', iconTint: 'var(--c-blue-medium)' },
      { icon: 'star', value: f.topZone, label: 'Favori', iconTint: 'var(--app-favorite)' },
      { icon: f.totalIcon, value: `${f.totalValue}${f.totalSuffix}`, label: f.totalLabel, iconTint: f.totalTint },
    ];
  });

  // ─── Radar « Équilibre par zone » (volume agrégé sur la période, 6 axes) ───
  protected readonly zoneRadar = computed(() => {
    const { entries } = this.aggregate('zone', 'TOTAL_WEIGHT');
    const totalByZone = new Map<string, number>();
    for (const [zone, data] of entries) totalByZone.set(zone, data.reduce((a, b) => a + b, 0));
    const lbs = this.settings.settings().weightUnit === 'LBS';
    const zoneColors = this.zoneColors();
    const fb = this.fallbackPalette();
    // 6 zones canoniques toujours présentes (axe à 0 si pas de volume) → hexagone stable = lecture
    // symétrie. Nom d'axe coloré par zone, série remplie en accent unique (le plus lisible).
    const zones: ZoneVolume[] = ZONES_ALL.map((z, i) => ({
      zone: z,
      color: zoneColors[z] ?? fb[i % fb.length],
      volume: Math.round((totalByZone.get(z) ?? 0) * (lbs ? KG_TO_LBS : 1) * 10) / 10,
    }));
    return zoneVolumeRadar(zones, 'var(--app-primary-action)');
  });

  // ─── Handlers ───
  protected setSortMode(v: string): void {
    this.sortMode.set(v as SortMode);
  }

  protected setChartType(id: SectionId, v: string): void {
    this.chartTypes[id].set(v as ChartKind);
  }

  protected setMetric(id: SectionId, v: string): void {
    this.metrics[id].set(v as Metric);
  }

  protected toggleChip(id: SectionId, name: string): void {
    const sig = this.visibles[id];
    const current = sig();
    // Seed lazy : 1er toggle muscle/exercice part de « tous sélectionnés » (miroir Android).
    let seed = current;
    if (current.size === 0 && (id === 'muscle' || id === 'exercise')) {
      const vm = this.sections().find((s) => s.id === id);
      seed = new Set(vm ? vm.chips.map((c) => c.name) : []);
    }
    const next = new Set(seed);
    if (next.has(name)) next.delete(name);
    else next.add(name);
    sig.set(next);
  }

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
    // Garantit start <= end (swap si besoin).
    if (iso < this.customStart()) {
      this.customEnd.set(this.customStart());
      this.customStart.set(iso);
    } else {
      this.customEnd.set(iso);
    }
    this.rangeKind.set('CUSTOM');
    this.pickerStage.set(null);
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

  /** Format volume (miroir formatVolume Android) : k au-delà de 1000, M au-delà de 1M. */
  private formatVolume(v: number): string {
    if (v <= 0) return '0';
    if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`;
    if (v >= 10_000) return `${Math.round(v / 1_000)}k`;
    if (v >= 1_000) return `${(v / 1_000).toFixed(1)}k`;
    return `${Math.round(v)}`;
  }
}
