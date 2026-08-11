import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Row d'entité canonique — miroir de EntityListRow.kt : squelette 44px (radius 8),
 * fond pendingDeletion-aware, 3 zones : slot `[leading]` optionnel + nom cliquable
 * (boîte centrée, couleur texte pendingDeletion-aware) + slot `[trailing]`.
 *
 * Usage :
 * ```html
 * <app-entity-list-row name="Bench Press" [backgroundColor]="..." (nameClick)="...">
 *   <span leading>...</span>
 *   <span trailing>...</span>
 * </app-entity-list-row>
 * ```
 */
@Component({
  selector: 'app-entity-list-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="elr" [style.padding-block.px]="verticalPadding()">
      <div
        class="elr__inner"
        [style.background]="isPendingDeletion() ? 'var(--c-dark-gray)' : backgroundColor()"
        [style.padding-inline-end.px]="contentEndPadding()"
      >
        <ng-content select="[leading]" />

        <div
          class="elr__name"
          [class.elr__name--clickable]="!isPendingDeletion()"
          [style.flex]="nameWeight()"
          [style.background]="isPendingDeletion() ? 'transparent' : nameBoxColor()"
          (click)="onName()"
        >
          <span
            class="elr__name-text"
            [class.elr__name-text--ellipsis]="nameMaxLines() === 1"
            [style.color]="isPendingDeletion() ? 'var(--app-text-tertiary)' : 'var(--app-text-primary)'"
            >{{ name() }}</span
          >
        </div>

        <ng-content select="[trailing]" />
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        /* Hauteur interne de la row — source unique. */
        --elr-inner-height: 44px;
      }
      .elr {
        width: 100%;
      }
      .elr__inner {
        display: flex;
        align-items: center;
        width: 100%;
        height: var(--elr-inner-height);
        border-radius: var(--radius-md);
        box-sizing: border-box;
      }
      .elr__name {
        display: flex;
        align-items: center;
        height: var(--elr-inner-height);
        border-radius: var(--radius-md);
        padding: 0 var(--space-3);
        min-width: 0;
        box-sizing: border-box;
      }
      .elr__name--clickable {
        cursor: pointer;
      }
      .elr__name-text {
        font-size: var(--font-size-body);
        font-weight: var(--font-weight-medium);
      }
      .elr__name-text--ellipsis {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    `,
  ],
})
export class EntityListRow {
  readonly isPendingDeletion = input(false);
  readonly backgroundColor = input.required<string>();
  readonly nameBoxColor = input('transparent');
  readonly name = input('');
  readonly nameWeight = input(1);
  /** 1 = une seule ligne + ellipsis ; 0/autre = pas de limite. */
  readonly nameMaxLines = input(0);
  readonly verticalPadding = input(5);
  readonly contentEndPadding = input(0);
  readonly nameClick = output<void>();

  protected onName(): void {
    if (!this.isPendingDeletion()) this.nameClick.emit();
  }
}
