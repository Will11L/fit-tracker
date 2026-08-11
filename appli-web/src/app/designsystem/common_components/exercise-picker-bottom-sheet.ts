import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { FilterDropdown } from '@designsystem/common_components/filter-dropdown';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { TitledDivider } from '@designsystem/common_components/titled-divider';

/** Un exercice sélectionnable (sous-ensemble UI de Exercise + ses équipements). */
export interface ExercisePickerItem {
  uuid: string;
  name: string;
  equipments: string[];
  /** Étiquettes muscle/groupe/zone pour le filtre muscle (vide si non fourni). */
  muscleTags?: string[];
}

/**
 * Bottom sheet de sélection d'exercice — miroir de ExercisePickerBottomSheet.kt : filtre
 * équipement (FilterDropdown) + recherche + liste (nom + bouton « voir » + bouton « ajouter »).
 * Réutilise AppBottomSheet. Le filtrage par équipement se fait sur `item.equipments`.
 */
@Component({
  selector: 'app-exercise-picker-bottom-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppBottomSheet, ActionIconButton, FilterDropdown, StyledSearchField, TitledDivider],
  template: `
    <app-bottom-sheet [open]="open()" (dismissRequest)="dismissRequest.emit()">
      <div class="epb">
        <app-titled-divider [title]="title()" />

        <div class="epb__filters">
          @if (equipmentOptions().length > 1) {
            <app-filter-dropdown
              class="epb__eq"
              label="Équipement"
              [options]="equipmentOptions()"
              [selected]="selectedEquipment()"
              (select)="selectedEquipment.set($event)"
            />
          }
          @if (muscleOptions().length > 1) {
            <app-filter-dropdown
              class="epb__eq"
              [label]="muscleLabel()"
              [options]="muscleOptions()"
              [selected]="selectedMuscle()"
              (select)="selectedMuscle.set($event)"
            />
          }
          <app-styled-search-field
            class="epb__search"
            [value]="query()"
            (valueChange)="query.set($event)"
            placeholderText="Rechercher…"
          />
        </div>

        <app-titled-divider title="Exercices" />

        <div class="epb__list">
          @for (ex of filtered(); track ex.uuid) {
            <div class="epb__item">
              <span class="epb__name">{{ ex.name }}</span>
              <div class="epb__actions">
                <app-action-icon-button icon="visibility" backgroundColor="var(--app-selected-fill)" (clicked)="viewExercise.emit(ex.uuid)" />
                <app-action-icon-button
                  icon="add"
                  backgroundColor="color-mix(in srgb, var(--app-primary-action) 75%, transparent)"
                  (clicked)="selectExercise.emit(ex.uuid)"
                />
              </div>
            </div>
          } @empty {
            <p class="epb__empty">Aucun exercice</p>
          }
        </div>
      </div>
    </app-bottom-sheet>
  `,
  styles: [
    `
      .epb {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        padding: 0 var(--space-4) var(--space-2);
      }
      .epb__filters {
        display: flex;
        align-items: flex-end;
        flex-wrap: wrap;
        gap: var(--space-2);
      }
      .epb__eq,
      .epb__search {
        flex: 1 1 120px;
      }
      /* Liste en 2 colonnes (chaque row = nom + 2 boutons, donc compacte). */
      .epb__list {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: var(--space-2);
        max-height: 40vh;
        overflow-y: auto;
      }
      .epb__item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
      }
      .epb__name {
        min-width: 0;
        color: var(--app-text-primary);
        font-size: var(--font-size-body);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .epb__actions {
        display: flex;
        gap: var(--space-2);
        flex-shrink: 0;
      }
      .epb__empty {
        grid-column: 1 / -1;
        color: var(--app-text-tertiary);
        font-size: var(--font-size-body);
        text-align: center;
        margin: var(--space-4) 0;
      }
    `,
  ],
})
export class ExercisePickerBottomSheet {
  readonly open = input(false);
  readonly title = input('');
  readonly exercises = input<ExercisePickerItem[]>([]);
  /** Options du filtre équipement ; inclure « Tous » en tête. */
  readonly equipmentOptions = input<string[]>(['Tous']);
  /** Options du filtre muscle/groupe/zone (incl. « Tous » en tête) ; vide = filtre masqué. */
  readonly muscleOptions = input<string[]>([]);
  readonly muscleLabel = input('Muscle');
  readonly selectExercise = output<string>();
  readonly viewExercise = output<string>();
  readonly dismissRequest = output<void>();

  protected readonly query = signal('');
  protected readonly selectedEquipment = signal<string | null>('Tous');
  protected readonly selectedMuscle = signal<string | null>('Tous');

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const eq = this.selectedEquipment();
    const allEq = !eq || eq === 'Tous' || eq === 'All';
    const m = this.selectedMuscle();
    const allM = !m || m === 'Tous' || m === 'Toutes' || m === 'All';
    return this.exercises().filter((ex) => {
      const matchesQuery = !q || ex.name.toLowerCase().includes(q);
      const matchesEquipment = allEq || ex.equipments.includes(eq!);
      const matchesMuscle = allM || (ex.muscleTags ?? []).includes(m!);
      return matchesQuery && matchesEquipment && matchesMuscle;
    });
  });
}
