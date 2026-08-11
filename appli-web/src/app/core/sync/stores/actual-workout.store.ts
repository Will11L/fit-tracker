import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { ActualWorkoutApi } from '@core/api/actual-workout-api';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour ActualWorkout — miroir de ActualWorkoutSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class ActualWorkoutStore extends BaseDexieStore<LocalActualWorkout> {
  readonly name = 'actual_workouts';
  readonly wsKey = 'actual_workout';
  private readonly db = inject(AppDb);
  private readonly api = inject(ActualWorkoutApi);

  protected table(): Table<LocalActualWorkout, string> {
    return this.db.actual_workouts;
  }

  async fetchRemote(): Promise<LocalActualWorkout[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalActualWorkout): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalActualWorkout[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalActualWorkout): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
