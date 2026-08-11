import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { ExerciseMuscleApi } from '@core/api/exercise-muscle-api';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour ExerciseMuscle — miroir de ExerciseMuscleSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class ExerciseMuscleStore extends BaseDexieStore<LocalExerciseMuscle> {
  readonly name = 'exercise_muscles';
  readonly wsKey = 'exercise_muscle';
  private readonly db = inject(AppDb);
  private readonly api = inject(ExerciseMuscleApi);

  protected table(): Table<LocalExerciseMuscle, string> {
    return this.db.exercise_muscles;
  }

  async fetchRemote(): Promise<LocalExerciseMuscle[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalExerciseMuscle): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalExerciseMuscle[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalExerciseMuscle): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
