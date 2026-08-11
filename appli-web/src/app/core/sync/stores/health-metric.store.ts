import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { HealthMetricApi } from '@core/api/health-metric-api';
import { LocalHealthMetric } from '@core/models/health-metric.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/**
 * Store des métriques santé — **lecture seule** : elles viennent de Health Connect (Android),
 * le web ne les écrit jamais. `fetchRemote` (GET) alimente Dexie ; les 3 opérations de push
 * sont volontairement des no-op → aucune écriture n'est jamais émise vers le serveur
 * (critère d'acceptation « aucune écriture »).
 */
@Injectable({ providedIn: 'root' })
export class HealthMetricStore extends BaseDexieStore<LocalHealthMetric> {
  readonly name = 'health_metrics';
  readonly wsKey = 'health_metric';
  private readonly db = inject(AppDb);
  private readonly api = inject(HealthMetricApi);

  protected table(): Table<LocalHealthMetric, string> {
    return this.db.health_metrics;
  }
  async fetchRemote(): Promise<LocalHealthMetric[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((m) => ({ ...m, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(): Promise<void> {
    /* read-only : aucune écriture émise depuis le web */
  }
  async pushUpsertBulk(): Promise<void> {
    /* read-only : aucune écriture émise depuis le web */
  }
  async pushDelete(): Promise<void> {
    /* read-only : aucune écriture émise depuis le web */
  }
}
