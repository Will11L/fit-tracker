import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RoutinePeriod } from '@core/models/routine-period.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class RoutinePeriodApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/routine-periods`;

  getAll(): Observable<RoutinePeriod[]> {
    return this.http.get<RoutinePeriod[]>(this.base);
  }

  upsert(p: RoutinePeriod): Observable<RoutinePeriod> {
    // Corps wire attendu par RoutinePeriodCreate (camelCase, sans userId).
    return this.http.put<RoutinePeriod>(`${this.base}/${p.uuid}`, {
      uuid: p.uuid,
      name: p.name,
      startTime: p.startTime,
      endTime: p.endTime,
      order: p.order,
      updatedAt: p.updatedAt,
    });
  }

  /** Upsert groupé `PUT /routine-periods/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: RoutinePeriod[]): Observable<RoutinePeriod[]> {
    return this.http.put<RoutinePeriod[]>(
      `${this.base}/bulk`,
      items.map((p) => ({
        uuid: p.uuid,
        name: p.name,
        startTime: p.startTime,
        endTime: p.endTime,
        order: p.order,
        updatedAt: p.updatedAt,
      })),
    );
  }

  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
