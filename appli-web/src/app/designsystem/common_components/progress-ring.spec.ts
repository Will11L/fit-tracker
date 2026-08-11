import { ringGeometry } from './progress-ring';

describe('ProgressRing — géométrie (calcul dasharray)', () => {
  it('rayon = (taille − épaisseur) / 2 et circonférence = 2πr', () => {
    const g = ringGeometry(76, 8, 0);
    expect(g.radius).toBe(34); // (76 - 8) / 2
    expect(g.circumference).toBeCloseTo(2 * Math.PI * 34);
  });

  it('progression 0 : aucun arc rempli (offset = circonférence complète)', () => {
    const g = ringGeometry(76, 8, 0);
    expect(g.dash).toBe(0);
    expect(g.offset).toBeCloseTo(g.circumference);
  });

  it('progression 0.5 : moitié de l’arc rempli, offset = moitié restante', () => {
    const g = ringGeometry(76, 8, 0.5);
    expect(g.dash).toBeCloseTo(g.circumference / 2);
    expect(g.offset).toBeCloseTo(g.circumference / 2);
  });

  it('progression bornée 0..1 (au-delà de 1 et en dessous de 0)', () => {
    const full = ringGeometry(76, 8, 1.4);
    expect(full.dash).toBeCloseTo(full.circumference);
    expect(full.offset).toBeCloseTo(0);

    const empty = ringGeometry(76, 8, -0.3);
    expect(empty.dash).toBe(0);
    expect(empty.offset).toBeCloseTo(empty.circumference);
  });
});
