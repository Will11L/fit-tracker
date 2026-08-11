import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppNotification } from '@core/models/notification.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/notifications`;

  getAll(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.base);
  }
  upsert(n: AppNotification): Observable<AppNotification> {
    return this.http.put<AppNotification>(`${this.base}/${n.uuid}`, {
      uuid: n.uuid,
      type: n.type,
      level: n.level,
      title: n.title,
      body: n.body ?? null,
      data: n.data ?? null,
      dedupeKey: n.dedupeKey ?? null,
      createdAt: n.createdAt,
      readAt: n.readAt ?? null,
      archivedAt: n.archivedAt ?? null,
      updatedAt: n.updatedAt,
    });
  }
  /** Upsert groupé `PUT /notifications/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: AppNotification[]): Observable<AppNotification[]> {
    return this.http.put<AppNotification[]>(
      `${this.base}/bulk`,
      items.map((n) => ({
        uuid: n.uuid,
        type: n.type,
        level: n.level,
        title: n.title,
        body: n.body ?? null,
        data: n.data ?? null,
        dedupeKey: n.dedupeKey ?? null,
        createdAt: n.createdAt,
        readAt: n.readAt ?? null,
        archivedAt: n.archivedAt ?? null,
        updatedAt: n.updatedAt,
      })),
    );
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
