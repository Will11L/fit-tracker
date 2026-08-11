import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { RoutinePeriodApi } from '@core/api/routine-period-api';
import { LocalRoutinePeriod } from '@core/models/routine-period.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour RoutinePeriod — miroir de RoutinePeriodSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class RoutinePeriodStore extends BaseDexieStore<LocalRoutinePeriod> {
  readonly name = 'routine_periods';
  readonly wsKey = 'routine_period';
  private readonly db = inject(AppDb);
  private readonly api = inject(RoutinePeriodApi);

  protected table(): Table<LocalRoutinePeriod, string> {
    return this.db.routine_periods;
  }

  async fetchRemote(): Promise<LocalRoutinePeriod[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalRoutinePeriod): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalRoutinePeriod[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalRoutinePeriod): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
