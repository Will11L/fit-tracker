import { TestBed } from '@angular/core/testing';
import { LocalFood } from '@core/models/food.model';
import { LocalFoodPortion } from '@core/models/food-portion.model';
import { FoodDetailPanel } from './food-detail-panel';

// Panneau de DÉTAIL du master/détail (catalogue T5) — composant présentationnel : toutes les
// écritures remontent en outputs. On teste le comportement OBSERVABLE piloté par les inputs
// (food/portions) et les interactions DOM : tri des portions, gating des actions selon la source /
// l'état archivé, validation + émission de l'ajout de portion, et reset de la saisie au changement
// d'aliment. Depuis 02eea9d4 le résumé démarre en RADAR (« chaque sélecteur démarre sur son 1er
// mode ») — or echarts n'est pas montable en jsdom (pas de contexte canvas 2D, ni de
// ResizeObserver — cf. radar-chart.spec) : mount() force donc le mode `bar` AVANT le premier
// detectChanges. Le rendu du radar lui-même est couvert par radar-chart.spec.

function food(over: Partial<LocalFood>): LocalFood {
  return {
    uuid: 'f1',
    userId: 1,
    name: 'Avoine',
    brand: null,
    source: 'CUSTOM',
    sourceRef: null,
    foodGroup: null,
    kcalPer100g: 380,
    proteinPer100g: 13,
    carbsPer100g: 60,
    fatPer100g: 7,
    fiberPer100g: 10,
    sugarPer100g: null,
    satFatPer100g: null,
    saltPer100g: null,
    ironPer100g: null,
    calciumPer100g: null,
    magnesiumPer100g: null,
    zincPer100g: null,
    potassiumPer100g: null,
    sodiumPer100g: null,
    vitaminCPer100g: null,
    vitaminDPer100g: null,
    vitaminB12Per100g: null,
    vitaminAPer100g: null,
    isFavorite: false,
    archived: false,
    isWater: false,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

function portion(over: Partial<LocalFoodPortion>): LocalFoodPortion {
  return {
    uuid: 'p1',
    foodUUID: 'f1',
    label: 'Portion',
    grams: 100,
    updatedAt: null,
    synced: true,
    pendingDeletion: false,
    ...over,
  };
}

function mount(f: LocalFood, portions: LocalFoodPortion[] = []) {
  const fixture = TestBed.createComponent(FoodDetailPanel);
  // Force le résumé en 'bar' avant le 1er render : le défaut 'radar' monterait ECharts (crash jsdom).
  (
    fixture.componentInstance as unknown as { summaryDisplay: { set(v: string): void } }
  ).summaryDisplay.set('bar');
  fixture.componentRef.setInput('food', f);
  fixture.componentRef.setInput('portions', portions);
  fixture.detectChanges();
  return fixture;
}

/** Noms d'icône des boutons d'action (icône seulement) de l'en-tête de la carte, en ordre DOM. */
function actionIcons(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('.detail__actions app-action-icon-button .ms')).map((n) =>
    (n.textContent ?? '').trim(),
  );
}

/** Clique le bouton d'action (icône seulement) portant cette icône (échoue si introuvable). */
function clickAction(el: HTMLElement, icon: string): void {
  const btn = Array.from(
    el.querySelectorAll('.detail__actions app-action-icon-button button'),
  ).find((b) => (b.querySelector('.ms')?.textContent ?? '').trim() === icon) as
    | HTMLButtonElement
    | undefined;
  if (!btn) throw new Error(`Action « ${icon} » introuvable`);
  btn.click();
}

/** Le chip d'une portion (cliquable → bascule en édition). */
function chipBtn(el: HTMLElement): HTMLButtonElement {
  const btn = el.querySelector('button.portion') as HTMLButtonElement | null;
  if (!btn) throw new Error('Chip de portion introuvable');
  return btn;
}

describe('FoodDetailPanel — portions nommées', () => {
  it('affiche les portions triées par grammage croissant (indépendamment de l’ordre d’entrée)', () => {
    const el = mount(food({}), [
      portion({ uuid: 'pa', label: 'Grande', grams: 200 }),
      portion({ uuid: 'pb', label: 'Petite', grams: 30 }),
      portion({ uuid: 'pc', label: 'Moyenne', grams: 100 }),
    ]).nativeElement as HTMLElement;
    const labels = Array.from(el.querySelectorAll('.portion .portion__label')).map((n) =>
      (n.textContent ?? '').trim(),
    );
    const grams = Array.from(el.querySelectorAll('.portion .portion__grams')).map((n) =>
      (n.textContent ?? '').trim(),
    );
    expect(labels).toEqual(['Petite', 'Moyenne', 'Grande']);
    expect(grams).toEqual(['30 g', '100 g', '200 g']);
  });

  it('aucune portion → indice « Aucune portion nommée. », pas de ligne de portion', () => {
    const el = mount(food({}), []).nativeElement as HTMLElement;
    expect(el.querySelectorAll('.portion').length).toBe(0);
    expect(el.querySelector('app-empty-list-row')).toBeTruthy();
  });
});

describe('FoodDetailPanel — en-tête de la carte aliment (ExpandableCard)', () => {
  it('nom (gauche) · actions en icône seulement (droite) ; la bascule d’affichage est dans l’en-tête du résumé', () => {
    const el = mount(food({ source: 'CIQUAL', foodGroup: 'FRUIT', name: 'Pomme' }))
      .nativeElement as HTMLElement;
    // Nom dans l'en-tête secondBlue de la carte (tronqué « … » si trop long via CSS).
    expect((el.querySelector('.detail__name')?.textContent ?? '').trim()).toBe('Pomme');
    // Badge catégorie (libellé FR + couleur mnémotechnique du groupe) à côté du nom.
    const badge = el.querySelector('.detail__badge') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('Fruits');
    expect(badge.style.getPropertyValue('--badge-c')).toBe('var(--food-grp-fruit)');
    // Actions en icône seulement (CIQUAL → pas de Modifier).
    expect(actionIcons(el)).toEqual(['archive', 'delete']);
    // La bascule d’affichage (barres / radar / anneaux) est projetée dans l’en-tête du panneau résumé,
    // pas dans la zone d’actions de l’en-tête de la carte.
    expect(el.querySelector('.detail__actions app-segmented-icon-toggle')).toBeNull();
    expect(el.querySelector('app-nutrition-summary-panel app-segmented-icon-toggle')).toBeTruthy();
  });

  it('le divider de tête est le titre fixe « Aliment » (plus le nom de l’aliment)', () => {
    const el = mount(food({ name: 'Avoine' })).nativeElement as HTMLElement;
    const firstDivider = (el.querySelector('app-titled-divider')?.textContent ?? '').trim();
    expect(firstDivider).toContain('Aliment');
    expect(firstDivider).not.toContain('Avoine');
  });
});

describe('FoodDetailPanel — actions selon la source / l’état archivé', () => {
  it('source CUSTOM → bouton « Modifier » (edit) présent', () => {
    const el = mount(food({ source: 'CUSTOM' })).nativeElement as HTMLElement;
    expect(actionIcons(el)).toEqual(['edit', 'archive', 'delete']);
  });

  it('source CIQUAL (non perso) → pas de bouton « Modifier »', () => {
    const el = mount(food({ source: 'CIQUAL' })).nativeElement as HTMLElement;
    expect(actionIcons(el)).toEqual(['archive', 'delete']);
  });

  it('aliment archivé → l’action passe de « archive » à « unarchive » (Restaurer)', () => {
    const archived = mount(food({ source: 'CIQUAL', archived: true })).nativeElement as HTMLElement;
    expect(actionIcons(archived)).toContain('unarchive');
    expect(actionIcons(archived)).not.toContain('archive');
  });
});

describe('FoodDetailPanel — câblage des actions vers les outputs', () => {
  it('chaque bouton de la barre émet SON output (et lui seul) avec l’aliment courant', () => {
    const f = food({ uuid: 'fx', source: 'CUSTOM' });
    const fixture = mount(f);
    const el = fixture.nativeElement as HTMLElement;
    const events: string[] = [];
    let editArg: LocalFood | null = null;
    let archiveArg: LocalFood | null = null;
    let removeArg: LocalFood | null = null;
    fixture.componentInstance.edit.subscribe((v) => {
      events.push('edit');
      editArg = v;
    });
    fixture.componentInstance.archiveToggle.subscribe((v) => {
      events.push('archive');
      archiveArg = v;
    });
    fixture.componentInstance.remove.subscribe((v) => {
      events.push('remove');
      removeArg = v;
    });

    clickAction(el, 'edit');
    clickAction(el, 'archive');
    clickAction(el, 'delete');

    // Pas de croisement de fils : un clic = exactement un output, dans l’ordre des clics.
    expect(events).toEqual(['edit', 'archive', 'remove']);
    // Chaque output transporte l’aliment affiché (même référence).
    expect(editArg).toBe(f);
    expect(archiveArg).toBe(f);
    expect(removeArg).toBe(f);
  });

  it('aliment archivé : le bouton « Restaurer » émet archiveToggle (pas remove)', () => {
    const f = food({ source: 'CIQUAL', archived: true });
    const fixture = mount(f);
    const el = fixture.nativeElement as HTMLElement;
    const events: string[] = [];
    fixture.componentInstance.archiveToggle.subscribe(() => events.push('archive'));
    fixture.componentInstance.remove.subscribe(() => events.push('remove'));

    clickAction(el, 'unarchive');

    expect(events).toEqual(['archive']);
  });
});

describe('FoodDetailPanel — ajout de portion', () => {
  function fields(el: HTMLElement) {
    return {
      label: el.querySelector('.portion-add__label input') as HTMLInputElement,
      grams: el.querySelector('.portion-add__grams input') as HTMLInputElement,
      addBtn: el.querySelector('.portion-add app-action-icon-button button') as HTMLButtonElement,
    };
  }
  function type(input: HTMLInputElement, value: string) {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('bouton désactivé tant que le libellé ou le grammage sont invalides', () => {
    const fixture = mount(food({}), []);
    const el = fixture.nativeElement as HTMLElement;
    let f = fields(el);
    expect(f.addBtn.disabled).toBe(true); // vide

    type(f.label, '1 œuf'); // libellé seul → toujours invalide (grammage manquant)
    fixture.detectChanges();
    f = fields(el);
    expect(f.addBtn.disabled).toBe(true);

    type(f.grams, '0'); // grammage 0 → invalide
    fixture.detectChanges();
    f = fields(el);
    expect(f.addBtn.disabled).toBe(true);

    type(f.grams, '60'); // libellé + grammage > 0 → valide
    fixture.detectChanges();
    f = fields(el);
    expect(f.addBtn.disabled).toBe(false);
  });

  it('clic « ajouter » émet { label, grams } (libellé trim) et réinitialise la saisie', () => {
    const fixture = mount(food({}), []);
    const el = fixture.nativeElement as HTMLElement;
    const emitted: { label: string; grams: number }[] = [];
    fixture.componentInstance.portionAdd.subscribe((v) => emitted.push(v));

    let f = fields(el);
    type(f.label, '  1 œuf  ');
    type(f.grams, '60');
    fixture.detectChanges();
    f = fields(el);
    expect(f.addBtn.disabled).toBe(false);

    f.addBtn.click();
    fixture.detectChanges();

    expect(emitted).toEqual([{ label: '1 œuf', grams: 60 }]);
    // Saisie réinitialisée après émission.
    f = fields(el);
    expect(f.label.value).toBe('');
    expect(f.grams.value).toBe('');
    expect(f.addBtn.disabled).toBe(true);
  });

  it('changer d’aliment réinitialise la saisie de portion en cours (n’émet rien)', () => {
    const fixture = mount(food({ uuid: 'f1' }), []);
    const el = fixture.nativeElement as HTMLElement;
    const emitted: unknown[] = [];
    fixture.componentInstance.portionAdd.subscribe((v) => emitted.push(v));

    type(fields(el).label, 'brouillon');
    fixture.detectChanges();
    expect(fields(el).label.value).toBe('brouillon');

    fixture.componentRef.setInput('food', food({ uuid: 'f2', name: 'Banane' }));
    fixture.detectChanges();

    expect(fields(el).label.value).toBe('');
    expect(emitted).toEqual([]);
  });
});

describe('FoodDetailPanel — édition d’une portion', () => {
  function setVal(input: HTMLInputElement, value: string) {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }
  /** Bouton (icône donnée) au sein du conteneur fourni (ligne d'affichage ou ligne d'édition). */
  function iconBtn(el: HTMLElement, container: string, icon: string): HTMLButtonElement {
    const btns = Array.from(
      el.querySelectorAll(`${container} app-action-icon-button button`),
    ) as HTMLButtonElement[];
    const found = btns.find((b) => (b.querySelector('.ms')?.textContent ?? '').trim() === icon);
    if (!found) throw new Error(`Bouton « ${icon} » introuvable dans ${container}`);
    return found;
  }
  function editFields(el: HTMLElement) {
    return {
      label: el.querySelector('.portion-edit__label input') as HTMLInputElement,
      grams: el.querySelector('.portion-edit__grams input') as HTMLInputElement,
      save: iconBtn(el, '.portion-edit', 'check'),
      cancel: iconBtn(el, '.portion-edit', 'close'),
    };
  }

  it('la portion est un chip cliquable (sans boutons inline) ; le tap ouvre l’édition (valider / annuler / supprimer)', () => {
    const fixture = mount(food({}), [portion({ uuid: 'pa', label: 'Grande', grams: 200 })]);
    const el = fixture.nativeElement as HTMLElement;
    // Le chip ne porte aucun bouton d'action inline.
    expect(chipBtn(el).querySelectorAll('app-action-icon-button').length).toBe(0);
    // Tap → édition, qui porte valider + annuler + supprimer.
    chipBtn(el).click();
    fixture.detectChanges();
    expect(iconBtn(el, '.portion-edit', 'check')).toBeTruthy();
    expect(iconBtn(el, '.portion-edit', 'close')).toBeTruthy();
    expect(iconBtn(el, '.portion-edit', 'delete')).toBeTruthy();
  });

  it('le bouton delete (en édition) émet portionRemove avec l’uuid de la portion', () => {
    const fixture = mount(food({}), [portion({ uuid: 'pa', grams: 100 })]);
    const el = fixture.nativeElement as HTMLElement;
    const emitted: string[] = [];
    fixture.componentInstance.portionRemove.subscribe((v) => emitted.push(v));

    chipBtn(el).click();
    fixture.detectChanges();
    iconBtn(el, '.portion-edit', 'delete').click();
    expect(emitted).toEqual(['pa']);
  });

  it('clic sur le chip bascule en édition, champs préremplis (libellé + grammes)', () => {
    const fixture = mount(food({}), [portion({ uuid: 'pa', label: '1 œuf', grams: 60 })]);
    const el = fixture.nativeElement as HTMLElement;

    chipBtn(el).click();
    fixture.detectChanges();

    // Le chip cède la place à la ligne d'édition.
    expect(el.querySelector('button.portion')).toBeNull();
    const ef = editFields(el);
    expect(ef.label.value).toBe('1 œuf');
    expect(ef.grams.value).toBe('60');
  });

  it('« valider » désactivé tant que le libellé est vide ou le grammage <= 0', () => {
    const fixture = mount(food({}), [portion({ uuid: 'pa', label: '1 œuf', grams: 60 })]);
    const el = fixture.nativeElement as HTMLElement;
    chipBtn(el).click();
    fixture.detectChanges();

    expect(editFields(el).save.disabled).toBe(false); // prérempli = valide

    setVal(editFields(el).label, '   '); // libellé vide → invalide
    fixture.detectChanges();
    expect(editFields(el).save.disabled).toBe(true);

    setVal(editFields(el).label, '1 œuf');
    setVal(editFields(el).grams, '0'); // grammage 0 → invalide
    fixture.detectChanges();
    expect(editFields(el).save.disabled).toBe(true);
  });

  it('« valider » émet portionUpdate { uuid, label (trim), grams } puis quitte l’édition', () => {
    const fixture = mount(food({}), [portion({ uuid: 'pa', label: '1 œuf', grams: 60 })]);
    const el = fixture.nativeElement as HTMLElement;
    const emitted: { uuid: string; label: string; grams: number }[] = [];
    fixture.componentInstance.portionUpdate.subscribe((v) => emitted.push(v));

    chipBtn(el).click();
    fixture.detectChanges();
    setVal(editFields(el).label, '  2 œufs  ');
    setVal(editFields(el).grams, '120');
    fixture.detectChanges();
    editFields(el).save.click();
    fixture.detectChanges();

    expect(emitted).toEqual([{ uuid: 'pa', label: '2 œufs', grams: 120 }]);
    // Retour à l'affichage (le parent applique la mise à jour ; le panneau quitte l'édition).
    expect(el.querySelector('.portion-edit')).toBeNull();
    expect(el.querySelector('.portion')).toBeTruthy();
  });

  it('« annuler » quitte l’édition sans émettre', () => {
    const fixture = mount(food({}), [portion({ uuid: 'pa', label: '1 œuf', grams: 60 })]);
    const el = fixture.nativeElement as HTMLElement;
    const emitted: unknown[] = [];
    fixture.componentInstance.portionUpdate.subscribe((v) => emitted.push(v));

    chipBtn(el).click();
    fixture.detectChanges();
    setVal(editFields(el).label, 'brouillon');
    fixture.detectChanges();
    editFields(el).cancel.click();
    fixture.detectChanges();

    expect(emitted).toEqual([]);
    expect(el.querySelector('.portion-edit')).toBeNull();
    expect(el.querySelector('.portion')).toBeTruthy();
  });

  it('changer d’aliment annule une édition de portion en cours', () => {
    const fixture = mount(food({ uuid: 'f1' }), [portion({ uuid: 'pa', label: '1 œuf', grams: 60 })]);
    const el = fixture.nativeElement as HTMLElement;

    chipBtn(el).click();
    fixture.detectChanges();
    expect(el.querySelector('.portion-edit')).toBeTruthy();

    fixture.componentRef.setInput('food', food({ uuid: 'f2', name: 'Banane' }));
    fixture.componentRef.setInput('portions', [portion({ uuid: 'pb', label: 'Tranche', grams: 30 })]);
    fixture.detectChanges();

    expect(el.querySelector('.portion-edit')).toBeNull();
    expect(el.querySelector('.portion')).toBeTruthy();
  });
});

// Fonds sémantiques (cœur de la tâche) : l'action DESTRUCTIVE (supprimer) doit ressortir en fond
// rouge plein (--app-btn-danger-bg) + icône/texte blancs (--app-btn-danger-fg), tandis que les
// actions non destructives gardent leur fond neutre/primaire. On vérifie le STYLE EFFECTIVEMENT
// RENDU (inline style), pas un input passé : c'est le rendu observable qui prouve « delete rouge /
// icône blanche ». (jsdom conserve les valeurs var() sur background/color — vérifié.)
describe('FoodDetailPanel — fonds sémantiques des actions (delete rouge / icône blanche)', () => {
  const DANGER_BG = 'var(--app-btn-danger-bg)';
  const DANGER_FG = 'var(--app-btn-danger-fg)';

  /** Bouton icône-seule (icône donnée) au sein du conteneur fourni. */
  function iconOnly(el: HTMLElement, container: string, icon: string): HTMLButtonElement {
    const found = Array.from(
      el.querySelectorAll(`${container} app-action-icon-button button`),
    ).find((b) => (b.querySelector('.ms')?.textContent ?? '').trim() === icon) as
      | HTMLButtonElement
      | undefined;
    if (!found) throw new Error(`Bouton « ${icon} » introuvable dans ${container}`);
    return found;
  }
  const iconColor = (btn: HTMLElement) => (btn.querySelector('.ms') as HTMLElement).style.color;

  it('action « Supprimer » (aliment) : fond rouge plein + icône blanche', () => {
    const el = mount(food({ source: 'CUSTOM' })).nativeElement as HTMLElement;
    const del = iconOnly(el, '.detail__actions', 'delete');
    expect(del.style.background).toBe(DANGER_BG);
    expect(iconColor(del)).toBe(DANGER_FG);
  });

  it('les actions NON destructives (Modifier / Archiver) n’utilisent pas le fond danger', () => {
    const el = mount(food({ source: 'CUSTOM' })).nativeElement as HTMLElement;
    expect(iconOnly(el, '.detail__actions', 'edit').style.background).not.toBe(DANGER_BG);
    expect(iconOnly(el, '.detail__actions', 'archive').style.background).not.toBe(DANGER_BG);
  });

  it('bouton delete d’une portion (en édition) : fond rouge + icône blanche ; valider reste neutre', () => {
    const fixture = mount(food({}), [portion({ uuid: 'pa', grams: 100 })]);
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('button.portion') as HTMLButtonElement).click();
    fixture.detectChanges();
    const del = iconOnly(el, '.portion-edit', 'delete');
    expect(del.style.background).toBe(DANGER_BG);
    expect(iconColor(del)).toBe(DANGER_FG);
    // Les actions non destructives (valider) ne prennent pas le fond danger.
    expect(iconOnly(el, '.portion-edit', 'check').style.background).not.toBe(DANGER_BG);
  });
});
