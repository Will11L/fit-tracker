import { TestBed } from '@angular/core/testing';
import { LocalFood } from '@core/models/food.model';
import { FoodListRow } from './food-list-row';

// Convention « boutons de rows app-wide » appliquée au favori du catalogue (cas précis de la tâche) :
// favori ACTIF = fond orange (--c-orange-medium) + icône blanche (--app-on-accent), étoile pleine ;
// favori INACTIF = fond neutre (--app-bg-button) + icône blanche en sombre (--app-text-primary, qui
// reste lisible en clair), étoile creuse. Aucun fond transparent.
// On vérifie le STYLE EFFECTIVEMENT RENDU (inline style sur le bouton / l'icône) — même approche que
// destructive-action-buttons.spec / food-detail-panel.spec (jsdom conserve les valeurs var()).

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
    fiberPer100g: 10,
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

function mount(f: LocalFood): HTMLElement {
  const fixture = TestBed.createComponent(FoodListRow);
  fixture.componentRef.setInput('food', f);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

/** Le bouton favori = l'unique app-action-icon-button de la row. */
function favBtn(el: HTMLElement): HTMLButtonElement {
  return el.querySelector('app-action-icon-button button') as HTMLButtonElement;
}
const icon = (btn: HTMLElement) => (btn.querySelector('.ms')?.textContent ?? '').trim();
const iconColor = (btn: HTMLElement) => (btn.querySelector('.ms') as HTMLElement).style.color;

describe('FoodListRow — convention bouton de row (favori)', () => {
  it('favori ACTIF : fond orange + icône blanche (étoile pleine)', () => {
    const el = mount(food({ isFavorite: true }));
    const btn = favBtn(el);
    expect(btn.style.background).toBe('var(--c-orange-medium)');
    expect(iconColor(btn)).toBe('var(--app-on-accent)');
    expect(icon(btn)).toBe('star');
  });

  it('favori INACTIF : fond neutre (pas transparent) + icône blanche (étoile creuse)', () => {
    const el = mount(food({ isFavorite: false }));
    const btn = favBtn(el);
    expect(btn.style.background).toBe('var(--app-bg-button)');
    expect(btn.style.background).not.toBe('transparent');
    expect(iconColor(btn)).toBe('var(--app-text-primary)');
    expect(icon(btn)).toBe('star_border');
  });
});

// Sucres dans la sous-ligne macros (information au moment de choisir, pas une barre vs plafond) :
// valeur colorée --macro-sugar comme les fibres, bascule en couleur d'alerte au-delà de 22,5 g/100 g
// (repère étiquetage UK « high in sugar »), rien d'affiché si sugarPer100g null.
describe('FoodListRow — sucres per-100 g dans la sous-ligne', () => {
  /** Span de la sous-ligne dont le texte commence par « S » suivi de la valeur sucres. */
  function sugarSpan(el: HTMLElement): HTMLElement | null {
    const spans = Array.from(el.querySelectorAll('.frow__sub span')) as HTMLElement[];
    return spans.find((s) => /^S\s/.test((s.textContent ?? '').trim())) ?? null;
  }

  it('sucres renseignés (aliment normal) : valeur affichée en teinte --macro-sugar', () => {
    const el = mount(food({ sugarPer100g: 4.2 }));
    const span = sugarSpan(el)!;
    expect(span).not.toBeNull();
    expect(span.textContent!.trim()).toBe('S 4.2');
    expect(span.style.color).toBe('var(--macro-sugar)');
  });

  it('aliment riche en sucres (> 22,5 g/100 g) : couleur d’alerte warning', () => {
    const el = mount(food({ sugarPer100g: 45 }));
    const span = sugarSpan(el)!;
    expect(span.style.color).toBe('var(--app-snackbar-warning)');
  });

  it('au seuil exact (22,5) : pas d’alerte (teinte normale)', () => {
    const el = mount(food({ sugarPer100g: 22.5 }));
    expect(sugarSpan(el)!.style.color).toBe('var(--macro-sugar)');
  });

  it('sucres non renseignés (null) : rien d’affiché', () => {
    const el = mount(food({ sugarPer100g: null }));
    expect(sugarSpan(el)).toBeNull();
  });
});
