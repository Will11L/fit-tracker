import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NutritionGoal } from '@core/models/nutrition-goal.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class NutritionGoalApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/nutrition-goals`;

  getAll(): Observable<NutritionGoal[]> {
    return this.http.get<NutritionGoal[]>(this.base);
  }
  upsert(g: NutritionGoal): Observable<NutritionGoal> {
    return this.http.put<NutritionGoal>(`${this.base}/${g.uuid}`, this.toBody(g));
  }
  /** Upsert groupé `PUT /nutrition-goals/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: NutritionGoal[]): Observable<NutritionGoal[]> {
    return this.http.put<NutritionGoal[]>(`${this.base}/bulk`, items.map((g) => this.toBody(g)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par NutritionGoalCreate (camelCase, sans userId — politique 8). */
  private toBody(g: NutritionGoal) {
    return {
      uuid: g.uuid,
      effectiveFrom: g.effectiveFrom,
      dayKind: g.dayKind,
      kcal: g.kcal,
      proteinG: g.proteinG,
      carbsG: g.carbsG,
      fatG: g.fatG,
      updatedAt: g.updatedAt,
    };
  }
}
