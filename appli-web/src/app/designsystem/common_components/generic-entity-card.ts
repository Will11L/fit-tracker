import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { AppIcon } from '@designsystem/icons/app-icon';

/**
 * Carte d'entité dépliable — miroir de GenericEntityCard.kt : header (icône 22px + titre
 * centré 16px SemiBold + slot `[headerTrailing]` + chevron) cliquable pour déplier ;
 * details (slot `[details]`) + actions (slot `[actions]`) révélés. Couleur header
 * pendingDeletion-aware (textTertiary) / expanded (primaryAction) / sinon textPrimary 80%.
 *
 * Usage :
 * ```html
 * <app-generic-entity-card title="Bench Press" icon="fitness_center">
 *   <span headerTrailing>★</span>
 *   <div details>...</div>
 *   <div actions>...</div>
 * </app-generic-entity-card>
 * ```
 */
@Component({
  selector: 'app-generic-entity-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppIcon],
  template: `
    <div class="gec" [style.background]="isPendingDeletion() ? 'var(--c-dark-gray)' : cardBackground()">
      <div class="gec__header" (click)="expanded.set(!expanded())">
        <div class="gec__header-row">
          <app-icon [name]="icon()" [size]="22" [color]="headerColor()" />
          <div class="gec__header-trailing">
            <ng-content select="[headerTrailing]" />
            <app-icon [name]="expanded() ? 'keyboard_arrow_up' : 'keyboard_arrow_down'" [size]="24" [color]="headerColor()" />
          </div>
        </div>
        <span class="gec__title" [style.color]="headerColor()">{{ title() }}</span>
      </div>

      <!-- Dépliage animé (≈ animateContentSize/AnimatedVisibility Android) : grid 0fr -> 1fr.
           Contenu toujours projeté ; \`inert\` quand replié (pas de focus sur les actions cachées). -->
      <div class="gec__collapse" [class.gec__collapse--open]="expanded()" [attr.inert]="expanded() ? null : ''">
        <div class="gec__collapse-inner">
          <div class="gec__divider" [style.background]="dividerColor()"></div>
          <div class="gec__details"><ng-content select="[details]" /></div>
          <div class="gec__divider" [style.background]="dividerColor()"></div>
          <div class="gec__actions"><ng-content select="[actions]" /></div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .gec {
        width: 100%;
        border-radius: 16px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
        overflow: hidden;
      }
      .gec__header {
        position: relative;
        cursor: pointer;
        padding: var(--space-2) var(--space-4);
      }
      .gec__header-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .gec__header-trailing {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .gec__title {
        position: absolute;
        left: 0;
        right: 0;
        top: 50%;
        transform: translateY(-50%);
        text-align: center;
        font-size: var(--font-size-subtitle);
        font-weight: 600;
        pointer-events: none;
      }
      .gec__collapse {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows 0.25s ease;
      }
      .gec__collapse--open {
        grid-template-rows: 1fr;
      }
      .gec__collapse-inner {
        min-height: 0;
        overflow: hidden;
      }
      .gec__divider {
        width: 100%;
        height: 1px;
      }
      .gec__details {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding: var(--space-3) 18px;
      }
      .gec__actions {
        display: flex;
        justify-content: space-evenly;
        padding: var(--space-2) 0;
      }
    `,
  ],
})
export class GenericEntityCard {
  readonly title = input('');
  readonly icon = input('');
  readonly isPendingDeletion = input(false);
  readonly cardBackground = input('var(--app-bg-recessed)');

  protected readonly expanded = signal(false);

  protected readonly headerColor = computed(() => {
    if (this.isPendingDeletion()) return 'var(--app-text-tertiary)';
    if (this.expanded()) return 'var(--app-primary-action)';
    return 'color-mix(in srgb, var(--app-text-primary) 80%, transparent)';
  });

  protected readonly dividerColor = computed(() =>
    this.isPendingDeletion() ? 'var(--app-text-tertiary)' : 'var(--app-divider)',
  );
}
