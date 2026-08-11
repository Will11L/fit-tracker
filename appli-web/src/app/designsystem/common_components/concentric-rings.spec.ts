import {
  concentricLabelViews,
  concentricRingViews,
  type ConcentricRing,
  type RingView,
} from './concentric-rings';

describe('concentricRingViews — pile d’anneaux concentriques (dasharray)', () => {
  const ring = (over: Partial<ConcentricRing> = {}): ConcentricRing => ({
    progress: 0,
    color: 'var(--macro-kcal)',
    width: 4,
    ...over,
  });

  it('produit un anneau par entrée, du plus extérieur (grand rayon) au plus intérieur', () => {
    const views = concentricRingViews(64, 2, [ring({ width: 6 }), ring({ width: 4 }), ring({ width: 4 })]);
    expect(views.length).toBe(3);
    // Rayons strictement décroissants.
    expect(views[0].radius).toBeGreaterThan(views[1].radius);
    expect(views[1].radius).toBeGreaterThan(views[2].radius);
  });

  it('dasharray = circonférence ; offset reflète la progression (0 → vide, 1 → plein)', () => {
    const [empty] = concentricRingViews(64, 2, [ring({ progress: 0, width: 6 })]);
    // progress 0 → offset = circonférence (rien de tracé).
    expect(empty.offset).toBeCloseTo(empty.circumference);

    const [full] = concentricRingViews(64, 2, [ring({ progress: 1, width: 6 })]);
    // progress 1 → offset = 0 (anneau complet).
    expect(full.offset).toBeCloseTo(0);

    const [half] = concentricRingViews(64, 2, [ring({ progress: 0.5, width: 6 })]);
    expect(half.offset).toBeCloseTo(half.circumference / 2);
  });

  it('s’arrête quand il n’y a plus de place au centre (diamètre ≤ 0)', () => {
    // Anneaux épais sur une petite boîte → certains sont écartés.
    const views = concentricRingViews(20, 2, [ring({ width: 6 }), ring({ width: 6 }), ring({ width: 6 })]);
    expect(views.length).toBeLessThan(3);
  });
});

describe('concentricLabelViews — étiquettes « en étoile » (lignes de rappel)', () => {
  const view = (over: Partial<RingView> = {}): RingView => ({
    radius: 50,
    circumference: 314,
    offset: 0,
    color: 'var(--macro-kcal)',
    width: 10,
    ...over,
  });

  it('une vue d’étiquette par anneau étiqueté, ignore les anneaux sans label', () => {
    const labels = concentricLabelViews(100, 100, 60, [
      view({ label: 'Calories 95%' }),
      view({ radius: 36 }), // pas de label → ignoré
      view({ radius: 24, label: 'Glucides 80%' }),
    ]);
    expect(labels.length).toBe(2);
    expect(labels.map((l) => l.text)).toEqual(['Calories 95%', 'Glucides 80%']);
  });

  it('le 1er anneau étiqueté est placé en haut (point d’ancrage au-dessus du centre)', () => {
    const [first] = concentricLabelViews(100, 100, 60, [view({ radius: 50, label: 'Calories 95%' })]);
    const [ax, ay] = first.points.split(' ')[0].split(',').map(Number);
    expect(ax).toBeCloseTo(100); // même x que le centre
    expect(ay).toBeCloseTo(50); // cy - radius = 100 - 50
    expect(first.textY).toBeLessThan(100); // texte au-dessus du centre
  });

  it('polyline à 3 points (ancrage anneau → coude radial → segment horizontal)', () => {
    const [l] = concentricLabelViews(100, 100, 60, [view({ label: 'X' })]);
    expect(l.points.trim().split(' ').length).toBe(3);
  });

  it('ancrage du texte selon le côté (droite = start, gauche = end)', () => {
    // 4 étiquettes → angles -90, 0, 90, 180 : k=1 (0°) à droite, k=3 (180°) à gauche.
    const labels = concentricLabelViews(100, 100, 60, [
      view({ label: 'top' }),
      view({ label: 'droite' }),
      view({ label: 'bas' }),
      view({ label: 'gauche' }),
    ]);
    expect(labels[1].anchor).toBe('start');
    expect(labels[3].anchor).toBe('end');
  });
});
