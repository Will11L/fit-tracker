import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Recipe } from '@core/models/recipe.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class RecipeApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/recipes`;

  getAll(): Observable<Recipe[]> {
    return this.http.get<Recipe[]>(this.base);
  }
  upsert(r: Recipe): Observable<Recipe> {
    return this.http.put<Recipe>(`${this.base}/${r.uuid}`, this.toBody(r));
  }
  /** Upsert groupé `PUT /recipes/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Recipe[]): Observable<Recipe[]> {
    return this.http.put<Recipe[]>(`${this.base}/bulk`, items.map((r) => this.toBody(r)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par RecipeCreate (camelCase, sans userId — politique 8). */
  private toBody(r: Recipe) {
    return {
      uuid: r.uuid,
      name: r.name,
      kind: r.kind,
      totalWeightG: r.totalWeightG,
      updatedAt: r.updatedAt,
    };
  }
}
