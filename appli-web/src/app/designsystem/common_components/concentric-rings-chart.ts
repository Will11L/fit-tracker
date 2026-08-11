import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ConcentricRings, type ConcentricRing } from './concentric-rings';

/**
 * Graphe « anneaux concentriques » prêt à l'emploi (Design System) : la pile d'anneaux de progression
 * (`ConcentricRings`, avec ses étiquettes « en étoile » optionnelles) + un libellé central optionnel,
 * le tout cadré (se remplit et se centre dans son conteneur). Générique : l'appelant fournit les
 * anneaux (progression / couleur / épaisseur / étiquette) et le texte central. Pendant « chart » du
 * primitif `ConcentricRings`. Ex. : profil macros des pages Stats / Objectifs nutrition.
 */
@Component({
  selector: 'app-concentric-rings-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ConcentricRings],
  template: `
    <app-concentric-rings [size]="size()" [rings]="rings()" [gap]="gap()" [fitHeight]="fitHeight()">
      @if (centerText()) {
        <span class="crc__center" [style.color]="centerColor()">{{ centerText() }}</span>
      }
    </app-concentric-rings>
  `,
  styles: [
    `
      :host {
        display: flex;
        flex: 1;
        min-height: 0;
        align-items: center;
        justify-content: center;
        padding: var(--space-3);
      }
      .crc__center {
        font-size: 18px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class ConcentricRingsChart {
  /** Anneaux, du plus EXTÉRIEUR au plus intérieur (progression / couleur / épaisseur / étiquette). */
  readonly rings = input.required<ConcentricRing[]>();
  /** Libellé central optionnel (ex. total). Vide = pas de libellé central. */
  readonly centerText = input('');
  /** Couleur du libellé central. */
  readonly centerColor = input('var(--app-text-primary)');
  /** Diamètre de la pile d'anneaux (unités viewBox). */
  readonly size = input(220);
  /** Espace entre deux anneaux consécutifs. */
  readonly gap = input(5);
  /** Se réduit pour tenir dans la hauteur du conteneur (parent flex à hauteur définie) au lieu d'être
   *  piloté seulement par la largeur. Off par défaut. Utile dans une cellule à hauteur contrainte. */
  readonly fitHeight = input(false);
}
