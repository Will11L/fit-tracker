import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

/**
 * Tests du garde d'auth : il doit valider la SESSION (pas juste l'existence du token) avant
 * d'activer le shell, sinon une session morte (access périmé + refresh absent) laisse entrer
 * sur un shell qui spinne dans le vide (bug 2026-06-13).
 */
describe('authGuard — validation de session au boot', () => {
  let tokenStore: TokenStore;
  let auth: {
    currentUser: ReturnType<typeof vi.fn>;
    loadMe: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  const run = () =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  beforeEach(() => {
    localStorage.clear();
    auth = { currentUser: vi.fn().mockReturnValue(null), loadMe: vi.fn().mockReturnValue(EMPTY) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    tokenStore = TestBed.inject(TokenStore);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('redirige vers /login quand aucun token', () => {
    const result = run() as UrlTree;
    expect(result instanceof UrlTree).toBe(true);
    expect(result.toString()).toBe('/login');
    expect(auth.loadMe).not.toHaveBeenCalled();
  });

  it('laisse passer sans re-frapper /me si la session est déjà validée', () => {
    tokenStore.setTokens('acc', 'ref');
    auth.currentUser.mockReturnValue({ id: 1, username: 'will' });
    expect(run()).toBe(true);
    expect(auth.loadMe).not.toHaveBeenCalled();
  });

  it('valide la session via /me et laisse passer si OK', () => {
    tokenStore.setTokens('acc', 'ref');
    auth.loadMe.mockReturnValue(of({ id: 1, username: 'will' }));
    let result: unknown;
    (run() as ReturnType<typeof run> & { subscribe: (cb: (v: unknown) => void) => void }).subscribe(
      (v) => (result = v),
    );
    expect(auth.loadMe).toHaveBeenCalledOnce();
    expect(result).toBe(true);
  });

  it('redirige vers /login?reason=session-expired si /me renvoie 401 (session morte)', () => {
    tokenStore.setTokens('acc-stale', 'ref-dead');
    auth.loadMe.mockReturnValue(throwError(() => new Error('401')));
    let result: unknown;
    (run() as ReturnType<typeof run> & { subscribe: (cb: (v: unknown) => void) => void }).subscribe(
      (v) => (result = v),
    );
    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?reason=session-expired');
  });
});
