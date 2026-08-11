import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { ExerciseApi } from '@core/api/exercise-api';
import { LocalExercise } from '@core/models/exercise.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour Exercise — miroir de ExerciseSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class ExerciseStore extends BaseDexieStore<LocalExercise> {
  readonly name = 'exercises';
  readonly wsKey = 'exercise';
  private readonly db = inject(AppDb);
  private readonly api = inject(ExerciseApi);

  protected table(): Table<LocalExercise, string> {
    return this.db.exercises;
  }

  async fetchRemote(): Promise<LocalExercise[]> {
    const wire = await firstValueFrom(this.api.getAll());
    // insertFromServer : force synced=true / pendingDeletion=false (miroir Android).
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalExercise): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalExercise[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalExercise): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      // 404 = déjà supprimé côté serveur -> delete considéré convergé.
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
