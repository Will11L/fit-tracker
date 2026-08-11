import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { HealthStepCountApi } from '@core/api/health-step-count-api';
import { LocalHealthStepCount } from '@core/models/health-step-count.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

/**
 * Store des pas santé — **lecture seule** : les pas viennent de Health Connect (Android),
 * le web ne les écrit jamais. `fetchRemote` (GET) alimente Dexie ; les 3 opérations de push
 * sont volontairement des no-op → même passé en `synced=false` par un outil dev, aucune
 * écriture n'est jamais émise vers le serveur (critère d'acceptation « aucune écriture »).
 */
@Injectable({ providedIn: 'root' })
export class HealthStepCountStore extends BaseDexieStore<LocalHealthStepCount> {
  readonly name = 'health_step_counts';
  readonly wsKey = 'health_step_count';
  private readonly db = inject(AppDb);
  private readonly api = inject(HealthStepCountApi);

  protected table(): Table<LocalHealthStepCount, string> {
    return this.db.health_step_counts;
  }
  async fetchRemote(): Promise<LocalHealthStepCount[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((s) => ({ ...s, synced: true, pendingDeletion: false }));
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
