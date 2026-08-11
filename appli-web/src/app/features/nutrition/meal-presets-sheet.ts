import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { LocalMealPreset } from '@core/models/meal-preset.model';
import { AppBottomSheet } from '@designsystem/common_components/app-bottom-sheet';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomHourPicker } from '@designsystem/common_components/custom-hour-picker';
import { MealRepository } from './meal.repository';

/**
 * Gestion des périodes habituelles du journal (`meal_presets`, D10 NUTRITION_DESIGN §3.5) —
 * renommer, réordonner (↑/↓, orderIndex resérialisé), heure indicative (affichage seulement),
 * ajouter / supprimer. Supprimer un preset ne touche pas aux repas déjà journalisés (les Meals
 * sont des rows indépendantes appariées par nom).
 */
@Component({
  selector: 'app-meal-presets-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppBottomSheet,
    TitledDivider,
    EmptyListRow,
    ActionIconButton,
    ActionIconWithTextButton,
    FormDialog,
    ConfirmationDialog,
    CustomTextField,
    CustomHourPicker,
  ],
  template: `
    <app-bottom-sheet [open]="open()" (dismissRequest)="dismissRequest.emit()">
      <div class="mps">
        <app-titled-divider title="Repas" />
        @if (presets().length === 0) {
          <app-empty-list-row text="Aucun repas — ajoute-en un." [verticalPadding]="0" />
        }
        @for (p of presets(); track p.uuid; let i = $index) {
          <div class="prow">
            <div class="prow__main">
              <span class="prow__name">{{ p.name }}</span>
              @if (p.defaultTime) {
                <span class="prow__time">{{ p.defaultTime }}</span>
              }
            </div>
            <!-- Réordonner (↑/↓) = secondaire : fond neutre + icône blanche (défauts). -->
            <app-action-icon-button
              icon="arrow_upward"
              [size]="30"
              [iconSize]="18"
              [disabled]="i === 0"
              (clicked)="move(i, -1)"
            />
            <app-action-icon-button
              icon="arrow_downward"
              [size]="30"
              [iconSize]="18"
              [disabled]="i === presets().length - 1"
              (clicked)="move(i, 1)"
            />
            <!-- Éditer = action bleue (fond blue-medium). -->
            <app-action-icon-button
              icon="edit"
              [size]="30"
              [iconSize]="18"
              backgroundColor="var(--c-blue-medium)"
              (clicked)="openEdit(p)"
            />
            <!-- Supprimer = destructif : fond rouge + icône blanche. -->
            <app-action-icon-button
              icon="delete"
              [size]="30"
              [iconSize]="18"
              backgroundColor="var(--app-btn-danger-bg)"
              tint="var(--app-btn-danger-fg)"
              (clicked)="presetToDelete.set(p)"
            />
          </div>
        }
        <app-titled-divider title="Actions" />
        <!-- Actions espacées uniformément (bords compris) : dupliquer un repas passé · ajouter un repas. -->
        <div class="mps__actions">
          <app-action-icon-with-text-button
            icon="content_copy"
            text="Dupliquer un repas passé"
            backgroundColor="var(--c-first-blue)"
            (clicked)="duplicateRequested.emit()"
          />
          <app-action-icon-with-text-button
            icon="add"
            text="Ajouter un repas"
            (clicked)="openCreate()"
          />
        </div>
      </div>
    </app-bottom-sheet>

    <!-- Création / renommage + heure indicative. -->
    <app-form-dialog
      [open]="formOpen()"
      [title]="editUuid() ? 'Modifier le repas' : 'Nouveau repas'"
      confirmText="Enregistrer"
      [confirmEnabled]="fName().trim().length > 0"
      disabledReason="Nom requis"
      (confirm)="submitForm()"
      (dismiss)="formOpen.set(false)"
    >
      <app-custom-text-field
        label="Nom"
        placeholder="Ex. Pré-training"
        [value]="fName()"
        (valueChange)="fName.set($event)"
      />
      <app-custom-hour-picker
        label="Heure indicative (facultatif)"
        [value]="fTime()"
        (valueChange)="fTime.set($event)"
      />
    </app-form-dialog>

    <app-confirmation-dialog
      [open]="presetToDelete() !== null"
      title="Supprimer le repas"
      [message]="deleteMsg()"
      confirmButtonText="Supprimer"
      dismissButtonText="Annuler"
      (confirm)="confirmDelete()"
      (dismiss)="presetToDelete.set(null)"
    />
  `,
  styles: [
    `
      .mps {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding: 0 var(--space-4) var(--space-3);
      }
      /* Boutons d'action de la sheet : espace égal aux bords et entre les deux
         (dupliquer · ajouter) — miroir de la row Actions Android. Les marges
         négatives annulent le padding horizontal de la sheet, sinon il s'ajoute
         aux espaces de bord de space-evenly (bords > centre). */
      .mps__actions {
        display: flex;
        justify-content: space-evenly;
        gap: var(--space-2);
        flex-wrap: wrap;
        margin-inline: calc(-1 * var(--space-4));
      }
      .prow {
        display: flex;
        align-items: center;
        gap: var(--space-1);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 6px var(--space-2) 6px var(--space-3);
      }
      .prow__main {
        flex: 1;
        min-width: 0;
        display: flex;
        align-items: baseline;
        gap: var(--space-2);
      }
      .prow__name {
        color: var(--app-text-primary);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
      }
      .prow__time {
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
    `,
  ],
})
export class MealPresetsSheet {
  private readonly mealRepo = inject(MealRepository);

  readonly open = input(false);
  readonly dismissRequest = output<void>();
  /** Demande d'ouverture du flux « dupliquer un repas passé » (géré par la page). */
  readonly duplicateRequested = output<void>();

  protected readonly presets = computed(() => this.mealRepo.presets());

  /** Réordonne : swap avec le voisin puis orderIndex resérialisés (0..n-1) sur les rows déplacées. */
  protected move(index: number, delta: number): void {
    const list = [...this.presets()];
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    [list[index], list[target]] = [list[target], list[index]];
    for (let i = 0; i < list.length; i++) {
      if (list[i].orderIndex !== i)
        void this.mealRepo.updatePreset(list[i].uuid, { orderIndex: i });
    }
  }

  // -------------------- Formulaire (création / renommage / heure) --------------------

  protected readonly formOpen = signal(false);
  protected readonly editUuid = signal<string | null>(null);
  protected readonly fName = signal('');
  protected readonly fTime = signal('');

  protected openCreate(): void {
    this.editUuid.set(null);
    this.fName.set('');
    this.fTime.set('');
    this.formOpen.set(true);
  }

  protected openEdit(p: LocalMealPreset): void {
    this.editUuid.set(p.uuid);
    this.fName.set(p.name);
    this.fTime.set(p.defaultTime ?? '');
    this.formOpen.set(true);
  }

  protected submitForm(): void {
    const name = this.fName().trim();
    if (!name) return;
    const defaultTime = this.fTime().trim() || null;
    this.formOpen.set(false);
    const uuid = this.editUuid();
    if (uuid) {
      void this.mealRepo.updatePreset(uuid, { name, defaultTime });
    } else {
      const maxOrder = Math.max(-1, ...this.presets().map((p) => p.orderIndex));
      void this.mealRepo.createPreset({ name, orderIndex: maxOrder + 1, defaultTime });
    }
  }

  // -------------------- Supprimer --------------------

  protected readonly presetToDelete = signal<LocalMealPreset | null>(null);
  protected readonly deleteMsg = computed(() => {
    const p = this.presetToDelete();
    return p
      ? `Supprimer le repas « ${p.name} » ? Les repas déjà journalisés sont conservés.`
      : '';
  });

  protected confirmDelete(): void {
    const p = this.presetToDelete();
    this.presetToDelete.set(null);
    if (p) void this.mealRepo.removePreset(p.uuid);
  }
}
