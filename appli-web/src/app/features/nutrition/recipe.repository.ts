import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalRecipe, Recipe } from '@core/models/recipe.model';
import { LocalRecipeIngredient } from '@core/models/recipe-ingredient.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';

/**
 * Façade UI pour Recipes + RecipeIngredients (plats composés ET repas enregistrés, D7).
 * Les ingrédients sont des références vivantes vers Food (pas de snapshot : une recette est un modèle).
 */
@Injectable({ providedIn: 'root' })
export class RecipeRepository {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  readonly recipes: Signal<LocalRecipe[]> = toSignal(
    from(liveQuery(() => this.db.recipes.filter((r) => !r.pendingDeletion).sortBy('name'))),
    { initialValue: [] as LocalRecipe[] },
  );

  /** Tous les ingrédients (l'UI groupe par recipeUUID, tri par orderIndex). */
  readonly ingredients: Signal<LocalRecipeIngredient[]> = toSignal(
    from(liveQuery(() => this.db.recipe_ingredients.filter((i) => !i.pendingDeletion).toArray())),
    { initialValue: [] as LocalRecipeIngredient[] },
  );

  /** Crée la recette et retourne son uuid (pour poser les ingrédients ensuite). */
  async create(input: { name: string; kind: string; totalWeightG?: number | null }): Promise<string> {
    const row: LocalRecipe = {
      uuid: uuidv4(),
      userId: this.auth.currentUser()?.id ?? 0,
      name: input.name,
      kind: input.kind,
      totalWeightG: input.totalWeightG ?? null,
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.recipes.put(row);
    this.triggerSync();
    return row.uuid;
  }

  async update(uuid: string, patch: Partial<Pick<Recipe, 'name' | 'kind' | 'totalWeightG'>>): Promise<void> {
    await this.db.recipes.update(uuid, { ...patch, synced: false, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  async remove(uuid: string): Promise<void> {
    await this.db.recipes.update(uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() });
    this.triggerSync();
  }

  /**
   * Remplace les ingrédients d'une recette (diff vs rows existantes, miroir setMuscles/setEquipments
   * d'ExerciseRepository) : retirés → pendingDeletion, ajoutés → nouvelles rows, quantités/ordre mis à jour.
   */
  async setIngredients(
    recipeUuid: string,
    items: { foodUUID: string; quantityG: number }[],
  ): Promise<void> {
    const now = new Date().toISOString();
    const existing = await this.db.recipe_ingredients
      .filter((i) => i.recipeUUID === recipeUuid && !i.pendingDeletion)
      .toArray();
    const wanted = new Map(items.map((it, idx) => [it.foodUUID, { ...it, orderIndex: idx }]));
    const existingByFood = new Map(existing.map((i) => [i.foodUUID, i]));

    for (const i of existing) {
      const w = wanted.get(i.foodUUID);
      if (!w) {
        await this.db.recipe_ingredients.update(i.uuid, { pendingDeletion: true, synced: false, updatedAt: now });
      } else if (w.quantityG !== i.quantityG || w.orderIndex !== i.orderIndex) {
        await this.db.recipe_ingredients.update(i.uuid, {
          quantityG: w.quantityG,
          orderIndex: w.orderIndex,
          synced: false,
          updatedAt: now,
        });
      }
    }
    for (const [foodUUID, w] of wanted) {
      if (!existingByFood.has(foodUUID)) {
        await this.db.recipe_ingredients.put({
          uuid: uuidv4(),
          recipeUUID: recipeUuid,
          foodUUID,
          quantityG: w.quantityG,
          orderIndex: w.orderIndex,
          updatedAt: now,
          synced: false,
          pendingDeletion: false,
        });
      }
    }
    this.triggerSync();
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
}
