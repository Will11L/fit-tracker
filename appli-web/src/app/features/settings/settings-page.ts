import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { CustomSwitch } from '@designsystem/common_components/custom-switch';
import { CustomRadioButton } from '@designsystem/common_components/custom-radio-button';
import { SingleSelectDropdown } from '@designsystem/common_components/single-select-dropdown';
import { ThemeService, type ThemeMode } from '@designsystem/theme/theme.service';
import { API_BASE_URL } from '@core/api/api.config';
import {
  SettingsStore,
  type AppLocale,
  type LengthUnit,
  type StartScreen,
  type WebSettings,
  type WeightUnit,
} from './settings-store';

type BoolKey =
  | 'vibrateOnInAppNotification'
  | 'soundOnInAppNotification'
  | 'showInAppNotificationOverlay'
  | 'showPhoneNotifications'
  | 'notifyTasks'
  | 'notifyTimers'
  | 'notifyRoutines';

/**
 * Écran Paramètres — refonte fidèle Android, flat. Android est un hub de drill-down
 * (SettingsScreen.kt → Appearance / Startup / LanguageFormat / Notification sub-screens) ;
 * le web aplatit les 4 catégories en sections (TitledDivider) sur une seule page, avec
 * les mêmes cards (SettingsSectionCard ≈ .card titre primaryAction), mêmes libellés FR,
 * mêmes contrôles (radios, dropdown, switches, chips rappel) et même ordre.
 * Déviations : « Refaire l'onboarding » reste Android-only (pas de flow onboarding web,
 * pas de bouton mort) ; « URL serveur » est présent mais read-only (API same-origin en
 * prod, proxy en dev — pas de switcher PC LAN / Pi comme sur Android).
 * Prefs persistées localStorage (SettingsStore).
 */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScreenTitleBar, TitledDivider, CustomSwitch, CustomRadioButton, SingleSelectDropdown],
  template: `
    <section class="page">
      <app-screen-title-bar title="Paramètres" />

      <div class="page__body">
        <!-- 2 colonnes équilibrées (comme Profil / Objectifs / Sync) : empilées < 900px. -->
        <div class="split">
        <!-- GAUCHE : Apparence · Démarrage · Langue & format -->
        <div class="split__col">
        <!-- ===== Apparence (≈ AppearanceSettingsScreen) ===== -->
        <app-titled-divider title="Apparence" />
        <div class="card">
          <h2 class="card__title">Thème</h2>
          <p class="desc">
            Thème de l'interface. « Suivre le système » suit le mode clair/sombre de l'appareil.
          </p>
          <div class="radios" role="radiogroup">
            @for (opt of themeOptions; track opt.value) {
              <button type="button" class="radiorow" (click)="setTheme(opt.value)">
                <app-custom-radio-button [selected]="theme.mode() === opt.value" />
                <span class="radiorow__label">{{ opt.label }}</span>
              </button>
            }
          </div>
        </div>

        <!-- ===== Démarrage (≈ StartupSettingsScreen) ===== -->
        <app-titled-divider title="Démarrage" />
        <div class="card">
          <h2 class="card__title">Page d'accueil au démarrage</h2>
          <p class="desc">Écran ouvert au lancement de l'app.</p>
          <app-single-select-dropdown
            label="Page d'accueil au démarrage"
            [selected]="startLabel()"
            [options]="startOptions"
            [disabledOptions]="startDisabled"
            disabledSuffix=" (à venir)"
            (select)="setStartScreen($event)"
          />
        </div>

        <!-- ===== Langue & format (≈ LanguageFormatSettingsScreen) ===== -->
        <app-titled-divider title="Langue &amp; format" />
        <div class="card">
          <h2 class="card__title">Langue</h2>
          <div class="radios" role="radiogroup">
            @for (opt of localeOptions; track opt.value) {
              <button type="button" class="radiorow" (click)="setLocale(opt.value)">
                <app-custom-radio-button [selected]="s().appLocale === opt.value" />
                <span class="radiorow__label">{{ opt.label }}</span>
              </button>
            }
          </div>
          <p class="desc">L'interface web est en français pour l'instant (traduction EN à venir).</p>
        </div>

        <div class="card">
          <h2 class="card__title">Unité de poids</h2>
          <p class="desc">Utilisée partout où des poids sont affichés (séries, stats).</p>
          <div class="radios" role="radiogroup">
            @for (opt of weightOptions; track opt.value) {
              <button type="button" class="radiorow" (click)="setWeight(opt.value)">
                <app-custom-radio-button [selected]="s().weightUnit === opt.value" />
                <span class="radiorow__label">{{ opt.label }}</span>
              </button>
            }
          </div>
        </div>

        <div class="card">
          <h2 class="card__title">Unité de longueur</h2>
          <p class="desc">Utilisée pour la taille et mensurations (à venir).</p>
          <div class="radios" role="radiogroup">
            @for (opt of lengthOptions; track opt.value) {
              <button type="button" class="radiorow" (click)="setLength(opt.value)">
                <app-custom-radio-button [selected]="s().lengthUnit === opt.value" />
                <span class="radiorow__label">{{ opt.label }}</span>
              </button>
            }
          </div>
        </div>

        </div>
        <!-- DROITE : Notifications · Serveur -->
        <div class="split__col">
        <!-- ===== Notifications (≈ NotificationSettingsScreen) ===== -->
        <app-titled-divider title="Notifications" />
        <div class="card">
          <h2 class="card__title">Notifications</h2>
          @for (row of notifRows; track row.key) {
            <div class="toggle">
              <div class="toggle__text">
                <span class="toggle__title">{{ row.title }}</span>
                <span class="toggle__desc">{{ row.desc }}</span>
              </div>
              <app-custom-switch [checked]="s()[row.key]" (checkedChange)="setBool(row.key, $event)" />
            </div>
          }
        </div>

        <div class="card">
          <h2 class="card__title">Catégories de notifications</h2>
          @for (row of categoryRows; track row.key) {
            <div class="toggle">
              <div class="toggle__text">
                <span class="toggle__title">{{ row.title }}</span>
                <span class="toggle__desc">{{ row.desc }}</span>
              </div>
              <app-custom-switch [checked]="s()[row.key]" (checkedChange)="setBool(row.key, $event)" />
            </div>
          }
        </div>

        <div class="card">
          <h2 class="card__title">Rappel par défaut</h2>
          <p class="desc">
            Pré-remplit le rappel à la création d'une tâche ou d'une routine. « Aucun » = pas de
            rappel par défaut.
          </p>
          <span class="chips__label">Me rappeler avant</span>
          <div class="chips">
            @for (preset of reminderPresets; track preset.label) {
              <button
                type="button"
                class="chip"
                [class.chip--on]="s().defaultReminderMinutesBefore === preset.minutes"
                (click)="setReminder(preset.minutes)"
              >
                {{ preset.label }}
              </button>
            }
          </div>
        </div>

        <!-- ===== Serveur (≈ ServerUrlSettingsScreen, read-only sur le web) ===== -->
        <app-titled-divider title="Serveur" />
        <div class="card">
          <h2 class="card__title">URL serveur</h2>
          <p class="desc">
            Sur le web, l'API est servie sur la même origine que la page (proxy en dev) — tu ne
            peux pas basculer entre PC LAN, Pi prod ou URL custom comme sur Android.
          </p>
          <span class="mono">Effectif : {{ apiUrl }}</span>
        </div>
        </div>
        </div>
      </div>
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; corps pleine largeur pour les 2 colonnes. */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* 2 colonnes équilibrées (comme Profil / Objectifs / Sync), empilées < 900px. */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__col {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
      }
      /* ≈ SettingsSectionCard : bg bgRecessed + titre primaryAction. */
      .card {
        background: var(--app-bg-recessed);
        border-radius: var(--radius-lg);
        padding: var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .card__title {
        margin: 0;
        font-size: var(--font-size-subtitle);
        font-weight: 600;
        color: var(--app-primary-action);
      }
      .desc {
        margin: 0;
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      /* ≈ SettingsRadioOptions : rows 40px radio + label. */
      .radios {
        display: flex;
        flex-direction: column;
      }
      .radiorow {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        height: 40px;
        width: 100%;
        background: transparent;
        border: none;
        padding: 0;
        cursor: pointer;
        text-align: left;
        font-family: var(--font-family-base);
        appearance: none;
        -webkit-appearance: none;
      }
      .radiorow__label {
        color: var(--app-text-primary);
        font-size: var(--font-size-body);
      }
      /* ≈ SettingsToggleRow : titre + description + CustomSwitch. */
      .toggle {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-4);
        padding: var(--space-1) 0;
      }
      .toggle__text {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .toggle__title {
        color: var(--app-text-primary);
        font-size: var(--font-size-body);
      }
      .toggle__desc {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      /* ≈ ReminderSelector : label tertiary + chips presets. */
      .chips__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
      .chip {
        background: var(--app-bg-surface);
        color: var(--app-text-tertiary);
        border: none;
        border-radius: var(--radius-sm);
        padding: 6px 10px;
        font-size: var(--font-size-caption);
        font-family: var(--font-family-base);
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
      }
      .chip--on {
        background: var(--app-primary-action);
        color: var(--app-text-primary);
        font-weight: 600;
      }
      /* ≈ texte « Effectif : <url> » Android : textSecondary + monospace. */
      .mono {
        font-family: monospace;
        font-size: var(--font-size-caption);
        color: var(--app-text-secondary);
        word-break: break-all;
      }
    `,
  ],
})
export class SettingsPage {
  protected readonly theme = inject(ThemeService);
  private readonly store = inject(SettingsStore);

  protected readonly s = this.store.settings;

  // ----- Serveur : URL API résolue (same-origin, read-only sur le web) -----
  protected readonly apiUrl = `${window.location.origin}${API_BASE_URL}`;

  // ----- Apparence (libellés = common_theme_* FR) -----
  protected readonly themeOptions: { value: ThemeMode; label: string }[] = [
    { value: 'light', label: 'Clair' },
    { value: 'dark', label: 'Sombre' },
    { value: 'system', label: 'Suivre le système' },
  ];

  // ----- Démarrage (libellés = settings_start_screen_* FR, ordre Android) -----
  private readonly startLabels: Record<StartScreen, string> = {
    HOME: 'Session du jour',
    TASKS: 'Tâches',
    CALENDAR: 'Calendrier',
    STATS: 'Stats',
    CHRONO: 'Chrono',
    PROGRAM: 'Programme',
    NOTIFICATIONS: 'Notifications',
    CONVERSATIONS: 'Conversations',
  };
  protected readonly startOptions = Object.values(this.startLabels);
  protected readonly startDisabled = [this.startLabels.CONVERSATIONS];
  protected readonly startLabel = computed(() => this.startLabels[this.s().startScreen]);

  // ----- Langue & format -----
  protected readonly localeOptions: { value: AppLocale; label: string }[] = [
    { value: 'SYSTEM', label: 'Suivre le système' },
    { value: 'EN', label: 'English' },
    { value: 'FR', label: 'Français' },
  ];
  protected readonly weightOptions: { value: WeightUnit; label: string }[] = [
    { value: 'KG', label: 'Kilogrammes (kg)' },
    { value: 'LBS', label: 'Livres (lbs)' },
  ];
  protected readonly lengthOptions: { value: LengthUnit; label: string }[] = [
    { value: 'CM', label: 'Centimètres (cm)' },
    { value: 'INCHES', label: 'Pouces (in)' },
  ];

  // ----- Notifications (libellés = settings_toggle_* FR) -----
  protected readonly notifRows: { key: BoolKey; title: string; desc: string }[] = [
    {
      key: 'vibrateOnInAppNotification',
      title: 'Vibrer pour les notifications in-app',
      desc: "Déclencher une vibration quand une notification apparaît dans l'app",
    },
    {
      key: 'soundOnInAppNotification',
      title: 'Son sur les notifications in-app',
      desc: "Jouer un son quand une notification apparaît dans l'app",
    },
    {
      key: 'showInAppNotificationOverlay',
      title: 'Afficher la superposition de notification',
      desc: 'Afficher une notification en superposition quand elle apparaît',
    },
    {
      key: 'showPhoneNotifications',
      title: 'Afficher les notifications système',
      desc: 'Afficher les notifications dans la barre système',
    },
  ];
  protected readonly categoryRows: { key: BoolKey; title: string; desc: string }[] = [
    {
      key: 'notifyTasks',
      title: 'Rappels de tâches',
      desc: 'Notifier pour les rappels de tâches',
    },
    {
      key: 'notifyTimers',
      title: 'Notifications de timer',
      desc: 'Notifier quand un timer se termine',
    },
    {
      key: 'notifyRoutines',
      title: 'Notifications de routines',
      desc: 'Notifier au début et à la fin des périodes de routine',
    },
  ];

  // ----- Rappel par défaut (presets = DEFAULT_REMINDER_PRESETS Android) -----
  protected readonly reminderPresets: { minutes: number | null; label: string }[] = [
    { minutes: null, label: 'Aucun' },
    { minutes: 5, label: '5 min' },
    { minutes: 15, label: '15 min' },
    { minutes: 30, label: '30 min' },
    { minutes: 60, label: '1 heure' },
  ];

  protected setTheme(mode: ThemeMode): void {
    this.theme.set(mode);
  }

  protected setStartScreen(label: string): void {
    const entry = (Object.entries(this.startLabels) as [StartScreen, string][]).find(
      ([, l]) => l === label,
    );
    if (entry) this.store.update({ startScreen: entry[0] });
  }

  protected setLocale(value: AppLocale): void {
    this.store.update({ appLocale: value });
  }

  protected setWeight(value: WeightUnit): void {
    this.store.update({ weightUnit: value });
  }

  protected setLength(value: LengthUnit): void {
    this.store.update({ lengthUnit: value });
  }

  protected setBool(key: BoolKey, value: boolean): void {
    this.store.update({ [key]: value } as Partial<WebSettings>);
  }

  protected setReminder(minutes: number | null): void {
    this.store.update({ defaultReminderMinutesBefore: minutes });
  }
}
