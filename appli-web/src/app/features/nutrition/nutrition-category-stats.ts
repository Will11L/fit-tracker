import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import type { DonutSlice } from '@designsystem/common_components/donut-chart';
import type { RadarAxis, RadarSeries } from '@designsystem/common_components/radar-chart';
import {
  FOOD_REALMS,
  FOOD_REALM_COLOR,
  FOOD_REALM_LABEL,
  FoodRealm,
  foodGroupColor,
  foodGroupLabel,
  realmOf,
} from './food-category';
import { entryTotals } from './journal-utils';

/**
 * Stats Catégories d'aliments (feature Catégories d'aliments, sous-tâche S4) — fonctions PURES
 * (sans Angular ni Dexie, testables seules) alimentant la page Stats Nutrition :
 *  - aggregateByOrigin : répartition par ORIGINE (règne dérivé) sur la période, en kcal — 4 parts.
 *  - computeVariety : panneau VARIÉTÉ (groupes distincts + signal monotonie).
 *
 * Métrique = kcal (cohérent avec le donut de la page Objectifs et avec le seuil de monotonie « % des
 * kcal »). Le groupe curaté d'une entry est résolu via son foodUUID dans le catalogue (foodGroupByUuid) ;
 * une entry de recette ou d'aliment hors catalogue (groupe inconnu) tombe dans AUTRE (best-effort,
 * v1 ne décompose pas les recettes).
 */

/** Seuil de dominance (~40 % des kcal) au-delà duquel le panneau Variété signale une « monotonie ». */
export const MONOTONY_THRESHOLD_SHARE = 0.4;

/** Une part de la répartition par origine (règne dérivé) sur la période, mesurée en kcal. */
export interface OriginSlice {
  realm: FoodRealm;
  label: string;
  color: string;
  kcal: number;
  /** Part du total kcal de la période, 0..1 (0 si total nul). */
  share: number;
}

/** Une source (groupe curaté ; COMPLEMENT_MACRO/MICRO fusionnés sous « Compléments ») du panneau Variété. */
export interface VarietyGroup {
  label: string;
  color: string;
  kcal: number;
  /** Part du total kcal de la période, 0..1. */
  share: number;
}

/** Signal informatif (jamais bloquant) de dominance d'un aliment ou d'un groupe sur la période. */
export interface MonotonySignal {
  active: boolean;
  /** Granularité du dominateur rapporté (aliment seul vs groupe). */
  kind: 'FOOD' | 'GROUP';
  /** Nom de l'aliment ou label du groupe dominant (vide si aucune donnée). */
  label: string;
  /** Part du dominateur, 0..1. */
  share: number;
}

export interface VarietyStats {
  /** Nombre de groupes distincts consommés (kcal > 0) — « Compléments » compte pour un. */
  distinctGroups: number;
  /** Groupes consommés, triés par apport kcal décroissant. */
  groups: VarietyGroup[];
  totalKcal: number;
  thresholdShare: number;
  monotony: MonotonySignal;
}

/** Groupe curaté (UPPER_CASE) d'une entry via son foodUUID ; null pour recette / aliment hors catalogue. */
function entryGroup(e: LocalMealEntry, foodGroupByUuid: Map<string, string | null>): string | null {
  return e.foodUUID ? (foodGroupByUuid.get(e.foodUUID) ?? null) : null;
}

/**
 * Répartition par ORIGINE (règne dérivé) sur [startIso, endIso] inclus, en kcal. Toujours 4 parts
 * (ANIMALE / VEGETALE / COMPLEMENT / AUTRE), ordre canonique, parts à 0 incluses (panneau stable).
 * Un complément (COMPLEMENT_MACRO/MICRO → règne COMPLEMENT) n'est JAMAIS agrégé dans animale/végétale.
 * Ignore les apports kcal ≤ 0 et les entries dont le repas parent est absent / hors période.
 */
export function aggregateByOrigin(
  entries: LocalMealEntry[],
  meals: LocalMeal[],
  foodGroupByUuid: Map<string, string | null>,
  startIso: string,
  endIso: string,
): OriginSlice[] {
  const mealById = new Map(meals.map((m) => [m.uuid, m]));
  const kcalByRealm = new Map<FoodRealm, number>();
  let total = 0;
  for (const e of entries) {
    const m = mealById.get(e.mealUUID);
    if (!m || m.date < startIso || m.date > endIso) continue;
    const kcal = entryTotals(e).kcal;
    if (kcal <= 0) continue;
    const realm = realmOf(entryGroup(e, foodGroupByUuid));
    kcalByRealm.set(realm, (kcalByRealm.get(realm) ?? 0) + kcal);
    total += kcal;
  }
  return FOOD_REALMS.map((realm) => {
    const kcal = kcalByRealm.get(realm) ?? 0;
    return {
      realm,
      label: FOOD_REALM_LABEL[realm],
      color: FOOD_REALM_COLOR[realm],
      kcal,
      share: total > 0 ? kcal / total : 0,
    };
  });
}

/**
 * Panneau VARIÉTÉ sur [startIso, endIso] inclus, en kcal. Compte les groupes distincts consommés
 * (un groupe qui apporte des kcal sur la période), « Compléments » étant une source à part entière
 * alimentée par les compléments MACRO — les MICRO (~0 kcal) n'ajoutent pas de source macro, ce qui
 * tombe naturellement du comptage par kcal. Émet un signal de MONOTONIE (informatif, jamais bloquant)
 * si un seul aliment OU groupe dépasse le seuil de dominance des kcal — l'aliment seul est rapporté en
 * priorité (plus spécifique), repli sur le groupe dominant.
 */
export function computeVariety(
  entries: LocalMealEntry[],
  meals: LocalMeal[],
  foodGroupByUuid: Map<string, string | null>,
  startIso: string,
  endIso: string,
  thresholdShare = MONOTONY_THRESHOLD_SHARE,
): VarietyStats {
  const mealById = new Map(meals.map((m) => [m.uuid, m]));
  const byLabel = new Map<string, { kcal: number; color: string }>();
  const byFood = new Map<string, { displayName: string; kcal: number }>();
  let total = 0;
  for (const e of entries) {
    const m = mealById.get(e.mealUUID);
    if (!m || m.date < startIso || m.date > endIso) continue;
    const kcal = entryTotals(e).kcal;
    if (kcal <= 0) continue;
    const group = entryGroup(e, foodGroupByUuid);
    const label = foodGroupLabel(group);
    const g = byLabel.get(label) ?? { kcal: 0, color: foodGroupColor(group) };
    g.kcal += kcal;
    byLabel.set(label, g);
    const fkey = e.foodUUID ?? e.recipeUUID ?? e.displayName;
    const f = byFood.get(fkey) ?? { displayName: e.displayName, kcal: 0 };
    f.kcal += kcal;
    byFood.set(fkey, f);
    total += kcal;
  }

  const groups: VarietyGroup[] = [...byLabel.entries()]
    .map(([label, { kcal, color }]) => ({ label, color, kcal, share: total > 0 ? kcal / total : 0 }))
    .sort((a, b) => b.kcal - a.kcal || a.label.localeCompare(b.label));

  // Dominateur le plus spécifique d'abord (un aliment seul), repli sur le groupe dominant.
  let topFoodShare = 0;
  let topFoodName = '';
  for (const { displayName, kcal } of byFood.values()) {
    const share = total > 0 ? kcal / total : 0;
    if (share > topFoodShare) {
      topFoodShare = share;
      topFoodName = displayName;
    }
  }
  const topGroup = groups[0];
  let monotony: MonotonySignal;
  if (topFoodShare > thresholdShare) {
    monotony = { active: true, kind: 'FOOD', label: topFoodName, share: topFoodShare };
  } else if (topGroup && topGroup.share > thresholdShare) {
    monotony = { active: true, kind: 'GROUP', label: topGroup.label, share: topGroup.share };
  } else {
    monotony = { active: false, kind: 'GROUP', label: topGroup?.label ?? '', share: topGroup?.share ?? 0 };
  }

  return { distinctGroups: groups.length, groups, totalKcal: total, thresholdShare, monotony };
}

// ============================================================================
// Adaptateurs vers les composants DS (donut-chart / radar-chart) — PURS, testables.
// Une même agrégation (kcal par origine / par groupe) alimente les deux représentations : le donut
// (parts circulaires) et le radar (axes), de sorte que basculer Cercle <-> Radar montre les MÊMES
// données. Les couleurs viennent toujours des tokens (--food-* pour l'origine, --food-grp-* pour la
// variété, déjà portés par OriginSlice.color / VarietyGroup.color) — jamais en dur.
// ============================================================================

/**
 * Couleur d'accent du polygone radar (origine + variété). Le radar ECharts ne colore le tracé que
 * d'une seule couleur ; ce sont les NOMS d'axes qui portent la couleur sémantique (origine / groupe).
 */
const CATEGORY_RADAR_ACCENT = 'var(--app-primary-action)';

/** Parts de donut de la répartition par origine (kcal par règne, couleur d'origine --food-*). */
export function originDonutSlices(slices: OriginSlice[]): DonutSlice[] {
  return slices.map((s) => ({ label: s.label, value: s.kcal, color: s.color }));
}

/**
 * Axes + série radar de la répartition par origine : 4 axes (règnes, ordre canonique), nom d'axe
 * coloré par le token d'origine (--food-*), une seule série remplie (kcal par origine) — mêmes
 * données que le donut. Tableaux vides si aucune kcal sur la période (→ placeholder du composant).
 */
export function originRadarData(slices: OriginSlice[]): { axes: RadarAxis[]; series: RadarSeries[] } {
  if (slices.every((s) => s.kcal <= 0)) return { axes: [], series: [] };
  return {
    axes: slices.map((s) => ({ label: s.label, color: s.color })),
    series: [
      { name: 'kcal par origine', values: slices.map((s) => s.kcal), color: CATEGORY_RADAR_ACCENT, area: true },
    ],
  };
}

/** Parts de donut du panneau Variété (kcal par groupe consommé, teinte de groupe --food-grp-*). */
export function varietyDonutSlices(groups: VarietyGroup[]): DonutSlice[] {
  return groups.map((g) => ({ label: g.label, value: g.kcal, color: g.color }));
}

/**
 * Axes + série radar du panneau Variété : un axe par groupe distinct consommé (ordre de la liste, kcal
 * décroissant), nom d'axe coloré par le token de groupe (--food-grp-*), une seule série remplie (kcal
 * par groupe) — mêmes données que le donut. Tableaux vides si aucun groupe (→ placeholder).
 */
export function varietyRadarData(groups: VarietyGroup[]): { axes: RadarAxis[]; series: RadarSeries[] } {
  if (groups.length === 0 || groups.every((g) => g.kcal <= 0)) return { axes: [], series: [] };
  return {
    axes: groups.map((g) => ({ label: g.label, color: g.color })),
    series: [
      { name: 'kcal par groupe', values: groups.map((g) => g.kcal), color: CATEGORY_RADAR_ACCENT, area: true },
    ],
  };
}
