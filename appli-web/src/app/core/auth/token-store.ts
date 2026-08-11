import { Injectable, computed, signal } from '@angular/core';

const ACCESS_KEY = 'access_token';
const REFRESH_KEY = 'refresh_token';

/** Persistance des JWT (access + refresh) en localStorage + état réactif (signals). */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly _access = signal<string | null>(localStorage.getItem(ACCESS_KEY));
  private readonly _refresh = signal<string | null>(localStorage.getItem(REFRESH_KEY));

  readonly accessToken = this._access.asReadonly();
  readonly isAuthenticated = computed(() => this._access() !== null);

  get refreshToken(): string | null {
    return this._refresh();
  }

  setTokens(access: string, refresh: string): void {
    this._access.set(access);
    this._refresh.set(refresh);
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  }

  clear(): void {
    this._access.set(null);
    this._refresh.set(null);
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }
}
