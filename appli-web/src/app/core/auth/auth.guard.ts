import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

/**
 * Protège les routes authentifiées.
 *
 * Ne pas se contenter de l'EXISTENCE d'un token : un vieil access token périmé (refresh
 * absent/expiré) passerait le garde, activerait le shell, puis chaque appel API renverrait
 * 401 → écran de chargement qui tourne dans le vide sans jamais aboutir au /login.
 *
 * On confronte donc le serveur via GET /me **avant** d'activer le shell. Si la session est
 * morte (401, access ET refresh périmés), l'interceptor purge les tokens ; ici on garantit
 * la redirection /login. Le /me n'est appelé qu'une fois par session de page (mis en cache
 * dans `currentUser`) → pas de coût sur la navigation interne.
 */
export const authGuard: CanActivateFn = () => {
  const tokenStore = inject(TokenStore);
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!tokenStore.isAuthenticated()) return router.createUrlTree(['/login']);

  // Session déjà validée pendant cette session de page : on laisse passer sans re-frapper /me.
  if (auth.currentUser()) return true;

  // Token présent mais session jamais validée (boot / refresh de page) : on valide via /me.
  return auth.loadMe().pipe(
    map(() => true as const),
    catchError(() =>
      of(router.createUrlTree(['/login'], { queryParams: { reason: 'session-expired' } })),
    ),
  );
};
