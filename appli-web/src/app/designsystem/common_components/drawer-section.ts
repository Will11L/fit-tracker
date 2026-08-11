import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Section du drawer — miroir de DrawerSection.kt / Figma M12 : titre centré MAJUSCULE 13px Bold
 * (accentText) + divider supérieur (2px, divider-strong @ 0.6, inset 20) + liste d'items projetés
 * (`<ng-content>`, typiquement des DrawerItem qui portent eux-mêmes leur hairline inter-items).
 * Fond bgRecessed.
 *
 * Mode accordéon (`collapsible`) : l'en-tête devient cliquable (émet `headerClick`) et les items se
 * déroulent/enroulent selon `open` (état piloté par le parent — plusieurs sections peuvent être
 * ouvertes). L'en-tête diffère selon le pli :
 *  - drawer déplié : titre texte centré + chevron (expand_more / expand_less) ;
 *  - rail (`collapsed`, 56px) : icône-titre `icon` cliquable (le titre texte ne rentrerait pas).
 * Le déroulé/enroulé est **animé** dans les deux modes via le trick CSS grid `0fr↔1fr` (anime la
 * hauteur sans connaître le contenu, sans JS). Mode non-`collapsible` : titre statique, items
 * toujours visibles (usage vitrine / DrawerSection figée).
 */
@Component({
  selector: 'app-drawer-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="ds" [class.ds--collapsed]="collapsed()">
      @if (collapsed()) {
        @if (collapsible()) {
          <!-- rail : icône-titre cliquable = toggle de la section (déroule/enroule ses items) -->
          <button
            type="button"
            class="ds__railicon ds__railicon--button"
            [class.is-open]="open()"
            [attr.aria-expanded]="open()"
            [attr.aria-label]="title()"
            [attr.title]="title()"
            (click)="headerClick.emit()"
          >
            <app-icon
              [name]="icon()"
              [size]="20"
              [color]="open() ? 'var(--app-accent-text)' : 'var(--app-text-secondary)'"
            />
          </button>
        } @else if (icon()) {
          <div class="ds__railicon" [attr.title]="title()" [attr.aria-label]="title()">
            <app-icon [name]="icon()" [size]="20" color="var(--app-accent-text)" />
          </div>
        }
      } @else if (collapsible()) {
        <button
          type="button"
          class="ds__title ds__title--button"
          [attr.aria-expanded]="open()"
          (click)="headerClick.emit()"
        >
          @if (icon()) {
            <app-icon [name]="icon()" [size]="18" color="var(--app-accent-text)" />
          }
          <span>{{ title() }}</span>
          <app-icon
            class="ds__chevron"
            [name]="open() ? 'expand_less' : 'expand_more'"
            [size]="20"
            color="var(--app-accent-text)"
          />
        </button>
      } @else {
        <div class="ds__title">{{ title() }}</div>
      }
      <!-- wrapper animé : grid-template-rows 0fr (fermé) -> 1fr (ouvert). Non-collapsible = toujours ouvert.
           Le divider vit DANS le wrapper -> il s'enroule/déroule avec les items (pas en rail : icône seule). -->
      <div class="ds__itemswrap" [class.is-open]="!collapsible() || open()">
        <div class="ds__items">
          @if (!collapsed()) {
            <div class="ds__divider"></div>
          }
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
      .ds {
        background: var(--app-bg-recessed);
        padding: var(--space-3) 0 var(--space-2);
      }
      .ds__title {
        text-align: center;
        text-transform: uppercase;
        color: var(--app-accent-text);
        font-size: 13px;
        font-weight: var(--font-weight-bold);
        letter-spacing: 1px;
        padding: 0 var(--space-4) var(--space-2);
      }
      /* Titre cliquable (accordéon) : on conserve l'aspect du titre, on ajoute le chevron à droite. */
      .ds__title--button {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-2);
        width: 100%;
        background: none;
        border: none;
        cursor: pointer;
        font-family: var(--font-family-base);
        /* Centre le libellé (le chevron déborde à droite, n'altère pas le centrage visuel). */
        position: relative;
      }
      .ds__chevron {
        position: absolute;
        right: var(--space-4);
      }
      .ds__divider {
        height: 2px;
        background: color-mix(in srgb, var(--app-divider-strong) 60%, transparent);
        margin: 0 20px var(--space-1);
        border-radius: 1px;
      }
      /* Wrapper animé : la grille passe de 0fr (fermé) à 1fr (ouvert) -> hauteur animée sans JS. */
      .ds__itemswrap {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows 220ms ease;
      }
      .ds__itemswrap.is-open {
        grid-template-rows: 1fr;
      }
      .ds__items {
        display: flex;
        flex-direction: column;
        overflow: hidden;
        min-height: 0;
      }
      /* mode rail : icône-titre centrée en tête de groupe (le titre texte ne rentre pas dans 56px). */
      .ds__railicon {
        display: flex;
        justify-content: center;
        padding: 0 0 var(--space-2);
      }
      /* rail cliquable : l'icône-titre devient un bouton (toggle), sans altérer le centrage. */
      .ds__railicon--button {
        width: 100%;
        background: none;
        border: none;
        cursor: pointer;
      }
      @media (prefers-reduced-motion: reduce) {
        .ds__itemswrap {
          transition: none;
        }
      }
    `,
  ],
})
export class DrawerSection {
  readonly title = input('');
  /** Icône représentant la section, affichée en tête de groupe en mode rail (à la place du titre). */
  readonly icon = input('');
  readonly collapsed = input(false);
  /** Active le mode accordéon (titre cliquable + chevron + masquage conditionnel des items). */
  readonly collapsible = input(false);
  /** Section dépliée (items visibles). Piloté par le parent. Ignoré si `collapsible` est faux. */
  readonly open = input(true);
  /** Clic sur le titre (mode accordéon) : le parent bascule l'ouverture. */
  readonly headerClick = output<void>();
}
