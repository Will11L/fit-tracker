import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, ElementRef, inject, input, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { LocalFood } from '@core/models/food.model';
import { LocalFoodPortion } from '@core/models/food-portion.model';
import { SyncEngine } from '@core/sync/sync-engine';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ListFrame } from '@designsystem/common_components/list-frame';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomSwitch } from '@designsystem/common_components/custom-switch';
import { AppIcon } from '@designsystem/icons/app-icon';
import { FilterDropdown } from '@designsystem/common_components/filter-dropdown';
import { FilterThresholdRow } from '@designsystem/common_components/filter-threshold-row';
import { CollapsibleSection } from '@designsystem/common_components/collapsible-section';
import { FilterPanel } from '@designsystem/common_components/filter-panel';
import { CustomRadioButton } from '@designsystem/common_components/custom-radio-button';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { RevealIn } from '@designsystem/common_components/reveal-in';
import { FoodRepository } from './food.repository';
import { MealRepository } from './meal.repository';
import { FoodListRow } from './food-list-row';
import { FoodDetailPanel } from './food-detail-panel';
import {
  buildFoodGroups,
  recentFoodUuids,
  FILTERABLE_MACROS,
  FILTERABLE_MICROS,
  type FoodGroup,
  type NutrientKey,
  type NutrientThreshold,
  type ThresholdOp,
} from './food-catalogue';
import {
  FOOD_GROUP_LABELS,
  FOOD_GROUP_OPTIONS,
  FOOD_REALMS,
  FOOD_REALM_LABEL,
  foodGroupLabel,
  groupCodesForLabel,
  realmFromLabel,
  realmOf,
  type FoodRealm,
} from './food-category';
import { parseMacro } from './food-picker-sheet';

/** Saisie d'un seuil dans le panneau de filtres : opérateur + valeur brute (texte). */
interface ThresholdInput {
  op: ThresholdOp;
  raw: string;
}

/**
 * Taille d'une tranche de chargement progressif de la colonne master. La liste ne rend jamais plus
 * que `visibleCount` rows d'un coup ; une sentinelle en bas dévoile la tranche suivante au scroll.
 * Anti-lag : avec un gros catalogue, on évite de monter des centaines de rows en un seul rendu.
 */
const CATALOGUE_BATCH = 60;

/**
 * Catalogue d'aliments (`/nutrition/foods`) — refonte master/détail (T5). Colonne gauche = liste
 * ordonnée Récents → Favoris → Tous (logique partagée `buildFoodGroups`) ; colonne droite = détail
 * de l'aliment cliqué (`FoodDetailPanel` : résumé visuel macros + micros via T4, portions, actions).
 * Sur mobile étroit le détail bascule en bottom-sheet (pas de colonne droite).
 *
 * Recherche multi-critères : nom/marque + seuils ≥/≤ par macro et par micro (per-100 g), combinables
 * (ET) et cumulables avec la recherche texte, via un panneau de filtres repliable. Édition/archivage
 * des aliments perso (source CUSTOM) ; bouton « Créer un aliment ». Toutes les écritures via FoodRepository.
 */
@Component({
  selector: 'app-food-catalogue-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    EmptyListRow,
    StyledSearchField,
    ActionIconWithTextButton,
    CustomTextField,
    CustomSwitch,
    AppIcon,
    FilterDropdown,
    FilterThresholdRow,
    CollapsibleSection,
    FilterPanel,
    CustomRadioButton,
    AppBottomSheet,
    ConfirmationDialog,
    FoodListRow,
    FoodDetailPanel,
    RevealIn,
    ListFrame,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Catalogue d'aliments" />

      <div class="page__body">
        <!-- Master/détail : colonne de gauche (toolbar + filtres + liste) · colonne détail à droite. -->
        <div class="master-detail" [class.master-detail--wide]="isWide()">
          <div class="md__master">
            <!-- Toolbar (ordre user 2026-07-15) : Archivés · Filtres · recherche · Nouveau. -->
            <div class="toolbar">
              <!-- Bouton-toggle « Archivés » : OFF = secondaire (transparent, bord gris-bleu),
                   ON = rempli bleu — icône miroir Android (folder_eye / list_alt). -->
              <button
                type="button"
                class="archived-btn"
                [class.archived-btn--on]="showArchived()"
                (click)="showArchived.set(!showArchived())"
              >
                <!-- Icône 24 (= défaut des boutons voisins) : cale la hauteur du contenu à 24px. -->
                <app-icon [name]="showArchived() ? 'folder_eye' : 'list_alt'" [size]="24" />
                Archivés
              </button>
              <app-action-icon-with-text-button
                icon="tune"
                [text]="filterButtonLabel()"
                [backgroundColor]="'var(--c-first-blue)'"
                (clicked)="filtersOpen.set(!filtersOpen())"
              />
              <app-styled-search-field
                class="toolbar__search"
                [value]="search()"
                (valueChange)="search.set($event)"
                placeholderText="Rechercher…"
              />
              <app-action-icon-with-text-button icon="add" text="Nouveau" (clicked)="openCreate()" />
            </div>

        <!-- Panneau de filtres repliable : facette catégorie (règne/groupe) puis seuils nutriments. -->
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

        @if (groups().length === 0) {
          <app-empty-list-row
            [text]="
              isFiltering()
                ? 'Aucun aliment ne correspond à la recherche.'
                : 'Aucun aliment — crée un aliment ou importe-en depuis un repas.'
            "
            icon="grocery"
          />
        } @else {
          @for (group of visibleGroups(); track group.title) {
            @if (group.title) {
              <app-titled-divider [title]="group.title" />
            }
            <app-list-frame>
              @for (f of group.foods; track f.uuid) {
                <app-food-list-row
                  [attr.data-uuid]="f.uuid"
                  [food]="f"
                  [selected]="detail()?.uuid === f.uuid"
                  [collapsibleMicros]="true"
                  (rowClicked)="openDetail($event)"
                  (favToggled)="toggleFav($event)"
                />
              }
            </app-list-frame>
          }
          <!-- Sentinelle du chargement progressif : son entrée dans le viewport dévoile la tranche
               suivante (anti-lag : jamais des centaines de rows montées d'un seul coup). -->
          @if (hasMore()) {
            <div #loadMore class="md__sentinel" aria-hidden="true"></div>
          }
        }
          </div>
          @if (isWide()) {
            <!-- La colonne détail entre en slide-down + fade ; re-animée au changement d'aliment. -->
            <div class="md__detail-col" [appRevealIn]="detailUuid()">
              @if (detail(); as f) {
                <app-food-detail-panel
                  [food]="f"
                  [portions]="portionsForFood(f)"
                  (edit)="openEdit($event)"
                  (archiveToggle)="toggleArchive($event)"
                  (remove)="foodToDelete.set($event)"
                  (portionAdd)="onPortionAdd($event)"
                  (portionUpdate)="onPortionUpdate($event)"
                  (portionRemove)="removePortion($event)"
                />
              } @else {
                <app-empty-list-row
                  text="Sélectionne un aliment pour voir son détail."
                  icon="grocery"
                />
              }
            </div>
          }
        </div>
      </div>

      <!-- Mobile étroit : le détail s'ouvre en bottom-sheet (pas de colonne droite). -->
      @if (!isWide()) {
        <app-bottom-sheet [open]="detail() !== null" (dismissRequest)="detailUuid.set(null)">
          @if (detail(); as f) {
            <div class="sheet-detail">
              <app-food-detail-panel
                [food]="f"
                [portions]="portionsForFood(f)"
                (edit)="openEdit($event)"
                (archiveToggle)="toggleArchive($event)"
                (remove)="foodToDelete.set($event)"
                (portionAdd)="onPortionAdd($event)"
                (portionUpdate)="onPortionUpdate($event)"
                (portionRemove)="removePortion($event)"
              />
            </div>
          }
        </app-bottom-sheet>
      }

      <!-- Éditeur (création ou modification d'un aliment perso, per-100 g). -->
      <app-bottom-sheet [open]="editorOpen()" (dismissRequest)="editorOpen.set(false)">
        <div class="editor">
          <app-titled-divider [title]="editUuid() ? 'Modifier l’aliment' : 'Nouvel aliment'" />
          <app-custom-text-field
            label="Nom"
            placeholder="Ex. Yaourt nature"
            [value]="eName()"
            (valueChange)="eName.set($event)"
          />
          <app-custom-text-field
            label="Marque (facultatif)"
            [value]="eBrand()"
            (valueChange)="eBrand.set($event)"
          />
          <!-- Catégorie obligatoire (bloque l'enregistrement si vide) ; sous-choix MACRO/MICRO pour un complément. -->
          <app-filter-dropdown
            label="Catégorie"
            [options]="groupPickerOptions"
            [selected]="eGroupLabel()"
            (select)="eGroupLabel.set($event)"
          />
          @if (isSupplementSelected()) {
            <div class="editor__supp">
              <span class="editor__supp-title">Type de complément</span>
              <div
                class="editor__supp-opt"
                (click)="eSupplementKind.set('COMPLEMENT_MACRO')"
              >
                <app-custom-radio-button
                  [selected]="eSupplementKind() === 'COMPLEMENT_MACRO'"
                  (clicked)="eSupplementKind.set('COMPLEMENT_MACRO')"
                />
                <span>Apporte des macros (whey, gainer…)</span>
              </div>
              <div
                class="editor__supp-opt"
                (click)="eSupplementKind.set('COMPLEMENT_MICRO')"
              >
                <app-custom-radio-button
                  [selected]="eSupplementKind() === 'COMPLEMENT_MICRO'"
                  (clicked)="eSupplementKind.set('COMPLEMENT_MICRO')"
                />
                <span>Micros / actifs (multivitamines…)</span>
              </div>
            </div>
          }
          <div class="editor__grid">
            <app-custom-text-field label="kcal / 100 g" [value]="eKcal()" (valueChange)="eKcal.set($event)" />
            <app-custom-text-field label="Protéines (g)" [value]="eProtein()" (valueChange)="eProtein.set($event)" />
            <app-custom-text-field label="Glucides (g)" [value]="eCarbs()" (valueChange)="eCarbs.set($event)" />
            <app-custom-text-field label="Lipides (g)" [value]="eFat()" (valueChange)="eFat.set($event)" />
          </div>
          <!-- Hydratation : marquer comme boisson eau (auto-comptage 1 g = 1 ml, backfill inclus). -->
          <div class="editor__water">
            <div class="editor__water-text">
              <span class="editor__water-title">Cet aliment est de l'eau</span>
              <span class="editor__water-hint">Compté dans l'hydratation (1 g = 1 ml)</span>
            </div>
            <app-custom-switch [checked]="eIsWater()" (checkedChange)="eIsWater.set($event)" />
          </div>
          @if (!editorValid()) {
            <p class="editor__hint">Nom, catégorie et 4 valeurs numériques (per 100 g) requis.</p>
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

      <app-confirmation-dialog
        [open]="foodToDelete() !== null"
        title="Supprimer l’aliment"
        [message]="deleteMsg()"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDelete()"
        (dismiss)="foodToDelete.set(null)"
      />
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
      /* Créer · recherche · filtres · switch sur une seule ligne (pas de wrap) ; la recherche absorbe
         l'espace restant et se réduit en premier (basis courte + min-width:0) pour tout faire tenir. */
      .toolbar {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      /* Boutons (Ajouter / Filtres) à largeur fixe → la recherche est la SEULE à se réduire, et le
         compteur « Filtres (N) » garde sa place (avant, le « (N) » passait à la ligne faute de place). */
      .toolbar > app-action-icon-with-text-button {
        flex-shrink: 0;
      }
      .toolbar__search {
        flex: 1 1 100px;
        min-width: 0;
      }
      /* Bouton-toggle « Archivés » : OFF = secondaire (transparent + bord gris-bleu),
         ON = rempli bleu (var(--c-blue-medium), parité bouton Android). */
      .archived-btn {
        flex-shrink: 0;
        white-space: nowrap;
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        border: 1px solid var(--c-gray-blue);
        background: transparent;
        color: var(--c-gray-blue);
        /* Mêmes métriques que action-icon-with-text-button (padding 8/12, radius-md, 14px) ;
           le padding vertical retranche le 1px de bord → hauteur IDENTIQUE aux voisins. */
        border-radius: var(--radius-md);
        padding: calc(var(--space-2) - 1px) var(--space-3);
        font-family: var(--font-family-base);
        font-size: 14px;
        line-height: var(--line-height-body);
        cursor: pointer;
      }
      .archived-btn--on {
        background: var(--c-blue-medium);
        border-color: var(--c-blue-medium);
        color: var(--app-text-primary);
      }
      /* Le cadre filtre (reveal animé + conteneur recessed + container-type) est porté par
         <app-filter-panel> ; ici on ne style que le contenu projeté (facettes + grilles de seuils). */
      /* Facette catégorie : règne + groupe côte à côte (s'empile en colonne étroite). */
      .filters__category {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: var(--space-2) var(--space-4);
      }
      /* Deux colonnes par défaut (kcal | Gluc., etc.) ; une seule colonne uniquement si le panneau est
         vraiment étroit (petit mobile) pour ne pas écraser les champs. */
      .filters__grid {
        display: grid;
        /* minmax(0, max-content) → colonnes au plus près du contenu mais compressibles (pas de débordement
           en panneau étroit) ; space-between les répartit sur toute la largeur (gauche ↔ droite). */
        grid-template-columns: minmax(0, max-content) minmax(0, max-content);
        justify-content: space-between;
        gap: var(--space-2) var(--space-4);
      }
      @container (max-width: 400px) {
        .filters__grid {
          grid-template-columns: 1fr;
        }
      }
      /* Master/détail : empilé par défaut ; deux colonnes (liste | détail collant) au-delà de 1000px. */
      .master-detail {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .master-detail--wide {
        display: grid;
        /* Master/détail 2/5 (liste) – 3/5 (détail), comme la page Recettes & repas. */
        grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
        gap: var(--page-gutter);
        align-items: start;
      }
      .md__master {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        min-width: 0;
      }
      /* Sections Récents / Favoris / Tous : le cadre est <app-list-frame>. La row est le composant
         partagé food-list-row (sa pilule sert au picker) → ici on la met à plat DANS le cadre et on pose
         le filet INSET + les coins arrondis (via ::ng-deep), scopés à l'app-list-frame du catalogue.
         (food-list-row garde son padding d'origine g space-3 / d space-2, calé sur le chevron.) */
      app-list-frame ::ng-deep app-food-list-row .frow {
        background: transparent;
        border-radius: 0;
      }
      app-list-frame ::ng-deep app-food-list-row:not(:last-child) .frow {
        position: relative;
      }
      app-list-frame ::ng-deep app-food-list-row:not(:last-child) .frow::after {
        content: '';
        position: absolute;
        left: var(--space-3);
        right: var(--space-2);
        bottom: 0;
        height: 1px;
        background: var(--c-second-blue);
      }
      /* Row sélectionnée : pas de filet en bas (le liseré de food-list-row fait office de séparateur). */
      app-list-frame ::ng-deep app-food-list-row:not(:last-child) .frow.frow--selected::after {
        display: none;
      }
      app-list-frame ::ng-deep app-food-list-row:first-child .frow {
        border-top-left-radius: var(--radius-md);
        border-top-right-radius: var(--radius-md);
      }
      app-list-frame ::ng-deep app-food-list-row:last-child .frow {
        border-bottom-left-radius: var(--radius-md);
        border-bottom-right-radius: var(--radius-md);
      }
      /* Cible d'observation du chargement progressif : invisible, ne change rien au flux visuel. */
      .md__sentinel {
        height: 1px;
      }
      .md__detail-col {
        position: sticky;
        top: var(--space-3);
        /* Colonne détail plafonnée à la fenêtre + scroll interne : sinon, micros déroulés, le panneau
           dépasse la hauteur d'écran et son bas (Portions) devient inatteignable (élément sticky pinné
           plus haut que le viewport). Bas dégagé (88px) pour la barre de nav flottante. */
        max-height: calc(100vh - var(--space-3) - 88px);
        overflow-y: auto;
        min-width: 0;
      }
      .sheet-detail {
        padding: 0 var(--space-4) var(--space-3);
      }
      .editor {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        padding: 0 var(--space-4) var(--space-3);
      }
      .editor__grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--space-3);
      }
      /* Sous-choix complément (MACRO vs MICRO) : deux options radio cliquables sur toute la ligne. */
      .editor__supp {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
      }
      .editor__supp-title {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      .editor__supp-opt {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        cursor: pointer;
        color: var(--app-text-primary);
        font-size: 14px;
      }
      .editor__water {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-3);
      }
      .editor__water-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .editor__water-title {
        color: var(--app-text-primary);
        font-size: 14px;
      }
      .editor__water-hint {
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
      .editor__hint {
        margin: 0;
        font-size: 12px;
        font-style: italic;
        color: var(--app-text-tertiary);
      }
    `,
  ],
})
export class FoodCataloguePage {
  private readonly sync = inject(SyncEngine);
  private readonly hostEl = inject(ElementRef<HTMLElement>);
  protected readonly foodRepo = inject(FoodRepository);
  protected readonly mealRepo = inject(MealRepository);

  // -------------------- Recherche + filtres seuils --------------------

  protected readonly search = signal('');
  protected readonly showArchived = signal(false);
  protected readonly filtersOpen = signal(false);
  /** Section micros du panneau de filtres repliée par défaut (chevron « Afficher les micros »). */
  protected readonly microsOpen = signal(false);

  /** Sections du panneau de filtres (libellés + unités issus des constantes catalogue). */
  protected readonly filterSections = [
    { title: 'Macros', items: FILTERABLE_MACROS, collapsible: false },
    { title: 'Vitamines & minéraux', items: FILTERABLE_MICROS, collapsible: true },
  ];

  // -------------------- Facette catégorie (règne + groupe) --------------------
  // Sentinelle 'Tous' = pas de filtre (convention FilterDropdown, cf. muscles-page). Les options ne
  // listent que les règnes/groupes effectivement présents dans le catalogue (pas de filtre vide).

  protected readonly realmFilter = signal('Tous');
  protected readonly groupFilter = signal('Tous');

  /** Règnes présents dans le catalogue (label FR), préfixés de « Tous ». */
  protected readonly realmOptions = computed(() => {
    const present = new Set(
      this.foodRepo
        .foods()
        .filter((f) => f.foodGroup)
        .map((f) => realmOf(f.foodGroup)),
    );
    return ['Tous', ...FOOD_REALMS.filter((r) => present.has(r)).map((r) => FOOD_REALM_LABEL[r])];
  });

  /** Groupes présents dans le catalogue (label FR dédupliqué), préfixés de « Tous ». */
  protected readonly groupOptions = computed(() => {
    const present = new Set(
      this.foodRepo
        .foods()
        .map((f) => (f.foodGroup ?? '').toUpperCase())
        .filter((g) => g.length > 0),
    );
    return ['Tous', ...FOOD_GROUP_OPTIONS.filter((o) => o.codes.some((c) => present.has(c))).map((o) => o.label)];
  });

  /** Règne actif (null = « Tous ») et codes de groupe actifs (vide = « Tous »), passés à buildFoodGroups. */
  private readonly activeRealm = computed<FoodRealm | null>(() =>
    this.realmFilter() === 'Tous' ? null : realmFromLabel(this.realmFilter()),
  );
  private readonly activeGroupCodes = computed<string[]>(() =>
    this.groupFilter() === 'Tous' ? [] : groupCodesForLabel(this.groupFilter()),
  );
  /** Nombre de facettes catégorie actives (règne + groupe), pour le compteur du bouton « Filtres ». */
  protected readonly activeCategoryCount = computed(
    () => (this.realmFilter() !== 'Tous' ? 1 : 0) + (this.groupFilter() !== 'Tous' ? 1 : 0),
  );

  /** État de saisie des seuils, indexé par clé de nutriment (opérateur + valeur brute). */
  private readonly thresholdInputs = signal<Record<string, ThresholdInput>>({});

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

  // -------------------- Liste (master) --------------------

  private readonly recentUuids = computed(() => recentFoodUuids(this.mealRepo.entries()));

  protected readonly groups = computed(() =>
    buildFoodGroups(this.foodRepo.foods(), this.recentUuids(), this.search(), {
      showArchived: this.showArchived(),
      thresholds: this.activeThresholds(),
      realm: this.activeRealm(),
      groupCodes: this.activeGroupCodes(),
    }),
  );

  /** Recherche/seuils/facette catégorie actifs → liste à plat filtrée (sinon les blocs Récents/Favoris/Tous). */
  protected readonly isFiltering = computed(
    () =>
      this.search().trim().length > 0 ||
      this.activeThresholds().length > 0 ||
      this.activeCategoryCount() > 0,
  );

  /** Colonne master : Récents → Favoris → Tous → Archivés (la liste à plat de recherche a un titre vide). */
  protected readonly masterGroups = computed(() => {
    const order = ['Récents', 'Favoris', 'Tous', 'Archivés', ''];
    return [...this.groups()].sort((a, b) => order.indexOf(a.title) - order.indexOf(b.title));
  });

  // -------------------- Chargement progressif (anti-lag) --------------------
  // La colonne master ne rend que `visibleCount` rows ; une sentinelle en bas dévoile la tranche
  // suivante quand elle entre dans le viewport (IntersectionObserver, câblé dans le constructeur).

  /** Signature des facettes (recherche + seuils + catégorie + archivés) : son changement = nouvelle liste. */
  private readonly listSignature = computed(() =>
    JSON.stringify([
      this.search().trim(),
      this.activeThresholds(),
      this.activeRealm(),
      this.activeGroupCodes(),
      this.showArchived(),
    ]),
  );

  /** Nombre de rows rendues : repart à la première tranche dès que la liste change (filtre/recherche),
   *  agrandi par paliers via la sentinelle. `linkedSignal` = reset auto sur `listSignature`. */
  protected readonly visibleCount = linkedSignal(() => {
    this.listSignature();
    return CATALOGUE_BATCH;
  });

  /** Total d'aliments dans la colonne master (toutes sections), pour borner la fenêtre. */
  private readonly masterTotal = computed(() =>
    this.masterGroups().reduce((n, g) => n + g.foods.length, 0),
  );

  /** Vue tronquée des sections master à `visibleCount` rows (réparties dans l'ordre des sections). */
  protected readonly visibleGroups = computed<FoodGroup[]>(() => {
    let budget = this.visibleCount();
    const out: FoodGroup[] = [];
    for (const g of this.masterGroups()) {
      if (budget <= 0) break;
      const foods = g.foods.length <= budget ? g.foods : g.foods.slice(0, budget);
      budget -= foods.length;
      out.push({ title: g.title, foods });
    }
    return out;
  });

  /** Reste-t-il des aliments non rendus (→ sentinelle affichée) ? */
  protected readonly hasMore = computed(() => this.visibleCount() < this.masterTotal());

  /** Sentinelle de fin de liste observée pour déclencher la tranche suivante (présente si `hasMore`). */
  private readonly loadMore = viewChild<ElementRef<HTMLElement>>('loadMore');

  protected toggleFav(f: LocalFood): void {
    void this.foodRepo.update(f.uuid, { isFavorite: !f.isFavorite });
  }

  // -------------------- Détail (master/détail desktop + sheet mobile) --------------------

  /** Vrai au-delà de 1000px : assez de place pour la colonne détail (sinon bottom-sheet). */
  protected readonly isWide = signal(false);

  /** Query param `?food=<uuid>` (withComponentInputBinding) — pré-sélectionne un aliment en détail
   *  (ex. action « Voir les détails de l'aliment » depuis une row du journal). */
  readonly food = input('');

  /** UUID de l'aliment ouvert en détail (pas une copie : le détail reste dérivé du catalogue vivant). */
  protected readonly detailUuid = signal<string | null>(null);

  /**
   * Détail dérivé du catalogue (réactif) : se met à jour si l'aliment est modifié (sync/WS) et se vide
   * tout seul s'il disparaît (suppression, recherche qui l'exclut) — plus d'aliment fantôme figé.
   */
  protected readonly detail = computed<LocalFood | null>(
    () => this.foodRepo.foods().find((f) => f.uuid === this.detailUuid()) ?? null,
  );

  protected openDetail(f: LocalFood): void {
    this.detailUuid.set(f.uuid);
  }

  /** Portions nommées de l'aliment courant (le panneau détail les trie par grammage). */
  protected portionsForFood(f: LocalFood): LocalFoodPortion[] {
    return this.foodRepo.portions().filter((p) => p.foodUUID === f.uuid);
  }

  protected onPortionAdd(e: { label: string; grams: number }): void {
    const f = this.detail();
    if (f) void this.foodRepo.addPortion(f.uuid, e);
  }

  protected onPortionUpdate(e: { uuid: string; label: string; grams: number }): void {
    void this.foodRepo.updatePortion(e.uuid, { label: e.label, grams: e.grams });
  }

  protected removePortion(uuid: string): void {
    void this.foodRepo.removePortion(uuid);
  }

  protected toggleArchive(f: LocalFood): void {
    void this.foodRepo.update(f.uuid, { archived: !f.archived });
    this.detailUuid.set(null);
  }

  // -------------------- Éditeur (création / modification) --------------------

  protected readonly editorOpen = signal(false);
  protected readonly editUuid = signal<string | null>(null);
  protected readonly eName = signal('');
  protected readonly eBrand = signal('');
  protected readonly eKcal = signal('');
  protected readonly eProtein = signal('');
  protected readonly eCarbs = signal('');
  protected readonly eFat = signal('');
  protected readonly eIsWater = signal(false);

  // Catégorie : label de groupe choisi (null = non renseigné → bloque l'enregistrement) + sous-choix
  // MACRO/MICRO quand le label sélectionné est « Compléments » (recouvre 2 codes).
  protected readonly eGroupLabel = signal<string | null>(null);
  protected readonly eSupplementKind = signal<'COMPLEMENT_MACRO' | 'COMPLEMENT_MICRO'>('COMPLEMENT_MACRO');
  /** Labels de groupe proposés au sélecteur de création (taxonomie complète, dédupliquée). */
  protected readonly groupPickerOptions: string[] = [...FOOD_GROUP_LABELS];

  /** Codes recouverts par le label choisi ; > 1 = « Compléments » (sous-choix MACRO/MICRO requis). */
  private readonly eGroupCodes = computed(() =>
    this.eGroupLabel() ? groupCodesForLabel(this.eGroupLabel()!) : [],
  );
  protected readonly isSupplementSelected = computed(() => this.eGroupCodes().length > 1);
  /** Code de groupe effectif à enregistrer (null si rien de sélectionné → catégorie obligatoire). */
  protected readonly eGroupCode = computed<string | null>(() => {
    const codes = this.eGroupCodes();
    if (codes.length === 0) return null;
    return codes.length === 1 ? codes[0] : this.eSupplementKind();
  });

  protected openCreate(): void {
    this.editUuid.set(null);
    this.eName.set('');
    this.eBrand.set('');
    this.eGroupLabel.set(null);
    this.eSupplementKind.set('COMPLEMENT_MACRO');
    this.eKcal.set('');
    this.eProtein.set('');
    this.eCarbs.set('');
    this.eFat.set('');
    this.eIsWater.set(false);
    this.editorOpen.set(true);
  }

  protected openEdit(f: LocalFood): void {
    this.editUuid.set(f.uuid);
    this.eName.set(f.name);
    this.eBrand.set(f.brand ?? '');
    const group = (f.foodGroup ?? '').toUpperCase();
    this.eGroupLabel.set(group ? foodGroupLabel(group) : null);
    this.eSupplementKind.set(group === 'COMPLEMENT_MICRO' ? 'COMPLEMENT_MICRO' : 'COMPLEMENT_MACRO');
    this.eKcal.set(String(f.kcalPer100g));
    this.eProtein.set(String(f.proteinPer100g));
    this.eCarbs.set(String(f.carbsPer100g));
    this.eFat.set(String(f.fatPer100g));
    this.eIsWater.set(f.isWater);
    this.detailUuid.set(null);
    this.editorOpen.set(true);
  }

  protected readonly editorValid = computed(
    () =>
      this.eName().trim().length > 0 &&
      this.eGroupCode() !== null &&
      parseMacro(this.eKcal()) !== null &&
      parseMacro(this.eProtein()) !== null &&
      parseMacro(this.eCarbs()) !== null &&
      parseMacro(this.eFat()) !== null,
  );

  protected saveEditor(): void {
    if (!this.editorValid()) return;
    const patch = {
      name: this.eName().trim(),
      brand: this.eBrand().trim() || null,
      foodGroup: this.eGroupCode(),
      kcalPer100g: parseMacro(this.eKcal())!,
      proteinPer100g: parseMacro(this.eProtein())!,
      carbsPer100g: parseMacro(this.eCarbs())!,
      fatPer100g: parseMacro(this.eFat())!,
      isWater: this.eIsWater(),
    };
    const uuid = this.editUuid();
    this.editorOpen.set(false);
    if (uuid) void this.foodRepo.update(uuid, patch);
    else void this.foodRepo.create(patch);
  }

  // -------------------- Supprimer --------------------

  protected readonly foodToDelete = signal<LocalFood | null>(null);
  protected readonly deleteMsg = computed(() => {
    const f = this.foodToDelete();
    return f
      ? `Supprimer « ${f.name} » du catalogue ? Les repas déjà journalisés sont conservés (snapshot).`
      : '';
  });

  protected confirmDelete(): void {
    const f = this.foodToDelete();
    this.foodToDelete.set(null);
    if (f) {
      void this.foodRepo.remove(f.uuid);
      this.detailUuid.set(null);
    }
  }

  /** Garde anti-répétition : on ne recentre la liste qu'une fois par arrivée en deep-link (?food=…). */
  private deepLinkScrolled = false;

  /** Attend que la row de l'aliment soit montée (rendu zoneless / chargement progressif) puis la centre
   *  dans le scroller. `scrollIntoView` remonte au bon conteneur scrollable tout seul. */
  private scrollMasterToFood(uuid: string): void {
    let tries = 0;
    const tick = () => {
      const row = this.hostEl.nativeElement.querySelector(
        `app-food-list-row[data-uuid="${uuid}"]`,
      ) as HTMLElement | null;
      if (row) {
        row.scrollIntoView({ block: 'center', behavior: 'smooth' });
        return;
      }
      if (tries++ < 30) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
    const destroyRef = inject(DestroyRef);
    // Pré-sélection du détail via ?food=<uuid> (navigation « voir les détails » depuis le journal).
    effect(() => {
      const f = this.food();
      if (f) this.detailUuid.set(f);
    });
    // Deep-link ?food=… : on recentre la colonne de gauche sur l'aliment. On suit `masterGroups()` car la
    // liste arrive en async (Dexie) ; dès que l'aliment y est, on élargit la fenêtre progressive pour
    // monter sa row si besoin, puis on scrolle — une seule fois (garde `deepLinkScrolled`).
    effect(() => {
      const f = this.food();
      const groups = this.masterGroups();
      if (!f || this.deepLinkScrolled) return;
      const idx = groups.flatMap((g) => g.foods).findIndex((x) => x.uuid === f);
      if (idx < 0) return;
      this.deepLinkScrolled = true;
      if (idx >= untracked(() => this.visibleCount())) {
        this.visibleCount.set(Math.min(idx + 1, untracked(() => this.masterTotal())));
      }
      this.scrollMasterToFood(f);
    });
    // Master/détail (2 colonnes) seulement quand l'outlet est assez large, sinon détail en sheet.
    const mq = window.matchMedia('(min-width: 1000px)');
    this.isWide.set(mq.matches);
    const onChange = (e: MediaQueryListEvent) => this.isWide.set(e.matches);
    mq.addEventListener('change', onChange);
    destroyRef.onDestroy(() => mq.removeEventListener('change', onChange));

    // Chargement progressif : on observe la sentinelle de fin de liste ; chaque entrée dans le
    // viewport dévoile une tranche de plus. L'effet re-observe à chaque palier pour continuer si la
    // sentinelle reste visible (rootMargin = pré-chargement). Zoneless : écrire le signal planifie le rendu.
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting) && this.hasMore()) {
          this.visibleCount.update((n) => Math.min(n + CATALOGUE_BATCH, this.masterTotal()));
        }
      },
      { rootMargin: '800px 0px' },
    );
    effect(() => {
      this.visibleCount();
      const el = this.loadMore()?.nativeElement;
      io.disconnect();
      if (el) io.observe(el);
    });
    destroyRef.onDestroy(() => io.disconnect());
  }
}
