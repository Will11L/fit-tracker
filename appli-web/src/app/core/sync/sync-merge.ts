import { SyncRow, SyncableStore } from './syncable-store';

/** Vrai si la version distante est strictement plus récente (last-write-wins, miroir isRemoteNewer). */
export function isRemoteNewer(localUpdatedAt: string | null, remoteUpdatedAt: string | null): boolean {
  if (!remoteUpdatedAt) return false;
  if (!localUpdatedAt) return true;
  const l = Date.parse(localUpdatedAt);
  const r = Date.parse(remoteUpdatedAt);
  if (Number.isNaN(l) || Number.isNaN(r)) return false; // format inattendu : on n'écrase pas
  return r > l;
}

/**
 * Pousse les locaux non syncés + suppressions en attente (miroir pushAll). Ordre delete-first.
 * Upserts en bulk (1 requête pour N rows, miroir EntitySyncUtils.syncEntity) avec fallback
 * 1-par-1 si le bulk échoue. Tolérant offline : une exception laisse les rows non syncées
 * -> re-tentées au prochain sync.
 */
export async function pushStore<T extends SyncRow>(store: SyncableStore<T>): Promise<void> {
  const rows = await store.getAllLocal();
  for (const r of rows) {
    if (r.pendingDeletion) {
      await store.pushDelete(r);
      await store.deleteLocal(r.uuid);
    }
  }
  const unsynced = rows.filter((r) => !r.pendingDeletion && !r.synced);
  await pushBulkWithFallback(store, unsynced);
}

/**
 * Pousse TOUTES les rows (pas seulement les non-syncées) — outil dev "Upsert" (miroir bulkPushAll).
 * Les suppressions en attente partent quand même en DELETE.
 */
export async function pushAllRows<T extends SyncRow>(store: SyncableStore<T>): Promise<void> {
  const rows = await store.getAllLocal();
  for (const r of rows) {
    if (r.pendingDeletion) {
      await store.pushDelete(r);
      await store.deleteLocal(r.uuid);
    }
  }
  await pushBulkWithFallback(store, rows.filter((r) => !r.pendingDeletion));
}

/** Bulk d'abord, fallback upserts individuels si le bulk échoue (miroir syncEntity Android). */
async function pushBulkWithFallback<T extends SyncRow>(store: SyncableStore<T>, rows: T[]): Promise<void> {
  if (rows.length === 0) return;
  try {
    await store.pushUpsertBulk(rows);
    for (const r of rows) await store.markSyncedLocal(r.uuid);
  } catch {
    for (const r of rows) {
      await store.pushUpsert(r);
      await store.markSyncedLocal(r.uuid);
    }
  }
}

/**
 * Merge le remote dans le local + prune les orphelins (miroir SyncMergeOps.mergeFromRemote).
 * Contrat 3 de SYNC_PATTERN : un local `synced && absent du remote` = supprimé ailleurs -> supprimé localement.
 */
export async function mergeFromRemote<T extends SyncRow>(store: SyncableStore<T>): Promise<void> {
  const remote = await store.fetchRemote();
  const local = await store.getAllLocal();
  const localByKey = new Map(local.map((r) => [r.uuid, r]));

  const toPut: T[] = [];
  for (const r of remote) {
    const l = localByKey.get(r.uuid);
    if (!l || isRemoteNewer(l.updatedAt, r.updatedAt)) {
      toPut.push({ ...r, synced: true, pendingDeletion: false });
    }
  }
  if (toPut.length > 0) await store.bulkPutLocal(toPut);

  const remoteKeys = new Set(remote.map((r) => r.uuid));
  const stale = local.filter((l) => l.synced && !remoteKeys.has(l.uuid)).map((l) => l.uuid);
  if (stale.length > 0) await store.bulkDeleteLocal(stale);
}

/**
 * Vide le local puis ré-insère intégralement le remote (miroir pullThenReplace, outil "Remplacer").
 * Écrase tout : les modifs locales non poussées sont perdues (c'est le but de l'outil).
 */
export async function replaceFromRemote<T extends SyncRow>(
  store: SyncableStore<T>,
  syncedAfter = true,
): Promise<void> {
  const remote = await store.fetchRemote();
  await store.clearLocal();
  const rows = remote.map((r) => ({ ...r, synced: syncedAfter, pendingDeletion: false }));
  if (rows.length > 0) await store.bulkPutLocal(rows);
}
