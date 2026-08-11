import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MuscleGoal } from '@core/models/muscle-goal.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class MuscleGoalApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/muscle-goals`;

  getAll(): Observable<MuscleGoal[]> {
    return this.http.get<MuscleGoal[]>(this.base);
  }
  upsert(g: MuscleGoal): Observable<MuscleGoal> {
    return this.http.put<MuscleGoal>(`${this.base}/${g.uuid}`, {
      uuid: g.uuid,
      muscleUUID: g.muscleUUID,
      priority: g.priority,
      done: g.done,
      target: g.target,
      weekISO: g.weekISO,
      status: g.status,
      addedManually: g.addedManually,
      updatedAt: g.updatedAt,
    });
  }
  /** Upsert groupé `PUT /muscle-goals/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: MuscleGoal[]): Observable<MuscleGoal[]> {
    return this.http.put<MuscleGoal[]>(
      `${this.base}/bulk`,
      items.map((g) => ({
        uuid: g.uuid,
        muscleUUID: g.muscleUUID,
        priority: g.priority,
        done: g.done,
        target: g.target,
        weekISO: g.weekISO,
        status: g.status,
        addedManually: g.addedManually,
        updatedAt: g.updatedAt,
      })),
    );
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
