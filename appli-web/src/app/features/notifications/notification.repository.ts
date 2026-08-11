import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from, map } from 'rxjs';
import { LocalNotification } from '@core/models/notification.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';

/**
 * Façade UI pour Notifications (miroir de NotificationViewModel + NotificationRepository Android).
 * Lecture réactive Dexie liveQuery -> signal (les plus récentes d'abord). Écritures optimistes
 * (synced=false) puis sync best-effort. markAsRead pose `readAt`, remove = tombstone pendingDeletion.
 */
@Injectable({ providedIn: 'root' })
export class NotificationRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);

  /** Notifications visibles (hors pendingDeletion), les plus récentes en tête. */
  readonly notifications: Signal<LocalNotification[]> = toSignal(
    from(liveQuery(() => this.db.notifications.filter((n) => !n.pendingDeletion).sortBy('createdAt'))).pipe(
      map((list) => [...list].reverse()),
    ),
    { initialValue: [] as LocalNotification[] },
  );

  async markAsRead(uuid: string): Promise<void> {
    const n = await this.db.notifications.get(uuid);
    if (!n || n.readAt) return;
    const now = new Date().toISOString();
    await this.db.notifications.update(uuid, { readAt: now, synced: false, updatedAt: now });
    this.triggerSync();
  }

  async markAllAsRead(): Promise<void> {
    const now = new Date().toISOString();
    const unread = await this.db.notifications.filter((n) => !n.readAt && !n.pendingDeletion).toArray();
    if (unread.length === 0) return;
    await Promise.all(
      unread.map((n) => this.db.notifications.update(n.uuid, { readAt: now, synced: false, updatedAt: now })),
    );
    this.triggerSync();
  }

  async remove(uuid: string): Promise<void> {
    await this.db.notifications.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
}
