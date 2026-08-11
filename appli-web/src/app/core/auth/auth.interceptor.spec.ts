import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { TokenStore } from './token-store';

/**
 * Tests du flow « Gestion 401 + refresh token silencieux » (parité Android V4.5/V8.2).
 * Comportements observables : header Bearer, refresh+replay sur 401, purge+redirect
 * si refresh KO/absent, single-flight, pas de retry sur les endpoints d'auth.
 */
describe('authInterceptor — 401 + refresh silencieux', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokenStore: TokenStore;
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    router = { navigateByUrl: vi.fn().mockResolvedValue(true) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(TokenStore);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('attache le Bearer access token aux requêtes API (pas aux endpoints auth)', () => {
    tokenStore.setTokens('acc-1', 'ref-1');
    http.get('/api/v1/muscles').subscribe();
    const apiReq = httpMock.expectOne('/api/v1/muscles');
    expect(apiReq.request.headers.get('Authorization')).toBe('Bearer acc-1');
    apiReq.flush([]);

    http.post('/api/v1/token', 'username=a&password=b').subscribe();
    const tokenReq = httpMock.expectOne('/api/v1/token');
    expect(tokenReq.request.headers.has('Authorization')).toBe(false);
    tokenReq.flush({ access_token: 'x', refresh_token: 'y', token_type: 'bearer' });
  });

  it('sur 401 : refresh silencieux puis replay de la requête avec le nouveau token', () => {
    tokenStore.setTokens('acc-old', 'ref-old');
    let result: unknown;
    http.get('/api/v1/muscles').subscribe((r) => (result = r));

    httpMock
      .expectOne('/api/v1/muscles')
      .flush({ detail: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne('/api/v1/refresh');
    expect(refreshReq.request.body).toEqual({ refresh_token: 'ref-old' });
    refreshReq.flush({ access_token: 'acc-new', refresh_token: 'ref-new', token_type: 'bearer' });

    const retried = httpMock.expectOne('/api/v1/muscles');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer acc-new');
    retried.flush([{ uuid: 'm1' }]);

    expect(result).toEqual([{ uuid: 'm1' }]);
    // Rotation persistée (V8.2)
    expect(tokenStore.accessToken()).toBe('acc-new');
    expect(tokenStore.refreshToken).toBe('ref-new');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('si le refresh échoue : purge des tokens + redirect /login?reason=session-expired', () => {
    tokenStore.setTokens('acc-old', 'ref-dead');
    let error: unknown;
    http.get('/api/v1/muscles').subscribe({ error: (e: unknown) => (error = e) });

    httpMock.expectOne('/api/v1/muscles').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/v1/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(error).toBeTruthy();
    expect(tokenStore.accessToken()).toBeNull();
    expect(tokenStore.refreshToken).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login?reason=session-expired');
  });

  it('sans refresh token : session expirée directe, pas de POST /refresh', () => {
    // access présent mais pas de refresh (état corrompu / legacy)
    localStorage.setItem('access_token', 'acc-only');
    // re-crée le store depuis localStorage
    const store = new TokenStore();
    expect(store.refreshToken).toBeNull();

    let error: unknown;
    http.get('/api/v1/muscles').subscribe({ error: (e: unknown) => (error = e) });
    httpMock.expectOne('/api/v1/muscles').flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone('/api/v1/refresh');
    expect(error).toBeTruthy();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login?reason=session-expired');
  });

  it('single-flight : deux 401 concurrents ne déclenchent qu UN seul POST /refresh', () => {
    tokenStore.setTokens('acc-old', 'ref-old');
    http.get('/api/v1/muscles').subscribe();
    http.get('/api/v1/exercises').subscribe();

    httpMock.expectOne('/api/v1/muscles').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/v1/exercises').flush(null, { status: 401, statusText: 'Unauthorized' });

    // un seul refresh pour les deux 401
    const refreshReq = httpMock.expectOne('/api/v1/refresh');
    refreshReq.flush({ access_token: 'acc-new', refresh_token: 'ref-new', token_type: 'bearer' });

    httpMock.expectOne('/api/v1/muscles').flush([]);
    httpMock.expectOne('/api/v1/exercises').flush([]);
  });

  it('401 sur un endpoint auth (/token, /refresh) : pas de retry (anti-boucle infinie)', () => {
    tokenStore.setTokens('acc', 'ref');
    let error: unknown;
    http.post('/api/v1/token', 'username=a&password=bad').subscribe({
      error: (e: unknown) => (error = e),
    });
    httpMock.expectOne('/api/v1/token').flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone('/api/v1/refresh');
    expect(error).toBeTruthy();
    // les tokens existants ne sont PAS purgés par un mauvais login
    expect(tokenStore.refreshToken).toBe('ref');
  });
});
