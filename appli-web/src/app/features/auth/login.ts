import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';

/**
 * Écran de connexion — calqué sur la frame Figma `1643:3` (fichier ca2qkjOKCy5N5uEbIKyqrO),
 * adapté desktop (carte centrée sur fond plein, sans chrome mobile).
 * Les écrans auth sont toujours en DARK (cf. .figma-refs/specs/login.md) -> palette figée.
 */
@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="login">
      <div class="inner">
        <div class="logo" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="42" height="42">
            <path
              fill="#2377CA"
              d="M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z"
            />
          </svg>
        </div>
        <h1 class="brand">Fit Tracker</h1>
        <p class="subtitle">Suis ta progression, séance après séance.</p>

        @if (sessionExpired()) {
          <p class="notice">Session expirée — reconnecte-toi.</p>
        }

        <form class="card" (submit)="$event.preventDefault(); submit()">
          <label class="field">
            <span class="field__label">Nom d'utilisateur</span>
            <input
              class="field__input"
              type="text"
              autocomplete="username"
              [value]="username()"
              (input)="username.set($any($event.target).value)"
            />
          </label>
          <label class="field">
            <span class="field__label">Mot de passe</span>
            <input
              class="field__input"
              type="password"
              autocomplete="current-password"
              [value]="password()"
              (input)="password.set($any($event.target).value)"
            />
          </label>
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          <button class="login-btn" type="submit" [disabled]="loading()">
            {{ loading() ? 'Connexion…' : 'Se connecter' }}
          </button>
        </form>
      </div>
    </main>
  `,
  styles: [
    `
      .login {
        min-height: 100vh;
        display: grid;
        place-items: center;
        background: #030506;
        padding: var(--space-4);
      }
      .inner {
        width: 100%;
        max-width: 400px;
        display: flex;
        flex-direction: column;
        align-items: center;
      }
      .logo {
        width: 84px;
        height: 84px;
        border-radius: 22px;
        background: #0f1c26;
        display: grid;
        place-items: center;
        margin-bottom: 28px;
      }
      .brand {
        margin: 0;
        font-size: 30px;
        font-weight: 700;
        color: #2377ca;
      }
      .subtitle {
        margin: 8px 0 40px;
        font-size: 15px;
        color: #245682;
        text-align: center;
      }
      .card {
        width: 100%;
        background: #0f1c26;
        border-radius: 16px;
        padding: 16px;
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .field {
        display: flex;
        flex-direction: column;
        gap: 2px;
        background: #091216;
        border-radius: 8px;
        padding: 8px 14px;
        border-bottom: 2px solid transparent;
        transition: border-color 0.15s ease;
      }
      .field:focus-within {
        border-bottom-color: #2377ca;
      }
      .field__label {
        font-size: 12px;
        color: #2377ca;
      }
      .field__input {
        background: transparent;
        border: none;
        outline: none;
        color: #ffffff;
        font-size: 15px;
        padding: 2px 0;
        font-family: var(--font-family-base);
      }
      .error {
        margin: 0;
        color: #e2574c;
        font-size: 12px;
      }
      .notice {
        width: 100%;
        margin: 0 0 12px;
        padding: 10px 14px;
        border-radius: 10px;
        background: #0f1c26;
        color: #d9a13b;
        font-size: 13px;
        text-align: center;
        box-sizing: border-box;
      }
      .login-btn {
        margin-top: 4px;
        border: none;
        border-radius: 10px;
        background: #245682;
        color: #ffffff;
        font-size: 15px;
        font-weight: 600;
        padding: 13px;
        cursor: pointer;
        font-family: var(--font-family-base);
        transition: filter 0.15s ease;
      }
      .login-btn:hover:not(:disabled) {
        filter: brightness(1.1);
      }
      .login-btn:disabled {
        opacity: 0.6;
        cursor: default;
      }
    `,
  ],
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query param `?reason=...` (bindé via withComponentInputBinding) — posé par AuthService.sessionExpired(). */
  readonly reason = input<string>();
  protected readonly sessionExpired = computed(() => this.reason() === 'session-expired');

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  submit(): void {
    if (this.loading()) return;
    this.error.set(null);
    this.loading.set(true);
    this.auth.login(this.username().trim(), this.password()).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigateByUrl('/exercises');
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Identifiants incorrects ou serveur injoignable.');
      },
    });
  }
}
