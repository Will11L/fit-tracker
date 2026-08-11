import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { TrainingCycleApi } from '@core/api/training-cycle-api';
import { LocalTrainingCycle } from '@core/models/training-cycle.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour TrainingCycle — miroir de TrainingCycleSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class TrainingCycleStore extends BaseDexieStore<LocalTrainingCycle> {
  readonly name = 'training_cycles';
  readonly wsKey = 'training_cycle';
  private readonly db = inject(AppDb);
  private readonly api = inject(TrainingCycleApi);

  protected table(): Table<LocalTrainingCycle, string> {
    return this.db.training_cycles;
  }

  async fetchRemote(): Promise<LocalTrainingCycle[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalTrainingCycle): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalTrainingCycle[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalTrainingCycle): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
