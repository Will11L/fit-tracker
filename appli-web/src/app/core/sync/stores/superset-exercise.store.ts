import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { SupersetExerciseApi } from '@core/api/superset-exercise-api';
import { LocalSupersetExercise } from '@core/models/superset-exercise.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour SupersetExercise — miroir de SupersetExerciseSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class SupersetExerciseStore extends BaseDexieStore<LocalSupersetExercise> {
  readonly name = 'superset_exercises';
  readonly wsKey = 'superset_exercise';
  private readonly db = inject(AppDb);
  private readonly api = inject(SupersetExerciseApi);

  protected table(): Table<LocalSupersetExercise, string> {
    return this.db.superset_exercises;
  }

  async fetchRemote(): Promise<LocalSupersetExercise[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalSupersetExercise): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalSupersetExercise[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalSupersetExercise): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
