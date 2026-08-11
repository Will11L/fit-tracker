import { Injectable, signal } from '@angular/core';
import { uuidv4 } from '@core/utils/uuid';
import { SnackbarEvent, SnackbarType } from '@designsystem/common_components/app-snackbar-host';

/** Durées (= SnackbarDuration.kt) : SHORT 2 s, LONG 4 s, INDEFINITE jusqu'au dismiss manuel. */
export type SnackbarDuration = 'SHORT' | 'LONG' | 'INDEFINITE';

/** Action d'un snackbar : libellé + callback exécuté au clic (= SnackbarAction.kt). */
export interface SnackbarAction {
  label: string;
  run: () => void | Promise<void>;
}

export interface ShowOptions {
  type?: SnackbarType;
  action?: SnackbarAction;
  secondaryAction?: SnackbarAction;
  duration?: SnackbarDuration;
}

/** Event interne : l'event UI (host) + les callbacks d'action (résolus par le service). */
interface InternalEvent extends SnackbarEvent {
  action?: SnackbarAction;
  secondaryAction?: SnackbarAction;
}

const DURATION_MS: Record<Exclude<SnackbarDuration, 'INDEFINITE'>, number> = {
  SHORT: 2000,
  LONG: 4000,
};

/**
 * Service de snackbars — miroir de SnackbarController.kt + showSnackbar() (SnackbarUtils.kt).
 * File réactive (`items` signal) consommée par <app-snackbar-host> monté dans le shell.
 * Plusieurs snackbars empilés ; auto-dismiss par durée (sauf INDEFINITE qui a un bouton « Fermer »).
 *
 * Déviation vs Android : durée par défaut LONG (4 s auto-dismiss) au lieu d'Indefinite — adapté
 * à l'UX web (on évite l'empilement permanent). Passer `duration: 'INDEFINITE'` pour un snackbar
 * persistant (erreur critique, action requise).
 */
@Injectable({ providedIn: 'root' })
export class SnackbarService {
  private readonly _items = signal<InternalEvent[]>([]);
  /** File des snackbars actifs (lecture seule) — branchée à <app-snackbar-host [snackbars]>. */
  readonly items = this._items.asReadonly();

  private readonly timers = new Map<string, ReturnType<typeof setTimeout>>();

  /** Pousse un snackbar et renvoie son id (= showSnackbar). */
  show(message: string, opts: ShowOptions = {}): string {
    const id = uuidv4();
    const type: SnackbarType = opts.type ?? 'INFO';
    const duration: SnackbarDuration = opts.duration ?? 'LONG';
    // Si INDEFINITE sans action secondaire fournie : on ajoute un « Fermer » par défaut.
    const secondaryAction =
      opts.secondaryAction ??
      (duration === 'INDEFINITE' ? { label: 'Fermer', run: () => this.dismiss(id) } : undefined);

    const ev: InternalEvent = {
      id,
      message,
      type,
      actionLabel: opts.action?.label,
      secondaryActionLabel: secondaryAction?.label,
      action: opts.action,
      secondaryAction,
    };
    this._items.update((list) => [...list, ev]);

    if (duration !== 'INDEFINITE') {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), DURATION_MS[duration]),
      );
    }
    return id;
  }

  success(message: string, opts: Omit<ShowOptions, 'type'> = {}): string {
    return this.show(message, { ...opts, type: 'SUCCESS' });
  }
  error(message: string, opts: Omit<ShowOptions, 'type'> = {}): string {
    return this.show(message, { ...opts, type: 'ERROR' });
  }
  warning(message: string, opts: Omit<ShowOptions, 'type'> = {}): string {
    return this.show(message, { ...opts, type: 'WARNING' });
  }
  info(message: string, opts: Omit<ShowOptions, 'type'> = {}): string {
    return this.show(message, { ...opts, type: 'INFO' });
  }

  /** Retire un snackbar (et annule son timer) — = dismissSnackbarById. */
  dismiss(id: string): void {
    const t = this.timers.get(id);
    if (t) {
      clearTimeout(t);
      this.timers.delete(id);
    }
    this._items.update((list) => list.filter((e) => e.id !== id));
  }

  dismissAll(): void {
    for (const t of this.timers.values()) clearTimeout(t);
    this.timers.clear();
    this._items.set([]);
  }

  /** Clic sur l'action principale : exécute le callback puis ferme. */
  runAction(id: string): void {
    const ev = this._items().find((e) => e.id === id);
    void ev?.action?.run();
    this.dismiss(id);
  }

  /** Clic sur l'action secondaire (« Fermer » par défaut) : exécute le callback puis ferme. */
  runSecondaryAction(id: string): void {
    const ev = this._items().find((e) => e.id === id);
    void ev?.secondaryAction?.run();
    this.dismiss(id);
  }
}
