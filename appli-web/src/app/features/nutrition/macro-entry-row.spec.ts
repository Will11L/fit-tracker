import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MacroEntryRow, type MacroEntryRowData } from './macro-entry-row';
import type { MicroLineItem } from './micro-colors';

/**
 * Ligne aliment / ingrédient partagée (extraite des cards repas du journal, réutilisée par la liste
 * d'ingrédients des recettes). On verrouille le comportement OBSERVABLE piloté par `data` : macros
 * colorées (part « F » conditionnée par `fiber`), chevron + dépli des micros présents seulement, et
 * projection du slot `[trailing]` (grammes, menu ⋮, contrôles d'édition selon le contexte appelant).
 */

const MICROS: MicroLineItem[] = [
  { short: 'Fe', value: 2.8, unit: 'mg', color: 'var(--micro-mineral)' },
  { short: 'Ca', value: 32, unit: 'mg', color: 'var(--micro-mineral)' },
];

function data(over: Partial<MacroEntryRowData> = {}): MacroEntryRowData {
  return { name: 'Avoine, crue', kcal: 195, carbs: 33, fat: 3.4, protein: 6.8, fiber: 5, micros: MICROS, ...over };
}

@Component({
  imports: [MacroEntryRow],
  template: `<app-macro-entry-row [data]="d" [divider]="divider"
    ><span trailing class="trail">120 g</span></app-macro-entry-row
  >`,
})
class Host {
  d: MacroEntryRowData = data();
  divider = true;
}

function mount(over: Partial<MacroEntryRowData> = {}, divider = true): { el: HTMLElement; fixture: ReturnType<typeof TestBed.createComponent<Host>> } {
  const fixture = TestBed.createComponent(Host);
  fixture.componentInstance.d = data(over);
  fixture.componentInstance.divider = divider;
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, fixture };
}

describe('MacroEntryRow', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [Host] }));

  it('affiche le nom + les 5 parts de macros (kcal/G/L/P/F) quand fiber est défini', () => {
    const { el } = mount();
    expect(el.querySelector('.mer__name')?.textContent).toContain('Avoine, crue');
    // 5 spans colorés (kcal, G, L, P, F) dans la ligne de macros.
    expect(el.querySelectorAll('.mer__macros > span').length).toBe(5);
    expect(el.querySelector('.mer__macros')?.textContent).toContain('195 kcal');
  });

  it('masque la part « F » quand fiber est null (4 parts)', () => {
    const { el } = mount({ fiber: null });
    expect(el.querySelectorAll('.mer__macros > span').length).toBe(4);
  });

  it('projette le contenu [trailing] (grammes / menu) avant le chevron', () => {
    const { el } = mount();
    expect(el.querySelector('.mer__row .trail')?.textContent).toContain('120 g');
  });

  it('un seul bouton (le chevron) quand il y a des micros ; aucun sinon', () => {
    expect(mount().el.querySelectorAll('app-action-icon-button button').length).toBe(1);
    expect(mount({ micros: [] }).el.querySelectorAll('app-action-icon-button button').length).toBe(0);
  });

  it('le chevron déplie / replie les micros', () => {
    const { el, fixture } = mount();
    expect(el.querySelector('.mer__micros-reveal--open')).toBeNull();
    (el.querySelector('app-action-icon-button button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('.mer__micros-reveal--open')).toBeTruthy();
    expect(el.querySelector('.mer__micros')?.textContent).toContain('Fe 2.8 mg');
  });

  it('le filet de séparation suit l’entrée divider', () => {
    expect(mount({}, true).el.querySelector('.mer--divider')).toBeTruthy();
    expect(mount({}, false).el.querySelector('.mer--divider')).toBeNull();
  });
});
