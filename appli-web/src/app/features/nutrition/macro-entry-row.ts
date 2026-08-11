import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { MACRO_COLOR, SUGAR_COLOR } from './macro-colors';
import type { MicroLineItem } from './micro-colors';

/**
 * Données d'une ligne aliment / ingrédient : nom + macros (kcal · G · L · P · F) colorées par macro
 * + micros présents (colorés par famille). Forme découplée du modèle (MealEntry, ingrédient de
 * recette…) → le parent construit la vue, le composant ne fait que l'afficher.
 */
export interface MacroEntryRowData {
  name: string;
  kcal: number;
  carbs: number;
  fat: number;
  protein: number;
  /** null = fibres inconnues → la part « F » est masquée (comme les entries du journal). */
  fiber: number | null;
  /** Micros présents (valeur non nulle), déjà colorés. Vide → pas de chevron ni de dépli. */
  micros: MicroLineItem[];
  /** Sucres consommés (g, à l'échelle de la quantité) → 1ʳᵉ valeur du dépli micros. null/absent = masqué. */
  sugarG?: number | null;
  /** Catégorie optionnelle (label + couleur) → pastille à côté du nom (ex. ingrédients des recettes). */
  category?: { label: string; color: string } | null;
}

/**
 * Ligne aliment / ingrédient réutilisable (design system nutrition) — extraite des lignes d'aliments
 * des cartes repas du journal (page Nutrition). Affiche le nom + les macros colorées (MACRO_COLOR),
 * un slot `[trailing]` projeté (grammes, menu ⋮, contrôles d'édition…), puis un chevron à droite qui
 * déroule les micros consommés (réutilisé par les cards repas ET la liste d'ingrédients des recettes).
 * Le dépli des micros est géré en interne (signal `expanded`) ; l'animation de hauteur suit la même
 * technique grid-template-rows (0fr↔1fr) que ExpandableCard.
 */
@Component({
  selector: 'app-macro-entry-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton],
  host: { '[class.mer--bare]': 'bare()' },
  template: `
    <div class="mer" [class.mer--divider]="divider() && !bare()">
      <div class="mer__row">
        <div class="mer__main">
          <span class="mer__head">
            <span class="mer__name">{{ data().name }}</span>
            @if (data().category; as cat) {
              <span class="mer__badge" [style.--badge-c]="cat.color">{{ cat.label }}</span>
            }
          </span>
          <!-- Chevron des micros collé aux macros (compact), comme la row du Catalogue. -->
          <span class="mer__macros-line">
            <span class="mer__macros">
              <span [style.color]="macro.kcal">{{ round(data().kcal) }} kcal</span> ·
              <span [style.color]="macro.carbs">G {{ round1(data().carbs) }}</span> ·
              <span [style.color]="macro.fat">L {{ round1(data().fat) }}</span> ·
              <span [style.color]="macro.protein">P {{ round1(data().protein) }}</span>
              @if (data().fiber !== null) {
                · <span [style.color]="macro.fiber">F {{ round1(data().fiber!) }}</span>
              }
            </span>
            @if (hasDetails()) {
              <app-action-icon-button
                [icon]="expanded() ? 'expand_less' : 'expand_more'"
                [size]="24"
                [iconSize]="18"
                [hasBackground]="false"
                tint="var(--app-primary-action)"
                (clicked)="expanded.set(!expanded())"
              />
            }
          </span>
        </div>
        <!-- Contenu projeté à droite (grammes, menu ⋮, contrôles d'édition). -->
        <ng-content select="[trailing]" />
      </div>
      @if (hasDetails()) {
        <div class="mer__micros-reveal" [class.mer__micros-reveal--open]="expanded()">
          <div class="mer__micros-clip">
            <span class="mer__micros">
              <!-- Sucres consommés (information, teinte dédiée) en tête du dépli. -->
              @if (data().sugarG != null) {
                <span [style.color]="sugarColor">Sucres {{ round1(data().sugarG!) }} g</span
                >{{ data().micros.length ? ' · ' : '' }}
              }
              @for (mi of data().micros; track $index) {
                <span [style.color]="mi.color">{{ mi.short }} {{ mi.value }} {{ mi.unit }}</span
                >{{ $last ? '' : ' · ' }}
              }
            </span>
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    `
      /* Hôte = bloc pleine largeur → les lignes s'empilent proprement dans la liste (sinon inline). */
      :host {
        display: block;
      }
      /* Mode « bare » : la ligne est projetée DANS un <app-list-row> (cadre + padding + filet ancré +
         coins fournis par la row du design system). Le composant ne fournit alors QUE son contenu :
         il remplit la row (flex item) et abandonne son propre padding + filet. Permet de réutiliser
         le même système de liste cadrée (app-list-frame / app-list-row) pour les lignes riches. */
      :host(.mer--bare) {
        flex: 1;
        min-width: 0;
      }
      :host(.mer--bare) .mer {
        padding: 0;
      }
      /* Ligne séparée par un filet secondBlue (comme les aliments d'une carte repas). Le parent pose
         [divider]="!$last" pour retirer le filet de la dernière ligne. */
      .mer {
        padding: 8px 0;
      }
      .mer--divider {
        border-bottom: 1px solid var(--c-second-blue);
      }
      .mer__row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .mer__main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      /* Nom + pastille catégorie optionnelle sur une ligne (la pastille ne se compresse pas). */
      .mer__head {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        min-width: 0;
      }
      .mer__name {
        color: var(--app-text-primary);
        font-size: 14px;
      }
      /* Avec une pastille, le nom tronque « … » pour ne pas l'écraser (sans pastille = rendu inchangé). */
      .mer__head:has(.mer__badge) .mer__name {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      /* Pastille catégorie colorée par groupe (même langage visuel que la row du Catalogue). */
      .mer__badge {
        flex-shrink: 0;
        font-size: 10px;
        font-weight: var(--font-weight-medium);
        line-height: 1;
        padding: 3px 7px;
        border-radius: 999px;
        /* Texte plus vif (luminosité + saturation rehaussées) pour mieux ressortir sur la teinte ; repli = couleur brute. */
        color: var(--badge-c);
        color: oklch(from var(--badge-c) calc(l + 0.1) calc(c * 1.25) h);
        background: color-mix(in srgb, var(--badge-c) 20%, transparent);
      }
      /* Macros + chevron des micros collé après (parité row Catalogue). */
      .mer__macros-line {
        display: flex;
        align-items: center;
        gap: 2px;
        min-width: 0;
      }
      /* Macros : valeurs colorées (spans) ; les « · » héritent du gris-bleu du conteneur. */
      .mer__macros {
        color: var(--c-gray-blue);
        font-size: 12px;
      }
      /* Micros consommés (repli/dépli animé) : la grille anime la hauteur, le clip masque. */
      .mer__micros-reveal {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows var(--motion-base) var(--motion-ease);
      }
      .mer__micros-reveal--open {
        grid-template-rows: 1fr;
      }
      .mer__micros-clip {
        overflow: hidden;
        min-height: 0;
      }
      /* a11y : pas d'animation si l'utilisateur a demandé moins de mouvement. */
      @media (prefers-reduced-motion: reduce) {
        .mer__micros-reveal {
          transition: none;
        }
      }
      /* Micros consommés colorés par famille (token via micro-colors) : un span coloré par micro. */
      .mer__micros {
        display: block;
        font-size: 11px;
        opacity: 0.9;
        padding-top: 4px;
      }
    `,
  ],
})
export class MacroEntryRow {
  readonly data = input.required<MacroEntryRowData>();
  /** Filet de séparation bas (entre lignes d'une liste) ; passer false sur la dernière ligne. */
  readonly divider = input(true);
  /** Projetée dans un `<app-list-row>` : abandonne padding + filet (fournis par la row du DS) et
      remplit la row. Ignore alors `divider` (le filet ancré est géré par `app-list-row`). */
  readonly bare = input(false);

  /** Dépli des micros (interne à la ligne). */
  protected readonly expanded = signal(false);
  protected readonly macro = MACRO_COLOR;
  protected readonly sugarColor = SUGAR_COLOR;

  /** Chevron + dépli présents si la ligne a des micros OU des sucres consommés à montrer. */
  protected readonly hasDetails = computed(
    () => this.data().micros.length > 0 || this.data().sugarG != null,
  );

  protected round(v: number): number {
    return Math.round(v);
  }
  protected round1(v: number): number {
    return Math.round(v * 10) / 10;
  }
}
