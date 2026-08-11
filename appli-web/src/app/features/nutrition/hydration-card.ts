import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ProgressBarPrimitive } from '@designsystem/common_components/progress-bar-primitive';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { AppIcon } from '@designsystem/icons/app-icon';
import { formatLiters } from './hydration';

/** Volumes standards des chips d'ajout rapide (ml). */
const QUICK_ADD_ML = [250, 500, 1000] as const;

/**
 * Card Hydratation du journal Nutrition (2026-07-05) — parité web de HydrationCard.kt.
 * Au-dessus des repas, suit le jour sélectionné. Barre de progression au dégradé de
 * bleus (firstBlue → bleu primaire via color-mix, tokens), trough secondBlue, card
 * thirdBlue ; chips 250/500 mL + 1 L (firstBlue) + saisie perso ; icône undo de la
 * dernière prise manuelle (visible seulement si prise à annuler). Total « X / Y L ».
 */
@Component({
  selector: 'app-hydration-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ProgressBarPrimitive, ActionIconButton, AppIcon],
  template: `
    <div class="hydra">
      <div class="hydra__head">
        <app-icon name="water_drop" [size]="20" color="var(--app-primary-action)" />
        <span class="hydra__title">Hydratation</span>
        <app-action-icon-button
          class="hydra__act"
          icon="edit"
          [hasBackground]="false"
          tint="var(--app-primary-action)"
          [size]="30"
          [iconSize]="18"
          title="Régler l'objectif quotidien"
          (clicked)="editGoal.emit()"
        />
        @if (canUndo()) {
          <app-action-icon-button
            class="hydra__act"
            icon="undo"
            [hasBackground]="false"
            tint="var(--app-primary-action)"
            [size]="30"
            [iconSize]="18"
            title="Annuler la dernière prise"
            (clicked)="undo.emit()"
          />
        }
      </div>

      <div class="hydra__bar">
        <app-progress-bar-primitive
          class="hydra__pbar"
          [progress]="progress()"
          [color]="fillColor()"
          troughColor="var(--c-second-blue)"
        />
        <span class="hydra__amount">{{ amountText() }}</span>
      </div>

      @if (goalMl() === null) {
        <p class="hydra__hint">Définis un objectif quotidien dans Objectifs.</p>
      }

      <div class="hydra__chips">
        @for (ml of quickAdd; track ml) {
          <button type="button" class="hydra__chip hydra__chip--add" (click)="add.emit(ml)">
            {{ ml === 1000 ? '1 L' : ml + ' mL' }}
          </button>
        }
        <button type="button" class="hydra__chip hydra__chip--custom" (click)="custom.emit()">
          Perso
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      .hydra {
        background: var(--c-third-blue);
        border-radius: var(--radius-md);
        padding: var(--space-3) var(--space-3);
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .hydra__head {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .hydra__title {
        flex: 1;
        min-width: 0;
        color: var(--app-text-primary);
        font-size: 15px;
        font-weight: 600;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .hydra__act {
        flex-shrink: 0;
      }
      .hydra__bar {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .hydra__pbar {
        flex: 1;
        min-width: 0;
      }
      .hydra__amount {
        flex-shrink: 0;
        color: var(--app-text-primary);
        font-size: 13px;
        font-weight: var(--font-weight-medium);
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
      }
      .hydra__hint {
        margin: 0;
        font-size: 11px;
        font-style: italic;
        color: var(--app-text-tertiary);
      }
      .hydra__chips {
        display: flex;
        gap: var(--space-2);
      }
      .hydra__chip {
        flex: 1;
        min-width: 0;
        border: none;
        border-radius: var(--radius-sm);
        padding: 8px var(--space-2);
        cursor: pointer;
        color: #fff;
        font-family: var(--font-family-base);
        font-size: 13px;
        font-weight: var(--font-weight-medium);
        transition: filter 0.15s ease;
      }
      .hydra__chip:hover {
        filter: brightness(1.12);
      }
      .hydra__chip--add {
        background: var(--c-first-blue);
      }
      .hydra__chip--custom {
        background: var(--app-primary-action);
      }
    `,
  ],
})
export class HydrationCard {
  readonly consumedMl = input(0);
  readonly goalMl = input<number | null>(null);
  readonly canUndo = input(false);

  readonly add = output<number>();
  readonly custom = output<void>();
  readonly undo = output<void>();
  readonly editGoal = output<void>();

  protected readonly quickAdd = QUICK_ADD_ML;

  protected readonly progress = computed(() => {
    const goal = this.goalMl();
    return goal && goal > 0 ? this.consumedMl() / goal : 0;
  });

  /** Dégradé de bleus firstBlue → bleu primaire selon l'avancement (parité lerp Android),
   *  borné à 100 % → reste sur le bleu clair au-delà de l'objectif. Tokens, pas de hex. */
  protected readonly fillColor = computed(() => {
    const pct = Math.max(0, Math.min(100, Math.round(this.progress() * 100)));
    return `color-mix(in srgb, var(--c-first-blue), var(--app-primary-action) ${pct}%)`;
  });

  protected readonly amountText = computed(() => {
    const goal = this.goalMl();
    const consumed = formatLiters(this.consumedMl());
    return goal && goal > 0 ? `${consumed} / ${formatLiters(goal)} L` : `${consumed} L`;
  });
}
