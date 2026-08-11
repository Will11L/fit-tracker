import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Barre de titre d'écran — miroir de ScreenTitleBar.kt : boîte pleine largeur 44px sur
 * fond bgSurface, titre centré 16px SemiBold. `clickable` rend la barre cliquable
 * (émet `clicked`) — équivalent du `onClick` non-null Android.
 */
@Component({
  selector: 'app-screen-title-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stb" [class.stb--clickable]="clickable()" (click)="handleClick()">
      <span class="stb__title">{{ title() }}</span>
    </div>
  `,
  styles: [
    `
      .stb {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 44px;
        background: var(--app-bg-surface);
      }
      .stb--clickable {
        cursor: pointer;
      }
      .stb__title {
        color: var(--app-text-primary);
        font-size: var(--font-size-subtitle);
        font-weight: 600;
      }
    `,
  ],
})
export class ScreenTitleBar {
  readonly title = input('');
  readonly clickable = input(false);
  readonly clicked = output<void>();

  protected handleClick(): void {
    if (this.clickable()) this.clicked.emit();
  }
}
