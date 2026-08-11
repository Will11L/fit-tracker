import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalPlannedWorkout, PlannedWorkout } from '@core/models/planned-workout.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { AppIcon } from '@designsystem/icons/app-icon';
import { SessionPage } from '@features/session/session-page';

/**
 * Page « Séance du jour » — contenu factorisé de l'onglet Séance de l'Accueil (HomePage),
 * réutilisé tel quel à deux endroits sans duplication de logique :
 *  - embarqué (`embedded=true`) dans le hub Accueil (DualTabMenu) ;
 *  - autonome via la route `/seance` (item drawer « Séance »).
 *
 * Affiche la séance du jour (`<app-session-page>` éditable inline) ou un fallback sans séance —
 * miroir NoSessionFallback.kt : « Voir le programme » + soit « Démarrer <planifiée du jour> »
 * (copie planned → actual : exercices non ignorés triés par order + sets NOT_STARTED, comme
 * HomeViewModel.startActualWorkoutFromPlanned), soit « Démarrer une nouvelle séance » (dialog nom,
 * refuse « Rest Day »). Un PlannedWorkout filler « Rest Day » est traité comme jour de repos.
 *
 * « Voir le programme » : en mode embarqué, émet `viewProgram` (le hub bascule sur l'onglet
 * Programme) ; en autonome, navigue vers `/planning`.
 */
@Component({
  selector: 'app-today-session-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScreenTitleBar, AppIcon, FormDialog, CustomTextField, SessionPage],
  template: `
    @if (todayUuid(); as uuid) {
      <app-session-page [uuid]="uuid" [embedded]="embedded()" />
    } @else {
      @if (!embedded()) { <app-screen-title-bar title="Séance" /> }
      <div class="nosession">
        <app-icon name="bedtime" [size]="48" color="var(--app-text-tertiary)" />
        <span class="nosession__title">Aucune séance aujourd'hui</span>
        <span class="nosession__sub">Repos 🌙 — ou démarre une séance ci-dessous.</span>
        <button type="button" class="nosession__btn" (click)="onViewProgram()">
          <app-icon name="calendar_month" [size]="24" color="var(--app-text-primary)" />
          Voir le programme
        </button>
        @if (plannedToday(); as pw) {
          <button type="button" class="nosession__btn nosession__btn--primary" (click)="startPlanned(pw)">
            <app-icon name="double_arrow" [size]="24" color="var(--app-text-primary)" />
            Démarrer {{ pw.name }}
          </button>
        } @else {
          <button type="button" class="nosession__btn nosession__btn--primary" (click)="openCreate()">
            <app-icon name="add_box" [size]="24" color="var(--app-text-primary)" />
            Démarrer une nouvelle séance
          </button>
        }
      </div>
    }

    <!-- Création séance libre — miroir CreateActualWorkoutDialog (nom requis, « Rest Day » refusé). -->
    <app-form-dialog
      [open]="showCreate()"
      title="Nouvelle séance"
      confirmText="Démarrer"
      [confirmEnabled]="canCreate()"
      [disabledReason]="createDisabledReason()"
      (confirm)="confirmCreate()"
      (dismiss)="showCreate.set(false)"
    >
      <app-custom-text-field
        label="Nom de la séance"
        placeholder="Ex. Push Day"
        [value]="createName()"
        (valueChange)="createName.set($event)"
      />
    </app-form-dialog>
  `,
  styles: [
    `
      /* Centré verticalement dans la zone disponible (miroir Android Arrangement.Center). */
      .nosession {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        min-height: calc(100vh - 160px);
        gap: var(--space-3);
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-8);
        text-align: center;
        box-sizing: border-box;
      }
      .nosession__title {
        color: var(--app-text-primary);
        font-size: var(--font-size-title);
        font-weight: 600;
      }
      .nosession__sub {
        color: var(--app-text-secondary);
      }
      /* Miroir ActionIconWithTextButton defaults : bgButton + textPrimary, icône 24, texte 14.
         Espacements Android : 32dp texte → 1er bouton, 20dp entre boutons (gap 12 + margin). */
      .nosession__btn {
        margin-top: 8px;
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--app-bg-button);
        color: var(--app-text-primary);
        border: none;
        border-radius: var(--radius-md);
        padding: 8px 12px;
        cursor: pointer;
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        font-weight: var(--font-weight-medium);
      }
      .nosession__btn:first-of-type {
        margin-top: 20px;
      }
      /* Bouton d'action principal — fond selectedFill comme Android (ActionIconWithTextButton). */
      .nosession__btn--primary {
        background: var(--app-selected-fill);
        color: var(--app-text-primary);
      }
    `,
  ],
})
export class TodaySessionPage {
  /** Mode embarqué (hub Accueil) : SessionPage sans title bar + « Voir le programme » émet `viewProgram`. */
  readonly embedded = input(false);
  /** Émis au clic sur « Voir le programme » en mode embarqué (le hub bascule sur l'onglet Programme). */
  readonly viewProgram = output<void>();

  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly showCreate = signal(false);
  protected readonly createName = signal('');

  private readonly workouts = toSignal(
    from(liveQuery(() => this.db.actual_workouts.toArray())),
    { initialValue: [] as LocalActualWorkout[] },
  );
  private readonly planned = toSignal(
    from(liveQuery(() => this.db.planned_workouts.toArray())),
    { initialValue: [] as LocalPlannedWorkout[] },
  );

  /** UUID de la séance du jour (date locale = date de l'actual_workout), null si repos. */
  protected readonly todayUuid = computed(() => {
    const now = new Date();
    const t = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const w = this.workouts().find((x) => !x.pendingDeletion && (x.date ?? '').slice(0, 10) === t);
    return w?.uuid ?? null;
  });

  /** Planifiée du jour (day_of_week EN canonique) — « Rest Day » = repos, pas de bouton démarrer. */
  protected readonly plannedToday = computed(() => {
    const day = new Date().toLocaleDateString('en-US', { weekday: 'long' });
    const pw = this.planned().find((p) => !p.pendingDeletion && p.dayOfWeek === day);
    return pw && pw.name.toLowerCase() !== 'rest day' ? pw : null;
  });

  protected readonly canCreate = computed(() => {
    const n = this.createName().trim();
    return n.length > 0 && n.toLowerCase() !== 'rest day';
  });
  protected readonly createDisabledReason = computed(() => {
    const n = this.createName().trim();
    if (n.length === 0) return 'Nom requis';
    if (n.toLowerCase() === 'rest day') return '« Rest Day » est réservé au programme';
    return '';
  });

  /** « Voir le programme » : bascule d'onglet en embarqué, sinon navigue vers la page Programme. */
  protected onViewProgram(): void {
    if (this.embedded()) this.viewProgram.emit();
    else void this.router.navigateByUrl('/planning');
  }

  /** Démarre la planifiée du jour — miroir startActualWorkoutFromPlanned (copie exos + sets). */
  protected async startPlanned(pw: PlannedWorkout): Promise<void> {
    if (this.todayUuid()) return; // anti-doublon séance du jour
    const workoutUUID = await this.createActualWorkout(pw.name);

    const plannedExercises = (
      await this.db.planned_workout_exercises.where('plannedWorkoutUUID').equals(pw.uuid).toArray()
    )
      .filter((pwe) => !pwe.ignored && !pwe.pendingDeletion)
      .sort((a, b) => a.order - b.order);

    const now = new Date().toISOString();
    const exercises: LocalActualWorkoutExercise[] = [];
    const sets: LocalActualWorkoutSet[] = [];
    for (const pwe of plannedExercises) {
      const aweUUID = uuidv4();
      exercises.push({
        uuid: aweUUID,
        actualWorkoutUUID: workoutUUID,
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
      for (let setIndex = 1; setIndex <= pwe.sets; setIndex++) {
        sets.push({
          uuid: uuidv4(),
          actualWorkoutExerciseUUID: aweUUID,
          setOrder: setIndex,
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
    await this.db.actual_workout_exercises.bulkPut(exercises);
    await this.db.actual_workout_sets.bulkPut(sets);
    void this.sync.syncAll().catch(() => undefined);
  }

  protected openCreate(): void {
    this.createName.set('');
    this.showCreate.set(true);
  }

  protected async confirmCreate(): Promise<void> {
    if (!this.canCreate() || this.todayUuid()) return;
    this.showCreate.set(false);
    await this.createActualWorkout(this.createName().trim());
    void this.sync.syncAll().catch(() => undefined);
  }

  /** Crée l'ActualWorkout du jour (synced=false) et retourne son uuid. */
  private async createActualWorkout(name: string): Promise<string> {
    const now = new Date();
    const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const row: LocalActualWorkout = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      name,
      date,
      notes: null,
      location: null,
      isDone: false,
      updatedAt: now.toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.actual_workouts.put(row);
    return row.uuid;
  }
}
