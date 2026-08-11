import { Injectable, inject } from '@angular/core';
import { Observable, combineLatest, of } from 'rxjs';
import { SYNCABLE_STORES, SyncRow, SyncStats, SyncableStore } from './syncable-store';
import { mergeFromRemote, pushAllRows, pushStore, replaceFromRemote } from './sync-merge';

/**
 * Orchestrateur de sync (miroir SyncEngine + mutex de SyncManager).
 * Itère sur le registre `SYNCABLE_STORES` ; ajouter une entité = 0 changement ici.
 */
@Injectable({ providedIn: 'root' })
export class SyncEngine {
  private readonly stores = inject(SYNCABLE_STORES);
  /** File d'exécution : les opérations de sync s'enchaînent (jamais jetées ni concurrentes). */
  private chain: Promise<void> = Promise.resolve();
  private syncAllQueued = false;

  /** Compteurs live de toutes les entités enregistrées (pour la page Sync). */
  allStats(): Observable<SyncStats[]> {
    if (this.stores.length === 0) return of([]);
    return combineLatest(this.stores.map((s) => s.liveStats()));
  }

  /**
   * Push (créations/màj non-syncées + suppressions), puis pull/merge (convergence + prune).
   * Un appel pendant un sync en cours est mis en file (le sync en cours a pu pousser AVANT
   * l'écriture locale qui motive l'appel) ; plusieurs appels en attente sont coalescés en un.
   * Miroir du Mutex SyncManager Android (qui attend au lieu de jeter).
   */
  syncAll(): Promise<void> {
    if (this.syncAllQueued) return this.chain; // déjà un syncAll en attente — coalesce
    this.syncAllQueued = true;
    return this.enqueue(async () => {
      this.syncAllQueued = false;
      await this.forEach((s) => pushStore(s));
      await this.forEachParallel((s) => mergeFromRemote(s));
    });
  }

  /** Push de TOUTES les rows (outil "Upsert"). */
  bulkPushAll(): Promise<void> {
    return this.enqueue(() => this.forEach((s) => pushAllRows(s)));
  }

  /** Pull + merge + prune (outil "Fusionner"). */
  pullMerge(): Promise<void> {
    return this.enqueue(() => this.forEachParallel((s) => mergeFromRemote(s)));
  }

  /** Vide le local puis ré-insère le serveur (outil "Remplacer"). */
  pullReplace(): Promise<void> {
    return this.enqueue(() => this.forEachParallel((s) => replaceFromRemote(s, true)));
  }

  /**
   * Pull-then-replace en `synced=false` (outil "Tout récupérer" = bouton "Get All" Android,
   * miroir RemoteDataGetter.getAllAsUnsynced) : force un re-push complet au prochain sync —
   * sert à tester le round-trip pull -> push. Ne pas utiliser hors outillage dev.
   */
  getAllAsUnsynced(): Promise<void> {
    return this.enqueue(() => this.forEachParallel((s) => replaceFromRemote(s, false)));
  }

  /** Vide toutes les tables locales (outil "Vider"). Local-only, instantané. */
  clearAll(): Promise<void> {
    return this.forEach((s) => s.clearLocal());
  }

  /** Séquentiel — requis pour les pushes (l'ordre FK du registre compte pour les deletes). */
  private async forEach(fn: (s: SyncableStore<SyncRow>) => Promise<void>): Promise<void> {
    for (const s of this.stores) await fn(s);
  }

  /**
   * Parallèle — pour les pulls (GETs indépendants entre entités, écritures locales par store).
   * 23 GETs séquentiels à ~200ms (Tailscale) = ~5s ; en parallèle le navigateur en pipeline 6+.
   */
  private async forEachParallel(fn: (s: SyncableStore<SyncRow>) => Promise<void>): Promise<void> {
    const results = await Promise.allSettled(this.stores.map((s) => fn(s)));
    const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
    if (failed) throw failed.reason; // même contrat qu'avant : le sync signale l'échec
  }

  /** Sérialise les opérations : chacune attend la fin de la précédente, aucune n'est perdue. */
  private enqueue(fn: () => Promise<void>): Promise<void> {
    const next = this.chain.then(fn);
    this.chain = next.catch(() => undefined); // une op en échec ne casse pas la file
    return next;
  }
}
