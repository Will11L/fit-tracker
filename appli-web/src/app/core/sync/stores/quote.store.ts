import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { QuoteApi } from '@core/api/quote-api';
import { LocalQuote } from '@core/models/quote.model';
import { AppDb } from '@core/sync/dexie-db';
import { BaseDexieStore } from '@core/sync/syncable-store';

@Injectable({ providedIn: 'root' })
export class QuoteStore extends BaseDexieStore<LocalQuote> {
  readonly name = 'quotes';
  readonly wsKey = 'quote';
  private readonly db = inject(AppDb);
  private readonly api = inject(QuoteApi);

  protected table(): Table<LocalQuote, string> {
    return this.db.quotes;
  }
  async fetchRemote(): Promise<LocalQuote[]> {
    const wire = await firstValueFrom(this.api.getAll());
    return wire.map((q) => ({ ...q, synced: true, pendingDeletion: false }));
  }
  async pushUpsert(row: LocalQuote): Promise<void> {
    await firstValueFrom(this.api.upsert(row));
  }
  async pushUpsertBulk(rows: LocalQuote[]): Promise<void> {
    await firstValueFrom(this.api.upsertAll(rows));
  }
  async pushDelete(row: LocalQuote): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(row.uuid));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) return;
      throw e;
    }
  }
}
