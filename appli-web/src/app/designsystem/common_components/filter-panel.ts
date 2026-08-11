import { ChangeDetectionStrategy, Component, model } from '@angular/core';

/**
 * Cadre de filtres repliable réutilisable — le « cadre filtre » du Catalogue d'aliments. Un conteneur
 * recessed animé (grid-rows 0fr↔1fr + clip, même motion que les autres déroulés de l'app) qui projette
 * son contenu : facettes (catégorie), sections de seuils macros/micros, section repliable micros, etc.
 *
 * Le composant ne porte QUE le cadre : le déclencheur (bouton « Filtres ») et le « Réinitialiser » sont
 * fournis par la page (bouton dans la toolbar, reset dans l'en-tête de la section micros) — c'est ce que
 * fait le catalogue. `container-type: inline-size` sur le cadre → les grilles internes passent à 2
 * colonnes selon la largeur du cadre (pas du viewport). `open` en two-way.
 *
 * `margin-top` négatif à l'état fermé (porté par l'hôte, l'élément du flux/flex de la page) = annule un
 * cran de gap parent (`space-2`) pour ne pas laisser d'espace mort sous le déclencheur quand c'est replié.
 */
@Component({
  selector: 'app-filter-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.fp-host--open]': 'open()',
  },
  template: `
    <div class="fp" [class.fp--open]="open()">
      <div class="fp__clip">
        <div class="fp__frame">
          <ng-content />
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      /* L'hôte est l'élément du flux de la page (souvent enfant flex) : margin-top négatif fermé = annule
         un cran de gap parent (space-2) → pas d'espace mort sous le déclencheur. */
      :host {
        display: block;
        margin-top: calc(-1 * var(--space-2));
        transition: margin-top var(--motion-base) var(--motion-ease);
      }
      :host(.fp-host--open) {
        margin-top: 0;
      }
      /* Déroulé animé du cadre (grid-rows 0fr↔1fr + clip). */
      .fp {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows var(--motion-base) var(--motion-ease);
      }
      .fp--open {
        grid-template-rows: 1fr;
      }
      .fp__clip {
        overflow: hidden;
        min-height: 0;
      }
      /* Cadre recessed ; conteneur de requête → les grilles projetées passent à 2 colonnes selon sa largeur. */
      .fp__frame {
        container-type: inline-size;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3) var(--space-4) var(--space-4);
      }
      @media (prefers-reduced-motion: reduce) {
        :host,
        .fp {
          transition: none;
        }
      }
    `,
  ],
})
export class FilterPanel {
  /** État ouvert/fermé du cadre (two-way). Le déclencheur « Filtres » est fourni par la page. */
  readonly open = model(false);
}
