import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { HmsWheelPicker } from '@designsystem/common_components/hms-wheel-picker';

/**
 * Écran Chrono — miroir flat de ChronoScreen.kt (feature/chrono Android) :
 * ScreenTitleBar + DualTabMenu (Chrono | Minuteur, en haut comme le hub web) puis :
 * - Chrono : affichage HH:MM:SS:CC + liste de tours (N° / Δ Tour / Temps) + boutons
 *   Tour/Réinitialiser (gauche) et Démarrer/Arrêter/Reprendre (droite) — mêmes cycles
 *   d'états que StopwatchStateMachine.kt (IDLE → RUNNING ⇄ PAUSED → reset).
 * - Minuteur : cadran circulaire 60 segments (TimerCircularDisplay.kt, SVG ici) qui
 *   s'éteignent sens horaire, pulse à FINISHED + 9 préréglages + durée custom (dialog
 *   HmsWheelPicker) — mêmes cycles que TimerStateMachine.kt (+ FINISHED → restart).
 * Formats portés de ChronoFormatters.kt. Persistance locale (dernier onglet + dernier
 * minuteur) en localStorage, comme les autres prefs UI web (thème). Fin de minuteur →
 * snackbar (équivalent web de NotificationCenter.notifyTimerDone).
 */

type StopwatchState = 'IDLE' | 'RUNNING' | 'PAUSED';
type TimerState = 'IDLE' | 'RUNNING' | 'PAUSED' | 'FINISHED';

interface Lap {
  index: number;
  lapMillis: number;
  totalMillis: number;
}

interface TimerPreset {
  label: string;
  millis: number;
}

interface Segment {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

const TICK_INTERVAL_MS = 50;
const STORAGE_TIMER_NAME = 'chrono.lastTimerName';
const STORAGE_TIMER_MS = 'chrono.lastTimerDurationMillis';

// — Formatters portés de ChronoFormatters.kt —

/** "HH:MM:SS:CC" — affichage principal (chrono + minuteur). */
function formatTimeWithCentiseconds(ms: number): string {
  const clamped = Math.max(0, ms);
  const totalSeconds = Math.floor(clamped / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const centiseconds = Math.floor((clamped % 1000) / 10);
  const p = (v: number) => v.toString().padStart(2, '0');
  return `${p(hours)}:${p(minutes)}:${p(seconds)}:${p(centiseconds)}`;
}

/** Nom "humain" depuis une durée (ex. 90000 → "1 min 30 s") — pour la durée custom. */
function timerNameForDuration(ms: number): string {
  const totalSeconds = Math.floor(Math.max(0, ms) / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const parts: string[] = [];
  if (hours > 0) parts.push(`${hours} h`);
  if (minutes > 0) parts.push(`${minutes} min`);
  if (seconds > 0) parts.push(`${seconds} s`);
  return parts.length ? parts.join(' ') : '0 s';
}

// Géométrie du cadran (TimerCircularDisplay.kt : diameter 260, barLength 18, segmentCount 60).
const DIAL_SIZE = 260;
const DIAL_BAR_LENGTH = 18;
const DIAL_SEGMENTS = 60;

function buildDialSegments(): Segment[] {
  const c = DIAL_SIZE / 2;
  const outer = DIAL_SIZE / 2;
  const inner = outer - DIAL_BAR_LENGTH;
  const segments: Segment[] = [];
  for (let i = 0; i < DIAL_SEGMENTS; i++) {
    // i=0 à 12h, rotation horaire — angle mesuré depuis le haut.
    const angle = ((i * 360) / DIAL_SEGMENTS - 90) * (Math.PI / 180);
    segments.push({
      x1: c + outer * Math.cos(angle),
      y1: c + outer * Math.sin(angle),
      x2: c + inner * Math.cos(angle),
      y2: c + inner * Math.sin(angle),
    });
  }
  return segments;
}

@Component({
  selector: 'app-chrono-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ScreenTitleBar, TitledDivider, ActionIconWithTextButton, FormDialog, HmsWheelPicker],
  template: `
    <section class="page">
      <app-screen-title-bar title="Chrono & Minuteur" />

      <div class="page__body">
        <div class="split">
          <div class="col">
            <!-- ===== Chronomètre (StopwatchPage.kt) ===== -->
            <app-titled-divider title="Chrono" />

            <div class="display">
              <span class="display__time">{{ stopwatchText() }}</span>
            </div>

            <app-titled-divider title="Tours" />

            <div class="laps-header">
              <span class="laps-header__num">N°</span>
              <span class="laps-header__delta">Δ Tour</span>
              <span class="laps-header__time">Temps</span>
            </div>

            @if (laps().length === 0) {
              <div class="lap-row lap-row--empty">Aucun tour</div>
            } @else {
              <div class="laps">
                @for (lap of laps(); track lap.index) {
                  <div class="lap-row">
                    <span class="lap-row__num">{{ lap.index }}</span>
                    <span class="lap-row__delta">{{ formatMs(lap.lapMillis) }}</span>
                    <span class="lap-row__time">{{ formatMs(lap.totalMillis) }}</span>
                  </div>
                }
              </div>
            }

            <div class="buttons">
              <app-action-icon-with-text-button
                [fullWidth]="true"
                [icon]="swLeftIcon()"
                [text]="swLeftText()"
                [backgroundColor]="swLeftEnabled() ? 'var(--c-blue-medium)' : 'var(--app-bg-recessed)'"
                [tint]="swLeftEnabled() ? 'var(--app-text-primary)' : 'var(--app-text-tertiary)'"
                [textColor]="swLeftEnabled() ? 'var(--app-text-primary)' : 'var(--app-text-tertiary)'"
                [clickable]="swLeftEnabled()"
                (clicked)="onStopwatchLeft()"
              />
              <app-action-icon-with-text-button
                [fullWidth]="true"
                [icon]="swRightIcon()"
                [text]="swRightText()"
                [backgroundColor]="swState() === 'RUNNING' ? 'var(--c-red-medium)' : 'var(--app-primary-action)'"
                (clicked)="onStopwatchRight()"
              />
            </div>
          </div>

          <div class="col">
            <!-- ===== Minuteur (TimerPage.kt) ===== -->
            <app-titled-divider title="Minuteur" />

            <div class="dial-wrap">
              <svg
                class="dial"
                [class.dial--finished]="timerState() === 'FINISHED'"
                [attr.viewBox]="'0 0 ' + dialSize + ' ' + dialSize"
                [attr.width]="dialSize"
                [attr.height]="dialSize"
              >
                @for (seg of dialSegments; track $index) {
                  <line
                    [attr.x1]="seg.x1"
                    [attr.y1]="seg.y1"
                    [attr.x2]="seg.x2"
                    [attr.y2]="seg.y2"
                    stroke-width="3"
                    stroke-linecap="round"
                    [attr.stroke]="segmentColor($index)"
                  />
                }
              </svg>
              <span class="dial__time" [class.dial__time--finished]="timerState() === 'FINISHED'">
                {{ timerText() }}
              </span>
            </div>

            <app-titled-divider title="Préréglages" />

            <div class="presets">
              @for (preset of presets; track preset.millis) {
                <button
                  type="button"
                  class="preset"
                  [class.preset--selected]="preset.millis === timerDurationMs() && timerState() === 'IDLE'"
                  [disabled]="timerState() !== 'IDLE'"
                  (click)="selectPreset(preset)"
                >
                  {{ preset.label }}
                </button>
              }
            </div>

            <p class="hint">{{ timerHint() }}</p>

            <div class="buttons">
              <app-action-icon-with-text-button
                [fullWidth]="true"
                [icon]="timerState() === 'IDLE' ? 'timer' : 'restart_alt'"
                [text]="timerState() === 'IDLE' ? 'Régler' : 'Réinitialiser'"
                backgroundColor="var(--c-blue-medium)"
                tint="var(--app-text-primary)"
                textColor="var(--app-text-primary)"
                (clicked)="onTimerLeft()"
              />
              <app-action-icon-with-text-button
                [fullWidth]="true"
                [icon]="timerState() === 'RUNNING' ? 'pause_circle' : 'play_circle'"
                [text]="timerRightText()"
                [backgroundColor]="timerState() === 'RUNNING' ? 'var(--c-red-medium)' : 'var(--app-primary-action)'"
                (clicked)="onTimerRight()"
              />
            </div>
          </div>
        </div>
      </div>

      <app-form-dialog
        [open]="showSetDialog()"
        title="Régler le minuteur"
        confirmText="Enregistrer"
        [confirmEnabled]="dialogHours() + dialogMinutes() + dialogSeconds() > 0"
        disabledReason="La durée doit être supérieure à 0"
        (confirm)="confirmDuration()"
        (dismiss)="showSetDialog.set(false)"
      >
        <app-hms-wheel-picker
          [hours]="dialogHours()"
          [minutes]="dialogMinutes()"
          [seconds]="dialogSeconds()"
          (hoursChange)="dialogHours.set($event)"
          (minutesChange)="dialogMinutes.set($event)"
          (secondsChange)="dialogSeconds.set($event)"
        />
      </app-form-dialog>
    </section>
  `,
  styles: [
    `
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
      }
      /* 2 colonnes : Chrono à gauche, Minuteur à droite ; empilé sous 760px. */
      .split {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--page-gutter);
        align-items: start;
        max-width: 1100px;
        margin: 0 auto;
      }
      .col {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        min-width: 0;
      }
      @media (max-width: 760px) {
        .split {
          grid-template-columns: 1fr;
        }
      }
      /* — Affichage principal du chrono (boîte bgRecessed + temps en primaryAction) — */
      .display {
        display: flex;
        justify-content: center;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-5);
      }
      .display__time {
        color: var(--app-primary-action);
        font-size: 44px;
        font-weight: 500;
        font-variant-numeric: tabular-nums;
      }
      /* — Tours : header + rows (LapsHeader.kt / LapRow.kt : 1f / 2f / 2f) — */
      .laps-header {
        display: flex;
        padding: 0 var(--space-3);
        font-size: 13px;
        font-weight: var(--font-weight-medium);
      }
      .laps-header__num {
        flex: 1;
        color: var(--app-primary-action);
      }
      .laps-header__delta {
        flex: 2;
        text-align: right;
        color: var(--c-blue-medium);
      }
      .laps-header__time {
        flex: 2;
        text-align: right;
        color: var(--app-text-tertiary);
      }
      .laps {
        display: flex;
        flex-direction: column;
        gap: 6px;
        max-height: 320px;
        overflow-y: auto;
      }
      .lap-row {
        display: flex;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
        font-size: 14px;
        font-variant-numeric: tabular-nums;
      }
      .lap-row--empty {
        justify-content: center;
        font-style: italic;
        color: var(--c-blue-medium);
      }
      .lap-row__num {
        flex: 1;
        color: var(--app-text-primary);
      }
      .lap-row__delta {
        flex: 2;
        text-align: right;
        color: var(--c-blue-medium);
      }
      .lap-row__time {
        flex: 2;
        text-align: right;
        color: var(--app-text-tertiary);
      }
      /* — Cadran minuteur (TimerCircularDisplay.kt) — */
      .dial-wrap {
        position: relative;
        display: flex;
        justify-content: center;
        align-items: center;
        padding: var(--space-2) 0;
      }
      .dial--finished {
        animation: chrono-pulse 800ms linear infinite;
      }
      .dial__time {
        position: absolute;
        color: var(--app-primary-action);
        font-size: 30px;
        font-weight: 500;
        font-variant-numeric: tabular-nums;
      }
      .dial__time--finished {
        animation: chrono-pulse 800ms linear infinite;
      }
      @keyframes chrono-pulse {
        0%,
        100% {
          opacity: 0;
        }
        50% {
          opacity: 1;
        }
      }
      /* — Préréglages : grille 3 colonnes (PresetTile.kt : tuiles bgRecessed, ratio 2:1) — */
      .presets {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 10px;
      }
      .preset {
        aspect-ratio: 2;
        border: none;
        border-radius: var(--radius-md);
        background: var(--app-bg-recessed);
        color: color-mix(in srgb, var(--app-text-primary) 90%, transparent);
        font-family: var(--font-family-base);
        font-size: 16px;
        font-weight: var(--font-weight-medium);
        cursor: pointer;
        transition: filter 0.15s ease;
      }
      .preset:hover:not(:disabled) {
        filter: brightness(1.3);
      }
      .preset:disabled {
        color: color-mix(in srgb, var(--app-text-primary) 40%, transparent);
        cursor: default;
      }
      .preset--selected {
        background: var(--c-blue-medium);
        color: var(--app-text-primary);
      }
      .hint {
        margin: 0;
        text-align: center;
        font-size: 14px;
        color: color-mix(in srgb, var(--app-text-primary) 65%, transparent);
      }
      /* — Boutons d'action : 2 boutons pleine largeur 46px (variante fullWidth du DS) — */
      .buttons {
        display: flex;
        gap: var(--space-5);
        margin-top: var(--space-3);
      }
    `,
  ],
})
export class ChronoPage {
  private readonly snackbar = inject(SnackbarService);

  // ===== Chronomètre — port de StopwatchStateMachine.kt =====
  protected readonly swState = signal<StopwatchState>('IDLE');
  protected readonly elapsedMs = signal(0);
  protected readonly laps = signal<Lap[]>([]);

  private swTicker: ReturnType<typeof setInterval> | null = null;
  private swAccumulatedMs = 0;
  private swRunningStartMs = 0;
  private swLastLapTotalMs = 0;

  // ===== Minuteur — port de TimerStateMachine.kt =====
  protected readonly timerState = signal<TimerState>('IDLE');
  protected readonly timerName = signal(localStorage.getItem(STORAGE_TIMER_NAME) ?? '');
  protected readonly timerDurationMs = signal(Number(localStorage.getItem(STORAGE_TIMER_MS)) || 60_000);
  protected readonly remainingMs = signal(this.timerDurationMs());

  private timerTicker: ReturnType<typeof setInterval> | null = null;
  private timerEndMs = 0;
  private timerRemainingOnPauseMs = 0;

  // ===== Dialog durée custom =====
  protected readonly showSetDialog = signal(false);
  protected readonly dialogHours = signal(0);
  protected readonly dialogMinutes = signal(0);
  protected readonly dialogSeconds = signal(0);

  protected readonly presets: TimerPreset[] = [
    { label: '30s', millis: 30_000 },
    { label: '45s', millis: 45_000 },
    { label: '1 min', millis: 60_000 },
    { label: '2 min', millis: 2 * 60_000 },
    { label: '5 min', millis: 5 * 60_000 },
    { label: '10 min', millis: 10 * 60_000 },
    { label: '15 min', millis: 15 * 60_000 },
    { label: '30 min', millis: 30 * 60_000 },
    { label: '1h', millis: 60 * 60_000 },
  ];

  protected readonly dialSize = DIAL_SIZE;
  protected readonly dialSegments = buildDialSegments();

  protected readonly stopwatchText = computed(() => formatTimeWithCentiseconds(this.elapsedMs()));
  protected readonly timerText = computed(() => formatTimeWithCentiseconds(this.remainingMs()));

  protected readonly swLeftEnabled = computed(() => this.swState() !== 'IDLE');
  protected readonly swLeftText = computed(() =>
    this.swState() === 'RUNNING' ? 'Tour' : this.swState() === 'PAUSED' ? 'Réinitialiser' : '',
  );
  protected readonly swLeftIcon = computed(() => (this.swState() === 'RUNNING' ? 'flag' : 'restart_alt'));
  protected readonly swRightText = computed(() =>
    this.swState() === 'IDLE' ? 'Démarrer' : this.swState() === 'RUNNING' ? 'Arrêter' : 'Reprendre',
  );
  protected readonly swRightIcon = computed(() => (this.swState() === 'RUNNING' ? 'pause_circle' : 'play_circle'));

  protected readonly timerRightText = computed(() => {
    switch (this.timerState()) {
      case 'IDLE':
        return 'Démarrer';
      case 'RUNNING':
        return 'Pause';
      case 'PAUSED':
        return 'Reprendre';
      case 'FINISHED':
        return 'Redémarrer';
    }
  });
  protected readonly timerHint = computed(() => {
    if (this.timerState() !== 'IDLE') return 'Minuteur en cours — réinitialise pour changer';
    if (this.timerDurationMs() <= 0) return 'Choisis un préréglage';
    return 'Prêt';
  });

  // Nombre de segments allumés ∝ remaining / duration (TimerCircularDisplay.kt).
  private readonly litCount = computed(() => {
    const state = this.timerState();
    const duration = this.timerDurationMs();
    if (state === 'FINISHED' || duration <= 0) return 0;
    if (state === 'IDLE') return DIAL_SEGMENTS;
    const progress = Math.min(1, Math.max(0, this.remainingMs() / duration));
    return Math.floor(progress * DIAL_SEGMENTS);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.stopStopwatchTicker();
      this.stopTimerTicker();
    });
  }

  protected formatMs(ms: number): string {
    return formatTimeWithCentiseconds(ms);
  }

  /** Couleur d'un segment du cadran : éteints sens horaire depuis 12h ; FINISHED → pulse CSS. */
  protected segmentColor(index: number): string {
    if (this.timerState() === 'FINISHED') return 'var(--app-primary-action)';
    return index >= DIAL_SEGMENTS - this.litCount()
      ? 'var(--app-primary-action)'
      : 'color-mix(in srgb, var(--app-primary-action) 15%, transparent)';
  }

  // ===== Chronomètre : dispatch des boutons (ChronoScreenViewModel.kt) =====

  protected onStopwatchRight(): void {
    switch (this.swState()) {
      case 'IDLE': {
        this.swAccumulatedMs = 0;
        this.swRunningStartMs = Date.now();
        this.swState.set('RUNNING');
        this.startStopwatchTicker();
        break;
      }
      case 'RUNNING': {
        this.swAccumulatedMs += Date.now() - this.swRunningStartMs;
        this.elapsedMs.set(this.swAccumulatedMs);
        this.swState.set('PAUSED');
        this.stopStopwatchTicker();
        break;
      }
      case 'PAUSED': {
        this.swRunningStartMs = Date.now();
        this.swState.set('RUNNING');
        this.startStopwatchTicker();
        break;
      }
    }
  }

  protected onStopwatchLeft(): void {
    switch (this.swState()) {
      case 'RUNNING': {
        const total = this.swAccumulatedMs + (Date.now() - this.swRunningStartMs);
        const lapMillis = total - this.swLastLapTotalMs;
        this.swLastLapTotalMs = total;
        this.laps.update((list) => [...list, { index: list.length + 1, lapMillis, totalMillis: total }]);
        break;
      }
      case 'PAUSED': {
        this.stopStopwatchTicker();
        this.swAccumulatedMs = 0;
        this.swRunningStartMs = 0;
        this.swLastLapTotalMs = 0;
        this.elapsedMs.set(0);
        this.laps.set([]);
        this.swState.set('IDLE');
        break;
      }
      case 'IDLE':
        break;
    }
  }

  private startStopwatchTicker(): void {
    this.stopStopwatchTicker();
    this.swTicker = setInterval(() => {
      this.elapsedMs.set(this.swAccumulatedMs + (Date.now() - this.swRunningStartMs));
    }, TICK_INTERVAL_MS);
  }

  private stopStopwatchTicker(): void {
    if (this.swTicker !== null) {
      clearInterval(this.swTicker);
      this.swTicker = null;
    }
  }

  // ===== Minuteur : dispatch des boutons =====

  protected selectPreset(preset: TimerPreset): void {
    if (this.timerState() !== 'IDLE') return;
    this.setTimerDuration(preset.label, preset.millis);
  }

  protected onTimerLeft(): void {
    if (this.timerState() === 'IDLE') {
      // Pré-remplit le dialog avec la durée courante (TimerDurationDialog.kt clampMillisToHms).
      const totalSeconds = Math.floor(Math.max(0, this.timerDurationMs()) / 1000);
      this.dialogHours.set(Math.min(23, Math.floor(totalSeconds / 3600)));
      this.dialogMinutes.set(Math.min(59, Math.floor((totalSeconds % 3600) / 60)));
      this.dialogSeconds.set(Math.min(59, totalSeconds % 60));
      this.showSetDialog.set(true);
    } else {
      this.resetTimer();
    }
  }

  protected onTimerRight(): void {
    switch (this.timerState()) {
      case 'IDLE':
        this.startTimer();
        break;
      case 'RUNNING': {
        const rem = Math.max(0, this.timerEndMs - Date.now());
        this.timerRemainingOnPauseMs = rem;
        this.remainingMs.set(rem);
        this.timerState.set('PAUSED');
        this.stopTimerTicker();
        break;
      }
      case 'PAUSED': {
        const rem = Math.max(0, this.timerRemainingOnPauseMs);
        if (rem <= 0) {
          this.remainingMs.set(0);
          this.finishTimer();
          return;
        }
        this.timerEndMs = Date.now() + rem;
        this.timerState.set('RUNNING');
        this.startTimerTicker();
        break;
      }
      case 'FINISHED':
        this.resetTimer();
        this.startTimer();
        break;
    }
  }

  protected confirmDuration(): void {
    const millis = (this.dialogHours() * 3600 + this.dialogMinutes() * 60 + this.dialogSeconds()) * 1000;
    if (millis > 0) this.setTimerDuration(timerNameForDuration(millis), millis);
    this.showSetDialog.set(false);
  }

  private setTimerDuration(name: string, millis: number): void {
    this.timerName.set(name);
    this.timerDurationMs.set(Math.max(0, millis));
    if (this.timerState() === 'IDLE') this.remainingMs.set(this.timerDurationMs());
    localStorage.setItem(STORAGE_TIMER_NAME, name);
    localStorage.setItem(STORAGE_TIMER_MS, String(this.timerDurationMs()));
  }

  private startTimer(): void {
    const duration = this.timerDurationMs();
    if (this.timerState() !== 'IDLE' || duration <= 0) return;
    this.timerEndMs = Date.now() + duration;
    this.timerState.set('RUNNING');
    this.startTimerTicker();
  }

  private resetTimer(): void {
    this.stopTimerTicker();
    this.timerEndMs = 0;
    this.timerRemainingOnPauseMs = 0;
    this.remainingMs.set(this.timerDurationMs());
    this.timerState.set('IDLE');
  }

  private startTimerTicker(): void {
    this.stopTimerTicker();
    this.timerTicker = setInterval(() => {
      const rem = Math.max(0, this.timerEndMs - Date.now());
      this.remainingMs.set(rem);
      if (rem <= 0) this.finishTimer();
    }, TICK_INTERVAL_MS);
  }

  private stopTimerTicker(): void {
    if (this.timerTicker !== null) {
      clearInterval(this.timerTicker);
      this.timerTicker = null;
    }
  }

  /** Fin de minuteur → snackbar (équivalent web de notifyTimerDone "Timer finished"). */
  private finishTimer(): void {
    this.stopTimerTicker();
    this.timerState.set('FINISHED');
    const name = this.timerName().trim() || 'Minuteur';
    const durationSec = Math.floor(this.timerDurationMs() / 1000);
    this.snackbar.info(`Minuteur terminé — ${name} (${durationSec} s)`);
  }
}
