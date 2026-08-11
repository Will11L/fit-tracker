import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { CustomSwitch } from './custom-switch';

// Deux familles de comportements observables :
//   1. interaction (toggle / aria / disabled / two-way binding) via le DOM rendu ;
//   2. contrat visuel de la tâche "thumb plus petit quand OFF" : on lit le CSS réellement
//      embarqué par le composant (styles injectés dans le DOM, fallback = styles de la def)
//      et on vérifie l'invariant — thumb circulaire, plus petit en OFF qu'en ON, et toujours
//      inset dans le track 44×24 (pas de débordement). jsdom n'applique pas la cascade CSS via
//      getComputedStyle, donc on inspecte la feuille de style elle-même = l'artefact qui ship.

describe('CustomSwitch — interrupteur (design system)', () => {
  function render() {
    const fixture = TestBed.createComponent(CustomSwitch);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button.sw') as HTMLButtonElement;
    return { fixture, button };
  }

  describe('interaction', () => {
    it('OFF par défaut : aria-checked=false, classe sw--on absente', () => {
      const { button } = render();
      expect(button.getAttribute('aria-checked')).toBe('false');
      expect(button.classList.contains('sw--on')).toBe(false);
    });

    it('clic → bascule ON (checked passe à true, aria + classe suivent)', () => {
      const { fixture, button } = render();
      button.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.checked()).toBe(true);
      expect(button.getAttribute('aria-checked')).toBe('true');
      expect(button.classList.contains('sw--on')).toBe(true);
    });

    it('deuxième clic → re-bascule OFF (toggle symétrique)', () => {
      const { fixture, button } = render();
      button.click();
      fixture.detectChanges();
      button.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.checked()).toBe(false);
      expect(button.classList.contains('sw--on')).toBe(false);
    });

    it('disabled : le bouton est désactivé et un clic ne bascule rien', () => {
      const fixture = TestBed.createComponent(CustomSwitch);
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      const button = fixture.nativeElement.querySelector('button.sw') as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      button.click(); // un bouton disabled ne dispatch pas l'event (garde native)
      fixture.detectChanges();
      expect(fixture.componentInstance.checked()).toBe(false);
    });

    it('two-way binding [(checked)] : le parent reçoit la nouvelle valeur au clic', () => {
      @Component({
        imports: [CustomSwitch],
        template: `<app-custom-switch [(checked)]="val" />`,
      })
      class HostComponent {
        val = false;
      }

      const fixture = TestBed.createComponent(HostComponent);
      fixture.detectChanges();
      const button = fixture.nativeElement.querySelector('button.sw') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.val).toBe(true);
    });
  });

  describe('contrat visuel — thumb plus petit en OFF (alignement M3/Android)', () => {
    /** CSS réellement embarqué : feuille injectée dans le DOM, sinon styles de la définition. */
    function switchCss(): string {
      const fixture = TestBed.createComponent(CustomSwitch);
      fixture.detectChanges();
      const fromDom = Array.from(document.querySelectorAll('style'))
        .map((s) => s.textContent ?? '')
        .join('\n');
      if (fromDom.includes('.sw__thumb')) return fromDom;
      const def = (CustomSwitch as unknown as { ɵcmp?: { styles?: string[] } }).ɵcmp;
      return (def?.styles ?? []).join('\n');
    }

    type Rule = { selector: string; decls: string };
    function rules(css: string): Rule[] {
      return [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map((m) => ({ selector: m[1], decls: m[2] }));
    }
    /** Lit une valeur en px d'une déclaration (anchrée pour ne pas matcher dans `transition`). */
    function px(decls: string, prop: string): number | undefined {
      const m = new RegExp(`(?:^|[;\\s{])${prop}\\s*:\\s*([\\d.]+)px`).exec(decls);
      return m ? parseFloat(m[1]) : undefined;
    }

    it('thumb circulaire, plus petit en OFF qu’en ON, et inset dans le track (pas de débordement)', () => {
      const all = rules(switchCss());

      const track = all.find(
        (r) =>
          r.selector.includes('.sw') &&
          !r.selector.includes('.sw__thumb') &&
          !r.selector.includes('.sw--on') &&
          !r.selector.includes(':disabled'),
      );
      const offThumb = all.find((r) => r.selector.includes('.sw__thumb') && !r.selector.includes('.sw--on'));
      const onThumb = all.find((r) => r.selector.includes('.sw--on') && r.selector.includes('.sw__thumb'));

      expect(track, 'règle .sw (track) introuvable').toBeTruthy();
      expect(offThumb, 'règle .sw__thumb (OFF) introuvable').toBeTruthy();
      expect(onThumb, 'règle .sw--on .sw__thumb (ON) introuvable').toBeTruthy();

      const trackW = px(track!.decls, 'width')!;
      const trackH = px(track!.decls, 'height')!;
      const offW = px(offThumb!.decls, 'width')!;
      const offH = px(offThumb!.decls, 'height')!;
      const offLeft = px(offThumb!.decls, 'left')!;
      const onW = px(onThumb!.decls, 'width')!;
      const onH = px(onThumb!.decls, 'height')!;
      const onLeft = px(onThumb!.decls, 'left')!;

      // Toutes les dimensions doivent être présentes (la règle ON re-déclare width/height/left).
      for (const v of [trackW, trackH, offW, offH, offLeft, onW, onH, onLeft]) {
        expect(typeof v).toBe('number');
      }

      // Thumbs circulaires.
      expect(offW).toBe(offH);
      expect(onW).toBe(onH);

      // Cœur de la tâche : OFF strictement plus petit que ON.
      expect(offW).toBeLessThan(onW);

      // Inset dans le track : pas de débordement horizontal ni vertical, dans les deux états.
      expect(offLeft + offW).toBeLessThanOrEqual(trackW);
      expect(offH).toBeLessThanOrEqual(trackH);
      expect(onLeft + onW).toBeLessThanOrEqual(trackW);
      expect(onH).toBeLessThanOrEqual(trackH);
    });
  });
});
