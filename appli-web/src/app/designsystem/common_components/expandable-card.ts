import { ChangeDetectionStrategy, Component, model } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Carte dépliable (design system) — en-tête **secondBlue** (`--c-second-blue`) portant 3 zones : le
 * contenu projeté `[header]` (info, à gauche), les boutons projetés `[actions]`, puis un **chevron
 * animé** tout à droite ; au-dessus d'un corps **thirdBlue** (`--app-bg-recessed`, contenu projeté par
 * défaut). Le chevron est le **seul** contrôle de dépli/repli : il pivote de 180°, et le corps se
 * déroule / enroule en animation de hauteur via `grid-template-rows` (0fr ↔ 1fr, sans mesure JS, même
 * famille de motion que ProgressRing). État via le modèle deux-voies `expanded` (défaut : déplié).
 * Testable isolément dans le showcase.
 */
@Component({
  selector: 'app-expandable-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="ec" [class.ec--open]="expanded()">
      <div class="ec__header">
        <div class="ec__info">
          <ng-content select="[header]" />
        </div>
        <ng-content select="[actions]" />
        <button
          type="button"
          class="ec__chevron"
          [attr.aria-expanded]="expanded()"
          aria-label="Déplier ou replier"
          (click)="expanded.set(!expanded())"
        >
          <app-icon name="expand_more" [size]="22" color="var(--app-primary-action)" />
        </button>
      </div>
      <!-- Corps déroulé/enroulé : la grille anime la hauteur (0fr↔1fr), le clip masque le débordement. -->
      <div class="ec__reveal">
        <div class="ec__clip">
          <div class="ec__body">
            <ng-content />
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .ec {
        display: flex;
        flex-direction: column;
      }
      /* En-tête secondBlue : info (gauche) · actions · chevron (droite). Arrondi complet si replié,
         arrondi haut seulement si déplié (se raccorde au corps thirdBlue). */
      .ec__header {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--c-second-blue);
        border-radius: var(--radius-md);
        padding: var(--space-1) var(--space-2) var(--space-1) var(--space-3);
      }
      .ec--open .ec__header {
        border-radius: var(--radius-md) var(--radius-md) 0 0;
      }
      .ec__info {
        flex: 1;
        min-width: 0;
      }
      /* Chevron : seul contrôle de dépli/repli, pivote 180° à l'ouverture. */
      .ec__chevron {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 34px;
        height: 34px;
        flex-shrink: 0;
        padding: 0;
        border: none;
        background: transparent;
        cursor: pointer;
        transition: transform var(--motion-base) var(--motion-ease);
      }
      .ec--open .ec__chevron {
        transform: rotate(180deg);
      }
      /* Corps déroulé/enroulé : animation de hauteur via grid-template-rows (0fr → 1fr). */
      .ec__reveal {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows var(--motion-base) var(--motion-ease);
      }
      .ec--open .ec__reveal {
        grid-template-rows: 1fr;
      }
      .ec__clip {
        overflow: hidden;
        min-height: 0;
      }
      /* Corps thirdBlue, raccordé sous l'en-tête (arrondi bas seulement). */
      .ec__body {
        background: var(--app-bg-recessed);
        border-radius: 0 0 var(--radius-md) var(--radius-md);
        /* Padding horizontal symétrique (space-3, = 12dp Android) : les boutons des rows du
           contenu respirent du bord droit (l'en-tête garde son space-2 propre). */
        padding: var(--space-1) var(--space-3) var(--space-2) var(--space-3);
      }
      /* a11y : pas d'animation si l'utilisateur a demandé moins de mouvement. */
      @media (prefers-reduced-motion: reduce) {
        .ec__chevron,
        .ec__reveal {
          transition: none;
        }
      }
    `,
  ],
})
export class ExpandableCard {
  /** État déplié (deux-voies). Défaut : déplié. */
  readonly expanded = model(true);
}
