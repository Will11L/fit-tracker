import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';

/**
 * Primitif de barre de progression — miroir de ProgressBarPrimitive.kt : trough (default bgRecessed)
 * + remplissage couleur, hauteur 7px par défaut, coins 2px. Source unique du look de la barre.
 *
 * Le remplissage est **animé 0 → valeur** : on peint 0 au 1er rendu puis on pose la vraie valeur au
 * paint suivant (`afterNextRender`) → la transition CSS joue le remplissage qui monte. Effet hérité
 * par tous les usages (LabeledProgressBar, etc.). `prefers-reduced-motion: reduce` coupe la transition.
 */
@Component({
  selector: 'app-progress-bar-primitive',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="trough" [style.height.px]="height()" [style.background]="troughColor()">
      <div class="fill" [style.width.%]="pct()" [style.background]="color()"></div>
      @if (markerAt() !== null) {
        <div class="marker" [style.left.%]="markerPct()"></div>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .trough {
        position: relative;
        width: 100%;
        border-radius: 2px;
        overflow: hidden;
      }
      /* Repère vertical statique (seuil) : trait fin par-dessus trough ET remplissage. */
      .marker {
        position: absolute;
        top: 0;
        bottom: 0;
        width: 2px;
        transform: translateX(-50%);
        background: var(--app-text-tertiary);
      }
      .fill {
        height: 100%;
        border-radius: 2px;
        /* Remplissage animé 0 → valeur (apparition + changements) via les tokens de motion. */
        transition: width var(--motion-base) var(--motion-ease);
      }
      /* a11y : aucune animation si l'utilisateur a demandé moins de mouvement. */
      @media (prefers-reduced-motion: reduce) {
        .fill {
          transition: none;
        }
      }
    `,
  ],
})
export class ProgressBarPrimitive {
  /** Progression 0..1. */
  readonly progress = input(0);
  readonly color = input('var(--app-primary-action)');
  readonly height = input(7);
  readonly troughColor = input('var(--app-bg-recessed)');
  /** Repère vertical optionnel (0..1, null = aucun) — ex. seuil « idéal » d'une barre de limite
   *  (sucres OMS : marque à 5 % sur la barre bornée au plafond 10 %). Statique, non animé. */
  readonly markerAt = input<number | null>(null);

  /** Faux jusqu'au 1er paint : on rend 0 d'abord, puis la vraie valeur → la transition part de 0. */
  private readonly mounted = signal(false);

  protected readonly pct = computed(
    () => Math.max(0, Math.min(1, this.mounted() ? this.progress() : 0)) * 100,
  );

  protected readonly markerPct = computed(
    () => Math.max(0, Math.min(1, this.markerAt() ?? 0)) * 100,
  );

  constructor() {
    afterNextRender(() => this.mounted.set(true));
  }
}
