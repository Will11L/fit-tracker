import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { RecipeApi } from '@core/api/recipe-api';
import { LocalRecipe } from '@core/models/recipe.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class RecipeStore extends BaseDexieStore<LocalRecipe> {
  readonly name = 'recipes';
  readonly wsKey = 'recipe';
  private readonly db = inject(AppDb);
  private readonly api = inject(RecipeApi);

  protected table(): Table<LocalRecipe, string> {
    return this.db.recipes;
  }
  async fetchRemote(): Promise<LocalRecipe[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((r) => ({ ...r, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalRecipe): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalRecipe[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalRecipe): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
