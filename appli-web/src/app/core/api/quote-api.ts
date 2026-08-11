import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Quote } from '@core/models/quote.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class QuoteApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/quotes`;

  getAll(): Observable<Quote[]> {
    return this.http.get<Quote[]>(this.base);
  }
  upsert(q: Quote): Observable<Quote> {
    return this.http.put<Quote>(`${this.base}/${q.uuid}`, {
      uuid: q.uuid,
      text: q.text,
      author: q.author ?? null,
      updatedAt: q.updatedAt,
    });
  }
  /** Upsert groupé `PUT /quotes/bulk` (miroir upsertAll Android) — 1 requête pour N rows. */
  upsertAll(items: Quote[]): Observable<Quote[]> {
    return this.http.put<Quote[]>(
      `${this.base}/bulk`,
      items.map((q) => ({
        uuid: q.uuid,
        text: q.text,
        author: q.author ?? null,
        updatedAt: q.updatedAt,
      })),
    );
  }
  delete(uuid: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${uuid}`);
  }
}
