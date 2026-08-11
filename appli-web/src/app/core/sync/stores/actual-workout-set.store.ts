import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { ActualWorkoutSetApi } from '@core/api/actual-workout-set-api';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour ActualWorkoutSet — miroir de ActualWorkoutSetSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class ActualWorkoutSetStore extends BaseDexieStore<LocalActualWorkoutSet> {
  readonly name = 'actual_workout_sets';
  readonly wsKey = 'actual_workout_set';
  private readonly db = inject(AppDb);
  private readonly api = inject(ActualWorkoutSetApi);

  protected table(): Table<LocalActualWorkoutSet, string> {
    return this.db.actual_workout_sets;
  }

  async fetchRemote(): Promise<LocalActualWorkoutSet[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalActualWorkoutSet): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalActualWorkoutSet[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalActualWorkoutSet): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
