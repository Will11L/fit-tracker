import { LocalMeal } from '@core/models/meal.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { LocalMealPreset } from '@core/models/meal-preset.model';
import { MICRO_KEYS, MicroTotals, ZERO_MICRO_TOTALS } from './micros';

/** Totaux kcal + macros dérivés des snapshots per-100g (D5 : total = per100g × quantityG / 100). */
export interface MacroTotals {
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  /** Fibres (D11) — snapshot optionnel, null traité comme 0 dans les cumuls. */
  fiber: number;
}

/** Section du journal : un preset (D10, meal éventuellement absent) ou un repas ad hoc. */
export interface JournalSection {
  /** Clé stable pour le track Angular : uuid du meal sinon uuid du preset. */
  key: string;
  name: string;
  defaultTime: string | null;
  /** null tant qu'aucune entry n'a été ajoutée (pas de rows fantômes, §3.4 NUTRITION_DESIGN). */
  meal: LocalMeal | null;
  /** orderIndex à utiliser si un meal doit être créé pour cette section. */
  orderIndex: number;
  /** uuid du preset de cette section (null pour une section ad hoc), à poser sur le meal créé. */
  presetUuid: string | null;
  entries: LocalMealEntry[];
  totals: MacroTotals;
}

export const ZERO_TOTALS: MacroTotals = { kcal: 0, protein: 0, carbs: 0, fat: 0, fiber: 0 };

/** Totaux d'une entry depuis son snapshot per-100g. */
export function entryTotals(e: LocalMealEntry): MacroTotals {
  const f = e.quantityG / 100;
  return {
    kcal: e.kcalPer100g * f,
    protein: e.proteinPer100g * f,
    carbs: e.carbsPer100g * f,
    fat: e.fatPer100g * f,
    fiber: (e.fiberPer100g ?? 0) * f,
  };
}

/**
 * Cumul jour des 10 micros depuis les snapshots per-100g des entries (total = per100g × q / 100,
 * null traité comme 0). Pure et testable — alimente le bandeau micros repliable du Journal.
 */
export function sumMicroTotals(entries: LocalMealEntry[]): MicroTotals {
  const acc: MicroTotals = { ...ZERO_MICRO_TOTALS };
  for (const e of entries) {
    const f = e.quantityG / 100;
    for (const k of MICRO_KEYS) {
      acc[k] += (e[k] ?? 0) * f;
    }
  }
  return acc;
}

export function sumTotals(entries: LocalMealEntry[]): MacroTotals {
  return entries.reduce(
    (acc, e) => {
      const t = entryTotals(e);
      return {
        kcal: acc.kcal + t.kcal,
        protein: acc.protein + t.protein,
        carbs: acc.carbs + t.carbs,
        fat: acc.fat + t.fat,
        fiber: acc.fiber + t.fiber,
      };
    },
    { ...ZERO_TOTALS },
  );
}

/**
 * Cible fibres dérivée : 15 g pour 1000 kcal de l'objectif calorique (reco santé courante).
 * null si aucune cible kcal n'est définie (pas de barre, comme les autres macros sans cible).
 */
export function fiberTargetG(kcalGoal: number | null | undefined): number | null {
  return kcalGoal ? (kcalGoal / 1000) * 15 : null;
}

/**
 * Total sucres du jour (g) depuis les snapshots per-100g des entries (total = per100g × q / 100,
 * null traité comme 0). Note sémantique : sugarPer100g = sucres TOTAUX (OFF) — comparés au
 * plafond « totaux » de sugarLimitsG (fruits comptés, plafond calibré en conséquence).
 */
export function sumSugarG(entries: LocalMealEntry[]): number {
  return entries.reduce((acc, e) => acc + (e.sugarPer100g ?? 0) * (e.quantityG / 100), 0);
}

/**
 * Plafond sucres (g/jour) sur les sucres TOTAUX, combinant les deux repères officiels (décision
 * 2026-07-13) : proportionnel à la cible kcal active — g = 5 % du nombre de kcal (≡ 20 % de
 * l'AET ÷ 4 kcal/g, transposition « totaux » de la limite OMS sucres libres, le ×2 absorbant
 * les sucres naturels fruits/laitages) — borné au repère français ANSES de 100 g/j (atteint à
 * 2000 kcal ; plus de calories ne justifie pas plus de sucre). Ex. 1800 → 90 g, 2600 → 100 g.
 * Repère « idéal » = moitié du plafond (miroir du rapport OMS 10 % → 5 %). Contrairement à
 * fiberTargetG, jamais null : sans cible kcal active, repli 2000 kcal → 100 g / 50 g.
 */
export function sugarLimitsG(kcalGoal: number | null | undefined): { limitG: number; idealG: number } {
  const limitG = Math.min((kcalGoal || 2000) * 0.05, 100);
  return { limitG, idealG: limitG / 2 };
}

/**
 * Seuil « riche en sucres » PAR ALIMENT (per-100 g) : repère étiquetage UK « high in sugar »
 * (> 22,5 g/100 g). Distinct du plafond JOURNALIER sugarLimitsG (bandeau) : dans les listes,
 * le sucre est une INFORMATION pour repérer les aliments sucrés au moment de choisir.
 */
export const HIGH_SUGAR_PER_100G = 22.5;

/** Aliment « riche en sucres » (> 22,5 g/100 g) → couleur d'alerte dans les rows. false si non renseigné. */
export function isHighSugar(sugarPer100g: number | null | undefined): boolean {
  return sugarPer100g != null && sugarPer100g > HIGH_SUGAR_PER_100G;
}

/**
 * Sucres consommés d'une entry (g) : snapshot per-100 g × quantité / 100 — null si le snapshot ne
 * les connaît pas (contrairement à sumSugarG qui traite null comme 0 dans le cumul du jour).
 * Alimente le dépli micros d'une ligne du journal (MacroEntryRow).
 */
export function entrySugarG(e: LocalMealEntry): number | null {
  return e.sugarPer100g != null ? e.sugarPer100g * (e.quantityG / 100) : null;
}

/** Date du jour locale au format "YYYY-MM-DD". */
export function todayIso(): string {
  return toIsoDate(new Date());
}

/** "YYYY-MM-DD" ± delta jours (arithmétique locale, pas d'UTC pour éviter les sauts DST). */
export function addDays(isoDate: string, delta: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(y, m - 1, d + delta);
  return toIsoDate(date);
}

function toIsoDate(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Répare les repas legacy (créés avant la colonne preset_uuid, donc presetUuid null) : pour chacun
 * dont le nom correspond encore à une période, retourne le presetUuid à poser — fige le lien
 * par-nom actuel en lien stable par uuid, pour qu'un futur renommage fasse suivre le repas.
 * Les repas sans correspondance de nom restent null (vrais repas ad hoc).
 */
export function legacyMealsToHeal(
  presets: LocalMealPreset[],
  meals: LocalMeal[],
): { uuid: string; presetUuid: string }[] {
  const presetByName = new Map(presets.map((p) => [p.name, p.uuid]));
  const heal: { uuid: string; presetUuid: string }[] = [];
  for (const m of meals) {
    if (m.presetUuid) continue;
    const presetUuid = presetByName.get(m.name);
    if (presetUuid) heal.push({ uuid: m.uuid, presetUuid });
  }
  return heal;
}

/**
 * Assemble les sections du journal d'un jour : les presets (triés par orderIndex) forment les
 * sections de base — chacune appariée à son meal du jour par `presetUuid` (lien stable), avec
 * repli sur le nom pour les meals legacy (presetUuid null). La section affiche toujours le nom
 * courant du preset, donc renommer une période fait suivre les repas existants (jours passés
 * inclus). Les repas ad hoc restants (sans preset apparié) sont appendus dans l'ordre.
 */
export function buildSections(
  presets: LocalMealPreset[],
  dayMeals: LocalMeal[],
  entries: LocalMealEntry[],
): JournalSection[] {
  const byMeal = new Map<string, LocalMealEntry[]>();
  for (const e of entries) {
    const list = byMeal.get(e.mealUUID) ?? [];
    list.push(e);
    byMeal.set(e.mealUUID, list);
  }
  const matched = new Set<string>();
  const sections: JournalSection[] = [];

  for (const p of [...presets].sort((a, b) => a.orderIndex - b.orderIndex)) {
    // Priorité au lien stable par uuid ; repli sur le nom pour les meals legacy (presetUuid null).
    const meal =
      dayMeals.find((m) => !matched.has(m.uuid) && m.presetUuid === p.uuid) ??
      dayMeals.find((m) => !matched.has(m.uuid) && !m.presetUuid && m.name === p.name) ??
      null;
    if (meal) matched.add(meal.uuid);
    const mealEntries = meal ? (byMeal.get(meal.uuid) ?? []) : [];
    sections.push({
      key: meal?.uuid ?? p.uuid,
      name: p.name,
      // Heure réelle du repas du jour si renseignée, sinon l'heure indicative récurrente de la période.
      defaultTime: meal?.time ?? p.defaultTime,
      meal,
      orderIndex: p.orderIndex,
      presetUuid: p.uuid,
      entries: mealEntries,
      totals: sumTotals(mealEntries),
    });
  }

  const adHoc = dayMeals.filter((m) => !matched.has(m.uuid)).sort((a, b) => a.orderIndex - b.orderIndex);
  for (const meal of adHoc) {
    const mealEntries = byMeal.get(meal.uuid) ?? [];
    sections.push({
      key: meal.uuid,
      name: meal.name,
      defaultTime: meal.time ?? null,
      meal,
      orderIndex: meal.orderIndex,
      presetUuid: null,
      entries: mealEntries,
      totals: sumTotals(mealEntries),
    });
  }
  return sections;
}
