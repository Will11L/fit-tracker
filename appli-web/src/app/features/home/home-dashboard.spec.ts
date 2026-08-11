import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { SyncEngine } from '@core/sync/sync-engine';
import { MealRepository } from '@features/nutrition/meal.repository';
import { FoodRepository } from '@features/nutrition/food.repository';
import { NutritionGoalRepository } from '@features/nutrition/nutrition-goal.repository';
import { NutritionPage } from '@features/nutrition/nutrition-page';
import { HomePage } from './home-page';

/**
 * Verrouille le comportement OBSERVABLE de la tâche « Accueil — dashboard du jour 2 colonnes
 * (Séance + Journal nutrition sans calendrier), sans onglets » (commit ad41fa2). C'est un
 * réagencement structurel : la logique métier (today-session, journal nutrition) est déjà couverte
 * ailleurs — seul l'arbre rendu distingue cette tâche. On verrouille donc directement la STRUCTURE :
 *   1. l'Accueil rend 2 colonnes (plus de DualTabMenu), Séance embarquée à gauche / Nutrition
 *      embarquée à droite, les deux en mode `embedded` ;
 *   2. « Voir le programme » de la colonne Séance navigue vers /planning (le hub n'a plus d'onglet
 *      Programme à activer) ;
 *   3. NutritionPage en mode `embedded` masque la title bar + le calendrier mensuel (vue jour seule),
 *      et garde la vue complète (calendrier + title bar) en autonome sur /nutrition.
 *
 * On neutralise les composants enfants via CUSTOM_ELEMENTS_SCHEMA (pattern nutrition-stats-layout.spec)
 * : ils restent des éléments inertes (pas d'init Dexie / echarts), ce qui suffit pour inspecter la
 * structure et les bindings d'entrée/sortie passés aux enfants. Données vides : la structure ne dépend
 * pas des données.
 */

function mountHome(navigateByUrl: (url: string) => Promise<boolean>): HTMLElement {
  TestBed.configureTestingModule({
    imports: [HomePage],
    providers: [{ provide: Router, useValue: { navigateByUrl } }],
  });
  TestBed.overrideComponent(HomePage, {
    set: { imports: [], schemas: [CUSTOM_ELEMENTS_SCHEMA] },
  });
  const fixture = TestBed.createComponent(HomePage);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('HomePage — dashboard du jour 2 colonnes, sans onglets', () => {
  it('affiche un header « Accueil » (ScreenTitleBar) en haut de la page', () => {
    const el = mountHome(() => Promise.resolve(true));
    const bar = el.querySelector('app-screen-title-bar');
    expect(bar).toBeTruthy();
    expect(bar!.getAttribute('title')).toBe('Accueil');
  });

  it('rend exactement 2 colonnes : Séance (embedded) à gauche, Journal nutrition (embedded) à droite', () => {
    const el = mountHome(() => Promise.resolve(true));

    const cols = el.querySelectorAll('.dashboard > .dashboard__col');
    expect(cols.length).toBe(2);

    // Gauche = la séance du jour embarquée ; droite = le journal nutrition embarqué.
    const session = cols[0].querySelector('app-today-session-page') as (HTMLElement & { embedded?: boolean }) | null;
    const nutrition = cols[1].querySelector('app-nutrition-page') as (HTMLElement & { embedded?: boolean }) | null;
    expect(session).toBeTruthy();
    expect(nutrition).toBeTruthy();

    // Les deux enfants sont montés en mode embarqué (input `embedded=true`).
    expect(session!.embedded).toBe(true);
    expect(nutrition!.embedded).toBe(true);
  });

  it('n’a plus d’onglets (le DualTabMenu de l’ancien hub a disparu)', () => {
    const el = mountHome(() => Promise.resolve(true));
    expect(el.querySelector('app-dual-tab-menu')).toBeNull();
  });

  it('« Voir le programme » de la colonne Séance (output viewProgram) navigue vers /planning', () => {
    const navigateByUrl = vi.fn().mockResolvedValue(true);
    const el = mountHome(navigateByUrl);

    const session = el.querySelector('app-today-session-page')!;
    // L'enfant embarqué émet `viewProgram` au clic « Voir le programme » → le hub doit router /planning.
    session.dispatchEvent(new CustomEvent('viewProgram'));

    expect(navigateByUrl).toHaveBeenCalledWith('/planning');
  });
});

function mountNutrition(embedded: boolean): HTMLElement {
  TestBed.configureTestingModule({
    imports: [NutritionPage],
    providers: [
      { provide: SyncEngine, useValue: { syncAll: () => Promise.resolve() } },
      {
        provide: MealRepository,
        useValue: { meals: signal([]), entries: signal([]), presets: signal([]) },
      },
      { provide: FoodRepository, useValue: { foods: signal([]), portions: signal([]) } },
      {
        provide: NutritionGoalRepository,
        useValue: { goals: signal([]), activeGoalFor: () => null },
      },
    ],
  });
  // NO_ERRORS_SCHEMA (et non CUSTOM_ELEMENTS_SCHEMA) : NutritionPage porte la directive d'attribut
  // [appRevealIn] sur un <div> standard — une fois ses imports neutralisés, ce binding doit être toléré.
  TestBed.overrideComponent(NutritionPage, {
    set: { imports: [], schemas: [NO_ERRORS_SCHEMA] },
  });
  const fixture = TestBed.createComponent(NutritionPage);
  fixture.componentRef.setInput('embedded', embedded);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('NutritionPage — mode embarqué (journal du jour sans calendrier) vs autonome', () => {
  it('embarqué (dashboard Accueil) : masque la title bar « Nutrition » + le calendrier mensuel, applique .page--embedded, garde le résumé du jour', () => {
    const el = mountNutrition(true);

    // Le marqueur de mode embarqué est posé sur la page…
    expect(el.querySelector('.page.page--embedded')).toBeTruthy();
    // …la title bar et le calendrier mensuel sont retirés…
    expect(el.querySelector('app-screen-title-bar')).toBeNull();
    expect(el.querySelector('.cal')).toBeNull();
    // …mais le bandeau résumé du jour (vue jour réutilisée) reste.
    expect(el.querySelector('.banner')).toBeTruthy();
  });

  it('autonome (/nutrition) : conserve la title bar + le calendrier mensuel (vue complète)', () => {
    const el = mountNutrition(false);

    expect(el.querySelector('.page--embedded')).toBeNull();
    expect(el.querySelector('app-screen-title-bar')).toBeTruthy();
    expect(el.querySelector('.cal')).toBeTruthy();
  });

  it('embarqué (Accueil) : pas de sélecteur de jour (.daynav) ; le résumé du jour (.banner) reste affiché', () => {
    // Le sélecteur ← date → a été retiré de l'Accueil (qui affiche par définition aujourd'hui) :
    // .daynav n'est plus rendu (en aucun mode), mais le bandeau résumé du jour reste présent.
    const el = mountNutrition(true);

    expect(el.querySelector('.daynav')).toBeNull();
    expect(el.querySelector('.banner')).toBeTruthy();
  });
});
