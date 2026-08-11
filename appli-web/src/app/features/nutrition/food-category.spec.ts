import {
  FOOD_GROUPS,
  FOOD_GROUP_COLOR,
  FOOD_GROUP_LABEL,
  FOOD_GROUP_LABELS,
  FOOD_GROUP_OPTIONS,
  FOOD_REALMS,
  FOOD_REALM_COLOR,
  FOOD_REALM_LABEL,
  foodGroupColor,
  foodGroupLabel,
  foodRealmLabel,
  groupCodesForLabel,
  mapOffCategoriesToGroup,
  realmFromLabel,
  realmOf,
  SUPPLEMENT_MACRO_MIN_G,
  type FoodGroupCode,
} from './food-category';

const NO_MACROS = { proteinPer100g: 0, carbsPer100g: 0, fatPer100g: 0 };

describe('food-category — règne dérivé du groupe', () => {
  it('mappe chaque groupe curaté vers un règne connu (pas de trou)', () => {
    for (const g of FOOD_GROUPS) {
      expect(FOOD_REALMS).toContain(realmOf(g));
    }
  });

  it('animaux → ANIMALE (viandes, poisson, fruits de mer, œuf, laitage)', () => {
    for (const g of ['VIANDE_ROUGE', 'VIANDE_BLANCHE', 'POISSON', 'FRUITS_DE_MER', 'OEUF', 'LAITAGE'] as FoodGroupCode[]) {
      expect(realmOf(g)).toBe('ANIMALE');
    }
  });

  it('végétaux → VEGETALE (légumineuse, légume, fruit, céréale, noix)', () => {
    for (const g of ['LEGUMINEUSE', 'LEGUME', 'FRUIT', 'CEREALE_FECULENT', 'NOIX_GRAINE'] as FoodGroupCode[]) {
      expect(realmOf(g)).toBe('VEGETALE');
    }
  });

  it('compléments MACRO + MICRO → COMPLEMENT', () => {
    expect(realmOf('COMPLEMENT_MACRO')).toBe('COMPLEMENT');
    expect(realmOf('COMPLEMENT_MICRO')).toBe('COMPLEMENT');
  });

  it('transformés/divers → AUTRE (matière grasse, sucré, boisson, plat composé, autre)', () => {
    for (const g of ['MATIERE_GRASSE', 'PRODUIT_SUCRE', 'BOISSON', 'PLAT_COMPOSE', 'AUTRE'] as FoodGroupCode[]) {
      expect(realmOf(g)).toBe('AUTRE');
    }
  });

  it('null / inconnu / casse → AUTRE (jamais une exception)', () => {
    expect(realmOf(null)).toBe('AUTRE');
    expect(realmOf(undefined)).toBe('AUTRE');
    expect(realmOf('')).toBe('AUTRE');
    expect(realmOf('PAS_UN_GROUPE')).toBe('AUTRE');
    expect(realmOf('viande_rouge')).toBe('ANIMALE'); // insensible à la casse
  });
});

describe('food-category — labels FR', () => {
  it('chaque groupe a un label non vide', () => {
    for (const g of FOOD_GROUPS) {
      expect(FOOD_GROUP_LABEL[g].length).toBeGreaterThan(0);
    }
  });

  it('COMPLEMENT_MACRO et COMPLEMENT_MICRO partagent le label « Compléments »', () => {
    expect(FOOD_GROUP_LABEL.COMPLEMENT_MACRO).toBe('Compléments');
    expect(FOOD_GROUP_LABEL.COMPLEMENT_MICRO).toBe('Compléments');
  });

  it('foodGroupLabel : fallback « Autre » sur null/inconnu', () => {
    expect(foodGroupLabel('POISSON')).toBe('Poisson');
    expect(foodGroupLabel(null)).toBe('Autre');
    expect(foodGroupLabel('XYZ')).toBe('Autre');
  });

  it('chaque règne a un label', () => {
    for (const r of FOOD_REALMS) {
      expect(FOOD_REALM_LABEL[r].length).toBeGreaterThan(0);
    }
  });
});

describe('food-category — couleur de badge (par groupe, mnémotechnique)', () => {
  it('FOOD_GROUP_COLOR a une entrée (token --food-grp-*) pour chaque groupe (pas de trou)', () => {
    for (const g of FOOD_GROUPS) {
      expect(FOOD_GROUP_COLOR[g]).toMatch(/^var\(--food-grp-/);
    }
  });

  it('foodGroupColor donne la couleur DU GROUPE (groupes distincts → teintes distinctes)', () => {
    expect(foodGroupColor('VIANDE_ROUGE')).toBe('var(--food-grp-viande-rouge)');
    expect(foodGroupColor('LEGUME')).toBe('var(--food-grp-legume)');
    // Cœur de la tâche : plus une seule teinte par règne — viande rouge ≠ légume, poisson ≠ viande.
    expect(foodGroupColor('VIANDE_ROUGE')).not.toBe(foodGroupColor('LEGUME'));
    expect(foodGroupColor('POISSON')).not.toBe(foodGroupColor('VIANDE_ROUGE'));
    // Insensible à la casse.
    expect(foodGroupColor('legume')).toBe('var(--food-grp-legume)');
  });

  it('COMPLEMENT_MACRO et COMPLEMENT_MICRO partagent une teinte (label « Compléments » commun)', () => {
    expect(foodGroupColor('COMPLEMENT_MACRO')).toBe(foodGroupColor('COMPLEMENT_MICRO'));
  });

  it('foodGroupColor : fallback --food-other sur null / inconnu', () => {
    expect(foodGroupColor(null)).toBe('var(--food-other)');
    expect(foodGroupColor(undefined)).toBe('var(--food-other)');
    expect(foodGroupColor('XYZ')).toBe('var(--food-other)');
  });
});

describe('food-category — couleur d’origine (par règne, graphe Origine inchangé)', () => {
  it('FOOD_REALM_COLOR conservée : chaque règne a un token --food-* (donut origine)', () => {
    for (const r of FOOD_REALMS) {
      expect(FOOD_REALM_COLOR[r]).toMatch(/^var\(--food-/);
    }
  });

  it('foodRealmLabel : libellé d’origine FR dérivé du groupe', () => {
    expect(foodRealmLabel('POISSON')).toBe('Animale');
    expect(foodRealmLabel('COMPLEMENT_MACRO')).toBe('Compléments');
    expect(foodRealmLabel(null)).toBe('Autre');
  });
});

describe('food-category — options de filtre / sélecteur (labels ↔ codes)', () => {
  it('FOOD_GROUP_OPTIONS : labels dédupliqués, « Compléments » recouvre MACRO + MICRO', () => {
    // Un label par option (pas de doublon), et chaque code n’apparaît qu’une fois au total.
    const labels = FOOD_GROUP_OPTIONS.map((o) => o.label);
    expect(new Set(labels).size).toBe(labels.length);
    const allCodes = FOOD_GROUP_OPTIONS.flatMap((o) => o.codes);
    expect([...allCodes].sort()).toEqual([...FOOD_GROUPS].sort());
    // « Compléments » = une seule option à 2 codes.
    const compl = FOOD_GROUP_OPTIONS.find((o) => o.label === 'Compléments');
    expect(compl?.codes.sort()).toEqual(['COMPLEMENT_MACRO', 'COMPLEMENT_MICRO']);
    // 18 groupes − 1 doublon « Compléments » = 17 labels.
    expect(FOOD_GROUP_LABELS.length).toBe(17);
  });

  it('groupCodesForLabel : résout un label vers ses codes ([] si inconnu)', () => {
    expect(groupCodesForLabel('Poisson')).toEqual(['POISSON']);
    expect(groupCodesForLabel('Compléments').sort()).toEqual(['COMPLEMENT_MACRO', 'COMPLEMENT_MICRO']);
    expect(groupCodesForLabel('Inconnu')).toEqual([]);
  });

  it('realmFromLabel : réciproque de FOOD_REALM_LABEL (null si inconnu)', () => {
    for (const r of FOOD_REALMS) {
      expect(realmFromLabel(FOOD_REALM_LABEL[r])).toBe(r);
    }
    expect(realmFromLabel('Pas un règne')).toBeNull();
  });
});

describe('food-category — mapping OFF categories_tags → groupe', () => {
  it('viande blanche avant viande rouge (poulet a les deux tags meats+poultry)', () => {
    expect(mapOffCategoriesToGroup(['en:meats', 'en:poultry', 'en:chickens'], NO_MACROS)).toBe('VIANDE_BLANCHE');
  });

  it('viande rouge (bœuf)', () => {
    expect(mapOffCategoriesToGroup(['en:meats', 'en:beef'], NO_MACROS)).toBe('VIANDE_ROUGE');
  });

  it('fruits de mer avant poisson (« shellfish » contient « fish »)', () => {
    expect(mapOffCategoriesToGroup(['en:seafood', 'en:shellfish'], NO_MACROS)).toBe('FRUITS_DE_MER');
  });

  it('poisson', () => {
    expect(mapOffCategoriesToGroup(['en:fishes', 'en:smoked-salmons'], NO_MACROS)).toBe('POISSON');
  });

  it('laitage (tags fr)', () => {
    expect(mapOffCategoriesToGroup(['fr:produits-laitiers', 'fr:fromages'], NO_MACROS)).toBe('LAITAGE');
  });

  it('céréales & féculents', () => {
    expect(mapOffCategoriesToGroup(['en:cereals-and-potatoes', 'en:pastas'], NO_MACROS)).toBe('CEREALE_FECULENT');
  });

  it('légumineuses', () => {
    expect(mapOffCategoriesToGroup(['en:pulses', 'en:lentils'], NO_MACROS)).toBe('LEGUMINEUSE');
  });

  it('boisson', () => {
    expect(mapOffCategoriesToGroup(['en:beverages', 'en:waters'], NO_MACROS)).toBe('BOISSON');
  });

  it('fallback AUTRE si rien ne matche / tags vides', () => {
    expect(mapOffCategoriesToGroup(['en:unknown-stuff'], NO_MACROS)).toBe('AUTRE');
    expect(mapOffCategoriesToGroup([], NO_MACROS)).toBe('AUTRE');
    expect(mapOffCategoriesToGroup(null, NO_MACROS)).toBe('AUTRE');
    expect(mapOffCategoriesToGroup(undefined, NO_MACROS)).toBe('AUTRE');
  });

  it('complément avec macros significatives → COMPLEMENT_MACRO (whey)', () => {
    const whey = { proteinPer100g: 75, carbsPer100g: 8, fatPer100g: 6 };
    expect(mapOffCategoriesToGroup(['en:dietary-supplements', 'en:proteins'], whey)).toBe('COMPLEMENT_MACRO');
  });

  it('complément sans macro → COMPLEMENT_MICRO (multivitamine)', () => {
    expect(mapOffCategoriesToGroup(['fr:complements-alimentaires'], NO_MACROS)).toBe('COMPLEMENT_MICRO');
  });

  it('le seuil MACRO/MICRO est piloté par SUPPLEMENT_MACRO_MIN_G', () => {
    const justBelow = { proteinPer100g: SUPPLEMENT_MACRO_MIN_G - 1, carbsPer100g: 0, fatPer100g: 0 };
    const atThreshold = { proteinPer100g: SUPPLEMENT_MACRO_MIN_G, carbsPer100g: 0, fatPer100g: 0 };
    expect(mapOffCategoriesToGroup(['en:dietary-supplements'], justBelow)).toBe('COMPLEMENT_MICRO');
    expect(mapOffCategoriesToGroup(['en:dietary-supplements'], atThreshold)).toBe('COMPLEMENT_MACRO');
  });

  it('complément prioritaire sur un éventuel tag aliment (whey taggée aussi « dairies »)', () => {
    const whey = { proteinPer100g: 75, carbsPer100g: 8, fatPer100g: 6 };
    expect(mapOffCategoriesToGroup(['en:dairies', 'en:dietary-supplements'], whey)).toBe('COMPLEMENT_MACRO');
  });
});
