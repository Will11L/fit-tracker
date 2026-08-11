import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { NotificationApi } from '@core/api/notification-api';
import { LocalNotification } from '@core/models/notification.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class NotificationStore extends BaseDexieStore<LocalNotification> {
  readonly name = 'notifications';
  readonly wsKey = 'notification';
  private readonly db = inject(AppDb);
  private readonly api = inject(NotificationApi);

  protected table(): Table<LocalNotification, string> {
    return this.db.notifications;
  }
  async fetchRemote(): Promise<LocalNotification[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((n) => ({ ...n, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalNotification): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalNotification[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalNotification): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
