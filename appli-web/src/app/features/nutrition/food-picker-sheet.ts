import { ChangeDetectionStrategy, Component, computed, inject, output, input, signal } from '@angular/core';
import { LocalFood } from '@core/models/food.model';
import { OffProduct } from '@core/models/off-product.model';
import { NutritionOffApi } from '@core/api/nutrition-off-api';
import { AppDb } from '@core/sync/dexie-db';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { TabRowCustom } from '@designsystem/common_components/tab-row-custom';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { AppIcon } from '@designsystem/icons/app-icon';
import { FoodListRow } from './food-list-row';
import { MACRO_COLOR, SUGAR_COLOR } from './macro-colors';
import { FoodRepository } from './food.repository';
import { MealRepository } from './meal.repository';
import { buildFoodGroups, recentFoodUuids } from './food-catalogue';
import { microLineItems } from './micro-colors';
import { isHighSugar } from './journal-utils';

/** "12,5" ou "12.5" → 12.5 ; null si vide/blanc, non numérique ou négatif (`Number('')===0`). */
export function parseMacro(raw: string): number | null {
  const trimmed = raw.trim();
  if (trimmed === '') return null;
  const v = Number(trimmed.replace(',', '.'));
  return Number.isFinite(v) && v >= 0 ? v : null;
}

/**
 * Sheet de recherche/ajout d'aliment (V4 NUTRITION_DESIGN §5.3) — 3 onglets :
 * **Mon catalogue** (favoris puis récents puis tout, recherche, étoile favori),
 * **Open Food Facts** (recherche via le proxy serveur, sélection = import → foods avec dédup
 * sourceRef, §4.1), **Créer** (aliment perso source CUSTOM, macros per-100g).
 * Émet `foodPicked` (LocalFood du catalogue) — le parent enchaîne sur le choix de quantité.
 */
@Component({
  selector: 'app-food-picker-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppBottomSheet,
    TabRowCustom,
    StyledSearchField,
    EmptyListRow,
    ActionIconButton,
    ActionIconWithTextButton,
    CustomTextField,
    TitledDivider,
    AppIcon,
    FoodListRow,
  ],
  template: `
    <app-bottom-sheet [open]="open()" maxWidth="900px" (dismissRequest)="dismissRequest.emit()">
      <div class="fps">
        <app-titled-divider title="Ajouter un aliment" />
        <app-tab-row-custom [items]="tabs" [selectedIndex]="tab()" [height]="42" (tabSelected)="tab.set($event)" />

        @switch (tab()) {
          @case (0) {
            <!-- Onglet 1 : Mon catalogue (favoris / récents / tout). -->
            <app-styled-search-field
              [value]="search()"
              (valueChange)="search.set($event)"
              placeholderText="Rechercher dans mon catalogue…"
            />
            @if (groups().length === 0) {
              <app-empty-list-row text="Aucun aliment — importe depuis Open Food Facts ou crée un aliment." icon="grocery" />
            }
            @for (group of groups(); track group.title) {
              @if (group.title) {
                <app-titled-divider [title]="group.title" />
              }
              @for (f of group.foods; track f.uuid) {
                <app-food-list-row
                  [food]="f"
                  (rowClicked)="foodPicked.emit($event)"
                  (favToggled)="toggleFav($event)"
                />
              }
            }
          }

          @case (1) {
            <!-- Onglet 2 : Open Food Facts via le proxy serveur (sélection = import dans le catalogue). -->
            <div class="offbar">
              <app-styled-search-field
                class="offbar__field"
                [value]="offQuery()"
                (valueChange)="offQuery.set($event)"
                placeholderText="Produit, marque… (Open Food Facts)"
              />
              <app-action-icon-button icon="search" [disabled]="offLoading()" (clicked)="searchOff()" />
            </div>
            @if (offLoading()) {
              <app-empty-list-row text="Recherche en cours…" icon="search" />
            } @else if (offError()) {
              <app-empty-list-row [text]="offError()!" icon="error" contentColor="var(--c-red-medium)" />
            } @else if (offSearched() && offResults().length === 0) {
              <app-empty-list-row text="Aucun produit trouvé." icon="search_off" />
            }
            @for (p of offResults(); track p.sourceRef) {
              <div class="frow" (click)="pickOff(p)">
                <div class="frow__main">
                  <span class="frow__name">{{ p.name }}</span>
                  <span class="frow__sub">
                    @if (p.brand) {
                      {{ p.brand }} ·
                    }
                    <span [style.color]="macro.kcal">{{ round(p.kcalPer100g) }} kcal</span> ·
                    <span [style.color]="macro.carbs">G {{ round1(p.carbsPer100g) }}</span> ·
                    <span [style.color]="macro.fat">L {{ round1(p.fatPer100g) }}</span> ·
                    <span [style.color]="macro.protein">P {{ round1(p.proteinPer100g) }}</span>
                    @if (p.sugarPer100g !== null) {
                      · <span [style.color]="sugarTintOf(p.sugarPer100g)">S {{ round1(p.sugarPer100g!) }}</span>
                    }
                    /100 g
                  </span>
                  @if (micros(p).length) {
                    <span class="frow__micros">
                      @for (mi of micros(p); track $index) {
                        <span [style.color]="mi.color">{{ mi.short }} {{ mi.value }} {{ mi.unit }}</span
                        >{{ $last ? '' : ' · ' }}
                      }
                    </span>
                  }
                </div>
                <app-icon name="download" [size]="22" color="var(--app-primary-action)" />
              </div>
            }
          }

          @case (2) {
            <!-- Onglet 3 : création d'un aliment perso (source CUSTOM, per-100g). -->
            <div class="create">
              <app-custom-text-field label="Nom" placeholder="Ex. Yaourt nature" [value]="cName()" (valueChange)="cName.set($event)" />
              <app-custom-text-field label="Marque (facultatif)" [value]="cBrand()" (valueChange)="cBrand.set($event)" />
              <div class="create__grid">
                <app-custom-text-field label="kcal / 100 g" [value]="cKcal()" (valueChange)="cKcal.set($event)" />
                <app-custom-text-field label="Protéines (g)" [value]="cProtein()" (valueChange)="cProtein.set($event)" />
                <app-custom-text-field label="Glucides (g)" [value]="cCarbs()" (valueChange)="cCarbs.set($event)" />
                <app-custom-text-field label="Lipides (g)" [value]="cFat()" (valueChange)="cFat.set($event)" />
              </div>
              @if (!createValid()) {
                <p class="create__hint">Nom + 4 valeurs numériques (per 100 g) requis.</p>
              }
              <app-action-icon-with-text-button
                icon="add"
                text="Créer et ajouter"
                [fullWidth]="true"
                [disabled]="!createValid()"
                (clicked)="createFood()"
              />
            </div>
          }
        }
      </div>
    </app-bottom-sheet>
  `,
  styles: [
    `
      .fps {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        padding: 0 var(--space-4) var(--space-3);
      }
      .frow {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 8px var(--space-2) 8px var(--space-3);
        cursor: pointer;
      }
      .frow:hover {
        filter: brightness(1.08);
      }
      .frow__main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .frow__name {
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
      }
      /* Sous-ligne (marque · macros /100 g) : « · » + texte en gris-bleu ; valeurs macro colorées via leurs spans. */
      .frow__sub {
        color: var(--c-gray-blue);
        font-size: 12px;
      }
      /* Micros colorés par famille (token via micro-colors) : chaque span porte sa propre teinte. */
      .frow__micros {
        font-size: 11px;
        opacity: 0.9;
        margin-top: 2px;
      }
      .offbar {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .offbar__field {
        flex: 1;
      }
      .create {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .create__grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--space-3);
      }
      .create__hint {
        margin: 0;
        font-size: 12px;
        color: var(--app-text-tertiary);
        font-style: italic;
      }
    `,
  ],
})
export class FoodPickerSheet {
  private readonly db = inject(AppDb);
  private readonly offApi = inject(NutritionOffApi);
  protected readonly foodRepo = inject(FoodRepository);
  private readonly mealRepo = inject(MealRepository);

  readonly open = input(false);
  readonly dismissRequest = output<void>();
  readonly foodPicked = output<LocalFood>();

  protected readonly tabs = ['Mon catalogue', 'Open Food Facts', 'Créer'];
  protected readonly tab = signal(0);

  // -------------------- Mon catalogue --------------------
  protected readonly search = signal('');

  /** uuids des aliments récemment consommés (entries les plus récentes d'abord). */
  private readonly recentUuids = computed(() => recentFoodUuids(this.mealRepo.entries()));

  /** Blocs Favoris / Récents / Tous (recherche active → liste à plat filtrée). */
  protected readonly groups = computed(() =>
    buildFoodGroups(this.foodRepo.foods(), this.recentUuids(), this.search()),
  );

  protected toggleFav(f: LocalFood): void {
    void this.foodRepo.update(f.uuid, { isFavorite: !f.isFavorite });
  }

  // -------------------- Open Food Facts --------------------
  protected readonly offQuery = signal('');
  protected readonly offResults = signal<OffProduct[]>([]);
  protected readonly offLoading = signal(false);
  protected readonly offSearched = signal(false);
  protected readonly offError = signal<string | null>(null);

  protected searchOff(): void {
    const q = this.offQuery().trim();
    if (!q) return;
    this.offLoading.set(true);
    this.offError.set(null);
    this.offApi.search(q).subscribe({
      next: (results) => {
        this.offResults.set(results);
        this.offSearched.set(true);
        this.offLoading.set(false);
      },
      error: () => {
        this.offError.set('Open Food Facts ne répond pas pour le moment — réessaie dans quelques secondes.');
        this.offLoading.set(false);
      },
    });
  }

  /** Import → foods (dédup sourceRef) puis émission du LocalFood importé. */
  protected pickOff(product: OffProduct): void {
    void this.foodRepo.importFromOff(product).then(async (uuid) => {
      const food = await this.db.foods.get(uuid);
      if (food) this.foodPicked.emit(food);
    });
  }

  // -------------------- Créer un aliment perso --------------------
  protected readonly cName = signal('');
  protected readonly cBrand = signal('');
  protected readonly cKcal = signal('');
  protected readonly cProtein = signal('');
  protected readonly cCarbs = signal('');
  protected readonly cFat = signal('');

  protected readonly createValid = computed(
    () =>
      this.cName().trim().length > 0 &&
      parseMacro(this.cKcal()) !== null &&
      parseMacro(this.cProtein()) !== null &&
      parseMacro(this.cCarbs()) !== null &&
      parseMacro(this.cFat()) !== null,
  );

  protected createFood(): void {
    if (!this.createValid()) return;
    void this.foodRepo
      .create({
        name: this.cName().trim(),
        brand: this.cBrand().trim() || null,
        kcalPer100g: parseMacro(this.cKcal())!,
        proteinPer100g: parseMacro(this.cProtein())!,
        carbsPer100g: parseMacro(this.cCarbs())!,
        fatPer100g: parseMacro(this.cFat())!,
      })
      .then(async (uuid) => {
        const food = await this.db.foods.get(uuid);
        this.cName.set('');
        this.cBrand.set('');
        this.cKcal.set('');
        this.cProtein.set('');
        this.cCarbs.set('');
        this.cFat.set('');
        if (food) this.foodPicked.emit(food);
      });
  }

  /** Code couleur par macro (P/G/L/kcal), partagé avec le Journal et le catalogue. */
  protected readonly macro = MACRO_COLOR;

  /** Vitamines & minéraux présents (per 100 g), colorés par famille (micro-colors). Vide si aucun. */
  protected micros(m: LocalFood | OffProduct) {
    return microLineItems(m);
  }

  /** Teinte des sucres d'un résultat OFF : alerte si riche (> 22,5 g/100 g), sinon --macro-sugar. */
  protected sugarTintOf(sugarPer100g: number | null): string {
    return isHighSugar(sugarPer100g) ? 'var(--app-snackbar-warning)' : SUGAR_COLOR;
  }

  protected round(v: number): number {
    return Math.round(v);
  }
  protected round1(v: number): number {
    return Math.round(v * 10) / 10;
  }
}
