import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { LocalFood } from '@core/models/food.model';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { AppIcon } from '@designsystem/icons/app-icon';
import { effectiveFoodKcal } from './food-kcal';
import { MACRO_COLOR, SUGAR_COLOR } from './macro-colors';
import { microLineItems } from './micro-colors';
import { isHighSugar } from './journal-utils';
import { foodGroupColor, foodGroupLabel } from './food-category';

/**
 * Ligne d'aliment du catalogue (rendu commun extrait du FoodPickerSheet) : nom + badge catégorie,
 * ligne kcal/P/G/L per-100 g, ligne micros (colorés par famille via micro-colors), et étoile favori.
 * Le clic émet `rowClicked` (détail / sélection selon le parent), l'étoile émet `favToggled` sans
 * propager le clic.
 *
 * `collapsibleMicros` (off par défaut, ex. picker = micros toujours visibles en ligne) : quand activé
 * (catalogue), les micros sont masqués et révélés par un chevron bleu à droite qui déroule la row
 * (animation hauteur 0fr↔1fr, même motion que ExpandableCard / les rows de repas).
 */
@Component({
  selector: 'app-food-list-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionIconButton, AppIcon],
  template: `
    <div
      class="frow"
      [class.frow--selected]="selected()"
      [class.frow--open]="collapsibleMicros() && expanded()"
    >
      <div class="frow__top" (click)="rowClicked.emit(food())">
        <div class="frow__main">
          <div class="frow__head">
            <span class="frow__name">{{ food().name }}</span>
            @if (food().foodGroup) {
              <span class="frow__badge" [style.--badge-c]="badgeColor()">{{ badgeLabel() }}</span>
            }
          </div>
          <!-- Chevron des micros (mode repliable) juste après le « /100 g », comme sur Android. -->
          <span class="frow__subline">
            <span class="frow__sub">
              @if (food().brand) {
                {{ food().brand }} ·
              }
              <span [style.color]="macro.kcal">{{ round(kcal()) }} kcal</span> ·
              <span [style.color]="macro.carbs">G {{ round1(food().carbsPer100g) }}</span> ·
              <span [style.color]="macro.fat">L {{ round1(food().fatPer100g) }}</span> ·
              <span [style.color]="macro.protein">P {{ round1(food().proteinPer100g) }}</span>
              @if (food().fiberPer100g !== null) {
                · <span [style.color]="macro.fiber">F {{ round1(food().fiberPer100g!) }}</span>
              }
              @if (food().sugarPer100g !== null) {
                · <span [style.color]="sugarTint()">S {{ round1(food().sugarPer100g!) }}</span>
              }
              /100 g
            </span>
            @if (collapsibleMicros() && micros().length) {
              <button
                type="button"
                class="frow__chevron"
                [attr.aria-expanded]="expanded()"
                aria-label="Afficher les micronutriments"
                (click)="toggleExpanded($event)"
              >
                <app-icon name="chevron_right" [size]="18" color="var(--app-primary-action)" />
              </button>
            }
          </span>
          <!-- Mode NON repliable (ex. picker) : micros toujours visibles en ligne sous les macros. -->
          @if (!collapsibleMicros() && micros().length) {
            <span class="frow__micros">
              @for (mi of micros(); track $index) {
                <span [style.color]="mi.color">{{ mi.short }} {{ mi.value }} {{ mi.unit }}</span
                >{{ $last ? '' : ' · ' }}
              }
            </span>
          }
        </div>
        <!-- Convention boutons de rows : favori actif = fond orange + icône blanche ; inactif = fond
             neutre + icône blanche (tint=text-primary, lisible en clair). -->
        <app-action-icon-button
          [icon]="food().isFavorite ? 'star' : 'star_border'"
          [backgroundColor]="food().isFavorite ? 'var(--app-favorite)' : 'var(--app-bg-button)'"
          [tint]="food().isFavorite ? 'var(--app-on-accent)' : 'var(--app-text-primary)'"
          (clicked)="favToggled.emit(food())"
          (click)="$event.stopPropagation()"
        />
      </div>
      <!-- Déroulé animé des micros (mode repliable) : hauteur 0fr↔1fr + clip (motion ExpandableCard). -->
      @if (collapsibleMicros() && micros().length) {
        <div class="frow__reveal">
          <div class="frow__clip">
            <span class="frow__micros frow__micros--body">
              @for (mi of micros(); track $index) {
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
      .frow {
        display: flex;
        flex-direction: column;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
      }
      /* Partie haute cliquable (ouvre le détail) : nom/macros (+ micros en ligne hors repliable) · étoile · chevron. */
      .frow__top {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        cursor: pointer;
      }
      .frow__top:hover {
        filter: brightness(1.08);
      }
      /* Ligne active du master/détail : liseré primaryAction (l'aliment dont le détail est ouvert). */
      .frow--selected {
        box-shadow: inset 0 0 0 1px var(--app-primary-action);
      }
      .frow__main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        /* Un peu d'air entre le nom, la ligne kcal/G/L/P et la ligne des micros. */
        gap: 4px;
      }
      /* Nom + badge catégorie sur la même ligne (le badge s'aligne après le nom). */
      .frow__head {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        min-width: 0;
        flex-wrap: wrap;
      }
      .frow__name {
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
      }
      /* Pastille colorée par groupe (mnémotechnique) : teinte du token --food-grp-* en fond léger + texte plein. */
      .frow__badge {
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
      /* Sous-ligne (marque · macros /100 g) + chevron des micros collé après (parité Android). */
      .frow__subline {
        display: flex;
        align-items: center;
        gap: 2px;
        min-width: 0;
      }
      /* « · » + texte en gris-bleu ; valeurs macro colorées via leurs spans. */
      .frow__sub {
        color: var(--c-gray-blue);
        font-size: 12px;
      }
      /* Micros colorés par famille (token via micro-colors) : chaque span porte sa propre teinte. */
      .frow__micros {
        font-size: 11px;
        opacity: 0.9;
      }
      /* Chevron bleu (mode repliable) : pointe à droite (replié), pivote vers le bas une fois déroulé.
         Compact (24px) pour ne pas gonfler la hauteur de la sous-ligne. */
      .frow__chevron {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 24px;
        height: 24px;
        flex-shrink: 0;
        padding: 0;
        border: none;
        background: transparent;
        cursor: pointer;
        transition: transform var(--motion-base) var(--motion-ease);
      }
      .frow--open .frow__chevron {
        transform: rotate(90deg);
      }
      /* Déroulé/enroulé animé des micros : la grille anime la hauteur (0fr↔1fr), le clip masque le débordement. */
      .frow__reveal {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows var(--motion-base) var(--motion-ease);
      }
      .frow--open .frow__reveal {
        grid-template-rows: 1fr;
      }
      .frow__clip {
        overflow: hidden;
        min-height: 0;
      }
      .frow__micros--body {
        display: block;
        padding-top: 6px;
      }
      /* a11y : pas d'animation si l'utilisateur a demandé moins de mouvement. */
      @media (prefers-reduced-motion: reduce) {
        .frow__chevron,
        .frow__reveal {
          transition: none;
        }
      }
    `,
  ],
})
export class FoodListRow {
  readonly food = input.required<LocalFood>();
  /** Surlignage master/détail : l'aliment dont le détail est actuellement ouvert. */
  readonly selected = input(false);
  /** Micros masqués + révélés par un chevron (catalogue). Off = micros en ligne (picker, inchangé). */
  readonly collapsibleMicros = input(false);
  readonly rowClicked = output<LocalFood>();
  readonly favToggled = output<LocalFood>();

  /** Déroulé des micros (mode repliable). Replié par défaut. */
  protected readonly expanded = signal(false);

  /** Code couleur par macro (P/G/L/kcal), partagé avec le bandeau du Journal. */
  protected readonly macro = MACRO_COLOR;

  /** kcal effective per-100g selon la source (D12) : dérivée pour un brut CIQUAL, étiquette OFF. */
  protected readonly kcal = computed(() => effectiveFoodKcal(this.food()));
  /** Teinte des sucres : alerte si aliment riche (> 22,5 g/100 g, repère UK), sinon --macro-sugar. */
  protected readonly sugarTint = computed(() =>
    isHighSugar(this.food().sugarPer100g) ? 'var(--app-snackbar-warning)' : SUGAR_COLOR,
  );
  /** Micros présents (per-100 g), colorés par famille (minéraux rouge / vitamines doré). */
  protected readonly micros = computed(() => microLineItems(this.food()));

  /** Badge catégorie : label FR + couleur mnémotechnique du groupe (affiché seulement si foodGroup posé). */
  protected readonly badgeLabel = computed(() => foodGroupLabel(this.food().foodGroup));
  protected readonly badgeColor = computed(() => foodGroupColor(this.food().foodGroup));

  /** Chevron : déroule/enroule les micros sans propager le clic (sinon ouvrirait le détail). */
  protected toggleExpanded(event: Event): void {
    event.stopPropagation();
    this.expanded.set(!this.expanded());
  }

  protected round(v: number): number {
    return Math.round(v);
  }
  protected round1(v: number): number {
    return Math.round(v * 10) / 10;
  }
}
