import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ActionIconButton } from './action-icon-button';

/**
 * Champ date (déclencheur) — conteneur recessed affichant la date déjà formatée par le callsite
 * (ou un placeholder si vide) + un bouton icône calendrier (fond first-blue) à droite. Seul ce
 * bouton icône est cliquable ; il émet `(clicked)` pour ouvrir un `CustomDatePickerDialog` que le
 * callsite garde et câble. Centralise le look du déclencheur de date dans le design system
 * (profil, objectifs nutrition, …) — une seule source pour le champ + le bouton calendrier.
 */
@Component({
  selector: 'app-date-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton],
  template: `
    <div class="df">
      <span class="df__text" [class.df__text--empty]="!value()">{{ value() || placeholder() }}</span>
      <app-action-icon-button
        icon="calendar_today"
        backgroundColor="var(--c-first-blue)"
        tint="#ffffff"
        [iconSize]="20"
        (clicked)="clicked.emit()"
      />
    </div>
  `,
  styles: [
    `
      .df {
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-sm);
        /* Padding réduit à droite : le bouton icône porte sa propre zone tap (40px). */
        padding: var(--space-1) var(--space-1) var(--space-1) var(--space-4);
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        color: var(--app-text-primary);
      }
      .df__text {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .df__text--empty {
        color: var(--app-text-tertiary);
      }
    `,
  ],
})
export class DateField {
  /** Texte affiché (date déjà formatée par le callsite). Vide → `placeholder`. */
  readonly value = input('');
  readonly placeholder = input('—');
  readonly clicked = output<void>();
}
