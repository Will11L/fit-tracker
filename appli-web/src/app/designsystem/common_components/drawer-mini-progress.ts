import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';

/**
 * Mini barre de progression d'un DrawerItem — miroir de DrawerMiniProgress.kt / Figma :
 * track 60×6 (boxBlue, coins 2) + remplissage (largeur = progress%) + pourcentage 12px SemiBold.
 * Couleur par seuil (cf. variantes Figma 30/75/100) : < 50 orange, < 100 vert, = 100 bleu.
 * Ex. « ▓▓░ 60% » en trailing d'un item séance.
 */
@Component({
  selector: 'app-drawer-mini-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="dmp">
      <span class="dmp__track">
        <span class="dmp__fill" [style.width.%]="fillWidth()" [style.background]="color()"></span>
      </span>
      <span class="dmp__pct" [style.color]="color()">{{ clamped() }}%</span>
    </span>
  `,
  styles: [
    `
      .dmp {
        display: inline-flex;
        align-items: center;
        gap: 8px;
      }
      .dmp__track {
        width: 60px;
        height: 6px;
        border-radius: 2px;
        background: var(--c-box-blue);
        overflow: hidden;
      }
      .dmp__fill {
        display: block;
        height: 100%;
        border-radius: 2px;
        /* Remplissage animé 0 → valeur (apparition + changements) via les tokens de motion. */
        transition: width var(--motion-base) var(--motion-ease);
      }
      /* a11y : aucune animation si l'utilisateur a demandé moins de mouvement. */
      @media (prefers-reduced-motion: reduce) {
        .dmp__fill {
          transition: none;
        }
      }
      .dmp__pct {
        font-size: 12px;
        font-weight: 600;
      }
    `,
  ],
})
export class DrawerMiniProgress {
  readonly progress = input(0);

  /** Faux jusqu'au 1er paint : la barre démarre à 0 puis monte vers la vraie valeur (transition CSS). */
  private readonly mounted = signal(false);

  protected readonly clamped = computed(() => Math.max(0, Math.min(100, Math.round(this.progress()))));
  /** Largeur de remplissage : 0 au 1er rendu, puis vraie valeur → la transition anime. Le % texte reste exact. */
  protected readonly fillWidth = computed(() => (this.mounted() ? this.clamped() : 0));
  protected readonly color = computed(() => {
    const p = this.clamped();
    if (p >= 100) return 'var(--app-primary-action)';
    if (p >= 50) return 'var(--c-medium-green)';
    return 'var(--c-orange-medium)';
  });

  constructor() {
    afterNextRender(() => this.mounted.set(true));
  }
}
