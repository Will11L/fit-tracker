import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HealthMetric } from '@core/models/health-metric.model';
import { API_BASE_URL } from './api.config';

/**
 * Accès REST aux métriques santé — **lecture seule côté web** : le web ne fait qu'afficher
 * ce que Health Connect (Android) a poussé, il n'écrit jamais ces métriques (pas d'upsert/delete).
 */
@Injectable({ providedIn: 'root' })
export class HealthMetricApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/health-metrics`;

  getAll(): Observable<HealthMetric[]> {
    return this.http.get<HealthMetric[]>(this.base);
  }
}
