import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { TaskApi } from '@core/api/task-api';
import { LocalTask } from '@core/models/task.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour Task — miroir de TaskSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class TaskStore extends BaseDexieStore<LocalTask> {
  readonly name = 'tasks';
  readonly wsKey = 'task';
  private readonly db = inject(AppDb);
  private readonly api = inject(TaskApi);

  protected table(): Table<LocalTask, string> {
    return this.db.tasks;
  }

  async fetchRemote(): Promise<LocalTask[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalTask): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalTask[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalTask): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
