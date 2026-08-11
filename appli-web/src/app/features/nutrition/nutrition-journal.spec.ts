import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalMealPreset } from '@core/models/meal-preset.model';
import {
  HIGH_SUGAR_PER_100G,
  addDays,
  buildSections,
  entrySugarG,
  entryTotals,
  fiberTargetG,
  isHighSugar,
  legacyMealsToHeal,
  sugarLimitsG,
  sumMicroTotals,
  sumSugarG,
  sumTotals,
} from './journal-utils';
import { parseMacro } from './food-picker-sheet';

function entry(over: Partial<LocalMealEntry>): LocalMealEntry {
  return {
    uuid: 'e1',
    mealUUID: 'm1',
    foodUUID: 'f1',
    recipeUUID: null,
    displayName: 'Œuf',
    quantityG: 100,
    portionLabel: null,
    kcalPer100g: 150,
    proteinPer100g: 13,
    carbsPer100g: 1,
    fatPer100g: 10,
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

function preset(over: Partial<LocalMealPreset>): LocalMealPreset {
  return {
    uuid: 'p1',
    userId: 1,
    name: 'Petit-déj',
    orderIndex: 0,
    defaultTime: null,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

describe('Nutrition V4 — journal utils', () => {
  it('entryTotals dérive les totaux du snapshot per-100g (D5 : total = per100g × q / 100)', () => {
    const t = entryTotals(entry({ quantityG: 60 }));
    expect(t.kcal).toBeCloseTo(90);
    expect(t.protein).toBeCloseTo(7.8);
    expect(t.carbs).toBeCloseTo(0.6);
    expect(t.fat).toBeCloseTo(6);
  });

  it('sumTotals additionne les entries', () => {
    const t = sumTotals([entry({ quantityG: 100 }), entry({ uuid: 'e2', quantityG: 50 })]);
    expect(t.kcal).toBeCloseTo(225);
    expect(t.protein).toBeCloseTo(19.5);
  });

  it('sumMicroTotals : cumule les micros depuis les snapshots per-100g (× q/100), null = 0', () => {
    const t = sumMicroTotals([
      entry({ quantityG: 200, ironPer100g: 2, sodiumPer100g: 500, vitaminCPer100g: null }),
      entry({ uuid: 'e2', quantityG: 50, ironPer100g: 4, sodiumPer100g: null, vitaminCPer100g: 10 }),
    ]);
    expect(t.ironPer100g).toBeCloseTo(2 * 2 + 4 * 0.5); // 4 + 2 = 6
    expect(t.sodiumPer100g).toBeCloseTo(500 * 2); // 1000 (2e entry null = 0)
    expect(t.vitaminCPer100g).toBeCloseTo(10 * 0.5); // 5
    expect(t.calciumPer100g).toBe(0); // absent partout
  });

  it('sumMicroTotals : une entry plat RECIPE (micros per-100g posés, T7) est comptée comme un aliment', () => {
    // Un plat RECIPE pose désormais ses micros per-100g sur l'entry (insertRecipeEntry) → ils
    // s'agrègent au jour comme n'importe quelle entry. Avant : micros null → perdus pour les plats.
    const recipeEntry = entry({
      recipeUUID: 'r1',
      foodUUID: null,
      quantityG: 200, // facteur ×2
      ironPer100g: 5, // → 10 mg
      calciumPer100g: 110, // → 220 mg
    });
    const t = sumMicroTotals([recipeEntry]);
    expect(t.ironPer100g).toBeCloseTo(10);
    expect(t.calciumPer100g).toBeCloseTo(220);
  });

  it('fiberTargetG : 15 g pour 1000 kcal de la cible calorique, null sans cible', () => {
    expect(fiberTargetG(2000)).toBeCloseTo(30);
    expect(fiberTargetG(2500)).toBeCloseTo(37.5);
    expect(fiberTargetG(null)).toBeNull();
    expect(fiberTargetG(0)).toBeNull();
    expect(fiberTargetG(undefined)).toBeNull();
  });

  it('sumSugarG : cumule les sucres depuis les snapshots per-100g (× q/100), null = 0', () => {
    const total = sumSugarG([
      entry({ quantityG: 200, sugarPer100g: 10 }), // 20 g
      entry({ uuid: 'e2', quantityG: 50, sugarPer100g: 4 }), // 2 g
      entry({ uuid: 'e3', quantityG: 100, sugarPer100g: null }), // 0
    ]);
    expect(total).toBeCloseTo(22);
    expect(sumSugarG([])).toBe(0);
  });

  it('sugarLimitsG : plafond = 5 % des kcal cibles en g, borné à 100 g ; idéal = moitié ; fallback 2000 kcal', () => {
    expect(sugarLimitsG(2000)).toEqual({ limitG: 100, idealG: 50 });
    // Sous 2000 kcal : proportionnel (5 % du nombre de kcal).
    expect(sugarLimitsG(1600).limitG).toBeCloseTo(80);
    expect(sugarLimitsG(1600).idealG).toBeCloseTo(40);
    // Au-dessus de 2000 kcal : cap ANSES 100 g (plus de calories ≠ plus de sucre).
    expect(sugarLimitsG(2500).limitG).toBe(100);
    expect(sugarLimitsG(2500).idealG).toBe(50);
    // Sans cible kcal active : repli 2000 kcal → 100 g / 50 g (jamais null).
    expect(sugarLimitsG(null)).toEqual({ limitG: 100, idealG: 50 });
    expect(sugarLimitsG(0)).toEqual({ limitG: 100, idealG: 50 });
    expect(sugarLimitsG(undefined)).toEqual({ limitG: 100, idealG: 50 });
  });

  it('isHighSugar : aliment riche en sucres strictement au-delà de 22,5 g/100 g (repère UK), null/undefined = false', () => {
    expect(HIGH_SUGAR_PER_100G).toBe(22.5);
    expect(isHighSugar(22.5)).toBe(false); // frontière : le seuil lui-même n'alerte pas
    expect(isHighSugar(22.6)).toBe(true);
    expect(isHighSugar(45)).toBe(true);
    expect(isHighSugar(4)).toBe(false);
    expect(isHighSugar(null)).toBe(false); // sucres non renseignés → jamais d'alerte
    expect(isHighSugar(undefined)).toBe(false);
  });

  it('entrySugarG : sucres consommés à l’échelle de la quantité, null si le snapshot ne les connaît pas', () => {
    expect(entrySugarG(entry({ quantityG: 200, sugarPer100g: 10 }))).toBeCloseTo(20);
    expect(entrySugarG(entry({ quantityG: 50, sugarPer100g: 4 }))).toBeCloseTo(2);
    // ≠ sumSugarG (null = 0 dans le cumul) : ici null = « inconnu » → pas de ligne dans le dépli.
    expect(entrySugarG(entry({ quantityG: 100, sugarPer100g: null }))).toBeNull();
  });

  it('addDays gère les passages de mois en local', () => {
    expect(addDays('2026-06-12', 1)).toBe('2026-06-13');
    expect(addDays('2026-06-01', -1)).toBe('2026-05-31');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('buildSections : presets en sections (vides sans meal) + repas ad hoc appendus', () => {
    const presets = [preset({ uuid: 'p2', name: 'Déjeuner', orderIndex: 1 }), preset({})];
    const meals = [
      meal({ uuid: 'm1', name: 'Déjeuner', orderIndex: 1 }),
      meal({ uuid: 'm2', name: 'Pré-training', orderIndex: 5 }),
    ];
    const entries = [entry({ mealUUID: 'm1' })];
    const sections = buildSections(presets, meals, entries);

    expect(sections.map((s) => s.name)).toEqual(['Petit-déj', 'Déjeuner', 'Pré-training']);
    // Preset sans meal : section vide, pas de row fantôme (§3.4).
    expect(sections[0].meal).toBeNull();
    expect(sections[0].entries).toEqual([]);
    // Preset apparié par nom au meal du jour, totaux dérivés.
    expect(sections[1].meal?.uuid).toBe('m1');
    expect(sections[1].totals.kcal).toBeCloseTo(150);
    // Repas ad hoc (nom hors presets) appendu.
    expect(sections[2].meal?.uuid).toBe('m2');
  });

  it('buildSections : lien stable par presetUuid — renommer le preset fait suivre le repas (plus d’orphelin)', () => {
    // Le preset a été renommé « Déjeuner » -> « Déjeuner4 » ; le meal garde son presetUuid.
    const presets = [preset({ uuid: 'p2', name: 'Déjeuner4', orderIndex: 1 })];
    const meals = [meal({ uuid: 'm1', name: 'Déjeuner', orderIndex: 1, presetUuid: 'p2' })];
    const entries = [entry({ mealUUID: 'm1' })];
    const sections = buildSections(presets, meals, entries);

    // Une seule section : pas d'orphelin ad hoc en bas.
    expect(sections.length).toBe(1);
    // La section affiche le nom courant du preset, et le repas (avec ses entries) suit.
    expect(sections[0].name).toBe('Déjeuner4');
    expect(sections[0].meal?.uuid).toBe('m1');
    expect(sections[0].totals.kcal).toBeCloseTo(150);
  });

  it('buildSections : meal legacy (presetUuid null) toujours apparié par nom au preset', () => {
    const presets = [preset({ uuid: 'p2', name: 'Déjeuner', orderIndex: 1 })];
    const meals = [meal({ uuid: 'm1', name: 'Déjeuner', orderIndex: 1, presetUuid: null })];
    const entries = [entry({ mealUUID: 'm1' })];
    const sections = buildSections(presets, meals, entries);

    expect(sections.length).toBe(1);
    expect(sections[0].meal?.uuid).toBe('m1');
  });

  it('buildSections : presetUuid prioritaire sur le nom quand les deux pourraient matcher', () => {
    // m1 lié par uuid à p2 même si son nom diffère ; un homonyme legacy ne doit pas voler le match.
    const presets = [preset({ uuid: 'p2', name: 'Déjeuner', orderIndex: 1 })];
    const meals = [
      meal({ uuid: 'm-legacy', name: 'Déjeuner', orderIndex: 1, presetUuid: null }),
      meal({ uuid: 'm-linked', name: 'Ancien nom', orderIndex: 2, presetUuid: 'p2' }),
    ];
    const sections = buildSections(presets, [...meals], []);

    // La section preset prend le meal lié par uuid ; le legacy homonyme tombe en ad hoc.
    expect(sections[0].meal?.uuid).toBe('m-linked');
    expect(sections.some((s) => s.meal?.uuid === 'm-legacy' && s.presetUuid === null)).toBe(true);
  });

  it('legacyMealsToHeal : pose le presetUuid sur les repas legacy dont le nom matche un preset', () => {
    const presets = [preset({ uuid: 'p2', name: 'Déjeuner', orderIndex: 1 }), preset({ uuid: 'p1', name: 'Petit-déj' })];
    const meals = [
      meal({ uuid: 'm1', name: 'Déjeuner', presetUuid: null }), // legacy à réparer
      meal({ uuid: 'm2', name: 'Petit-déj', presetUuid: null }), // legacy à réparer
      meal({ uuid: 'm3', name: 'Déjeuner', presetUuid: 'p2' }), // déjà lié -> ignoré
      meal({ uuid: 'm4', name: 'Resto', presetUuid: null }), // vrai ad hoc (aucun preset) -> ignoré
    ];
    const heal = legacyMealsToHeal(presets, meals);

    expect(heal).toEqual([
      { uuid: 'm1', presetUuid: 'p2' },
      { uuid: 'm2', presetUuid: 'p1' },
    ]);
  });

  it('parseMacro accepte la virgule française et rejette le non-numérique/négatif', () => {
    expect(parseMacro('12,5')).toBe(12.5);
    expect(parseMacro('100')).toBe(100);
    expect(parseMacro('abc')).toBeNull();
    expect(parseMacro('-3')).toBeNull();
  });

  it('parseMacro : chaîne vide ou blanche → null (régression : Number("")===0)', () => {
    // Sans ce garde, une saisie vide validait comme 0 → seuils « ≥ 0 » fantômes et macros 0 silencieux.
    expect(parseMacro('')).toBeNull();
    expect(parseMacro('   ')).toBeNull();
    // Le 0 explicitement saisi reste un 0 valide (≠ champ vide).
    expect(parseMacro('0')).toBe(0);
  });
});
