import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';

/** Géométrie d'un anneau de progression. */
export interface RingGeometry {
  /** Rayon du tracé (centre de l'épaisseur du trait). */
  radius: number;
  /** Circonférence complète (longueur du dasharray). */
  circumference: number;
  /** Longueur de l'arc rempli (= circonférence × progression bornée). */
  dash: number;
  /** Décalage du dash pour démarrer le remplissage en haut (= circonférence − dash). */
  offset: number;
}

/**
 * Géométrie pure de l'anneau — testable sans DOM. Le tracé est centré dans la boîte `size`, avec une
 * marge égale à l'épaisseur du trait pour que le trait ne déborde pas du viewBox.
 */
export function ringGeometry(size: number, thickness: number, progress: number): RingGeometry {
  const radius = (size - thickness) / 2;
  const circumference = 2 * Math.PI * radius;
  const p = Math.max(0, Math.min(1, progress));
  const dash = circumference * p;
  return { radius, circumference, dash, offset: circumference - dash };
}

/**
 * Anneau de progression (cercle SVG qui se remplit, stroke-dasharray) — primitif réutilisable du
 * Design System. Taille / épaisseur paramétrables, couleur = accent (token, jamais de M3 brut),
 * label central valeur + cible. Pendant circulaire de ProgressBarPrimitive.
 */
@Component({
  selector: 'app-progress-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ring" [style.width.px]="size()" [style.height.px]="size()">
      <svg
        [attr.width]="size()"
        [attr.height]="size()"
        [attr.viewBox]="'0 0 ' + size() + ' ' + size()"
      >
        <circle
          [attr.cx]="center()"
          [attr.cy]="center()"
          [attr.r]="geo().radius"
          fill="none"
          [attr.stroke]="troughColor()"
          [attr.stroke-width]="thickness()"
        />
        <circle
          class="ring__fill"
          [attr.cx]="center()"
          [attr.cy]="center()"
          [attr.r]="geo().radius"
          fill="none"
          [attr.stroke]="color()"
          [attr.stroke-width]="thickness()"
          stroke-linecap="round"
          [attr.stroke-dasharray]="geo().circumference"
          [attr.stroke-dashoffset]="geo().offset"
          [attr.transform]="'rotate(-90 ' + center() + ' ' + center() + ')'"
        />
      </svg>
      <div class="ring__label">
        <span class="ring__value">{{ label() }}</span>
        @if (sublabel()) {
          <span class="ring__sub">{{ sublabel() }}</span>
        }
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .ring {
        position: relative;
        display: inline-flex;
      }
      .ring svg {
        display: block;
      }
      /* Remplissage animé 0 → valeur (apparition + changements) via les tokens de motion. */
      .ring__fill {
        transition: stroke-dashoffset var(--motion-base) var(--motion-ease);
      }
      /* a11y : aucune animation si l'utilisateur a demandé moins de mouvement. */
      @media (prefers-reduced-motion: reduce) {
        .ring__fill {
          transition: none;
        }
      }
      .ring__label {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        font-variant-numeric: tabular-nums;
        line-height: 1.1;
      }
      .ring__value {
        color: var(--app-text-primary);
        font-size: 13px;
        font-weight: var(--font-weight-medium);
      }
      .ring__sub {
        color: var(--app-text-secondary);
        font-size: 10px;
      }
    `,
  ],
})
export class ProgressRing {
  /** Progression 0..1 (bornée par la géométrie). */
  readonly progress = input(0);
  readonly size = input(76);
  readonly thickness = input(8);
  readonly color = input('var(--app-primary-action)');
  readonly troughColor = input('var(--app-bg-surface)');
  /** Texte central principal (valeur). */
  readonly label = input('');
  /** Texte central secondaire (cible / unité). */
  readonly sublabel = input('');

  /** Faux jusqu'au 1er paint : on rend l'anneau vide d'abord, puis la vraie valeur → transition part de 0. */
  private readonly mounted = signal(false);

  protected readonly center = computed(() => this.size() / 2);
  protected readonly geo = computed(() =>
    ringGeometry(this.size(), this.thickness(), this.mounted() ? this.progress() : 0),
  );

  constructor() {
    afterNextRender(() => this.mounted.set(true));
  }
}
