import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'dark' | 'light' | 'system';

const STORAGE_KEY = 'app-theme';

/**
 * Gère le thème dark/light/system — miroir de SportAppTheme + ThemeMode (Android).
 * Défaut = dark (comme `LocalAppColors = staticCompositionLocalOf { appColorsDark }`).
 * 'system' suit `prefers-color-scheme` (≈ ThemeMode.SYSTEM Android) et réagit en live.
 * Pose `data-theme` sur <html> ; les variables CSS de _colors.scss font basculer la palette.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly media = window.matchMedia('(prefers-color-scheme: light)');
  private readonly _mode = signal<ThemeMode>(this.readInitial());
  readonly mode = this._mode.asReadonly();

  constructor() {
    this.apply(this._mode());
    this.media.addEventListener('change', () => {
      if (this._mode() === 'system') this.apply('system');
    });
  }

  toggle(): void {
    this.set(this.resolved() === 'dark' ? 'light' : 'dark');
  }

  set(mode: ThemeMode): void {
    this._mode.set(mode);
    this.apply(mode);
    localStorage.setItem(STORAGE_KEY, mode);
  }

  /** Mode effectif appliqué (résout 'system' via prefers-color-scheme). */
  resolved(): 'dark' | 'light' {
    const m = this._mode();
    if (m === 'system') return this.media.matches ? 'light' : 'dark';
    return m;
  }

  private apply(mode: ThemeMode): void {
    const root = document.documentElement;
    const effective = mode === 'system' ? (this.media.matches ? 'light' : 'dark') : mode;
    // dark = défaut (aucun attribut) ; light = data-theme="light"
    if (effective === 'light') {
      root.setAttribute('data-theme', 'light');
    } else {
      root.removeAttribute('data-theme');
    }
  }

  private readInitial(): ThemeMode {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'system' ? stored : 'dark';
  }
}
