import { Injector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { Food } from '@core/models/food.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { AppDb } from '@core/sync/dexie-db';
import { FoodApi } from '@core/api/food-api';
import { NutritionGoalRepository } from './nutrition-goal.repository';

function goal(uuid: string, effectiveFrom: string): LocalNutritionGoal {
  return {
    uuid,
    userId: 1,
    effectiveFrom,
    dayKind: 'ALL',
    kcal: 2200,
    proteinG: 150,
    carbsG: 220,
    fatG: 70,
    updatedAt: '2026-01-01T00:00:00.000Z',
    synced: true,
    pendingDeletion: false,
  };
}

// activeGoalFor est pur (n'utilise pas `this`) -> appel via le prototype, sans contexte d'injection.
const activeGoalFor = NutritionGoalRepository.prototype.activeGoalFor;

describe('NutritionGoalRepository.activeGoalFor — cible active §3.7', () => {
  it('retourne la cible avec le plus grand effectiveFrom <= J', () => {
    const goals = [goal('a', '2026-01-01'), goal('b', '2026-03-01'), goal('c', '2026-06-01')];
    expect(activeGoalFor(goals, '2026-04-15')?.uuid).toBe('b');
  });

  it('effectiveFrom == J est inclus (borne)', () => {
    const goals = [goal('a', '2026-01-01'), goal('b', '2026-04-15')];
    expect(activeGoalFor(goals, '2026-04-15')?.uuid).toBe('b');
  });

  it('null si toutes les cibles sont dans le futur', () => {
    expect(activeGoalFor([goal('a', '2026-07-01')], '2026-04-15')).toBeNull();
  });

  it('null si aucune cible', () => {
    expect(activeGoalFor([], '2026-04-15')).toBeNull();
  });

  it('indépendant de l\'ordre de la liste (non triée)', () => {
    const goals = [goal('c', '2026-06-01'), goal('a', '2026-01-01'), goal('b', '2026-03-01')];
    expect(activeGoalFor(goals, '2026-04-15')?.uuid).toBe('b');
  });
});

describe('AppDb v8 — tables nutrition Dexie', () => {
  it('déclare les 8 tables nutrition avec les index clés (sourceRef, date, effectiveFrom)', () => {
    const db = new AppDb(); // pas de open() : on inspecte juste le schéma déclaré
    const tables = new Map(db.tables.map((t) => [t.name, t.schema]));

    for (const name of [
      'foods',
      'food_portions',
      'recipes',
      'recipe_ingredients',
      'meals',
      'meal_presets',
      'meal_entries',
      'nutrition_goals',
    ]) {
      expect(tables.has(name), `table ${name} manquante`).toBe(true);
      expect(tables.get(name)!.primKey.name).toBe('uuid');
    }

    const idx = (table: string) => tables.get(table)!.indexes.map((i) => i.name);
    expect(idx('foods')).toContain('sourceRef'); // dédup importFromOff
    expect(idx('foods')).toContain('foodGroup'); // v10 : filtre catalogue par groupe/règne
    expect(idx('meals')).toContain('date'); // journal par jour
    expect(idx('nutrition_goals')).toContain('effectiveFrom'); // cible active
    expect(idx('meal_entries')).toContain('mealUUID');
    expect(idx('food_portions')).toContain('foodUUID');
    expect(idx('recipe_ingredients')).toContain('recipeUUID');
  });
});

describe('FoodApi — contrat wire (politique 8 : camelCase, jamais de userId)', () => {
  function buildApi(captured: { url?: string; body?: unknown }): FoodApi {
    const httpFake = {
      put: (url: string, body: unknown) => {
        captured.url = url;
        captured.body = body;
        return of(body);
      },
    } as unknown as HttpClient;
    const injector = Injector.create({
      providers: [
        { provide: HttpClient, useValue: httpFake },
        { provide: FoodApi, useClass: FoodApi },
      ],
    });
    return injector.get(FoodApi);
  }

  const food: Food = {
    uuid: 'f-1',
    userId: 42,
    name: 'Oats',
    brand: null,
    source: 'CUSTOM',
    sourceRef: null,
    foodGroup: 'CEREALE_FECULENT',
    kcalPer100g: 370,
    proteinPer100g: 13,
    carbsPer100g: 60,
    fatPer100g: 7,
    fiberPer100g: 10,
    sugarPer100g: 1,
    satFatPer100g: 1.2,
    saltPer100g: 0,
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
    isFavorite: false,
    archived: false,
    isWater: false,
    updatedAt: '2026-06-12T00:00:00.000Z',
  };

  it('upsert PUT /foods/{uuid} : body camelCase sans userId', async () => {
    const captured: { url?: string; body?: unknown } = {};
    const api = buildApi(captured);
    await new Promise((resolve) => api.upsert(food).subscribe(resolve));

    expect(captured.url).toMatch(/\/foods\/f-1$/);
    const body = captured.body as Record<string, unknown>;
    expect(body['userId']).toBeUndefined();
    expect(body['kcalPer100g']).toBe(370);
    expect(body['sourceRef']).toBeNull();
    expect(body['foodGroup']).toBe('CEREALE_FECULENT'); // catégorie traverse le wire (camelCase)
    expect(body['isFavorite']).toBe(false);
  });

  it('upsertAll PUT /foods/bulk : 1 requête, N bodies sans userId', async () => {
    const captured: { url?: string; body?: unknown } = {};
    const api = buildApi(captured);
    await new Promise((resolve) => api.upsertAll([food, { ...food, uuid: 'f-2' }]).subscribe(resolve));

    expect(captured.url).toMatch(/\/foods\/bulk$/);
    const bodies = captured.body as Record<string, unknown>[];
    expect(bodies).toHaveLength(2);
    expect(bodies.every((b) => b['userId'] === undefined)).toBe(true);
  });
});
