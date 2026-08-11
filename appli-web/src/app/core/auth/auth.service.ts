import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, map, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { AuthApi } from '@core/api/auth-api';
import { CurrentUser, MeProfileUpdate } from '@core/models/user.model';
import { TokenStore } from './token-store';

/** État d'auth + flux login/refresh/logout. Miroir de AuthManager + CurrentUserManager (Android). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly tokenStore = inject(TokenStore);
  private readonly router = inject(Router);

  /** Anti-doublon : un seul redirect « session expirée » même si plusieurs 401 concurrents. */
  private sessionExpiredHandled = false;

  readonly isAuthenticated = this.tokenStore.isAuthenticated;
  private readonly _currentUser = signal<CurrentUser | null>(null);
  readonly currentUser = this._currentUser.asReadonly();

  /** Refresh single-flight partagé par les 401 concurrents (miroir du mutex Android). */
  private refresh$: Observable<string> | null = null;

  constructor() {
    // Refresh proactif au retour sur l'onglet : si l'access token (30 min) a expiré pendant
    // l'absence, on le rafraîchit AVANT que les appels/WS ne partent avec un token mort — ça évite
    // la rafale de 401 (API) / 403 (WebSocket) au retour après une longue inactivité.
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') this.refreshIfStale();
      });
    }
  }

  /** Rafraîchit l'access token s'il est expiré ou proche de l'être (single-flight). Best-effort. */
  private refreshIfStale(): void {
    if (!this.tokenStore.isAuthenticated() || !this.tokenStore.refreshToken) return;
    if (!accessTokenStale(this.tokenStore.accessToken())) return;
    this.refreshAccessToken().subscribe({ error: () => undefined });
  }

  login(username: string, password: string): Observable<CurrentUser> {
    return this.api.login(username, password).pipe(
      tap((t) => {
        this.tokenStore.setTokens(t.access_token, t.refresh_token);
        this.sessionExpiredHandled = false;
      }),
      switchMap(() => this.loadMe()),
    );
  }

  loadMe(): Observable<CurrentUser> {
    return this.api.me().pipe(tap((u) => this._currentUser.set(u)));
  }

  /** PATCH /me/profile (self-only) — met aussi à jour le signal currentUser. */
  updateMeProfile(body: MeProfileUpdate): Observable<CurrentUser> {
    return this.api.updateMeProfile(body).pipe(tap((u) => this._currentUser.set(u)));
  }

  /** DELETE /me — suppression de compte (mot de passe requis). Ne purge PAS la session (au caller). */
  deleteMe(password: string): Observable<CurrentUser> {
    return this.api.deleteMe(password);
  }

  logout(): void {
    const rt = this.tokenStore.refreshToken;
    if (rt) this.api.logout(rt).subscribe({ error: () => undefined });
    this.tokenStore.clear();
    this._currentUser.set(null);
  }

  /**
   * Session morte (401 + refresh KO ou absent) : purge les tokens et redirige vers /login
   * avec le message « session expirée ». Pas de POST /logout (le refresh token est déjà
   * invalide côté serveur). Miroir du redirect LoginScreen Android (V4.5).
   */
  sessionExpired(): void {
    this.tokenStore.clear();
    this._currentUser.set(null);
    if (this.sessionExpiredHandled) return;
    this.sessionExpiredHandled = true;
    void this.router.navigateByUrl('/login?reason=session-expired');
  }

  refreshAccessToken(): Observable<string> {
    if (this.refresh$) return this.refresh$;
    const rt = this.tokenStore.refreshToken;
    if (!rt) return throwError(() => new Error('No refresh token'));
    this.refresh$ = this.api.refresh(rt).pipe(
      tap((t) => this.tokenStore.setTokens(t.access_token, t.refresh_token)),
      map((t) => t.access_token),
      shareReplay(1),
      finalize(() => {
        this.refresh$ = null;
      }),
    );
    return this.refresh$;
  }
}

/**
 * Vrai si l'access token est expiré ou expire dans moins de 30 s (skew). Décode l'`exp` du JWT
 * (base64url). Sur token absent/illisible : false (on laisse le flux 401 normal gérer — pas de
 * refresh proactif à l'aveugle).
 */
function accessTokenStale(token: string | null): boolean {
  if (!token) return false;
  const part = token.split('.')[1];
  if (!part) return false;
  try {
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = (JSON.parse(json) as { exp?: number }).exp;
    if (typeof exp !== 'number') return false;
    return exp <= Date.now() / 1000 + 30;
  } catch {
    return false;
  }
}
