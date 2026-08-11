import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { Food, LocalFood } from '@core/models/food.model';
import { FoodPortion, LocalFoodPortion } from '@core/models/food-portion.model';
import { OffProduct } from '@core/models/off-product.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { mapOffCategoriesToGroup } from './food-category';
import { detectWaterFromOffCategories } from './hydration';

/**
 * Façade UI pour le catalogue Foods + FoodPortions (miroir de MuscleRepository/ExerciseRepository).
 * Lecture réactive Dexie liveQuery -> signal ; écritures = mutation locale optimiste (synced=false)
 * puis sync best-effort.
 */
@Injectable({ providedIn: 'root' })
export class FoodRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  /** Catalogue trié par nom, masquant les rows en attente de suppression (archived inclus : l'UI filtre). */
  readonly foods: Signal<LocalFood[]> = toSignal(
    from(liveQuery(() => this.db.foods.filter((f) => !f.pendingDeletion).sortBy('name'))),
    { initialValue: [] as LocalFood[] },
  );

  /** Toutes les portions nommées (l'UI groupe par foodUUID). */
  readonly portions: Signal<LocalFoodPortion[]> = toSignal(
    from(liveQuery(() => this.db.food_portions.filter((p) => !p.pendingDeletion).toArray())),
    { initialValue: [] as LocalFoodPortion[] },
  );

  /** Crée un aliment (source CUSTOM par défaut) et retourne son uuid. */
  async create(input: {
    name: string;
    brand?: string | null;
    source?: string;
    sourceRef?: string | null;
    foodGroup?: string | null;
    kcalPer100g: number;
    proteinPer100g: number;
    carbsPer100g: number;
    fatPer100g: number;
    fiberPer100g?: number | null;
    sugarPer100g?: number | null;
    satFatPer100g?: number | null;
    saltPer100g?: number | null;
    ironPer100g?: number | null;
    calciumPer100g?: number | null;
    magnesiumPer100g?: number | null;
    zincPer100g?: number | null;
    potassiumPer100g?: number | null;
    sodiumPer100g?: number | null;
    vitaminCPer100g?: number | null;
    vitaminDPer100g?: number | null;
    vitaminB12Per100g?: number | null;
    vitaminAPer100g?: number | null;
    isFavorite?: boolean;
    isWater?: boolean;
  }): Promise<string> {
    const row: LocalFood = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      name: input.name,
      brand: input.brand ?? null,
      source: input.source ?? 'CUSTOM',
      sourceRef: input.sourceRef ?? null,
      foodGroup: input.foodGroup ?? null,
      kcalPer100g: input.kcalPer100g,
      proteinPer100g: input.proteinPer100g,
      carbsPer100g: input.carbsPer100g,
      fatPer100g: input.fatPer100g,
      fiberPer100g: input.fiberPer100g ?? null,
      sugarPer100g: input.sugarPer100g ?? null,
      satFatPer100g: input.satFatPer100g ?? null,
      saltPer100g: input.saltPer100g ?? null,
      ironPer100g: input.ironPer100g ?? null,
      calciumPer100g: input.calciumPer100g ?? null,
      magnesiumPer100g: input.magnesiumPer100g ?? null,
      zincPer100g: input.zincPer100g ?? null,
      potassiumPer100g: input.potassiumPer100g ?? null,
      sodiumPer100g: input.sodiumPer100g ?? null,
      vitaminCPer100g: input.vitaminCPer100g ?? null,
      vitaminDPer100g: input.vitaminDPer100g ?? null,
      vitaminB12Per100g: input.vitaminB12Per100g ?? null,
      vitaminAPer100g: input.vitaminAPer100g ?? null,
      isFavorite: input.isFavorite ?? false,
      archived: false,
      isWater: input.isWater ?? false,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.foods.put(row);
    this.triggerSync();
    return row.uuid;
  }

  async update(uuid: string, patch: Partial<Omit<Food, 'uuid' | 'userId'>>): Promise<void> {
    await this.db.foods.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  async remove(uuid: string): Promise<void> {
    await this.db.foods.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  /**
   * Copie un produit OFF dans le catalogue (flux d'import §4.1 NUTRITION_DESIGN) avec dédup
   * par sourceRef : re-sélectionner le même produit réutilise le Food existant. La serving_size
   * OFF alimente une portion nommée quand disponible. Retourne l'uuid du Food (existant ou créé).
   */
  async importFromOff(product: OffProduct): Promise<string> {
    const existing = await this.db.foods
      .where('sourceRef')
      .equals(product.sourceRef)
      .filter((f) => f.source === 'OFF' && !f.pendingDeletion)
      .first();
    if (existing) {
      // Re-scan : rafraîchir isWater depuis les categoriesTags frais (l'aliment a pu
      // être importé avant la feature Hydratation → isWater stale). On ne fait que
      // PROMOUVOIR à true (jamais démarquer) pour ne pas écraser un marquage manuel.
      if (!existing.isWater && detectWaterFromOffCategories(product.categoriesTags)) {
        await this.update(existing.uuid, { isWater: true });
      }
      return existing.uuid;
    }

    const foodUuid = await this.create({
      name: product.name,
      brand: product.brand,
      source: 'OFF',
      sourceRef: product.sourceRef,
      foodGroup: mapOffCategoriesToGroup(product.categoriesTags, product),
      kcalPer100g: product.kcalPer100g,
      proteinPer100g: product.proteinPer100g,
      carbsPer100g: product.carbsPer100g,
      fatPer100g: product.fatPer100g,
      fiberPer100g: product.fiberPer100g,
      sugarPer100g: product.sugarPer100g,
      satFatPer100g: product.satFatPer100g,
      saltPer100g: product.saltPer100g,
      ironPer100g: product.ironPer100g,
      calciumPer100g: product.calciumPer100g,
      magnesiumPer100g: product.magnesiumPer100g,
      zincPer100g: product.zincPer100g,
      potassiumPer100g: product.potassiumPer100g,
      sodiumPer100g: product.sodiumPer100g,
      vitaminCPer100g: product.vitaminCPer100g,
      vitaminDPer100g: product.vitaminDPer100g,
      vitaminB12Per100g: product.vitaminB12Per100g,
      vitaminAPer100g: product.vitaminAPer100g,
      isWater: detectWaterFromOffCategories(product.categoriesTags),
    });
    if (product.servingSize && product.servingQuantityG) {
      await this.addPortion(foodUuid, { label: product.servingSize, grams: product.servingQuantityG });
    }
    return foodUuid;
  }

  async addPortion(foodUuid: string, input: Pick<FoodPortion, 'label' | 'grams'>): Promise<string> {
    const row: LocalFoodPortion = {
      uuid: uuidv4(),
      foodUUID: foodUuid,
      label: input.label,
      grams: input.grams,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.food_portions.put(row);
    this.triggerSync();
    return row.uuid;
  }

  async updatePortion(uuid: string, patch: Pick<FoodPortion, 'label' | 'grams'>): Promise<void> {
    await this.db.food_portions.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  async removePortion(uuid: string): Promise<void> {
    await this.db.food_portions.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  private triggerSync(): void {
    // Fire-and-forget, tolérant offline : les rows non syncées repartiront plus tard.
    void this.sync.syncAll().catch(() => undefined);
  }
}
