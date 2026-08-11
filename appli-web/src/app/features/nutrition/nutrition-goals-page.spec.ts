import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '@core/auth/auth.service';
import { CurrentUser } from '@core/models/user.model';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { SyncEngine } from '@core/sync/sync-engine';
import { NutritionGoalRepository } from './nutrition-goal.repository';
import { MealRepository } from './meal.repository';
import { NutritionGoalsPage } from './nutrition-goals-page';
import { deriveGoalFromMacros } from './goal-macros';
import { todayIso } from './journal-utils';
import { MACRO_COLOR, MACRO_LABEL } from './macro-colors';

// Page Objectifs — composition des 3 colonnes d'analyse (donut | % des kcal | g/kg de poids).
// Les helpers PURS (macroKcalBreakdown / macroPerKg / fiberDensity) sont couverts par
// goal-analysis.spec ; on couvre ici ce qui n'existe QUE dans la page : le câblage des computed
// qui alimentent les colonnes — en particulier la colonne g/kg qui couvre désormais P/G/L (et plus
// seulement les protéines) et son repli « — » quand le poids du profil manque, sans casser la grille.
//
// echarts (donut + radar) crashe en jsdom (cf. donut-chart.spec) : on N'APPELLE PAS detectChanges,
// on lit directement les signaux dérivés sur l'instance (même approche que donut-chart.spec).

// Cible de référence P180 / G250 / L80 → kcal dérivée (macro-first, D12).
const GOAL: LocalNutritionGoal = {
  uuid: 'g1',
  userId: 1,
  effectiveFrom: '2020-01-01',
  dayKind: 'ALL',
  kcal: deriveGoalFromMacros({ proteinG: 180, carbsG: 250, fatG: 80 }).kcal,
  proteinG: 180,
  carbsG: 250,
  fatG: 80,
  updatedAt: null,
  synced: true,
  pendingDeletion: false,
};

function user(weightKg: number | null): CurrentUser {
  return {
    id: 1,
    username: 'will',
    isAdmin: false,
    firstName: null,
    lastName: null,
    email: 'w@x.y',
    birthDate: null,
    sex: null,
    heightCm: null,
    weightKg,
  };
}

function meal(over: Partial<LocalMeal>): LocalMeal {
  return {
    uuid: 'm1',
    userId: 1,
    date: '2026-06-12',
    name: 'Déjeuner',
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
    uuid: 'e1',
    mealUUID: 'm1',
    foodUUID: 'f1',
    recipeUUID: null,
    displayName: 'Aliment',
    quantityG: 100,
    portionLabel: null,
    kcalPer100g: 0,
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

interface Analysis {
  bodyweightTiles(): { key: string; num: string; unit: string }[];
  hasWeight(): boolean;
  donutSlices(): { label: string; value: number; color: string }[];
  comparisonRadar(): {
    axes: { label: string; max?: number; color?: string }[];
    series: { name: string; values: number[]; color: string; area?: boolean }[];
  };
}

/**
 * Monte la page sans rendre le template (pas de detectChanges → pas d'echarts) et renvoie
 * l'instance castée sur les computed d'analyse. `weightKg` null = poids du profil absent.
 */
function setup(
  weightKg: number | null,
  goals: LocalNutritionGoal[] = [GOAL],
  meals: LocalMeal[] = [],
  entries: LocalMealEntry[] = [],
): Analysis {
  const goalRepo = {
    goals: signal<LocalNutritionGoal[]>(goals),
    // Sélection fidèle (§3.7) : plus grand effectiveFrom <= date. Un seul objectif ici.
    activeGoalFor: (gs: LocalNutritionGoal[], date: string): LocalNutritionGoal | null => {
      let active: LocalNutritionGoal | null = null;
      for (const g of gs) {
        if (g.effectiveFrom <= date && (!active || g.effectiveFrom > active.effectiveFrom)) active = g;
      }
      return active;
    },
  };
  const mealRepo = { entries: signal(entries), meals: signal(meals) };
  const auth = { currentUser: signal<CurrentUser | null>(user(weightKg)) };
  const sync = { syncAll: () => Promise.resolve() };

  TestBed.configureTestingModule({
    imports: [NutritionGoalsPage],
    providers: [
      { provide: NutritionGoalRepository, useValue: goalRepo },
      { provide: MealRepository, useValue: mealRepo },
      { provide: AuthService, useValue: auth },
      { provide: SyncEngine, useValue: sync },
    ],
  });
  return TestBed.createComponent(NutritionGoalsPage).componentInstance as unknown as Analysis;
}

describe('NutritionGoalsPage — colonne g/kg (P/G/L) + câblage du donut', () => {
  it('tuiles g/kg : ordre grille Glucides/Lipides/Protéines (g/kg) puis Fibres, valeurs rapportées au poids', () => {
    const rows = setup(80).bodyweightTiles();
    // Ordre de la grille 2×2 (G haut-G, L haut-D, P bas-G) + densité fibres en 4ᵉ tuile.
    expect(rows.map((r) => r.key)).toEqual(['carbs', 'fat', 'protein', 'fiber']);
    const perKg = rows.slice(0, 3);
    expect(perKg.map((r) => r.num)).toEqual(['3.1', '1', '2.3']); // 250/80, 80/80, 180/80
    expect(perKg.every((r) => r.unit === 'g/kg')).toBe(true);
  });

  it('poids du profil absent : les 3 tuiles P/G/L restent (grille intacte) en « — », hasWeight = false', () => {
    const a = setup(null);
    const perKg = a.bodyweightTiles().slice(0, 3);
    expect(perKg.map((r) => r.key)).toEqual(['carbs', 'fat', 'protein']); // grille non cassée
    expect(perKg.every((r) => r.num === '—')).toBe(true);
    expect(a.hasWeight()).toBe(false); // pilote l'indice « renseigne ton poids »
  });

  it('poids = 0 (renseigné mais nul) traité comme absent → « — » + hasWeight false', () => {
    const a = setup(0);
    expect(a.bodyweightTiles().slice(0, 3).every((r) => r.num === '—')).toBe(true);
    expect(a.hasWeight()).toBe(false);
  });

  it('donut : 4 parts (carbs/fat/protein/fiber) aux couleurs macro, kcal arrondies > 0', () => {
    const slices = setup(80).donutSlices();
    expect(slices.map((s) => s.label)).toEqual([
      MACRO_LABEL.carbs,
      MACRO_LABEL.fat,
      MACRO_LABEL.protein,
      MACRO_LABEL.fiber,
    ]);
    expect(slices.map((s) => s.color)).toEqual([
      MACRO_COLOR.carbs,
      MACRO_COLOR.fat,
      MACRO_COLOR.protein,
      MACRO_COLOR.fiber,
    ]);
    expect(slices.find((s) => s.label === MACRO_LABEL.carbs)!.value).toBe(1000); // 4 × 250
    expect(slices.every((s) => Number.isInteger(s.value) && s.value > 0)).toBe(true);
  });
});

// Câblage du radar comparatif « cible vs réel 7 j » sur la page Objectifs (tâche « 2e tracé
// comparatif réel vs cible ») : le helper pur macroRadarData est déjà couvert
// (nutrition-summary-panel.spec) — on verrouille ici que la page l'alimente bien avec le réel moyen
// des 7 derniers jours (weekAvg) vs la cible active, en 2 tracés superposés à légende claire.
describe('NutritionGoalsPage — radar comparatif cible vs réel 7 j', () => {
  it('aucune consommation 7 j → 2 séries (« Réel (7 j) » à 0 % + « Cible » à 100 %), axes max 120 colorés', () => {
    const r = setup(80).comparisonRadar();
    expect(r.series.length).toBe(2);
    // Série réelle remplie (en accent kcal) + repère cible à 100 % → 2 tracés distincts.
    expect(r.series[0].name).toBe('Réel (7 j)');
    expect(r.series[0].area).toBe(true);
    expect(r.series[0].color).toBe('var(--macro-kcal)');
    expect(r.series[0].values).toEqual([0, 0, 0, 0]); // pas de repas sur 7 j → réel nul
    expect(r.series[1].name).toBe('Cible');
    expect(r.series[1].values).toEqual([100, 100, 100, 100]); // repère cible
    // Mode %-vs-cible : axes plafonnés à 120, colorés par macro (ordre carbs/fat/protein/fiber).
    expect(r.axes.every((a) => a.max === 120)).toBe(true);
    expect(r.axes.map((a) => a.label)).toEqual([
      MACRO_LABEL.carbs,
      MACRO_LABEL.fat,
      MACRO_LABEL.protein,
      MACRO_LABEL.fiber,
    ]);
    expect(r.axes.map((a) => a.color)).toEqual([
      MACRO_COLOR.carbs,
      MACRO_COLOR.fat,
      MACRO_COLOR.protein,
      MACRO_COLOR.fiber,
    ]);
  });

  it('réel moyen 7 j = moitié de la cible → série « Réel (7 j) » à 50 % (carbs/fat/protein)', () => {
    // Un repas du jour (dans la fenêtre 7 j), 700 g : totaux = per100g × 7 → moyenne /7 jours = per100g.
    const meals = [meal({ uuid: 'm1', date: todayIso() })];
    const entries = [
      entry({ mealUUID: 'm1', quantityG: 700, carbsPer100g: 125, fatPer100g: 40, proteinPer100g: 90 }),
    ];
    const r = setup(80, [GOAL], meals, entries).comparisonRadar();
    // Cible : carbs 250 / fat 80 / protein 180 → réel moyen 125 / 40 / 90 = 50 % chacun.
    const [carbs, fat, protein] = r.series[0].values;
    expect(carbs).toBe(50);
    expect(fat).toBe(50);
    expect(protein).toBe(50);
    expect(r.series[1].values).toEqual([100, 100, 100, 100]); // cible inchangée
  });
});

// Action « Redéfinir comme objectif » (options d'une ligne d'historique) : reprend les macros d'une
// cible passée dans une NOUVELLE cible effective aujourd'hui (via goalRepo.create) → elle redevient la
// cible active. On vérifie le câblage (mapping des champs + date du jour) sans rendre le template (pas
// de detectChanges → pas d'echarts), en lisant l'appel capturé sur le repo mocké.
describe('NutritionGoalsPage — Redéfinir comme objectif', () => {
  it("crée une nouvelle cible effective aujourd'hui avec les macros de la cible sélectionnée", () => {
    const past: LocalNutritionGoal = {
      ...GOAL,
      uuid: 'old',
      effectiveFrom: '2020-01-01',
      kcal: 1740,
      proteinG: 200,
      carbsG: 100,
      fatG: 60,
    };
    let created: unknown = null;
    const goalRepo = {
      goals: signal<LocalNutritionGoal[]>([past]),
      activeGoalFor: () => past,
      create: (input: unknown) => {
        created = input;
        return Promise.resolve('new-uuid');
      },
    };

    TestBed.configureTestingModule({
      imports: [NutritionGoalsPage],
      providers: [
        { provide: NutritionGoalRepository, useValue: goalRepo },
        { provide: MealRepository, useValue: { entries: signal([]), meals: signal([]) } },
        { provide: AuthService, useValue: { currentUser: signal<CurrentUser | null>(user(80)) } },
        { provide: SyncEngine, useValue: { syncAll: () => Promise.resolve() } },
      ],
    });

    const page = TestBed.createComponent(NutritionGoalsPage).componentInstance as unknown as {
      redefineAsGoal(g: LocalNutritionGoal): void;
    };
    page.redefineAsGoal(past);

    expect(created).toEqual({
      effectiveFrom: todayIso(),
      kcal: past.kcal,
      proteinG: past.proteinG,
      carbsG: past.carbsG,
      fatG: past.fatG,
    });
  });
});
