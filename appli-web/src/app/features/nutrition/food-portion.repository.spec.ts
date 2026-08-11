import { Injector } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { FoodRepository } from './food.repository';

/**
 * Couvre `FoodRepository.updatePortion` (ajout de l'édition de portion côté catalogue/détail).
 * Pas de Dexie réel dans la stack (cf. food-import.spec.ts) : on stubbe AppDb et on observe l'appel
 * `food_portions.update` — la portion est repassée `synced=false` (re-poussée à la sync) avec le
 * nouveau libellé + grammage et un `updatedAt` rafraîchi, sans toucher `pendingDeletion`.
 */
function buildRepo(): {
  repo: FoodRepository;
  updates: { uuid: string; patch: Record<string, unknown> }[];
  synced: number[];
} {
  const updates: { uuid: string; patch: Record<string, unknown> }[] = [];
  const synced: number[] = [];
  const dbFake = {
    food_portions: {
      update: async (uuid: string, patch: Record<string, unknown>) => {
        updates.push({ uuid, patch });
      },
    },
  } as unknown as AppDb;
  const authFake = { currentUser: () => ({ id: 7 }) } as unknown as AuthService;
  const syncFake = {
    syncAll: () => {
      synced.push(1);
      return Promise.resolve();
    },
  } as unknown as SyncEngine;

  const injector = Injector.create({
    providers: [
      { provide: AppDb, useValue: dbFake },
      { provide: AuthService, useValue: authFake },
      { provide: SyncEngine, useValue: syncFake },
      { provide: FoodRepository, useClass: FoodRepository },
    ],
  });
  return { repo: injector.get(FoodRepository), updates, synced };
}

describe('FoodRepository.updatePortion', () => {
  it('met à jour label + grams, repasse synced=false et déclenche la sync', async () => {
    const { repo, updates, synced } = buildRepo();
    await repo.updatePortion('p1', { label: '2 œufs', grams: 120 });

    expect(updates).toHaveLength(1);
    expect(updates[0].uuid).toBe('p1');
    expect(updates[0].patch['label']).toBe('2 œufs');
    expect(updates[0].patch['grams']).toBe(120);
    expect(updates[0].patch['synced']).toBe(false);
    expect(typeof updates[0].patch['updatedAt']).toBe('string');
    // Édition ≠ suppression : on ne touche pas pendingDeletion.
    expect('pendingDeletion' in updates[0].patch).toBe(false);
    expect(synced).toHaveLength(1);
  });
});
