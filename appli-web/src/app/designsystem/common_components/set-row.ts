import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { SetRowBoxContent } from '@designsystem/common_components/set-row-box-content';

/** Données d'une ligne de série (sous-ensemble UI de ActualWorkoutSet). */
export interface SetRowData {
  setOrder: number;
  reps: number;
  weight: number;
  status: string;
  isDropset: boolean;
  pendingDeletion: boolean;
  hasNote: boolean;
}

/**
 * Ligne de série d'une séance — miroir de SetRow.kt : rangée 35px (fond par état :
 * pendingDeletion=darkGray / dropset=bgRecessed atténué / sinon bgRecessed) avec cellules
 * index / reps / poids (SetRowBoxContent), icône tendance reps (inline ↔ RepsTrendIcon feature),
 * icône statut, bouton supprimer (rouge) + bouton note.
 * (Poids `weight()` Compose → flex pour index/icônes/boutons (1.6 chacun) ; reps/poids en
 * largeur fixe 48px = largeur des sélecteurs de chiffre des lignes de goals — glyphes centrés.)
 */
@Component({
  selector: 'app-set-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon, ActionIconButton, SetRowBoxContent],
  template: `
    <div class="sr" [style.background]="rowBg()">
      @if (set().isDropset) {
        <app-icon class="sr__idx" name="subdirectory_arrow_right" [size]="20" color="var(--app-divider)" />
      } @else {
        <app-set-row-box-content
          class="sr__idx"
          [text]="set().setOrder.toString()"
          [hasBackground]="!set().pendingDeletion"
          (clicked)="indexClick.emit()"
        />
      }

      <app-set-row-box-content
        class="sr__reps"
        [text]="set().reps.toString()"
        [hasBackground]="!set().pendingDeletion"
        (clicked)="editRepsClick.emit()"
      />
      <app-set-row-box-content
        class="sr__weight"
        [text]="set().weight.toString()"
        [hasBackground]="!set().pendingDeletion"
        (clicked)="editWeightClick.emit()"
      />

      <app-icon class="sr__ico" [name]="trendIcon()" [size]="22" [color]="trendColor()" />
      <app-icon class="sr__ico" [name]="statusIcon()" [size]="22" [color]="statusColor()" />

      <app-action-icon-button
        class="sr__btn"
        icon="delete_sweep"
        [size]="32"
        [iconSize]="18"
        [hasBackground]="!set().pendingDeletion"
        backgroundColor="var(--app-btn-danger-bg)"
        [tint]="set().pendingDeletion ? 'var(--app-text-tertiary)' : 'var(--app-text-primary)'"
        (clicked)="deleteClick.emit()"
      />
      <app-action-icon-button
        class="sr__btn"
        [icon]="set().hasNote ? 'sticky_note_2' : 'note_add'"
        [size]="32"
        [iconSize]="18"
        [hasBackground]="!set().pendingDeletion"
        [tint]="set().pendingDeletion ? 'var(--app-text-tertiary)' : 'var(--app-text-primary)'"
        (clicked)="addNoteClick.emit()"
      />
    </div>
  `,
  styles: [
    `
      .sr {
        display: flex;
        align-items: center;
        gap: 4px;
        width: 100%;
        height: 35px;
        box-sizing: border-box;
        border-radius: 6px;
        padding: 0 4px;
      }
      .sr__idx {
        flex: 1.6;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      /* Même flex que l'en-tête du tableau (settable__h--reps/--weight : flex 2) → colonnes des rows
         alignées sous les libellés Rép./Poids. */
      .sr__reps,
      .sr__weight {
        flex: 2;
        height: 100%;
      }
      .sr__ico,
      .sr__btn {
        flex: 1.6;
        display: flex;
        align-items: center;
        justify-content: center;
      }
    `,
  ],
})
export class SetRow {
  readonly set = input.required<SetRowData>();
  readonly targetRepsMin = input(0);
  readonly targetRepsMax = input(0);
  readonly weightUnit = input<'KG' | 'LB'>('KG');

  readonly indexClick = output<void>();
  readonly editRepsClick = output<void>();
  readonly editWeightClick = output<void>();
  readonly deleteClick = output<void>();
  readonly addNoteClick = output<void>();

  protected rowBg(): string {
    const s = this.set();
    if (s.pendingDeletion) return 'var(--c-dark-gray)';
    if (s.isDropset) return 'color-mix(in srgb, var(--app-bg-recessed) 50%, transparent)';
    return 'var(--app-bg-recessed)';
  }

  // — icône tendance reps (↔ RepsTrendIcon.kt) —
  protected trendIcon(): string {
    const s = this.set();
    if (s.pendingDeletion || s.reps === 0) return 'check_indeterminate_small';
    if (s.reps > this.targetRepsMax()) return 'north';
    if (s.reps < this.targetRepsMin()) return 'south';
    return 'check';
  }
  protected trendColor(): string {
    const s = this.set();
    if (s.pendingDeletion || s.reps === 0) return 'var(--app-text-tertiary)';
    if (s.reps > this.targetRepsMax()) return 'var(--c-blue-medium)';
    if (s.reps < this.targetRepsMin()) return 'var(--c-red-medium)';
    if (s.reps === this.targetRepsMin()) return 'var(--c-orange-medium)';
    return 'var(--c-medium-green)';
  }

  // — icône statut (↔ getStatusIconAndColor) —
  protected statusIcon(): string {
    if (this.set().pendingDeletion) return 'check_indeterminate_small';
    switch (this.set().status.toUpperCase()) {
      case 'DONE':
        return 'check_circle';
      case 'IN_PROGRESS':
        // 'arrow_progress' (drawable Android ic_arrow_progress) n'existe pas dans la police web ;
        // 'arrow_circle_up' = flèche vers le haut dans un cercle (esprit de l'icône Android,
        // distincte de la flèche 'north' de la tendance reps).
        return 'arrow_circle_up';
      case 'NOT_STARTED':
        return 'help';
      case 'SKIPPED':
        return 'cancel';
      default:
        return 'question_mark';
    }
  }
  protected statusColor(): string {
    if (this.set().pendingDeletion) return 'var(--app-text-tertiary)';
    switch (this.set().status.toUpperCase()) {
      case 'DONE':
        return 'var(--app-snackbar-success)';
      case 'IN_PROGRESS':
        return 'var(--app-snackbar-warning)';
      case 'SKIPPED':
        return 'var(--app-snackbar-error)';
      default:
        return 'var(--app-text-tertiary)';
    }
  }
}
