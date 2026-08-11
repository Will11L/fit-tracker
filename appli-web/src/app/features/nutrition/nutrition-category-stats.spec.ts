import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { FOOD_REALM_COLOR, FOOD_REALM_LABEL, foodGroupColor } from './food-category';
import {
  MONOTONY_THRESHOLD_SHARE,
  aggregateByOrigin,
  computeVariety,
  originDonutSlices,
  originRadarData,
  varietyDonutSlices,
  varietyRadarData,
} from './nutrition-category-stats';

/**
 * Couverture des fonctions pures Stats Catégories (S4) : répartition par origine (4 parts),
 * comptage de variété (groupes distincts, source « Compléments »), et signal de monotonie.
 * Pas de TestBed : on construit entries/meals + une map foodUUID→foodGroup à la main.
 */

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

const START = '2026-06-01';
const END = '2026-06-30';

describe('nutrition-category-stats — répartition par origine (4 parts)', () => {
  const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
  // 100 kcal animale (poulet) + 100 kcal végétale (riz) + 100 kcal complément (whey) + 100 kcal autre (huile).
  const entries = [
    entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 100 }),
    entry({ uuid: 'e2', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 100 }),
    entry({ uuid: 'e3', foodUUID: 'f-whey', kcalPer100g: 100, quantityG: 100 }),
    entry({ uuid: 'e4', foodUUID: 'f-huile', kcalPer100g: 100, quantityG: 100 }),
  ];
  const groupByUuid = new Map<string, string | null>([
    ['f-poulet', 'VIANDE_BLANCHE'],
    ['f-riz', 'CEREALE_FECULENT'],
    ['f-whey', 'COMPLEMENT_MACRO'],
    ['f-huile', 'MATIERE_GRASSE'],
  ]);

  it('renvoie toujours 4 parts en ordre canonique (ANIMALE, VEGETALE, COMPLEMENT, AUTRE)', () => {
    const slices = aggregateByOrigin(entries, meals, groupByUuid, START, END);
    expect(slices.map((s) => s.realm)).toEqual(['ANIMALE', 'VEGETALE', 'COMPLEMENT', 'AUTRE']);
    expect(slices.map((s) => s.label)).toEqual([
      FOOD_REALM_LABEL.ANIMALE,
      FOOD_REALM_LABEL.VEGETALE,
      FOOD_REALM_LABEL.COMPLEMENT,
      FOOD_REALM_LABEL.AUTRE,
    ]);
  });

  it('répartit les kcal par règne, parts sommant à 1', () => {
    const slices = aggregateByOrigin(entries, meals, groupByUuid, START, END);
    for (const s of slices) {
      expect(s.kcal).toBeCloseTo(100);
      expect(s.share).toBeCloseTo(0.25);
    }
    expect(slices.reduce((sum, s) => sum + s.share, 0)).toBeCloseTo(1);
  });

  it('un complément n’est JAMAIS agrégé dans animale/végétale (part COMPLEMENT dédiée)', () => {
    const slices = aggregateByOrigin(entries, meals, groupByUuid, START, END);
    const byRealm = new Map(slices.map((s) => [s.realm, s]));
    // La whey (COMPLEMENT_MACRO) pèse uniquement dans COMPLEMENT, pas dans ANIMALE ni VEGETALE.
    expect(byRealm.get('COMPLEMENT')!.kcal).toBeCloseTo(100);
    expect(byRealm.get('ANIMALE')!.kcal).toBeCloseTo(100); // poulet seul
    expect(byRealm.get('VEGETALE')!.kcal).toBeCloseTo(100); // riz seul
  });

  it('recette / aliment hors catalogue (groupe inconnu) → AUTRE', () => {
    const recipeEntry = entry({ uuid: 'e5', foodUUID: null, recipeUUID: 'r1', kcalPer100g: 100, quantityG: 100 });
    const orphan = entry({ uuid: 'e6', foodUUID: 'f-absent', kcalPer100g: 100, quantityG: 100 });
    const slices = aggregateByOrigin([recipeEntry, orphan], meals, groupByUuid, START, END);
    const autre = slices.find((s) => s.realm === 'AUTRE')!;
    expect(autre.kcal).toBeCloseTo(200);
    expect(autre.share).toBeCloseTo(1);
  });

  it('ignore les entries hors période et renvoie des parts nulles si aucune donnée', () => {
    const outside = [meal({ uuid: 'm1', date: '2025-01-01' })];
    const slices = aggregateByOrigin(entries, outside, groupByUuid, START, END);
    expect(slices).toHaveLength(4);
    for (const s of slices) {
      expect(s.kcal).toBe(0);
      expect(s.share).toBe(0);
    }
  });

  it('bornes de période inclusives : un repas pile sur START ou END compte, la veille/le lendemain non', () => {
    const boundaryMeals = [
      meal({ uuid: 'm-start', date: START }), // 2026-06-01 inclus
      meal({ uuid: 'm-end', date: END }), // 2026-06-30 inclus
      meal({ uuid: 'm-before', date: '2026-05-31' }), // veille exclue
      meal({ uuid: 'm-after', date: '2026-07-01' }), // lendemain exclu
    ];
    const boundaryEntries = [
      entry({ uuid: 'b1', mealUUID: 'm-start', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'b2', mealUUID: 'm-end', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'b3', mealUUID: 'm-before', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'b4', mealUUID: 'm-after', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 100 }),
    ];
    const slices = aggregateByOrigin(boundaryEntries, boundaryMeals, groupByUuid, START, END);
    const byRealm = new Map(slices.map((s) => [s.realm, s]));
    // Seuls les repas du 01 et du 30 comptent : 100 animale (poulet) + 100 végétale (riz).
    expect(byRealm.get('ANIMALE')!.kcal).toBeCloseTo(100);
    expect(byRealm.get('VEGETALE')!.kcal).toBeCloseTo(100);
    expect(slices.reduce((sum, s) => sum + s.kcal, 0)).toBeCloseTo(200);
  });
});

describe('nutrition-category-stats — panneau variété + monotonie', () => {
  const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];

  it('compte les groupes distincts et fusionne « Compléments » (MACRO + MICRO) en une source', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e2', foodUUID: 'f-saumon', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e3', foodUUID: 'f-whey', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e4', foodUUID: 'f-gainer', kcalPer100g: 100, quantityG: 100 }),
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-poulet', 'VIANDE_BLANCHE'],
      ['f-saumon', 'POISSON'],
      ['f-whey', 'COMPLEMENT_MACRO'],
      ['f-gainer', 'COMPLEMENT_MICRO'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    // 4 aliments mais 3 sources : Viande blanche, Poisson, Compléments (MACRO+MICRO fusionnés).
    expect(v.distinctGroups).toBe(3);
    const labels = v.groups.map((g) => g.label);
    expect(labels).toContain('Compléments');
    expect(labels.filter((l) => l === 'Compléments')).toHaveLength(1);
    const compl = v.groups.find((g) => g.label === 'Compléments')!;
    expect(compl.kcal).toBeCloseTo(200); // whey + gainer cumulés sous une seule source
  });

  it('un complément MICRO ~0 kcal n’ajoute PAS de source macro', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 165, quantityG: 200 }),
      // Multivitamine : 0 kcal → n'apporte aucune source macro (exclue du comptage par kcal).
      entry({ uuid: 'e2', foodUUID: 'f-multivit', kcalPer100g: 0, quantityG: 1 }),
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-poulet', 'VIANDE_BLANCHE'],
      ['f-multivit', 'COMPLEMENT_MICRO'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    expect(v.distinctGroups).toBe(1);
    expect(v.groups.map((g) => g.label)).toEqual(['Viande blanche']);
  });

  it('monotonie GROUPE : un groupe domine > seuil (réparti sur plusieurs aliments)', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-boeuf', kcalPer100g: 100, quantityG: 200 }), // 200 kcal viande rouge
      entry({ uuid: 'e2', foodUUID: 'f-agneau', kcalPer100g: 100, quantityG: 200 }), // 200 kcal viande rouge
      entry({ uuid: 'e3', foodUUID: 'f-veau', kcalPer100g: 100, quantityG: 200 }), // 200 kcal viande rouge
      entry({ uuid: 'e4', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 100 }), // 100 kcal féculent
      entry({ uuid: 'e5', foodUUID: 'f-brocoli', kcalPer100g: 100, quantityG: 100 }), // 100 kcal légume
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-boeuf', 'VIANDE_ROUGE'],
      ['f-agneau', 'VIANDE_ROUGE'],
      ['f-veau', 'VIANDE_ROUGE'],
      ['f-riz', 'CEREALE_FECULENT'],
      ['f-brocoli', 'LEGUME'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    // Viande rouge = 600/800 = 75 % ; chaque aliment seul = 200/800 = 25 % < seuil → dominateur = GROUPE.
    expect(v.monotony.active).toBe(true);
    expect(v.monotony.kind).toBe('GROUP');
    expect(v.monotony.label).toBe('Viande rouge');
    expect(v.monotony.share).toBeCloseTo(0.75);
  });

  it('monotonie ALIMENT : un aliment seul domine > seuil (rapporté avant le groupe)', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-poulet', displayName: 'Poulet', kcalPer100g: 100, quantityG: 600 }),
      entry({ uuid: 'e2', foodUUID: 'f-riz', displayName: 'Riz', kcalPer100g: 100, quantityG: 200 }),
      entry({ uuid: 'e3', foodUUID: 'f-brocoli', displayName: 'Brocoli', kcalPer100g: 100, quantityG: 200 }),
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-poulet', 'VIANDE_BLANCHE'],
      ['f-riz', 'CEREALE_FECULENT'],
      ['f-brocoli', 'LEGUME'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    // Poulet = 600/1000 = 60 % → dominateur le plus spécifique = l'ALIMENT.
    expect(v.monotony.active).toBe(true);
    expect(v.monotony.kind).toBe('FOOD');
    expect(v.monotony.label).toBe('Poulet');
    expect(v.monotony.share).toBeCloseTo(0.6);
  });

  it('bonne diversité : aucun dominateur > seuil → signal inactif', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 300 }),
      entry({ uuid: 'e2', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 300 }),
      entry({ uuid: 'e3', foodUUID: 'f-brocoli', kcalPer100g: 100, quantityG: 300 }),
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-poulet', 'VIANDE_BLANCHE'],
      ['f-riz', 'CEREALE_FECULENT'],
      ['f-brocoli', 'LEGUME'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    // 3 groupes ~33 % chacun, aucun aliment > 40 %.
    expect(v.distinctGroups).toBe(3);
    expect(v.monotony.active).toBe(false);
  });

  it('période vide : 0 groupe, signal inactif, total nul', () => {
    const v = computeVariety([], meals, new Map(), START, END);
    expect(v.distinctGroups).toBe(0);
    expect(v.groups).toEqual([]);
    expect(v.totalKcal).toBe(0);
    expect(v.monotony.active).toBe(false);
  });

  it('seuil strict : un dominateur PILE au seuil (40 %) ne déclenche pas la monotonie', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 40 }), // 40 / 100 = 40 %
      entry({ uuid: 'e2', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 30 }), // 30 %
      entry({ uuid: 'e3', foodUUID: 'f-brocoli', kcalPer100g: 100, quantityG: 30 }), // 30 %
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-poulet', 'VIANDE_BLANCHE'],
      ['f-riz', 'CEREALE_FECULENT'],
      ['f-brocoli', 'LEGUME'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    // Aliment ET groupe dominants à exactement 0.40 : la condition est « > seuil », donc inactif.
    expect(v.monotony.share).toBeCloseTo(0.4);
    expect(v.monotony.active).toBe(false);
    expect(v.distinctGroups).toBe(3);
  });

  it('chaque source de variété porte la couleur mnémotechnique DE SON GROUPE (teintes distinctes)', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-boeuf', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e2', foodUUID: 'f-brocoli', kcalPer100g: 100, quantityG: 100 }),
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-boeuf', 'VIANDE_ROUGE'],
      ['f-brocoli', 'LEGUME'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    const byLabel = new Map(v.groups.map((g) => [g.label, g]));
    // Cœur de la tâche : la couleur de la source vient du GROUPE (token --food-grp-*), pas du règne.
    expect(byLabel.get('Viande rouge')!.color).toBe(foodGroupColor('VIANDE_ROUGE'));
    expect(byLabel.get('Légumes')!.color).toBe(foodGroupColor('LEGUME'));
    // Deux groupes distincts → deux teintes distinctes (viande rouge et légume ne sont plus la même couleur).
    expect(byLabel.get('Viande rouge')!.color).not.toBe(byLabel.get('Légumes')!.color);
  });

  it('la source « Compléments » fusionnée porte la teinte partagée des compléments', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-whey', kcalPer100g: 100, quantityG: 100 }),
      entry({ uuid: 'e2', foodUUID: 'f-gainer', kcalPer100g: 100, quantityG: 100 }),
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-whey', 'COMPLEMENT_MACRO'],
      ['f-gainer', 'COMPLEMENT_MICRO'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    const compl = v.groups.find((g) => g.label === 'Compléments')!;
    // MACRO et MICRO partagent un label ET une teinte (foodGroupColor identique pour les deux codes).
    expect(compl.color).toBe(foodGroupColor('COMPLEMENT_MACRO'));
    expect(foodGroupColor('COMPLEMENT_MACRO')).toBe(foodGroupColor('COMPLEMENT_MICRO'));
  });

  it('groupes triés par apport kcal décroissant (ordre de la liste affichée)', () => {
    const entries = [
      entry({ uuid: 'e1', foodUUID: 'f-legume', kcalPer100g: 100, quantityG: 50 }), // 50 kcal
      entry({ uuid: 'e2', foodUUID: 'f-viande', kcalPer100g: 100, quantityG: 150 }), // 150 kcal
      entry({ uuid: 'e3', foodUUID: 'f-fruit', kcalPer100g: 100, quantityG: 100 }), // 100 kcal
    ];
    const groupByUuid = new Map<string, string | null>([
      ['f-legume', 'LEGUME'],
      ['f-viande', 'VIANDE_BLANCHE'],
      ['f-fruit', 'FRUIT'],
    ]);
    const v = computeVariety(entries, meals, groupByUuid, START, END);
    expect(v.groups.map((g) => g.label)).toEqual(['Viande blanche', 'Fruits', 'Légumes']);
    expect(v.groups.map((g) => g.kcal)).toEqual([150, 100, 50]);
  });
});

describe('nutrition-category-stats — adaptateurs Cercle (donut) / Radar (origine)', () => {
  const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
  // 200 kcal animale (poulet) + 100 kcal végétale (riz) ; compléments + autre à 0.
  const entries = [
    entry({ uuid: 'e1', foodUUID: 'f-poulet', kcalPer100g: 100, quantityG: 200 }),
    entry({ uuid: 'e2', foodUUID: 'f-riz', kcalPer100g: 100, quantityG: 100 }),
  ];
  const groupByUuid = new Map<string, string | null>([
    ['f-poulet', 'VIANDE_BLANCHE'],
    ['f-riz', 'CEREALE_FECULENT'],
  ]);
  const slices = aggregateByOrigin(entries, meals, groupByUuid, START, END);

  it('donut et radar montrent LES MÊMES données (kcal + couleur par règne, ordre canonique)', () => {
    const donut = originDonutSlices(slices);
    const radar = originRadarData(slices);
    expect(donut.map((d) => d.value)).toEqual([200, 100, 0, 0]);
    expect(radar.series[0].values).toEqual([200, 100, 0, 0]);
    expect(donut.map((d) => d.label)).toEqual(radar.axes.map((a) => a.label));
    // Couleur portée par le token d'origine (--food-*) dans les deux vues, jamais en dur.
    expect(donut[0].color).toBe(FOOD_REALM_COLOR.ANIMALE);
    expect(radar.axes[0].color).toBe(FOOD_REALM_COLOR.ANIMALE);
  });

  it('radar = 4 axes (règnes) + une seule série remplie « kcal par origine »', () => {
    const radar = originRadarData(slices);
    expect(radar.axes).toHaveLength(4);
    expect(radar.series).toHaveLength(1);
    expect(radar.series[0].name).toBe('kcal par origine');
    expect(radar.series[0].area).toBe(true);
  });

  it('période sans kcal → radar vide (placeholder du composant)', () => {
    const empty = aggregateByOrigin([], meals, groupByUuid, START, END);
    expect(originRadarData(empty)).toEqual({ axes: [], series: [] });
  });
});

describe('nutrition-category-stats — adaptateurs Cercle (donut) / Radar (variété)', () => {
  const meals = [meal({ uuid: 'm1', date: '2026-06-12' })];
  const entries = [
    entry({ uuid: 'e1', foodUUID: 'f-viande', kcalPer100g: 100, quantityG: 150 }), // 150 kcal
    entry({ uuid: 'e2', foodUUID: 'f-fruit', kcalPer100g: 100, quantityG: 100 }), // 100 kcal
    entry({ uuid: 'e3', foodUUID: 'f-legume', kcalPer100g: 100, quantityG: 50 }), // 50 kcal
  ];
  const groupByUuid = new Map<string, string | null>([
    ['f-viande', 'VIANDE_BLANCHE'],
    ['f-fruit', 'FRUIT'],
    ['f-legume', 'LEGUME'],
  ]);
  const groups = computeVariety(entries, meals, groupByUuid, START, END).groups;

  it('donut et radar montrent LES MÊMES données (un axe/part par groupe, kcal décroissant)', () => {
    const donut = varietyDonutSlices(groups);
    const radar = varietyRadarData(groups);
    expect(donut.map((d) => d.value)).toEqual([150, 100, 50]);
    expect(radar.series[0].values).toEqual([150, 100, 50]);
    expect(donut.map((d) => d.label)).toEqual(radar.axes.map((a) => a.label));
    // Couleur portée par le token mnémotechnique du GROUPE (--food-grp-*) dans les deux vues.
    expect(donut[0].color).toBe(foodGroupColor('VIANDE_BLANCHE'));
    expect(radar.axes[0].color).toBe(foodGroupColor('VIANDE_BLANCHE'));
  });

  it('radar = un axe par groupe distinct + une seule série remplie « kcal par groupe »', () => {
    const radar = varietyRadarData(groups);
    expect(radar.axes).toHaveLength(3);
    expect(radar.series).toHaveLength(1);
    expect(radar.series[0].name).toBe('kcal par groupe');
    expect(radar.series[0].area).toBe(true);
  });

  it('aucun groupe → donut et radar vides (placeholder du composant)', () => {
    const empty = computeVariety([], meals, new Map(), START, END).groups;
    expect(varietyDonutSlices(empty)).toEqual([]);
    expect(varietyRadarData(empty)).toEqual({ axes: [], series: [] });
  });
});
