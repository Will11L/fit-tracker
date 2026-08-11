import { Directive, ElementRef, effect, inject, input } from '@angular/core';

/**
 * `appRevealIn` — anime l'entrée en douceur de l'hôte. Principe partagé par toute l'app :
 * **1ère apparition = entrée animée** (selon le `variant`), **apparitions suivantes** (changement
 * de clé `[appRevealIn]="selectedUuid()"`, hôte déjà monté) **= fondu seul** (opacity 0 → 1, sans
 * `translateY`) → crossfade doux, sans saut vertical de la colonne déjà visible (évite les sursauts).
 *
 * `variant` adapte l'entrée au type d'élément :
 * - `slide-down` (défaut) : colonnes / panneaux (détail master-detail, contenu de route) —
 *   slide-down (translateY −8px → 0) + fade à la 1ère apparition, puis fondu seul ensuite.
 * - `fade` : petits éléments inline / sections échangées qui se **remontent** à chaque bascule
 *   (ex. swap barres ↔ anneaux) — fondu pur à chaque apparition (jamais de slide, sinon un
 *   glissement se rejouerait à chaque toggle = jank).
 *
 * Approche légère (pas `@angular/animations`) : pose l'animation CSS (`app-reveal-in` /
 * `app-fade-in` — cf. `theme/_motion.scss`) en inline-style sur l'hôte, avec un reset + reflow
 * forcé pour la rejouer même quand l'hôte persiste entre deux clés.
 * `prefers-reduced-motion: reduce` est respecté — aucune animation n'est posée (mouvement coupé).
 *
 * Fondation réutilisable : applicable tel quel à tout conteneur (colonnes détail, sheets, listes…).
 */
@Directive({
  selector: '[appRevealIn]',
})
export class RevealIn {
  /** Clé d'entrée : changer sa valeur rejoue l'animation (ex. UUID sélectionné). `undefined` = init seul. */
  readonly key = input<unknown>(undefined, { alias: 'appRevealIn' });
  /** Vitesse : `base` (~220ms, défaut) ou `fast` (~160ms). */
  readonly speed = input<'base' | 'fast'>('base');
  /** Type d'entrée : `slide-down` (colonnes/panneaux, défaut) ou `fade` (inline / sections échangées). */
  readonly variant = input<'slide-down' | 'fade'>('slide-down');

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  /** Faux après la 1ère apparition : distingue l'entrée initiale du fondu seul des changements de clé. */
  private firstRun = true;

  constructor() {
    // Nettoie l'animation inline une fois jouée : évite de laisser un `transform: translateY(0)`
    // résiduel (fill `both`) sur l'hôte, qui établirait un bloc conteneur pour les descendants
    // `position: fixed` (dialogs/sheets mal cadrés quand on enveloppe un grand conteneur, ex. l'outlet).
    // `animationend` bubble → on ne traite que l'animation de l'hôte (`e.target === host`).
    this.host.addEventListener('animationend', (e) => {
      if (e.target === this.host) this.host.style.animation = '';
    });
    // L'effet lit `key()` → se relance (et rejoue l'animation) à l'init puis à chaque changement.
    effect(() => {
      this.key();
      this.play();
    });
  }

  private play(): void {
    if (prefersReducedMotion()) return;
    const duration = this.speed() === 'fast' ? 'var(--motion-fast)' : 'var(--motion-base)';
    // Entrée initiale selon le variant ; `fade` reste un fondu pur même à la 1ère apparition.
    const enter = this.variant() === 'fade' ? 'app-fade-in' : 'app-reveal-in';
    // 1ère apparition : entrée (slide-down + fade, ou fondu). Changements de clé suivants : fondu seul.
    const keyframe = this.firstRun ? enter : 'app-fade-in';
    this.firstRun = false;
    // Reset + reflow forcé : permet de rejouer l'animation même si l'hôte n'est pas recréé (changement de clé).
    this.host.style.animation = 'none';
    void this.host.offsetWidth;
    this.host.style.animation = `${keyframe} ${duration} var(--motion-ease) both`;
  }
}

/** Vrai si l'utilisateur a demandé moins de mouvement (a11y). Faux si matchMedia indisponible (SSR/jsdom). */
function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}
