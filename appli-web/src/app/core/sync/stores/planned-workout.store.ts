import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { PlannedWorkoutApi } from '@core/api/planned-workout-api';
import { LocalPlannedWorkout } from '@core/models/planned-workout.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour PlannedWorkout — miroir de PlannedWorkoutSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class PlannedWorkoutStore extends BaseDexieStore<LocalPlannedWorkout> {
  readonly name = 'planned_workouts';
  readonly wsKey = 'planned_workout';
  private readonly db = inject(AppDb);
  private readonly api = inject(PlannedWorkoutApi);

  protected table(): Table<LocalPlannedWorkout, string> {
    return this.db.planned_workouts;
  }

  async fetchRemote(): Promise<LocalPlannedWorkout[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalPlannedWorkout): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalPlannedWorkout[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalPlannedWorkout): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
