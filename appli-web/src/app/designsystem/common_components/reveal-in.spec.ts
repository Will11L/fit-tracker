import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RevealIn } from './reveal-in';

// La directive pose une animation CSS `app-reveal-in` en inline-style sur l'hôte, et la rejoue à
// chaque changement de clé. jsdom n'exécute pas l'animation (pas de moteur de rendu) : on observe
// donc l'artefact réellement posé — `host.style.animation` — qui prouve le déclenchement.
// `prefers-reduced-motion` est lu via window.matchMedia (stubé par test, jsdom ne l'implémente pas).

@Component({
  imports: [RevealIn],
  template: `<div class="target" [appRevealIn]="key()">contenu</div>`,
})
class HostComponent {
  readonly key = signal<string>('a');
}

// Hôte variante "fast" : exerce l'autre branche du token de durée (--motion-fast).
@Component({
  imports: [RevealIn],
  template: `<div class="target" appRevealIn speed="fast">contenu</div>`,
})
class FastHostComponent {}

// Hôte variant="fade" : entrée en fondu pur (pas de slide), même à la 1ère apparition.
@Component({
  imports: [RevealIn],
  template: `<div class="target" appRevealIn variant="fade">contenu</div>`,
})
class FadeHostComponent {}

function setMatchMedia(reduceMatches: boolean): void {
  window.matchMedia = ((query: string) =>
    ({
      // Seule la requête reduced-motion renvoie la valeur testée ; le reste est neutre.
      matches: query.includes('prefers-reduced-motion') ? reduceMatches : false,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      onchange: null,
      dispatchEvent: () => false,
    }) as MediaQueryList) as typeof window.matchMedia;
}

function render() {
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  const target = fixture.nativeElement.querySelector('.target') as HTMLElement;
  return { fixture, target };
}

describe('RevealIn — directive appRevealIn (animation d’entrée)', () => {
  it('pose l’animation slide-down + fade à l’init', () => {
    setMatchMedia(false);
    const { target } = render();
    expect(target.style.animation).toContain('app-reveal-in');
    // Durée par défaut = motion-base (~220ms), easing partagé.
    expect(target.style.animation).toContain('var(--motion-base)');
    expect(target.style.animation).toContain('var(--motion-ease)');
  });

  it('change de clé (re-sélection) → fondu seul app-fade-in, sans slide (pas de saut vertical)', () => {
    setMatchMedia(false);
    const { fixture, target } = render();
    // Init = slide-down + fade.
    expect(target.style.animation).toContain('app-reveal-in');
    // On efface l'inline-style posé à l'init : seul un re-déclenchement le repeuplera.
    target.style.animation = '';
    fixture.componentInstance.key.set('b');
    fixture.detectChanges();
    // Changement de clé = fondu seul (opacity) — pas le slide-down → pas de translateY → pas de saut.
    expect(target.style.animation).toContain('app-fade-in');
    expect(target.style.animation).not.toContain('app-reveal-in');
  });

  it('reste en fondu seul sur plusieurs changements de clé (le slide ne réapparaît jamais)', () => {
    setMatchMedia(false);
    const { fixture, target } = render();
    // 2e puis 3e sélection : le slide-down (app-reveal-in) doit rester réservé à la 1ère apparition.
    for (const k of ['b', 'c']) {
      target.style.animation = '';
      fixture.componentInstance.key.set(k);
      fixture.detectChanges();
      expect(target.style.animation).toContain('app-fade-in');
      expect(target.style.animation).not.toContain('app-reveal-in');
    }
  });

  it('respecte prefers-reduced-motion: reduce (aucune animation posée)', () => {
    setMatchMedia(true);
    const { target } = render();
    expect(target.style.animation).toBe('');
  });

  it('utilise la durée --motion-fast quand speed="fast"', () => {
    setMatchMedia(false);
    const fixture = TestBed.createComponent(FastHostComponent);
    fixture.detectChanges();
    const target = fixture.nativeElement.querySelector('.target') as HTMLElement;
    expect(target.style.animation).toContain('app-reveal-in');
    expect(target.style.animation).toContain('var(--motion-fast)');
    expect(target.style.animation).not.toContain('var(--motion-base)');
  });

  it('variant="fade" : fondu pur dès la 1ère apparition (pas de slide-down)', () => {
    setMatchMedia(false);
    const fixture = TestBed.createComponent(FadeHostComponent);
    fixture.detectChanges();
    const target = fixture.nativeElement.querySelector('.target') as HTMLElement;
    // Sections échangées qui se remontent : un slide se rejouerait à chaque toggle → on force le fondu.
    expect(target.style.animation).toContain('app-fade-in');
    expect(target.style.animation).not.toContain('app-reveal-in');
  });

  it('nettoie l’inline-style à animationend (pas de transform résiduel qui casserait les position:fixed)', () => {
    setMatchMedia(false);
    const { target } = render();
    expect(target.style.animation).toContain('app-reveal-in');
    // Fin d'animation : la directive efface l'inline-style. Sinon un `transform: translateY(0)` résiduel
    // (fill `both`) établirait un bloc conteneur pour les descendants `position: fixed` (dialogs/sheets
    // sous l'outlet mal cadrés) — c'est la régression que ce cleanup, ajouté par le sweep, prévient.
    target.dispatchEvent(new Event('animationend'));
    expect(target.style.animation).toBe('');
  });

  it('ignore l’animationend qui bubble depuis un enfant (n’efface que la sienne)', () => {
    setMatchMedia(false);
    const { target } = render();
    expect(target.style.animation).toContain('app-reveal-in');
    // Un enfant qui termine SA propre animation (l'événement bubble jusqu'à l'hôte) ne doit pas
    // effacer l'animation de l'hôte — le garde `e.target === host` protège ce cas.
    const child = document.createElement('span');
    target.appendChild(child);
    child.dispatchEvent(new Event('animationend', { bubbles: true }));
    expect(target.style.animation).toContain('app-reveal-in');
  });
});
