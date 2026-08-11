import { TestBed } from '@angular/core/testing';
import { Showcase } from './showcase';

/**
 * Tâche « Showcase Foundations — afficher TOUTE la palette, groupée ».
 *
 * Comportement observable verrouillé ici :
 *  1. Foundations rend chaque famille de tokens dans son propre bloc (card + titre), une pastille
 *     par token → la palette est bien AFFICHÉE et GROUPÉE.
 *  2. Chaque pastille résout `var(--token)` (varName bien formé) → « aucun hex en dur » (cf. commit).
 *  3. La page couvre TOUTE la palette : chaque token de `_colors.scss` (primitives --c-x,
 *     sémantiques --app-x, macros --macro-x, micros --micro-x, origines --food-x, groupes
 *     --food-grp-x, boutons --app-btn-x et --app-on-accent) apparaît. Garde anti-oubli si on
 *     retire une famille/un token du showcase.
 *
 * Le set canonique est mirroré depuis `designsystem/theme/_colors.scss` (le builder de test ne
 * permet pas de lire le .scss du disque : pas de @types/node dans tsconfig.spec). Même convention
 * que le reste du repo, qui mirrore déjà les tokens cross-stack (Android ↔ web). Si un token est
 * ajouté dans _colors.scss, l'ajouter aussi ici ET dans le showcase.
 */

interface Swatch {
  name: string;
  varName: string;
  label?: string;
}
interface SwatchGroup {
  title: string;
  swatches: Swatch[];
}
interface ShowcaseInternal {
  swatchGroups: SwatchGroup[];
}

// — Palette canonique (mirror 1:1 de _colors.scss). Union dark + light (light ne fait que
//   redéfinir un sous-ensemble déjà présent en dark). —
const CANONICAL_TOKENS: string[] = [
  // Primitives --c-*
  '--c-first-blue', '--c-second-blue', '--c-third-blue', '--c-light-gray-blue', '--c-gray-blue',
  '--c-box-blue', '--c-light-green', '--c-medium-green', '--c-yellow-medium', '--c-orange-medium',
  '--c-dark-orange', '--c-red-medium', '--c-red-dark', '--c-blue-medium', '--c-dark-gray',
  '--c-light-blue', '--c-button-primary', '--c-light-purple', '--c-medium-purple', '--c-turquoise',
  '--c-blue-background', '--c-ui-showcase-card',
  // Macros --macro-*
  '--macro-kcal', '--macro-protein', '--macro-carbs', '--macro-fat', '--macro-fiber',
  '--macro-sugar',
  // Micros --micro-*
  '--micro-mineral', '--micro-vitamin',
  // Origines / règnes --food-*
  '--food-animal', '--food-vegetal', '--food-supplement', '--food-other',
  // Groupes d'aliment --food-grp-* (17)
  '--food-grp-viande-rouge', '--food-grp-viande-blanche', '--food-grp-poisson',
  '--food-grp-fruits-de-mer', '--food-grp-oeuf', '--food-grp-laitage', '--food-grp-legume',
  '--food-grp-fruit', '--food-grp-legumineuse', '--food-grp-cereale-feculent',
  '--food-grp-noix-graine', '--food-grp-matiere-grasse', '--food-grp-produit-sucre',
  '--food-grp-boisson', '--food-grp-plat-compose', '--food-grp-complement', '--food-grp-autre',
  // Sémantiques --app-*
  '--app-bg-screen', '--app-bg-surface', '--app-bg-recessed', '--app-bg-bottom-nav',
  '--app-bg-button', '--app-selected-fill', '--app-primary-action', '--app-text-primary',
  '--app-text-secondary', '--app-text-tertiary', '--app-text-on-selected', '--app-accent-text',
  '--app-divider', '--app-divider-strong', '--app-priority-high', '--app-priority-medium',
  '--app-priority-low', '--app-task-row-green-bg', '--app-task-row-green-name-box',
  '--app-task-row-orange-bg', '--app-task-row-orange-name-box', '--app-snackbar-success',
  '--app-snackbar-warning', '--app-snackbar-error',
  // Boutons d'action
  '--app-btn-danger-bg', '--app-btn-danger-fg', '--app-on-accent',
];

function makeFixture() {
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
  const fixture = TestBed.createComponent(Showcase);
  fixture.detectChanges(); // catégorie par défaut = 'foundations'
  return fixture;
}

function groups(fixture: ReturnType<typeof makeFixture>): SwatchGroup[] {
  return (fixture.componentInstance as unknown as ShowcaseInternal).swatchGroups;
}

describe('Showcase Foundations — toute la palette, groupée', () => {
  it('rend chaque famille dans un bloc titré, une pastille par token (groupé + rendu DOM)', () => {
    const fixture = makeFixture();
    const data = groups(fixture);

    // Données structurées : >= 1 groupe par famille, chacun titré et non vide.
    expect(data.length).toBeGreaterThanOrEqual(7);
    for (const g of data) {
      expect(g.title.trim().length).toBeGreaterThan(0);
      expect(g.swatches.length).toBeGreaterThan(0);
    }

    const el: HTMLElement = fixture.nativeElement;
    // Un titre <h2> rendu par groupe, dans l'ordre, avec le bon libellé.
    const renderedTitles = Array.from(el.querySelectorAll('section.card h2')).map((h) =>
      (h.textContent ?? '').trim(),
    );
    expect(renderedTitles).toEqual(data.map((g) => g.title));

    // Une pastille rendue par token.
    const totalSwatches = data.reduce((n, g) => n + g.swatches.length, 0);
    expect(el.querySelectorAll('.swatch__chip').length).toBe(totalSwatches);

    // Les 7 familles attendues sont chacune représentée par >= 1 token.
    const allVars = data.flatMap((g) => g.swatches.map((s) => s.varName));
    expect(allVars.some((v) => v.startsWith('--c-'))).toBe(true); // primitives
    expect(allVars.some((v) => v.startsWith('--app-') && !v.startsWith('--app-btn-') && v !== '--app-on-accent')).toBe(true); // sémantiques
    expect(allVars.some((v) => v.startsWith('--macro-'))).toBe(true); // macros
    expect(allVars.some((v) => v.startsWith('--micro-'))).toBe(true); // micros
    expect(allVars.some((v) => v.startsWith('--food-') && !v.startsWith('--food-grp-'))).toBe(true); // origines
    expect(allVars.some((v) => v.startsWith('--food-grp-'))).toBe(true); // groupes d'aliment
    expect(allVars.some((v) => v.startsWith('--app-btn-') || v === '--app-on-accent')).toBe(true); // boutons
  });

  it('chaque pastille résout un var(--token) bien formé — aucun hex en dur, pas de doublon', () => {
    const data = groups(makeFixture());
    const allVars = data.flatMap((g) => g.swatches.map((s) => s.varName));

    for (const v of allVars) {
      // Custom property valide → le binding [style.background]="'var(' + varName + ')'" produit
      // toujours var(--token), jamais un #hex littéral.
      expect(v).toMatch(/^--[a-z0-9-]+$/);
      expect(v.includes('#')).toBe(false);
    }
    // Aucun token affiché deux fois (copier-coller).
    expect(new Set(allVars).size).toBe(allVars.length);
  });

  it('couvre TOUTE la palette de _colors.scss (chaque token canonique est affiché)', () => {
    const shown = new Set(groups(makeFixture()).flatMap((g) => g.swatches.map((s) => s.varName)));
    const missing = CANONICAL_TOKENS.filter((t) => !shown.has(t));
    expect(missing).toEqual([]);
  });
});
