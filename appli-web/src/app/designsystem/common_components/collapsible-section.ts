import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Section repliable légère : titre (gauche) + CTA chevron « Afficher / Masquer <cta> » en bleu accent
 * (droite), puis le contenu projeté déroulé en animation (grid-rows 0fr↔1fr + clip, même motion que
 * les autres déroulés de l'app). `open` en two-way, replié par défaut. Reprend le bandeau micros du
 * résumé nutrition — p.ex. replier la section micros des panneaux de filtres (catalogue, recettes…).
 */
@Component({
  selector: 'app-collapsible-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="cs__header">
      <button type="button" class="cs__toggle" [attr.aria-expanded]="open()" (click)="open.set(!open())">
        @if (title()) {
          <span class="cs__title">{{ title() }}</span>
        }
        <span class="cs__cta">
          <app-icon [name]="open() ? 'expand_less' : 'expand_more'" [size]="18" color="var(--app-accent-text)" />
          {{ open() ? 'Masquer ' + cta() : 'Afficher ' + cta() }}
        </span>
      </button>
      <!-- Action optionnelle alignée à droite du bandeau (même ligne que le chevron), p.ex. Réinitialiser. -->
      <ng-content select="[header-trailing]" />
    </div>
    <div class="cs__reveal" [class.cs__reveal--open]="open()">
      <div class="cs__clip">
        <div class="cs__body">
          <ng-content />
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      /* Bandeau : bouton repliable (flex:1) + une éventuelle action projetée à droite (même ligne). */
      .cs__header {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        width: 100%;
      }
      /* Bouton repliable : titre (gauche) + CTA chevron « Afficher/Masquer » (droite). */
      .cs__toggle {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        background: transparent;
        border: none;
        padding: 0;
        cursor: pointer;
      }
      .cs__title {
        color: var(--c-gray-blue);
        font-size: 14px;
        font-weight: 600;
      }
      .cs__cta {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        flex-shrink: 0;
        color: var(--app-accent-text);
        font-family: var(--font-family-base);
        font-size: 12px;
        white-space: nowrap;
      }
      /* Déroulé animé : hauteur 0fr↔1fr + clip (même motion que les autres déroulés de l'app). */
      .cs__reveal {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows var(--motion-base) var(--motion-ease);
      }
      .cs__reveal--open {
        grid-template-rows: 1fr;
      }
      .cs__clip {
        overflow: hidden;
        min-height: 0;
      }
      .cs__body {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding-top: var(--space-2);
      }
      @media (prefers-reduced-motion: reduce) {
        .cs__reveal {
          transition: none;
        }
      }
    `,
  ],
})
export class CollapsibleSection {
  /** Titre affiché à gauche du bandeau. */
  readonly title = input('');
  /** Suffixe du CTA : « les micros » → « Afficher les micros » / « Masquer les micros ». */
  readonly cta = input('');
  /** État ouvert/fermé (two-way) ; replié par défaut. */
  readonly open = model(false);
}
