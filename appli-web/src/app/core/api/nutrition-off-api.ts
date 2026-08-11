import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { OffProduct } from '@core/models/off-product.model';
import { API_BASE_URL } from './api.config';

/**
 * Client du proxy Open Food Facts serveur (docs/NUTRITION_DESIGN.md §4.1) — read-only,
 * pas une entité synchronisable. Le client copie un produit choisi dans son catalogue
 * `foods` (source=OFF, sourceRef=barcode) via FoodRepository.importFromOff.
 */
@Injectable({ providedIn: 'root' })
export class NutritionOffApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/nutrition/off`;

  search(q: string, pageSize = 20): Observable<OffProduct[]> {
    const params = new HttpParams().set('q', q).set('pageSize', pageSize);
    return this.http.get<OffProduct[]>(`${this.base}/search`, { params });
  }

  getProduct(barcode: string): Observable<OffProduct> {
    return this.http.get<OffProduct>(`${this.base}/product/${barcode}`);
  }
}
