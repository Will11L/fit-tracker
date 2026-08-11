import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalFood } from '@core/models/food.model';
import { LocalMeal, Meal } from '@core/models/meal.model';
import { LocalMealEntry, MealEntry } from '@core/models/meal-entry.model';
import { LocalMealPreset, MealPreset } from '@core/models/meal-preset.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { effectiveFoodKcal } from './food-kcal';

/**
 * Façade UI pour le journal Nutrition : Meals + MealEntries + MealPresets.
 * Un Meal n'est créé que lorsqu'une première entry y est ajoutée (§3.4 NUTRITION_DESIGN) —
 * le journal affiche les presets comme sections vides sans créer de rows fantômes.
 * Snapshot D5 : addEntryFromFood fige les macros per-100g du Food au moment de l'ajout.
 */
@Injectable({ providedIn: 'root' })
export class MealRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  /** Tous les repas (l'UI filtre par date, tri par orderIndex). */
  readonly meals: Signal<LocalMeal[]> = toSignal(
    from(liveQuery(() => this.db.meals.filter((m) => !m.pendingDeletion).toArray())),
    { initialValue: [] as LocalMeal[] },
  );

  /** Toutes les entries (l'UI groupe par mealUUID ; la vue à plat filtre/trie en mémoire). */
  readonly entries: Signal<LocalMealEntry[]> = toSignal(
    from(liveQuery(() => this.db.meal_entries.filter((e) => !e.pendingDeletion).toArray())),
    { initialValue: [] as LocalMealEntry[] },
  );

  /** Périodes habituelles triées par ordre (sections du journal, D10). */
  readonly presets: Signal<LocalMealPreset[]> = toSignal(
    from(liveQuery(() => this.db.meal_presets.filter((p) => !p.pendingDeletion).sortBy('orderIndex'))),
    { initialValue: [] as LocalMealPreset[] },
  );

  // -------------------- Meals --------------------

  /** Crée un repas du journal et retourne son uuid. presetUuid = lien stable vers la période (null si ad hoc). */
  async createMeal(input: {
    date: string;
    name: string;
    orderIndex: number;
    presetUuid?: string | null;
    time?: string | null;
  }): Promise<string> {
    const row: LocalMeal = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      date: input.date,
      name: input.name,
      orderIndex: input.orderIndex,
      time: input.time ?? null,
      presetUuid: input.presetUuid ?? null,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.meals.put(row);
    this.triggerSync();
    return row.uuid;
  }

  /**
   * Pose le presetUuid sur les repas legacy (créés avant la colonne, presetUuid null) dont le nom
   * correspond encore à une période — une seule fois, idempotent. Sans ça, renommer une période
   * orphelinerait ces vieux repas. Best-effort, ne bloque jamais l'UI.
   */
  async healPresetLinks(toHeal: { uuid: string; presetUuid: string }[]): Promise<void> {
    if (toHeal.length === 0) return;
    const now = new Date().toISOString();
    for (const { uuid, presetUuid } of toHeal) {
      await this.db.meals.update(uuid, { presetUuid, synced: false, updatedAt: now });
    }
    this.triggerSync();
  }

  async updateMeal(uuid: string, patch: Partial<Pick<Meal, 'date' | 'name' | 'orderIndex'>>): Promise<void> {
    await this.db.meals.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  /** Supprime un repas + ses entries (tombstones locaux ; le serveur cascade de toute façon). */
  async removeMeal(uuid: string): Promise<void> {
    const now = new Date().toISOString();
    await this.db.meal_entries
      .where('mealUUID')
      .equals(uuid)
      .modify({ pendingDeletion: true, synced: false, updatedAt: now });
    await this.db.meals.update(uuid, { pendingDeletion: true, updatedAt: now });
    this.triggerSync();
  }

  // -------------------- Entries (snapshot D5) --------------------

  /**
   * Ajoute un aliment du catalogue à un repas : snapshot des macros per-100g du Food au moment
   * de l'ajout (D5 — l'historique est immuable). Retourne l'uuid de l'entry.
   * La kcal snapshotée est la kcal *effective* selon la source (D12) : dérivée des macros pour
   * un brut CIQUAL, étiquette pour OFF, saisie pour CUSTOM — pour un brut, le snapshot vaut donc
   * exactement `kcalFromMacros(macros snapshotées)` et reste reproductible depuis ces macros.
   */
  async addEntryFromFood(
    mealUuid: string,
    food: LocalFood,
    quantityG: number,
    portionLabel: string | null = null,
  ): Promise<string> {
    const row: LocalMealEntry = {
      uuid: uuidv4(),
      mealUUID: mealUuid,
      foodUUID: food.uuid,
      recipeUUID: null,
      displayName: food.name,
      quantityG,
      portionLabel,
      kcalPer100g: effectiveFoodKcal(food),
      proteinPer100g: food.proteinPer100g,
      carbsPer100g: food.carbsPer100g,
      fatPer100g: food.fatPer100g,
      fiberPer100g: food.fiberPer100g,
      sugarPer100g: food.sugarPer100g,
      satFatPer100g: food.satFatPer100g,
      saltPer100g: food.saltPer100g,
      ironPer100g: food.ironPer100g,
      calciumPer100g: food.calciumPer100g,
      magnesiumPer100g: food.magnesiumPer100g,
      zincPer100g: food.zincPer100g,
      potassiumPer100g: food.potassiumPer100g,
      sodiumPer100g: food.sodiumPer100g,
      vitaminCPer100g: food.vitaminCPer100g,
      vitaminDPer100g: food.vitaminDPer100g,
      vitaminB12Per100g: food.vitaminB12Per100g,
      vitaminAPer100g: food.vitaminAPer100g,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.meal_entries.put(row);
    this.triggerSync();
    return row.uuid;
  }

  /** Ajoute une entry déjà snapshotée (recette kind=RECIPE au prorata, duplication d'un repas passé…). */
  async addEntry(entry: Omit<MealEntry, 'uuid' | 'updatedAt'>): Promise<string> {
    const row: LocalMealEntry = {
      ...entry,
      uuid: uuidv4(),
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.meal_entries.put(row);
    this.triggerSync();
    return row.uuid;
  }

  /** Modifier la quantité ne re-résout pas l'aliment : le snapshot per-100g reste figé (D5). */
  async updateEntry(
    uuid: string,
    patch: Partial<Pick<MealEntry, 'quantityG' | 'portionLabel' | 'displayName' | 'mealUUID'>>,
  ): Promise<void> {
    await this.db.meal_entries.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  async removeEntry(uuid: string): Promise<void> {
    await this.db.meal_entries.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  // -------------------- Presets (D10) --------------------

  async createPreset(input: { name: string; orderIndex: number; defaultTime?: string | null }): Promise<string> {
    const row: LocalMealPreset = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      name: input.name,
      orderIndex: input.orderIndex,
      defaultTime: input.defaultTime ?? null,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.meal_presets.put(row);
    this.triggerSync();
    return row.uuid;
  }

  async updatePreset(
    uuid: string,
    patch: Partial<Pick<MealPreset, 'name' | 'orderIndex' | 'defaultTime'>>,
  ): Promise<void> {
    await this.db.meal_presets.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  async removePreset(uuid: string): Promise<void> {
    await this.db.meal_presets.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
}
