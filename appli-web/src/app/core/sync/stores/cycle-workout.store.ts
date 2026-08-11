import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { CycleWorkoutApi } from '@core/api/cycle-workout-api';
import { LocalCycleWorkout } from '@core/models/cycle-workout.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour CycleWorkout — miroir de CycleWorkoutSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class CycleWorkoutStore extends BaseDexieStore<LocalCycleWorkout> {
  readonly name = 'cycle_workouts';
  readonly wsKey = 'cycle_workout';
  private readonly db = inject(AppDb);
  private readonly api = inject(CycleWorkoutApi);

  protected table(): Table<LocalCycleWorkout, string> {
    return this.db.cycle_workouts;
  }

  async fetchRemote(): Promise<LocalCycleWorkout[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalCycleWorkout): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalCycleWorkout[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalCycleWorkout): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
