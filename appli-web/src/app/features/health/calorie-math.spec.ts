import { ageYears, bmr, fromActive, fromTotal, type CalorieProfile } from './calorie-math';

/** Profil synthétique (82 kg · 183 cm · MALE · né 1999-01-01 → 27 ans le 2026-07-06). */
const willProfile: CalorieProfile = {
  weightKg: 82,
  heightCm: 183,
  birthDate: '1999-01-01',
  sex: 'MALE',
};
const today = new Date(2026, 6, 6); // 2026-07-06 (mois 0-based)
// BMR MALE = 10·82 + 6,25·183 − 5·27 + 5 = 820 + 1143,75 − 135 + 5 = 1833,75 → 1834.
const willBmr = 1834;

describe('calorie-math', () => {
  describe('ageYears', () => {
    it('calcule l\'âge à la date donnée', () => {
      expect(ageYears('1999-01-01', today)).toBe(27);
    });
    it('anniversaire pas encore passé cette année → -1 an', () => {
      expect(ageYears('1999-12-31', today)).toBe(26);
    });
    it('null si absente, non parsable ou future', () => {
      expect(ageYears(null, today)).toBeNull();
      expect(ageYears('', today)).toBeNull();
      expect(ageYears('pas-une-date', today)).toBeNull();
      expect(ageYears('2030-01-01', today)).toBeNull();
    });
  });

  describe('bmr (Mifflin-St Jeor)', () => {
    it('MALE = 10·kg + 6,25·cm − 5·âge + 5', () => {
      expect(bmr(82, 183, 27, 'MALE')).toBeCloseTo(1833.75, 2);
    });
    it('FEMALE = base − 161', () => {
      expect(bmr(60, 165, 30, 'FEMALE')).toBeCloseTo(10 * 60 + 6.25 * 165 - 5 * 30 - 161, 2);
    });
    it('null si sexe ≠ MALE/FEMALE ou champ manquant/invalide', () => {
      expect(bmr(82, 183, 27, 'OTHER')).toBeNull();
      expect(bmr(82, 183, 27, null)).toBeNull();
      expect(bmr(null, 183, 27, 'MALE')).toBeNull();
      expect(bmr(82, 0, 27, 'MALE')).toBeNull();
      expect(bmr(82, 183, 200, 'MALE')).toBeNull();
    });
  });

  describe('fromActive (montre : total = actives + BMR)', () => {
    it('profil complet → 3 champs (actives mesurées, total dérivé)', () => {
      const b = fromActive(722, willProfile, today);
      expect(b.activeKcal).toBe(722);
      expect(b.bmrKcal).toBe(willBmr);
      expect(b.totalKcal).toBe(722 + willBmr);
    });
    it('profil incomplet → actives seules', () => {
      const b = fromActive(722, { weightKg: null, heightCm: null, birthDate: null, sex: null }, today);
      expect(b).toEqual({ bmrKcal: null, activeKcal: 722, totalKcal: null });
    });
  });

  describe('fromTotal (HC : actives = max(0, total − BMR))', () => {
    it('profil complet → 3 champs (total mesuré, actives dérivées)', () => {
      const b = fromTotal(2601, willProfile, today);
      expect(b.totalKcal).toBe(2601);
      expect(b.bmrKcal).toBe(willBmr);
      expect(b.activeKcal).toBe(2601 - willBmr);
    });
    it('actives clampées à 0 si total < BMR', () => {
      expect(fromTotal(1000, willProfile, today).activeKcal).toBe(0);
    });
    it('profil incomplet → total seul', () => {
      const b = fromTotal(2601, { weightKg: 82, heightCm: 183, birthDate: null, sex: 'MALE' }, today);
      expect(b).toEqual({ bmrKcal: null, activeKcal: null, totalKcal: 2601 });
    });
  });
});
