import { TestBed } from '@angular/core/testing';
import { Signal, signal } from '@angular/core';
import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalFood } from '@core/models/food.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { SyncEngine } from '@core/sync/sync-engine';
import { MealRepository } from './meal.repository';
import { FoodRepository } from './food.repository';
import { NutritionGoalRepository } from './nutrition-goal.repository';
import { NutritionStatsPage } from './nutrition-stats-page';

/**
 * Câblage du toggle Cercle/Radar de la page Stats Nutrition (tâche « toggle cercle/radar sur Origine
 * + Variété »). Les builders purs (originDonutSlices/originRadarData/variety*) sont déjà couverts par
 * nutrition-category-stats.spec ; ici on verrouille le comportement OBSERVABLE de la page que ces
 * helpers n'expriment pas :
 *  - chaque section a son propre sélecteur (Origine et Variété sont indépendants), défaut = Radar ;
 *  - chaque section alimente donut ET radar avec SES données (pas de croisement origine↔variété).
 * On instancie la classe dans un contexte d'injection (repos stubés, SyncEngine stubé) SANS monter le
 * template — les graphes ECharts n'ont pas de contexte canvas en jsdom (cf. radar-chart.spec).
 */

function meal(over: Partial<LocalMeal>): LocalMeal {
  return {
    uuid: 'm1',
    userId: 1,
    date: '2026-06-12',
    name: 'Repas',
    orderIndex: 1,
    presetUuid: null,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

function entry(over: Partial<LocalMealEntry>): LocalMealEntry {
  return {
    uuid: 'e',
    mealUUID: 'm1',
    foodUUID: null,
    recipeUUID: null,
    displayName: 'Aliment',
    quantityG: 100,
    portionLabel: null,
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
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

/** Accès aux membres `protected` de la page (signaux + setters + computeds du toggle). */
interface PageProbe {
  rangeKind: { set(k: string): void };
  customStart: { set(iso: string): void };
  customEnd: { set(iso: string): void };
  categoryView: Signal<string>;
  setCategoryView(v: string): void;
  originDonut: Signal<{ value: number }[]>;
  originRadar: Signal<{ series: { values: number[] }[] }>;
  varietyDonut: Signal<{ value: number }[]>;
  varietyRadar: Signal<{ series: { values: number[] }[] }>;
  profileRadar: Signal<{ series: { name: string; values: number[] }[] }>;
}

function makePage(opts: {
  meals?: LocalMeal[];
  entries?: LocalMealEntry[];
  foods?: { uuid: string; foodGroup: string | null }[];
  goals?: LocalNutritionGoal[];
} = {}): { page: NutritionStatsPage; probe: PageProbe } {
  TestBed.configureTestingModule({
    providers: [
      { provide: SyncEngine, useValue: { syncAll: () => Promise.resolve() } },
      {
        provide: MealRepository,
        useValue: {
          meals: signal<LocalMeal[]>(opts.meals ?? []),
          entries: signal<LocalMealEntry[]>(opts.entries ?? []),
        },
      },
      {
        provide: FoodRepository,
        useValue: { foods: signal<LocalFood[]>((opts.foods ?? []) as unknown as LocalFood[]) },
      },
      {
        provide: NutritionGoalRepository,
        useValue: { goals: signal<LocalNutritionGoal[]>(opts.goals ?? []) },
      },
    ],
  });
  const page = TestBed.runInInjectionContext(() => new NutritionStatsPage());
  return { page, probe: page as unknown as PageProbe };
}

describe('NutritionStatsPage — toggle Cercle/Radar (câblage de la page)', () => {
  it('le sélecteur partagé démarre sur Radar (RADAR) par défaut (app-wide : radar = vue ouverte)', () => {
    const { probe } = makePage();
    expect(probe.categoryView()).toBe('RADAR');
  });

  it('un seul sélecteur partagé pilote Origine + Variété ensemble (Cercle ↔ Radar)', () => {
    const { probe } = makePage();
    probe.setCategoryView('CIRCLE');
    expect(probe.categoryView()).toBe('CIRCLE');

    probe.setCategoryView('RADAR');
    expect(probe.categoryView()).toBe('RADAR');
  });

  it('Origine alimente donut ET radar avec les MÊMES kcal (la bascule ne change que la forme)', () => {
    const { probe } = makePage({
      meals: [meal({ uuid: 'm1', date: '2026-06-12' })],
      // 200 kcal animale (poulet) + 100 kcal végétale (riz).
      entries: [
        entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 200 }),
        entry({ uuid: 'e2', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 100 }),
      ],
      foods: [
        { uuid: 'f-poulet', foodGroup: 'VIANDE_BLANCHE' },
        { uuid: 'f-riz', foodGroup: 'CEREALE_FECULENT' },
      ],
    });
    probe.rangeKind.set('CUSTOM');
    probe.customStart.set('2026-06-01');
    probe.customEnd.set('2026-06-30');

    const donutValues = probe.originDonut().map((d) => d.value);
    const radarValues = probe.originRadar().series[0].values;
    expect(donutValues).toEqual([200, 100, 0, 0]); // ANIMALE, VEGETALE, COMPLEMENT, AUTRE
    expect(radarValues).toEqual(donutValues);
  });

  it('Variété est câblée sur SES propres groupes (pas les règnes d’Origine) dans donut et radar', () => {
    const { probe } = makePage({
      meals: [meal({ uuid: 'm1', date: '2026-06-12' })],
      // 200 kcal viande blanche + 100 kcal féculent → 2 groupes distincts.
      entries: [
        entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 200 }),
        entry({ uuid: 'e2', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 100 }),
      ],
      foods: [
        { uuid: 'f-poulet', foodGroup: 'VIANDE_BLANCHE' },
        { uuid: 'f-riz', foodGroup: 'CEREALE_FECULENT' },
      ],
    });
    probe.rangeKind.set('CUSTOM');
    probe.customStart.set('2026-06-01');
    probe.customEnd.set('2026-06-30');

    const donutValues = probe.varietyDonut().map((d) => d.value);
    const radarValues = probe.varietyRadar().series[0].values;
    // Un axe/part par GROUPE consommé, kcal décroissant — et NON 4 règnes (preuve du non-croisement).
    expect(donutValues).toEqual([200, 100]);
    expect(radarValues).toEqual(donutValues);
  });
});

// Câblage du radar « profil macros » de la page Stats (tâche « 2e tracé comparatif réel vs cible » —
// volet Stats : la légende du radar consommé vs cible est clarifiée en « Consommé » / « Cible »).
// Le helper macroRadarData (libellés personnalisables) est couvert par nutrition-summary-panel.spec ;
// on verrouille ici que la PAGE l'alimente bien avec ces libellés-là quand une cible est active.
describe('NutritionStatsPage — radar profil macros (légende « Consommé » / « Cible »)', () => {
  const GOAL: LocalNutritionGoal = {
    uuid: 'g1',
    userId: 1,
    effectiveFrom: '2026-01-01',
    dayKind: 'ALL',
    kcal: 2000,
    proteinG: 150,
    carbsG: 200,
    fatG: 70,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
  };

  it('cible active sur la période → 2 séries étiquetées « Consommé » puis « Cible »', () => {
    const { probe } = makePage({
      meals: [meal({ uuid: 'm1', date: '2026-06-12' })],
      entries: [entry({ uuid: 'e1', foodUUID: 'f1', kcalPer100g: 100, quantityG: 100 })],
      goals: [GOAL],
    });
    probe.rangeKind.set('CUSTOM');
    probe.customStart.set('2026-06-01');
    probe.customEnd.set('2026-06-30');

    const series = probe.profileRadar().series;
    expect(series.length).toBe(2);
    // Volet Stats de la tâche : libellés explicites (≠ défaut « Profil » / « Objectif »).
    expect(series.map((s) => s.name)).toEqual(['Consommé', 'Cible']);
  });

  it('aucune cible ni consommation → radar vide (placeholder du composant, pas de tracé fantôme)', () => {
    const { probe } = makePage(); // ni repas ni objectif
    probe.rangeKind.set('CUSTOM');
    probe.customStart.set('2026-06-01');
    probe.customEnd.set('2026-06-30');
    expect(probe.profileRadar().series.length).toBe(0);
  });
});
