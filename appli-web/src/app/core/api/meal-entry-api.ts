import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MealEntry } from '@core/models/meal-entry.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class MealEntryApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/meal-entries`;

  getAll(): Observable<MealEntry[]> {
    return this.http.get<MealEntry[]>(this.base);
  }
  upsert(e: MealEntry): Observable<MealEntry> {
    return this.http.put<MealEntry>(`${this.base}/${e.uuid}`, this.toBody(e));
  }
  /** Upsert groupé `PUT /meal-entries/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: MealEntry[]): Observable<MealEntry[]> {
    return this.http.put<MealEntry[]>(`${this.base}/bulk`, items.map((e) => this.toBody(e)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par MealEntryCreate (camelCase, snapshot D5 per-100g). */
  private toBody(e: MealEntry) {
    return {
      uuid: e.uuid,
      mealUUID: e.mealUUID,
      foodUUID: e.foodUUID,
      recipeUUID: e.recipeUUID,
      displayName: e.displayName,
      quantityG: e.quantityG,
      portionLabel: e.portionLabel,
      kcalPer100g: e.kcalPer100g,
      proteinPer100g: e.proteinPer100g,
      carbsPer100g: e.carbsPer100g,
      fatPer100g: e.fatPer100g,
      fiberPer100g: e.fiberPer100g,
      sugarPer100g: e.sugarPer100g,
      satFatPer100g: e.satFatPer100g,
      saltPer100g: e.saltPer100g,
      ironPer100g: e.ironPer100g,
      calciumPer100g: e.calciumPer100g,
      magnesiumPer100g: e.magnesiumPer100g,
      zincPer100g: e.zincPer100g,
      potassiumPer100g: e.potassiumPer100g,
      sodiumPer100g: e.sodiumPer100g,
      vitaminCPer100g: e.vitaminCPer100g,
      vitaminDPer100g: e.vitaminDPer100g,
      vitaminB12Per100g: e.vitaminB12Per100g,
      vitaminAPer100g: e.vitaminAPer100g,
      updatedAt: e.updatedAt,
    };
  }
}
