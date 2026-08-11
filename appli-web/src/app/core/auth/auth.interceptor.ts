import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

/** Endpoints d'auth : pas d'Authorization, pas de retry 401 (sinon boucle infinie sur /refresh). */
const AUTH_ENDPOINT = /\/(token|refresh|signup|logout)$/;

/**
 * Attache le Bearer access token ; sur 401, tente un refresh unique (single-flight)
 * puis rejoue la requête. Miroir de l'Authenticator OkHttp Android (V4.5).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStore = inject(TokenStore);
  const auth = inject(AuthService);
  const isAuthEndpoint = AUTH_ENDPOINT.test(req.url);
  const access = tokenStore.accessToken();

  const authReq =
    access && !isAuthEndpoint ? req.clone({ setHeaders: { Authorization: `Bearer ${access}` } }) : req;

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !isAuthEndpoint) {
        // Pas de refresh token : session irrécupérable -> purge + redirect login direct.
        if (!tokenStore.refreshToken) {
          auth.sessionExpired();
          return throwError(() => err);
        }
        return auth.refreshAccessToken().pipe(
          // catchError AVANT le retry : seul un échec du refresh expire la session
          // (un retry qui échoue en 500/timeout ne doit PAS déconnecter l'utilisateur).
          catchError((refreshErr: unknown) => {
            auth.sessionExpired();
            return throwError(() => refreshErr);
          }),
          switchMap((newAccess) =>
            next(req.clone({ setHeaders: { Authorization: `Bearer ${newAccess}` } })),
          ),
        );
      }
      return throwError(() => err);
    }),
  );
};
