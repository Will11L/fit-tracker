import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { SupersetGroupApi } from '@core/api/superset-group-api';
import { LocalSupersetGroup } from '@core/models/superset-group.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/** Adaptateur de sync pour SupersetGroup — miroir de SupersetGroupSyncable (Android). */
@Injectable({ providedIn: 'root' })
export class SupersetGroupStore extends BaseDexieStore<LocalSupersetGroup> {
  readonly name = 'superset_groups';
  readonly wsKey = 'superset_group';
  private readonly db = inject(AppDb);
  private readonly api = inject(SupersetGroupApi);

  protected table(): Table<LocalSupersetGroup, string> {
    return this.db.superset_groups;
  }

  async fetchRemote(): Promise<LocalSupersetGroup[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((e) => ({ ...e, synced: true, pendingDeletion: false }));
  }

  async pushUpsert(row: LocalSupersetGroup): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }

  async pushUpsertBulk(rows: LocalSupersetGroup[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }

  async pushDelete(row: LocalSupersetGroup): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
