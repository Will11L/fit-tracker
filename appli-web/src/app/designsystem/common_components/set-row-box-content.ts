import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Cellule cliquable d'une SetRow — miroir de SetRowBoxContent.kt : « champ » compact centré dans la
 * cellule (n'épouse QUE le chiffre, la cellule respire autour), coins 4px, texte 14px. Reps/poids :
 * fond bgSurface + liseré fin → lit comme un champ éditable ; liseré primaryAction au survol/focus.
 * Index (sans fond) : simple chiffre cliquable, léger fond au survol. Sert index / reps / poids.
 */
@Component({
  selector: 'app-set-row-box-content',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="srbc" [class.srbc--bg]="hasBackground()" (click)="clicked.emit()">{{ text() }}</button>
  `,
  styles: [
    `
      :host {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
      }
      /* La boîte remplit sa cellule jusqu'à 48px (aspect « bouton », = boutons « À faire » des goals)
         plutôt que d'épouser juste le chiffre. */
      .srbc {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        max-width: 48px;
        min-width: 0;
        height: 28px;
        box-sizing: border-box;
        border: 1px solid transparent;
        border-radius: var(--radius-sm);
        background: transparent;
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
        transition: border-color 0.12s ease;
      }
      /* Reps / poids : champ second-blue + liseré fin (test). */
      .srbc--bg {
        background: var(--c-second-blue);
        border-color: var(--c-second-blue);
      }
      .srbc--bg:hover,
      .srbc--bg:focus-visible {
        border-color: var(--app-primary-action);
      }
      /* Index (sans fond) : léger fond au survol pour signaler le clic. */
      .srbc:not(.srbc--bg):hover {
        background: color-mix(in srgb, var(--app-primary-action) 10%, transparent);
      }
    `,
  ],
})
export class SetRowBoxContent {
  readonly text = input('');
  readonly hasBackground = input(true);
  readonly clicked = output<void>();
}
