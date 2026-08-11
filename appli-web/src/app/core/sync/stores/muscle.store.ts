import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { MuscleApi } from '@core/api/muscle-api';
import { LocalMuscle } from '@core/models/muscle.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour Muscle — miroir de MuscleSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class MuscleStore extends BaseDexieStore<LocalMuscle> {
  readonly name = 'muscles';
  readonly wsKey = 'muscle';
  private readonly db = inject(AppDb);
  private readonly api = inject(MuscleApi);

  protected table(): Table<LocalMuscle, string> {
    return this.db.muscles;
  }

  async fetchRemote(): Promise<LocalMuscle[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((m) => ({ ...m, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalMuscle): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalMuscle[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalMuscle): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
