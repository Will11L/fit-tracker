import { Injector } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { OffProduct } from '@core/models/off-product.model';
import { LocalFood } from '@core/models/food.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { FoodRepository } from './food.repository';

/**
 * Couvre le câblage S2 « Catégories d'aliments » côté client : `importFromOff` doit dériver le
 * `foodGroup` curaté depuis `categoriesTags` (via mapOffCategoriesToGroup) et le persister sur le
 * Food créé. Le mapping pur est testé ailleurs (food-category.spec.ts) — ici on vérifie l'INTÉGRATION
 * (le bon argument est passé, le résultat atterrit bien dans la row). On reste sans Dexie réel
 * (pas de fake-indexeddb dans la stack) : on observe le résultat = la row passée à `foods.put`.
 */

function offProduct(over: Partial<OffProduct>): OffProduct {
  return {
    sourceRef: '1234567890',
    name: 'Produit OFF',
    brand: null,
    kcalPer100g: 100,
    proteinPer100g: 0,
    carbsPer100g: 0,
    fatPer100g: 0,
    fiberPer100g: null,
    sugarPer100g: null,
    satFatPer100g: null,
    saltPer100g: null,
    ironPer100g: null,
    calciumPer100g: null,
    magnesiumPer100g: null,
    zincPer100g: null,
    potassiumPer100g: null,
    sodiumPer100g: null,
    vitaminCPer100g: null,
    vitaminDPer100g: null,
    vitaminB12Per100g: null,
    vitaminAPer100g: null,
    servingSize: null,
    servingQuantityG: null,
    categoriesTags: [],
    ...over,
  };
}

/** Fake AppDb minimal : capture les rows `foods.put`, et la chaîne de dédup renvoie « pas d'existant ». */
function buildRepo(): { repo: FoodRepository; puts: LocalFood[] } {
  const puts: LocalFood[] = [];
  const foodsFake = {
    // dedup : where('sourceRef').equals(x).filter(fn).first() -> undefined (aucun Food OFF existant)
    where: () => ({ equals: () => ({ filter: () => ({ first: async () => undefined }) }) }),
    put: async (row: LocalFood) => {
      puts.push(row);
    },
  };
  const dbFake = {
    foods: foodsFake,
    food_portions: { put: async () => undefined },
  } as unknown as AppDb;
  const authFake = { currentUser: () => ({ id: 7 }) } as unknown as AuthService;
  const syncFake = { syncAll: () => Promise.resolve() } as unknown as SyncEngine;

  const injector = Injector.create({
    providers: [
      { provide: AppDb, useValue: dbFake },
      { provide: AuthService, useValue: authFake },
      { provide: SyncEngine, useValue: syncFake },
      { provide: FoodRepository, useClass: FoodRepository },
    ],
  });
  return { repo: injector.get(FoodRepository), puts };
}

describe('FoodRepository.importFromOff — dérivation du foodGroup (S2)', () => {
  it('mappe categoriesTags vers le groupe curaté et le persiste (laitage)', async () => {
    const { repo, puts } = buildRepo();
    await repo.importFromOff(offProduct({ categoriesTags: ['en:dairies', 'en:milks'] }));

    expect(puts).toHaveLength(1);
    expect(puts[0].foodGroup).toBe('LAITAGE');
    expect(puts[0].source).toBe('OFF');
    expect(puts[0].sourceRef).toBe('1234567890');
  });

  it('catégories absentes / non reconnues -> fallback AUTRE (jamais null/exception)', async () => {
    const { repo, puts } = buildRepo();
    await repo.importFromOff(offProduct({ categoriesTags: [] }));
    expect(puts[0].foodGroup).toBe('AUTRE');

    const { repo: repo2, puts: puts2 } = buildRepo();
    await repo2.importFromOff(offProduct({ categoriesTags: ['en:unknown-stuff'] }));
    expect(puts2[0].foodGroup).toBe('AUTRE');
  });

  it('complément : les macros du produit pilotent MACRO vs MICRO (le produit est passé comme macros)', async () => {
    // whey : tag dietary-supplements + macros significatives -> COMPLEMENT_MACRO.
    const { repo, puts } = buildRepo();
    await repo.importFromOff(
      offProduct({ categoriesTags: ['en:dietary-supplements'], proteinPer100g: 75, carbsPer100g: 8, fatPer100g: 6 }),
    );
    expect(puts[0].foodGroup).toBe('COMPLEMENT_MACRO');

    // multivitamine : même tag mais aucune macro -> COMPLEMENT_MICRO.
    const { repo: repo2, puts: puts2 } = buildRepo();
    await repo2.importFromOff(offProduct({ categoriesTags: ['fr:complements-alimentaires'] }));
    expect(puts2[0].foodGroup).toBe('COMPLEMENT_MICRO');
  });
});
