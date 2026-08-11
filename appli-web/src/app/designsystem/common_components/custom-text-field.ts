import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';

let _uid = 0;

/**
 * Champ texte — miroir de CustomTextField.kt : style *filled* (PAS outlined). Fond enfoncé
 * `bg-recessed`, sans bordure, **soulignement** `primary-action` au focus (transparent au repos),
 * label `text-tertiary` qui passe en `primary-action` quand le champ est focus ou rempli,
 * placeholder `text-tertiary`. `multiline` (miroir `singleLine = false` Android) rend un
 * textarea (même style, redimensionnement vertical désactivé).
 */
@Component({
  selector: 'app-custom-text-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field" [class.field--filled]="value().length > 0">
      @if (label()) {
        <label class="field__label" [attr.for]="id">{{ label() }}</label>
      }
      @if (multiline()) {
        <textarea
          class="field__input field__input--multiline"
          [id]="id"
          [rows]="rows()"
          [placeholder]="placeholder()"
          [disabled]="disabled()"
          [value]="value()"
          (input)="value.set($any($event.target).value)"
          (blur)="blurred.emit(value())"
        ></textarea>
      } @else {
        <input
          class="field__input"
          [id]="id"
          [type]="type()"
          [placeholder]="placeholder()"
          [disabled]="disabled()"
          [value]="value()"
          (input)="value.set($any($event.target).value)"
          (blur)="blurred.emit(value())"
        />
      }
    </div>
  `,
  styles: [
    `
      .field {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
      }
      .field__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
        transition: color 0.15s ease;
      }
      .field:focus-within .field__label,
      .field--filled .field__label {
        color: var(--app-primary-action);
      }
      .field__input {
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        color: var(--app-text-primary);
        background-color: var(--app-bg-recessed);
        border: none;
        border-bottom: 1.5px solid transparent;
        border-radius: var(--radius-md) var(--radius-md) 0 0;
        padding: var(--space-3) var(--space-4);
        outline: none;
        transition: border-color 0.15s ease;
      }
      .field__input::placeholder {
        color: var(--app-text-tertiary);
      }
      .field__input:focus {
        border-bottom-color: var(--app-primary-action);
      }
      .field__input:disabled {
        opacity: 0.5;
      }
      .field__input--multiline {
        resize: none;
      }
    `,
  ],
})
export class CustomTextField {
  readonly label = input('');
  readonly placeholder = input('');
  readonly type = input('text');
  readonly multiline = input(false);
  readonly rows = input(3);
  readonly disabled = input(false);
  readonly value = model('');
  /** Émis à la perte de focus (valeur courante) — permet une sauvegarde « au blur » côté parent. */
  readonly blurred = output<string>();
  protected readonly id = `app-tf-${_uid++}`;
}
