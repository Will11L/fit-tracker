import type { SheetAction } from '@designsystem/common_components/options-bottom-sheet';

/** Libellés des actions du bottom sheet d'un jour (source unique partagée avec calendar-page + tests). */
export const VIEW_ACTUAL_LABEL = 'Voir la séance du jour';
export const VIEW_PLANNED_LABEL = 'Voir la séance planifiée';
export const CREATE_NEW_LABEL = 'Créer une nouvelle séance';
export const FROM_PLANNED_LABEL = 'Démarrer depuis planifié';

/** État d'un jour pertinent pour décider quelles actions afficher. */
export interface DayOptionsState {
  /** Une séance réalisée (`actual_workout`) existe ce jour. */
  hasActual: boolean;
  /** Une séance planifiée non-repos existe pour ce jour de semaine. */
  hasPlannedSession: boolean;
}

/**
 * Construit les actions du bottom sheet d'un jour (= DayOptionsBottomSheet.kt).
 *
 * `SheetAction` ne supporte pas l'état désactivé : on n'inclut donc une action que si elle est
 * pertinente. Ordre : d'abord les actions de consultation (voir la séance du jour / voir la séance
 * planifiée) quand elles existent.
 *
 * Les 2 actions d'écriture (créer une nouvelle séance / démarrer depuis planifié) ne sont
 * proposées que pour un jour **encore vide** (`!hasActual`) : une séance existe déjà ce jour-là,
 * on ne peut donc plus en créer une seconde — on la consulte.
 */
export function buildDayOptions(state: DayOptionsState): SheetAction[] {
  const actions: SheetAction[] = [];

  if (state.hasActual) {
    actions.push({ label: VIEW_ACTUAL_LABEL, icon: 'visibility', color: 'var(--c-blue-medium)' });
  }
  if (state.hasPlannedSession) {
    actions.push({ label: VIEW_PLANNED_LABEL, icon: 'event', color: 'var(--c-blue-medium)' });
  }

  if (!state.hasActual) {
    actions.push({ label: CREATE_NEW_LABEL, icon: 'add', color: 'var(--app-primary-action)' });
    actions.push({ label: FROM_PLANNED_LABEL, icon: 'add_link', color: 'var(--app-selected-fill)' });
  }

  return actions;
}
