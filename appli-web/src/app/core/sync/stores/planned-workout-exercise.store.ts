import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { PlannedWorkoutExerciseApi } from '@core/api/planned-workout-exercise-api';
import { LocalPlannedWorkoutExercise } from '@core/models/planned-workout-exercise.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour PlannedWorkoutExercise — miroir de PlannedWorkoutExerciseSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class PlannedWorkoutExerciseStore extends BaseDexieStore<LocalPlannedWorkoutExercise> {
  readonly name = 'planned_workout_exercises';
  readonly wsKey = 'planned_workout_exercise';
  private readonly db = inject(AppDb);
  private readonly api = inject(PlannedWorkoutExerciseApi);

  protected table(): Table<LocalPlannedWorkoutExercise, string> {
    return this.db.planned_workout_exercises;
  }

  async fetchRemote(): Promise<LocalPlannedWorkoutExercise[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalPlannedWorkoutExercise): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalPlannedWorkoutExercise[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalPlannedWorkoutExercise): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
