import { CUSTOM_ELEMENTS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SyncEngine } from '@core/sync/sync-engine';
import { MealRepository } from './meal.repository';
import { FoodRepository } from './food.repository';
import { NutritionGoalRepository } from './nutrition-goal.repository';
import { NutritionStatsPage } from './nutrition-stats-page';

/**
 * Layout de la page Stats Nutrition (tâche « Origine + Variété + Profil macros sur UNE ligne, alignés
 * avec la grille du dessous »). C'est un changement purement structurel (DOM/grille) : les builders et
 * computeds, déjà couverts par les autres nutrition-stats-*.spec, ne le distinguent pas. On verrouille
 * donc l'invariant OBSERVABLE de la tâche directement sur l'arbre DOM rendu :
 *   1. la ligne de synthèse `.toprow` regroupe 3 panneaux (Origine + Variété + Profil macros radar) ;
 *   2. le radar « profil macros » est dans cette ligne (3e colonne), plus en bloc isolé sous la grille ;
 *   3. le 6e graphe (% de l'objectif) est descendu DANS la grille `.cards` (5 cartes macros + 6e = 6
 *      cellules), il n'est plus posé à part au-dessus.
 *
 * Les graphes ECharts crashent en jsdom (pas de canvas 2D — cf. radar-chart.spec). On monte donc la
 * page en neutralisant ses composants enfants via CUSTOM_ELEMENTS_SCHEMA : ils restent des éléments
 * inertes (`<app-radar-chart>`, `<app-multi-line-chart>`…) sans init echarts, ce qui suffit pour
 * inspecter la STRUCTURE de la grille. Données vides : la structure (3 panneaux / 6 cellules) ne
 * dépend pas des données (les 5 cartes macros + le 6e graphe sont toujours présents).
 */

function mountStatsPage(): HTMLElement {
  TestBed.configureTestingModule({
    imports: [NutritionStatsPage],
    providers: [
      { provide: SyncEngine, useValue: { syncAll: () => Promise.resolve() } },
      { provide: MealRepository, useValue: { meals: signal([]), entries: signal([]) } },
      { provide: FoodRepository, useValue: { foods: signal([]) } },
      { provide: NutritionGoalRepository, useValue: { goals: signal([]) } },
    ],
  });
  // Rend les composants graphes inertes (sinon echarts.init crashe en jsdom) tout en gardant le
  // template réel → on inspecte la vraie structure de grille de la page.
  TestBed.overrideComponent(NutritionStatsPage, {
    set: { imports: [], schemas: [CUSTOM_ELEMENTS_SCHEMA] },
  });
  const fixture = TestBed.createComponent(NutritionStatsPage);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('NutritionStatsPage — layout ligne de synthèse + grille (tâche « sur une ligne, aligné »)', () => {
  it('la ligne de synthèse `.toprow` contient exactement 3 panneaux (Origine + Variété + Profil macros)', () => {
    const el = mountStatsPage();

    const toprow = el.querySelector('.toprow');
    expect(toprow).toBeTruthy();

    // Origine + Variété + Profil macros = 3 panneaux sur la même ligne.
    expect(toprow!.querySelectorAll(':scope > section.catpanel').length).toBe(3);

    // En mode Radar (défaut), les 3 panneaux affichent un radar (le sélecteur partagé pilote les 3).
    expect(toprow!.querySelectorAll('app-radar-chart').length).toBe(3);
  });

  it('le 6e graphe (% objectif) est descendu dans `.cards` → 6 cellules (5 macros + 1), plus en bloc isolé', () => {
    const el = mountStatsPage();

    const cards = el.querySelector('.cards');
    expect(cards).toBeTruthy();

    // 5 cartes macros (kcal/glucides/lipides/protéines/fibres) + le 6e graphe % objectif = 6 cellules.
    expect(cards!.querySelectorAll(':scope > article.card').length).toBe(6);

    // Le graphe multi-lignes « % de l'objectif » vit DANS la grille du dessous…
    expect(cards!.querySelector('app-multi-line-chart')).toBeTruthy();
    // …et nulle part ailleurs (il n'est plus posé en standalone au-dessus / dans la ligne de synthèse).
    expect(el.querySelectorAll('app-multi-line-chart').length).toBe(1);
    expect(el.querySelector('.toprow app-multi-line-chart')).toBeNull();
  });
});

/**
 * Réagencements DOM purs de la ligne de synthèse Origine/Variété (mêmes builders/computeds qu'avant →
 * seul l'arbre rendu les distingue) : toggle Cercle/Radar partagé en barre d'outils, graphes
 * auto-étiquetés (plus de légende séparée), taille homogène, et ligne diversité/monotonie posée en
 * légende OVERLAY dans le panneau Variété (comme « moyenne / jour » du Profil macros). On verrouille les
 * invariants OBSERVABLES de chacun, données vides (la structure ne dépend pas des données : Origine rend
 * toujours 4 règnes, les panneaux/toggles sont toujours là).
 */
describe('NutritionStatsPage — réagencement Origine/Variété (toggle partagé / légende dans le graphe / diversité-monotonie en overlay)', () => {
  it('un seul toggle Cercle/Radar PARTAGÉ dans la barre d’outils (ligne des périodes), aucun dans les panneaux', () => {
    const el = mountStatsPage();

    // Un seul sélecteur sur toute la page (partagé Origine + Variété).
    expect(el.querySelectorAll('app-segmented-icon-toggle').length).toBe(1);
    // Il vit dans la barre d’outils, à droite des chips de période…
    expect(el.querySelectorAll('.toolbar app-segmented-icon-toggle').length).toBe(1);
    // …et plus dans aucun panneau (ni pied ni en-tête).
    expect(el.querySelectorAll('.catpanel app-segmented-icon-toggle').length).toBe(0);
  });

  it('plus de légende séparée : le graphe s’auto-étiquette (donut en Cercle, axes « Nom X % » en Radar)', () => {
    const el = mountStatsPage();

    // Les 2 panneaux à toggle (Origine + Variété) ont leur graphe dans le corps, SANS légende séparée
    // (le donut s'auto-étiquette en Cercle, le radar via ses axes « Nom X % » en Radar).
    const bodies = el.querySelectorAll('.toprow .catpanel__body');
    expect(bodies.length).toBe(2);
    for (const body of Array.from(bodies)) {
      expect(body.querySelector(':scope > .chartwrap')).toBeTruthy();
      expect(body.querySelector('ul.legend')).toBeNull();
    }
  });

  it('la ligne diversité/monotonie est une légende en overlay DANS le panneau Variété (comme « moyenne / jour » du Profil macros), plus en encart .infoline', () => {
    const el = mountStatsPage();

    // L'ancien encart .infoline (cadre coloré sous le graphe / sous le range picker) a disparu partout.
    expect(el.querySelectorAll('.infoline').length).toBe(0);

    // La légende Variété est une caption dans son corps de panneau. Origine en porte désormais une
    // aussi (légende « règne dominant », ajoutée pour aligner la hauteur des cadres de la ligne de
    // synthèse) → 2 captions de .catpanel__body. Données vides → texte « Aucun aliment ».
    const captions = el.querySelectorAll('.toprow .catpanel__body .chart-caption');
    expect(captions.length).toBe(2);
    for (const c of Array.from(captions)) {
      expect(c.textContent).toContain('Aucun aliment sur cette période.');
    }

    // Le corps Variété est positionné (ancre de l'overlay) via le modifieur dédié.
    expect(el.querySelector('.toprow .catpanel__body--overlay .chart-caption')).toBeTruthy();
  });

  it('taille « = grille du dessous » : les 3 graphes de la ligne de synthèse partagent la même hauteur (catChartHeight)', () => {
    const el = mountStatsPage();

    // Défaut RADAR → Origine + Variété + Profil = 3 radars, tous bindés sur catChartHeight.
    const radars = Array.from(el.querySelectorAll('.toprow app-radar-chart')) as Array<
      HTMLElement & { height?: number }
    >;
    expect(radars.length).toBe(3);

    const heights = radars.map((r) => r.height);
    // Toutes définies, identiques (homogènes avec le Profil macros), et = la constante de page (300).
    expect(heights.every((h) => h === heights[0])).toBe(true);
    expect(heights[0]).toBe(300);
  });
});
