/**
 * Logique de liste/filtre du catalogue d'aliments (Catalogue page + FoodPickerSheet).
 *
 * Bouts communs extraits du FoodPickerSheet (NUTRITION_DESIGN §5.3) pour être réutilisés par la
 * page Catalogue dédiée et testés sans Angular : regroupement Favoris / Récents / Tous,
 * recherche à plat, et résolution des aliments récemment consommés depuis les entries.
 */
import { LocalFood } from '@core/models/food.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { effectiveFoodKcal } from './food-kcal';
import { MICRO_SHORT, MICRO_TARGETS, type MicroNutrients } from './micros';
import { MACRO_ABBR, MACRO_KEYS, MACRO_LABEL, MACRO_UNIT, type MacroKey } from './macro-colors';
import { realmOf, type FoodRealm } from './food-category';

/** Bloc de la liste catalogue (titre vide = liste à plat de recherche, sans en-tête). */
export interface FoodGroup {
  title: string;
  foods: LocalFood[];
}

/** Options de regroupement (la page Catalogue peut afficher en plus les aliments archivés). */
export interface FoodGroupOptions {
  /** Inclure les aliments archivés dans un bloc « Archivés » (catalogue) ; sinon ils sont masqués. */
  showArchived?: boolean;
  /** Nombre max d'aliments récents affichés (par défaut 8, comme la sheet d'ajout). */
  recentLimit?: number;
  /** Filtres de seuil macros/micros (combinés en ET), cumulables avec la recherche texte. */
  thresholds?: readonly NutrientThreshold[];
  /** Facette par règne (origine dérivée du groupe) ; null/absent = tous règnes. */
  realm?: FoodRealm | null;
  /** Facette par groupe : codes acceptés (Compléments = 2 codes) ; null/vide = tous groupes. */
  groupCodes?: readonly string[] | null;
}

// ----------------------------- Recherche multi-critères (seuils macros/micros) ----------------

/** Opérateur d'un seuil sur un nutriment (par 100 g) : « au moins » (≥) ou « au plus » (≤). */
export type ThresholdOp = 'gte' | 'lte';

/** Clé d'un nutriment filtrable par seuil (per-100 g) : kcal + 4 macros + les 10 micros. */
export type NutrientKey =
  | 'kcalPer100g'
  | 'proteinPer100g'
  | 'carbsPer100g'
  | 'fatPer100g'
  | 'fiberPer100g'
  | keyof MicroNutrients;

/** Un filtre de seuil actif : nutriment + opérateur + valeur (per 100 g). */
export interface NutrientThreshold {
  key: NutrientKey;
  op: ThresholdOp;
  value: number;
}

/** Métadonnées d'un nutriment proposé dans le panneau de filtres (libellé + unité). */
export interface FilterableNutrient {
  key: NutrientKey;
  label: string;
  /** Libellé abrégé (Prot./Gluc./Lip. ; Ca/Mg/Fe… ; Vit C/D…) pour les affichages denses (filtres compacts). */
  abbr: string;
  unit: string;
}

/** Mapping macro (code couleur) → clé per-100 g du modèle Food. */
const MACRO_FILTER_KEY: Record<MacroKey, NutrientKey> = {
  kcal: 'kcalPer100g',
  protein: 'proteinPer100g',
  carbs: 'carbsPer100g',
  fat: 'fatPer100g',
  fiber: 'fiberPer100g',
};

/** Macros filtrables (kcal + glucides/lipides/protéines/fibres), ordre canonique des macros. */
export const FILTERABLE_MACROS: readonly FilterableNutrient[] = MACRO_KEYS.map((k) => ({
  key: MACRO_FILTER_KEY[k],
  label: MACRO_LABEL[k],
  abbr: MACRO_ABBR[k],
  unit: MACRO_UNIT[k],
}));

/** Micros filtrables (10 vitamines & minéraux), même ordre que les barres du résumé (T3/T4). */
export const FILTERABLE_MICROS: readonly FilterableNutrient[] = MICRO_TARGETS.map((t) => ({
  key: t.key,
  label: t.label,
  abbr: MICRO_SHORT[t.key],
  unit: t.unit,
}));

/**
 * Valeur per-100 g d'un nutriment, pour le filtrage. kcal = kcal *effective* (dérivée des macros
 * selon la source, D12) pour rester cohérent avec la valeur affichée. Micros/fibres absents (null)
 * comptés comme 0 : un seuil « ≥ X » (X > 0) exclut donc naturellement les aliments sans la donnée.
 * Pure, testable sans Angular.
 */
export function foodNutrientValue(food: LocalFood, key: NutrientKey): number {
  if (key === 'kcalPer100g') return effectiveFoodKcal(food);
  return food[key] ?? 0;
}

/** Vrai si l'aliment satisfait TOUS les seuils (ET). Liste vide → vrai. Pure, testable. */
export function passesThresholds(food: LocalFood, thresholds: readonly NutrientThreshold[]): boolean {
  return thresholds.every((t) => {
    const v = foodNutrientValue(food, t.key);
    return t.op === 'gte' ? v >= t.value : v <= t.value;
  });
}

/**
 * Vrai si l'aliment passe la facette catégorie (règne ET groupe, chacune optionnelle, combinées en
 * ET). Le règne est dérivé du groupe (realmOf) ; le filtre par groupe matche sur les codes acceptés.
 * Facettes absentes (null/vide) → toujours vrai. Pure, testable sans Angular.
 */
export function passesCategory(
  food: LocalFood,
  realm: FoodRealm | null | undefined,
  groupCodes: readonly string[] | null | undefined,
): boolean {
  if (realm && realmOf(food.foodGroup) !== realm) return false;
  if (groupCodes && groupCodes.length && !groupCodes.includes((food.foodGroup ?? '').toUpperCase())) {
    return false;
  }
  return true;
}

/**
 * uuids des aliments récemment consommés (entries les plus récentes d'abord, dédupliqués).
 * Pure : trie une copie des entries par `updatedAt` décroissant. Limite par défaut 8.
 */
export function recentFoodUuids(entries: LocalMealEntry[], limit = 8): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  const sorted = [...entries].sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''));
  for (const e of sorted) {
    if (e.foodUUID && !seen.has(e.foodUUID)) {
      seen.add(e.foodUUID);
      out.push(e.foodUUID);
      if (out.length >= limit) break;
    }
  }
  return out;
}

/**
 * Construit les blocs de la liste catalogue. Recherche texte OU seuils OU facette catégorie actifs →
 * liste à plat filtrée (nom/marque ET seuils macros/micros ET règne/groupe, titre vide) ; sinon
 * Favoris puis Récents puis Tous (aliments non archivés), et optionnellement un bloc « Archivés » en
 * fin de liste. Toutes les facettes sont cumulables (ET). Pure, testable.
 */
export function buildFoodGroups(
  foods: LocalFood[],
  recentUuids: string[],
  query: string,
  options: FoodGroupOptions = {},
): FoodGroup[] {
  const showArchived = options.showArchived ?? false;
  const thresholds = options.thresholds ?? [];
  const realm = options.realm ?? null;
  const groupCodes = options.groupCodes ?? null;
  const hasCategory = !!realm || !!(groupCodes && groupCodes.length);
  const active = foods.filter((f) => !f.archived);
  const q = query.trim().toLowerCase();

  // Recherche texte OU seuil OU facette catégorie → liste à plat filtrée (toutes les facettes en ET).
  if (q || thresholds.length || hasCategory) {
    const pool = showArchived ? foods : active;
    const filtered = pool.filter(
      (f) =>
        (!q || f.name.toLowerCase().includes(q) || (f.brand ?? '').toLowerCase().includes(q)) &&
        passesThresholds(f, thresholds) &&
        passesCategory(f, realm, groupCodes),
    );
    return filtered.length ? [{ title: '', foods: filtered }] : [];
  }

  const favorites = active.filter((f) => f.isFavorite);
  const favSet = new Set(favorites.map((f) => f.uuid));
  const byUuid = new Map(active.map((f) => [f.uuid, f]));
  const recents = recentUuids
    .filter((uuid) => !favSet.has(uuid))
    .map((uuid) => byUuid.get(uuid))
    .filter((f): f is LocalFood => !!f);
  const recentSet = new Set(recents.map((f) => f.uuid));
  const rest = active.filter((f) => !favSet.has(f.uuid) && !recentSet.has(f.uuid));

  const groups: FoodGroup[] = [];
  if (favorites.length) groups.push({ title: 'Favoris', foods: favorites });
  if (recents.length) groups.push({ title: 'Récents', foods: recents });
  if (rest.length) groups.push({ title: 'Tous', foods: rest });
  if (showArchived) {
    const archived = foods.filter((f) => f.archived);
    if (archived.length) groups.push({ title: 'Archivés', foods: archived });
  }
  return groups;
}
