import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalAvailableEquipment } from '@core/models/available-equipment.model';
import { LocalEquipment } from '@core/models/equipment.model';
import { LocalExerciseEquipment } from '@core/models/exercise-equipment.model';
import { LocalExercise } from '@core/models/exercise.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { FilterDropdown } from '@designsystem/common_components/filter-dropdown';
import { EntityListRow } from '@designsystem/common_components/entity-list-row';
import { EntityRowTrailing } from '@designsystem/common_components/entity-row-trailing';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ListFrame } from '@designsystem/common_components/list-frame';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { AppIcon } from '@designsystem/icons/app-icon';
import { RevealIn } from '@designsystem/common_components/reveal-in';

/** Ligne unifiée de la liste matériel : catalogue global + entrées « mon matériel » hors catalogue. */
interface EquipItem {
  /** UUID du catalogue (Equipment) si présent — requis pour lister les exercices liés. */
  uuid: string | null;
  name: string;
  /** Possédé = un AvailableEquipment du même nom existe. */
  owned: boolean;
  /** Présent dans le catalogue global (Equipment). */
  inCatalog: boolean;
  /** Synchronisé = aucun changement local en attente (catalogue + possession). */
  synced: boolean;
}

const FILTER_ALL = 'Tout';
const FILTER_MINE = 'Mon matériel';
const FILTER_NOT_MINE = 'Hors mon matériel';

/**
 * Écran Matériel — master-detail 2 colonnes (pattern exercises-page / muscles-page) :
 * **gauche** : select de filtre possession (Tout · Mon matériel · Hors mon matériel) + boutons
 * (+ ajout perso · tri · sync), recherche pleine largeur, puis rows (nom · étoile possession toggle ·
 * flèche de sélection). La liste = catalogue global (Equipment) ∪ matériel perso hors catalogue
 * (AvailableEquipment). **droite** : détail du matériel sélectionné — toggle « Mon matériel »
 * (crée/retire l'AvailableEquipment homonyme) + liste des exercices qui l'utilisent (via
 * exercise_equipments, cliquables → /exercise/:uuid). Données offline-first (Dexie liveQuery).
 */
@Component({
  selector: 'app-materiel-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    StyledSearchField,
    FilterDropdown,
    EntityListRow,
    EntityRowTrailing,
    ActionIconButton,
    ActionIconWithTextButton,
    EmptyListRow,
    ListFrame,
    FormDialog,
    CustomTextField,
    AppIcon,
    RevealIn,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Matériel" />

      <div class="page__body">
        <div class="split">
          <!-- Gauche : filtre possession + boutons + recherche + rows. -->
          <div class="split__list">
            <!-- Ligne unique : select Possession + recherche + boutons. -->
            <div class="toolbar">
              <app-filter-dropdown
                class="toolbar__filter"
                label="Possession"
                [options]="filterOptions"
                [selected]="filter()"
                (select)="filter.set($event)"
              />
              <app-styled-search-field
                class="toolbar__search"
                [value]="search()"
                (valueChange)="search.set($event)"
                placeholderText="Rechercher du matériel…"
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
              </div>
            </div>

            <app-titled-divider title="Matériel" />

            @if (filtered().length === 0) {
              <app-empty-list-row [text]="listEmptyText()" icon="fitness_center" />
            } @else {
              <!-- Cadre thirdBlue façon catalogue d'aliments : rows à plat, filets inset entre elles. -->
              <app-list-frame>
              @for (it of filtered(); track it.name) {
                <app-entity-list-row
                  [name]="it.name"
                  [nameMaxLines]="1"
                  [nameWeight]="1"
                  backgroundColor="transparent"
                  [contentEndPadding]="6"
                >
                  <span trailing appEntityRowTrailing>
                    <!-- Colonne chips (1/3) TOUJOURS rendue (alignement inter-rows) : « Perso » =
                         matériel personnel hors catalogue global. -->
                    <span class="row__tags">
                      @if (!it.inCatalog) {
                        <span class="row__tag row__tag--perso">Perso</span>
                      }
                    </span>
                    <!-- Colonne boutons (1/3). -->
                    <span class="row__btns">
                      <app-icon
                        [name]="it.synced ? 'cloud_done' : 'cloud_off'"
                        [size]="22"
                        [color]="it.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                      />
                      <app-action-icon-button
                        [icon]="it.owned ? 'star' : 'star_border'"
                        [backgroundColor]="it.owned ? 'var(--c-orange-medium)' : 'var(--app-bg-button)'"
                        [tint]="it.owned ? 'var(--app-on-accent)' : 'var(--app-text-primary)'"
                        (clicked)="toggleOwned(it)"
                      />
                      <app-action-icon-button
                        icon="arrow_right_alt"
                        tint="var(--app-text-primary)"
                        [backgroundColor]="it.name === effectiveSelected()?.name ? 'var(--app-primary-action)' : 'var(--c-blue-medium)'"
                        (clicked)="selectedName.set(it.name)"
                      />
                    </span>
                  </span>
                </app-entity-list-row>
              }
              </app-list-frame>
            }
          </div>

          <!-- Droite : détail du matériel sélectionné.
               Entre en slide-down + fade ; re-animée (fondu seul) au changement de matériel. -->
          <div class="split__detail" [appRevealIn]="effectiveSelected()?.name">
            @if (effectiveSelected(); as sel) {
              <!-- Cadre nom + bouton possession. -->
              <div class="maincard">
                <app-titled-divider [title]="sel.name" />

                <div class="detail-toggle">
                  <app-action-icon-with-text-button
                    [icon]="sel.owned ? 'star' : 'star_border'"
                    [text]="sel.owned ? 'Dans mon matériel — retirer' : 'Ajouter à mon matériel'"
                    [backgroundColor]="sel.owned ? 'var(--app-selected-fill)' : 'var(--app-primary-action)'"
                    [tint]="sel.owned ? 'var(--c-orange-medium)' : '#ffffff'"
                    (clicked)="toggleOwned(sel)"
                  />
                </div>
              </div>

              <!-- Cadre exercices liés : titre + rows à plat, séparées par un filet. -->
              <div class="maincard">
                <app-titled-divider [title]="exercisesTitle()" />
                <div class="exos">
                @if (!sel.inCatalog) {
                  <app-empty-list-row text="Matériel personnel (hors catalogue) — aucun exercice lié." />
                } @else if (exercisesUsing().length === 0) {
                  <app-empty-list-row text="Aucun exercice n'utilise ce matériel." />
                } @else {
                  @for (ex of exercisesUsing(); track ex.uuid) {
                    <app-entity-list-row
                      [name]="ex.name"
                      [nameMaxLines]="1"
                      backgroundColor="transparent"
                      [contentEndPadding]="6"
                    >
                      <span trailing appEntityRowTrailing>
                        <app-action-icon-button
                          icon="arrow_right_alt"
                          tint="var(--app-text-primary)"
                          backgroundColor="var(--c-blue-medium)"
                          (clicked)="goToExercise(ex.uuid)"
                        />
                      </span>
                    </app-entity-list-row>
                  }
                }
                </div>
              </div>
            } @else {
              <div class="ph">
                <!-- Cadre unique (titre + bouton possession) — miroir du maincard du détail. -->
                <div class="ph__box ph__box--main">
                  <app-titled-divider title="Aucun matériel sélectionné" />
                  <div class="ph__frame">Mon matériel</div>
                </div>
                <div class="ph__box ph__box--list"><div class="ph__frame">Exercices qui l'utilisent</div></div>
              </div>
            }
          </div>
        </div>
      </div>

      <app-form-dialog
        [open]="showForm()"
        title="Nouveau matériel"
        confirmText="Ajouter"
        [confirmEnabled]="formName().trim().length > 0"
        disabledReason="Le nom ne peut pas être vide"
        (confirm)="submitForm()"
        (dismiss)="showForm.set(false)"
      >
        <app-custom-text-field
          label="Nom"
          placeholder="ex. Haltères 10 kg"
          [value]="formName()"
          (valueChange)="formName.set($event)"
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
      }
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__list,
      .split__detail {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding-bottom: var(--space-3);
      }
      /* Rapport de largeur 2/5 · 3/5 (comme les pages Exercices / Muscles). */
      .split__list {
        flex: 2 1 0;
      }
      .split__detail {
        flex: 3 1 0;
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
      }
      /* Ligne unique : select + recherche (flexibles) + boutons centrés verticalement. */
      .toolbar {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .toolbar__filter {
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
      /* Row en 3 colonnes égales (nom · chips · boutons, miroir Exercices/Muscles), scopée à la
         liste de gauche (les rows du détail gardent le layout par défaut nom + flèche). */
      app-list-frame span[appEntityRowTrailing] {
        flex: 2 1 0;
        min-width: 0;
      }
      .row__tags {
        flex: 1;
        min-width: 0;
        display: flex;
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
      /* Chip au style du badge catégorie du catalogue (miroir Exercices/Muscles). */
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
      .row__tag--perso {
        --tag-c: var(--c-medium-purple);
      }
      /* Rows à plat dans un cadre : filet INSET secondBlue entre rows (liste + exercices du détail). */
      app-list-frame app-entity-list-row:not(:last-child) ::ng-deep .elr,
      .exos app-entity-list-row:not(:last-child) ::ng-deep .elr {
        position: relative;
      }
      app-list-frame app-entity-list-row:not(:last-child) ::ng-deep .elr::after,
      .exos app-entity-list-row:not(:last-child) ::ng-deep .elr::after {
        content: '';
        position: absolute;
        left: var(--space-3);
        right: var(--space-2);
        bottom: 0;
        height: 1px;
        background: var(--c-second-blue);
      }
      /* Cadres du détail (padding canonique 16px). */
      .maincard {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 16px;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .exos {
        display: flex;
        flex-direction: column;
      }
      .detail-toggle {
        display: flex;
      }
      .detail-toggle > * {
        flex: 1;
      }
      /* Placeholders de la colonne détail (aucune sélection). */
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
        border: 1.5px solid var(--app-primary-action);
        border-radius: var(--radius-md);
        color: var(--app-primary-action);
        font-size: var(--font-size-body);
        padding: var(--space-2) var(--space-3);
      }
      .ph__box--main {
        height: 140px;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .ph__box--main .ph__frame {
        flex: 1;
        height: auto;
        min-height: 0;
      }
      .ph__box--list {
        height: 160px;
      }
    `,
  ],
})
export class MaterielPage {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private readonly equipments = toSignal(
    from(liveQuery(() => this.db.equipments.filter((e) => !e.pendingDeletion).toArray())),
    { initialValue: [] as LocalEquipment[] },
  );
  private readonly available = toSignal(
    from(liveQuery(() => this.db.available_equipments.filter((e) => !e.pendingDeletion).toArray())),
    { initialValue: [] as LocalAvailableEquipment[] },
  );
  private readonly exerciseEquipments = toSignal(
    from(liveQuery(() => this.db.exercise_equipment.toArray())),
    { initialValue: [] as LocalExerciseEquipment[] },
  );
  private readonly exercises = toSignal(
    from(liveQuery(() => this.db.exercises.filter((e) => !e.pendingDeletion).toArray())),
    { initialValue: [] as LocalExercise[] },
  );

  protected readonly search = signal('');
  protected readonly sortMode = signal<'AZ' | 'ZA'>('AZ');
  protected readonly sortOpen = signal(false);
  protected readonly filter = signal(FILTER_ALL);
  protected readonly selectedName = signal<string | null>(null);
  protected readonly showForm = signal(false);
  protected readonly formName = signal('');

  protected readonly sortOptions = ['Nom (A-Z)', 'Nom (Z-A)'];
  protected readonly filterOptions = [FILTER_ALL, FILTER_MINE, FILTER_NOT_MINE];
  protected readonly allSynced = computed(
    () => this.equipments().every((e) => e.synced) && this.available().every((e) => e.synced),
  );

  /** Liste unifiée : catalogue (Equipment) + matériel perso (AvailableEquipment) hors catalogue. */
  protected readonly items = computed<EquipItem[]>(() => {
    const availByName = new Map(this.available().map((a) => [a.name.toLowerCase(), a]));
    const catalogNames = new Set(this.equipments().map((e) => e.name.toLowerCase()));
    const list: EquipItem[] = this.equipments().map((e) => {
      const av = availByName.get(e.name.toLowerCase());
      return {
        uuid: e.uuid,
        name: e.name,
        owned: !!av,
        inCatalog: true,
        // Non synchronisé si le catalogue OU la possession a un changement local en attente.
        synced: e.synced && (!av || av.synced),
      };
    });
    for (const a of this.available()) {
      if (!catalogNames.has(a.name.toLowerCase())) {
        list.push({ uuid: null, name: a.name, owned: true, inCatalog: false, synced: a.synced });
      }
    }
    return list;
  });

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const f = this.filter();
    const list = this.items().filter((it) => {
      if (q && !it.name.toLowerCase().includes(q)) return false;
      if (f === FILTER_MINE && !it.owned) return false;
      if (f === FILTER_NOT_MINE && it.owned) return false;
      return true;
    });
    const dir = this.sortMode() === 'ZA' ? -1 : 1;
    return [...list].sort((a, b) => dir * a.name.localeCompare(b.name));
  });

  protected readonly listEmptyText = computed(() =>
    this.search().trim() || this.filter() !== FILTER_ALL
      ? 'Aucun matériel ne correspond.'
      : 'Catalogue vide — synchronise, ou ajoute du matériel perso avec +.',
  );

  /** Sélection effective : nom encore présent dans la liste, sinon état vide à droite. */
  protected readonly effectiveSelected = computed<EquipItem | null>(() => {
    const n = this.selectedName();
    return (n && this.items().find((it) => it.name === n)) || null;
  });

  protected readonly exercisesUsing = computed(() => {
    const sel = this.effectiveSelected();
    if (!sel || !sel.uuid) return [] as LocalExercise[];
    const exIds = new Set(
      this.exerciseEquipments()
        .filter((ee) => !ee.pendingDeletion && ee.equipmentUUID === sel.uuid)
        .map((ee) => ee.exerciseUUID),
    );
    return this.exercises()
      .filter((e) => exIds.has(e.uuid))
      .sort((a, b) => a.name.localeCompare(b.name));
  });
  protected readonly exercisesTitle = computed(() => {
    const sel = this.effectiveSelected();
    if (sel && !sel.inCatalog) return 'Exercices';
    return `Exercices qui l'utilisent · ${this.exercisesUsing().length}`;
  });

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

  protected openAdd(): void {
    this.formName.set('');
    this.showForm.set(true);
  }

  protected async submitForm(): Promise<void> {
    const n = this.formName().trim();
    if (!n) return;
    this.showForm.set(false);
    // Doublon « mon matériel » insensible à la casse : on sélectionne l'existant au lieu de recréer.
    if (!this.available().some((a) => a.name.toLowerCase() === n.toLowerCase())) {
      await this.db.available_equipments.put({
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        name: n,
        updatedAt: new Date().toISOString(),
        synced: false,
        pendingDeletion: false,
      });
      void this.sync.syncAll().catch(() => undefined);
    }
    this.selectedName.set(n);
  }

  /** Bascule la possession : crée l'AvailableEquipment homonyme, ou marque l'existant supprimé. */
  protected async toggleOwned(it: EquipItem): Promise<void> {
    const existing = this.available().find((a) => a.name.toLowerCase() === it.name.toLowerCase());
    if (existing) {
      await this.db.available_equipments.update(existing.uuid, {
        pendingDeletion: true,
        updatedAt: new Date().toISOString(),
      });
    } else {
      await this.db.available_equipments.put({
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        name: it.name,
        updatedAt: new Date().toISOString(),
        synced: false,
        pendingDeletion: false,
      });
    }
    void this.sync.syncAll().catch(() => undefined);
  }

  protected goToExercise(uuid: string): void {
    void this.router.navigate(['/exercise', uuid]);
  }

  protected refresh(): void {
    void this.sync.syncAll().catch(() => undefined);
  }
}
