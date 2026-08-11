import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LocalFood } from '@core/models/food.model';
import { LocalMealEntry } from '@core/models/meal-entry.model';
import { SyncEngine } from '@core/sync/sync-engine';
import { FoodRepository } from './food.repository';
import { MealRepository } from './meal.repository';
import { FoodCataloguePage } from './food-catalogue-page';

// Orchestration master/détail de la page Catalogue (refonte T5). On ne re-teste pas la logique pure
// (buildFoodGroups / seuils — couverte par food-catalogue.spec) ni le panneau de détail
// (food-detail-panel.spec) : on couvre ce qui n'existe QUE dans la page —
//   (1) l'ordre de la colonne master (Récents → Favoris → Tous), volontairement différent de
//       l'ordre rendu par buildFoodGroups (Favoris d'abord) ;
//   (2) le câblage du panneau de filtres → liste : une valeur saisie devient un seuil actif,
//       l'opérateur ≥/≤ s'applique, le compteur du bouton suit, et « Réinitialiser » rétablit la liste.
// Repos mockés (signals) : la page est présentationnelle au-dessus de FoodRepository/MealRepository.
// matchMedia stubé matches:false → mode étroit (pas de colonne détail), on reste sur la liste master.

function food(over: Partial<LocalFood>): LocalFood {
  return {
    uuid: 'f1',
    userId: 1,
    name: 'Avoine',
    brand: null,
    source: 'CUSTOM',
    sourceRef: null,
    foodGroup: null,
    kcalPer100g: 380,
    proteinPer100g: 13,
    carbsPer100g: 60,
    fatPer100g: 7,
    fiberPer100g: null,
    sugarPer100g: null,
    satFatPer100g: null,
    saltPer100g: null,
    ironPer100g: null,
    calciumPer100g: null,
    magnesiumPer100g: null,
    zincPer100g: null,
    potassiumPer100g: null,
    sodiumPer100g: null,
    vitaminCPer100g: null,
    vitaminDPer100g: null,
    vitaminB12Per100g: null,
    vitaminAPer100g: null,
    isFavorite: false,
    archived: false,
    isWater: false,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

function entry(foodUUID: string, updatedAt: string): LocalMealEntry {
  return {
    uuid: 'e-' + foodUUID,
    mealUUID: 'm1',
    foodUUID,
    recipeUUID: null,
    displayName: 'X',
    quantityG: 100,
    portionLabel: null,
    kcalPer100g: 100,
    proteinPer100g: 0,
    carbsPer100g: 0,
    fatPer100g: 0,
    fiberPer100g: null,
    sugarPer100g: null,
    satFatPer100g: null,
    saltPer100g: null,
    ironPer100g: null,
    calciumPer100g: null,
    magnesiumPer100g: null,
    zincPer100g: null,
    potassiumPer100g: null,
    sodiumPer100g: null,
    vitaminCPer100g: null,
    vitaminDPer100g: null,
    vitaminB12Per100g: null,
    vitaminAPer100g: null,
    updatedAt,
    synced: true,
    pendingDeletion: false,
  };
}

function setup(foods: LocalFood[], entries: LocalMealEntry[] = []) {
  // jsdom n'implémente pas matchMedia (le constructeur de la page le lit pour le mode large/étroit).
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

  // jsdom n'implémente pas IntersectionObserver (le constructeur en crée un pour le chargement
  // progressif) → stub no-op : aucun test ne simule le scroll via l'observer, ils pilotent
  // directement visibleCount.
  (globalThis as { IntersectionObserver?: unknown }).IntersectionObserver ??= class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): [] {
      return [];
    }
  } as unknown as typeof IntersectionObserver;

  const foodRepo = {
    foods: signal<LocalFood[]>(foods),
    portions: signal([]),
    update: () => Promise.resolve(),
    create: () => Promise.resolve('uuid'),
    remove: () => Promise.resolve(),
    addPortion: () => Promise.resolve('uuid'),
    removePortion: () => Promise.resolve(),
  };
  const mealRepo = { entries: signal<LocalMealEntry[]>(entries) };
  const sync = { syncAll: () => Promise.resolve() };

  TestBed.configureTestingModule({
    imports: [FoodCataloguePage],
    providers: [
      { provide: FoodRepository, useValue: foodRepo },
      { provide: MealRepository, useValue: mealRepo },
      { provide: SyncEngine, useValue: sync },
    ],
  });
  const fixture = TestBed.createComponent(FoodCataloguePage);
  fixture.detectChanges();
  return fixture;
}

/** Noms des aliments rendus dans la colonne master, dans l'ordre du DOM. */
function masterNames(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('.md__master .frow__name')).map((n) =>
    (n.textContent ?? '').trim(),
  );
}

/** Titres des sections (titled-divider) de la LISTE master, dans l'ordre du DOM. Le panneau de
 *  filtres (app-filter-panel, collapse animé → contenu toujours au DOM) a ses propres dividers
 *  (Catégorie / Macros / Vitamines & minéraux) : exclus, on ne mesure que les blocs de la liste. */
function masterTitles(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('.md__master app-titled-divider .td__title'))
    .filter((n) => !n.closest('app-filter-panel'))
    .map((n) => (n.textContent ?? '').trim());
}

/** Bouton « icône + texte » dont le libellé contient `label`. */
function clickButtonByLabel(el: HTMLElement, label: string): void {
  const btn = Array.from(el.querySelectorAll('app-action-icon-with-text-button button.aitb')).find(
    (b) => (b.querySelector('.aitb__label')?.textContent ?? '').trim().includes(label),
  ) as HTMLButtonElement | undefined;
  if (!btn) throw new Error(`Bouton « ${label} » introuvable`);
  btn.click();
}

function labelOfButton(el: HTMLElement, contains: string): string {
  const btn = Array.from(el.querySelectorAll('app-action-icon-with-text-button button.aitb')).find(
    (b) => (b.querySelector('.aitb__label')?.textContent ?? '').trim().includes(contains),
  );
  return (btn?.querySelector('.aitb__label')?.textContent ?? '').trim();
}

/** Saisit `value` dans la ligne de seuil (FilterThresholdRow) du nutriment `abbr` (ex. « Prot. » —
 *  libellés abrégés depuis les filtres compacts 49bf9d0f). */
function setFilterValue(el: HTMLElement, abbr: string, value: string): void {
  const row = Array.from(el.querySelectorAll('app-filter-threshold-row .ftr')).find(
    (r) => (r.querySelector('.ftr__label')?.textContent ?? '').trim() === abbr,
  ) as HTMLElement | undefined;
  if (!row) throw new Error(`Ligne de filtre « ${abbr} » introuvable`);
  const input = row.querySelector('input.ftr__input') as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

/** Sélectionne l'opérateur (≥ / ≤) dans la ligne de seuil du nutriment donné (libellé abrégé). */
function setFilterOp(el: HTMLElement, abbr: string, op: '≥' | '≤'): void {
  const row = Array.from(el.querySelectorAll('app-filter-threshold-row .ftr')).find(
    (r) => (r.querySelector('.ftr__label')?.textContent ?? '').trim() === abbr,
  ) as HTMLElement | undefined;
  if (!row) throw new Error(`Ligne de filtre « ${abbr} » introuvable`);
  const btn = Array.from(row.querySelectorAll('.ftr__op')).find(
    (b) => (b.textContent ?? '').trim() === op,
  ) as HTMLButtonElement | undefined;
  if (!btn) throw new Error(`Opérateur « ${op} » introuvable`);
  btn.click();
}

describe('FoodCataloguePage — colonne master ordonnée Récents → Favoris → Tous', () => {
  it('réordonne les blocs (Récents avant Favoris), à l’inverse de buildFoodGroups', () => {
    const foods = [
      food({ uuid: 'f1', name: 'Avoine', isFavorite: true }), // Favoris
      food({ uuid: 'f2', name: 'Banane' }), // Récent (entry ci-dessous)
      food({ uuid: 'f3', name: 'Poulet' }), // Tous
    ];
    const fixture = setup(foods, [entry('f2', '2026-06-10T10:00:00Z')]);
    const el = fixture.nativeElement as HTMLElement;

    // Ordre des sections : Récents d'abord (décision UX de la page), puis Favoris, puis Tous.
    expect(masterTitles(el)).toEqual(['Récents', 'Favoris', 'Tous']);
    // Et l'ordre des lignes suit : Banane (récent) → Avoine (favori) → Poulet (reste).
    expect(masterNames(el)).toEqual(['Banane', 'Avoine', 'Poulet']);
  });
});

describe('FoodCataloguePage — panneau de filtres câblé sur la liste', () => {
  const poulet = food({ uuid: 'p1', name: 'Poulet', source: 'CUSTOM', kcalPer100g: 165, proteinPer100g: 27 });
  const riz = food({ uuid: 'r1', name: 'Riz', source: 'CUSTOM', kcalPer100g: 130, proteinPer100g: 7 });

  it('saisir « Protéines ≥ 20 » réduit la liste aux aliments qui passent ; ≤ inverse ; reset rétablit', () => {
    const fixture = setup([poulet, riz]);
    const el = fixture.nativeElement as HTMLElement;

    // Sans filtre : les deux aliments visibles (bloc « Tous »), bouton « Filtres » sans compteur.
    expect(masterNames(el)).toEqual(['Poulet', 'Riz']);
    expect(labelOfButton(el, 'Filtres')).toBe('Filtres');

    // Ouvre le panneau de filtres puis saisit un seuil Protéines (« Prot. ») ≥ 20 (opérateur ≥ par défaut).
    clickButtonByLabel(el, 'Filtres');
    fixture.detectChanges();
    setFilterValue(el, 'Prot.', '20');
    fixture.detectChanges();

    expect(masterNames(el)).toEqual(['Poulet']); // 27 ≥ 20 ; Riz (7) exclu
    expect(labelOfButton(el, 'Filtres')).toBe('Filtres (1)'); // le compteur reflète le seuil actif

    // Bascule l'opérateur en ≤ (seuil 20 conservé) → seul Riz (7 ≤ 20) passe.
    setFilterOp(el, 'Prot.', '≤');
    fixture.detectChanges();
    expect(masterNames(el)).toEqual(['Riz']);

    // « Réinitialiser » efface les seuils → retour à la liste groupée complète.
    clickButtonByLabel(el, 'Réinitialiser');
    fixture.detectChanges();
    expect(masterNames(el)).toEqual(['Poulet', 'Riz']);
    expect(labelOfButton(el, 'Filtres')).toBe('Filtres');
  });

  it('cliquer un opérateur ≥ SANS saisir de valeur ne crée aucun seuil (régression parseMacro vide)', () => {
    const fixture = setup([poulet, riz]);
    const el = fixture.nativeElement as HTMLElement;

    clickButtonByLabel(el, 'Filtres');
    fixture.detectChanges();
    setFilterOp(el, 'Prot.', '≥'); // opérateur sélectionné mais champ vide
    fixture.detectChanges();

    // Avant le fix : parseMacro('') === 0 → seuil « ≥ 0 » actif → « Filtres (1) » + vue groupée effondrée.
    // Après : saisie vide = pas de seuil → compteur absent, liste groupée intacte.
    expect(labelOfButton(el, 'Filtres')).toBe('Filtres');
    expect(masterTitles(el)).toEqual(['Tous']);
    expect(masterNames(el)).toEqual(['Poulet', 'Riz']);
  });
});

describe('FoodCataloguePage — éditeur : nom + macros + catégorie obligatoire', () => {
  type EditorCmp = {
    openCreate(): void;
    eName: { set(v: string): void };
    eKcal: { set(v: string): void };
    eProtein: { set(v: string): void };
    eCarbs: { set(v: string): void };
    eFat: { set(v: string): void };
    eGroupLabel: { set(v: string | null): void };
    eSupplementKind: { set(v: 'COMPLEMENT_MACRO' | 'COMPLEMENT_MICRO'): void };
    isSupplementSelected(): boolean;
    eGroupCode(): string | null;
    editorValid(): boolean;
  };

  it('nom + 4 macros mais catégorie vide → editorValid faux (catégorie obligatoire)', () => {
    const fixture = setup([]);
    const cmp = fixture.componentInstance as unknown as EditorCmp;
    cmp.openCreate();
    cmp.eName.set('Yaourt nature');
    expect(cmp.editorValid()).toBe(false); // macros vides
    cmp.eKcal.set('60');
    cmp.eProtein.set('4');
    cmp.eCarbs.set('5');
    cmp.eFat.set('3');
    // Nom + 4 macros OK mais AUCUNE catégorie → toujours invalide (nouvelle règle S3).
    expect(cmp.editorValid()).toBe(false);
    expect(cmp.eGroupCode()).toBeNull();
    cmp.eGroupLabel.set('Laitages');
    expect(cmp.eGroupCode()).toBe('LAITAGE');
    expect(cmp.editorValid()).toBe(true); // catégorie renseignée → valide
  });

  it('catégorie « Compléments » → sous-choix MACRO/MICRO requis (code effectif suit le sous-choix)', () => {
    const fixture = setup([]);
    const cmp = fixture.componentInstance as unknown as EditorCmp;
    cmp.openCreate();
    cmp.eName.set('Whey');
    cmp.eKcal.set('380');
    cmp.eProtein.set('75');
    cmp.eCarbs.set('8');
    cmp.eFat.set('6');
    cmp.eGroupLabel.set('Compléments');
    expect(cmp.isSupplementSelected()).toBe(true);
    expect(cmp.eGroupCode()).toBe('COMPLEMENT_MACRO'); // défaut MACRO
    cmp.eSupplementKind.set('COMPLEMENT_MICRO');
    expect(cmp.eGroupCode()).toBe('COMPLEMENT_MICRO');
    expect(cmp.editorValid()).toBe(true);
  });
});

describe('FoodCataloguePage — facette catégorie câblée + badge', () => {
  const poulet = food({ uuid: 'p1', name: 'Poulet', foodGroup: 'VIANDE_BLANCHE' });
  const lentilles = food({ uuid: 'l1', name: 'Lentilles', foodGroup: 'LEGUMINEUSE' });

  it('filtrer par règne puis par groupe réduit la liste master ; reset rétablit', () => {
    const fixture = setup([poulet, lentilles]);
    const el = fixture.nativeElement as HTMLElement;
    const cmp = fixture.componentInstance as unknown as {
      realmFilter: { set(v: string): void };
      groupFilter: { set(v: string): void };
    };

    // Sans facette : les deux aliments visibles.
    expect(masterNames(el).sort()).toEqual(['Lentilles', 'Poulet']);

    // Facette règne « Végétale » → seules les Lentilles ; le compteur de filtres suit.
    cmp.realmFilter.set('Végétale');
    fixture.detectChanges();
    expect(masterNames(el)).toEqual(['Lentilles']);
    expect(labelOfButton(el, 'Filtres')).toBe('Filtres (1)');

    // On repasse à « Tous » sur le règne et on filtre par groupe « Viande blanche » → Poulet seul.
    cmp.realmFilter.set('Tous');
    cmp.groupFilter.set('Viande blanche');
    fixture.detectChanges();
    expect(masterNames(el)).toEqual(['Poulet']);

    // Réinitialiser via le bouton du panneau de filtres.
    clickButtonByLabel(el, 'Filtres'); // ouvre le panneau
    fixture.detectChanges();
    clickButtonByLabel(el, 'Réinitialiser');
    fixture.detectChanges();
    expect(masterNames(el).sort()).toEqual(['Lentilles', 'Poulet']);
    expect(labelOfButton(el, 'Filtres')).toBe('Filtres');
  });

  it('badge catégorie : label FR du groupe rendu sur la row (absent si foodGroup null)', () => {
    const fixture = setup([poulet, food({ uuid: 'x1', name: 'Sans groupe', foodGroup: null })]);
    const el = fixture.nativeElement as HTMLElement;
    const badges = Array.from(el.querySelectorAll('.md__master .frow__badge')).map((b) =>
      (b.textContent ?? '').trim(),
    );
    // Un seul badge (Poulet), « Viande blanche » ; l'aliment sans groupe n'en a pas.
    expect(badges).toEqual(['Viande blanche']);
  });
});

describe('FoodCataloguePage — détail réactif (dérivé du catalogue, fix aliment fantôme)', () => {
  it('le détail suit la modification de l’aliment et se vide s’il disparaît', () => {
    const fixture = setup([food({ uuid: 'a', name: 'Avoine' }), food({ uuid: 'b', name: 'Banane' })]);
    const cmp = fixture.componentInstance as unknown as {
      openDetail(f: LocalFood): void;
      detail(): LocalFood | null;
    };
    const repo = TestBed.inject(FoodRepository) as unknown as { foods: { set(v: LocalFood[]): void } };

    cmp.openDetail(food({ uuid: 'a', name: 'Avoine' }));
    expect(cmp.detail()?.uuid).toBe('a');

    // Modifié (sync/WS) → le détail reflète la nouvelle valeur (plus une copie figée au clic).
    repo.foods.set([food({ uuid: 'a', name: 'Avoine bio' }), food({ uuid: 'b', name: 'Banane' })]);
    expect(cmp.detail()?.name).toBe('Avoine bio');

    // Supprimé (ou exclu par une recherche) → le détail se vide tout seul.
    repo.foods.set([food({ uuid: 'b', name: 'Banane' })]);
    expect(cmp.detail()).toBeNull();
  });
});

describe('FoodCataloguePage — la catégorie obligatoire est persistée à l’enregistrement', () => {
  // Le reste du spec vérifie que l'éditeur est *bloqué* sans catégorie (editorValid faux). Ici on
  // ferme la boucle « catégorie obligatoire » côté écriture : à l'enregistrement, le code de groupe
  // choisi (et le sous-choix MACRO/MICRO d'un complément) doit effectivement atteindre le dépôt —
  // sinon un aliment serait créé sans groupe alors que l'UI le réclamait (régression silencieuse).
  type SaveCmp = {
    openCreate(): void;
    eName: { set(v: string): void };
    eKcal: { set(v: string): void };
    eProtein: { set(v: string): void };
    eCarbs: { set(v: string): void };
    eFat: { set(v: string): void };
    eGroupLabel: { set(v: string | null): void };
    eSupplementKind: { set(v: 'COMPLEMENT_MACRO' | 'COMPLEMENT_MICRO'): void };
    saveEditor(): void;
  };

  function captureCreate(): { get(): Record<string, unknown> | null } {
    const repo = TestBed.inject(FoodRepository) as unknown as {
      create: (p: Record<string, unknown>) => Promise<string>;
    };
    let captured: Record<string, unknown> | null = null;
    repo.create = (p) => {
      captured = p;
      return Promise.resolve('uuid');
    };
    return { get: () => captured };
  }

  function fillValidMacros(cmp: SaveCmp): void {
    cmp.eName.set('Yaourt nature');
    cmp.eKcal.set('60');
    cmp.eProtein.set('4');
    cmp.eCarbs.set('5');
    cmp.eFat.set('3');
  }

  it('create() reçoit le code de groupe choisi dans foodGroup (catégorie simple)', () => {
    const fixture = setup([]);
    const cmp = fixture.componentInstance as unknown as SaveCmp;
    const created = captureCreate();

    cmp.openCreate();
    fillValidMacros(cmp);
    cmp.eGroupLabel.set('Laitages');
    cmp.saveEditor();

    expect(created.get()).not.toBeNull();
    expect(created.get()!['foodGroup']).toBe('LAITAGE');
    expect(created.get()!['name']).toBe('Yaourt nature');
  });

  it('complément : le sous-choix MICRO est le code persisté (pas le label « Compléments »)', () => {
    const fixture = setup([]);
    const cmp = fixture.componentInstance as unknown as SaveCmp;
    const created = captureCreate();

    cmp.openCreate();
    fillValidMacros(cmp);
    cmp.eGroupLabel.set('Compléments');
    cmp.eSupplementKind.set('COMPLEMENT_MICRO');
    cmp.saveEditor();

    expect(created.get()!['foodGroup']).toBe('COMPLEMENT_MICRO');
  });
});

describe('FoodCataloguePage — options de facette limitées aux catégories présentes', () => {
  // S3 : les dropdowns Règne/Groupe ne listent que les catégories effectivement présentes dans le
  // catalogue (+ sentinelle « Tous ») — on ne peut pas filtrer vers un ensemble vide.
  type FacetCmp = { realmOptions(): string[]; groupOptions(): string[] };

  it('catalogue 100 % animal → seules les options Animale / Viande blanche (pas de végétal)', () => {
    const fixture = setup([food({ uuid: 'p1', name: 'Poulet', foodGroup: 'VIANDE_BLANCHE' })]);
    const cmp = fixture.componentInstance as unknown as FacetCmp;
    expect(cmp.realmOptions()).toEqual(['Tous', 'Animale']);
    expect(cmp.groupOptions()).toEqual(['Tous', 'Viande blanche']);
  });

  it('aliment sans groupe (legacy null) n’ajoute aucune option de facette (« Tous » seul)', () => {
    const fixture = setup([food({ uuid: 'x1', name: 'Sans groupe', foodGroup: null })]);
    const cmp = fixture.componentInstance as unknown as FacetCmp;
    expect(cmp.realmOptions()).toEqual(['Tous']);
    expect(cmp.groupOptions()).toEqual(['Tous']);
  });
});

describe('FoodCataloguePage — chargement progressif de la colonne master (anti-lag)', () => {
  // La liste ne doit jamais monter des centaines de rows d'un coup : seule une tranche est rendue,
  // la sentinelle (IntersectionObserver, stubé en test) dévoile la suite. Les tests pilotent
  // visibleCount directement (l'observer ne tourne pas sous jsdom).
  type ProgressiveCmp = {
    visibleCount: { (): number; set(v: number): void };
    hasMore(): boolean;
    search: { set(v: string): void };
  };

  function manyFoods(n: number): LocalFood[] {
    return Array.from({ length: n }, (_, i) =>
      food({ uuid: 'f' + i, name: 'Aliment ' + String(i).padStart(3, '0') }),
    );
  }

  it('ne rend que la première tranche, puis dévoile les tranches suivantes', () => {
    const fixture = setup(manyFoods(150));
    const el = fixture.nativeElement as HTMLElement;
    const cmp = fixture.componentInstance as unknown as ProgressiveCmp;

    // Premier rendu : une seule tranche (< 150) au DOM même si 150 aliments → pas de freeze.
    const batch = cmp.visibleCount();
    expect(batch).toBeLessThan(150);
    expect(masterNames(el).length).toBe(batch);
    expect(cmp.hasMore()).toBe(true);

    // Sentinelle vue (simulée) → tranche suivante dévoilée.
    cmp.visibleCount.set(batch * 2);
    fixture.detectChanges();
    expect(masterNames(el).length).toBe(Math.min(150, batch * 2));

    // Fenêtre ≥ total → tout est rendu, plus de sentinelle.
    cmp.visibleCount.set(150);
    fixture.detectChanges();
    expect(masterNames(el).length).toBe(150);
    expect(cmp.hasMore()).toBe(false);
  });

  it('petite liste (< une tranche) : tout est rendu, pas de sentinelle', () => {
    const fixture = setup(manyFoods(5));
    const el = fixture.nativeElement as HTMLElement;
    const cmp = fixture.componentInstance as unknown as ProgressiveCmp;
    expect(masterNames(el).length).toBe(5);
    expect(cmp.hasMore()).toBe(false);
  });

  it('réinitialise la fenêtre à la première tranche quand la recherche change', () => {
    const fixture = setup(manyFoods(150));
    const cmp = fixture.componentInstance as unknown as ProgressiveCmp;
    const batch = cmp.visibleCount();

    // L'utilisateur a fait défiler loin (fenêtre agrandie au max)…
    cmp.visibleCount.set(150);
    expect(cmp.visibleCount()).toBe(150);

    // …puis change la recherche → on repart de la première tranche (sinon 150 rows remontées d'un coup).
    cmp.search.set('Aliment 0');
    expect(cmp.visibleCount()).toBe(batch);
  });
});
