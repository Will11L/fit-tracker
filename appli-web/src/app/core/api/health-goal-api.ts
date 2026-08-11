import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HealthGoal } from '@core/models/health-goal.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class HealthGoalApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/health-goals`;

  getAll(): Observable<HealthGoal[]> {
    return this.http.get<HealthGoal[]>(this.base);
  }
  upsert(g: HealthGoal): Observable<HealthGoal> {
    return this.http.put<HealthGoal>(`${this.base}/${g.uuid}`, this.toBody(g));
  }
  /** Upsert groupé `PUT /health-goals/bulk` — 1 requête pour N rows. */
  upsertAll(items: HealthGoal[]): Observable<HealthGoal[]> {
    return this.http.put<HealthGoal[]>(`${this.base}/bulk`, items.map((g) => this.toBody(g)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par HealthGoalCreate (camelCase, sans userId — politique 8). */
  private toBody(g: HealthGoal) {
    return {
      uuid: g.uuid,
      type: g.type,
      target: g.target,
      effectiveFrom: g.effectiveFrom,
      updatedAt: g.updatedAt,
    };
  }
}
