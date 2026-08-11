import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { LocalMuscle } from '@core/models/muscle.model';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { SyncEngine } from '@core/sync/sync-engine';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { FilterDropdown } from '@designsystem/common_components/filter-dropdown';
import { EntityListRow } from '@designsystem/common_components/entity-list-row';
import { EntityRowTrailing } from '@designsystem/common_components/entity-row-trailing';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ListFrame } from '@designsystem/common_components/list-frame';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { AppIcon } from '@designsystem/icons/app-icon';
import { RevealIn } from '@designsystem/common_components/reveal-in';
import { paletteForZone, resolveCssColor } from '../stats/palette-util';
import { MuscleRepository } from './muscle.repository';
import { MuscleDetailPage } from './muscle-detail-page';

/** Zones canoniques (miroir core/data/Zones.kt) — ordre stable du filtre comme Android. */
const ZONES_ALL = ['Chest', 'Back', 'Shoulders', 'Arms', 'Legs', 'Core'];

/**
 * Écran Muscles — master-detail 2 colonnes (même pattern que la page Exercices) :
 * **gauche** : ligne select Zone + boutons (+ ajout · tri · sync · ⋮ bottom sheet exemple-export-tout
 * effacer), recherche pleine largeur dessous, puis rows simples (nom · nuage sync · flèche → seul
 * déclencheur de sélection, fond primaryAction quand sélectionné) + FormDialog d'ajout (miroir du
 * AddMuscleDialog Android : nom + zone texte libre, validation « Nom requis ») ; **droite** :
 * muscle-detail-page en mode embedded (action bar, card Zone/Groupe, stats hebdo, exercices liés —
 * l'édition zone/groupe et la suppression vivent là). Sans sélection : structure complète du détail
 * en placeholders cadres bleus. Empilement vertical < 900px. Données offline-first via Dexie.
 */
@Component({
  selector: 'app-muscles-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    StyledSearchField,
    FilterDropdown,
    EntityListRow,
    EntityRowTrailing,
    ActionIconButton,
    EmptyListRow,
    ListFrame,
    ConfirmationDialog,
    FormDialog,
    CustomTextField,
    OptionsBottomSheet,
    AppIcon,
    RevealIn,
    MuscleDetailPage,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Mes muscles" />

      <div class="page__body">
        <div class="split">
          <!-- Gauche : contrôles + rows simples (nom · sync · favori · flèche de sélection). -->
          <div class="split__list">
            <!-- Ligne unique : select Zone + recherche + boutons (+ ajout · tri · sync · ⋮). -->
            <div class="toolbar">
              <app-filter-dropdown
                class="toolbar__zone"
                label="Zone"
                [options]="zoneOptions()"
                [selected]="zoneFilter()"
                (select)="zoneFilter.set($event)"
              />
              <app-styled-search-field
                class="toolbar__search"
                [value]="search()"
                (valueChange)="search.set($event)"
                placeholderText="Rechercher un muscle…"
              />
              <div class="toolbar__buttons">
                <app-action-icon-button icon="add" (clicked)="openAdd()" />
                <div class="toolbar__sort">
                  <app-action-icon-button icon="sort" (clicked)="sortOpen.set(!sortOpen())" />
                  @if (sortOpen()) {
                    <div class="toolbar__backdrop" (click)="sortOpen.set(false)"></div>
                    <div class="toolbar__menu">
                      @for (opt of sortOptions; track opt) {
                        <button class="toolbar__menu-item" (click)="chooseSort(opt)">{{ opt }}</button>
                      }
                    </div>
                  }
                </div>
                <app-action-icon-button
                  [icon]="allSynced() ? 'cloud_done' : 'cloud_off'"
                  [iconSize]="28"
                  [hasBackground]="false"
                  [tint]="allSynced() ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                  (clicked)="refresh()"
                />
                <app-action-icon-button icon="more_vert" (clicked)="showOptionsSheet.set(true)" />
              </div>
            </div>

            <app-titled-divider title="Muscles" />

            @if (filtered().length === 0) {
              <app-empty-list-row text="Aucun muscle — ajoute-en un (bouton +) ou synchronise." />
            } @else {
              <!-- Cadre thirdBlue façon catalogue d'aliments : rows à plat, filets inset entre elles. -->
              <app-list-frame>
              @for (m of filtered(); track m.uuid) {
                <app-entity-list-row
                  [name]="m.name"
                  [nameMaxLines]="1"
                  [nameWeight]="1"
                  backgroundColor="transparent"
                  [contentEndPadding]="6"
                >
                  <span trailing appEntityRowTrailing>
                    <!-- Colonne chips (1/3) TOUJOURS rendue (alignement vertical inter-rows) :
                         groupe (nuance stats) + zone (couleur de zone) du muscle. -->
                    <span class="row__tags">
                      @if (m.muscleGroup) {
                        <span class="row__tag" [style.--tag-c]="groupColorMap().get(m.muscleGroup!) ?? ''">{{ m.muscleGroup }}</span>
                      }
                      @if (m.zone) {
                        <span class="row__tag" [style.--tag-c]="zoneColors()[m.zone!] ?? ''">{{ m.zone }}</span>
                      }
                    </span>
                    <!-- Colonne boutons (1/3). -->
                    <span class="row__btns">
                      <app-icon
                        [name]="m.synced ? 'cloud_done' : 'cloud_off'"
                        [size]="22"
                        [color]="m.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                      />
                      <app-action-icon-button
                        [icon]="m.isFavorite ? 'star' : 'star_border'"
                        [backgroundColor]="m.isFavorite ? 'var(--c-orange-medium)' : 'var(--app-bg-button)'"
                        [tint]="m.isFavorite ? 'var(--app-on-accent)' : 'var(--app-text-primary)'"
                        (clicked)="toggleFav(m)"
                      />
                      <!-- Seule la flèche sélectionne (fond primaryAction si sélectionné, comme Exercices). -->
                      <app-action-icon-button
                        icon="arrow_right_alt"
                        tint="var(--app-text-primary)"
                        [backgroundColor]="m.uuid === effectiveSelected() ? 'var(--app-primary-action)' : 'var(--c-blue-medium)'"
                        (clicked)="selectedUuid.set(m.uuid)"
                      />
                    </span>
                  </span>
                </app-entity-list-row>
              }
              </app-list-frame>
            }
          </div>

          <!-- Droite : détail du muscle sélectionné (muscle-detail-page en mode embedded).
               Entre en slide-down + fade ; re-animée (fondu seul) au changement de muscle. -->
          <div class="split__detail" [appRevealIn]="effectiveSelected()">
            @if (effectiveSelected(); as uuid) {
              <app-muscle-detail-page [embedded]="true" [uuid]="uuid" />
            } @else {
              <!-- Aucune sélection : structure complète du détail, sections en placeholders cadres bleus. -->
              <div class="ph">
                <!-- Cadre unique (titre + boutons + détails) — miroir du maincard de la page détail. -->
                <div class="ph__box ph__box--main">
                  <app-titled-divider title="Aucun muscle sélectionné" />
                  <div class="ph__frame">Boutons · Zone · Groupe</div>
                </div>
                <!-- Titres désormais DANS leur cadre (miroir des maincards de la page détail). -->
                <div class="ph__box ph__box--chart"><div class="ph__frame">Stats</div></div>
                <div class="ph__box ph__box--exos"><div class="ph__frame">Exercices liés</div></div>
              </div>
            }
          </div>
        </div>
      </div>

      <!-- ⋮ options de la liste (miroir MuscleListOptionsBottomSheet). -->
      <app-options-bottom-sheet
        [open]="showOptionsSheet()"
        title="Options des muscles"
        [actions]="listSheetActions"
        (actionSelected)="onListSheetAction($event)"
        (dismissRequest)="showOptionsSheet.set(false)"
      />

      <!-- « Tout effacer » : confirmation destructive (Android l'a désactivé — version web sécurisée). -->
      <app-confirmation-dialog
        [open]="showClearAll()"
        title="Tout effacer ?"
        [message]="clearAllMsg()"
        confirmButtonText="Tout effacer"
        dismissButtonText="Annuler"
        (confirm)="confirmClearAll()"
        (dismiss)="showClearAll.set(false)"
      />

      <!-- Ajout — miroir AddMuscleDialog Android : nom + zone (texte libre), validation « Nom requis ». -->
      <app-form-dialog
        [open]="showForm()"
        title="Ajouter un muscle"
        confirmText="Ajouter"
        [confirmEnabled]="formName().trim().length > 0"
        [disabledReason]="formName().trim().length === 0 ? 'Nom requis' : ''"
        (confirm)="submitForm()"
        (dismiss)="closeForm()"
      >
        <app-custom-text-field
          label="Nom"
          placeholder="Ex. Pectoraux"
          [value]="formName()"
          (valueChange)="formName.set($event)"
        />
        <app-custom-text-field
          label="Zone"
          placeholder="Ex. Chest"
          [value]="formZone()"
          (valueChange)="formZone.set($event)"
        />
      </app-form-dialog>
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; le corps prend la gouttière (--page-gutter). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* Master-detail 2 colonnes (même pattern que exercises-page / planning-page). */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      /* Rapport de largeur 2/5 · 3/5 (comme la page Exercices). */
      .split__list {
        flex: 2 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding-bottom: var(--space-3);
      }
      .split__detail {
        flex: 3 1 0;
        min-width: 0;
        /* Le détail embarqué garde son .page__body : on annule ses gouttières (pattern calendar-goals). */
        --page-gutter: 0px;
        --page-gutter-top: 0px;
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
        .split__list {
          flex: none;
          width: 100%;
        }
        .split__detail {
          width: 100%;
        }
      }
      /* Ligne unique : select Zone + recherche (flexibles) + boutons centrés verticalement. */
      .toolbar {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .toolbar__zone {
        flex: 1;
        min-width: 0;
      }
      .toolbar__search {
        flex: 1.4;
        min-width: 0;
      }
      .toolbar__buttons {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      /* Menu de tri (mêmes styles que ListSearchHeader). */
      .toolbar__sort {
        position: relative;
      }
      .toolbar__backdrop {
        position: fixed;
        inset: 0;
        z-index: 10;
      }
      .toolbar__menu {
        position: absolute;
        top: calc(100% + 4px);
        right: 0;
        z-index: 11;
        min-width: 160px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        padding: var(--space-1);
        box-sizing: border-box;
      }
      .toolbar__menu-item {
        display: block;
        width: 100%;
        text-align: left;
        background: transparent;
        border: none;
        border-radius: var(--radius-md);
        padding: 10px var(--space-3);
        cursor: pointer;
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 14px;
        appearance: none;
        -webkit-appearance: none;
      }
      .toolbar__menu-item:hover {
        background: color-mix(in srgb, var(--app-text-primary) 6%, transparent);
      }
      /* Row en 3 colonnes égales 1/3 · 1/3 · 1/3 (nom · chips · boutons, miroir Exercices) : le nom
         porte flex 1 via nameWeight, la zone trailing prend 2/3 et se partage 1:1 entre chips et
         boutons → colonnes alignées verticalement d'une row à l'autre. */
      span[appEntityRowTrailing] {
        flex: 2 1 0;
        min-width: 0;
      }
      .row__tags {
        flex: 1;
        min-width: 0;
        display: flex;
        /* Alignées à gauche de leur colonne : toutes les chips démarrent sur la même verticale. */
        justify-content: flex-start;
        align-items: center;
        gap: var(--space-3);
        white-space: nowrap;
      }
      .row__btns {
        flex: 1;
        min-width: 0;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: var(--space-3);
      }
      /* Chips au style du badge catégorie du catalogue, un cran plus grandes (miroir Exercices). */
      .row__tag {
        font-size: 11px;
        font-weight: var(--font-weight-medium);
        line-height: 1;
        padding: 4px 9px;
        border-radius: 999px;
        color: var(--tag-c);
        color: oklch(from var(--tag-c) calc(l + 0.1) calc(c * 1.25) h);
        background: color-mix(in srgb, var(--tag-c) 20%, transparent);
      }
      /* Rows à plat dans le cadre (style catalogue d'aliments) : filet INSET secondBlue entre rows. */
      app-list-frame app-entity-list-row:not(:last-child) ::ng-deep .elr {
        position: relative;
      }
      app-list-frame app-entity-list-row:not(:last-child) ::ng-deep .elr::after {
        content: '';
        position: absolute;
        left: var(--space-3);
        right: var(--space-2);
        bottom: 0;
        height: 1px;
        background: var(--c-second-blue);
      }
      /* Placeholders de la colonne détail (aucune sélection) : mêmes codes que l'empty state des
         charts (stats-section-chart) — container bgRecessed + cadre primaryAction intérieur. */
      .ph {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .ph__box {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-3);
        box-sizing: border-box;
      }
      .ph__frame {
        height: 100%;
        box-sizing: border-box;
        display: flex;
        align-items: center;
        justify-content: center;
        text-align: center;
        white-space: pre-line;
        border: 1.5px solid var(--app-primary-action);
        border-radius: var(--radius-md);
        color: var(--app-primary-action);
        font-size: var(--font-size-body);
        padding: var(--space-2) var(--space-3);
      }
      .ph__box--main {
        height: 200px;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .ph__box--main .ph__frame {
        flex: 1;
        height: auto;
        min-height: 0;
      }
      .ph__box--chart {
        height: 240px;
      }
      .ph__box--exos {
        height: 132px;
      }
    `,
  ],
})
export class MusclesPage {
  private readonly repo = inject(MuscleRepository);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly snackbar = inject(SnackbarService);

  protected readonly muscles = this.repo.muscles;
  protected readonly search = signal('');
  protected readonly sortMode = signal<'AZ' | 'ZA'>('AZ');
  protected readonly sortOpen = signal(false);
  protected readonly zoneFilter = signal('Toutes');
  protected readonly syncing = signal(false);

  /** Sélection master-detail : posée uniquement par la flèche des rows. */
  protected readonly selectedUuid = signal<string | null>(null);
  protected readonly showOptionsSheet = signal(false);
  protected readonly showClearAll = signal(false);
  protected readonly showForm = signal(false);
  protected readonly formName = signal('');
  protected readonly formZone = signal('');

  protected readonly sortOptions = ['Nom (A-Z)', 'Nom (Z-A)'];
  /** Miroir MuscleListOptionsBottomSheet.kt : ajout d'exemple (bleu) · export (selectedFill) · clear (rouge). */
  protected readonly listSheetActions: SheetAction[] = [
    { label: "Ajouter des muscles d'exemple", icon: 'add', color: 'var(--c-blue-medium)' },
    { label: 'Exporter la liste', icon: 'share', color: 'var(--app-selected-fill)' },
    { label: 'Tout effacer', icon: 'delete_forever', color: 'var(--c-red-medium)' },
  ];
  protected readonly allSynced = computed(() => this.muscles().every((m) => m.synced));

  /** Sélection effective : invalide (supprimé / introuvable) → placeholders à droite. */
  protected readonly effectiveSelected = computed(() => {
    const u = this.selectedUuid();
    return u && this.muscles().some((m) => m.uuid === u) ? u : null;
  });

  /** « Toutes » + zones canoniques présentes, dans l'ordre Zones.ALL (miroir MuscleListScreen). */
  protected readonly zoneOptions = computed(() => {
    const present = new Set(this.muscles().map((m) => m.zone?.trim()).filter((z): z is string => !!z));
    return ['Toutes', ...ZONES_ALL.filter((z) => present.has(z))];
  });

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const zone = this.zoneFilter();
    const list = this.muscles().filter((m) => {
      if (q && !m.name.toLowerCase().includes(q)) return false;
      if (zone !== 'Toutes' && m.zone?.trim() !== zone) return false;
      return true;
    });
    const dir = this.sortMode() === 'ZA' ? -1 : 1;
    return [...list].sort((a, b) => dir * a.name.localeCompare(b.name));
  });
  /** Couleurs par zone (miroir stats-page / exercises-page). */
  protected readonly zoneColors = computed<Record<string, string>>(() => ({
    Chest: resolveCssColor('var(--app-primary-action)'),
    Back: resolveCssColor('var(--c-orange-medium)'),
    Shoulders: resolveCssColor('var(--app-accent-text)'),
    Arms: resolveCssColor('var(--c-red-medium)'),
    Legs: resolveCssColor('var(--c-medium-green)'),
    Core: resolveCssColor('var(--c-yellow-medium)'),
    Other: resolveCssColor('var(--c-medium-purple)'),
  }));

  /** Nuance par groupe (port stats-page, spread 0.55) : chaque groupe = une nuance de sa zone. */
  protected readonly groupColorMap = computed(() => {
    const zoneColors = this.zoneColors();
    const byZone = new Map<string, string[]>();
    for (const m of this.muscles()) {
      if (!m.muscleGroup || !m.zone) continue;
      const arr = byZone.get(m.zone) ?? [];
      if (!arr.includes(m.muscleGroup)) arr.push(m.muscleGroup);
      byZone.set(m.zone, arr);
    }
    const out = new Map<string, string>();
    for (const [zone, groups] of byZone) {
      groups.sort();
      const shades = paletteForZone(zoneColors[zone] ?? '#888888', groups.length, 0.55);
      groups.forEach((g, i) => out.set(g, shades[i]));
    }
    return out;
  });
  protected readonly clearAllMsg = computed(
    () =>
      `Supprimer les ${this.muscles().length} muscle(s) ? Les suppressions seront poussées au serveur — action irréversible.`,
  );

  constructor() {
    if (this.auth.isAuthenticated() && !this.auth.currentUser()) {
      this.auth.loadMe().subscribe({ error: () => undefined });
    }
    this.refresh();
  }

  protected chooseSort(label: string): void {
    this.sortMode.set(label.includes('Z-A') ? 'ZA' : 'AZ');
    this.sortOpen.set(false);
  }

  protected toggleFav(m: LocalMuscle): void {
    void this.repo.update(m.uuid, { isFavorite: !m.isFavorite });
  }

  /** Actions du ⋮ (miroir MuscleListScreen.kt : add → dialog d'ajout, export, clear all). */
  protected onListSheetAction(label: string): void {
    this.showOptionsSheet.set(false);
    if (label === "Ajouter des muscles d'exemple") this.openAdd();
    else if (label === 'Exporter la liste') this.exportList();
    else if (label === 'Tout effacer') this.showClearAll.set(true);
  }

  /** Export JSON de la liste (Android : TODO — implémentation web simple par téléchargement). */
  protected exportList(): void {
    const payload = this.muscles().map(({ synced, pendingDeletion, ...wire }) => wire);
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'muscles.json';
    a.click();
    URL.revokeObjectURL(url);
    this.snackbar.info(`Liste exportée (${payload.length} muscles — muscles.json).`);
  }

  protected confirmClearAll(): void {
    this.showClearAll.set(false);
    void this.repo.removeAll();
    this.snackbar.success('Tous les muscles ont été supprimés.');
  }

  protected openAdd(): void {
    this.formName.set('');
    this.formZone.set('');
    this.showForm.set(true);
  }

  protected closeForm(): void {
    this.showForm.set(false);
  }

  protected async submitForm(): Promise<void> {
    const name = this.formName().trim();
    if (!name) return;
    const zone = this.formZone().trim() || null;
    await this.repo.create({ name, zone });
    this.showForm.set(false);
  }

  protected refresh(): void {
    this.syncing.set(true);
    void this.sync.syncAll().finally(() => this.syncing.set(false));
  }
}
