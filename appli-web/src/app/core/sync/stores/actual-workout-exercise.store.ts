import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { ActualWorkoutExerciseApi } from '@core/api/actual-workout-exercise-api';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour ActualWorkoutExercise — miroir de ActualWorkoutExerciseSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class ActualWorkoutExerciseStore extends BaseDexieStore<LocalActualWorkoutExercise> {
  readonly name = 'actual_workout_exercises';
  readonly wsKey = 'actual_workout_exercise';
  private readonly db = inject(AppDb);
  private readonly api = inject(ActualWorkoutExerciseApi);

  protected table(): Table<LocalActualWorkoutExercise, string> {
    return this.db.actual_workout_exercises;
  }

  async fetchRemote(): Promise<LocalActualWorkoutExercise[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalActualWorkoutExercise): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalActualWorkoutExercise[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalActualWorkoutExercise): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
