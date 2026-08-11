import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { LocalFood } from '@core/models/food.model';
import { LocalFoodPortion } from '@core/models/food-portion.model';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { SegmentedIconToggle, type SegmentItem } from '@designsystem/common_components/segmented-icon-toggle';
import { NutritionSummaryPanel, type MacroAmounts, type SummaryDisplay } from './nutrition-summary-panel';
import { parseMacro } from './food-picker-sheet';
import { effectiveFoodKcal, FOOD_SOURCE } from './food-kcal';
import { foodGroupColor, foodGroupLabel } from './food-category';

/**
 * Détail d'un aliment du catalogue (panneau master/détail T5) — affiché à droite de la liste sur
 * desktop, ou dans un bottom-sheet sur mobile étroit. Résumé visuel macros + micros via le panneau
 * réutilisable T4 (`NutritionSummaryPanel`, barres / radar / anneaux au choix), portions nommées (ajout /
 * suppression), et actions (modifier les aliments perso CUSTOM, archiver / restaurer, supprimer).
 * Composant présentationnel : toutes les écritures remontent en outputs vers la page (qui détient
 * le FoodRepository) ; seul l'état de saisie de portion + le mode de résumé sont locaux.
 */
@Component({
  selector: 'app-food-detail-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TitledDivider,
    EmptyListRow,
    ActionIconButton,
    CustomTextField,
    SegmentedIconToggle,
    NutritionSummaryPanel,
  ],
  template: `
    <div class="detail">
      <app-titled-divider title="Aliment" />

      <!--
        Carte aliment (non repliable, toujours déroulée) : en-tête secondBlue en 3 tiers — nom (tiers
        gauche, tronqué « … » si trop long), badge catégorie centré (tiers milieu), actions en icône
        seulement (modifier custom / archiver-restaurer / supprimer) sur le tiers droite. En dessous,
        le corps thirdBlue = le résumé macros/micros. La bascule d'affichage (barres / radar / anneaux)
        reste projetée dans l'en-tête du panneau (slot [panelToggle]).
      -->
      <div class="detail__card">
        <div class="detail__head" [class.detail__head--no-badge]="!food().foodGroup">
          <span class="detail__name">{{ food().name }}</span>
          @if (food().foodGroup) {
            <div class="detail__cats">
              <span class="detail__badge" [style.--badge-c]="badgeColor()">{{ badgeLabel() }}</span>
            </div>
          }
          <div class="detail__actions">
            @if (isCustom()) {
              <app-action-icon-button
                icon="edit"
                backgroundColor="var(--c-blue-medium)"
                (clicked)="edit.emit(food())"
              />
            }
            <app-action-icon-button
              [icon]="food().archived ? 'unarchive' : 'archive'"
              backgroundColor="var(--c-first-blue)"
              (clicked)="archiveToggle.emit(food())"
            />
            <app-action-icon-button
              icon="delete"
              backgroundColor="var(--app-btn-danger-bg)"
              tint="var(--app-btn-danger-fg)"
              (clicked)="remove.emit(food())"
            />
          </div>
        </div>

        <app-nutrition-summary-panel
          [kcal]="kcal()"
          [macros]="macros()"
          [micros]="food()"
          [sugar]="food().sugarPer100g"
          [display]="summaryDisplay()"
          [sectionHeadings]="true"
          unitSuffix="/ 100 g"
        >
          <app-segmented-icon-toggle
            panelToggle
            [items]="displayItems"
            [selected]="summaryDisplay()"
            (select)="setDisplay($event)"
          />
        </app-nutrition-summary-panel>
      </div>

      <app-titled-divider title="Portions" />
      <!-- Portions en chips : tap un chip → édition inline (libellé · grammes). -->
      <div class="portions">
        @if (sortedPortions().length === 0) {
          <app-empty-list-row text="Aucune portion nommée." [verticalPadding]="0" />
        }
        @for (p of sortedPortions(); track p.uuid) {
          @if (editingUuid() === p.uuid) {
            <!-- Édition inline : libellé + grammes, puis valider / annuler / supprimer. -->
            <div class="portion-edit">
              <app-custom-text-field
                class="portion-edit__label"
                placeholder="Nom (ex. 1 œuf)"
                [value]="epLabel()"
                (valueChange)="epLabel.set($event)"
              />
              <app-custom-text-field
                class="portion-edit__grams"
                placeholder="g"
                type="number"
                [value]="epGrams()"
                (valueChange)="epGrams.set($event)"
              />
              <app-action-icon-button
                icon="check"
                tint="var(--app-primary-action)"
                [disabled]="!editValid()"
                (clicked)="saveEdit(p.uuid)"
              />
              <app-action-icon-button icon="close" (clicked)="cancelEdit()" />
              <app-action-icon-button
                icon="delete"
                backgroundColor="var(--app-btn-danger-bg)"
                tint="var(--app-btn-danger-fg)"
                (clicked)="portionRemove.emit(p.uuid)"
              />
            </div>
          } @else {
            <!-- Chip cliquable : libellé · grammes. Tap → mode édition. -->
            <button type="button" class="portion" (click)="startEdit(p)">
              <span class="portion__label">{{ p.label }}</span>
              <span class="portion__grams">{{ round(p.grams) }} g</span>
            </button>
          }
        }
      </div>
      <div class="portion-add">
        <app-custom-text-field
          class="portion-add__label"
          placeholder="Nom (ex. 1 œuf)"
          [value]="pLabel()"
          (valueChange)="pLabel.set($event)"
        />
        <app-custom-text-field
          class="portion-add__grams"
          placeholder="g"
          type="number"
          [value]="pGrams()"
          (valueChange)="pGrams.set($event)"
        />
        <app-action-icon-button
          icon="add"
          backgroundColor="var(--app-primary-action)"
          [disabled]="!portionValid()"
          (clicked)="addPortion()"
        />
      </div>
    </div>
  `,
  styles: [
    `
      .detail {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* Carte aliment (non repliable, toujours déroulée) : en-tête secondBlue + corps thirdBlue raccordés. */
      .detail__card {
        display: flex;
        flex-direction: column;
      }
      /* En-tête flex : nom (tronqué « … » si long) · badge catégorie (occupe l'espace entre le nom et
         les actions, centré, minimum réservé) · actions (collées à droite). */
      .detail__head {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--c-second-blue);
        border-radius: var(--radius-md) var(--radius-md) 0 0;
        padding: var(--space-1) var(--space-2) var(--space-1) var(--space-3);
      }
      /* Nom, tronqué « … » si trop long ; ne grandit pas (laisse la place au badge). */
      .detail__name {
        flex: 0 1 auto;
        min-width: 0;
        color: var(--app-text-primary);
        font-size: 15px;
        font-weight: var(--font-weight-medium);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      /* Sans badge, le nom reprend toute la place (pousse les actions à droite). */
      .detail__head--no-badge .detail__name {
        flex: 1 1 auto;
      }
      /* Conteneur du badge : occupe l'espace entre la fin du nom et les actions (centré), minimum
         réservé → un nom long tronque plutôt que d'écraser le badge. */
      .detail__cats {
        flex: 1 1 auto;
        min-width: 6rem;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      /* Badge catégorie coloré par groupe. */
      .detail__badge {
        font-size: 11px;
        font-weight: var(--font-weight-medium);
        line-height: 1;
        padding: 4px 9px;
        border-radius: 999px;
        /* Texte plus vif (luminosité + saturation rehaussées) pour mieux ressortir sur la teinte ; repli = couleur brute. */
        color: var(--badge-c);
        color: oklch(from var(--badge-c) calc(l + 0.1) calc(c * 1.25) h);
        background: color-mix(in srgb, var(--badge-c) 20%, transparent);
      }
      /* Le résumé (panneau, fond thirdBlue) = corps de la carte : coins HAUTS à plat pour se raccorder
         sans couture à l'en-tête secondBlue. Scopé à ce panneau (autres usages du panneau intacts). */
      .detail__card ::ng-deep .nsp {
        border-top-left-radius: 0;
        border-top-right-radius: 0;
      }
      /* Liste des portions : chips qui passent à la ligne. */
      .portions {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--space-2);
      }
      /* Chip portion cliquable (tap → édition) façon dialog : fond thirdBlue, bordure + texte blue-medium, pilule. */
      .portion {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--c-third-blue);
        border: 1.5px solid var(--c-blue-medium);
        border-radius: var(--radius-pill);
        padding: 6px var(--space-3);
        line-height: 1;
        cursor: pointer;
        font: inherit;
      }
      .portion:hover {
        border-color: var(--c-light-gray-blue);
      }
      .portion__label {
        color: var(--c-blue-medium);
        font-size: 14px;
      }
      .portion__grams {
        color: var(--c-blue-medium);
        font-size: 13px;
      }
      /* En édition : le formulaire prend sa propre ligne pleine largeur dans le wrap. */
      .portion-edit {
        flex: 1 1 100%;
      }
      .portion-add,
      .portion-edit {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .portion-add__label,
      .portion-edit__label {
        flex: 1;
        min-width: 0;
      }
      .portion-add__grams,
      .portion-edit__grams {
        width: 72px;
        flex-shrink: 0;
      }
      /* Actions en icône seulement, collées à droite de l'en-tête. */
      .detail__actions {
        flex-shrink: 0;
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
    `,
  ],
})
export class FoodDetailPanel {
  /** Aliment affiché. */
  readonly food = input.required<LocalFood>();
  /** Portions nommées de l'aliment (non triées — le panneau trie par grammage croissant). */
  readonly portions = input<LocalFoodPortion[]>([]);

  readonly edit = output<LocalFood>();
  readonly archiveToggle = output<LocalFood>();
  readonly remove = output<LocalFood>();
  readonly portionAdd = output<{ label: string; grams: number }>();
  readonly portionUpdate = output<{ uuid: string; label: string; grams: number }>();
  readonly portionRemove = output<string>();

  /** Mode du résumé visuel (T4) : radar (défaut = 1er du sélecteur), anneaux ou barres. */
  protected readonly summaryDisplay = signal<SummaryDisplay>('radar');
  protected readonly displayItems: SegmentItem[] = [
    { value: 'radar', icon: 'radar', description: 'Radar' },
    { value: 'rings', icon: 'donut_large', description: 'Anneaux' },
    { value: 'bar', icon: 'bar_chart', description: 'Barres' },
  ];

  protected readonly kcal = computed(() => effectiveFoodKcal(this.food()));
  protected readonly macros = computed<MacroAmounts>(() => ({
    protein: this.food().proteinPer100g,
    carbs: this.food().carbsPer100g,
    fat: this.food().fatPer100g,
    fiber: this.food().fiberPer100g,
  }));
  protected readonly isCustom = computed(() => this.food().source === FOOD_SOURCE.CUSTOM);
  protected readonly sortedPortions = computed(() =>
    [...this.portions()].sort((a, b) => a.grams - b.grams),
  );

  /** Badge catégorie : label FR + couleur mnémotechnique du groupe (affiché seulement si foodGroup posé). */
  protected readonly badgeLabel = computed(() => foodGroupLabel(this.food().foodGroup));
  protected readonly badgeColor = computed(() => foodGroupColor(this.food().foodGroup));

  // -------------------- Ajout de portion (état local de saisie) --------------------

  protected readonly pLabel = signal('');
  protected readonly pGrams = signal('');
  protected readonly portionValid = computed(
    () => this.pLabel().trim().length > 0 && (parseMacro(this.pGrams()) ?? 0) > 0,
  );

  protected addPortion(): void {
    const grams = parseMacro(this.pGrams());
    if (this.pLabel().trim().length === 0 || !grams || grams <= 0) return;
    this.portionAdd.emit({ label: this.pLabel().trim(), grams });
    this.pLabel.set('');
    this.pGrams.set('');
  }

  // -------------------- Édition d'une portion (état local de saisie) --------------------

  /** UUID de la portion en cours d'édition (null = aucune). Une seule ligne éditable à la fois. */
  protected readonly editingUuid = signal<string | null>(null);
  protected readonly epLabel = signal('');
  protected readonly epGrams = signal('');
  protected readonly editValid = computed(
    () => this.epLabel().trim().length > 0 && (parseMacro(this.epGrams()) ?? 0) > 0,
  );

  protected startEdit(p: LocalFoodPortion): void {
    this.editingUuid.set(p.uuid);
    this.epLabel.set(p.label);
    this.epGrams.set(String(p.grams));
  }

  protected cancelEdit(): void {
    this.editingUuid.set(null);
    this.epLabel.set('');
    this.epGrams.set('');
  }

  protected saveEdit(uuid: string): void {
    const grams = parseMacro(this.epGrams());
    if (this.epLabel().trim().length === 0 || !grams || grams <= 0) return;
    this.portionUpdate.emit({ uuid, label: this.epLabel().trim(), grams });
    this.cancelEdit();
  }

  protected setDisplay(value: string): void {
    this.summaryDisplay.set(value as SummaryDisplay);
  }

  protected round(v: number): number {
    return Math.round(v);
  }

  constructor() {
    // Changer d'aliment réinitialise la saisie/édition de portion en cours (évite de reporter un libellé).
    let lastUuid: string | null = null;
    effect(() => {
      const uuid = this.food().uuid;
      if (uuid !== lastUuid) {
        lastUuid = uuid;
        this.pLabel.set('');
        this.pGrams.set('');
        this.cancelEdit();
      }
    });
  }
}
