import { InjectionToken } from '@angular/core';
import { Table, liveQuery } from 'dexie';
import { Observable, from, map } from 'rxjs';

/** Ligne synchronisable = entité + métadonnées de sync (miroir des flags Room synced/pendingDeletion). */
export interface SyncRow {
  uuid: string;
  updatedAt: string | null;
  synced: boolean;
  pendingDeletion: boolean;
}

/** Compteurs de sync d'une entité (miroir de EntityStats Android). */
export interface SyncStats {
  name: string;
  total: number;
  synced: number;
  unsynced: number;
  pendingDeletion: number;
}

/** Catégorise les rows locales en compteurs. */
export function computeStats(name: string, rows: SyncRow[]): SyncStats {
  let synced = 0;
  let unsynced = 0;
  let pendingDeletion = 0;
  for (const r of rows) {
    if (r.pendingDeletion) pendingDeletion++;
    else if (r.synced) synced++;
    else unsynced++;
  }
  return { name, total: rows.length, synced, unsynced, pendingDeletion };
}

/** Contrat d'une entité synchronisable — miroir de SyncableEntity (Android). */
export interface SyncableStore<T extends SyncRow> {
  readonly name: string;
  /** Préfixe des events WebSocket (singulier, ex. 'exercise' pour `exercise_updated`/`exercise_deleted`). */
  readonly wsKey: string;
  getAllLocal(): Promise<T[]>;
  bulkPutLocal(rows: T[]): Promise<void>;
  bulkDeleteLocal(uuids: string[]): Promise<void>;
  markSyncedLocal(uuid: string): Promise<void>;
  deleteLocal(uuid: string): Promise<void>;
  clearLocal(): Promise<void>;
  liveStats(): Observable<SyncStats>;
  fetchRemote(): Promise<T[]>;
  pushUpsert(row: T): Promise<void>;
  /** Upsert groupé via `PUT /<entity>/bulk` (miroir upsertBulk Android) — 1 requête pour N rows. */
  pushUpsertBulk(rows: T[]): Promise<void>;
  pushDelete(row: T): Promise<void>;
}

/**
 * Implémentation Dexie des opérations LOCALES ; les opérations REMOTE restent
 * abstraites (une par entité). Miroir du squelette DAO Style A + SyncableEntity.
 */
export abstract class BaseDexieStore<T extends SyncRow> implements SyncableStore<T> {
  abstract readonly name: string;
  abstract readonly wsKey: string;
  protected abstract table(): Table<T, string>;

  getAllLocal(): Promise<T[]> {
    return this.table().toArray();
  }

  async bulkPutLocal(rows: T[]): Promise<void> {
    await this.table().bulkPut(rows);
  }

  async bulkDeleteLocal(uuids: string[]): Promise<void> {
    await this.table().bulkDelete(uuids);
  }

  async markSyncedLocal(uuid: string): Promise<void> {
    const row = await this.table().get(uuid);
    if (row) {
      row.synced = true;
      row.pendingDeletion = false;
      await this.table().put(row);
    }
  }

  async deleteLocal(uuid: string): Promise<void> {
    await this.table().delete(uuid);
  }

  async clearLocal(): Promise<void> {
    await this.table().clear();
  }

  /** Compteurs réactifs (Dexie liveQuery -> Observable) — pour la page Sync. */
  liveStats(): Observable<SyncStats> {
    return from(liveQuery(() => this.table().toArray())).pipe(
      map((rows) => computeStats(this.name, rows)),
    );
  }

  abstract fetchRemote(): Promise<T[]>;
  abstract pushUpsert(row: T): Promise<void>;
  abstract pushUpsertBulk(rows: T[]): Promise<void>;
  abstract pushDelete(row: T): Promise<void>;
}

/** Registre DI des stores synchronisables (miroir de SyncRegistry). Chaque entité s'enregistre via un multi-provider. */
export const SYNCABLE_STORES = new InjectionToken<SyncableStore<SyncRow>[]>('SYNCABLE_STORES');
