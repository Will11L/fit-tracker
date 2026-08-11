import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Meal } from '@core/models/meal.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class MealApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/meals`;

  getAll(): Observable<Meal[]> {
    return this.http.get<Meal[]>(this.base);
  }
  upsert(m: Meal): Observable<Meal> {
    return this.http.put<Meal>(`${this.base}/${m.uuid}`, this.toBody(m));
  }
  /** Upsert groupé `PUT /meals/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Meal[]): Observable<Meal[]> {
    return this.http.put<Meal[]>(`${this.base}/bulk`, items.map((m) => this.toBody(m)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par MealCreate (camelCase, sans userId — politique 8). */
  private toBody(m: Meal) {
    return {
      uuid: m.uuid,
      date: m.date,
      name: m.name,
      orderIndex: m.orderIndex,
      time: m.time ?? null,
      presetUuid: m.presetUuid,
      updatedAt: m.updatedAt,
    };
  }
}
