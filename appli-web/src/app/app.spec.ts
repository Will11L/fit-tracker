import { TestBed } from '@angular/core/testing';
import { SYNCABLE_STORES } from '@core/sync/syncable-store';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    // jsdom n'implémente pas matchMedia (requis par ThemeService à la construction).
    window.matchMedia ??= ((query: string) =>
      ({
        matches: false,
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        onchange: null,
        dispatchEvent: () => false,
      }) as MediaQueryList) as typeof window.matchMedia;
    await TestBed.configureTestingModule({
      imports: [App],
      // App -> WebSocketService -> SYNCABLE_STORES : registre vide suffit pour le test de création.
      providers: [{ provide: SYNCABLE_STORES, useValue: [] }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
