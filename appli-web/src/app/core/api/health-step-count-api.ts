import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HealthStepCount } from '@core/models/health-step-count.model';
import { API_BASE_URL } from './api.config';

/**
 * Accès REST aux pas santé — **lecture seule côté web** : le web ne fait qu'afficher
 * ce que Health Connect (Android) a poussé, il n'écrit jamais ces buckets (pas d'upsert/delete).
 */
@Injectable({ providedIn: 'root' })
export class HealthStepCountApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/health-step-counts`;

  getAll(): Observable<HealthStepCount[]> {
    return this.http.get<HealthStepCount[]>(this.base);
  }
}
