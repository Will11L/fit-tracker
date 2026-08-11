import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Food } from '@core/models/food.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class FoodApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/foods`;

  getAll(): Observable<Food[]> {
    return this.http.get<Food[]>(this.base);
  }
  upsert(f: Food): Observable<Food> {
    return this.http.put<Food>(`${this.base}/${f.uuid}`, this.toBody(f));
  }
  /** Upsert groupé `PUT /foods/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Food[]): Observable<Food[]> {
    return this.http.put<Food[]>(`${this.base}/bulk`, items.map((f) => this.toBody(f)));
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }

  /** Corps wire attendu par FoodCreate (camelCase, sans userId — politique 8). */
  private toBody(f: Food) {
    return {
      uuid: f.uuid,
      name: f.name,
      brand: f.brand,
      source: f.source,
      sourceRef: f.sourceRef,
      foodGroup: f.foodGroup,
      kcalPer100g: f.kcalPer100g,
      proteinPer100g: f.proteinPer100g,
      carbsPer100g: f.carbsPer100g,
      fatPer100g: f.fatPer100g,
      fiberPer100g: f.fiberPer100g,
      sugarPer100g: f.sugarPer100g,
      satFatPer100g: f.satFatPer100g,
      saltPer100g: f.saltPer100g,
      ironPer100g: f.ironPer100g,
      calciumPer100g: f.calciumPer100g,
      magnesiumPer100g: f.magnesiumPer100g,
      zincPer100g: f.zincPer100g,
      potassiumPer100g: f.potassiumPer100g,
      sodiumPer100g: f.sodiumPer100g,
      vitaminCPer100g: f.vitaminCPer100g,
      vitaminDPer100g: f.vitaminDPer100g,
      vitaminB12Per100g: f.vitaminB12Per100g,
      vitaminAPer100g: f.vitaminAPer100g,
      isFavorite: f.isFavorite,
      archived: f.archived,
      isWater: f.isWater,
      updatedAt: f.updatedAt,
    };
  }
}
