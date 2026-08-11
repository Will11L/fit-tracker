import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RecipeIngredient } from '@core/models/recipe-ingredient.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class RecipeIngredientApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/recipe-ingredients`;

  getAll(): Observable<RecipeIngredient[]> {
    return this.http.get<RecipeIngredient[]>(this.base);
  }
  upsert(i: RecipeIngredient): Observable<RecipeIngredient> {
    return this.http.put<RecipeIngredient>(`${this.base}/${i.uuid}`, this.toBody(i));
  }
  /** Upsert groupé `PUT /recipe-ingredients/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: RecipeIngredient[]): Observable<RecipeIngredient[]> {
    return this.http.put<RecipeIngredient[]>(`${this.base}/bulk`, items.map((i) => this.toBody(i)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par RecipeIngredientCreate (camelCase). */
  private toBody(i: RecipeIngredient) {
    return {
      uuid: i.uuid,
      recipeUUID: i.recipeUUID,
      foodUUID: i.foodUUID,
      quantityG: i.quantityG,
      orderIndex: i.orderIndex,
      updatedAt: i.updatedAt,
    };
  }
}
