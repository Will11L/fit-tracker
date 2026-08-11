import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActionIconButton } from './action-icon-button';
import { EntityRowTrailing } from './entity-row-trailing';

// Contrat de la convention trailing partagée (tâche « boutons trailing pleine hauteur + meilleur
// espacement ») : un seul endroit (la directive) règle (1) la hauteur pleine des boutons d'action
// via la var CSS `--aib-button-height`, et (2) l'agencement (flex + gap aéré). jsdom préserve les
// valeurs `var()` en inline-style (cf. notifications-row-buttons.spec / food-detail-panel.spec).

describe('ActionIconButton — hauteur pilotable par variable CSS', () => {
  function button(size?: number): HTMLButtonElement {
    const fixture = TestBed.createComponent(ActionIconButton);
    if (size !== undefined) fixture.componentRef.setInput('size', size);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('button.aib') as HTMLButtonElement;
  }

  it('par défaut : hauteur = var(--aib-button-height, <size>px) avec fallback = size (carré inchangé)', () => {
    const b = button();
    expect(b.style.height).toContain('var(--aib-button-height');
    expect(b.style.height).toContain('34px'); // fallback = size par défaut (baseline web compacte)
    expect(b.style.width).toBe('34px'); // largeur = size par défaut
  });

  it('le fallback suit la prop `size` (rétro-compatible)', () => {
    const b = button(32);
    expect(b.style.height).toContain('32px');
    expect(b.style.width).toBe('32px');
  });
});

describe('EntityRowTrailing — convention de zone trailing partagée', () => {
  @Component({
    imports: [EntityRowTrailing, ActionIconButton],
    template: `
      <span appEntityRowTrailing>
        <app-action-icon-button icon="star" />
        <app-action-icon-button icon="arrow_right_alt" />
      </span>
    `,
  })
  class HostComponent {}

  function render() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement.querySelector('span') as HTMLElement;
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button.aib')) as HTMLButtonElement[];
    return { host, buttons };
  }

  it('pose --aib-button-height = hauteur interne de la row (pleine hauteur des boutons)', () => {
    const { host } = render();
    const v = host.style.getPropertyValue('--aib-button-height');
    expect(v).toContain('var(--elr-inner-height');
    expect(v).toContain('44px'); // fallback = .elr__inner d'EntityListRow
  });

  it('agence la zone en flex (icônes/boutons espacés)', () => {
    const { host } = render();
    expect(host.style.display).toBe('flex');
  });

  it('espace sync/favori/flèche avec le gap aéré de la convention (space-3, pas space-1)', () => {
    // Cœur de la tâche « meilleur espacement (sync/favori/flèche) » : la convention impose un gap
    // aéré entre les éléments trailing. Verrouille la valeur pour empêcher un retour au space-1
    // serré (ou la suppression du gap). jsdom préserve le token var() en inline-style.
    const { host } = render();
    expect(host.style.getPropertyValue('gap')).toBe('var(--space-3)');
  });

  it('les boutons d’action lisent la var → hauteur pilotée par la convention, largeur inchangée', () => {
    const { buttons } = render();
    expect(buttons.length).toBe(2);
    for (const b of buttons) {
      expect(b.style.height).toContain('var(--aib-button-height');
      expect(b.style.width).toBe('34px');
    }
  });
});
