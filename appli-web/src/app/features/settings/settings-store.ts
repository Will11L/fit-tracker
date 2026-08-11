import { Injectable, signal } from '@angular/core';

/**
 * Préférences web locales — miroir de OnboardingPreferences + AppSettings (Android).
 * Local-only (localStorage, non synchronisé serveur), valeurs d'état en UPPER_CASE
 * (politique 11) identiques aux enums Kotlin.
 */
export type StartScreen =
  | 'HOME'
  | 'TASKS'
  | 'CALENDAR'
  | 'STATS'
  | 'CHRONO'
  | 'PROGRAM'
  | 'NOTIFICATIONS'
  | 'CONVERSATIONS';
export type AppLocale = 'SYSTEM' | 'EN' | 'FR';
export type WeightUnit = 'KG' | 'LBS';
export type LengthUnit = 'CM' | 'INCHES';

export interface WebSettings {
  startScreen: StartScreen;
  appLocale: AppLocale;
  weightUnit: WeightUnit;
  lengthUnit: LengthUnit;
  // Miroir AppSettings.kt (notifications)
  vibrateOnInAppNotification: boolean;
  soundOnInAppNotification: boolean;
  showInAppNotificationOverlay: boolean;
  showPhoneNotifications: boolean;
  notifyTasks: boolean;
  notifyTimers: boolean;
  notifyRoutines: boolean;
  /** Minutes avant échéance/début, null = « Aucun ». Défaut usine 15 min (Android). */
  defaultReminderMinutesBefore: number | null;
}

export const DEFAULT_SETTINGS: WebSettings = {
  startScreen: 'HOME',
  appLocale: 'SYSTEM',
  weightUnit: 'KG',
  lengthUnit: 'CM',
  vibrateOnInAppNotification: true,
  soundOnInAppNotification: false,
  showInAppNotificationOverlay: true,
  showPhoneNotifications: true,
  notifyTasks: true,
  notifyTimers: true,
  notifyRoutines: true,
  defaultReminderMinutesBefore: 15,
};

const STORAGE_KEY = 'app-settings';

/** Route web associée à chaque écran de démarrage (≈ mapping SplashScreenViewModel Android). */
export const START_SCREEN_ROUTES: Record<StartScreen, string> = {
  HOME: 'home',
  TASKS: 'routines',
  CALENDAR: 'calendar',
  STATS: 'stats',
  CHRONO: 'chrono',
  PROGRAM: 'planning',
  NOTIFICATIONS: 'notifications',
  CONVERSATIONS: 'home', // pas de Conversations web pour l'instant — fallback home
};

/** Lecture pure (utilisée par app.routes.ts : redirect '' → page d'accueil choisie). */
export function readStartScreenRoute(): string {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const start = raw ? (JSON.parse(raw) as Partial<WebSettings>).startScreen : undefined;
    return start && start in START_SCREEN_ROUTES ? START_SCREEN_ROUTES[start] : 'home';
  } catch {
    return 'home';
  }
}

@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly _settings = signal<WebSettings>(this.read());
  readonly settings = this._settings.asReadonly();

  update(patch: Partial<WebSettings>): void {
    const next = { ...this._settings(), ...patch };
    this._settings.set(next);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  private read(): WebSettings {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw
        ? { ...DEFAULT_SETTINGS, ...(JSON.parse(raw) as Partial<WebSettings>) }
        : DEFAULT_SETTINGS;
    } catch {
      return DEFAULT_SETTINGS;
    }
  }
}
