import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { LocalFood } from '@core/models/food.model';
import { LocalRecipe } from '@core/models/recipe.model';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import {
  OptionsBottomSheet,
  type SheetAction,
} from '@designsystem/common_components/options-bottom-sheet';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { TabRowCustom } from '@designsystem/common_components/tab-row-custom';
import {
  SegmentedIconToggle,
  type SegmentItem,
} from '@designsystem/common_components/segmented-icon-toggle';
import { RevealIn } from '@designsystem/common_components/reveal-in';
import { ListFrame } from '@designsystem/common_components/list-frame';
import { ListRow } from '@designsystem/common_components/list-row';
import { SyncEngine } from '@core/sync/sync-engine';
import { FoodRepository } from './food.repository';
import { MealRepository } from './meal.repository';
import { RecipeRepository } from './recipe.repository';
import { FoodPickerSheet, parseMacro } from './food-picker-sheet';
import { JournalSection, buildSections, todayIso } from './journal-utils';
import { recipeMacros, splitRecipesByKind } from './recipe-utils';
import {
  NutritionSummaryPanel,
  type MacroAmounts,
  type SummaryDisplay,
} from './nutrition-summary-panel';
import { MACRO_COLOR } from './macro-colors';
import {
  FOOD_GROUPS,
  FOOD_GROUP_OPTIONS,
  FOOD_REALMS,
  FOOD_REALM_LABEL,
  foodGroupColor,
  foodGroupLabel,
  groupCodesForLabel,
  realmFromLabel,
  realmOf,
  type FoodRealm,
} from './food-category';
import {
  FILTERABLE_MACROS,
  FILTERABLE_MICROS,
  type NutrientKey,
  type NutrientThreshold,
  type ThresholdOp,
} from './food-catalogue';
import { FilterDropdown } from '@designsystem/common_components/filter-dropdown';
import { FilterThresholdRow } from '@designsystem/common_components/filter-threshold-row';
import { CollapsibleSection } from '@designsystem/common_components/collapsible-section';
import { FilterPanel } from '@designsystem/common_components/filter-panel';
import { MacroEntryRow, type MacroEntryRowData } from './macro-entry-row';
import { microLineItems } from './micro-colors';
import { effectiveFoodKcal } from './food-kcal';

/** Ligne d'ingrédient du brouillon d'édition (quantité en saisie libre, parsée à la sauvegarde). */
interface DraftIngredient {
  foodUUID: string;
  name: string;
  quantity: string;
}

/** Vue d'un ingrédient du plat sélectionné : identité (pour les actions ⋮) + données d'affichage de la ligne. */
interface IngredientRowVM {
  foodUUID: string;
  name: string;
  quantityG: number;
  data: MacroEntryRowData;
}

/**
 * Données d'affichage (macros + micros) d'une ligne d'ingrédient depuis le Food (référence vivante) et
 * sa quantité en grammes : per-100g × quantité/100, kcal effective selon la source (D12), micros
 * colorés par famille — même calcul que recipeMacros. Food absent (supprimé) → repli libellé + zéros.
 */
function ingredientRowData(
  food: LocalFood | undefined,
  quantityG: number,
  fallbackName = 'Aliment supprimé',
): MacroEntryRowData {
  if (!food) return { name: fallbackName, kcal: 0, carbs: 0, fat: 0, protein: 0, fiber: null, micros: [] };
  const f = quantityG / 100;
  return {
    name: food.name,
    kcal: effectiveFoodKcal(food) * f,
    carbs: food.carbsPer100g * f,
    fat: food.fatPer100g * f,
    protein: food.proteinPer100g * f,
    fiber: food.fiberPer100g != null ? food.fiberPer100g * f : null,
    micros: microLineItems(food, f),
    category: food.foodGroup
      ? { label: foodGroupLabel(food.foodGroup), color: foodGroupColor(food.foodGroup) }
      : null,
  };
}

/** Valeur per-100 g d'un nutriment d'une recette (depuis recipeMacros), pour le filtrage par seuil. */
function recipeNutrientValue(m: ReturnType<typeof recipeMacros>, key: NutrientKey): number {
  switch (key) {
    case 'kcalPer100g':
      return m.per100g.kcal;
    case 'proteinPer100g':
      return m.per100g.protein;
    case 'carbsPer100g':
      return m.per100g.carbs;
    case 'fatPer100g':
      return m.per100g.fat;
    case 'fiberPer100g':
      return m.per100g.fiber ?? 0;
    default:
      return (m.microPer100g as Record<string, number>)[key] ?? 0;
  }
}

/**
 * Recettes & repas enregistrés (`/nutrition/recipes`, V5 NUTRITION_DESIGN §5.4) — refonte
 * master/détail 2 colonnes (pattern materiel-page / food-catalogue-page). **Gauche** : la liste,
 * Plats (kind=RECIPE) en haut, Repas enregistrés (kind=SAVED_MEAL) en dessous ; chaque ligne en
 * `EntityListRow` (fond thirdBlue, barre d'actions à droite : ajouter au journal / ⋮). **Droite** :
 * le résumé du plat/repas sélectionné — macros + micros agrégés via `NutritionSummaryPanel` (T4) —
 * puis la liste de ses ingrédients (références vivantes vers Food). L'éditeur (nom, kind RECIPE avec
 * poids cuit / SAVED_MEAL, ingrédients réordonnables) reste en bottom-sheet. Action « Ajouter au
 * journal » : choix d'une période ; kind=SAVED_MEAL insère les ingrédients tels quels (un tap),
 * kind=RECIPE insère une entry au prorata du poids consommé (snapshot per-100g du plat).
 */
@Component({
  selector: 'app-nutrition-recipes-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet,
    ScreenTitleBar,
    TitledDivider,
    EmptyListRow,
    ActionIconButton,
    StyledSearchField,
    ActionIconWithTextButton,
    FormDialog,
    ConfirmationDialog,
    CustomTextField,
    OptionsBottomSheet,
    AppBottomSheet,
    TabRowCustom,
    SegmentedIconToggle,
    FoodPickerSheet,
    NutritionSummaryPanel,
    MacroEntryRow,
    FilterDropdown,
    FilterThresholdRow,
    CollapsibleSection,
    FilterPanel,
    RevealIn,
    ListFrame,
    ListRow,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Recettes & repas enregistrés" />

      <div class="page__body">
        <div class="split">
          <!-- Gauche : actions + liste (Plats puis Repas enregistrés), rows EntityListRow. -->
          <div class="split__list">
            <div class="toolbar">
              <app-action-icon-with-text-button
                icon="add"
                text="Ajouter"
                (clicked)="openCreate()"
              />
              <app-styled-search-field
                class="toolbar__search"
                [value]="search()"
                (valueChange)="search.set($event)"
                placeholderText="Rechercher…"
              />
              <app-action-icon-with-text-button
                icon="tune"
                [text]="filterButtonLabel()"
                [backgroundColor]="'var(--c-first-blue)'"
                (clicked)="filtersOpen.set(!filtersOpen())"
              />
            </div>

            <!-- Panneau de filtres repliable (parité catalogue) : facette catégorie + seuils macros/micros. -->
            <app-filter-panel [open]="filtersOpen()">
                <app-titled-divider title="Catégorie" />
                <div class="filters__category">
                  <app-filter-dropdown
                    label="Règne"
                    [options]="realmOptions()"
                    [selected]="realmFilter()"
                    (select)="realmFilter.set($event)"
                    [raised]="true"
                  />
                  <app-filter-dropdown
                    label="Groupe"
                    [options]="groupOptions()"
                    [selected]="groupFilter()"
                    (select)="groupFilter.set($event)"
                    [raised]="true"
                  />
                </div>
                @for (section of filterSections; track section.title) {
                  @if (section.collapsible) {
                    <app-collapsible-section
                      cta="les micros"
                      [open]="microsOpen()"
                      (openChange)="microsOpen.set($event)"
                    >
                      <app-action-icon-with-text-button
                        header-trailing
                        icon="filter_alt_off"
                        text="Réinitialiser"
                        [disabled]="activeFilterCount() === 0"
                        (clicked)="resetFilters()"
                      />
                      <app-titled-divider [title]="section.title" />
                      <div class="filters__grid">
                        @for (n of section.items; track n.key) {
                          <app-filter-threshold-row
                            [label]="n.abbr"
                            [labelWidth]="'3rem'"
                            [placeholder]="n.unit + '/100g'"
                            [step]="n.key === 'kcalPer100g' ? 10 : 1"
                            [op]="opFor(n.key)"
                            (opChange)="setOp(n.key, $event)"
                            [value]="rawFor(n.key)"
                            (valueChange)="setRaw(n.key, $event)"
                          />
                        }
                      </div>
                    </app-collapsible-section>
                  } @else {
                    <app-titled-divider [title]="section.title" />
                    <div class="filters__grid">
                      @for (n of section.items; track n.key) {
                        <app-filter-threshold-row
                          [label]="n.abbr"
                          [labelWidth]="'2.25rem'"
                          [placeholder]="n.unit + '/100g'"
                          [step]="n.key === 'kcalPer100g' ? 10 : 1"
                          [op]="opFor(n.key)"
                          (opChange)="setOp(n.key, $event)"
                          [value]="rawFor(n.key)"
                          (valueChange)="setRaw(n.key, $event)"
                        />
                      }
                    </div>
                  }
                }
            </app-filter-panel>

            @if (rows().length === 0) {
              <app-empty-list-row
                text="Aucune recette — crée un plat ou un repas enregistré."
                icon="menu_book"
              />
            } @else {
              <app-titled-divider title="Plats (recettes)" />
              @if (split().recipes.length === 0) {
                <app-empty-list-row text="Aucun plat." [verticalPadding]="0" />
              } @else {
                <app-list-frame>
                  @for (r of split().recipes; track r.recipe.uuid) {
                    <ng-container [ngTemplateOutlet]="recipeRow" [ngTemplateOutletContext]="{ r: r }" />
                  }
                </app-list-frame>
              }

              <app-titled-divider title="Repas enregistrés" />
              @if (split().savedMeals.length === 0) {
                <app-empty-list-row text="Aucun repas enregistré." [verticalPadding]="0" />
              } @else {
                <app-list-frame>
                  @for (r of split().savedMeals; track r.recipe.uuid) {
                    <ng-container [ngTemplateOutlet]="recipeRow" [ngTemplateOutletContext]="{ r: r }" />
                  }
                </app-list-frame>
              }
            }
          </div>

          <!-- Droite : résumé macros + micros (T4) + ingrédients du plat/repas sélectionné.
               Entre en slide-down + fade ; re-animée au changement de recette sélectionnée. -->
          <div class="split__detail" [appRevealIn]="selectedUuid()">
            @if (selectedRow(); as sel) {
              <!-- En-tête de la card détail (second-blue) : nom (gauche) + actions icônes (droite) :
                   Ajouter à un repas (restaurant) · Modifier (edit) · Supprimer (delete). -->
              <div class="detail__head" [class.detail__head--no-cats]="!selectedCategories().length">
                <span class="detail__head-name">{{ sel.recipe.name }}</span>
                @if (selectedCategories().length) {
                  <div class="detail__cats">
                    @for (c of selectedCategories(); track c.label) {
                      <span class="detail__badge" [style.--badge-c]="c.color">{{ c.label }}</span>
                    }
                  </div>
                }
                <div class="detail__head-actions">
                  <app-action-icon-button
                    icon="restaurant"
                    backgroundColor="var(--app-primary-action)"
                    [disabled]="sel.count === 0"
                    (clicked)="openAddToJournal(sel.recipe)"
                  />
                  <app-action-icon-button
                    icon="edit"
                    backgroundColor="var(--c-blue-medium)"
                    (clicked)="openEdit(sel.recipe)"
                  />
                  <app-action-icon-button
                    icon="delete"
                    backgroundColor="var(--app-btn-danger-bg)"
                    tint="var(--app-btn-danger-fg)"
                    (clicked)="recipeToDelete.set(sel.recipe)"
                  />
                </div>
              </div>

              <app-nutrition-summary-panel
                [kcal]="sel.macros.totals.kcal"
                [macros]="selMacros()!"
                [micros]="sel.macros.microTotals"
                [display]="detailView()"
                [sectionHeadings]="true"
                [unitSuffix]="summaryUnit()"
              >
                <app-segmented-icon-toggle
                  panelToggle
                  [items]="viewSegments"
                  [selected]="detailView()"
                  (select)="setDetailView($event)"
                />
              </app-nutrition-summary-panel>

              <app-titled-divider [title]="'Ingrédients · ' + sel.count" />
              @if (selectedIngredientRows().length === 0) {
                <app-empty-list-row text="Aucun ingrédient." [verticalPadding]="0" />
              } @else {
                <!-- Liste cadrée du design system (cadre thirdBlue + filets ancrés via app-list-row) ;
                     chaque ligne projette la row riche (macros colorées + grammes + menu ⋮ + chevron
                     micros) en mode « bare » — composant partagé avec les aliments du journal. -->
                <app-list-frame>
                  @for (ing of selectedIngredientRows(); track ing.foodUUID) {
                    <app-list-row [clickable]="false">
                      <app-macro-entry-row [data]="ing.data" [bare]="true">
                        <span trailing class="ing-row__qty">{{ round(ing.quantityG) }} g</span>
                        <app-action-icon-button
                          trailing
                          icon="more_vert"
                          backgroundColor="var(--c-first-blue)"
                          [size]="34"
                          [iconSize]="20"
                          (clicked)="ingredientForOptions.set(ing)"
                        />
                      </app-macro-entry-row>
                    </app-list-row>
                  }
                </app-list-frame>
              }
            } @else {
              <div class="ph">
                <app-titled-divider title="Aucune recette sélectionnée" />
                <app-empty-list-row
                  text="Sélectionne un plat ou un repas pour voir son résumé et ses ingrédients."
                  icon="menu_book"
                />
              </div>
            }
          </div>
        </div>
      </div>

      <!-- Ligne commune (plat ou repas enregistré) — rendue dans les deux sections de gauche. -->
      <ng-template #recipeRow let-r="r">
        <!-- Row recette (custom, façon catalogue) : nom + macros agrégées (sous-ligne), bordure bleue si
             sélectionnée ; actions à droite (Ajouter à un repas · ⋮), clic ailleurs = sélection. -->
        <app-list-row
          [selected]="selectedUuid() === r.recipe.uuid"
          (clicked)="selectedUuid.set(r.recipe.uuid)"
        >
          <div class="rrow__main">
            <span class="rrow__name">{{ r.recipe.name }}</span>
            <span class="rrow__sub">
              <span [style.color]="macroColor.kcal">{{ round(r.macros.totals.kcal) }} kcal</span> ·
              <span [style.color]="macroColor.carbs">G {{ round(r.macros.totals.carbs) }}</span> ·
              <span [style.color]="macroColor.fat">L {{ round(r.macros.totals.fat) }}</span> ·
              <span [style.color]="macroColor.protein">P {{ round(r.macros.totals.protein) }}</span>
            </span>
          </div>
          <!-- Ajouter au journal = action principale : fond bleu primaryAction + icône blanche. -->
          <app-action-icon-button
            icon="restaurant"
            [disabled]="r.count === 0"
            backgroundColor="var(--app-primary-action)"
            tint="var(--app-on-accent)"
            (clicked)="openAddToJournal(r.recipe)"
            (click)="$event.stopPropagation()"
          />
          <!-- Menu (⋮) = secondaire : fond neutre --app-bg-button + icône blanche (défauts). -->
          <app-action-icon-button
            icon="more_vert"
            (clicked)="recipeForOptions.set(r.recipe)"
            (click)="$event.stopPropagation()"
          />
        </app-list-row>
      </ng-template>

      <!-- ⋮ recette : ajouter au journal / modifier / supprimer. -->
      <app-options-bottom-sheet
        [open]="recipeForOptions() !== null"
        [title]="recipeForOptions()?.name ?? ''"
        [actions]="recipeActions"
        (dismissRequest)="recipeForOptions.set(null)"
        (actionSelected)="onRecipeOption($event)"
      />

      <!-- Éditeur (création ou modification) : nom + kind + poids cuit + ingrédients. -->
      <app-bottom-sheet [open]="editorOpen()" (dismissRequest)="closeEditor()">
        <div class="editor">
          <app-titled-divider [title]="editUuid() ? 'Modifier la recette' : 'Nouvelle recette'" />
          <app-custom-text-field
            label="Nom"
            placeholder="Ex. Bol d'avoine"
            [value]="eName()"
            (valueChange)="eName.set($event)"
          />
          <app-tab-row-custom
            [items]="kindTabs"
            [selectedIndex]="eKindIndex()"
            [height]="42"
            (tabSelected)="eKindIndex.set($event)"
          />
          <p class="editor__hint">
            {{
              eKindIndex() === 0
                ? 'Plat : ajouté au journal comme un aliment, macros au prorata du poids consommé.'
                : 'Repas enregistré : ses ingrédients sont insérés tels quels dans un repas, en un tap.'
            }}
          </p>
          @if (eKindIndex() === 0) {
            <app-custom-text-field
              label="Poids final cuit (g, facultatif)"
              placeholder="Ex. 450"
              [value]="eWeight()"
              (valueChange)="eWeight.set($event)"
            />
          }

          <app-titled-divider title="Ingrédients" />
          @if (eIngredients().length === 0) {
            <app-empty-list-row
              text="Aucun ingrédient — ajoute-en depuis le catalogue."
              [verticalPadding]="0"
            />
          } @else {
            <!-- Même rendu que la liste lecture seule : cadre du DS + row riche projetée (mode « bare »).
                 Le slot [trailing] porte ici les contrôles d'édition (quantité, réordonner, retirer). -->
            <app-list-frame>
              @for (ing of eIngredients(); track ing.foodUUID; let i = $index) {
                <app-list-row [clickable]="false">
                  <app-macro-entry-row [data]="eIngredientData(ing)" [bare]="true">
                  <app-custom-text-field
                    trailing
                    class="ing__qty"
                    placeholder="g"
                    [value]="ing.quantity"
                    (valueChange)="setIngQty(i, $event)"
                  />
                  <!-- Réordonner = secondaire : fond neutre + icône blanche (défauts). -->
                  <app-action-icon-button
                    trailing
                    icon="arrow_upward"
                    [size]="30"
                    [iconSize]="18"
                    [disabled]="i === 0"
                    (clicked)="moveIng(i, -1)"
                  />
                  <app-action-icon-button
                    trailing
                    icon="arrow_downward"
                    [size]="30"
                    [iconSize]="18"
                    [disabled]="i === eIngredients().length - 1"
                    (clicked)="moveIng(i, 1)"
                  />
                  <!-- Retirer = destructif : fond rouge + icône blanche. -->
                  <app-action-icon-button
                    trailing
                    icon="delete"
                    [size]="30"
                    [iconSize]="18"
                    backgroundColor="var(--app-btn-danger-bg)"
                    tint="var(--app-btn-danger-fg)"
                    (clicked)="removeIng(i)"
                  />
                  </app-macro-entry-row>
                </app-list-row>
              }
            </app-list-frame>
          }
          <app-action-icon-with-text-button
            icon="add"
            text="Ajouter un ingrédient"
            backgroundColor="var(--c-blue-medium)"
            (clicked)="ingredientPickerOpen.set(true)"
          />

          @if (!editorValid()) {
            <p class="editor__hint">
              Nom, au moins 1 ingrédient et quantités en g (&gt; 0) requis.
            </p>
          }
          <app-action-icon-with-text-button
            icon="check"
            text="Enregistrer"
            [fullWidth]="true"
            [disabled]="!editorValid()"
            (clicked)="saveEditor()"
          />
        </div>
      </app-bottom-sheet>

      <!-- Picker d'aliment pour les ingrédients (catalogue / OFF / créer). -->
      <app-food-picker-sheet
        [open]="ingredientPickerOpen()"
        (dismissRequest)="ingredientPickerOpen.set(false)"
        (foodPicked)="onIngredientPicked($event)"
      />

      <!-- Ajouter au journal : choix de la période du jour. -->
      <app-bottom-sheet
        [open]="recipeForJournal() !== null"
        (dismissRequest)="recipeForJournal.set(null)"
      >
        <div class="periods">
          <app-titled-divider [title]="'Ajouter « ' + (recipeForJournal()?.name ?? '') + ' » à…'" />
          @for (s of todaySections(); track s.key) {
            <div class="periods__row" (click)="pickSection(s)">
              <span class="periods__name">{{ s.name }}</span>
              @if (s.defaultTime) {
                <span class="periods__time">{{ s.defaultTime }}</span>
              }
            </div>
          }
        </div>
      </app-bottom-sheet>

      <!-- kind=RECIPE : quantité consommée (g) avant insertion au prorata. -->
      <app-form-dialog
        [open]="qtyOpen()"
        [title]="'Quantité consommée — ' + (qtyRecipe()?.name ?? '')"
        confirmText="Ajouter"
        [confirmEnabled]="qtyValid()"
        disabledReason="Quantité en grammes requise (> 0)"
        (confirm)="confirmRecipeQty()"
        (dismiss)="qtyOpen.set(false)"
      >
        <app-custom-text-field
          label="Quantité (g)"
          placeholder="100"
          [value]="qtyValue()"
          (valueChange)="qtyValue.set($event)"
        />
      </app-form-dialog>

      <app-confirmation-dialog
        [open]="recipeToDelete() !== null"
        title="Supprimer la recette"
        [message]="deleteMsg()"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDelete()"
        (dismiss)="recipeToDelete.set(null)"
      />

      <!-- Menu ⋮ d'un ingrédient (panneau détail) : voir au catalogue / modifier la quantité / retirer. -->
      <app-options-bottom-sheet
        [open]="ingredientForOptions() !== null"
        [title]="ingredientForOptions()?.name ?? ''"
        [actions]="ingredientActions"
        (actionSelected)="onIngredientOption($event)"
        (dismissRequest)="ingredientForOptions.set(null)"
      />

      <!-- ⋮ → « Modifier la quantité » : édite la quantité de l'ingrédient dans la recette sélectionnée. -->
      <app-form-dialog
        [open]="editQtyOpen()"
        [title]="'Quantité — ' + (editQtyTarget()?.name ?? '')"
        confirmText="Enregistrer"
        [confirmEnabled]="editQtyValid()"
        disabledReason="Quantité en grammes requise (> 0)"
        (confirm)="confirmEditQty()"
        (dismiss)="editQtyOpen.set(false)"
      >
        <app-custom-text-field
          label="Quantité (g)"
          placeholder="100"
          [value]="editQtyValue()"
          (valueChange)="editQtyValue.set($event)"
        />
      </app-form-dialog>
    </section>
  `,
  styles: [
    `
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        box-sizing: border-box;
        width: 100%;
      }
      /* Master/détail : liste à gauche, résumé + ingrédients à droite ; empilé sur écran étroit. */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__list,
      .split__detail {
        flex: 2 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding-bottom: var(--space-3);
      }
      /* Master/détail 2/5 (liste de noms) – 3/5 (détail : résumé + ingrédients). La colonne détail,
         plus chargée, est plus large que la liste. Empilé pleine largeur sous 900px (media ci-dessous). */
      .split__detail {
        flex: 3 1 0;
        /* Détail collant + plafonné à la fenêtre + scroll interne (même pattern que food-catalogue) :
           le résumé (macros/micros/radar) + les ingrédients ne pilotent plus le scroll de la FENÊTRE.
           La colonne reste compacte si courte (1-2 ingrédients → aucune barre), pinnée pendant qu'on
           scrolle la liste (gauche), et ne scrolle en interne QUE si elle dépasse réellement la hauteur
           d'écran. Bas dégagé (88px) pour la barre de nav flottante. */
        position: sticky;
        top: var(--space-3);
        max-height: calc(100vh - var(--space-3) - 88px);
        overflow-y: auto;
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
        .split__list,
        .split__detail {
          flex: none;
          width: 100%;
        }
        /* Empilé : le détail reprend le flux normal (ni collant, ni plafond, ni scroll interne). */
        .split__detail {
          position: static;
          max-height: none;
          overflow: visible;
        }
      }
      .toolbar {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      /* Cadre filtre porté par <app-filter-panel> ; ici on ne style que le contenu projeté. */
      .filters__category {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: var(--space-2) var(--space-4);
      }
      /* Deux colonnes réparties sur la largeur (gauche ↔ droite) ; minmax(0,…) → compressibles, pas de
         débordement ; une seule colonne si le cadre est vraiment étroit (petit mobile). */
      .filters__grid {
        display: grid;
        grid-template-columns: minmax(0, max-content) minmax(0, max-content);
        justify-content: space-between;
        gap: var(--space-2) var(--space-4);
      }
      @container (max-width: 400px) {
        .filters__grid {
          grid-template-columns: 1fr;
        }
      }
      /* En-tête de la card détail (second-blue) : nom (gauche) + actions icônes (droite). */
      .detail__head {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--c-second-blue);
        /* Collé à la card thirdBlue dessous : arrondi haut seulement + on annule le gap flex pour qu'il
           touche le résumé (header + card = une seule carte, comme le détail catalogue). */
        border-radius: var(--radius-md) var(--radius-md) 0 0;
        margin-bottom: calc(-1 * var(--space-2));
        padding: var(--space-1) var(--space-2) var(--space-1) var(--space-3);
      }
      /* Le résumé (card thirdBlue) qui suit l'en-tête : coins hauts carrés pour se raccorder au header. */
      .split__detail ::ng-deep .nsp {
        border-top-left-radius: 0;
        border-top-right-radius: 0;
      }
      .detail__head-name {
        flex: 0 1 auto;
        min-width: 0;
        color: var(--app-text-primary);
        font-size: 15px;
        font-weight: var(--font-weight-medium);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      /* Sans badge de catégorie, le nom reprend toute la place (pousse les actions à droite). */
      .detail__head--no-cats .detail__head-name {
        flex: 1 1 auto;
      }
      .detail__head-actions {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        flex-shrink: 0;
      }
      /* Badges catégories : occupent l'espace entre la fin du nom et les actions (centrés), avec un
         minimum réservé → un nom long tronque « … » plutôt que d'écraser les labels. */
      .detail__head .detail__cats {
        flex: 1 1 auto;
        min-width: 6rem;
      }
      /* Recherche dans la toolbar de gauche : prend la place restante à côté du bouton « Ajouter ». */
      .toolbar__search {
        flex: 1;
        min-width: 0;
      }
      /* Rows Plats / Repas : cadre + filet + sélection portés par <app-list-frame> / <app-list-row>.
         Ici on ne style que le CONTENU projeté (zone principale nom + macros). */
      .rrow__main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .rrow__name {
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .rrow__sub {
        color: var(--c-gray-blue);
        font-size: 12px;
      }
      /* Barre façon détail Catalogue : toggle (gauche) · catégories (centre) · Supprimer (droite). */
      .detail__bar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: var(--space-2);
        flex-wrap: wrap;
      }
      /* Badges des catégories d'aliments du plat, centrés ; wrap propre si trop étroit. */
      .detail__cats {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-2);
        flex: 1 1 auto;
        flex-wrap: wrap;
      }
      /* Pastille catégorie colorée par groupe (même langage visuel que la row du Catalogue). */
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
      /* Zone droite : action Supprimer, ne se compresse pas. */
      /* margin-left:auto → reste collé à droite même sans badges de catégories (la bascule a quitté la barre). */
      .detail__baractions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
        justify-content: flex-end;
        flex-shrink: 0;
        margin-left: auto;
      }
      .detail__actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
        margin-top: var(--space-2);
      }
      /* Grammes de l'ingrédient (lecture seule), projetés dans le slot [trailing] de la ligne. */
      .ing-row__qty {
        flex-shrink: 0;
        margin-right: var(--space-2);
        color: var(--app-text-tertiary);
        font-size: 13px;
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
      }
      .ph {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .editor {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        padding: 0 var(--space-4) var(--space-3);
      }
      .editor__hint {
        margin: 0;
        font-size: 12px;
        font-style: italic;
        color: var(--app-text-tertiary);
      }
      /* Champ quantité de l'ingrédient dans l'éditeur, projeté dans le slot [trailing] de la ligne. */
      .ing__qty {
        width: 84px;
        flex-shrink: 0;
      }
      .periods {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding: 0 var(--space-4) var(--space-3);
      }
      .periods__row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
        cursor: pointer;
      }
      .periods__row:hover {
        filter: brightness(1.08);
      }
      .periods__name {
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
      }
      .periods__time {
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
    `,
  ],
})
export class RecipesPage {
  private readonly sync = inject(SyncEngine);
  private readonly router = inject(Router);
  protected readonly foodRepo = inject(FoodRepository);
  protected readonly mealRepo = inject(MealRepository);
  protected readonly recipeRepo = inject(RecipeRepository);

  protected readonly kindTabs = ['Plat (recette)', 'Repas enregistré'];

  /** Code couleur des macros (kcal d'aperçu dans les rows), partagé avec le résumé + le Journal. */
  protected readonly macroColor = MACRO_COLOR;

  // -------------------- Représentation de la répartition macro (détail) --------------------

  /** Représentations de la répartition macro du plat/repas sélectionné (Cercle / Radar / Barre). */
  protected readonly viewSegments: SegmentItem[] = [
    { value: 'radar', icon: 'radar', description: 'Radar' },
    { value: 'donut', icon: 'donut_large', description: 'Cercle' },
    { value: 'bar', icon: 'bar_chart', description: 'Barre' },
  ];

  /** Mode d'affichage du résumé dans le détail. Défaut RADAR app-wide (« vue ouverte »), le toggle
   * laisse toujours basculer en Cercle (donut) ou Barre. */
  protected readonly detailView = signal<SummaryDisplay>('radar');

  protected setDetailView(view: string): void {
    this.detailView.set(view as SummaryDisplay);
  }

  // -------------------- Liste --------------------

  private readonly foodsByUuid = computed(
    () => new Map(this.foodRepo.foods().map((f) => [f.uuid, f])),
  );

  /** Ingrédients groupés par recette, triés par orderIndex. */
  private readonly ingredientsByRecipe = computed(() => {
    const map = new Map<string, { foodUUID: string; quantityG: number; orderIndex: number }[]>();
    for (const i of this.recipeRepo.ingredients()) {
      const list = map.get(i.recipeUUID) ?? [];
      list.push(i);
      map.set(i.recipeUUID, list);
    }
    for (const list of map.values()) list.sort((a, b) => a.orderIndex - b.orderIndex);
    return map;
  });

  protected readonly rows = computed(() =>
    this.recipeRepo.recipes().map((recipe) => {
      const foods = this.foodsByUuid();
      const ingredients = this.ingredientsByRecipe().get(recipe.uuid) ?? [];
      // Codes de groupes d'aliments présents dans la recette (pour la facette catégorie « contient »).
      const groupCodes = new Set<string>();
      for (const ing of ingredients) {
        const code = foods.get(ing.foodUUID)?.foodGroup;
        if (code) groupCodes.add(code.toUpperCase());
      }
      return {
        recipe,
        count: ingredients.length,
        macros: recipeMacros(recipe, ingredients, foods),
        groupCodes,
      };
    }),
  );

  /** Recherche par nom (colonne de gauche). */
  protected readonly search = signal('');

  // -------------------- Filtres (parité catalogue : catégorie + seuils macros/micros per-100 g) --------------------

  protected readonly filtersOpen = signal(false);
  /** Section micros du panneau de filtres repliée par défaut (chevron « Afficher les micros »). */
  protected readonly microsOpen = signal(false);
  /** Sections du panneau de filtres (libellés + unités issus des constantes catalogue). */
  protected readonly filterSections = [
    { title: 'Macros', items: FILTERABLE_MACROS, collapsible: false },
    { title: 'Vitamines & minéraux', items: FILTERABLE_MICROS, collapsible: true },
  ];

  // Facette catégorie : une recette « contient » un règne/groupe si l'un de ses ingrédients en relève.
  // Sentinelle 'Tous' = pas de filtre (convention FilterDropdown). Options = règnes/groupes réellement présents.
  protected readonly realmFilter = signal('Tous');
  protected readonly groupFilter = signal('Tous');

  /** Codes de groupes d'aliments présents dans les ingrédients de toutes les recettes (UPPER_CASE). */
  private readonly presentGroupCodes = computed(() => {
    const codes = new Set<string>();
    for (const r of this.rows()) for (const c of r.groupCodes) codes.add(c);
    return codes;
  });
  protected readonly realmOptions = computed(() => {
    const present = new Set([...this.presentGroupCodes()].map((c) => realmOf(c)));
    return ['Tous', ...FOOD_REALMS.filter((r) => present.has(r)).map((r) => FOOD_REALM_LABEL[r])];
  });
  protected readonly groupOptions = computed(() => {
    const present = this.presentGroupCodes();
    return ['Tous', ...FOOD_GROUP_OPTIONS.filter((o) => o.codes.some((c) => present.has(c))).map((o) => o.label)];
  });
  private readonly activeRealm = computed<FoodRealm | null>(() =>
    this.realmFilter() === 'Tous' ? null : realmFromLabel(this.realmFilter()),
  );
  private readonly activeGroupCodes = computed<string[]>(() =>
    this.groupFilter() === 'Tous' ? [] : groupCodesForLabel(this.groupFilter()),
  );
  protected readonly activeCategoryCount = computed(
    () => (this.realmFilter() !== 'Tous' ? 1 : 0) + (this.groupFilter() !== 'Tous' ? 1 : 0),
  );

  /** État de saisie des seuils, indexé par clé de nutriment (opérateur + valeur brute). */
  private readonly thresholdInputs = signal<Record<string, { op: ThresholdOp; raw: string }>>({});
  protected opFor(key: NutrientKey): ThresholdOp {
    return this.thresholdInputs()[key]?.op ?? 'gte';
  }
  protected rawFor(key: NutrientKey): string {
    return this.thresholdInputs()[key]?.raw ?? '';
  }
  protected setOp(key: NutrientKey, op: ThresholdOp): void {
    this.thresholdInputs.update((cur) => ({ ...cur, [key]: { op, raw: cur[key]?.raw ?? '' } }));
  }
  protected setRaw(key: NutrientKey, raw: string): void {
    this.thresholdInputs.update((cur) => ({ ...cur, [key]: { op: cur[key]?.op ?? 'gte', raw } }));
  }
  protected resetFilters(): void {
    this.thresholdInputs.set({});
    this.realmFilter.set('Tous');
    this.groupFilter.set('Tous');
  }
  /** Seuils effectifs : seules les lignes avec une valeur numérique valide deviennent actives. */
  protected readonly activeThresholds = computed<NutrientThreshold[]>(() => {
    const out: NutrientThreshold[] = [];
    for (const [key, inp] of Object.entries(this.thresholdInputs())) {
      const value = parseMacro(inp.raw);
      if (value !== null) out.push({ key: key as NutrientKey, op: inp.op, value });
    }
    return out;
  });
  protected readonly activeFilterCount = computed(
    () => this.activeThresholds().length + this.activeCategoryCount(),
  );
  protected readonly filterButtonLabel = computed(() =>
    this.activeFilterCount() > 0 ? `Filtres (${this.activeFilterCount()})` : 'Filtres',
  );

  /** Plats (kind=RECIPE) et repas enregistrés (kind=SAVED_MEAL), filtrés par recherche + facettes + seuils. */
  protected readonly split = computed(() => {
    const q = this.search().toLowerCase().trim();
    const realm = this.activeRealm();
    const groupCodes = this.activeGroupCodes();
    const thresholds = this.activeThresholds();
    const filtered = this.rows().filter(
      (r) =>
        (!q || r.recipe.name.toLowerCase().includes(q)) &&
        (!realm || [...r.groupCodes].some((c) => realmOf(c) === realm)) &&
        (!groupCodes.length || [...r.groupCodes].some((c) => groupCodes.includes(c))) &&
        thresholds.every((t) => {
          const v = recipeNutrientValue(r.macros, t.key);
          return t.op === 'gte' ? v >= t.value : v <= t.value;
        }),
    );
    return splitRecipesByKind(filtered);
  });

  // -------------------- Sélection (colonne détail) --------------------

  protected readonly selectedUuid = signal<string | null>(null);

  /** Ligne sélectionnée, ou null si rien de sélectionné / la recette a disparu (supprimée). */
  protected readonly selectedRow = computed(() => {
    const uuid = this.selectedUuid();
    return uuid ? (this.rows().find((r) => r.recipe.uuid === uuid) ?? null) : null;
  });

  /** Macros (grammes) du plat sélectionné pour le résumé — totaux absolus de la recette. */
  protected readonly selMacros = computed<MacroAmounts | null>(() => {
    const r = this.selectedRow();
    if (!r) return null;
    const t = r.macros.totals;
    return { protein: t.protein, carbs: t.carbs, fat: t.fat, fiber: t.fiber };
  });

  /** Suffixe d'unité du résumé : base de poids des totaux (poids cuit kind=RECIPE, sinon cru). */
  protected readonly summaryUnit = computed(() => {
    const r = this.selectedRow();
    return r ? `/ ${this.round(r.macros.weightBaseG)} g` : '';
  });

  /**
   * Ingrédients du plat sélectionné, enrichis pour la ligne partagée MacroEntryRow : macros + micros
   * dérivés du Food (référence vivante) × quantité. Un ingrédient dont le Food a disparu retombe sur
   * des valeurs nulles (libellé « Aliment supprimé »).
   */
  protected readonly selectedIngredientRows = computed<IngredientRowVM[]>(() => {
    const uuid = this.selectedUuid();
    if (!uuid) return [];
    const foods = this.foodsByUuid();
    return (this.ingredientsByRecipe().get(uuid) ?? []).map((i) => {
      const data = ingredientRowData(foods.get(i.foodUUID), i.quantityG);
      return { foodUUID: i.foodUUID, name: data.name, quantityG: i.quantityG, data };
    });
  });

  /** Données d'affichage d'une ligne d'ingrédient de l'éditeur (quantité en saisie libre → parsée). */
  protected eIngredientData(ing: DraftIngredient): MacroEntryRowData {
    return ingredientRowData(this.foodsByUuid().get(ing.foodUUID), parseMacro(ing.quantity) ?? 0, ing.name);
  }

  /**
   * Catégories d'aliments distinctes composant le plat (badges, façon Catalogue) — un aperçu « d'un
   * coup d'œil » de ce que contient le repas. Dédupliquées par label, ordre canonique FOOD_GROUPS ;
   * les ingrédients sans groupe ne produisent pas de badge.
   */
  protected readonly selectedCategories = computed(() => {
    const uuid = this.selectedUuid();
    if (!uuid) return [] as { label: string; color: string }[];
    const foods = this.foodsByUuid();
    const present = new Set<string>();
    for (const ing of this.ingredientsByRecipe().get(uuid) ?? []) {
      const group = foods.get(ing.foodUUID)?.foodGroup;
      if (group) present.add(group.toUpperCase());
    }
    const seenLabels = new Set<string>();
    const out: { label: string; color: string }[] = [];
    for (const code of FOOD_GROUPS) {
      if (!present.has(code)) continue;
      const label = foodGroupLabel(code);
      if (seenLabels.has(label)) continue;
      seenLabels.add(label);
      out.push({ label, color: foodGroupColor(code) });
    }
    return out;
  });

  // -------------------- Options recette --------------------

  protected readonly recipeForOptions = signal<LocalRecipe | null>(null);
  protected readonly recipeActions: SheetAction[] = [
    { label: 'Ajouter au journal', icon: 'restaurant', color: 'var(--app-primary-action)' },
    { label: 'Modifier', icon: 'edit', color: 'var(--c-blue-medium)' },
    { label: 'Supprimer', icon: 'delete', color: 'var(--c-red-medium)' },
  ];

  protected onRecipeOption(label: string): void {
    const recipe = this.recipeForOptions();
    this.recipeForOptions.set(null);
    if (!recipe) return;
    if (label === 'Ajouter au journal') this.openAddToJournal(recipe);
    else if (label === 'Modifier') this.openEdit(recipe);
    else if (label === 'Supprimer') this.recipeToDelete.set(recipe);
  }

  // -------------------- Éditeur --------------------

  protected readonly editorOpen = signal(false);
  protected readonly editUuid = signal<string | null>(null);
  protected readonly eName = signal('');
  protected readonly eKindIndex = signal(0);
  protected readonly eWeight = signal('');
  protected readonly eIngredients = signal<DraftIngredient[]>([]);
  protected readonly ingredientPickerOpen = signal(false);

  protected openCreate(): void {
    this.editUuid.set(null);
    this.eName.set('');
    this.eKindIndex.set(0);
    this.eWeight.set('');
    this.eIngredients.set([]);
    this.editorOpen.set(true);
  }

  protected openEdit(recipe: LocalRecipe): void {
    this.editUuid.set(recipe.uuid);
    this.eName.set(recipe.name);
    this.eKindIndex.set(recipe.kind === 'SAVED_MEAL' ? 1 : 0);
    this.eWeight.set(recipe.totalWeightG ? String(recipe.totalWeightG) : '');
    const foods = this.foodsByUuid();
    this.eIngredients.set(
      (this.ingredientsByRecipe().get(recipe.uuid) ?? []).map((i) => ({
        foodUUID: i.foodUUID,
        name: foods.get(i.foodUUID)?.name ?? 'Aliment supprimé',
        quantity: String(i.quantityG),
      })),
    );
    this.editorOpen.set(true);
  }

  protected closeEditor(): void {
    this.editorOpen.set(false);
  }

  protected onIngredientPicked(food: LocalFood): void {
    this.ingredientPickerOpen.set(false);
    if (this.eIngredients().some((i) => i.foodUUID === food.uuid)) return;
    this.eIngredients.update((list) => [
      ...list,
      { foodUUID: food.uuid, name: food.name, quantity: '100' },
    ]);
  }

  protected setIngQty(index: number, value: string): void {
    this.eIngredients.update((list) =>
      list.map((i, idx) => (idx === index ? { ...i, quantity: value } : i)),
    );
  }

  protected moveIng(index: number, delta: number): void {
    this.eIngredients.update((list) => {
      const next = [...list];
      const target = index + delta;
      if (target < 0 || target >= next.length) return list;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  protected removeIng(index: number): void {
    this.eIngredients.update((list) => list.filter((_, idx) => idx !== index));
  }

  protected readonly editorValid = computed(() => {
    if (this.eName().trim().length === 0) return false;
    const ings = this.eIngredients();
    if (ings.length === 0) return false;
    if (!ings.every((i) => (parseMacro(i.quantity) ?? 0) > 0)) return false;
    if (
      this.eKindIndex() === 0 &&
      this.eWeight().trim() !== '' &&
      (parseMacro(this.eWeight()) ?? 0) <= 0
    )
      return false;
    return true;
  });

  protected saveEditor(): void {
    if (!this.editorValid()) return;
    const kind = this.eKindIndex() === 0 ? 'RECIPE' : 'SAVED_MEAL';
    const weight =
      this.eKindIndex() === 0 && this.eWeight().trim() !== '' ? parseMacro(this.eWeight()) : null;
    const items = this.eIngredients().map((i) => ({
      foodUUID: i.foodUUID,
      quantityG: parseMacro(i.quantity)!,
    }));
    const name = this.eName().trim();
    this.editorOpen.set(false);
    const uuid = this.editUuid();
    if (uuid) {
      void this.recipeRepo
        .update(uuid, { name, kind, totalWeightG: weight })
        .then(() => this.recipeRepo.setIngredients(uuid, items));
    } else {
      void this.recipeRepo
        .create({ name, kind, totalWeightG: weight })
        .then((newUuid) => {
          // Sélectionne la nouvelle recette pour afficher son résumé immédiatement à droite.
          this.selectedUuid.set(newUuid);
          return this.recipeRepo.setIngredients(newUuid, items);
        });
    }
  }

  // -------------------- Supprimer --------------------

  protected readonly recipeToDelete = signal<LocalRecipe | null>(null);
  protected readonly deleteMsg = computed(() => {
    const r = this.recipeToDelete();
    return r
      ? `Supprimer « ${r.name} » ? Les repas déjà journalisés sont conservés (snapshot).`
      : '';
  });

  protected confirmDelete(): void {
    const r = this.recipeToDelete();
    this.recipeToDelete.set(null);
    if (r) {
      if (this.selectedUuid() === r.uuid) this.selectedUuid.set(null);
      void this.recipeRepo.remove(r.uuid);
    }
  }

  // -------------------- Ajouter au journal (période du jour) --------------------

  protected readonly recipeForJournal = signal<LocalRecipe | null>(null);
  protected readonly qtyOpen = signal(false);
  protected readonly qtyRecipe = signal<LocalRecipe | null>(null);
  protected readonly qtyValue = signal('100');
  private pendingSection: JournalSection | null = null;

  /** Sections du jour courant (presets + repas ad hoc), mêmes règles que le journal. */
  protected readonly todaySections = computed(() => {
    const today = todayIso();
    const dayMeals = this.mealRepo
      .meals()
      .filter((m) => m.date === today)
      .sort((a, b) => a.orderIndex - b.orderIndex);
    return buildSections(this.mealRepo.presets(), dayMeals, this.mealRepo.entries());
  });

  protected openAddToJournal(recipe: LocalRecipe): void {
    this.recipeForJournal.set(recipe);
  }

  /** Navigue vers le catalogue d'aliments avec cet aliment ouvert en détail (⋮ → Voir dans le catalogue). */
  protected viewFoodInCatalogue(foodUuid: string): void {
    this.router.navigate(['/nutrition/foods'], { queryParams: { food: foodUuid } });
  }

  // -------------------- Options ingrédient (panneau détail) --------------------

  protected readonly ingredientForOptions = signal<IngredientRowVM | null>(null);
  /** Actions du menu ⋮ d'un ingrédient : voir au catalogue / modifier la quantité / retirer du plat. */
  protected readonly ingredientActions: SheetAction[] = [
    { label: 'Voir dans le catalogue', icon: 'visibility', color: 'var(--app-primary-action)' },
    { label: 'Modifier la quantité', icon: 'edit', color: 'var(--c-first-blue)' },
    { label: 'Retirer du plat', icon: 'delete', color: 'var(--c-red-medium)' },
  ];

  protected onIngredientOption(label: string): void {
    const ing = this.ingredientForOptions();
    this.ingredientForOptions.set(null);
    if (!ing) return;
    if (label === 'Voir dans le catalogue') {
      this.viewFoodInCatalogue(ing.foodUUID);
    } else if (label === 'Modifier la quantité') {
      this.editQtyTarget.set(ing);
      this.editQtyValue.set(String(ing.quantityG));
      this.editQtyOpen.set(true);
    } else if (label === 'Retirer du plat') {
      this.updateSelectedIngredients((items) => items.filter((it) => it.foodUUID !== ing.foodUUID));
    }
  }

  // ⋮ → « Modifier la quantité » : petit dialogue de saisie, appliqué sur la recette sélectionnée.
  protected readonly editQtyOpen = signal(false);
  protected readonly editQtyValue = signal('');
  protected readonly editQtyTarget = signal<IngredientRowVM | null>(null);
  protected readonly editQtyValid = computed(() => (parseMacro(this.editQtyValue()) ?? 0) > 0);

  protected confirmEditQty(): void {
    const target = this.editQtyTarget();
    const qty = parseMacro(this.editQtyValue());
    this.editQtyOpen.set(false);
    if (!target || qty == null || qty <= 0) return;
    this.updateSelectedIngredients((items) =>
      items.map((it) => (it.foodUUID === target.foodUUID ? { foodUUID: it.foodUUID, quantityG: qty } : it)),
    );
  }

  /** Réécrit les ingrédients du plat sélectionné via une transformation (modifier la quantité / retirer). */
  private updateSelectedIngredients(
    transform: (items: { foodUUID: string; quantityG: number }[]) => { foodUUID: string; quantityG: number }[],
  ): void {
    const uuid = this.selectedUuid();
    if (!uuid) return;
    const current = (this.ingredientsByRecipe().get(uuid) ?? []).map((i) => ({
      foodUUID: i.foodUUID,
      quantityG: i.quantityG,
    }));
    void this.recipeRepo.setIngredients(uuid, transform(current));
  }

  /** SAVED_MEAL : insertion immédiate des ingrédients (un tap). RECIPE : demande la quantité. */
  protected pickSection(section: JournalSection): void {
    const recipe = this.recipeForJournal();
    this.recipeForJournal.set(null);
    if (!recipe) return;
    if (recipe.kind === 'SAVED_MEAL') {
      void this.insertSavedMeal(recipe, section);
    } else {
      this.pendingSection = section;
      this.qtyRecipe.set(recipe);
      this.qtyValue.set('100');
      this.qtyOpen.set(true);
    }
  }

  protected readonly qtyValid = computed(() => (parseMacro(this.qtyValue()) ?? 0) > 0);

  protected confirmRecipeQty(): void {
    const recipe = this.qtyRecipe();
    const section = this.pendingSection;
    const qty = parseMacro(this.qtyValue());
    if (!recipe || !section || !qty || qty <= 0) return;
    this.qtyOpen.set(false);
    this.qtyRecipe.set(null);
    this.pendingSection = null;
    void this.insertRecipeEntry(recipe, section, qty);
  }

  /** Meal row de la section, créé à la première entry seulement (§3.4 — pas de rows fantômes). */
  private async ensureMeal(section: JournalSection): Promise<string> {
    if (section.meal) return section.meal.uuid;
    return this.mealRepo.createMeal({
      date: todayIso(),
      name: section.name,
      orderIndex: section.orderIndex,
    });
  }

  /** kind=SAVED_MEAL : chaque ingrédient devient une entry snapshotée depuis le Food vivant (D5). */
  private async insertSavedMeal(recipe: LocalRecipe, section: JournalSection): Promise<void> {
    const foods = this.foodsByUuid();
    const ingredients = this.ingredientsByRecipe().get(recipe.uuid) ?? [];
    // Ne garder que les ingrédients dont le Food existe encore (références vivantes). Si aucun n'est
    // résolu (recette vide ou Foods supprimés), ne PAS créer de Meal — sinon row de repas fantôme.
    const resolved = ingredients.filter((ing) => foods.has(ing.foodUUID));
    if (resolved.length === 0) return;
    const mealUuid = await this.ensureMeal(section);
    for (const ing of resolved) {
      await this.mealRepo.addEntryFromFood(mealUuid, foods.get(ing.foodUUID)!, ing.quantityG);
    }
  }

  /** kind=RECIPE : une seule entry au prorata du poids consommé, per-100g du plat snapshoté (D5). */
  private async insertRecipeEntry(
    recipe: LocalRecipe,
    section: JournalSection,
    quantityG: number,
  ): Promise<void> {
    const ingredients = this.ingredientsByRecipe().get(recipe.uuid) ?? [];
    const macros = recipeMacros(recipe, ingredients, this.foodsByUuid());
    // Aucun ingrédient résolu (recette vide / Foods supprimés) → per-100g nul : ne PAS créer d'entry
    // 0 kcal fantôme ni de Meal vide.
    if (macros.ingredientsWeightG <= 0) return;
    const mealUuid = await this.ensureMeal(section);
    const micros = macros.microPer100g;
    await this.mealRepo.addEntry({
      mealUUID: mealUuid,
      foodUUID: null,
      recipeUUID: recipe.uuid,
      displayName: recipe.name,
      quantityG,
      portionLabel: null,
      kcalPer100g: macros.per100g.kcal,
      proteinPer100g: macros.per100g.protein,
      carbsPer100g: macros.per100g.carbs,
      fatPer100g: macros.per100g.fat,
      fiberPer100g: macros.per100g.fiber,
      sugarPer100g: null,
      satFatPer100g: null,
      saltPer100g: null,
      // Micros per-100g dérivés du plat (microPer100g, T7) — symétrise le tracking journal avec
      // SAVED_MEAL (qui snapshote les 10 micros via addEntryFromFood). sugar/satFat/salt restent hors
      // scope v1 (non dérivables d'une recette).
      ironPer100g: micros.ironPer100g,
      calciumPer100g: micros.calciumPer100g,
      magnesiumPer100g: micros.magnesiumPer100g,
      zincPer100g: micros.zincPer100g,
      potassiumPer100g: micros.potassiumPer100g,
      sodiumPer100g: micros.sodiumPer100g,
      vitaminCPer100g: micros.vitaminCPer100g,
      vitaminDPer100g: micros.vitaminDPer100g,
      vitaminB12Per100g: micros.vitaminB12Per100g,
      vitaminAPer100g: micros.vitaminAPer100g,
    });
  }

  // -------------------- Helpers d'affichage --------------------

  protected round(v: number): number {
    return Math.round(v);
  }

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }
}
