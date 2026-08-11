import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Cadre de liste thirdBlue (fond recessed + coins arrondis), pensé pour contenir des `<app-list-row>` :
 * des rows À PLAT séparées par un filet horizontal. `overflow: hidden` → les coins arrondis sont nets et
 * le liseré de sélection des rows extrêmes suit l'arrondi. Pattern partagé dans l'app : aliments d'un
 * repas (journal), catalogue d'aliments, historique des objectifs, recettes & repas.
 *
 * Usage :
 * ```html
 * <app-list-frame>
 *   @for (item of items; track item.id) {
 *     <app-list-row [selected]="item.id === selectedId()" (clicked)="select(item)">
 *       … contenu de la row (nom, macros, boutons…) …
 *     </app-list-row>
 *   }
 * </app-list-frame>
 * ```
 */
@Component({
  selector: 'app-list-frame',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        min-width: 0;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        overflow: hidden;
      }
    `,
  ],
})
export class ListFrame {}
