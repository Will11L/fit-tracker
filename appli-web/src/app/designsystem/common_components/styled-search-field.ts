import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/**
 * Champ de recherche — miroir de StyledSearchField.kt : input pleine largeur sur fond
 * bgRecessed (radius 8), texte primaryAction 14px, placeholder textPrimary 60%, indicateur
 * bas primaryAction au focus (transparent sinon). singleLine.
 */
@Component({
  selector: 'app-styled-search-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <input
      type="text"
      class="ssf"
      [value]="value()"
      (input)="value.set(asValue($event))"
      [placeholder]="placeholderText()"
    />
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
      .ssf {
        width: 100%;
        box-sizing: border-box;
        height: 44px;
        background: var(--app-bg-recessed);
        border: none;
        border-bottom: 2px solid transparent;
        border-radius: var(--radius-md);
        color: var(--app-primary-action);
        font-family: var(--font-family-base);
        font-size: 14px;
        padding: 0 var(--space-3);
        outline: none;
        transition: border-color 0.15s ease;
      }
      .ssf::placeholder {
        color: color-mix(in srgb, var(--app-text-primary) 60%, transparent);
      }
      .ssf:focus {
        border-bottom-color: var(--app-primary-action);
      }
    `,
  ],
})
export class StyledSearchField {
  readonly value = model('');
  readonly placeholderText = input('Search');

  protected asValue(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }
}
