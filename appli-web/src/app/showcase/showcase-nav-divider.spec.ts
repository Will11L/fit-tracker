import { TestBed } from '@angular/core/testing';
import { Showcase } from './showcase';

/**
 * Diagnostic : le divider sous le champ de recherche (nav de gauche) doit être présent dans le DOM.
 * Il est statique dans le template (hors @if), donc rendu quel que soit l'onglet — on teste sur
 * l'onglet par défaut (foundations) pour éviter de monter les charts echarts (crash jsdom).
 */
describe('Showcase — divider sous le search (présence DOM)', () => {
  it('rend .explorer__nav-divider comme enfant de .explorer__nav', () => {
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

    const fixture = TestBed.createComponent(Showcase);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const nav = el.querySelector('.explorer__nav');
    const divider = el.querySelector('.explorer__nav-divider');

    expect(nav).toBeTruthy();
    expect(divider).toBeTruthy();
    expect(divider?.parentElement?.classList.contains('explorer__nav')).toBe(true);
  });
});
