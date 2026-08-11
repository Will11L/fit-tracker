/**
 * Taxonomie des aliments (feature Catégories d'aliments) — pendant web de `app/food_taxonomy.py`
 * (serveur) et de `macro-colors.ts` (conventions d'affichage). Module PUR (sans Angular ni Dexie),
 * testable seul.
 *
 * 2 niveaux (même motif que la refonte muscles, version à 2 niveaux) :
 *  - Groupe : colonne stockée `foods.foodGroup`, UPPER_CASE (politique 11), ~18 valeurs curatées
 *    (FOOD_GROUPS), alignées 1:1 avec FOOD_GROUPS côté serveur.
 *  - Règne : NON stocké → dérivé du groupe (realmOf) : ANIMALE / VEGETALE / COMPLEMENT / AUTRE.
 *
 * La distinction viande rouge / blanche vit au niveau du groupe (pas de 3e niveau) car c'est l'axe
 * utile à l'indicateur de variété. Les groupes COMPLEMENT_MACRO / COMPLEMENT_MICRO partagent le
 * label court « Compléments ».
 */

// -------------------- Groupes curatés (colonne foodGroup) --------------------
// UPPER_CASE (politique 11). Ordre = ordre d'affichage suggéré (animaux, végétaux, transformés,
// compléments, fallback). Doit rester aligné avec app/food_taxonomy.py:FOOD_GROUPS (serveur).
export const FOOD_GROUPS = [
  'VIANDE_ROUGE',
  'VIANDE_BLANCHE',
  'POISSON',
  'FRUITS_DE_MER',
  'OEUF',
  'LAITAGE',
  'LEGUMINEUSE',
  'LEGUME',
  'FRUIT',
  'CEREALE_FECULENT',
  'NOIX_GRAINE',
  'MATIERE_GRASSE',
  'PRODUIT_SUCRE',
  'BOISSON',
  'PLAT_COMPOSE',
  'COMPLEMENT_MACRO',
  'COMPLEMENT_MICRO',
  'AUTRE',
] as const;

export type FoodGroupCode = (typeof FOOD_GROUPS)[number];

// -------------------- Règnes (dérivés, jamais stockés) --------------------
export const FOOD_REALMS = ['ANIMALE', 'VEGETALE', 'COMPLEMENT', 'AUTRE'] as const;
export type FoodRealm = (typeof FOOD_REALMS)[number];

/** Dérivation groupe → règne (miroir de _REALM_BY_GROUP côté serveur). */
const REALM_BY_GROUP: Record<FoodGroupCode, FoodRealm> = {
  VIANDE_ROUGE: 'ANIMALE',
  VIANDE_BLANCHE: 'ANIMALE',
  POISSON: 'ANIMALE',
  FRUITS_DE_MER: 'ANIMALE',
  OEUF: 'ANIMALE',
  LAITAGE: 'ANIMALE',
  LEGUMINEUSE: 'VEGETALE',
  LEGUME: 'VEGETALE',
  FRUIT: 'VEGETALE',
  CEREALE_FECULENT: 'VEGETALE',
  NOIX_GRAINE: 'VEGETALE',
  MATIERE_GRASSE: 'AUTRE',
  PRODUIT_SUCRE: 'AUTRE',
  BOISSON: 'AUTRE',
  PLAT_COMPOSE: 'AUTRE',
  COMPLEMENT_MACRO: 'COMPLEMENT',
  COMPLEMENT_MICRO: 'COMPLEMENT',
  AUTRE: 'AUTRE',
};

/** Règne dérivé du groupe curaté. null / inconnu → AUTRE. */
export function realmOf(foodGroup: string | null | undefined): FoodRealm {
  if (!foodGroup) return 'AUTRE';
  return REALM_BY_GROUP[foodGroup.toUpperCase() as FoodGroupCode] ?? 'AUTRE';
}

// -------------------- Libellés FR --------------------
/** Label FR court de chaque groupe. COMPLEMENT_MACRO/MICRO partagent « Compléments ». */
export const FOOD_GROUP_LABEL: Record<FoodGroupCode, string> = {
  VIANDE_ROUGE: 'Viande rouge',
  VIANDE_BLANCHE: 'Viande blanche',
  POISSON: 'Poisson',
  FRUITS_DE_MER: 'Fruits de mer',
  OEUF: 'Œufs',
  LAITAGE: 'Laitages',
  LEGUMINEUSE: 'Légumineuses',
  LEGUME: 'Légumes',
  FRUIT: 'Fruits',
  CEREALE_FECULENT: 'Céréales & féculents',
  NOIX_GRAINE: 'Noix & graines',
  MATIERE_GRASSE: 'Matières grasses',
  PRODUIT_SUCRE: 'Produits sucrés',
  BOISSON: 'Boissons',
  PLAT_COMPOSE: 'Plats composés',
  COMPLEMENT_MACRO: 'Compléments',
  COMPLEMENT_MICRO: 'Compléments',
  AUTRE: 'Autre',
};

/** Label FR du règne (origine), utilisé par la répartition Stats. */
export const FOOD_REALM_LABEL: Record<FoodRealm, string> = {
  ANIMALE: 'Animale',
  VEGETALE: 'Végétale',
  COMPLEMENT: 'Compléments',
  AUTRE: 'Autre',
};

/** Label d'un groupe (fallback « Autre » si code inconnu/null). */
export function foodGroupLabel(foodGroup: string | null | undefined): string {
  if (!foodGroup) return FOOD_GROUP_LABEL.AUTRE;
  return FOOD_GROUP_LABEL[foodGroup.toUpperCase() as FoodGroupCode] ?? FOOD_GROUP_LABEL.AUTRE;
}

/** Règne (origine) FR à partir d'un groupe, fallback « Autre ». */
export function foodRealmLabel(foodGroup: string | null | undefined): string {
  return FOOD_REALM_LABEL[realmOf(foodGroup)];
}

// -------------------- Couleurs --------------------
/**
 * Couleur par RÈGNE (origine), tokens SCSS `--food-*` (cf. _colors.scss) — pendant de
 * MACRO_COLOR / MICRO_FAMILY_COLOR, jamais de M3 brut. CONSERVÉE : utilisée par le graphe
 * « Origine des calories » (donut 4 parts) de la page Stats, qui reste par règne.
 */
export const FOOD_REALM_COLOR: Record<FoodRealm, string> = {
  ANIMALE: 'var(--food-animal)',
  VEGETALE: 'var(--food-vegetal)',
  COMPLEMENT: 'var(--food-supplement)',
  AUTRE: 'var(--food-other)',
};

/**
 * Couleur de badge par GROUPE (mnémotechnique), tokens SCSS `--food-grp-*` (cf. _colors.scss, dark
 * + light). Une teinte par groupe curaté qui évoque l'aliment (au lieu des 4 teintes par règne).
 * Le badge porte aussi le libellé texte → des clusters proches sont assumés (jaunes œuf / matière
 * grasse, verts légume / légumineuse, bleus poisson / boisson). COMPLEMENT_MACRO et COMPLEMENT_MICRO
 * partagent une teinte (label « Compléments » commun). Jamais de M3 brut.
 */
export const FOOD_GROUP_COLOR: Record<FoodGroupCode, string> = {
  VIANDE_ROUGE: 'var(--food-grp-viande-rouge)',
  VIANDE_BLANCHE: 'var(--food-grp-viande-blanche)',
  POISSON: 'var(--food-grp-poisson)',
  FRUITS_DE_MER: 'var(--food-grp-fruits-de-mer)',
  OEUF: 'var(--food-grp-oeuf)',
  LAITAGE: 'var(--food-grp-laitage)',
  LEGUMINEUSE: 'var(--food-grp-legumineuse)',
  LEGUME: 'var(--food-grp-legume)',
  FRUIT: 'var(--food-grp-fruit)',
  CEREALE_FECULENT: 'var(--food-grp-cereale-feculent)',
  NOIX_GRAINE: 'var(--food-grp-noix-graine)',
  MATIERE_GRASSE: 'var(--food-grp-matiere-grasse)',
  PRODUIT_SUCRE: 'var(--food-grp-produit-sucre)',
  BOISSON: 'var(--food-grp-boisson)',
  PLAT_COMPOSE: 'var(--food-grp-plat-compose)',
  COMPLEMENT_MACRO: 'var(--food-grp-complement)',
  COMPLEMENT_MICRO: 'var(--food-grp-complement)',
  AUTRE: 'var(--food-grp-autre)',
};

/** Couleur (token CSS) du badge d'un groupe (fallback `--food-other` si code inconnu / null). */
export function foodGroupColor(foodGroup: string | null | undefined): string {
  if (!foodGroup) return 'var(--food-other)';
  return FOOD_GROUP_COLOR[foodGroup.toUpperCase() as FoodGroupCode] ?? 'var(--food-other)';
}

// -------------------- Options de filtre / sélecteur (labels ↔ codes) --------------------
/** Une option de filtre par groupe : label FR + les codes qu'il recouvre (Compléments = 2 codes). */
export interface FoodGroupOption {
  label: string;
  codes: FoodGroupCode[];
}

/**
 * Options de groupe dédupliquées PAR LABEL, ordre d'affichage de FOOD_GROUPS. COMPLEMENT_MACRO et
 * COMPLEMENT_MICRO partagent « Compléments » → une seule option recouvrant les deux codes (utile au
 * filtre catalogue et au sélecteur de création, où le sous-choix macro/micro tranche ensuite).
 */
export const FOOD_GROUP_OPTIONS: readonly FoodGroupOption[] = (() => {
  const byLabel = new Map<string, FoodGroupCode[]>();
  for (const g of FOOD_GROUPS) {
    const label = FOOD_GROUP_LABEL[g];
    (byLabel.get(label) ?? byLabel.set(label, []).get(label)!).push(g);
  }
  return [...byLabel.entries()].map(([label, codes]) => ({ label, codes }));
})();

/** Labels de groupe (dédupliqués, ordonnés) — pour peupler un dropdown. */
export const FOOD_GROUP_LABELS: readonly string[] = FOOD_GROUP_OPTIONS.map((o) => o.label);

/** Codes recouverts par un label de groupe (vide si label inconnu). */
export function groupCodesForLabel(label: string): FoodGroupCode[] {
  return FOOD_GROUP_OPTIONS.find((o) => o.label === label)?.codes ?? [];
}

/** Règne dérivé d'un label FR de règne (réciproque de FOOD_REALM_LABEL), null si inconnu. */
export function realmFromLabel(label: string): FoodRealm | null {
  return FOOD_REALMS.find((r) => FOOD_REALM_LABEL[r] === label) ?? null;
}

// ============================================================================
// Mapping Open Food Facts (categories_tags) → groupe curaté
// ============================================================================
//
// OFF expose des tags slugifiés, préfixés par langue (`en:`, `fr:`…), ex.
// `en:dairies`, `fr:viandes`, `en:dietary-supplements`. Best-effort par matching de mots-clés
// normalisés (sans préfixe de langue, tirets → espaces). Fallback AUTRE si rien ne matche.
// Les compléments alimentaires sont classés COMPLEMENT_MACRO/MICRO selon les macros du produit.

/** Total de macros (g/100 g) à partir duquel un complément est jugé contributif → COMPLEMENT_MACRO. */
export const SUPPLEMENT_MACRO_MIN_G = 5;

/** Normalise un tag OFF : retire le préfixe de langue, tirets → espaces, sans accents, minuscule. */
function normalizeTag(tag: string): string {
  const withoutLang = tag.includes(':') ? tag.slice(tag.indexOf(':') + 1) : tag;
  return withoutLang
    .replace(/-/g, ' ')
    .normalize('NFKD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

interface OffMacros {
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
}

/**
 * Mappe les `categories_tags` OFF vers un groupe curaté (best-effort, fallback AUTRE).
 * Les compléments alimentaires sont détectés en priorité puis classés MACRO vs MICRO selon
 * les macros du produit (≥ SUPPLEMENT_MACRO_MIN_G de macros totales → MACRO).
 */
export function mapOffCategoriesToGroup(
  categoriesTags: readonly string[] | null | undefined,
  macros: OffMacros,
): FoodGroupCode {
  const text = ' ' + (categoriesTags ?? []).map(normalizeTag).join(' ') + ' ';
  const has = (...needles: string[]) => needles.some((n) => text.includes(n));

  // 1. Compléments alimentaires : détectés avant tout (un complément n'est jamais une vraie viande).
  if (has('dietary supplements', 'food supplements', 'complements alimentaires')) {
    const totalMacros = (macros.proteinPer100g || 0) + (macros.carbsPer100g || 0) + (macros.fatPer100g || 0);
    return totalMacros >= SUPPLEMENT_MACRO_MIN_G ? 'COMPLEMENT_MACRO' : 'COMPLEMENT_MICRO';
  }

  // 2. Animal : blanc avant rouge, fruits de mer avant poisson (« shellfish » contient « fish »).
  if (has('poultry', 'chicken', 'turkey', 'volaille', 'poulet', 'dinde', 'rabbit', 'lapin')) return 'VIANDE_BLANCHE';
  if (has('seafood', 'shellfish', 'mollusc', 'crustacean', 'fruits de mer', 'mollusque', 'crustace')) return 'FRUITS_DE_MER';
  if (has('fish', 'poisson')) return 'POISSON';
  if (has('eggs', 'oeuf')) return 'OEUF';
  if (has('meats', 'beef', 'pork', 'charcuterie', 'viande', 'boeuf', 'porc')) return 'VIANDE_ROUGE';
  if (has('dairies', 'dairy', 'milk', 'cheese', 'yogurt', 'produits laitiers', 'fromage', 'lait', 'yaourt')) return 'LAITAGE';

  // 3. Végétal : légumineuses / noix avant les fallbacks larges (fruit/légume).
  if (has('legumes', 'pulses', 'lentil', 'bean', 'chickpea', 'legumineuse', 'lentille', 'haricot', 'pois chiche'))
    return 'LEGUMINEUSE';
  if (has('nuts', 'seeds', 'oleaginous', 'noix', 'graine', 'amande', 'oleagineux')) return 'NOIX_GRAINE';
  if (has('cereal', 'pasta', 'rice', 'bread', 'potato', 'cereale', 'feculent', 'riz', 'pates', 'pain', 'pomme de terre'))
    return 'CEREALE_FECULENT';
  if (has('fruits', 'fruit')) return 'FRUIT';
  if (has('vegetable', 'legume', 'mushroom', 'champignon', 'algue')) return 'LEGUME';

  // 4. Transformés / divers.
  if (has('fats', 'oils', 'butter', 'matiere grasse', 'huile', 'beurre', 'margarine')) return 'MATIERE_GRASSE';
  if (has('sugary', 'sweet', 'chocolate', 'dessert', 'sugar', 'candies', 'biscuit', 'confiserie', 'sucre', 'glace'))
    return 'PRODUIT_SUCRE';
  if (has('beverage', 'drink', 'water', 'soda', 'boisson', 'eaux', 'jus')) return 'BOISSON';
  if (has('meals', 'prepared', 'pizza', 'sandwich', 'plats', 'plat cuisine', 'plat prepare')) return 'PLAT_COMPOSE';

  return 'AUTRE';
}
