import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { RecipeIngredientApi } from '@core/api/recipe-ingredient-api';
import { LocalRecipeIngredient } from '@core/models/recipe-ingredient.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class RecipeIngredientStore extends BaseDexieStore<LocalRecipeIngredient> {
  readonly name = 'recipe_ingredients';
  readonly wsKey = 'recipe_ingredient';
  private readonly db = inject(AppDb);
  private readonly api = inject(RecipeIngredientApi);

  protected table(): Table<LocalRecipeIngredient, string> {
    return this.db.recipe_ingredients;
  }
  async fetchRemote(): Promise<LocalRecipeIngredient[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((i) => ({ ...i, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalRecipeIngredient): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalRecipeIngredient[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalRecipeIngredient): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
