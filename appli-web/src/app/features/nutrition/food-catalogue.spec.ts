import { LocalFood } from '@core/models/food.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import {
  buildFoodGroups,
  recentFoodUuids,
  foodNutrientValue,
  passesThresholds,
  passesCategory,
  FILTERABLE_MACROS,
  FILTERABLE_MICROS,
  type NutrientThreshold,
} from './food-catalogue';
import { splitRecipesByKind } from './recipe-utils';

function food(over: Partial<LocalFood>): LocalFood {
  return {
    uuid: 'f1',
    userId: 1,
    name: 'Avoine',
    brand: null,
    source: 'CUSTOM',
    sourceRef: null,
    foodGroup: null,
    kcalPer100g: 380,
    proteinPer100g: 13,
    carbsPer100g: 60,
    fatPer100g: 7,
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
    isFavorite: false,
    archived: false,
    isWater: false,
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
    displayName: 'X',
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

describe('Catalogue d’aliments — recentFoodUuids', () => {
  it('déduplique et trie par updatedAt décroissant, borné à la limite', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f1', updatedAt: '2026-06-01T10:00:00Z' }),
      entry({ uuid: 'e2', foodUUID: 'f2', updatedAt: '2026-06-03T10:00:00Z' }),
      entry({ uuid: 'e3', foodUUID: 'f1', updatedAt: '2026-06-05T10:00:00Z' }), // f1 plus récent
      entry({ uuid: 'e4', foodUUID: 'f3', updatedAt: '2026-06-02T10:00:00Z' }),
    ];
    expect(recentFoodUuids(entries)).toEqual(['f1', 'f2', 'f3']);
    expect(recentFoodUuids(entries, 2)).toEqual(['f1', 'f2']);
  });

  it('ignore les entries sans foodUUID (recettes journalisées)', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: null, recipeUUID: 'r1', updatedAt: '2026-06-05T10:00:00Z' }),
      entry({ uuid: 'e2', foodUUID: 'f2', updatedAt: '2026-06-01T10:00:00Z' }),
    ];
    expect(recentFoodUuids(entries)).toEqual(['f2']);
  });
});

describe('Catalogue d’aliments — buildFoodGroups', () => {
  const foods = [
    food({ uuid: 'f1', name: 'Avoine', isFavorite: true }),
    food({ uuid: 'f2', name: 'Banane' }),
    food({ uuid: 'f3', name: 'Poulet' }),
    food({ uuid: 'f4', name: 'Vieux yaourt', archived: true }),
  ];

  it('sans recherche : Favoris puis Récents puis Tous, archivés masqués par défaut', () => {
    const groups = buildFoodGroups(foods, ['f2'], '');
    expect(groups.map((g) => g.title)).toEqual(['Favoris', 'Récents', 'Tous']);
    expect(groups[0].foods.map((f) => f.uuid)).toEqual(['f1']); // favori
    expect(groups[1].foods.map((f) => f.uuid)).toEqual(['f2']); // récent (non favori)
    expect(groups[2].foods.map((f) => f.uuid)).toEqual(['f3']); // reste (archivé f4 exclu)
  });

  it('un favori récemment consommé reste dans Favoris (pas dupliqué dans Récents)', () => {
    const groups = buildFoodGroups(foods, ['f1', 'f2'], '');
    expect(groups[0].foods.map((f) => f.uuid)).toEqual(['f1']);
    expect(groups[1].foods.map((f) => f.uuid)).toEqual(['f2']);
  });

  it('showArchived : bloc « Archivés » en fin de liste', () => {
    const groups = buildFoodGroups(foods, [], '', { showArchived: true });
    expect(groups.map((g) => g.title)).toEqual(['Favoris', 'Tous', 'Archivés']);
    expect(groups.at(-1)!.foods.map((f) => f.uuid)).toEqual(['f4']);
  });

  it('recherche : liste à plat (nom ou marque), titre vide, archivés exclus sauf showArchived', () => {
    expect(buildFoodGroups(foods, [], 'ban').map((g) => g.foods.map((f) => f.uuid))).toEqual([['f2']]);
    // archivé exclu par défaut même en recherche
    expect(buildFoodGroups(foods, [], 'yaourt')).toEqual([]);
    // showArchived le ramène
    expect(buildFoodGroups(foods, [], 'yaourt', { showArchived: true })[0].foods.map((f) => f.uuid)).toEqual(['f4']);
    // recherche par marque
    const withBrand = [food({ uuid: 'f5', name: 'Skyr', brand: 'Danone' })];
    expect(buildFoodGroups(withBrand, [], 'dano')[0].foods.map((f) => f.uuid)).toEqual(['f5']);
  });

  it('recherche sans résultat : aucun groupe', () => {
    expect(buildFoodGroups(foods, [], 'zzz')).toEqual([]);
  });

  it('Récents : préserve l’ordre most-recent-first et ignore les uuids archivés/inconnus', () => {
    // f4 est archivé, 'f9' n'existe pas → tous deux ignorés ; ordre f3 puis f2 conservé.
    const groups = buildFoodGroups(foods, ['f3', 'f4', 'f9', 'f2'], '');
    const recents = groups.find((g) => g.title === 'Récents')!;
    expect(recents.foods.map((f) => f.uuid)).toEqual(['f3', 'f2']);
    // le reste (« Tous ») ne contient plus les récents déjà placés
    const tous = groups.find((g) => g.title === 'Tous');
    expect(tous).toBeUndefined();
  });
});

describe('Catalogue d’aliments — recherche multi-critères (seuils)', () => {
  it('foodNutrientValue : kcal effective (CUSTOM saisie vs CIQUAL dérivée), micro null → 0', () => {
    // CUSTOM avec kcal saisie (380) → kcal effective = valeur saisie.
    expect(foodNutrientValue(food({ source: 'CUSTOM', kcalPer100g: 380 }), 'kcalPer100g')).toBe(380);
    // CIQUAL → kcal dérivée des macros : 4·13 + 4·60 + 9·7 + 2·0 = 355.
    expect(foodNutrientValue(food({ source: 'CIQUAL' }), 'kcalPer100g')).toBe(355);
    // Macro toujours présente.
    expect(foodNutrientValue(food({ proteinPer100g: 13 }), 'proteinPer100g')).toBe(13);
    // Micro absent (null) compté comme 0.
    expect(foodNutrientValue(food({ ironPer100g: null }), 'ironPer100g')).toBe(0);
    expect(foodNutrientValue(food({ ironPer100g: 4.2 }), 'ironPer100g')).toBe(4.2);
  });

  it('passesThresholds : liste vide → vrai, ≥/≤, combinés en ET', () => {
    const f = food({ proteinPer100g: 13, carbsPer100g: 60, fatPer100g: 7 });
    expect(passesThresholds(f, [])).toBe(true);
    expect(passesThresholds(f, [{ key: 'proteinPer100g', op: 'gte', value: 10 }])).toBe(true);
    expect(passesThresholds(f, [{ key: 'proteinPer100g', op: 'gte', value: 15 }])).toBe(false);
    expect(passesThresholds(f, [{ key: 'carbsPer100g', op: 'lte', value: 70 }])).toBe(true);
    expect(passesThresholds(f, [{ key: 'carbsPer100g', op: 'lte', value: 50 }])).toBe(false);
    // ET : protéines ≥ 10 (ok) ET lipides ≤ 5 (7 > 5 → ko) → faux.
    const thresholds: NutrientThreshold[] = [
      { key: 'proteinPer100g', op: 'gte', value: 10 },
      { key: 'fatPer100g', op: 'lte', value: 5 },
    ];
    expect(passesThresholds(f, thresholds)).toBe(false);
  });

  const richProtein = food({ uuid: 'p1', name: 'Poulet', source: 'CIQUAL', proteinPer100g: 27, carbsPer100g: 0, fatPer100g: 3 });
  const richCarb = food({ uuid: 'c1', name: 'Riz', source: 'CIQUAL', proteinPer100g: 7, carbsPer100g: 78, fatPer100g: 1 });

  it('seuils seuls (sans texte) → liste à plat des aliments qui passent', () => {
    const groups = buildFoodGroups([richProtein, richCarb], [], '', {
      thresholds: [{ key: 'proteinPer100g', op: 'gte', value: 20 }],
    });
    expect(groups.map((g) => g.title)).toEqual(['']);
    expect(groups[0].foods.map((f) => f.uuid)).toEqual(['p1']);
  });

  it('seuils ET recherche texte cumulés', () => {
    // texte « ou » (Poulet) + protéines ≥ 20 → Poulet seul.
    const groups = buildFoodGroups([richProtein, richCarb], [], 'ou', {
      thresholds: [{ key: 'proteinPer100g', op: 'gte', value: 20 }],
    });
    expect(groups[0].foods.map((f) => f.uuid)).toEqual(['p1']);
  });

  it('seuils combinés en ET ; aucun résultat → aucun groupe', () => {
    const groups = buildFoodGroups([richProtein, richCarb], [], '', {
      thresholds: [
        { key: 'proteinPer100g', op: 'gte', value: 20 },
        { key: 'carbsPer100g', op: 'gte', value: 50 },
      ],
    });
    expect(groups).toEqual([]); // p1 : 0 g de glucides ; c1 : 7 g de protéines → personne ne passe les deux.
  });
});

describe('Catalogue d’aliments — facette catégorie (règne / groupe)', () => {
  const poulet = food({ uuid: 'p1', name: 'Poulet', foodGroup: 'VIANDE_BLANCHE' });
  const boeuf = food({ uuid: 'b1', name: 'Bœuf', foodGroup: 'VIANDE_ROUGE' });
  const lentilles = food({ uuid: 'l1', name: 'Lentilles', foodGroup: 'LEGUMINEUSE' });
  const whey = food({ uuid: 'w1', name: 'Whey', foodGroup: 'COMPLEMENT_MACRO' });
  const legacy = food({ uuid: 'x1', name: 'Sans groupe', foodGroup: null });

  it('passesCategory : règne dérivé du groupe (null/vide → vrai)', () => {
    expect(passesCategory(poulet, null, null)).toBe(true); // aucune facette → vrai
    expect(passesCategory(poulet, 'ANIMALE', null)).toBe(true);
    expect(passesCategory(lentilles, 'ANIMALE', null)).toBe(false);
    expect(passesCategory(lentilles, 'VEGETALE', null)).toBe(true);
    expect(passesCategory(legacy, 'ANIMALE', null)).toBe(false); // null → règne AUTRE
  });

  it('passesCategory : appartenance à un ensemble de codes de groupe (Compléments = 2 codes)', () => {
    expect(passesCategory(poulet, null, ['VIANDE_BLANCHE'])).toBe(true);
    expect(passesCategory(poulet, null, ['VIANDE_ROUGE'])).toBe(false);
    expect(passesCategory(whey, null, ['COMPLEMENT_MACRO', 'COMPLEMENT_MICRO'])).toBe(true);
    expect(passesCategory(legacy, null, ['VIANDE_BLANCHE'])).toBe(false);
  });

  it('passesCategory : règne ET groupe combinés (ET)', () => {
    // règne animal OK mais code groupe non listé → faux (les deux doivent passer).
    expect(passesCategory(poulet, 'ANIMALE', ['VIANDE_ROUGE'])).toBe(false);
    expect(passesCategory(poulet, 'ANIMALE', ['VIANDE_BLANCHE'])).toBe(true);
  });

  it('buildFoodGroups : facette règne seule → liste à plat des aliments du règne', () => {
    const groups = buildFoodGroups([poulet, boeuf, lentilles, whey], [], '', { realm: 'ANIMALE' });
    expect(groups.map((g) => g.title)).toEqual(['']);
    expect(groups[0].foods.map((f) => f.uuid).sort()).toEqual(['b1', 'p1']);
  });

  it('buildFoodGroups : facette groupe seule (Compléments = 2 codes)', () => {
    const groups = buildFoodGroups([poulet, whey], [], '', {
      groupCodes: ['COMPLEMENT_MACRO', 'COMPLEMENT_MICRO'],
    });
    expect(groups[0].foods.map((f) => f.uuid)).toEqual(['w1']);
  });

  it('buildFoodGroups : facette catégorie cumulée avec recherche texte + seuil (ET)', () => {
    const richBlanche = food({ uuid: 'p2', name: 'Poulet rôti', foodGroup: 'VIANDE_BLANCHE', source: 'CUSTOM', proteinPer100g: 27 });
    const maigreBlanche = food({ uuid: 'p3', name: 'Dinde', foodGroup: 'VIANDE_BLANCHE', source: 'CUSTOM', proteinPer100g: 10 });
    const groups = buildFoodGroups([richBlanche, maigreBlanche, boeuf], [], 'poulet', {
      realm: 'ANIMALE',
      thresholds: [{ key: 'proteinPer100g', op: 'gte', value: 20 }],
    });
    // « poulet » (texte) ET règne ANIMALE ET protéines ≥ 20 → seul p2.
    expect(groups[0].foods.map((f) => f.uuid)).toEqual(['p2']);
  });

  it('buildFoodGroups : facette catégorie sans résultat → aucun groupe', () => {
    expect(buildFoodGroups([lentilles], [], '', { realm: 'ANIMALE' })).toEqual([]);
  });
});

describe('Catalogue d’aliments — critères filtrables (panneau recherche multi-critères)', () => {
  // Contrat du panneau de filtres : il doit proposer un seuil pour kcal + chaque macro et pour
  // CHAQUE micronutriment suivi par le modèle. Les clés doivent matcher exactement les champs
  // per-100 g lus par foodNutrientValue/passesThresholds — sinon un critère filtre dans le vide
  // (régression silencieuse si un micro est retiré/réordonné ou une clé mal mappée).
  it('FILTERABLE_MACROS : kcal + 4 macros, mappés sur les clés per-100 g du modèle', () => {
    expect(FILTERABLE_MACROS.map((m) => m.key)).toEqual([
      'kcalPer100g',
      'carbsPer100g',
      'fatPer100g',
      'proteinPer100g',
      'fiberPer100g',
    ]);
    expect(FILTERABLE_MACROS.every((m) => m.label.length > 0 && m.unit.length > 0)).toBe(true);
  });

  it('FILTERABLE_MICROS : un critère par micronutriment suivi (10 clés per-100 g)', () => {
    expect(FILTERABLE_MICROS.map((m) => m.key)).toEqual([
      'ironPer100g',
      'calciumPer100g',
      'magnesiumPer100g',
      'zincPer100g',
      'potassiumPer100g',
      'sodiumPer100g',
      'vitaminCPer100g',
      'vitaminDPer100g',
      'vitaminB12Per100g',
      'vitaminAPer100g',
    ]);
    expect(FILTERABLE_MICROS.every((m) => m.label.length > 0 && m.unit.length > 0)).toBe(true);
  });

  it('chaque clé filtrable est lisible par foodNutrientValue (pas de critère orphelin)', () => {
    const f = food({ ironPer100g: 4.2, vitaminCPer100g: 12 });
    for (const n of [...FILTERABLE_MACROS, ...FILTERABLE_MICROS]) {
      expect(Number.isFinite(foodNutrientValue(f, n.key))).toBe(true);
    }
  });
});

describe('Recettes & repas enregistrés — splitRecipesByKind', () => {
  const row = (kind: string, uuid: string) => ({ recipe: { kind, uuid }, count: 1 });

  it('sépare RECIPE et SAVED_MEAL en préservant l’ordre', () => {
    const rows = [row('RECIPE', 'r1'), row('SAVED_MEAL', 's1'), row('RECIPE', 'r2'), row('SAVED_MEAL', 's2')];
    const { recipes, savedMeals } = splitRecipesByKind(rows);
    expect(recipes.map((r) => r.recipe.uuid)).toEqual(['r1', 'r2']);
    expect(savedMeals.map((r) => r.recipe.uuid)).toEqual(['s1', 's2']);
  });

  it('listes vides quand aucune recette', () => {
    expect(splitRecipesByKind([])).toEqual({ recipes: [], savedMeals: [] });
  });
});
