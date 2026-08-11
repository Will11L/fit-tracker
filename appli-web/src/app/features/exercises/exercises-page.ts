import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalExercise } from '@core/models/exercise.model';
import { LocalMuscle } from '@core/models/muscle.model';
import { LocalEquipment } from '@core/models/equipment.model';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { AppDb } from '@core/sync/dexie-db';
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
import { HorizontalNumberPicker } from '@designsystem/common_components/horizontal-number-picker';
import { MultiSelectDropdown } from '@designsystem/common_components/multi-select-dropdown';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { AppIcon } from '@designsystem/icons/app-icon';
import { RevealIn } from '@designsystem/common_components/reveal-in';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { paletteForZone, resolveCssColor } from '../stats/palette-util';
import { ExerciseRepository } from './exercise.repository';
import { ExerciseDetailPage } from './exercise-detail-page';

/**
 * Écran Exercices — master-detail 2 colonnes (pattern planning-page / calendar-goals-page) :
 * **gauche** : ligne select Zone + boutons (+ ajout · tri · sync · ⋮ bottom sheet exemple-export-tout
 * effacer), recherche pleine largeur dessous, puis rows simples (nom · nuage sync · étoile favori
 * toggle · flèche → seul déclencheur de sélection, fond primaryAction quand sélectionné) + FormDialog
 * d'ajout (miroir AddExerciseDialog Android) ; **droite** : exercise-detail-page en mode embedded
 * (action bar, cards détail, stats, dernières séances — l'édition/suppression vit là). Sans sélection :
 * structure complète de la page détail avec placeholders skeleton sobres. Deep link
 * /exercise/:uuid → page combinée avec présélection ; /exercises?q= pré-remplit la recherche.
 * Empilement vertical < 900px. Données offline-first via Dexie liveQuery.
 */
@Component({
  selector: 'app-exercises-page',
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
    HorizontalNumberPicker,
    MultiSelectDropdown,
    OptionsBottomSheet,
    AppIcon,
    RevealIn,
    ExerciseDetailPage,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Mes exercices" />

      <div class="page__body">
        <div class="split">
          <!-- Gauche : recherche/filtre + rows simples (nom · sync · favori · flèche de sélection). -->
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
                placeholderText="Rechercher un exercice…"
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

            <app-titled-divider title="Exercices" />

            @if (filtered().length === 0) {
              <app-empty-list-row text="Aucun exercice — ajoute-en un (bouton ⋮) ou synchronise." />
            } @else {
              <!-- Cadre thirdBlue façon catalogue d'aliments : rows à plat, filets inset entre elles. -->
              <app-list-frame>
              @for (e of filtered(); track e.uuid) {
                <app-entity-list-row
                  [name]="e.name"
                  [nameMaxLines]="1"
                  [nameWeight]="1"
                  backgroundColor="transparent"
                  [contentEndPadding]="6"
                >
                  <span trailing appEntityRowTrailing>
                    <!-- Colonne chips (2/7) TOUJOURS rendue (alignement vertical inter-rows),
                         groupe (nuance stats) + zone (couleur de zone) du muscle dominant. -->
                    <span class="row__tags">
                      @if (tagsByExercise().get(e.uuid); as t) {
                        @if (t.group) {
                          <span class="row__tag" [style.--tag-c]="t.groupColor">{{ t.group }}</span>
                        }
                        <span class="row__tag" [style.--tag-c]="t.zoneColor">{{ t.zone }}</span>
                      }
                    </span>
                    <!-- Colonne boutons (2/7). -->
                    <span class="row__btns">
                      <app-icon
                        [name]="e.synced ? 'cloud_done' : 'cloud_off'"
                        [size]="22"
                        [color]="e.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                      />
                      <app-action-icon-button
                        [icon]="e.isFavorite ? 'star' : 'star_border'"
                        [backgroundColor]="e.isFavorite ? 'var(--c-orange-medium)' : 'var(--app-bg-button)'"
                        [tint]="e.isFavorite ? 'var(--app-on-accent)' : 'var(--app-text-primary)'"
                        (clicked)="toggleFav(e)"
                      />
                      <!-- Seule la flèche sélectionne (fond primaryAction si sélectionné, comme Planning). -->
                      <app-action-icon-button
                        icon="arrow_right_alt"
                        tint="var(--app-text-primary)"
                        [backgroundColor]="e.uuid === effectiveSelected() ? 'var(--app-primary-action)' : 'var(--c-blue-medium)'"
                        (clicked)="selectedUuid.set(e.uuid)"
                      />
                    </span>
                  </span>
                </app-entity-list-row>
              }
              </app-list-frame>
            }
          </div>

          <!-- Droite : détail de l'exo sélectionné (exercise-detail-page en mode embedded).
               Entre en slide-down + fade ; re-animée (fondu seul) au changement d'exercice. -->
          <div class="split__detail" [appRevealIn]="effectiveSelected()">
            @if (effectiveSelected(); as uuid) {
              <app-exercise-detail-page [embedded]="true" [uuid]="uuid" />
            } @else {
              <!-- Aucune sélection : structure complète de la page détail, chaque section en placeholder
                   au style "empty state de chart" (container bgRecessed + cadre primaryAction intérieur). -->
              <div class="ph">
                <!-- Cadre unique (titre + boutons + 3 sections détail) — miroir du maincard de la page détail. -->
                <div class="ph__box ph__box--main">
                  <app-titled-divider title="Aucun exercice sélectionné" />
                  <div class="ph__frame">Boutons · Détails</div>
                </div>

                <!-- Titres désormais DANS leur cadre (miroir des maincards de la page détail). -->
                <div class="ph__box ph__box--chart"><div class="ph__frame">Stats</div></div>
                <div class="ph__box ph__box--sessions"><div class="ph__frame">Dernières séances</div></div>
              </div>
            }
          </div>
        </div>
      </div>

      <!-- ⋮ options de la liste (miroir ExerciseListOptionsBottomSheet). -->
      <app-options-bottom-sheet
        [open]="showOptionsSheet()"
        title="Options des exercices"
        [actions]="listSheetActions"
        (actionSelected)="onListSheetAction($event)"
        (dismissRequest)="showOptionsSheet.set(false)"
      />

      <!-- « Tout effacer » : confirmation destructive (divergence assumée vs Android qui efface sans confirmer). -->
      <app-confirmation-dialog
        [open]="showClearAll()"
        title="Tout effacer ?"
        [message]="clearAllMsg()"
        confirmButtonText="Tout effacer"
        dismissButtonText="Annuler"
        (confirm)="confirmClearAll()"
        (dismiss)="showClearAll.set(false)"
      />

      <!-- Ajout — miroir AddExerciseDialog Android : nom, muscles, reps 1-100, séries 1-10, équipement,
           validations avec raison. L'édition/suppression vit dans la colonne détail (⋮ + crayons). -->
      <app-form-dialog
        [open]="showForm()"
        title="Ajouter un exercice"
        confirmText="Ajouter"
        [confirmEnabled]="canSubmitForm()"
        [disabledReason]="formDisabledReason()"
        (confirm)="submitForm()"
        (dismiss)="closeForm()"
      >
        <app-custom-text-field
          label="Nom"
          placeholder="Ex. Développé couché"
          [value]="formName()"
          (valueChange)="formName.set($event)"
        />
        <app-multi-select-dropdown
          label="Sélectionner des muscles"
          [options]="allMuscleNames()"
          [selectedItems]="formMuscleNames()"
          (selectionChange)="formMuscleNames.set($event)"
        />
        <div class="form-reps">
          <app-horizontal-number-picker label="Reps min" [min]="1" [max]="100" [selected]="formRepsMin()" (selectedChange)="formRepsMin.set($event)" />
          <app-horizontal-number-picker label="Reps max" [min]="1" [max]="100" [selected]="formRepsMax()" (selectedChange)="formRepsMax.set($event)" />
        </div>
        <app-horizontal-number-picker label="Séries" [min]="1" [max]="10" [selected]="formSets()" (selectedChange)="formSets.set($event)" />
        <app-multi-select-dropdown
          label="Sélectionner l'équipement"
          [options]="allEquipmentNames()"
          [selectedItems]="formEquipmentNames()"
          (selectionChange)="formEquipmentNames.set($event)"
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
      /* Master-detail 2 colonnes (même pattern que planning-page / calendar-goals-page). */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      /* Rapport de largeur 2/5 · 3/5 (demande user). */
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
      /* Row en 3 colonnes égales 1/3 · 1/3 · 1/3 (nom · chips · boutons, demande user) : le nom
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
      /* Chips au style du badge catégorie du catalogue d'aliments (fond teinté 20 %, texte rehaussé). */
      .row__tag {
        /* Un cran plus grand que le badge catégorie du catalogue (10px / 3×7, demande user). */
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
        height: 240px;
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
      .ph__box--sessions {
        height: 132px;
      }
      .form-reps {
        display: flex;
        gap: var(--space-4);
      }
      .form-reps > * {
        flex: 1;
        min-width: 0;
      }
    `,
  ],
})
export class ExercisesPage {
  private readonly repo = inject(ExerciseRepository);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly db = inject(AppDb);
  private readonly snackbar = inject(SnackbarService);

  protected readonly exercises = this.repo.exercises;
  // Joins read-only pour afficher muscles/équipement par exercice + filtre par zone (≈ ExerciseCard).
  private readonly muscles = toSignal(from(liveQuery(() => this.db.muscles.toArray())), {
    initialValue: [] as LocalMuscle[],
  });
  private readonly equipments = toSignal(from(liveQuery(() => this.db.equipments.toArray())), {
    initialValue: [] as LocalEquipment[],
  });
  private readonly exerciseMuscles = toSignal(from(liveQuery(() => this.db.exercise_muscles.toArray())), {
    initialValue: [] as LocalExerciseMuscle[],
  });

  protected readonly search = signal('');
  protected readonly sortMode = signal<'AZ' | 'ZA'>('AZ');
  protected readonly sortOpen = signal(false);
  protected readonly zoneFilter = signal('Toutes');
  protected readonly syncing = signal(false);

  /** Sélection master-detail : posée uniquement par la flèche des rows (+ deep link /exercise/:uuid). */
  protected readonly selectedUuid = signal<string | null>(null);
  protected readonly showOptionsSheet = signal(false);
  protected readonly showClearAll = signal(false);
  protected readonly showForm = signal(false);
  protected readonly formName = signal('');
  protected readonly formSets = signal(3);
  protected readonly formRepsMin = signal(8);
  protected readonly formRepsMax = signal(12);
  protected readonly formMuscleNames = signal<string[]>([]);
  protected readonly formEquipmentNames = signal<string[]>([]);

  protected readonly sortOptions = ['Nom (A-Z)', 'Nom (Z-A)'];
  /** Miroir ExerciseListOptionsBottomSheet.kt : ajout d'exemple (bleu) · export (selectedFill) · clear (rouge). */
  protected readonly listSheetActions: SheetAction[] = [
    { label: "Ajouter des exercices d'exemple", icon: 'add', color: 'var(--c-blue-medium)' },
    { label: 'Exporter la liste', icon: 'share', color: 'var(--app-selected-fill)' },
    { label: 'Tout effacer', icon: 'delete_forever', color: 'var(--c-red-medium)' },
  ];
  protected readonly allSynced = computed(() => this.exercises().every((e) => e.synced));

  /** Sélection effective : invalide (supprimé / introuvable) → état vide à droite. */
  protected readonly effectiveSelected = computed(() => {
    const u = this.selectedUuid();
    return u && this.exercises().some((e) => e.uuid === u) ? u : null;
  });

  // muscleUUID -> Muscle, puis exerciseUUID -> Muscle[] (filtre par zone).
  private readonly musclesByExercise = computed(() => {
    const byUuid = new Map(this.muscles().map((m) => [m.uuid, m]));
    const map = new Map<string, LocalMuscle[]>();
    for (const em of this.exerciseMuscles()) {
      const m = byUuid.get(em.muscleUUID);
      if (!m) continue;
      const list = map.get(em.exerciseUUID) ?? [];
      list.push(m);
      map.set(em.exerciseUUID, list);
    }
    return map;
  });

  /** Couleurs par zone (miroir stats-page). */
  private readonly zoneColors = computed<Record<string, string>>(() => ({
    Chest: resolveCssColor('var(--app-primary-action)'),
    Back: resolveCssColor('var(--c-orange-medium)'),
    Shoulders: resolveCssColor('var(--app-accent-text)'),
    Arms: resolveCssColor('var(--c-red-medium)'),
    Legs: resolveCssColor('var(--c-medium-green)'),
    Core: resolveCssColor('var(--c-yellow-medium)'),
    Other: resolveCssColor('var(--c-medium-purple)'),
  }));

  /** Nuance par groupe (port stats-page, spread 0.55) : chaque groupe = une nuance de sa zone. */
  private readonly groupColorMap = computed(() => {
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

  /** Étiquettes groupe + zone du muscle DOMINANT (coef max) de chaque exercice, colorées. */
  protected readonly tagsByExercise = computed(() => {
    const byUuid = new Map(this.muscles().map((m) => [m.uuid, m]));
    const groups = this.groupColorMap();
    const zoneColors = this.zoneColors();
    const top = new Map<string, { coef: number; m: LocalMuscle }>();
    for (const em of this.exerciseMuscles()) {
      const m = byUuid.get(em.muscleUUID);
      if (!m) continue;
      const cur = top.get(em.exerciseUUID);
      if (!cur || em.coefficient > cur.coef) top.set(em.exerciseUUID, { coef: em.coefficient, m });
    }
    const out = new Map<string, { group: string; groupColor: string; zone: string; zoneColor: string }>();
    for (const [exUuid, { m }] of top) {
      if (!m.zone) continue;
      out.set(exUuid, {
        group: m.muscleGroup ?? '',
        groupColor: (m.muscleGroup ? groups.get(m.muscleGroup) : undefined) ?? zoneColors[m.zone] ?? '',
        zone: m.zone,
        zoneColor: zoneColors[m.zone] ?? '',
      });
    }
    return out;
  });

  protected readonly allMuscleNames = computed(() =>
    this.muscles()
      .filter((m) => !m.pendingDeletion)
      .map((m) => m.name)
      .sort((a, b) => a.localeCompare(b)),
  );
  protected readonly allEquipmentNames = computed(() =>
    this.equipments()
      .filter((eq) => !eq.pendingDeletion)
      .map((eq) => eq.name)
      .sort((a, b) => a.localeCompare(b)),
  );

  protected readonly zoneOptions = computed(() => {
    const zones = [...new Set(this.muscles().map((m) => m.zone).filter((z): z is string => !!z))].sort();
    return ['Toutes', ...zones];
  });

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const zone = this.zoneFilter();
    const byEx = this.musclesByExercise();
    const list = this.exercises().filter((e) => {
      if (q && !e.name.toLowerCase().includes(q)) return false;
      if (zone !== 'Toutes' && !(byEx.get(e.uuid) ?? []).some((m) => m.zone === zone)) return false;
      return true;
    });
    const dir = this.sortMode() === 'ZA' ? -1 : 1;
    return [...list].sort((a, b) => dir * a.name.localeCompare(b.name));
  });
  /** Validations du AddExerciseDialog Android : nom + ≥1 muscle + ≥1 équipement. */
  protected readonly canSubmitForm = computed(
    () =>
      this.formName().trim().length > 0 &&
      this.formMuscleNames().length > 0 &&
      this.formEquipmentNames().length > 0,
  );
  protected readonly formDisabledReason = computed(() => {
    if (this.formName().trim().length === 0) return 'Nom requis';
    if (this.formMuscleNames().length === 0) return 'Sélectionne au moins un muscle';
    if (this.formEquipmentNames().length === 0) return "Sélectionne au moins un équipement";
    return '';
  });
  protected readonly clearAllMsg = computed(
    () =>
      `Supprimer les ${this.exercises().length} exercice(s) ? Les suppressions seront poussées au serveur — action irréversible.`,
  );

  constructor() {
    // « Voir les détails de l'exercice » depuis le Planning : nom pré-rempli dans la recherche.
    const q = inject(ActivatedRoute).snapshot.queryParamMap.get('q');
    if (q) this.search.set(q);
    // Deep link /exercise/:uuid : exercice présélectionné dans la colonne détail.
    const preselect = inject(ActivatedRoute).snapshot.paramMap.get('uuid');
    if (preselect) this.selectedUuid.set(preselect);
    if (this.auth.isAuthenticated() && !this.auth.currentUser()) {
      this.auth.loadMe().subscribe({ error: () => undefined });
    }
    this.refresh();
  }

  protected chooseSort(label: string): void {
    this.sortMode.set(label.includes('Z-A') ? 'ZA' : 'AZ');
    this.sortOpen.set(false);
  }

  protected toggleFav(e: LocalExercise): void {
    void this.repo.update(e.uuid, { isFavorite: !e.isFavorite });
  }

  /** Actions du ⋮ (miroir ExerciseListScreen.kt : add → dialog d'ajout, export, clear all). */
  protected onListSheetAction(label: string): void {
    this.showOptionsSheet.set(false);
    if (label === "Ajouter des exercices d'exemple") this.openAdd();
    else if (label === 'Exporter la liste') this.exportList();
    else if (label === 'Tout effacer') this.showClearAll.set(true);
  }

  /** Export JSON de la liste (Android : TODO — implémentation web simple par téléchargement). */
  protected exportList(): void {
    const payload = this.exercises().map(({ synced, pendingDeletion, ...wire }) => wire);
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'exercices.json';
    a.click();
    URL.revokeObjectURL(url);
    this.snackbar.info(`Liste exportée (${payload.length} exercices — exercices.json).`);
  }

  protected confirmClearAll(): void {
    this.showClearAll.set(false);
    void this.repo.removeAll();
    this.snackbar.success('Tous les exercices ont été supprimés.');
  }

  protected openAdd(): void {
    this.formName.set('');
    this.formSets.set(3);
    this.formRepsMin.set(8);
    this.formRepsMax.set(12);
    this.formMuscleNames.set([]);
    this.formEquipmentNames.set([]);
    this.showForm.set(true);
  }

  protected closeForm(): void {
    this.showForm.set(false);
  }

  protected async submitForm(): Promise<void> {
    const name = this.formName().trim();
    if (!name) return;
    const min = Math.min(this.formRepsMin(), this.formRepsMax());
    const max = Math.max(this.formRepsMin(), this.formRepsMax());
    const recommendedReps = `${min}-${max}`;
    const recommendedSets = this.formSets();

    // Miroir AddExerciseDialog Android : doublon (insensible à la casse) → snackbar info, pas de création.
    if (this.exercises().some((e) => e.name.toLowerCase() === name.toLowerCase())) {
      this.showForm.set(false);
      this.snackbar.info(`L'exercice « ${name} » existe déjà`);
      return;
    }

    // Création + jonctions muscles (coefficient 1.0) / équipement, synced=false + push (≈ addExerciseManually).
    const uuid = await this.repo.create({ name, recommendedSets, recommendedReps, restTimeSeconds: 60 });
    const muscleUuids = this.namesToUuids(this.formMuscleNames(), this.muscles());
    const equipmentUuids = this.namesToUuids(this.formEquipmentNames(), this.equipments());
    await this.repo.setMuscles(uuid, muscleUuids);
    await this.repo.setEquipments(uuid, equipmentUuids);
    this.showForm.set(false);
  }

  private namesToUuids(names: string[], entities: { uuid: string; name: string; pendingDeletion: boolean }[]): string[] {
    const byName = new Map(entities.filter((e) => !e.pendingDeletion).map((e) => [e.name, e.uuid]));
    return names.map((n) => byName.get(n)).filter((u): u is string => !!u);
  }

  protected refresh(): void {
    this.syncing.set(true);
    void this.sync.syncAll().finally(() => this.syncing.set(false));
  }
}
