import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { TaskCheckApi } from '@core/api/task-check-api';
import { LocalTaskCheck } from '@core/models/task-check.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour TaskCheck — miroir de TaskCheckSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class TaskCheckStore extends BaseDexieStore<LocalTaskCheck> {
  readonly name = 'task_checks';
  readonly wsKey = 'task_check';
  private readonly db = inject(AppDb);
  private readonly api = inject(TaskCheckApi);

  protected table(): Table<LocalTaskCheck, string> {
    return this.db.task_checks;
  }

  async fetchRemote(): Promise<LocalTaskCheck[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalTaskCheck): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalTaskCheck[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalTaskCheck): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
