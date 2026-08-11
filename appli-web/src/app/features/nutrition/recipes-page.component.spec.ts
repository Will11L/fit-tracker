import { TestBed } from '@angular/core/testing';
import { Signal, signal } from '@angular/core';
import { SyncEngine } from '@core/sync/sync-engine';
import { FoodRepository } from './food.repository';
import { MealRepository } from './meal.repository';
import { RecipeRepository } from './recipe.repository';
import { RecipesPage } from './recipes-page';

/**
 * Câblage du toggle « représentation de la répartition macro » du détail Recettes (tâche « cercle
 * (donut) + radar (toggle dans le détail) »). Le helper de données du donut (macroEnergyShares) est
 * déjà couvert par nutrition-summary-panel.spec ; on verrouille ici le comportement OBSERVABLE que le
 * helper n'exprime pas :
 *  - le toggle propose exactement Cercle / Radar / Barre et pilote la prop `display` du panneau ;
 *  - le défaut est « Radar » (app-wide : radar = vue ouverte par défaut sur les toggles cercle/radar).
 * On instancie la classe dans un contexte d'injection (repos + SyncEngine stubés) SANS monter le
 * template — le donut/radar instancie ECharts qui n'a pas de contexte canvas en jsdom (cf.
 * donut-chart.spec / radar-chart.spec).
 */

interface PageProbe {
  viewSegments: { value: string; icon: string; description: string }[];
  detailView: Signal<string>;
  setDetailView(view: string): void;
}

function makePage(): { page: RecipesPage; probe: PageProbe } {
  TestBed.configureTestingModule({
    providers: [
      { provide: SyncEngine, useValue: { syncAll: () => Promise.resolve() } },
      { provide: FoodRepository, useValue: { foods: signal([]) } },
      {
        provide: MealRepository,
        useValue: { meals: signal([]), presets: signal([]), entries: signal([]) },
      },
      { provide: RecipeRepository, useValue: { recipes: signal([]), ingredients: signal([]) } },
    ],
  });
  const page = TestBed.runInInjectionContext(() => new RecipesPage());
  return { page, probe: page as unknown as PageProbe };
}

describe('RecipesPage — toggle Cercle/Radar/Barre du détail (câblage de la page)', () => {
  it('propose exactement Radar / Cercle (donut) / Barre, dans cet ordre (radar 1er = mode par défaut), avec une icône chacun', () => {
    const { probe } = makePage();
    expect(probe.viewSegments.map((s) => s.value)).toEqual(['radar', 'donut', 'bar']);
    expect(probe.viewSegments.map((s) => s.description)).toEqual(['Radar', 'Cercle', 'Barre']);
    // Chaque segment porte une icône Material non vide (pilote le rendu du toggle).
    expect(probe.viewSegments.every((s) => s.icon.length > 0)).toBe(true);
  });

  it('démarre sur « Radar » par défaut (app-wide : radar = vue ouverte sur les toggles cercle/radar)', () => {
    const { probe } = makePage();
    expect(probe.detailView()).toBe('radar');
  });

  it('setDetailView bascule la prop display vers donut puis barre puis revient à radar', () => {
    const { probe } = makePage();
    probe.setDetailView('donut');
    expect(probe.detailView()).toBe('donut');
    probe.setDetailView('bar');
    expect(probe.detailView()).toBe('bar');
    probe.setDetailView('radar');
    expect(probe.detailView()).toBe('radar');
  });
});
