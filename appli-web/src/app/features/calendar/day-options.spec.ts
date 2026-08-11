import {
  buildDayOptions,
  VIEW_ACTUAL_LABEL,
  VIEW_PLANNED_LABEL,
  CREATE_NEW_LABEL,
  FROM_PLANNED_LABEL,
} from './day-options';

describe('buildDayOptions', () => {
  it('jour vide (ni séance ni planifié) : seulement les 2 actions d\'écriture', () => {
    const labels = buildDayOptions({ hasActual: false, hasPlannedSession: false }).map((a) => a.label);
    expect(labels).toEqual([CREATE_NEW_LABEL, FROM_PLANNED_LABEL]);
  });

  it('séance réalisée seule : seulement « Voir la séance du jour » (pas d\'écriture, jour non vide)', () => {
    const labels = buildDayOptions({ hasActual: true, hasPlannedSession: false }).map((a) => a.label);
    expect(labels).toEqual([VIEW_ACTUAL_LABEL]);
  });

  it('séance planifiée seule (jour vide) : « Voir la séance planifiée » puis les 2 actions d\'écriture', () => {
    const labels = buildDayOptions({ hasActual: false, hasPlannedSession: true }).map((a) => a.label);
    expect(labels).toEqual([VIEW_PLANNED_LABEL, CREATE_NEW_LABEL, FROM_PLANNED_LABEL]);
  });

  it('séance + planifié : seulement les 2 consultations (jour non vide, pas d\'écriture)', () => {
    const labels = buildDayOptions({ hasActual: true, hasPlannedSession: true }).map((a) => a.label);
    expect(labels).toEqual([VIEW_ACTUAL_LABEL, VIEW_PLANNED_LABEL]);
  });

  it('chaque action a un libellé, une icône et une couleur non vides', () => {
    for (const action of buildDayOptions({ hasActual: true, hasPlannedSession: true })) {
      expect(action.label.length).toBeGreaterThan(0);
      expect(action.icon.length).toBeGreaterThan(0);
      expect(action.color.length).toBeGreaterThan(0);
    }
  });
});
