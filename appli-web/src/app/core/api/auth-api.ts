import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TokenResponse } from '@core/models/auth.model';
import { CurrentUser, MeProfileUpdate } from '@core/models/user.model';
import { API_BASE_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  /** POST /token — OAuth2 form-urlencoded (username/password). */
  login(username: string, password: string): Observable<TokenResponse> {
    const body = new HttpParams({ fromObject: { username, password } }).toString();
    return this.http.post<TokenResponse>(`${API_BASE_URL}/token`, body, {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
    });
  }

  /** POST /refresh — rotation : renvoie un nouveau pair access+refresh. */
  refresh(refreshToken: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${API_BASE_URL}/refresh`, { refresh_token: refreshToken });
  }

  logout(refreshToken: string): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/logout`, { refresh_token: refreshToken });
  }

  me(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${API_BASE_URL}/me`);
  }

  /** PATCH /me/profile — édition self-only (champs omis = inchangés, exclude_unset serveur). */
  updateMeProfile(body: MeProfileUpdate): Observable<CurrentUser> {
    return this.http.patch<CurrentUser>(`${API_BASE_URL}/me/profile`, body);
  }

  /** DELETE /me — suppression de compte irréversible, confirmée par mot de passe. */
  deleteMe(password: string): Observable<CurrentUser> {
    return this.http.delete<CurrentUser>(`${API_BASE_URL}/me`, { body: { password } });
  }
}
