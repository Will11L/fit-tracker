import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Row à plat d'un `<app-list-frame>`. Layout flex (contenu projeté = items flex : zone principale +
 * boutons à droite). Fournit :
 *  - un filet horizontal ANCRÉ dans le cadre (inset par le padding) sauf sur la dernière row ;
 *  - des coins arrondis sur la 1re / dernière row → le liseré de sélection suit l'arrondi du cadre ;
 *  - un liseré `primaryAction` (box-shadow inset) si `selected` ;
 *  - `clicked` émis au clic sur la row (les boutons internes doivent stopper la propagation), et un
 *    curseur pointeur quand `clickable` (défaut on ; mettre à `false` pour une row non sélectionnable au
 *    clic, ex. la row gère son propre clic en interne).
 */
@Component({
  selector: 'app-list-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
  host: {
    '[class.list-row--selected]': 'selected()',
    '[class.list-row--clickable]': 'clickable()',
    '(click)': 'clicked.emit()',
  },
  styles: [
    `
      :host {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        position: relative;
        /* Asymétrique (g space-3 / d space-2) : les boutons à droite ont leur propre marge interne. */
        padding: 8px var(--space-2) 8px var(--space-3);
      }
      :host(.list-row--clickable) {
        cursor: pointer;
      }
      :host(.list-row--clickable):hover {
        background: color-mix(in srgb, var(--app-primary-action) 8%, transparent);
      }
      :host(.list-row--selected) {
        box-shadow: inset 0 0 0 1px var(--app-primary-action);
      }
      /* Filet horizontal ancré dans le cadre (inset par le padding), sauf la dernière row. */
      :host(:not(:last-child))::after {
        content: '';
        position: absolute;
        left: var(--space-3);
        right: var(--space-2);
        bottom: 0;
        height: 1px;
        background: var(--c-second-blue);
      }
      /* Row sélectionnée : pas de filet en bas — le liseré primaryAction fait office de séparateur.
         Sinon le filet, peint PAR-DESSUS le box-shadow inset, masquait le bas du liseré (on voyait le
         divider au lieu du liseré bleu). */
      :host(.list-row--selected)::after {
        display: none;
      }
      /* Coins arrondis 1re / dernière → le liseré de sélection suit l'arrondi du cadre (overflow:hidden). */
      :host(:first-child) {
        border-top-left-radius: var(--radius-md);
        border-top-right-radius: var(--radius-md);
      }
      :host(:last-child) {
        border-bottom-left-radius: var(--radius-md);
        border-bottom-right-radius: var(--radius-md);
      }
    `,
  ],
})
export class ListRow {
  /** Row sélectionnée → liseré primaryAction. */
  readonly selected = input(false);
  /** Row cliquable (curseur + hover + émet `clicked`). Off = la row gère son clic en interne. */
  readonly clickable = input(true);
  readonly clicked = output<void>();
}
